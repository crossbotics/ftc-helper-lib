package io.github.jasoncross.ftchelper.auto;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.drive.AutoDrive;
import io.github.jasoncross.ftchelper.match.Alliance;
import io.github.jasoncross.ftchelper.match.MatchTimer;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
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
 * <p>{@code EasyAuto} makes and runs one of these for you. Docs: docs/modules/auto.md
 */
public class AutoRoutine {

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

    private final BooleanSupplier active;
    private final Runnable idle;
    private final Telemetry rawTelemetry;
    private LongSupplier clock = System::nanoTime;
    private AutoDrive drive = null;
    private Subsystem robot = null;
    private RobotTelemetry telemetry = null;
    private MatchTimer timer = null;
    private Alliance mirror = null;
    private double parkReserveSec = 0;

    private final List<Entry> entries = new ArrayList<>();
    private final List<String> report = new ArrayList<>();
    private boolean addingPark = false;
    private boolean inPark = false;
    private int current = -1;
    private double storedHeading = Double.NaN;

    /** @param opMode your autonomous OpMode ({@code this}) */
    public AutoRoutine(LinearOpMode opMode) {
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
     */
    public AutoRoutine mirrorFor(Alliance alliance) {
        mirror = alliance;
        return this;
    }

    /** Main steps stop when this many seconds are left, so the park steps have time. Default 0 (off). */
    public AutoRoutine reserveForPark(double seconds) {
        parkReserveSec = Math.abs(seconds);
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Steps (each returns this, so you can chain)
    // ---------------------------------------------------------------------------------------

    /** Drives a distance: direction 0 forward, 90 right, 180 back, -90 left (robot-relative). */
    public AutoRoutine move(String label, double distanceIn, double directionDeg, double maxPower) {
        return add(label, ctx -> record(needDrive().move(distanceIn, m(directionDeg), maxPower)));
    }

    /** Turns by an amount: positive = left. */
    public AutoRoutine turn(String label, double degrees, double maxPower) {
        return add(label, ctx -> record(needDrive().turn(m(degrees), maxPower)));
    }

    /** Turns to a heading (0 = the direction at the start, positive = left). */
    public AutoRoutine turnTo(String label, double headingDeg, double maxPower) {
        return add(label, ctx -> record(needDrive().turnTo(m(headingDeg), maxPower)));
    }

    /** Drives in a field direction while turning to a heading (mecanum only). */
    public AutoRoutine moveWithTurn(String label, double distanceIn, double fieldDirectionDeg,
                                    double headingDeg, double maxPower, double maxTurnPower) {
        return add(label, ctx -> record(needDrive().moveWithTurn(distanceIn, m(fieldDirectionDeg),
                m(headingDeg), maxPower, maxTurnPower)));
    }

    /** Remembers the heading now, for {@link #returnToHeading}. */
    public AutoRoutine rememberHeading(String label) {
        return add(label, ctx -> storedHeading = needDrive().headingDeg());
    }

    /** Turns back to the heading saved by {@link #rememberHeading}. */
    public AutoRoutine returnToHeading(String label, double maxPower) {
        return add(label, ctx -> {
            if (Double.isNaN(storedHeading)) { note("no heading remembered"); return; }
            record(needDrive().turnTo(storedHeading, maxPower));
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
            if (!ctx.waitUntil(condition, timeoutSec) && active.getAsBoolean()) note("timed out");
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

    /** Runs every step in order. Call after {@code waitForStart()}. Returns when done or stopped. */
    public void runAll() {
        robotTelemetry();
        if (timer == null) {
            timer = MatchTimer.autonomous();
            timer.start();
        }
        if (drive != null) {
            drive.setLoopHook(this::tick);
            drive.setStopCondition(() -> !inPark && mainTimeOver());
        }
        try {
            for (current = 0; current < entries.size(); current++) {
                Entry e = entries.get(current);
                if (!active.getAsBoolean()) break;
                if (e.park) inPark = true;
                if (!e.park && mainTimeOver()) {
                    report.add(e.label + ": skipped (time for park)");
                    continue;
                }
                stepNote = "";
                e.step.run(context);
                report.add(e.label + (stepNote.isEmpty() ? ": done" : ": " + stepNote));
            }
        } finally {
            current = -1;
            if (drive != null) {
                drive.setLoopHook(null);
                drive.setStopCondition(null);
                drive.stop();      // a custom step that drove and then threw an error must not leave wheels running
            }
        }
    }

    /** What happened to each step, e.g. "Drive to basket: STALLED". */
    public List<String> getReport() {
        return Collections.unmodifiableList(report);
    }

    // ---------------------------------------------------------------------------------------
    // What a running step can use. A separate object, so routine.waitFor("label", s) (a step)
    // can never be confused with a wait that runs at once.
    // ---------------------------------------------------------------------------------------

    private final AutoContext context = new AutoContext() {
        @Override
        public boolean isActive() {
            return active.getAsBoolean() && (inPark || !mainTimeOver());
        }

        @Override
        public void loop() {
            tick();
            idle.run();
        }

        @Override
        public boolean waitUntil(BooleanSupplier done, double timeoutSec) {
            long end = clock.getAsLong() + (long) (timeoutSec * 1e9);
            while (isActive() && clock.getAsLong() < end) {
                if (done.getAsBoolean()) return true;
                loop();
            }
            return done.getAsBoolean();
        }

        @Override
        public void waitFor(double seconds) {
            long end = clock.getAsLong() + (long) (seconds * 1e9);
            while (isActive() && clock.getAsLong() < end) loop();
        }

        @Override
        public double secondsLeft() {
            return timer == null ? MatchTimer.AUTO_SECONDS : timer.secondsLeft();
        }

        @Override
        public RobotTelemetry telemetry() {
            return robotTelemetry();
        }
    };

    /** Number of steps added. */
    public int size() {
        return entries.size();
    }

    // ---------------------------------------------------------------------------------------

    private String stepNote = "";

    private AutoRoutine add(String label, AutoStep step) {
        entries.add(new Entry(label, step, addingPark));
        return this;
    }

    private void tick() {
        if (robot != null) robot.update();
        if (timer != null) timer.update();
        RobotTelemetry t = robotTelemetry();
        if (current >= 0 && current < entries.size()) {
            Entry e = entries.get(current);
            t.line("Step", "%d/%d %s%s", current + 1, entries.size(), e.label, e.park ? " (park)" : "");
        }
        t.line("Time left", timer == null ? "-" : timer.format());
        if (robot != null) robot.addTelemetry(t);
        t.update();
    }

    private RobotTelemetry robotTelemetry() {
        if (telemetry == null) telemetry = new RobotTelemetry(rawTelemetry);
        return telemetry;
    }

    private boolean mainTimeOver() {
        return parkReserveSec > 0 && timer != null && timer.secondsLeft() <= parkReserveSec;
    }

    private double m(double value) {
        return mirror == null ? value : mirror.mirror(value);
    }

    private AutoDrive needDrive() {
        if (drive == null) throw new IllegalStateException("This step needs a drive: call routine.drive(autoDrive) first");
        return drive;
    }

    private void record(AutoDrive.Result result) {
        if (result != AutoDrive.Result.DONE) note(result.name());
    }

    private void note(String text) {
        stepNote = stepNote.isEmpty() ? text : stepNote + ", " + text;
    }

    /** Formats the report for telemetry. */
    public String reportText() {
        StringBuilder sb = new StringBuilder();
        for (String r : report) sb.append(r).append('\n');
        return String.format(Locale.US, "%s", sb.toString().trim());
    }
}
