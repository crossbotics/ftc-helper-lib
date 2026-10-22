package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.launcher.Feeder;
import com.crossbotics.ftchelper.launcher.MotorFeeder;
import com.crossbotics.ftchelper.launcher.ServoFeeder;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Feeds ONE game piece at a time into a launcher: a servo that pushes, a CR servo or a motor that runs
 * for a moment. Usually given to a {@link Launcher}, which decides when to feed:
 *
 * <pre>{@code
 * launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer")));
 *
 * public static class Indexer {                       // Tunables.java
 *     public static double rest = 0.15, push = 0.55;  // servo positions (Indexer.servo)
 *     public static double pushSec = 0.25, returnSec = 0.25;
 * }
 * }</pre>
 *
 * <p>Tunables: {@code Indexer.servo}: {@code rest}, {@code push}, {@code pushSec}, {@code returnSec}.
 * {@code Indexer.crServo} and {@code Indexer.motor}: {@code power} and {@code feedSec} (how long it
 * runs per piece). Added on its own (no launcher), its suggested button is operator RB: feed one.
 *
 * Docs: docs/modules/robot.md (section "Indexer")
 */
public final class Indexer extends Module<Indexer> implements Feeder {
    private enum Kind { SERVO, CR_SERVO, MOTOR }

    private final Kind kind;
    private boolean reversed = false;
    // INIT notes about starting values (from the parts catalog, or the guessed rest/push positions).
    private final List<String> startNotes = new java.util.ArrayList<>();
    boolean inLauncher = false;
    Feeder feeder;
    private ServoFeeder servoFeeder;
    private MotorFeeder motorFeeder;
    private Tunable rest, push, pushSec, returnSec, power, feedSec;
    LongSupplier clock = System::nanoTime;
    // Told each time a feed really starts (team code).
    private final List<Runnable> feedListeners = new java.util.ArrayList<>();
    // Told when a feed starts and when it ends, and whether it ended normally (a Pieces counter).
    private final List<FeedWatcher> feedWatchers = new java.util.ArrayList<>();
    // A feed is going on (started, not yet ended), and when it started (nanoseconds).
    private boolean feeding = false;
    private long feedStartedAt = 0;

    /** Told when a feed starts and when it ends ({@link Pieces} counts a piece out only when it really went). */
    interface FeedWatcher {
        void started();

        /** @param fired true when the feed ended normally; false when it jammed or was stopped part way */
        void ended(boolean fired);
    }

    private Indexer(Kind kind, String device) {
        super(device);
        this.kind = kind;
    }

    /** A servo that pushes a piece in, then comes back. */
    public static Indexer servo(String name) {
        return new Indexer(Kind.SERVO, name);
    }

    /**
     * A servo indexer uses its servo's whole turn: positions 0 to 1 send the servo's full pulse range (the
     * part's, else 500-2500 us) instead of the FTC SDK's normal 600-2400 us. Set {@code rest} and
     * {@code push} again after turning it on: the same position points somewhere else.
     *
     * @return this indexer
     * @throws IllegalStateException on a CR servo or motor indexer
     */
    public Indexer fullRange() {
        if (kind != Kind.SERVO) throw new IllegalStateException("Robot.java: " + name() + ": fullRange() is for Indexer.servo(...)");
        fullRange = true;
        return this;
    }

    /** A continuous-rotation servo that runs for a moment per piece. */
    public static Indexer crServo(String name) {
        return new Indexer(Kind.CR_SERVO, name);
    }

    /** A motor (a roller or belt) that runs for a moment per piece. */
    public static Indexer motor(String name) {
        return new Indexer(Kind.MOTOR, name);
    }

