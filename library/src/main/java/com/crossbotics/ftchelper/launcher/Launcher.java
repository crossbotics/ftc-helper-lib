package com.crossbotics.ftchelper.launcher;

import com.crossbotics.ftchelper.auto.AutoStep;
import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.mechanism.AngleDevice;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
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
 * <p>How it works: {@link #shoot} only adds to a count of "shots still to fire". Each loop,
 * {@link #update()} feeds ONE piece if everything is ready, and otherwise just checks again next loop.
 * No waiting anywhere (see docs/guides/non-blocking.md). Tutorial 17 builds a shooter step by step.
 *
 * Docs: docs/modules/launcher.md
 */
public class Launcher implements Subsystem {
    // "implements Subsystem": it has update/stop/addTelemetry/name like every mechanism helper.

    /**
     * What the launcher is doing (for telemetry). An "enum" is a fixed list of named choices.
     * OFF = fully off after stop(); IDLE = at the idle speed; SPINNING = at shot speed, no shots waiting;
     * WAITING_READY = shots waiting for everything to be ready; FEEDING = a piece is being pushed in now.
     */
    public enum State { OFF, IDLE, SPINNING, WAITING_READY, FEEDING }

    /**
     * How strict the launcher is before it feeds a piece.
     * CAREFUL = every piece waits until the wheel is at speed (the band, then the settle time) and every
     * check passes: one shot that must score. QUICK = the first piece of a volley waits like CAREFUL; the
     * next ones go in a looser speed band ({@link #quickBand}), or after {@link #quickMaxWait} at most: a
     * volley with time to spare. SPRAY = no wait for the speed or the checks: time is nearly out, get
     * everything out (the wheel must still be spinning).
     */
    public enum ShotMode { CAREFUL, QUICK, SPRAY }

    private final Flywheel flywheel;
    private final Feeder feeder;
    private AngleDevice hood = null;   // null = no hood
    private ShotTable table = null;      // null = no table (use setRpm)
    // The extra "ready" checks: a name for telemetry, and a yes/no question asked every loop.
    // The two lists go together: checkNames.get(i) is the name of checks.get(i).
    private final List<String> checkNames = new ArrayList<>();
    private final List<BooleanSupplier> checks = new ArrayList<>();
    // Where we get the time (nanoseconds). Tests swap in a fake clock.
    private LongSupplier clockNanos = System::nanoTime;

    private double shotIntervalSec = 0.25;
    private double readyTimeoutSec = 3.0;
    private double idleRpm = 0;

    private double shotRpm = 0;
    private double hoodOffsetDeg = 0;    // hoodOffset(): degrees added to the table's hood angle
    private double hoodDeadbandDeg = 0.25;   // hoodDeadband(): smaller hood changes are not sent
    private double speedScale = 1;       // speedScale(): the shot speed x this (1 = as set)
    private boolean spinning = false;    // true = at shot speed; false = idle speed (or off)
    private boolean off = true;          // stop() keeps it fully off (even the idle speed) until started again
    private int shotsLeft = 0;
    private int fired = 0;
    private double nextFeedAt = 0;       // the earliest time (seconds) the next piece may be fed
    private double waitingSince = Double.NaN;   // when it started waiting for "ready"; NaN = not waiting
    private String lastProblem = "";
    private ShotMode mode = ShotMode.CAREFUL;
    private double quickBandRpm = Double.NaN;   // NaN = 3 x the flywheel's ready band
    private double quickMaxWaitSec = 0.3;
    // A piece asked for within this many seconds after the last feed belongs to the same volley.
    private double volleyGapSec = 1.0;
    private double lastFeedAt = Double.NEGATIVE_INFINITY;
    private final ShotStats stats = new ShotStats();
    private double problemAt = 0;        // when lastProblem happened (seconds), for the warning's few seconds

    /** SPRAY feeds only when the wheel turns at least this share of its target speed (0.5). */
    public static final double SPRAY_MIN_SHARE = 0.5;

    /** How long a problem ("gave up: turret aimed") stays as a warning on the Driver Station, seconds. */
    public static final double PROBLEM_SHOW_SEC = 5;

    /**
     * Makes a launcher from a flywheel and a feeder you already made. It starts OFF.
     * Put only the launcher in your loop (or Subsystems group): it updates both for you.
     */
    public Launcher(Flywheel flywheel, Feeder feeder) {
        this.flywheel = flywheel;
        this.feeder = feeder;
    }

    /** For tests: use a pretend clock so tests do not wait for real time. */
    Launcher withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /**
     * A hood: a {@link com.crossbotics.ftchelper.mechanism.PositionServo} with {@code degrees(...)} set,
     * or any {@link AngleDevice} (the five-files {@code Hood}, a motor, your own). The table's hood angle goes to it (only from a table
     * with hood angles, see {@link ShotTable#hasHood()}), and the launcher waits for it to arrive before
     * feeding ("hood" in {@link #whyNotReady()}).
     */
    public Launcher hood(AngleDevice hoodServo) {
        hood = hoodServo;
        return this;
    }

    /** A shot table, used by {@link #setDistance}. */
    public Launcher table(ShotTable shotTable) {
        table = shotTable;
        return this;
    }

    /**
     * Another condition that must be true before each piece is fed (aim, sensor, anything).
     * {@code checkName} is shown by {@link #whyNotReady()} while it is false.
     * Example: {@code requireReady("Turret", turret::isAimed)}: "turret::isAimed" hands over the
     * question "is the turret aimed?" so the launcher can ask it every loop.
     */
    public Launcher requireReady(String checkName, BooleanSupplier check) {
        checkNames.add(checkName);
        checks.add(check);
        return this;
    }

    /**
     * Removes every {@link #requireReady} check. The Shot Tuner does this for a robot in five files:
     * there you aim by hand, so a "turret aimed" check must not stop each shot.
     */
    public Launcher clearReadyChecks() {
        checkNames.clear();
        checks.clear();
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

    /**
     * The hood moves only when the table's angle changes by more than this (degrees, default 0.25). A
     * camera's distance jitters a little every picture; without this the hood would be sent a new angle
     * every loop, and a servo that is always "moving" never lets the launcher feed.
     *
     * @param degrees 0 or more, in the hood's scale (a hood in servo positions: e.g. 0.005); 0 = send every change
     */
    public Launcher hoodDeadband(double degrees) {
        hoodDeadbandDeg = Double.isNaN(degrees) ? 0.25 : Math.abs(degrees);
        return this;
    }

    /** How strict it is before each feed (see {@link ShotMode}). Default CAREFUL. Takes effect at the next piece. */
    public Launcher shotMode(ShotMode shotMode) {
        mode = shotMode == null ? ShotMode.CAREFUL : shotMode;
        return this;
    }

    /** The shot mode in use. */
    public ShotMode getShotMode() {
        return mode;
    }

    /**
     * QUICK: after the volley's first piece, feed when the wheel is within this many RPM of its speed.
     * Default: 3 x the flywheel's ready band (its tolerance).
     */
    public Launcher quickBand(double rpm) {
        quickBandRpm = Double.isNaN(rpm) ? Double.NaN : Math.abs(rpm);
        return this;
    }

    /** QUICK: after the volley's first piece, feed after waiting this long at most, seconds. Default 0.3. */
    public Launcher quickMaxWait(double seconds) {
        quickMaxWaitSec = Math.abs(seconds);
        return this;
    }

    /** The least time between two pieces, seconds ({@link #shotInterval}). */
    public double getShotInterval() {
        return shotIntervalSec;
    }

    /** The QUICK wait limit, seconds ({@link #quickMaxWait}). */
    public double getQuickMaxWait() {
        return quickMaxWaitSec;
    }

    /** The QUICK band in use, RPM. */
    public double getQuickBand() {
        return Double.isNaN(quickBandRpm) ? 3 * flywheel.getTolerance() : quickBandRpm;
    }

    /** Speed to keep when not shooting, in RPM (0 = stop). A low idle speed makes spin-up faster. */
    public Launcher idleRpm(double rpm) {
        idleRpm = rpm;
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Commands (quick: safe to call from buttons; update() does the work)
    // ---------------------------------------------------------------------------------------

    /** Sets the shot speed by hand, in RPM. */
    public void setRpm(double rpm) {
        shotRpm = rpm;
    }

    /**
     * Sets the shot from the table: the speed, and the hood angle (plus {@link #hoodOffset}) when there is
     * a hood and the table has hood angles. Distance in the table's unit (inches in the examples). Safe to
     * call every loop, e.g. with the camera's distance: the hood is only sent a new angle when it changes
     * by more than {@link #hoodDeadband} (0.25 deg).
     */
    public void setDistance(double distance) {
        if (table == null) throw new IllegalStateException("Launcher.setDistance(...) has no shot table: add .table(...) where you make the Launcher (docs/modules/launcher.md)");
        ShotTable.Shot shot = table.lookup(distance);
        shotRpm = shot.rpm;
        // A table with no hood column leaves the hood where it is (it would otherwise go to 0 degrees).
        if (hood != null && table.hasHood()) {
            double want = shot.hoodDeg + hoodOffsetDeg;
            // Where the hood is going now (after its limits), NaN if it was never sent anywhere.
            double going = hood.getTargetDegrees();
            if (Double.isNaN(going) || Math.abs(want - going) > hoodDeadbandDeg) hood.setDegrees(want);
        }
    }

    /**
     * Degrees added to the table's hood angle by {@link #setDistance} (a hood trim: +1 = every shot 1
     * degree more). 0 = as the table says (the default). Takes effect at the next setDistance.
     *
     * @param degrees e.g. +1 or -0.5
     */
    public void hoodOffset(double degrees) {
        hoodOffsetDeg = Double.isNaN(degrees) ? 0 : degrees;
    }

    /** The degrees added to the table's hood angle ({@link #hoodOffset}); 0 = none. */
    public double getHoodOffset() {
        return hoodOffsetDeg;
    }

    /** The shot table given with {@link #table}, or null. */
    public ShotTable getTable() {
        return table;
    }

    /**
     * Multiplies the shot speed (from {@link #setRpm} or the table) by this, e.g. 1.03 = 3% faster: a
     * live trim for a match (a tired battery, a worn wheel), not a new table. Not the idle speed. 1 = as
     * set (the default). The robot in five files' speed trim uses it.
     *
     * @param scale 0.5 to 1.5 (kept to that); 1.03 = +3%
     */
    public void speedScale(double scale) {
        speedScale = Double.isNaN(scale) ? 1 : Math.max(0.5, Math.min(1.5, scale));
    }

    /** The shot speed before {@link #speedScale}: from {@link #setRpm} or the table (RPM; 0 = none yet). */
    public double getShotRpm() {
        return shotRpm;
    }

    /** The shot speed's multiplier ({@link #speedScale}): 1 = as set. */
    public double getSpeedScale() {
        return speedScale;
    }

    /** Spins the flywheel up to the shot speed and keeps it there. */
    public void spinUp() {
        spinning = true;
        off = false;
    }

    /** Goes back to the idle speed (cancels any shots). Use {@link #stop()} for fully off. */
    public void idle() {
        spinning = false;
        shotsLeft = 0;
        off = false;
    }

    /** Shoots {@code count} pieces (spins up first if needed). Adds to any shots still waiting. */
    public void shoot(int count) {
        spinUp();
        feeder.clearJam();          // the driver asks again: try again (a new jam is detected again)
        // Nothing was waiting: start the "ready" time limit fresh. (If shots were already waiting,
        // keep the old timer, so pressing again and again cannot keep it waiting forever.)
        if (shotsLeft == 0) waitingSince = Double.NaN;
        lastProblem = "";
        // No shot speed yet: refuse now with a clear reason, instead of waiting and then
        // "giving up" with a confusing flywheel message.
        if (shotRpm <= 0) {
            problem("no shot speed: call setRpm(...) or setDistance(...) first");
            return;
        }
        // Math.max(0, count): a negative count must not take away shots.
        shotsLeft += Math.max(0, count);
    }

    /** Cancels the shots still waiting (keeps spinning). */
    public void stopShooting() {
        shotsLeft = 0;
    }

    /** Fully off: no shots, no idle speed. Stays off until spinUp(), shoot() or idle(). */
    @Override
    public void stop() {
        spinning = false;
        shotsLeft = 0;
        // This flag is what keeps it OFF: update() sends 0 RPM while it is true, instead of the idle speed.
        off = true;
        // Safety: stop EVERY part (feeder, flywheel, and a hood: a motor hood must not keep its power)
        // even if stopping one of them fails.
        // "try { ... } catch (RuntimeException e) { ... }" means: run this, and if it fails with an
        // error, do not crash right away; keep the error (e) and go on. We report the first error at the end.
        RuntimeException first = null;
        try { feeder.stop(); } catch (RuntimeException e) { first = e; }
        try { flywheel.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        if (hood != null) {
            try { hood.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        }
        if (first != null) throw first;
    }

    // ---------------------------------------------------------------------------------------
    // Every loop
    // ---------------------------------------------------------------------------------------

    /**
     * Once per loop: sets the flywheel speed, updates the flywheel, feeder and hood, then feeds one piece
     * if shots are waiting and everything is ready. Gives up (with a reason) if not ready in time.
     */
    @Override
    public void update() {
        double now = clockNanos.getAsLong() / 1e9;   // seconds
        // Off: 0. Spinning: the shot speed. Otherwise: the idle speed.
        // ("a ? b : c ? d : e" reads: if a then b, else if c then d, else e.)
        flywheel.setRpm(off ? 0 : spinning ? shotRpm * speedScale : idleRpm);
        flywheel.update();
        stats.watch(now, flywheel.getRpm(), flywheel.isReady());
        feeder.update();
        if (hood != null) hood.update();

        // A jammed feeder: cancel the shots. Pushing more pieces into a jam only makes it worse.
        // The driver clears it by pressing shoot again.
        if (feeder.isJammed()) {
            shotsLeft = 0;
            problem("feeder jammed");
            return;
        }
        // Nothing to do this loop: no shots waiting, the feeder is still pushing the last piece,
        // or it is too soon after the last piece (the wheel needs a moment to get back to speed).
        if (shotsLeft == 0 || feeder.isBusy() || now < nextFeedAt) return;

        // Feed only when EVERYTHING is ready: flywheel at speed, hood arrived, every extra check true.
        // A piece fed too early flies short or wide.
        if (Double.isNaN(waitingSince)) waitingSince = now;
        if (readyForMode(now)) {
            stats.fed(mode, now, now - lastFeedAt <= volleyGap(), flywheel.getRpm(), flywheel.getTargetRpm());
            feeder.feedOne();
            shotsLeft--;
            fired++;
            lastFeedAt = now;
            nextFeedAt = now + shotIntervalSec;
            waitingSince = Double.NaN;
        } else {
            // Not ready: the clock started above (first time only); check again next loop.
            // Waited too long: give up the rest of the shots, and say WHY, so the drivers are not left
            // wondering and the robot does not fire later at a bad moment.
            if (now - waitingSince > readyTimeoutSec) {
                problem("gave up: " + whyNotReady());
                shotsLeft = 0;
                waitingSince = Double.NaN;
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Questions
    // ---------------------------------------------------------------------------------------

    // The time after a feed within which the next piece is part of the same volley.
    private double volleyGap() {
        return shotIntervalSec + volleyGapSec;
    }

    // May the next piece go now, for the shot mode in use?
    private boolean readyForMode(double now) {
        switch (mode) {
            case SPRAY:
                // No wait for the speed band, the hood or the checks, but the wheel must really turn:
                // at least SPRAY_MIN_SHARE of its target (a stopped or stalled wheel only jams pieces).
                return spinning && !off
                        && Math.abs(flywheel.getRpm()) >= SPRAY_MIN_SHARE * Math.abs(flywheel.getTargetRpm());
            case QUICK:
                // The volley's first piece waits like CAREFUL. The next ones: everything but the speed
                // ready, and the speed in the looser band or the wait at its limit.
                if (now - lastFeedAt > volleyGap()) return isReady();
                if (!othersReady()) return false;
                double err = Math.abs(flywheel.getTargetRpm() - flywheel.getRpm());
                return err <= getQuickBand() || now - waitingSince >= quickMaxWaitSec;
            default:
                return isReady();
        }
    }

    // Everything but the flywheel's speed: spinning, the hood arrived, every check true.
    private boolean othersReady() {
        if (!spinning || off) return false;
        if (hood != null && !hood.isDone()) return false;
        for (BooleanSupplier c : checks) if (!c.getAsBoolean()) return false;
        return true;
    }

    /** What each fed piece did to the wheel: for tuning the shot modes (debug lines). */
    public ShotStats getShotStats() {
        return stats;
    }

    /** True when the flywheel is at speed, the hood has arrived, and every check passes. */
    public boolean isReady() {
        // Ready = nothing on the "not ready" list. One place decides, so the two can never disagree.
        return whyNotReady().isEmpty();
    }

    /** What is not ready yet, e.g. "flywheel, Turret" ("" when ready). */
    public String whyNotReady() {
        // A StringBuilder builds up a piece of text bit by bit.
        StringBuilder sb = new StringBuilder();
        if (!spinning) sb.append(off ? "off" : "not spinning");
        else if (!flywheel.isReady()) sb.append("flywheel");
        if (hood != null && !hood.isDone()) append(sb, "hood");
        // Ask every extra check; add the name of each one that says "no".
        for (int i = 0; i < checks.size(); i++) if (!checks.get(i).getAsBoolean()) append(sb, checkNames.get(i));
        return sb.toString();
    }

    /** What it is doing now (see {@link State}). */
    public State getState() {
        if (feeder.isBusy()) return State.FEEDING;
        if (shotsLeft > 0) return State.WAITING_READY;
        if (off) return State.OFF;
        return spinning ? State.SPINNING : State.IDLE;
    }

    /** True while shots are waiting or a piece is being fed. */
    public boolean isShooting() { return shotsLeft > 0 || feeder.isBusy(); }
    /** Shots still waiting to be fired. */
    public int getShotsLeft() { return shotsLeft; }
    /** Pieces fed since the OpMode started. */
    public int getShotsFired() { return fired; }
    // Remembers a problem and when, for the warning (shown PROBLEM_SHOW_SEC, then it clears itself).
    private void problem(String words) {
        lastProblem = words;
        problemAt = clockNanos.getAsLong() / 1e9;
    }

    /** The last problem (e.g. why it gave up), or "". */
    public String getLastProblem() { return lastProblem; }
    /** The flywheel inside. */
    public Flywheel getFlywheel() { return flywheel; }
    /** The hood, or null if there is none. */
    public AngleDevice getHood() { return hood; }
    /** True if a shot table was given ({@link #setDistance} works). */
    public boolean hasTable() { return table != null; }

    // ---------------------------------------------------------------------------------------
    // Autonomous steps
    // ---------------------------------------------------------------------------------------

    /** A step: spin up and wait until ready (or the ready timeout). */
    public AutoStep spinUpStep() {
        // "ctx -> { ... }" is a small piece of code handed over to run later, when the routine reaches it.
        return ctx -> {
            spinUp();
            ctx.waitUntil(this::isReady, readyTimeoutSec);
        };
    }

    /** A step: shoot {@code count} pieces and wait until done (or it gives up). */
    public AutoStep shootStep(int count) {
        return ctx -> {
            shoot(count);
            // Time limit: for each piece, the gap between pieces plus the longest wait for "ready",
            // and 2 s spare. Normally the launcher finishes or gives up long before this.
            ctx.waitUntil(() -> !isShooting(), count * (shotIntervalSec + readyTimeoutSec) + 2);
            // If the time limit ended the wait, cancel what is left so it does not fire during the next step.
            stopShooting();
        };
    }

    @Override public String name() { return "Launcher"; }

    /** Shows the state, shots left and READY; problems as warnings; then the flywheel, feeder and hood. */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        // A problem shows for a few seconds (a jam stays while the feeder says it is jammed).
        double now = clockNanos.getAsLong() / 1e9;
        if (!lastProblem.isEmpty() && (now - problemAt < PROBLEM_SHOW_SEC || feeder.isJammed())) t.warn("Launcher " + lastProblem);
        // Waiting to fire: say for what (e.g. "waits for: turret aimed"), so the drivers know.
        String waits = getState() == State.WAITING_READY ? whyNotReady() : "";
        t.line("Launcher", "%s  left %d%s%s", getState(), shotsLeft, isReady() ? "  READY" : "", waits.isEmpty() ? "" : "  waits for: " + waits);
        flywheel.addTelemetry(t);
        t.debug("Launcher waits for", whyNotReady().isEmpty() ? "-" : whyNotReady());
        t.debug("Launcher fired", fired);
        t.debug("Launcher mode", mode);
        stats.addTelemetry(t);
        feeder.addTelemetry(t);
        if (hood != null) hood.addTelemetry(t);
    }

    // Adds text to the list, with ", " between items.
    // "static": it uses nothing from a particular launcher, only what it is given.
    private static void append(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(text);
    }
}
