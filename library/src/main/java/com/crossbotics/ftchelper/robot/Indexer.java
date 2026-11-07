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
 * <p>Two arms that push together (one on each side, pinching behind the piece):
 * {@code Indexer.servo("feeder_left").alsoServoReversed("feeder_right")}. They share one Tunables section (the first
 * name's): the second arm mirrors the first; {@code alsoTrim} lines it up.
 *
 * <p>Tunables: {@code Indexer.servo}: {@code rest}, {@code push}, {@code pushSec}, {@code returnSec} (and
 * {@code alsoTrim} with a second servo).
 * {@code Indexer.crServo} and {@code Indexer.motor}: {@code power} and {@code feedSec} (how long it
 * runs per piece). Added on its own (no launcher), its suggested button is operator RB: feed one.
 *
 * <p>A feed wheel ({@code Launcher.feedWheel(Indexer.crServo("feeder_wheel"))}): a CR servo or motor that
 * moves the pieces up to the pusher (the launcher's {@code feeder}). Its Tunables: {@code power},
 * {@code alwaysOn} (false: it runs only while firing), {@code stageSec} (it runs this long before the
 * push, 0.2) and {@code stopSec} (it stops this long after the push starts, 0.3; below 0 = before the push).
 *
 * Docs: docs/modules/robot.md (section "Indexer")
 */
public final class Indexer extends Module<Indexer> implements Feeder {
    private enum Kind { SERVO, CR_SERVO, MOTOR }

    private final Kind kind;
    private boolean reversed = false;
    // alsoServo: a second push servo that moves with the first, and whether it turns the same way (else mirrored).
    private String alsoServo = null;
    private boolean alsoSameWay = false;
    private Tunable alsoTrim;
    // INIT notes about starting values (from the parts catalog, or the guessed rest/push positions).
    private final List<String> startNotes = new java.util.ArrayList<>();
    boolean inLauncher = false;
    // A launcher's feed wheel (Launcher.feedWheel): it stages the pieces; the launcher runs it (runWheel).
    boolean feedWheel = false;
    private boolean alwaysOn = false;
    private Tunable stageSec, stopSec;
    private CRServo wheelCr;
    private DcMotorEx wheelMotor;
    Feeder feeder;
    private ServoFeeder servoFeeder;
    private MotorFeeder motorFeeder;
    private Tunable rest, push, pushSec, returnSec, power, feedSec, pushStep;
    // adjustPush(...): steps of pushStep added to the push position, further from rest (+) or nearer (-).
    private int pushSteps = 0;
    // A catalog servo: its time for a full turn (NaN = none), and whether pushSec/returnSec came from it.
    private double fullMoveSec = Double.NaN;
    private boolean pushSecFromCatalog = false, returnSecFromCatalog = false;
    LongSupplier clock = com.crossbotics.ftchelper.core.Time::nanos;
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
        if (alsoServo == null && t.has("alsoTrim")) {
            throw new IllegalStateException("Tunables." + name() + ".alsoTrim needs a second servo: add .alsoServoReversed(name) "
                    + "to " + name() + " in Robot.java (a servo indexer), or delete the alsoTrim line");
        }
        switch (kind) {
            case SERVO:
                rest = t.tunable("rest", 0.0, 0.01).range(0, 1);
                push = t.tunable("push", 0.5, 0.01).range(0, 1);
                pushStep = t.tunable("pushStep", 0.01, 0.005).range(0.001, 0.1);
                // pushSec/returnSec not written, with a catalog servo (part(...)): its time from rest to push
                // at the catalog's speed, never under 0.25 s (the piece needs time to leave). A slow torque
                // servo needs more than 0.25 s just to get there.
                startNotes.clear();
                double full = catalogFullMoveSec();
                double move = Double.isNaN(full) ? Double.NaN
                        : Math.max(0.25, Math.round(full * Math.abs(push.get() - rest.get()) * 100) / 100.0);
                boolean pushFromCatalog = !t.has("pushSec") && !Double.isNaN(move);
                boolean returnFromCatalog = !t.has("returnSec") && !Double.isNaN(move);
                fullMoveSec = full;
                pushSecFromCatalog = pushFromCatalog;
                returnSecFromCatalog = returnFromCatalog;
                pushSec = t.tunable("pushSec", pushFromCatalog ? move : 0.25, 0.01).range(0.02, 2);
                returnSec = t.tunable("returnSec", returnFromCatalog ? move : 0.25, 0.01).range(0.02, 2);
                List<String> cat = new java.util.ArrayList<>();
                if (pushFromCatalog) { cat.add("pushSec " + exact(move)); t.fromCatalog("pushSec"); }
                if (returnFromCatalog) { cat.add("returnSec " + exact(move)); t.fromCatalog("returnSec"); }
                if (!cat.isEmpty()) {
                    startNotes.add(defaultsNote(partId, cat, "Watch a feed and change them by test"));
                }
                if (Double.isNaN(move) && (t.isMarked("pushSec") || t.isMarked("returnSec"))) {
                    startNotes.add(generalServoNote(t.isMarked("pushSec") ? "pushSec" : "returnSec", 0.25));
                    t.fromCatalog("pushSec", "returnSec");
                }
                if (!t.has("rest") && !t.has("push")) {
                    startNotes.add(name() + ": no positions written: rest 0, push 0.5 are library guesses, not your servo's: "
                            + "find yours with the Servo Finder before the first TeleOp");
                }
                Servo s = devices.get(Servo.class, device);
                if (reversed) s.setDirection(Servo.Direction.REVERSE);
                applyPulse(s);   // .fullRange(): the servo's whole turn (nothing changes without it)
                servoFeeder = new ServoFeeder(name(), s);
                if (alsoServo != null) {
                    Servo o = devices.get(Servo.class, alsoServo);
                    if (alsoReversed()) o.setDirection(Servo.Direction.REVERSE);
                    applyPulse(o, alsoServo);
                    alsoTrim = t.tunable("alsoTrim", 0, 0.005).range(-0.2, 0.2);
                    servoFeeder.withServo(o, alsoTrim::get);
                }
                feeder = servoFeeder;
                break;
            case CR_SERVO:
                power = t.tunable("power", 1.0, 0.05).range(-1, 1);
                // A feed wheel runs as long as the launcher says, not feedSec: no feedSec in its menu.
                if (!feedWheel) feedSec = t.tunable("feedSec", 0.4, 0.02).range(0.02, 3);
                CRServo cr = devices.get(CRServo.class, device);
                cr.setDirection(reversed ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD);
                feeder = new TimedCrServo(cr);
                wheelCr = cr;
                buildWheel(t);
                break;
            default:
                power = t.tunable("power", 1.0, 0.05).range(-1, 1);
                // A feed wheel runs as long as the launcher says, not feedSec: no feedSec in its menu.
                if (!feedWheel) feedSec = t.tunable("feedSec", 0.4, 0.02).range(0.02, 3);
                DcMotorEx m = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                        .brake().runWithoutEncoder().done();
                motorFeeder = new MotorFeeder(name(), m);
                feeder = motorFeeder;
                wheelMotor = m;
                buildWheel(t);
                break;
        }
        applyTunables();
    }

    // A feed wheel's own numbers (only for Launcher.feedWheel: an indexer that feeds has no use for them).
    private void buildWheel(TunableSection t) {
        if (!feedWheel) return;
        alwaysOn = t.flag("alwaysOn", false);
        stageSec = t.tunable("stageSec", 0.2, 0.02).range(0, 2);
        stopSec = t.tunable("stopSec", 0.3, 0.02).range(-2, 2);
    }

    /** A feed wheel: true = it runs all the time the flywheel is on; false = only while firing. */
    boolean wheelAlwaysOn() {
        return alwaysOn;
    }

    /** A feed wheel: seconds it runs before the push (Tunables stageSec). */
    double stageSec() {
        return stageSec == null ? 0 : stageSec.get();
    }

    /** A feed wheel: seconds after the push starts that it stops (Tunables stopSec; below 0 = before the push). */
    double stopSec() {
        return stopSec == null ? 0 : stopSec.get();
    }

    /** A feed wheel: its power when on (Tunables power). */
    double wheelPower() {
        return power.get();
    }

    /** A feed wheel: sets its power (0 = stop). The launcher calls it on a change only. */
    void runWheel(double p) {
        if (wheelCr != null) wheelCr.setPower(p);
        else if (wheelMotor != null) wheelMotor.setPower(p);
    }

    /** True for a CR servo or a motor (they can be a feed wheel), false for a push servo. */
    boolean spins() {
        return kind != Kind.SERVO;
    }

    // Live tuning: copies the tuning menu's values in (cheap, so every loop).
    private void applyTunables() {
        if (servoFeeder != null) {
            double p = pushNow();
            // A longer push (adjustPush) with times from the catalog: the servo needs longer to get there.
            double travel = Double.isNaN(fullMoveSec) ? Double.NaN : Math.max(0.25, fullMoveSec * Math.abs(p - rest.get()));
            double up = pushSecFromCatalog && !Double.isNaN(travel) ? Math.max(pushSec.get(), travel) : pushSec.get();
            double down = returnSecFromCatalog && !Double.isNaN(travel) ? Math.max(returnSec.get(), travel) : returnSec.get();
            servoFeeder.positions(rest.get(), p).times(up, down);
        }
        if (motorFeeder != null && feedSec != null) motorFeeder.power(power.get()).feedTime(feedSec.get());
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
        if (feedWheel) return;   // a launcher's feed wheel: the launcher runs it (fire the launcher instead)
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
        if (feeder == null || feedWheel) return;   // a feed wheel: the launcher runs and stops it
        touch();
        stopFeeder();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (feeder != null) feeder.addTelemetry(t);
        if (!inLauncher) addPushTrim(t);
    }

    // The adjustPush(...) trim, when there is one (a launcher's feeder: the launcher shows it, near its speed trim).
    void addPushTrim(RobotTelemetry t) {
        if (pushSteps != 0 && push != null) {
            t.line(name() + " push", "%.3f (%+d x %s; to keep it: Tunables %s push = %.3f)", pushNow(), pushSteps,
                    exact(pushStep.get()), name(), pushNow());
        }
    }

    /**
     * A push servo: moves the push position by {@code steps} x {@code Tunables pushStep} (0.01): + = further
     * from rest (a longer push), - = nearer rest. For a D-pad during TeleOp, when the push stops getting the
     * piece all the way (a servo horn that slips, a worn pad): {@code g1.dpadRight.press(() ->
     * robot.launcher.feeder().adjustPush(+1))}. Kept until the program ends; the Tunables value is not
     * changed (the screen shows the number to copy into Tunables). Never past rest, never outside 0 to 1.
     * Ignored for a CR servo or motor feeder.
     */
    public void adjustPush(int steps) {
        // Not touch(): a trim is not taking the feeder away from an action.
        if (kind != Kind.SERVO || push == null) return;
        int next = pushSteps + steps;
        double dir = Math.signum(push.get() - rest.get());
        if (dir == 0) dir = 1;
        double p = push.get() + dir * next * pushStep.get();
        // Not past rest (the push would go the wrong way) and not outside the servo's range.
        if (p < 0 || p > 1 || Math.signum(p - rest.get()) != dir) return;
        pushSteps = next;
    }

    /** The push position in use now: Tunables push plus the {@link #adjustPush} trim. */
    public double pushNow() {
        if (push == null) return Double.NaN;
        double dir = Math.signum(push.get() - rest.get());
        if (dir == 0) dir = 1;
        double p = push.get() + dir * pushSteps * pushStep.get();
        // Checked again here (not only in adjustPush): pushStep, rest or push may change in the tuning menu
        // after the trim. Outside 0 to 1, or past rest: the Tunables push alone.
        if (p < 0 || p > 1 || Math.signum(p - rest.get()) != dir) return push.get();
        return p;
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        if (!inLauncher) plan.operator().rb.press(this::feedOne).says("feed one");
    }

    // The simulator's stand-in for a MEASURE field: a servo indexer's rest 0.0 and push 0.5 (the defaults).
    @Override
    double simStandIn(String field, TunableSection t) {
        if (field.equals("rest")) return 0.0;
        if (field.equals("push")) return 0.5;
        return super.simStandIn(field, t);
    }

    @Override
    String toolFor(String field) {
        if (field.equals("rest") || field.equals("push")) return "the Servo Finder, docs/guides/finders.md#servo-finder-ends-and-positions";
        return "test it: watch a feed";
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

    // A feeder wheel spins with its CR servo or motor. A push servo swings an arm shape between "rest" and
    // "push" (the simulator turns it with the servo), the same way a Positioner's arm moves.
    @Override
    String driverFor(Shape s, TunableSection t) {
        if (s.spins() && kind != Kind.SERVO) return (kind == Kind.CR_SERVO ? "crservo=" : "motor=") + device;
        if (kind == Kind.SERVO && s.type.equals("arm")) return armWords(s, t);
        throw new IllegalStateException("Robot.java: " + name() + (kind == Kind.SERVO
                ? " is a push servo: it swings an arm shape (Shape.arm()...angle(\"rest\", 0).angle(\"push\", 40)), not a " + s.type
                : " can only spin a roller (on a CR servo or a motor)"));
    }

    // "servo=feeder_arm, pos0=..., pos1=...": the arm's angle at servo position 0 and 1, from its angles at
    // rest and push (Tunables.java), as the simulated servo sees them (reversed() swaps 0 and 1).
    private String armWords(Shape s, TunableSection t) {
        if (!s.angles.containsKey("rest") || !s.angles.containsKey("push") || s.angles.size() != 2) {
            throw new IllegalStateException("Robot.java: " + name() + "'s arm needs its angle at rest and at push: "
                    + ".angle(\"rest\", 0).angle(\"push\", 40) (degrees: 0 = level forward, +90 = straight up)");
        }
        double rest = t.number("rest", 0), push = t.number("push", 0.5);
        double vr = reversed ? 1 - rest : rest, vp = reversed ? 1 - push : push;
        if (vr == vp) {
            throw new IllegalStateException("Robot.java: " + name() + ": rest and push are the same servo position, so the arm's angles can't be worked out");
        }
        double perUnit = (s.angles.get("push") - s.angles.get("rest")) / (vp - vr);
        double pos0 = s.angles.get("rest") - perUnit * vr;
        return "servo=" + device + ", pos0=" + Shape.n(pos0) + ", pos1=" + Shape.n(pos0 + perUnit);
    }

    /**
     * A second push servo that moves with this one, mirrored: two arms, one on each side, that pinch
     * together behind the piece and push it in. Both go to the same {@code rest} and {@code push}; the
     * second turns the other way (it is mounted facing the first). If the two do not quite meet, set
     * {@code alsoTrim} (raw position added for the second one). For {@code Indexer.servo} only.
     *
     * <pre>{@code
     * .feeder(Indexer.servo("feeder_left").alsoServoReversed("feeder_right").part("gobilda-2000-torque"))
     * }</pre>
     *
     * @param name the second servo's configuration name
     * @return this indexer
     * @throws IllegalStateException on a CR servo or motor indexer
     */
    public Indexer alsoServoReversed(String name) {
        if (kind != Kind.SERVO) throw new IllegalStateException("Robot.java: " + name() + ": a second servo (alsoServo...) is for Indexer.servo(...)");
        secondServo(name, false);
        return this;
    }

    /**
     * Like {@link #alsoServoReversed}, for a second servo mounted facing the same way (it turns the same way).
     *
     * @param name the second servo's configuration name
     * @return this indexer
     */
    public Indexer alsoServo(String name) {
        if (kind != Kind.SERVO) throw new IllegalStateException("Robot.java: " + name() + ": a second servo (alsoServo...) is for Indexer.servo(...)");
        secondServo(name, true);
        return this;
    }

    private void secondServo(String name, boolean sameWay) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Robot.java: " + name() + ".alsoServo needs the servo's name");
        if (name.equals(device)) throw new IllegalArgumentException("Robot.java: " + name() + ".alsoServo(\"" + name + "\") is its first servo");
        if (alsoServo != null) {
            throw new IllegalStateException("Robot.java: " + name() + " already has a second servo (\"" + alsoServo
                    + "\"); a part takes two servos at most");
        }
        alsoServo = name;
        alsoSameWay = sameWay;
    }

    /** The second servo's configuration name ({@code alsoServo...}), or null if it has one servo. */
    public String secondServoName() {
        return alsoServo;
    }

    /** True if {@code reversed()} is set in Robot.java (positions 0 and 1 swap ends). */
    public boolean isReversed() {
        return reversed;
    }

    /** True if the second servo mirrors the first ({@code alsoServoReversed}). */
    public boolean secondServoMirrored() {
        return alsoServo != null && !alsoSameWay;
    }

    // The second servo's direction: mirrored (the other way from the first), or the same way.
    private boolean alsoReversed() {
        return alsoSameWay ? reversed : !reversed;
    }

    @Override
    List<String> deviceNames() {
        if (alsoServo == null) return super.deviceNames();
        return java.util.Arrays.asList(device, alsoServo);
    }

    // ", feederPush=0.40": where a push servo goes to push, as the simulated servo sees it (reversed()
    // swaps 0 and 1). The simulator counts a push once the arm is half way there, so a short push (0.1)
    // fires too. Empty for a CR servo or motor feeder.
    String pushWords() {
        if (kind != Kind.SERVO || simSection == null) return "";
        double push = simSection.number("push", 0.5);
        return fmt(", feederPush=%.2f", reversed ? 1 - push : push);
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        if (kind == Kind.SERVO) {
            // Where it rests, as the simulated servo sees it (a reversed servo's 0.15 is really 0.85).
            double rest = t.number("rest", 0);
            out.add(servoLine(device, reversed ? 1 - rest : rest));
            if (alsoServo != null) {
                double r2 = Math.max(0, Math.min(1, rest + t.number("alsoTrim", 0)));
                out.add(servoLine(alsoServo, alsoReversed() ? 1 - r2 : r2));
            }
        }
        else if (kind == Kind.CR_SERVO) out.add("crservo." + device + " =" + (partId != null ? "   # " + partId : ""));
        else if (partId != null) out.add("motor." + device + " = free, " + motorWords(partId, "gobilda-5203-1150") + simTodo(partId));
        else out.add("motor." + device + " = free, part=gobilda-5203-1150   # TODO the real motor");
        lookLines(out, t);
    }

    // One servo's robot-file line (the simulator).
    private String servoLine(String name, double rest) {
        return partId != null ? fmt("servo.%s = %.2f, %s", name, rest, servoWords(partId, "gobilda-2000-speed")) + pulseWords() + simTodo(partId)
                : fmt("servo.%s = %.2f, part=gobilda-2000-speed", name, rest) + pulseWords() + "   # TODO the real servo";
    }

    /** True for a servo that pushes and comes back (not a CR servo or motor that runs). */
    public boolean isServo() {
        return kind == Kind.SERVO;
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
