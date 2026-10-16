package io.github.jasoncross.ftchelper.launcher;

import io.github.jasoncross.ftchelper.auto.AutoStep;
import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.mechanism.PositionServo;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Joins a flywheel, a feeder, and optionally a hood, a shot table and any "ready" checks into one
 * shooter: spin up, wait until everything is ready, feed, repeat. It never stops the loop, so it
 * works the same in TeleOp and in autonomous. (Last season's FiringController, made general.)
 *
 * <pre>{@code
 * Launcher launcher = new Launcher(flywheel, feeder)
 *         .hood(hood)                              // a PositionServo with degrees(...)
 *         .table(pollenTable)                      // distance -> rpm + hood
 *         .requireReady("Turret", turret::isAimed) // add any check you like
 *         .shotInterval(0.25);                     // at least 0.25 s between pieces
 *
 * launcher.setDistance(40);                        // or launcher.setRpm(3200)
 * controls.bindPress(Pad.G2, Btn.RB, () -> launcher.shoot(1))
 *         .bindPress(Pad.G2, Btn.LB, () -> launcher.shoot(4))
 *         .bindPress(Pad.G2, Btn.B, launcher::stopShooting);
 * routine.step("Shoot 3", launcher.shootStep(3));
 * // every loop: launcher.update();   (it updates the flywheel, feeder and hood for you)
 * }</pre>
 *
 * <p>If it is not ready within {@link #readyTimeout}, it gives up the remaining shots and
 * {@link #whyNotReady()} says what was missing. That is your first debug clue.
 *
 * Docs: docs/modules/launcher.md
 */
public class Launcher implements Subsystem {

    /** What the launcher is doing. */
    public enum State { OFF, SPINNING, WAITING_READY, FEEDING }

    private final Flywheel flywheel;
    private final Feeder feeder;
    private PositionServo hood = null;
    private ShotTable table = null;
    private final List<String> checkNames = new ArrayList<>();
    private final List<BooleanSupplier> checks = new ArrayList<>();
    private LongSupplier clockNanos = System::nanoTime;

    private double shotIntervalSec = 0.25;
    private double readyTimeoutSec = 3.0;
    private double idleRpm = 0;

    private double shotRpm = 0;
    private boolean spinning = false;
    private int shotsLeft = 0;
    private int fired = 0;
    private double nextFeedAt = 0;
    private double waitingSince = Double.NaN;
    private String lastProblem = "";

    public Launcher(Flywheel flywheel, Feeder feeder) {
        this.flywheel = flywheel;
        this.feeder = feeder;
    }

    Launcher withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** A hood servo that has {@code degrees(...)} set. The table's hood angle goes to it. */
    public Launcher hood(PositionServo hoodServo) {
        hood = hoodServo;
        return this;
    }

    /** A shot table, used by {@link #setDistance}. */
    public Launcher table(ShotTable shotTable) {
        table = shotTable;
        return this;
    }

    /** Another condition that must be true before each piece is fed (aim, sensor, anything). */
    public Launcher requireReady(String checkName, BooleanSupplier check) {
        checkNames.add(checkName);
        checks.add(check);
        return this;
    }

    /** Least time between two pieces, seconds. Default 0.25. */
    public Launcher shotInterval(double seconds) {
        shotIntervalSec = Math.abs(seconds);
        return this;
    }

    /** Give up the remaining shots if not ready within this many seconds. Default 3. */
    public Launcher readyTimeout(double seconds) {
        readyTimeoutSec = Math.abs(seconds);
        return this;
    }

    /** Speed to keep when not shooting (0 = stop). A low idle speed makes spin-up faster. */
    public Launcher idleRpm(double rpm) {
        idleRpm = rpm;
        return this;
    }

    // ---------------------------------------------------------------------------------------

    /** Sets the shot speed by hand. */
    public void setRpm(double rpm) {
        shotRpm = rpm;
    }

    /** Sets the shot from the table (speed, and hood angle if there is a hood). */
    public void setDistance(double distance) {
        if (table == null) throw new IllegalStateException("Launcher.setDistance needs a table(...)");
        ShotTable.Shot shot = table.lookup(distance);
        shotRpm = shot.rpm;
        if (hood != null) hood.setDegrees(shot.hoodDeg);
    }

    /** Spins the flywheel up to the shot speed and keeps it there. */
    public void spinUp() {
        spinning = true;
    }

    /** Goes back to the idle speed (cancels any shots). */
    public void idle() {
        spinning = false;
        shotsLeft = 0;
    }

    /** Shoots {@code count} pieces (spins up first if needed). Adds to any shots still waiting. */
    public void shoot(int count) {
        spinUp();
        if (shotsLeft == 0) waitingSince = Double.NaN;
        shotsLeft += Math.max(0, count);
        lastProblem = "";
    }

    /** Cancels the shots still waiting (keeps spinning). */
    public void stopShooting() {
        shotsLeft = 0;
    }

    @Override
    public void stop() {
        spinning = false;
        shotsLeft = 0;
        feeder.stop();
        flywheel.stop();
    }

    @Override
    public void update() {
        double now = clockNanos.getAsLong() / 1e9;
        flywheel.setRpm(spinning ? shotRpm : idleRpm);
        flywheel.update();
        feeder.update();
        if (hood != null) hood.update();

        if (feeder.isJammed()) {
            shotsLeft = 0;
            lastProblem = "feeder jammed";
            return;
        }
        if (shotsLeft == 0 || feeder.isBusy() || now < nextFeedAt) return;

        if (isReady()) {
            feeder.feedOne();
            shotsLeft--;
            fired++;
            nextFeedAt = now + shotIntervalSec;
            waitingSince = Double.NaN;
        } else {
            if (Double.isNaN(waitingSince)) waitingSince = now;
            if (now - waitingSince > readyTimeoutSec) {
                lastProblem = "gave up: " + whyNotReady();
                shotsLeft = 0;
                waitingSince = Double.NaN;
            }
        }
    }

    // ---------------------------------------------------------------------------------------

    /** True when the flywheel is at speed, the hood has arrived, and every check passes. */
    public boolean isReady() {
        return whyNotReady().isEmpty();
    }

    /** What is not ready yet, e.g. "flywheel, Turret" ("" when ready). */
    public String whyNotReady() {
        StringBuilder sb = new StringBuilder();
        if (!spinning) sb.append("not spinning");
        else if (!flywheel.isReady()) sb.append("flywheel");
        if (hood != null && !hood.isDone()) append(sb, "hood");
        for (int i = 0; i < checks.size(); i++) if (!checks.get(i).getAsBoolean()) append(sb, checkNames.get(i));
        return sb.toString();
    }

    public State getState() {
        if (feeder.isBusy()) return State.FEEDING;
        if (shotsLeft > 0) return State.WAITING_READY;
        return spinning ? State.SPINNING : State.OFF;
    }

    /** True while shots are waiting or a piece is being fed. */
    public boolean isShooting() { return shotsLeft > 0 || feeder.isBusy(); }
    public int getShotsLeft() { return shotsLeft; }
    /** Pieces fed since the OpMode started. */
    public int getShotsFired() { return fired; }
    /** The last problem (e.g. why it gave up), or "". */
    public String getLastProblem() { return lastProblem; }
    public Flywheel getFlywheel() { return flywheel; }

    /** A step: spin up and wait until ready (or the ready timeout). */
    public AutoStep spinUpStep() {
        return ctx -> {
            spinUp();
            ctx.waitUntil(this::isReady, readyTimeoutSec);
        };
    }

    /** A step: shoot {@code count} pieces and wait until done (or it gives up). */
    public AutoStep shootStep(int count) {
        return ctx -> {
            shoot(count);
            ctx.waitUntil(() -> !isShooting(), count * (shotIntervalSec + readyTimeoutSec) + 2);
            stopShooting();
        };
    }

    @Override public String name() { return "Launcher"; }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!lastProblem.isEmpty()) t.warn("Launcher " + lastProblem);
        t.line("Launcher", "%s  left %d%s", getState(), shotsLeft, isReady() ? "  READY" : "");
        flywheel.addTelemetry(t);
        t.debug("Launcher waits for", whyNotReady().isEmpty() ? "-" : whyNotReady());
        t.debug("Launcher fired", fired);
        feeder.addTelemetry(t);
        if (hood != null) hood.addTelemetry(t);
    }

    private static void append(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(text);
    }
}