    /** It turns the other way. */
    public Indexer reversed() {
        reversed = true;
        return this;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        switch (kind) {
            case SERVO:
                rest = t.tunable("rest", 0.0, 0.01).range(0, 1);
                push = t.tunable("push", 0.5, 0.01).range(0, 1);
                // pushSec/returnSec not written, with a catalog servo (part(...)): its time from rest to push
                // at the catalog's speed, never under 0.25 s (the piece needs time to leave). A slow torque
                // servo needs more than 0.25 s just to get there.
                startNotes.clear();
                double full = catalogFullMoveSec();
                double move = Double.isNaN(full) ? Double.NaN
                        : Math.max(0.25, Math.round(full * Math.abs(push.get() - rest.get()) * 100) / 100.0);
                boolean pushFromCatalog = !t.has("pushSec") && !Double.isNaN(move);
                boolean returnFromCatalog = !t.has("returnSec") && !Double.isNaN(move);
                pushSec = t.tunable("pushSec", pushFromCatalog ? move : 0.25, 0.01).range(0.02, 2);
                returnSec = t.tunable("returnSec", returnFromCatalog ? move : 0.25, 0.01).range(0.02, 2);
                List<String> cat = new java.util.ArrayList<>();
                if (pushFromCatalog) { cat.add("pushSec " + exact(move)); t.fromCatalog("pushSec"); }
                if (returnFromCatalog) { cat.add("returnSec " + exact(move)); t.fromCatalog("returnSec"); }
                if (!cat.isEmpty()) {
                    startNotes.add(name() + ": starting values from the parts catalog (" + partId + ": " + listed(cat)
                            + "): watch a feed and change them by test");
                }
                if (!t.has("rest") && !t.has("push")) {
                    startNotes.add(name() + ": no positions written: rest 0, push 0.5 are library guesses, not your servo's: "
                            + "find yours with the Servo Finder before the first TeleOp");
                }
                Servo s = devices.get(Servo.class, device);
                if (reversed) s.setDirection(Servo.Direction.REVERSE);
                applyPulse(s);   // .fullRange(): the servo's whole turn (nothing changes without it)
                servoFeeder = new ServoFeeder(name(), s);
                feeder = servoFeeder;
                break;
            case CR_SERVO:
                power = t.tunable("power", 1.0, 0.05).range(-1, 1);
                feedSec = t.tunable("feedSec", 0.4, 0.02).range(0.02, 3);
                CRServo cr = devices.get(CRServo.class, device);
                cr.setDirection(reversed ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD);
                feeder = new TimedCrServo(cr);
                break;
            default:
                power = t.tunable("power", 1.0, 0.05).range(-1, 1);
                feedSec = t.tunable("feedSec", 0.4, 0.02).range(0.02, 3);
                DcMotorEx m = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                        .brake().runWithoutEncoder().done();
                motorFeeder = new MotorFeeder(name(), m);
                feeder = motorFeeder;
                break;
        }
        applyTunables();
    }

    // Live tuning: copies the tuning menu's values in (cheap, so every loop).
    private void applyTunables() {
        if (servoFeeder != null) {
            servoFeeder.positions(rest.get(), push.get()).times(pushSec.get(), returnSec.get());
        }
        if (motorFeeder != null) motorFeeder.power(power.get()).feedTime(feedSec.get());
    }

    @Override
    void start() {
        // The servo goes to its rest position at START (never in INIT: nothing moves before START).
        if (servoFeeder != null) servoFeeder.goToRest();
    }

    // ----------------------------- commands -----------------------------

    /** Feeds one piece now (ignored while busy). A launcher usually calls this, when it is ready. */
    @Override
    public void feedOne() {
        needBuilt(feeder);
        touch();
        feedCounted();
    }

    /**
     * Runs {@code listener} each time a feed starts (a call while busy is not a feed). A feed that
     * starts may still jam: {@link Pieces} counts a piece out only when the feed ends normally (or its
     * ready sensor sees the piece leave).
     */
    public void onFeed(Runnable listener) {
        feedListeners.add(listener);
    }

    void watchFeeds(FeedWatcher watcher) {
        feedWatchers.add(watcher);
    }

    /** True from the start of a feed until it ends (normally, jammed or stopped). */
    public boolean isFeeding() {
        return feeding && !missing;
    }

    // Feeds, and tells the listeners if a feed really started (a call while busy is ignored).
    private void feedCounted() {
        if (missing) return;   // not on the robot (optional): no piece goes anywhere, so nothing is counted
        boolean wasBusy = feeder.isBusy();
        feeder.feedOne();
        if (!wasBusy && feeder.isBusy()) {
            feeding = true;
            feedStartedAt = clock.getAsLong();
            for (Runnable r : feedListeners) r.run();
            for (FeedWatcher w : feedWatchers) w.started();
        }
    }

    // After the feeder's update or a stop: a feed that is no longer busy has ended. It fired unless it
    // jammed, or was stopped part way (a push servo that finished its push did fire).
    private void checkFeedEnded(boolean stopped) {
        if (!feeding || feeder.isBusy()) return;
        feeding = false;
        boolean fired = !feeder.isJammed() && (!stopped || pushedFully());
        for (FeedWatcher w : feedWatchers) w.ended(fired);
    }

    // A push servo stopped on its way back: the push itself was done.
    private boolean pushedFully() {
        return kind == Kind.SERVO && (clock.getAsLong() - feedStartedAt) / 1e9 >= pushSec.get();
    }

