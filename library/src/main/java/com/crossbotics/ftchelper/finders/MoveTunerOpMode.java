package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.drive.AutoDrive;
import com.crossbotics.ftchelper.drive.AutoDriveConfig;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.drive.MecanumMixer;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.FieldPose;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures how exactly the autonomous moves stop and the turns end, and suggests the numbers in
 * {@code Tunables.Drive} (or {@code RobotSettings.auto()}). Run it after the Drive Tuner (and the
 * Odometry Tuner, if the robot has odometry): it needs the wheel size right first.
 *
 * <pre>
 * D-pad up/down = choose a test, D-pad left/right = the test power (0.3 to 1.0, starts 0.5), A = start it.
 *   1 Creep:  the power rises slowly until the robot rolls (forward, then right). -> moveMinPower
 *   2 Stops:  moves of 6, 12 and 24 in, out and back, with slowDownIn 0, 3, 6, 9, 12 until they
 *             stop within 0.5 in. -> slowDownIn
 *   3 Turns:  turns 90, 135 and 45 left and back. -> turnKp, turnKd, turnMinPower or turnToleranceDeg (one per round)
 *   4 Drift:  48 in forward and back (and right and left): how much the heading changes. Report only.
 *   5 Move and turn (mecanum): 24 in sideways while turning 90 (moveWithTurn). -> moveTurnScale, moveMaxTurnPower
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * Each new number is used at once in this tuner (no rebuild): run the test again until it says OK,
 * then copy the lines into your code. It measures with the odometry when the robot has it (it sees a
 * wheel that skids), else with the wheel counts. Leave 5 ft of space around the robot. Every test
 * ends about where it began.
 *
 * <p>In your TeamCode, the robot in five files (next to Robot.java):
 * <pre>{@code
 * @TeleOp(name = "Move Tuner", group = "Finders")
 * public class MoveTuner extends MoveTunerOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 * With a RobotSettings class instead: {@code @Override protected RobotConfig robotConfig() { return new RobotSettings(); }}
 * The "Tune and check" menu has it already: nothing to copy there.
 *
 * <p>Each move and turn WAITS while the robot moves (like autonomous): STOP, GUIDE and START still
 * stop it at once. Between moves nothing waits: the loop goes on.
 *
 * Docs: docs/modules/finders.md. Guide: docs/tuning/10-move-tuner.md
 */
public abstract class MoveTunerOpMode extends FinderOpMode {

    // Its numbers are powers and gains: they depend on the battery.
    @Override
    protected boolean batteryMatters() {
        return true;
    }

    private static final String[] TESTS = {"1 Creep", "2 Stops", "3 Turns", "4 Drift", "5 Move and turn"};
    private static final int CREEP = 0, STOPS = 1, TURNS = 2, DRIFT = 3, MOVE_TURN = 4;

    /**
     * Your robot in five files: {@code return new Robot();}. The tuner uses its drive, IMU,
     * odometry (if any) and {@code Tunables.Drive}. Default null: then {@link #robotConfig()} must be given.
     */
    protected RobotDefinition robot() {
        return null;
    }

    /**
     * Your robot's settings (the same as autonomous), e.g. {@code return new RobotSettings();}.
     * Default null: the drive of {@link #robot()}. Give one of the two.
     */
    protected RobotConfig robotConfig() {
        return null;
    }

    private DriveConfig driveConfig;
    private AutoDriveConfig autoConfig;
    private AutoDrive auto;
    // The odometry, when the robot has it: it sees a skid that the wheel counts miss. Else null.
    private PoseTracker odo;
    private final List<DcMotorEx> wheels = new ArrayList<>();
    // Tunables.Drive.calibrated (or calibrationConfirmed): the Drive Tuner is done.
    private boolean calibrated;

    private int test = 0;
    private double testPower = DriveTunerOpMode.DEFAULT_POWER;
    // A test asked for by A, started in step() (not inside the button action, because a move waits).
    private int pendingTest = -1;
    // The test now running (-1 = none), its moves still to do, the one being measured.
    private int runningTest = -1;
    private final ArrayDeque<Trial> queue = new ArrayDeque<>();
    private final List<Trial> done = new ArrayList<>();
    private Trial current;
    private MoveMath.StillWatch still;
    // The screen's report: the last test's table and what to do next.
    private final List<String> report = new ArrayList<>();
    private long movingShownAt = 0;

    // Stops: which slowDownIn step is being tried, and the slowDownIn before the test.
    private int stopsStep;
    private double bestSlide = Double.MAX_VALUE, bestSlowDown = Double.NaN, slowDownBefore, firstSlide = Double.NaN;

    // Creep: the axis (0 forward, 1 right), the power now, when it began, the wheel counts then.
    private int creepAxis;
    private double creepPower, creepStepSec, creepForward, creepRight;
    private int[] creepTicks;

    // One move or turn of a test, and what was measured.
    private static final class Trial {
        final int kind;             // 0 move, 1 turn, 2 move with turn
        final double distanceIn, directionDeg, turnDeg, targetHeadingDeg;
        AutoDrive.Result result;
        int[] startTicks;
        int[] lastTicks;
        FieldPose startPose;
        double startHeading, startSec, seconds, target;
        double slideIn, headingChangeDeg, endErrorDeg, lagDeg = 0, traveledIn;
        boolean lagMeasured;
        MoveMath.TurnTrace trace;

        Trial(int kind, double distanceIn, double directionDeg, double turnDeg, double targetHeadingDeg) {
            this.kind = kind;
            this.distanceIn = distanceIn;
            this.directionDeg = directionDeg;
            this.turnDeg = turnDeg;
            this.targetHeadingDeg = targetHeadingDeg;
        }

        static Trial move(double inches, double directionDeg) {
            return new Trial(0, inches, directionDeg, 0, 0);
        }

        static Trial turn(double degrees) {
            return new Trial(1, 0, 0, degrees, 0);
        }

        static Trial moveTurn(double inches, double fieldDirectionDeg, double targetHeadingDeg) {
            return new Trial(2, inches, fieldDirectionDeg, 0, targetHeadingDeg);
        }
    }

    @Override protected String finderName() { return "Move tuner"; }

    @Override
    protected void setup() {
        RobotDefinition robot = robot();
        RobotConfig config = robotConfig();
        if (config == null) {
            if (robot == null) {
                throw new IllegalStateException("The move tuner needs your robot: add "
                        + "\"@Override protected RobotDefinition robot() { return new Robot(); }\" (or robotConfig()).");
            }
            // With odometry: set the robot up, so its odometry runs (like the Odometry Tuner).
            if (robot.odometrySpec() != null) {
                robot.setup(hardwareMap);
                odo = robot.odometry();
            }
            config = robot.robotConfig();
        }
        driveConfig = config.drive();
        autoConfig = config.auto();
        calibrated = autoConfig.calibrationConfirmed;
        // AutoDrive refuses to start without it. This tool checks it itself (A says "Drive Tuner first"),
        // so INIT still shows the screen with the reason.
        autoConfig.calibrationConfirmed = true;
        IMU imu = config.imu(hardwareMap);
        if (imu == null) throw new IllegalStateException("The move tuner needs the IMU: add .imu(...) to the drive in Robot.java (or RobotSettings.imu()).");
        // running(), not opModeIsActive(): inside "Tune and check" this finder is not the running OpMode.
        auto = new AutoDrive(this, driveConfig, autoConfig, imu, this::running);
        // The wheel motors, for the Creep test (it sets small powers itself).
        wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.frontLeft));
        wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.frontRight));
        if (driveConfig.hasBackMotors()) {
            wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.backLeft));
            wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.backRight));
        }
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.text("Move tuner: how exactly autonomous moves stop and turns end.");
        t.text("Clear floor: 5 ft around the robot. Press {PLAY}.");
        if (!calibrated) t.warn("Run the Drive Tuner first, then set calibrated = true.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> choose(-1))
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> choose(+1))
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> changePower(+1))
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> changePower(-1))
         .bindPress(Pad.G1, Btn.A, this::pressA);
    }

    private boolean mecanum() {
        return driveConfig.type == DriveConfig.DriveType.MECANUM;
    }

    // Move and turn needs sideways wheels: a tank drive skips it.
    private boolean available(int which) {
        return which != MOVE_TURN || mecanum();
    }

    private void choose(int direction) {
        if (runningTest >= 0) return;
        test = (test + direction + TESTS.length) % TESTS.length;
        // Only one test can be missing (Move and turn, on a tank drive): then skip one more.
        if (!available(test)) test = (test + direction + TESTS.length) % TESTS.length;
    }

    private void changePower(int direction) {
        if (runningTest >= 0) return;
        testPower = DriveTunerOpMode.nextPower(testPower, direction);
    }

    private void pressA() {
        if (runningTest >= 0) return;
        if (!calibrated) {
            setReport("Run the Drive Tuner first: the wheel size must be right.",
                    "Then set Tunables.Drive: calibrated = true.");
            return;
        }
        pendingTest = test;   // step() starts it
    }

    // ---------------------------------------------------------------------------------------------
    // Each loop
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void step() {
        if (odo != null) odo.update();
        if (pendingTest >= 0) {
            int which = pendingTest;
            pendingTest = -1;
            begin(which);
            return;
        }
        if (runningTest == CREEP) {
            creepStep();
        } else if (current != null) {
            // The last move ended: wait (without blocking) until the robot is still, then measure it.
            int[] ticks = auto.getWheelTicks();
            double tpi = autoConfig.ticksPerInch();
            double[] values = {ticks[0] / tpi, ticks[1] / tpi, ticks[2] / tpi, ticks[3] / tpi, auto.headingDeg()};
            double[] tolerances = {0.05, 0.05, 0.05, 0.05, 0.3};
            if (still.update(seconds(), values, tolerances)) {
                measure(current);
                done.add(current);
                current = null;
                if (queue.isEmpty()) endOfQueue();
            }
        } else if (!queue.isEmpty()) {
            runTrial(queue.poll());
        }
    }

    private static double seconds() {
        return System.nanoTime() / 1e9;
    }

    private void begin(int which) {
        report.clear();
        done.clear();
        queue.clear();
        runningTest = which;
        results.set("move:power", "// measured at power " + FinderResults.num(testPower, 1)
                + ": these numbers fit autonomous moves at that power");
        switch (which) {
            case CREEP:
                creepAxis = 0;
                creepForward = Double.NaN;
                creepRight = Double.NaN;
                startCreepAxis();
                break;
            case STOPS:
                stopsStep = 0;
                bestSlide = Double.MAX_VALUE;
                bestSlowDown = Double.NaN;
                firstSlide = Double.NaN;
                slowDownBefore = autoConfig.slowDownIn;
                autoConfig.slowDownIn = MoveMath.SLOW_DOWN_STEPS[0];
                queueStops();
                break;
            case TURNS:
                for (double d : MoveMath.TURNS_DEG) queue.add(Trial.turn(d));
                break;
            case DRIFT:
                queue.add(Trial.move(MoveMath.DRIFT_MOVE_IN, 0));
                queue.add(Trial.move(MoveMath.DRIFT_MOVE_IN, 180));
                if (mecanum()) {
                    queue.add(Trial.move(MoveMath.DRIFT_MOVE_IN, -90));
                    queue.add(Trial.move(MoveMath.DRIFT_MOVE_IN, 90));
                }
                break;
            default:
                // Field directions are from the heading at the start: zero it here.
                auto.resetHeading();
                double d = MoveMath.MOVE_TURN_IN;
                queue.add(Trial.moveTurn(d, -90, 90));   // right, turning to face left
                queue.add(Trial.moveTurn(d, 90, 0));     // back left, turning back
                queue.add(Trial.moveTurn(d, 90, -90));   // left, turning to face right
                queue.add(Trial.moveTurn(d, -90, 0));    // back right, turning back
                break;
        }
    }

    private void queueStops() {
        for (double d : MoveMath.STOP_MOVES_IN) {
            queue.add(Trial.move(d, 0));
            queue.add(Trial.move(d, 180));
        }
    }

    // Runs one move or turn. BLOCKS on purpose until it ends (like autonomous): STOP, GUIDE and START
    // end it at once. The screen keeps updating (the move's loop calls the hook).
    private void runTrial(final Trial tr) {
        tr.startTicks = auto.getWheelTicks();
        tr.lastTicks = tr.startTicks;
        tr.startHeading = auto.headingDeg();
        tr.startPose = odo != null ? odo.pose() : null;
        tr.startSec = seconds();
        tr.trace = new MoveMath.TurnTrace(autoConfig.turnToleranceDeg);
        if (tr.kind == 1) tr.target = Angles.wrapDegrees(tr.startHeading + tr.turnDeg);
        if (tr.kind == 2) tr.target = tr.targetHeadingDeg;
        auto.setStopCondition(this::helpKeyDown);
        auto.setLoopHook(() -> duringTrial(tr));
        try {
            if (tr.kind == 0) tr.result = auto.move(tr.distanceIn, tr.directionDeg, testPower);
            else if (tr.kind == 1) tr.result = auto.turn(tr.turnDeg, testPower, MoveMath.TURN_TIMEOUT_SEC);
            else tr.result = auto.moveWithTurn(tr.distanceIn, tr.directionDeg, tr.targetHeadingDeg, testPower, testPower);
        } finally {
            auto.setStopCondition(null);
            auto.setLoopHook(null);
        }
        tr.seconds = seconds() - tr.startSec;
        if (helpKeyDown() && running()) {
            abort("Stopped by {GUIDE}/{START}: not measured. Run the test again.");
            openHelp();
            return;
        }
        if (tr.result == AutoDrive.Result.STOPPED) {
            abort("Stopped: not measured.");
            return;
        }
        current = tr;
        still = new MoveMath.StillWatch(0.15, 1.0);
    }

    // Each loop of a move or turn: the odometry, what the heading does, the screen.
    private void duringTrial(Trial tr) {
        if (odo != null) odo.update();
        double error = Angles.shortestDiff(tr.target, auto.headingDeg());
        if (tr.kind == 1) {
            tr.trace.add(error);
        } else if (tr.kind == 2) {
            // How far it has gone (added up piece by piece, like moveWithTurn does).
            int[] now = auto.getWheelTicks();
            double[] step = auto.displacementIn(tr.lastTicks, now);
            tr.lastTicks = now;
            tr.traveledIn += Math.hypot(step[0], step[1]);
            if (tr.traveledIn < tr.distanceIn) {
                tr.trace.add(error);
            } else if (!tr.lagMeasured) {
                // The distance is done: how far is the heading still off? (Then it turns in place.)
                tr.lagDeg = Math.abs(error);
                tr.lagMeasured = true;
            }
        }
        showMoving();
    }

    private void measure(Trial tr) {
        double headingNow = auto.headingDeg();
        tr.headingChangeDeg = Angles.shortestDiff(headingNow, tr.startHeading);
        if (tr.kind == 0) {
            double[] travel;
            if (odo != null && tr.startPose != null) {
                FieldPose p = odo.pose();
                travel = MoveMath.robotTravel(tr.startPose.x, tr.startPose.y, tr.startPose.headingDeg, p.x, p.y);
            } else {
                travel = auto.displacementIn(tr.startTicks, auto.getWheelTicks());
            }
            tr.slideIn = MoveMath.along(travel[0], travel[1], tr.directionDeg) - tr.distanceIn;
        } else if (tr.kind == 1) {
            tr.endErrorDeg = Math.abs(Angles.shortestDiff(tr.target, headingNow));
        } else if (!tr.lagMeasured) {
            // It never got the whole way (stuck or out of time).
            tr.lagDeg = Math.abs(Angles.shortestDiff(tr.target, headingNow));
        }
    }

    // The test's moves are all done: work out the result.
    private void endOfQueue() {
        switch (runningTest) {
            case STOPS: endOfStopsStep(); return;
            case TURNS: endTurns(); break;
            case DRIFT: endDrift(); break;
            default: endMoveTurn(); break;
        }
        runningTest = -1;
    }

    // ---------------------------------------------------------------------------------------------
    // 1 Creep (does not block: one small power step per loop)
    // ---------------------------------------------------------------------------------------------

    private void startCreepAxis() {
        creepPower = MoveMath.CREEP_STEP;
        creepStepSec = seconds();
        creepTicks = auto.getWheelTicks();
        setCreepPower();
    }

    private void setCreepPower() {
        double[] p = creepAxis == 0 ? MecanumMixer.mix(creepPower, 0, 0, 1) : MecanumMixer.mix(0, creepPower, 0, 1);
        for (int i = 0; i < wheels.size(); i++) wheels.get(i).setPower(p[i]);
    }

    private void creepStep() {
        double now = seconds();
        double dt = now - creepStepSec;
        if (dt < MoveMath.CREEP_STEP_SEC) return;
        int[] ticks = auto.getWheelTicks();
        double[] step = auto.displacementIn(creepTicks, ticks);
        double speed = Math.abs(step[creepAxis]) / dt;
        if (speed >= MoveMath.CREEP_MOVING_IN_PER_SEC || creepPower >= MoveMath.CREEP_MAX_POWER) {
            boolean moved = speed >= MoveMath.CREEP_MOVING_IN_PER_SEC;
            auto.stop();
            if (creepAxis == 0) creepForward = moved ? creepPower : Double.NaN;
            else creepRight = moved ? creepPower : Double.NaN;
            if (creepAxis == 0 && mecanum()) {
                creepAxis = 1;
                startCreepAxis();
                return;
            }
            endCreep();
            return;
        }
        creepPower = MoveMath.round(creepPower + MoveMath.CREEP_STEP, 2);
        creepStepSec = now;
        creepTicks = ticks;
        setCreepPower();
    }

    private void endCreep() {
        runningTest = -1;
        report.add("Rolls forward at " + creepText(creepForward)
                + (mecanum() ? ", right at " + creepText(creepRight) : ""));
        double s = MoveMath.moveMinPowerFromCreep(creepForward, creepRight);
        if (Double.isNaN(s)) {
            report.add("It did not roll at " + FinderResults.num(MoveMath.CREEP_MAX_POWER, 1)
                    + ". Is something holding it? Check the wheels.");
            return;
        }
        setNumber("moveMinPower", s, 2);
        report.add("moveMinPower now " + FinderResults.num(s, 2) + " (with a margin). Next: Stops.");
    }

    private static String creepText(double power) {
        return Double.isNaN(power) ? "-" : FinderResults.num(power, 2);
    }

    // ---------------------------------------------------------------------------------------------
    // 2 Stops
    // ---------------------------------------------------------------------------------------------

    private void endOfStopsStep() {
        double worst = -Double.MAX_VALUE, slowest24 = 0;
        boolean stalled = false;
        for (Trial tr : done) {
            if (tr.result != AutoDrive.Result.DONE) stalled = true;
            worst = Math.max(worst, tr.slideIn);
            if (tr.distanceIn == 24) slowest24 = Math.max(slowest24, tr.seconds);
        }
        double slowDown = autoConfig.slowDownIn;
        report.add("slowDownIn " + FinderResults.num(slowDown, 0) + ": slide " + FinderResults.num(worst, 1)
                + " in, 24 in " + FinderResults.num(slowest24, 1) + " s" + (stalled ? ", STUCK" : ""));
        done.clear();
        if (stalled) {
            runningTest = -1;
            setNumber("slowDownIn", slowDown, 0);
            report.add("A move did not finish: it stalled near the end.");
            report.add("Run Creep (moveMinPower), or clear the path.");
            return;
        }
        // The smallest slowDownIn wins unless a bigger one stops clearly better (WHY: each inch of
        // slowing down costs time on every move).
        if (MoveMath.clearlyBetter(worst, bestSlide)) {
            bestSlide = worst;
            bestSlowDown = slowDown;
        }
        if (Double.isNaN(firstSlide)) firstSlide = worst;
        if (MoveMath.stopsOk(worst, false)) {
            runningTest = -1;
            setNumber("slowDownIn", slowDown, 0);
            report.add("OK: slowDownIn " + FinderResults.num(slowDown, 0) + ". Copy the lines.");
            if (odo == null) report.add("(Wheel counts: check one move with a tape.)");
            return;
        }
        stopsStep++;
        if (stopsStep < MoveMath.SLOW_DOWN_STEPS.length) {
            autoConfig.slowDownIn = MoveMath.SLOW_DOWN_STEPS[stopsStep];
            queueStops();
            return;
        }
        runningTest = -1;
        setNumber("slowDownIn", bestSlowDown, 0);
        report.add("None stopped within " + FinderResults.num(MoveMath.SLIDE_OK_IN, 1) + " in. Best: slowDownIn "
                + FinderResults.num(bestSlowDown, 0) + ".");
        if (!MoveMath.clearlyBetter(bestSlide, firstSlide)) {
            // Slowing down longer did not help: every move already arrives at moveMinPower.
            report.add("slowDownIn does not help: each move ends at moveMinPower "
                    + FinderResults.num(autoConfig.moveMinPower, 2) + ".");
            report.add("Run Creep for a lower moveMinPower, then Stops again.");
        } else {
            report.add("Try a lower power, or Creep for a lower moveMinPower.");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 3 Turns
    // ---------------------------------------------------------------------------------------------

    private void endTurns() {
        double overshoot = 0, endError = 0, slowest90 = 0;
        int swings = 0;
        boolean timedOut = false;
        for (Trial tr : done) {
            report.add((tr.turnDeg > 0 ? "+" : "") + FinderResults.num(tr.turnDeg, 0) + ": past " + FinderResults.num(tr.trace.overshootDeg, 1)
                    + ", off " + FinderResults.num(tr.endErrorDeg, 1) + " deg, " + FinderResults.num(tr.seconds, 1) + " s"
                    + (tr.result == AutoDrive.Result.DONE ? "" : " " + tr.result));
            overshoot = Math.max(overshoot, tr.trace.overshootDeg);
            swings = Math.max(swings, tr.trace.swings);
            endError = Math.max(endError, tr.endErrorDeg);
            if (tr.result != AutoDrive.Result.DONE) timedOut = true;
            if (Math.abs(tr.turnDeg) == 90) slowest90 = Math.max(slowest90, tr.seconds);
        }
        report.add("Worst: past " + FinderResults.num(overshoot, 1) + " deg, swings " + swings
                + ", end off " + FinderResults.num(endError, 1) + " deg");
        report.add("90 deg in " + FinderResults.num(slowest90, 1) + " s" + (timedOut ? ", a turn ran out of time" : ""));
        MoveMath.Suggestion s = MoveMath.suggestTurn(overshoot, swings, endError, timedOut, slowest90,
                autoConfig.turnKp, autoConfig.turnKd, autoConfig.turnMinPower, autoConfig.turnToleranceDeg);
        showSuggestion(s, "Turns");
    }

    // ---------------------------------------------------------------------------------------------
    // 4 Drift (report only)
    // ---------------------------------------------------------------------------------------------

    private void endDrift() {
        double worst = 0;
        for (Trial tr : done) {
            report.add(directionName(tr.directionDeg) + " " + FinderResults.num(tr.distanceIn, 0) + " in: heading "
                    + FinderResults.num(tr.headingChangeDeg, 1) + " deg, slide " + FinderResults.num(tr.slideIn, 1) + " in");
            worst = Math.max(worst, Math.abs(tr.headingChangeDeg));
        }
        if (worst <= MoveMath.DRIFT_OK_DEG) {
            report.add("OK: the heading stays within " + FinderResults.num(MoveMath.DRIFT_OK_DEG, 0) + " deg.");
        } else {
            report.add("The robot turns while it drives. Check the weight and the wheels;");
            report.add(mecanum() ? "  moveWithTurn holds the heading on long moves." : "  turn back (turnTo) after long moves.");
        }
    }

    private static String directionName(double deg) {
        return deg == 0 ? "Forward" : deg == 180 ? "Back" : deg == -90 ? "Right" : "Left";
    }

    // ---------------------------------------------------------------------------------------------
    // 5 Move and turn
    // ---------------------------------------------------------------------------------------------

    private void endMoveTurn() {
        double lag = 0, overshoot = 0;
        boolean stuck = false;
        for (Trial tr : done) {
            report.add(directionName(tr.directionDeg) + " to " + FinderResults.num(tr.targetHeadingDeg, 0) + " deg: "
                    + (tr.result == AutoDrive.Result.DONE ? "" : tr.result + ", ") + "off " + FinderResults.num(tr.lagDeg, 1)
                    + " deg at the end of the move");
            lag = Math.max(lag, tr.lagDeg);
            overshoot = Math.max(overshoot, tr.trace.overshootDeg);
            if (tr.result != AutoDrive.Result.DONE) stuck = true;
        }
        report.add("Worst: off " + FinderResults.num(lag, 1) + " deg, past target "
                + FinderResults.num(overshoot, 1) + " deg" + (stuck ? ", a move did not finish" : ""));
        MoveMath.Suggestion s = MoveMath.suggestMoveTurn(lag, overshoot, autoConfig.moveTurnScale, autoConfig.moveMaxTurnPower);
        showSuggestion(s, "Move and turn");
    }

    // ---------------------------------------------------------------------------------------------
    // Numbers and the screen
    // ---------------------------------------------------------------------------------------------

    private void showSuggestion(MoveMath.Suggestion s, String testName) {
        if (s.ok()) {
            report.add("OK: " + s.why + ".");
            return;
        }
        int decimals = s.field.startsWith("turnK") ? 4 : s.field.equals("turnToleranceDeg") ? 1 : 2;
        setNumber(s.field, s.value, decimals);
        report.add(s.field + " now " + FinderResults.num(s.value, decimals) + ": " + s.why + ".");
        report.add("Run " + testName + " again.");
    }

    // A new number: used at once (AutoDrive reads its settings on every move), and the lines to copy.
    private void setNumber(String field, double value, int decimals) {
        switch (field) {
            case "moveMinPower": autoConfig.moveMinPower = value; break;
            case "slowDownIn": autoConfig.slowDownIn = value; break;
            case "turnKp": autoConfig.turnKp = value; break;
            case "turnKd": autoConfig.turnKd = value; break;
            case "turnMinPower": autoConfig.turnMinPower = value; break;
            case "turnToleranceDeg": autoConfig.turnToleranceDeg = value; break;
            case "moveTurnScale": autoConfig.moveTurnScale = value; break;
            case "moveMaxTurnPower": autoConfig.moveMaxTurnPower = value; break;
            default: throw new IllegalArgumentException(field);
        }
        String v = FinderResults.num(value, decimals);
        results.set("move:" + field, "c." + field + " = " + v + ";   // RobotSettings.auto()");
        results.set("move:" + field + "5", "Tunables.Drive: " + field + " = " + v);
    }

    private void setReport(String... lines) {
        report.clear();
        for (String l : lines) report.add(l);
    }

    // Stops the test now (GUIDE, STOP, help): what it measured so far is not used.
    private void abort(String why) {
        auto.stop();
        queue.clear();
        done.clear();
        current = null;
        // Stopped in the middle of Stops: back to the number it had before (nothing was found).
        if (runningTest == STOPS) autoConfig.slowDownIn = slowDownBefore;
        runningTest = -1;
        report.clear();
        report.add(why);
    }

    // The screen while a move runs (about 10 times a second).
    private void showMoving() {
        long now = System.nanoTime();
        if (now - movingShownAt < 100_000_000L) return;
        movingShownAt = now;
        t.section("Move tuner");
        showRunning(t);
        t.line("Heading", FinderResults.num(auto.headingDeg(), 1) + " deg");
        t.update();
    }

    private void showRunning(RobotTelemetry t) {
        t.text(TESTS[runningTest] + ": running... ({GUIDE} or {START} stops it)");
        if (runningTest == CREEP) {
            t.line("Power", FinderResults.num(creepPower, 2) + (creepAxis == 0 ? " forward" : " right"));
        } else if (runningTest == STOPS) {
            t.line("Trying", "slowDownIn " + FinderResults.num(autoConfig.slowDownIn, 0));
        }
        for (String l : report) t.text(l);
    }

    @Override
    protected void show(RobotTelemetry t) {
        t.section("Move tuner");
        if (runningTest >= 0) {
            showRunning(t);
            return;
        }
        for (int i = 0; i < TESTS.length; i++) {
            if (!available(i)) continue;
            t.text((i == test ? "> " : "  ") + TESTS[i]);
        }
        t.text("D-pad up/down = choose, {A} = start");
        t.line("Power", FinderResults.num(testPower, 1) + " (D-pad left/right: your autos' power)");
        t.line("Now using", "moveMinPower " + FinderResults.num(autoConfig.moveMinPower, 2) + ", slowDownIn "
                + FinderResults.num(autoConfig.slowDownIn, 0));
        t.line("Turns", "Kp " + FinderResults.num(autoConfig.turnKp, 4) + ", Kd " + FinderResults.num(autoConfig.turnKd, 4)
                + ", min " + FinderResults.num(autoConfig.turnMinPower, 2) + ", tol " + FinderResults.num(autoConfig.turnToleranceDeg, 1));
        if (mecanum()) {
            t.line("Move and turn", "scale " + FinderResults.num(autoConfig.moveTurnScale, 2) + ", max "
                    + FinderResults.num(autoConfig.moveMaxTurnPower, 2));
        }
        t.line("Measures with", odo != null ? "the odometry" : "the wheel counts");
        if (!calibrated) t.warn("Run the Drive Tuner first, then set calibrated = true.");
        for (String l : report) t.text(l);
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "How exactly moves stop and turns end. Clear floor",
            "  5 ft around. Run it after the Drive Tuner.",
            "D-pad up/down = a test, l/r = power. {A} = start.",
            "{GUIDE} or {START} stops a test (not measured).",
            "1 Creep: power rises until it rolls: moveMinPower",
            "2 Stops: 6, 12, 24 in out and back; tries",
            "  slowDownIn 0 to 12 until the slide is small.",
            "3 Turns: 90, 135, 45 and back: turnKp/Kd/MinPower",
            "4 Drift: 48 in moves: heading change (report).",
            "5 Move and turn: sideways + 90 deg turn:",
            "  moveTurnScale, moveMaxTurnPower.",
            "Each new number is used at once: run the test",
            "  again until it says OK. Then copy the lines.",
        };
    }

    @Override
    protected void pause() {
        if (runningTest >= 0) abort("Paused: run the test again.");
        if (auto != null) auto.stop();
    }

    @Override
    protected void stopAll() {
        if (auto != null) auto.stop();
    }
}
