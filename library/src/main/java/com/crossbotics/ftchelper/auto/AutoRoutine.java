package com.crossbotics.ftchelper.auto;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.drive.AutoDrive;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.match.MatchTimer;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * An autonomous written as a list of named steps. (Last season's AutoSequence, made general.)
 *
 * <pre>{@code
 * routine.move("Leave the wall", 6, 0, 0.4)
 *        .step("Lift to HIGH", lift.startStep("HIGH"))        // starts; keeps moving during the next steps
 *        .move("Drive to basket", 24, 0, 0.6)
 *        .turn("Face basket", 45, 0.5)                        // written for BLUE, mirrored for RED
 *        .waitUntil("Lift up", lift::isAtTarget, 2.0)
 *        .run("Drop", claw::open)
 *        .waitFor("Let it fall", 0.3)
 *        .thenPark()                                         // steps below run even if time is short
 *        .move("Park", 30, 180, 0.8)
 *        .runAll();
 * }</pre>
 *
 * <ul>
 *   <li>Every step has a name. It shows on the Driver Station while it runs, and in the report at the end.</li>
 *   <li>Mechanisms (a {@link Subsystem} group) are updated every loop, during every step.</li>
 *   <li>{@link #mirrorFor} flips turns and sideways moves for RED: write the route once, for BLUE.</li>
 *   <li>{@link #reserveForPark}: the main steps stop early so the park steps always have time.</li>
 * </ul>
 *
 * <p>Two stages: first you ADD steps (the methods below only write each step onto the list, nothing
 * moves yet). Then {@link #runAll} RUNS the list, one step after another.
 *
 * <p>{@code EasyAuto} makes and runs one of these for you. Docs: docs/modules/auto.md,
 * docs/guides/easy-autos.md. The signs (which way is left) are in docs/conventions.md.
 */
public class AutoRoutine {

    // One line of the list: the step's name, the step itself, and whether it is a park step.
    // "static final class" inside a class: a small helper type only this file uses.
    private static final class Entry {
        final String label;
        final AutoStep step;
        final boolean park;
        Entry(String label, AutoStep step, boolean park) {
            this.label = label;
            this.step = step;
            this.park = park;
        }
    }

    // ---- How to talk to the OpMode (fixed when the routine is made) ----
    // "final": set once in the constructor and never changed.
    // BooleanSupplier / Runnable / LongSupplier hold a small piece of code to call later
    // (here: "is the OpMode still running?", "give the phone a moment", "what time is it?").
    private final BooleanSupplier active;
    private final Runnable idle;
    private final Telemetry rawTelemetry;
    private LongSupplier clock = System::nanoTime;
    // ---- Setup choices (null / 0 = not given) ----
    private AutoDrive drive = null;
    private final java.util.List<Runnable> beforeDriving = new java.util.ArrayList<>();
    private Subsystem robot = null;
    private RobotTelemetry telemetry = null;
    private MatchTimer timer = null;
    private Alliance mirror = null;
    private double parkReserveSec = 0;

    // ---- The list and what happens while it runs ----
    private final List<Entry> entries = new ArrayList<>();
    private final List<String> report = new ArrayList<>();
    // true after thenPark(): every step added from then on is a park step
    private boolean addingPark = false;
    // true once the first park step starts running: from then on the park time limit no longer applies
    private boolean inPark = false;
    // which step is running now (its place in the list), or -1 when nothing runs
    private int current = -1;
    // the heading saved by rememberHeading(), in degrees; NaN = nothing saved yet
    private double storedHeading = Double.NaN;

    /**
     * Makes a routine for your OpMode.
     *
     * @param opMode your autonomous OpMode ({@code this})
     */
    public AutoRoutine(LinearOpMode opMode) {
        // opMode::opModeIsActive is a "method reference": we hand over the OpMode's own method,
        // so the routine can ask "still running?" whenever it needs to.
        this(opMode::opModeIsActive, opMode::idle, opMode.telemetry);
    }

    /** For tests and simulations: what "running" and "idle" mean, and the telemetry. */
    public AutoRoutine(BooleanSupplier isActive, Runnable idle, Telemetry telemetry) {
        this.active = isActive;
        this.idle = idle;
        this.rawTelemetry = telemetry;
    }

    /** For tests: the clock used for waits (use the same one as the timer). */
    AutoRoutine withClock(LongSupplier clockNanos) {
        clock = clockNanos;
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------

    /** The drivetrain for move and turn steps. */
    public AutoRoutine drive(AutoDrive autoDrive) {
        drive = autoDrive;
        return this;
    }

    /** The mechanisms to keep updated during every step (a Subsystems group, or one Subsystem). */
    public AutoRoutine subsystems(Subsystem mechanisms) {
        robot = mechanisms;
        return this;
    }

    /** The telemetry to show steps on (made for you if you do not give one). */
    public AutoRoutine telemetry(RobotTelemetry t) {
        telemetry = t;
        return this;
    }

    /** A match timer already started at START (made and started by {@link #runAll} if not given). */
    public AutoRoutine timer(MatchTimer matchTimer) {
        timer = matchTimer;
        return this;
    }

    /**
     * Flips turns, headings and sideways directions when {@code alliance} is RED. Write routes for BLUE.
     * For a field that is ROTATED (not mirrored) between alliances, do not call this: robot-relative
     * moves and turns are then the same for both alliances. Pass null to turn mirroring off.
     * See match/Alliance.java and docs/modules/match.md.
     */
    public AutoRoutine mirrorFor(Alliance alliance) {
        mirror = alliance;
        return this;
    }

    /** True when routes are mirrored now (RED with {@link #mirrorFor}): x and headings flip. */
    public boolean mirrorsX() {
        return mirror == Alliance.RED;
    }

    /** Main steps stop when this many seconds are left, so the park steps have time. Default 0 (off). */
    public AutoRoutine reserveForPark(double seconds) {
        parkReserveSec = Math.abs(seconds);
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Steps (each returns this, so you can chain)
    // Each method only ADDS a step to the list. The code after "ctx ->" is a lambda: a small piece
    // of code saved now and run later, when runAll() reaches that step.
    // m(...) mirrors an angle for RED (see mirrorFor); distances are never mirrored.
    // ---------------------------------------------------------------------------------------

    /**
     * Drives a distance: direction 0 forward, 90 right, 180 back, -90 left (robot-relative).
     *
     * @param label       the name shown on the Driver Station and in the report
     * @param distanceIn  inches
     * @param directionDeg degrees, clockwise from forward (mirrored for RED)
     * @param maxPower    0 to 1
     */
    public AutoRoutine move(String label, double distanceIn, double directionDeg, double maxPower) {
        return add(label, ctx -> record(takeDrive().move(distanceIn, m(directionDeg), maxPower)));
    }

    /** Turns by an amount, in degrees: positive = left (mirrored for RED). */
    public AutoRoutine turn(String label, double degrees, double maxPower) {
        return add(label, ctx -> record(takeDrive().turn(m(degrees), maxPower)));
    }

    /** Turns to a heading (0 = the direction at the start, positive = left; mirrored for RED). */
    public AutoRoutine turnTo(String label, double headingDeg, double maxPower) {
        return add(label, ctx -> record(takeDrive().turnTo(m(headingDeg), maxPower)));
    }

    /**
     * {@link #turnToward} with a sweep: when nothing is seen at the start, the robot turns slowly, in
     * steps of {@code stepDeg}, up to {@code sweepDeg} to the left, then up to {@code sweepDeg} to the
     * right of where it started, and looks after each step. As soon as it sees the target it turns
     * toward it as {@link #turnToward} does. Never seen: it turns back to where it started, and the
     * report notes "not seen (swept)". It never turns farther than {@code sweepDeg} either way.
     */
    public AutoRoutine turnTowardWithSweep(String label, java.util.function.DoubleSupplier bearingDeg, double toleranceDeg,
                                           double maxPower, double timeoutSec, double sweepDeg, double stepDeg, double sweepPower) {
        return add(label, ctx -> {
            long end = clock.getAsLong() + (long) (timeoutSec * 1e9);
            java.util.function.DoubleSupplier left = () -> (end - clock.getAsLong()) / 1e9;
            // Headings are measured, not added up: a turn that stops short does not throw the sweep off.
            double start = needDrive().headingDeg();
            java.util.List<Double> stops = sweepStops(sweepDeg, stepDeg);
            int next = 0;
            boolean seen = !Double.isNaN(bearingDeg.getAsDouble());
            while (!seen && ctx.isActive() && left.getAsDouble() > 0) {
                // a fresh picture first (it may see the target without any turn)
                if (ctx.waitUntil(() -> !Double.isNaN(bearingDeg.getAsDouble()), Math.min(0.2, left.getAsDouble()))) {
                    seen = true;
                    break;
                }
                if (next >= stops.size() || left.getAsDouble() <= 0) break;
                record(takeDrive().turnTo(start + stops.get(next++), sweepPower));
            }
            if (!seen) {
                // Back to where it started (also after the time limit: a robot left turned is worse).
                if (ctx.isActive() && Math.abs(needDrive().headingDeg() - start) > 1) record(takeDrive().turnTo(start, sweepPower));
                note("not seen (swept)");
                return;
            }
            for (int i = 0; i < 4 && ctx.isActive() && left.getAsDouble() > 0; i++) {
                double b = bearingDeg.getAsDouble();
                if (Double.isNaN(b)) {
                    if (!ctx.waitUntil(() -> !Double.isNaN(bearingDeg.getAsDouble()), left.getAsDouble())) {
                        note("lost it");
                        return;
                    }
                    b = bearingDeg.getAsDouble();
                }
                if (Math.abs(b) <= toleranceDeg) return;
                record(takeDrive().turn(b, maxPower));
                ctx.waitFor(Math.min(0.15, Math.max(0, left.getAsDouble())));   // a fresh camera picture after the turn
            }
            double b = bearingDeg.getAsDouble();
            if (Double.isNaN(b) || Math.abs(b) > toleranceDeg) note("not within " + toleranceDeg + " deg");
        });
    }

    /**
     * The sweep's stops, degrees from the start (+ = left): left in steps up to {@code sweepDeg}, then right
     * in steps down to {@code -sweepDeg}; both ends always included. None for a sweep of 0. Public for tests.
     */
    public static java.util.List<Double> sweepStops(double sweepDeg, double stepDeg) {
        java.util.List<Double> stops = new java.util.ArrayList<>();
        double most = Math.abs(sweepDeg);
        if (most < 1e-9) return stops;
        double step = Math.min(Math.max(1, Math.abs(stepDeg)), most);
        for (double a = step; a < most - 1e-9; a += step) stops.add(a);
        stops.add(most);
        for (double a = most - step; a > -most + 1e-9; a -= step) stops.add(a);
        stops.add(-most);
        return stops;
    }

    /**
     * Turns toward something the robot measures, like the goal from the camera: {@code bearingDeg} says
     * how far to turn (positive = left, NaN = not seen). Up to 4 turns (a fresh reading after each),
     * until it is within {@code toleranceDeg}, or {@code timeoutSec} has passed. Not mirrored for RED:
     * the bearing is measured, not written. The report notes "not seen" or "not within" if it did not get there.
     */
    public AutoRoutine turnToward(String label, java.util.function.DoubleSupplier bearingDeg, double toleranceDeg,
                                  double maxPower, double timeoutSec) {
        return add(label, ctx -> {
            long end = clock.getAsLong() + (long) (timeoutSec * 1e9);
            for (int i = 0; i < 4 && ctx.isActive(); i++) {
                double left = (end - clock.getAsLong()) / 1e9;
                if (left <= 0 || !ctx.waitUntil(() -> !Double.isNaN(bearingDeg.getAsDouble()), left)) {
                    note(i == 0 ? "not seen" : "not within " + toleranceDeg + " deg");
                    return;
                }
                double b = bearingDeg.getAsDouble();
                if (Math.abs(b) <= toleranceDeg) return;
                record(takeDrive().turn(b, maxPower));
                ctx.waitFor(0.15);   // a fresh camera picture after the turn
            }
            double b = bearingDeg.getAsDouble();
            if (Double.isNaN(b) || Math.abs(b) > toleranceDeg) note("not within " + toleranceDeg + " deg");
        });
    }

    /** Drives in a field direction while turning to a heading (mecanum only). Both angles are mirrored for RED. */
    public AutoRoutine moveWithTurn(String label, double distanceIn, double fieldDirectionDeg,
                                    double headingDeg, double maxPower, double maxTurnPower) {
        return add(label, ctx -> record(takeDrive().moveWithTurn(distanceIn, m(fieldDirectionDeg),
                m(headingDeg), maxPower, maxTurnPower)));
    }

    /**
     * Drives to a field point with odometry and ends at a heading (mecanum). Written for BLUE: for RED
     * with mirroring on, x and the heading are mirrored.
     *
     * @param pose the odometry's pose (x right, y away, heading CCW from "away")
     */
    public AutoRoutine driveTo(String label, java.util.function.Supplier<com.crossbotics.ftchelper.util.FieldPose> pose,
                               double xIn, double yIn, double headingDeg, double maxPower, double toleranceIn) {
        return driveTo(label, pose, () -> true, xIn, yIn, headingDeg, maxPower, toleranceIn);
    }

    /** {@link #driveTo(String, java.util.function.Supplier, double, double, double, double, double)}; stops if {@code ready} turns false. */
    public AutoRoutine driveTo(String label, java.util.function.Supplier<com.crossbotics.ftchelper.util.FieldPose> pose,
                               BooleanSupplier ready, double xIn, double yIn, double headingDeg, double maxPower, double toleranceIn) {
        return driveTo(label, pose, ready, xIn, yIn, headingDeg, maxPower, toleranceIn, Collections.<PathEvent>emptyList());
    }

    /**
     * {@link #driveTo(String, java.util.function.Supplier, BooleanSupplier, double, double, double, double, double)}
     * that also does things on the way ({@link PathEvent}): the "path" is the straight line from where
     * the robot is at the start of the step to the point. Its distance left is the distance to the point.
     */
    public AutoRoutine driveTo(String label, final java.util.function.Supplier<com.crossbotics.ftchelper.util.FieldPose> pose,
                               BooleanSupplier ready, double xIn, double yIn, double headingDeg, double maxPower,
                               double toleranceIn, java.util.List<PathEvent> events) {
        final List<PathEvent> evs = new ArrayList<>(events);
        return add(label, ctx -> {
            if (evs.isEmpty()) {
                record(takeDrive().driveTo(pose, ready, m(xIn), yIn, m(headingDeg), maxPower, toleranceIn));
                return;
            }
            final double tx = m(xIn), ty = yIn;
            final Events along = new Events(evs);
            com.crossbotics.ftchelper.util.FieldPose at = pose.get();
            if (at != null) along.total = Math.hypot(tx - at.x, ty - at.y);
            // The drive calls tick() every loop while it drives: check the events there.
            duringStep = () -> {
                com.crossbotics.ftchelper.util.FieldPose now = pose.get();
                if (now != null) along.check(Math.hypot(tx - now.x, ty - now.y), Double.NaN, 1);
            };
            boolean done = false;
            try {
                AutoDrive.Result r = takeDrive().driveTo(pose, ready, tx, ty, m(headingDeg), maxPower, toleranceIn);
                record(r);
                done = r == AutoDrive.Result.DONE;
            } finally {
                duringStep = null;
                if (done) along.atEnd();
                else cutShortNote(along);
                along.noteMissed(done);
            }
        });
    }

    /**
     * Follows a path driven by another library (see {@link PathJob}) and waits until it is at the end,
     * at most {@code timeoutSec}. The report says {@code timed out}, {@code STALLED} (the distance left
     * stopped going down for 1.5 s, more than 3 in from the end) or {@code NO_ODOMETRY} (the pose was
     * not ready at the start, or for 0.2 s on the way). BLOCKS until then, keeping the mechanisms updated.
     */
    public AutoRoutine followPath(String label, final PathJob job, final double timeoutSec) {
        return followPath(label, job, timeoutSec, Collections.<PathEvent>emptyList());
    }

    /**
     * {@link #followPath(String, PathJob, double)} that also does things along the path: each
     * {@link PathEvent} fires once, when the robot gets far enough along (checked every loop, in the
     * order given). At the end of the path the ones reached there fire; if the path ends early, the
     * report lists the ones that did not fire ({@code STALLED, not fired: Spin up}).
     */
    public AutoRoutine followPath(String label, final PathJob job, final double timeoutSec, java.util.List<PathEvent> events) {
        final List<PathEvent> evs = new ArrayList<>(events);
        return add(label, ctx -> {
            final Events along = new Events(evs);
            if (!job.isReady()) {
                ctx.note("NO_ODOMETRY");
                along.noteMissed(false);
                return;
            }
            job.start();
            // The path's length, read now: if the first loop already finishes the path, the end check
            // below still knows it ("1 in done" fires at the end of even a very short path).
            along.total = lengthOf(job);
            long start = System.nanoTime();
            com.crossbotics.ftchelper.drive.StallDetector stall = new com.crossbotics.ftchelper.drive.StallDetector(0.25, 1.5);
            double notReadySince = Double.NaN;
            // The distance left means nothing before the follower's first update (a follower may say 0), and
            // may jump up when a path follower starts the next part of a chain: the stuck check skips both.
            boolean updated = false;
            double lastRemaining = Double.NaN;
            boolean done = false;
            try {
                while (job.isBusy()) {
                    double t = (System.nanoTime() - start) / 1e9;
                    if (!ctx.isActive()) {
                        // park time or STOP: the report says "cut short"
                        cutShortNote(along);
                        return;
                    }
                    if (t > timeoutSec) {
                        ctx.note("timed out");
                        return;
                    }
                    if (!job.isReady()) {
                        if (Double.isNaN(notReadySince)) notReadySince = t;
                        if (t - notReadySince >= 0.2) {
                            ctx.note("NO_ODOMETRY");
                            return;
                        }
                    } else {
                        notReadySince = Double.NaN;
                    }
                    if (updated) {
                        double left = job.remainingIn();
                        // Things to do along the path: fire the ones the robot has got to.
                        if (Double.isNaN(along.total)) along.total = lengthOf(job);   // not known yet: ask the follower
                        along.check(left, along.total, job.partNow());
                        if (left > lastRemaining + 1) stall.reset();
                        lastRemaining = left;
                        // The last 3 in: the follower may still be finishing its turn while the
                        // distance hardly changes. The time limit still guards it.
                        if (left <= 3) stall.reset();
                        else if (stall.update(left, t)) {
                            ctx.note("STALLED");
                            return;
                        }
                    }
                    ctx.loop();
                    updated = true;
                }
                done = true;
            } finally {
                if (job.isBusy()) job.cancel();
                // At the end of the path: what is reached at the end fires now. Then the report
                // lists what did not fire.
                if (done) {
                    // Read the length again: a follower may only know it once its path has started.
                    double length = lengthOf(job);
                    if (!Double.isNaN(length)) along.total = length;
                    along.atEnd();
                }
                along.noteMissed(done);
            }
        });
    }

    // The length a path follower gives for its path, inches, or NaN when it does not know (NaN, 0 or less).
    private static double lengthOf(PathJob job) {
        double length = job.totalIn();
        return length > 0 ? length : Double.NaN;
    }

    // The PathEvents of one running step: which have fired, and the length of the path.
    private final class Events {
        final List<PathEvent> list;
        final boolean[] fired;
        // The path's length, inches. NaN: not known yet (the first distance left is used then).
        double total = Double.NaN;

        Events(List<PathEvent> list) {
            this.list = list;
            this.fired = new boolean[list.size()];
        }

        // Fires every event reached now, in the order written. part: from 1, 0 = unknown.
        void check(double left, double length, int part) {
            if (list.isEmpty() || Double.isNaN(left)) return;   // nothing to do, or no distance
            if (!(length > 0)) {
                if (Double.isNaN(total) || total < left) total = left;   // a follower that does not know the length
                length = total;
            }
            double doneIn = Math.max(0, length - left);
            double fraction = length > 0 ? Math.min(1, doneIn / length) : 1;
            fire(new PathEvent.Progress(doneIn, left, fraction, part));
        }

        // The path got to its end: everything along it has been passed.
        void atEnd() {
            double length = Double.isNaN(total) ? 0 : total;
            fire(new PathEvent.Progress(length, 0, 1, Integer.MAX_VALUE));
        }

        private void fire(PathEvent.Progress p) {
            for (int i = 0; i < fired.length; i++) {
                if (!fired[i] && list.get(i).isReached(p)) {
                    fired[i] = true;
                    list.get(i).fire();
                }
            }
        }

        boolean anyLeft() {
            for (boolean f : fired) if (!f) return true;
            return false;
        }

        // e.g. "not fired: Spin up, Intake on" in the report. reachedEnd: the path got to its end
        // (then "done" comes first, so the report still says it got there).
        void noteMissed(boolean reachedEnd) {
            if (!anyLeft()) return;
            if (reachedEnd && stepNote.isEmpty()) note("done");
            StringBuilder sb = new StringBuilder("not fired: ");
            String sep = "";
            for (int i = 0; i < fired.length; i++) {
                if (fired[i]) continue;
                sb.append(sep).append(list.get(i).name());
                sep = ", ";
            }
            note(sb.toString());
        }
    }

    // A step stopped by the park time leaves "not fired: ..." notes: say "cut short" first, as runAll
    // would for a step with no notes.
    private void cutShortNote(Events along) {
        if (along.anyLeft() && stepNote.isEmpty() && active.getAsBoolean() && !inPark && mainTimeOver()) {
            note("cut short (time for park)");
        }
    }

    // Code the drive's loop hook runs for the step that is running now (driveTo's PathEvents), or null.
    private Runnable duringStep = null;

    /** Remembers the heading now, for {@link #returnToHeading}. */
    public AutoRoutine rememberHeading(String label) {
        return add(label, ctx -> storedHeading = needDrive().headingDeg());
    }

    /** Turns back to the heading saved by {@link #rememberHeading}. */
    public AutoRoutine returnToHeading(String label, double maxPower) {
        return add(label, ctx -> {
            if (Double.isNaN(storedHeading)) { note("no heading remembered"); return; }
            // not mirrored with m(): the saved heading was measured on the real robot, so it is already right
            record(takeDrive().turnTo(storedHeading, maxPower));
        });
    }

    /** Runs some code at once and goes on (e.g. {@code claw::open}, {@code () -> lift.goTo("HIGH")}). */
    public AutoRoutine run(String label, Runnable action) {
        return add(label, ctx -> action.run());
    }

    /** Waits (mechanisms keep working). */
    public AutoRoutine waitFor(String label, double seconds) {
        return add(label, ctx -> ctx.waitFor(seconds));
    }

    /** Waits until something is true, at most {@code timeoutSec}. The report says if it timed out. */
    public AutoRoutine waitUntil(String label, BooleanSupplier condition, double timeoutSec) {
        return add(label, ctx -> {
            // Write "timed out" in the report only for a real time-out: not when the OpMode was
            // stopped, and not when the park time cut the wait short (runAll writes that note).
            if (!ctx.waitUntil(condition, timeoutSec) && ctx.isActive()) note("timed out");
        });
    }

    /** Any step: a mechanism's step (e.g. {@code lift.goToStep("HIGH")}) or your own lambda. */
    public AutoRoutine step(String label, AutoStep step) {
        return add(label, step);
    }

    /** The steps after this are PARK steps: they run even when the main steps ran out of time. */
    public AutoRoutine thenPark() {
        addingPark = true;
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Running
    // ---------------------------------------------------------------------------------------

    /**
     * Runs every step in order. Call after {@code waitForStart()}.
     * <b>BLOCKS</b> (on purpose): returns when the routine is done or stopped. Autonomous only.
     * Mechanisms are updated every loop the whole time.
     */
    public void runAll() {
        robotTelemetry();
        // No timer given? Start our own 30 s autonomous clock now.
        if (timer == null) {
            timer = MatchTimer.autonomous();
            timer.start();
        }
        if (drive != null) {
            // During every move, the drive calls our tick() each loop, so the mechanisms keep working
            // and the telemetry stays live while the robot drives.
            drive.setLoopHook(this::tick);
            // And it stops a MAIN move early once the park time has come, so parking always has time.
            // (Park moves are never stopped this way: !inPark.)
            drive.setStopCondition(() -> !inPark && mainTimeOver());
        }
        // try/finally: the "finally" part runs however we leave the "try" part: done, stopped, or
        // after an error. That is where we clean up, so the wheels are always stopped.
        try {
            for (current = 0; current < entries.size(); current++) {
                Entry e = entries.get(current);
                // The OpMode was stopped (STOP pressed or the match ended): run nothing more.
                if (!active.getAsBoolean()) break;
                // The first park step switches park mode on; it then stays on for the rest.
                if (e.park) inPark = true;
                // Not enough time left for main steps: skip them, so the park steps still get their time.
                if (!e.park && mainTimeOver()) {
                    report.add(e.label + ": skipped (time for park)");
                    continue;
                }
                stepNote = "";
                // Run the step. It blocks until it is finished (or stopped), calling tick() while it waits.
                e.step.run(context);
                // A main step that ended because the park time came was cut short: say so, so the
                // report does not claim it was "done".
                if (!e.park && stepNote.isEmpty() && active.getAsBoolean() && mainTimeOver()) {
                    stepNote = "cut short (time for park)";
                }
                // Write what happened: "done", or the notes the step left (e.g. "STALLED", "timed out").
                report.add(e.label + (stepNote.isEmpty() ? ": done" : ": " + stepNote));
            }
        } finally {
            current = -1;
            if (drive != null) {
                // Remove our hooks, so the drive does not call into this routine after it is over.
                drive.setLoopHook(null);
                drive.setStopCondition(null);
                drive.stop();      // a custom step that drove and then threw an error must not leave wheels running
            }
        }
    }

    /** What happened to each step, e.g. "Drive to basket: STALLED". Read-only. */
    public List<String> getReport() {
        return Collections.unmodifiableList(report);
    }

    // ---------------------------------------------------------------------------------------
    // What a running step can use. A separate object, so routine.waitFor("label", s) (a step)
    // can never be confused with a wait that runs at once.
    // (The routine's own waitFor ADDS a step to the list; ctx.waitFor WAITS right now. Giving
    // steps only this small helper object means a step cannot call the wrong one by mistake.)
    // "new AutoContext() { ... }" makes a one-off object that fills in the AutoContext methods.
    // ---------------------------------------------------------------------------------------

    private final AutoContext context = new AutoContext() {
        @Override
        public boolean isActive() {
            // Keep going while the OpMode runs AND (we are parking, or the main time is not over yet).
            return active.getAsBoolean() && (inPark || !mainTimeOver());
        }

        @Override
        public void loop() {
            // update the mechanisms and telemetry, then let the phone do its own work for a moment
            tick();
            idle.run();
        }

        @Override
        public boolean waitUntil(BooleanSupplier done, double timeoutSec) {
            // the time (nanoseconds) when we give up; 1e9 nanoseconds in a second
            long end = clock.getAsLong() + (long) (timeoutSec * 1e9);
            // This loop waits ON PURPOSE (autonomous only), but every pass calls loop(),
            // so the mechanisms keep working and STOP is noticed.
            while (isActive() && clock.getAsLong() < end) {
                if (done.getAsBoolean()) return true;
                loop();
            }
            // one last check: it may have become true on the very last pass
            return done.getAsBoolean();
        }

        @Override
        public void waitFor(double seconds) {
            long end = clock.getAsLong() + (long) (seconds * 1e9);
            // the safe way to "sleep": keep looping (and updating) until the time is up
            while (isActive() && clock.getAsLong() < end) loop();
        }

        @Override
        public double secondsLeft() {
            // no timer yet (only before runAll): assume the whole 30 s
            return timer == null ? MatchTimer.AUTO_SECONDS : timer.secondsLeft();
        }

        @Override
        public RobotTelemetry telemetry() {
            return robotTelemetry();
        }

        @Override
        public void note(String text) {
            AutoRoutine.this.note(text);
        }
    };

    /** Number of steps added. */
    public int size() {
        return entries.size();
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    // Notes for the step that is running now, added to the report when it ends ("" = nothing to say).
    private String stepNote = "";

    // Puts a step on the list. A step added after thenPark() is marked as a park step.
    private AutoRoutine add(String label, AutoStep step) {
        entries.add(new Entry(label, step, addingPark));
        return this;
    }

    // One loop's work while a step waits or drives: update the mechanisms and the timer, then show
    // which step runs, the time left, and the mechanisms' telemetry. Must be quick: it runs every loop.
    private void tick() {
        if (robot != null) robot.update();
        if (duringStep != null) duringStep.run();
        if (timer != null) timer.update();
        RobotTelemetry t = robotTelemetry();
        if (current >= 0 && current < entries.size()) {
            Entry e = entries.get(current);
            // e.g. "Step  3/8 Drive to basket" (+ " (park)" for park steps); current + 1 because people count from 1
            t.line("Step", "%d/%d %s%s", current + 1, entries.size(), e.label, e.park ? " (park)" : "");
        }
        t.line("Time left", timer == null ? "-" : timer.format());
        if (robot != null) robot.addTelemetry(t);
        t.update();
    }

    // The telemetry helper, made the first time it is needed if none was given.
    private RobotTelemetry robotTelemetry() {
        if (telemetry == null) telemetry = new RobotTelemetry(rawTelemetry);
        return telemetry;
    }

    // True when the main steps must stop so the park steps keep their reserved time.
    // Always false when no park time was reserved (0) or there is no timer.
    private boolean mainTimeOver() {
        return parkReserveSec > 0 && timer != null && timer.secondsLeft() <= parkReserveSec;
    }

    // Mirrors a turn or direction for RED (its sign flips: left becomes right); unchanged for BLUE
    // or when mirroring is off. 0 (forward) and 180 (back) point the same way after the flip.
    private double m(double value) {
        return mirror == null ? value : mirror.mirror(value);
    }

    // The drive, or a clear error message if a move/turn step was added without one.
    /**
     * Code to run when a step starts driving the wheels (move, turn, driveTo...). The robot layer uses it
     * to end a path follower's hold at the end of a path, so the two never drive the wheels at once.
     * Each call adds one more; they run in order.
     */
    public AutoRoutine beforeDriving(Runnable hook) {
        if (hook != null) beforeDriving.add(hook);
        return this;
    }

    // A drive step starts: the hook first (see beforeDriving).
    private AutoDrive takeDrive() {
        AutoDrive d = needDrive();
        for (Runnable r : beforeDriving) r.run();
        return d;
    }

    private AutoDrive needDrive() {
        if (drive == null) throw new IllegalStateException("This step needs a drive: call routine.drive(autoDrive) first");
        return drive;
    }

    // A move that did not end with DONE (STALLED, TIMED_OUT, STOPPED) gets that word in the report.
    private void record(AutoDrive.Result result) {
        if (result != AutoDrive.Result.DONE) note(result.name());
    }

    // Adds a note for the running step; several notes are joined with ", ".
    private void note(String text) {
        stepNote = stepNote.isEmpty() ? text : stepNote + ", " + text;
    }

    /** Formats the report for telemetry: one line per step. */
    public String reportText() {
        // StringBuilder: an efficient way to glue many pieces of text together
        StringBuilder sb = new StringBuilder();
        for (String r : report) sb.append(r).append('\n');
        return String.format(Locale.US, "%s", sb.toString().trim());
    }
}