    // Stops the feeder; a feed going on ends without firing (see checkFeedEnded).
    private void stopFeeder() {
        feeder.stop();
        checkFeedEnded(true);
    }

    @Override
    public boolean isBusy() {
        return feeder != null && !missing && feeder.isBusy();
    }

    @Override
    public boolean isJammed() {
        return feeder != null && !missing && feeder.isJammed();
    }

    @Override
    public void clearJam() {
        if (feeder != null) feeder.clearJam();
    }

    @Override
    void pause() {
        if (feeder != null) stopFeeder();   // a feed in progress ends (a push servo goes back to rest)
    }

    @Override
    public void update() {
        applyTunables();
        feeder.update();
        checkFeedEnded(false);
    }

    @Override
    public void stop() {
        if (feeder == null) return;
        touch();
        stopFeeder();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (feeder != null) feeder.addTelemetry(t);
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        if (!inLauncher) plan.operator().rb.press(this::feedOne).says("feed one");
    }

    @Override
    List<String> notes() {
        List<String> out = super.notes();
        out.addAll(startNotes);
        return out;
    }

    @Override
    String checkPart(String id) {
        return kind == Kind.MOTOR ? super.checkPart(id) : Parts.servo(id);
    }

    @Override
    boolean drawsLooks() {
        return true;
    }

    // A feeder wheel spins with its CR servo or motor.
    @Override
    String driverFor(Shape s, TunableSection t) {
        if (s.spins() && kind != Kind.SERVO) return (kind == Kind.CR_SERVO ? "crservo=" : "motor=") + device;
        throw new IllegalStateException("Robot.java: " + name() + " can only spin a roller (on a CR servo or a motor)");
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        if (kind == Kind.SERVO) {
            // Where it rests, as the simulated servo sees it (a reversed servo's 0.15 is really 0.85).
            double rest = t.number("rest", 0);
            out.add(partId != null ? fmt("servo.%s = %.2f, part=%s", device, reversed ? 1 - rest : rest, partId) + pulseWords()
                    : fmt("servo.%s = %.2f, part=gobilda-2000-speed", device, reversed ? 1 - rest : rest) + pulseWords() + "   # TODO the real servo");
        }
        else if (kind == Kind.CR_SERVO) out.add("crservo." + device + " =" + (partId != null ? "   # " + partId : ""));
        else if (partId != null) out.add("motor." + device + " = free, part=" + partId);
        else out.add("motor." + device + " = free, part=gobilda-5203-1150   # TODO the real motor");
        lookLines(out, t);
    }

    /** True for a motor feeder (the simulator's flywheel needs to know). */
    boolean isMotor() {
        return kind == Kind.MOTOR;
    }

    /**
     * The feeder as the launcher sees it: the launcher's own feeds are not the driver's commands, so
     * they do not stop an action (see Module.touch), and its update keeps the live tuning.
     */
    Feeder forLauncher() {
        needBuilt(feeder);
        return new Feeder() {
            @Override public void feedOne() { feedCounted(); }
            @Override public boolean isBusy() { return feeder.isBusy(); }
            @Override public boolean isJammed() { return feeder.isJammed(); }
            @Override public void clearJam() { feeder.clearJam(); }
            @Override public void update() { Indexer.this.update(); }
            @Override public void stop() { stopFeeder(); }
            @Override public String name() { return Indexer.this.name(); }
            @Override public void addTelemetry(RobotTelemetry t) { feeder.addTelemetry(t); }
        };
    }

    // A CR servo that runs for feedSec per piece, then stops.
    private final class TimedCrServo implements Feeder {
        private final CRServo servo;
        private double endAt = -1;    // seconds; -1 = not feeding

        TimedCrServo(CRServo servo) {
            this.servo = servo;
        }

        private double now() {
            return clock.getAsLong() / 1e9;
        }

        @Override public void feedOne() {
            if (isBusy()) return;
            endAt = now() + feedSec.get();
            servo.setPower(power.get());
        }

        @Override public boolean isBusy() {
            return endAt >= 0;
        }

        @Override public void update() {
            if (endAt >= 0 && now() >= endAt) stop();
        }

        @Override public void stop() {
            endAt = -1;
            servo.setPower(0);
        }

        @Override public String name() {
            return Indexer.this.name();
        }

        @Override public void addTelemetry(RobotTelemetry t) {
            t.debug(name(), isBusy() ? "feeding" : "idle");
        }
    }

    @Override
    boolean makesDefaultShape() {
        return false;   // it draws nothing of its own: a shape cannot ride on it without looks(...)
    }
}
