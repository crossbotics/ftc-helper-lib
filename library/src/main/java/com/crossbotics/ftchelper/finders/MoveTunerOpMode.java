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
 * Each new number is used at once in this tuner (no rebuild). Each test ends PASSED, RUN AGAIN or CAN'T FIX
 * (and says what to do): run it again until it says PASSED, then copy the lines when the screen says DONE. It measures with the odometry when the robot has it (it sees a
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

    private static final String[] TESTS = {"1 Creep: least power to move", "2 Stops: where a move ends",
            "3 Turns: how a turn ends", "4 Drift: is it straight (check only)", "5 Move and turn: sideways and turning"};
    private static final String[] NAMES = {"1 Creep", "2 Stops", "3 Turns", "4 Drift", "5 Move and turn"};
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
    // The same test's numbers in detail (each move or turn): under the lines to copy.
    private final List<String> details = new ArrayList<>();
    private long movingShownAt = 0;
    private boolean fiveFiles;   // the lines are for Tunables.java (else RobotSettings)
    // Each number-finding test that ran: did its last run pass?
    private final boolean[] ran = new boolean[5], passed = new boolean[5];

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
        fiveFiles = hasRobotFiles() || (config == null && robot != null);
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
        t.warn("The robot will DRIVE and TURN BY ITSELF.");
        t.warn("Clear 5 ft all around. Everybody back.");
        t.warn("{GUIDE} or {START} stops any test.");
        t.text("Move tuner: makes autonomous moves stop");
        t.text("in the right place.");
        if (!calibrated) {
            t.warn("Run the Drive Tuner first.");
            t.warn("Then set calibrated = true in class Drive.");
        }
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
            setReport("Not started. Run the Drive Tuner first.",
                    "Then set calibrated = true in Tunables.java,",
                    "class Drive. Install it. Come back here.");
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
        // SAFETY: START or GUIDE ends every test at once, also the Creep and the wait between two moves
        // (the moves that wait watch the same keys themselves).
        if (runningTest >= 0 && helpKeyDown()) {
            abort("You stopped it: not measured.", "Next: run the test again.");
            openHelp();
            return;
        }
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
        details.clear();
        done.clear();
        queue.clear();
        runningTest = which;
        if (which != DRIFT) {
            ran[which] = true;
            passed[which] = false;   // until this run passes
        }
        updateStatus();
        results.set("move:power", "Measured at power " + FinderResults.num(testPower, 1) + ": use it in autonomous.");
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
            // SAFETY: a button pressed during the move must not start anything when it ends.
            waitForRelease();
        }
        tr.seconds = seconds() - tr.startSec;
        if (helpKeyDown() && running()) {
            abort("You stopped it: not measured.", "Next: run the test again.");
            openHelp();
            return;
        }
        if (tr.result == AutoDrive.Result.STOPPED) {
            abort("Stopped: not measured.", "Next: run the test again.");
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
        double s = MoveMath.moveMinPowerFromCreep(creepForward, creepRight);
        String stuck = Double.isNaN(creepForward) ? "forward" : mecanum() && Double.isNaN(creepRight) ? "to the right" : null;
        if (!Double.isNaN(s)) setNumber("moveMinPower", s, 2);
        else forget("moveMinPower");
        if (stuck != null) {
            ended(CREEP, MoveMath.Outcome.RUN_AGAIN, "it did not roll " + stuck + " at " + FinderResults.num(MoveMath.CREEP_MAX_POWER, 1),
                    "Next: free it, check the wheels, run 1 again.");
        } else {
            ended(CREEP, MoveMath.Outcome.PASSED, "moveMinPower is now " + FinderResults.num(s, 2),
                    "(a little extra, for a low battery)", "Next: 2 Stops.");
        }
        details.add("It starts to roll at power " + creepText(creepForward) + " forward"
                + (mecanum() ? "," : "."));
        if (mecanum()) details.add("  and " + creepText(creepRight) + " to the right.");
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
        details.add("slowDownIn " + FinderResults.num(slowDown, 0) + ": slide " + pastEnd(worst) + ", "
                + FinderResults.num(slowest24, 1) + " s");
        done.clear();
        if (stalled) {
            runningTest = -1;
            // A run that did not finish found nothing: the number from before, and no line to copy.
            autoConfig.slowDownIn = slowDownBefore;
            forget("slowDownIn");
            ended(STOPS, MoveMath.Outcome.RUN_AGAIN, "a move did not finish",
                    "Next: clear the path. Still stuck? Run 1 Creep.");
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
            if (odo == null) {
                ended(STOPS, MoveMath.Outcome.PASSED, "slowDownIn " + FinderResults.num(slowDown, 0),
                        "Wheel counts miss a skid: check with a tape.", "Next: 3 Turns.");
            } else {
                ended(STOPS, MoveMath.Outcome.PASSED, "slowDownIn " + FinderResults.num(slowDown, 0), "Next: 3 Turns.");
            }
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
        String why = "none stopped within " + FinderResults.num(MoveMath.SLIDE_OK_IN, 1) + " in";
        String best = "The best was slowDownIn " + FinderResults.num(bestSlowDown, 0) + ".";
        if (!MoveMath.clearlyBetter(bestSlide, firstSlide)) {
            // Slowing down longer did not help: every move already arrives at moveMinPower.
            ended(STOPS, MoveMath.Outcome.CANT_FIX, why, best,
                    "Slowing longer does not help.",
                    "Next: run 1 Creep, then 2 Stops again.");
        } else {
            ended(STOPS, MoveMath.Outcome.CANT_FIX, why, best,
                    "Next: use less power, or run 1 Creep first.");
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
            details.add("Turn " + FinderResults.num(Math.abs(tr.turnDeg), 0) + (tr.turnDeg > 0 ? " left" : " right")
                    + ": past " + FinderResults.num(tr.trace.overshootDeg, 1)
                    + ", off " + FinderResults.num(tr.endErrorDeg, 1) + " deg, " + FinderResults.num(tr.seconds, 1) + " s");
            if (tr.result != AutoDrive.Result.DONE) details.add("  That turn " + howItEnded(tr.result) + ".");
            overshoot = Math.max(overshoot, tr.trace.overshootDeg);
            swings = Math.max(swings, tr.trace.swings);
            endError = Math.max(endError, tr.endErrorDeg);
            if (tr.result != AutoDrive.Result.DONE) timedOut = true;
            if (Math.abs(tr.turnDeg) == 90) slowest90 = Math.max(slowest90, tr.seconds);
        }
        report.add("Worst turn: past " + verdict(overshoot, MoveMath.OVERSHOOT_OK_DEG)
                + ", off " + verdict(endError, autoConfig.turnToleranceDeg));
        details.add("Turn 90 took " + FinderResults.num(slowest90, 1) + " s (limit " + FinderResults.num(MoveMath.SLOW_90_SEC, 0)
                + "). Swings: " + swings + ".");
        MoveMath.Suggestion s = MoveMath.suggestTurn(overshoot, swings, endError, timedOut, slowest90,
                autoConfig.turnKp, autoConfig.turnKd, autoConfig.turnMinPower, autoConfig.turnToleranceDeg);
        showSuggestion(s, TURNS, "Next: 4 Drift.");
    }

    // ---------------------------------------------------------------------------------------------
    // 4 Drift (report only)
    // ---------------------------------------------------------------------------------------------

    private void endDrift() {
        double worst = 0;
        boolean stuck = false;
        for (Trial tr : done) {
            details.add(directionName(tr.directionDeg) + " " + FinderResults.num(tr.distanceIn, 0) + " in: turned "
                    + FinderResults.num(Math.abs(tr.headingChangeDeg), 1) + " deg, " + pastEnd(tr.slideIn));
            worst = Math.max(worst, Math.abs(tr.headingChangeDeg));
            if (tr.result != AutoDrive.Result.DONE) stuck = true;
        }
        if (stuck) {
            ended(DRIFT, MoveMath.Outcome.RUN_AGAIN, "a move did not finish", "Next: clear the path. Run 4 again.");
        } else if (worst <= MoveMath.DRIFT_OK_DEG) {
            ended(DRIFT, MoveMath.Outcome.PASSED, "the heading stays within " + FinderResults.num(MoveMath.DRIFT_OK_DEG, 0) + " deg",
                    mecanum() ? "Next: 5 Move and turn." : "Next: copy the lines when it says DONE.");
        } else {
            ended(DRIFT, MoveMath.Outcome.CANT_FIX, "it turns while it drives",
                    "Next: check the weight and the wheels. Or:",
                    mecanum() ? "  moveWithTurn holds the angle on long moves." : "  turn back (turnTo) after long moves.");
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
            double to = tr.targetHeadingDeg;
            details.add(directionName(tr.directionDeg)
                    + (to == 0 ? ", turning back" : ", turning " + FinderResults.num(Math.abs(to), 0) + (to > 0 ? " left" : " right"))
                    + ": off " + FinderResults.num(tr.lagDeg, 1) + " deg");
            if (tr.result != AutoDrive.Result.DONE) details.add("  That move " + howItEnded(tr.result) + ".");
            lag = Math.max(lag, tr.lagDeg);
            overshoot = Math.max(overshoot, tr.trace.overshootDeg);
            if (tr.result != AutoDrive.Result.DONE) stuck = true;
        }
        report.add("Worst: off " + verdict(lag, MoveMath.TURN_LAG_OK_DEG)
                + ", past " + verdict(overshoot, MoveMath.MOVE_TURN_OVERSHOOT_OK_DEG));
        MoveMath.Suggestion s = MoveMath.suggestMoveTurn(lag, overshoot, autoConfig.moveTurnScale, autoConfig.moveMaxTurnPower, stuck);
        showSuggestion(s, MOVE_TURN, "Next: copy the lines when it says DONE.");
    }

    // ---------------------------------------------------------------------------------------------
    // Numbers and the screen
    // ---------------------------------------------------------------------------------------------

    private void showSuggestion(MoveMath.Suggestion s, int which, String afterPass) {
        String again = "Next: run " + NAMES[which] + " again.";
        if (s.outcome == MoveMath.Outcome.PASSED) {
            ended(which, s.outcome, s.why, afterPass);
        } else if (s.outcome == MoveMath.Outcome.CANT_FIX) {
            // No number fixes it: the ones tried on the way did not pass, so they are not lines to copy.
            forget("moveTurnScale");
            forget("moveMaxTurnPower");
            ended(which, s.outcome, s.why, "Next: use less move power. Or turn first,", "then move (two steps).");
        } else if (s.field == null) {
            ended(which, s.outcome, s.why, "Next: clear the path.", again);
        } else {
            int decimals = s.field.startsWith("turnK") ? 4 : s.field.equals("turnToleranceDeg") ? 1 : 2;
            setNumber(s.field, s.value, decimals);
            ended(which, s.outcome, s.why, s.field + " is now " + FinderResults.num(s.value, decimals) + ".", again);
        }
    }

    // A test ended: its verdict and what to do go first on the screen, the numbers measured after them.
    private void ended(int which, MoveMath.Outcome outcome, String why, String... whatToDo) {
        List<String> top = new ArrayList<>();
        String line = outcome.words + ": " + why + ".";
        if (line.length() <= FinderResults.SCREEN_WIDTH) {
            top.add(line);
        } else {
            top.add(outcome.words + ":");
            top.add("  " + why + ".");
        }
        java.util.Collections.addAll(top, whatToDo);
        report.addAll(0, top);
        passed[which] = outcome == MoveMath.Outcome.PASSED;
        updateStatus();
    }

    private void updateStatus() {
        for (int i = 0; i < NAMES.length; i++) {
            if (ran[i] && !passed[i]) {
                results.notDone(NAMES[i] + " has not passed.");
                return;
            }
        }
        for (boolean r : ran) {
            if (r) {
                results.done();
                return;
            }
        }
    }

    // "2.1 deg OK" or "5.4 deg too much": every number with its verdict.
    private static String verdict(double value, double limit) {
        return FinderResults.num(value, 1) + " deg" + (value <= limit ? " OK" : " too much");
    }

    private static String pastEnd(double slideIn) {
        return FinderResults.num(Math.abs(slideIn), 1) + (slideIn < 0 ? " in short" : " in past");
    }

    // No line to copy for this number.
    private void forget(String field) {
        results.remove("move:" + field);
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
        if (fiveFiles) results.setTunable("move:" + field, "Drive", field, v);
        else results.setIn("move:" + field, "In RobotSettings.java, in auto():", "c." + field + " = " + v + ";");
    }

    private void setReport(String... lines) {
        report.clear();
        details.clear();
        for (String l : lines) report.add(l);
    }

    // Stops the test now (GUIDE, STOP, help): what it measured so far is not used.
    private void abort(String... why) {
        auto.stop();
        queue.clear();
        done.clear();
        current = null;
        // Stopped in the middle of Stops: back to the number it had before (nothing was found).
        if (runningTest == STOPS) autoConfig.slowDownIn = slowDownBefore;
        runningTest = -1;
        report.clear();
        details.clear();
        java.util.Collections.addAll(report, why);
    }

    // The screen while a move runs (about 10 times a second).
    private void showMoving() {
        long now = System.nanoTime();
        if (now - movingShownAt < 100_000_000L) return;
        movingShownAt = now;
        showRunning(t);
        t.line("Turn angle", FinderResults.num(auto.headingDeg(), 1) + " deg");
        t.update();
    }

    private void showRunning(RobotTelemetry t) {
        t.warn("The robot moves. {GUIDE} or {START} = stop.");
        t.section("Move tuner: test " + (runningTest + 1));
        t.text(NAMES[runningTest] + ": running... Stand back.");
        if (runningTest == CREEP) {
            t.line("Power", FinderResults.num(creepPower, 2) + (creepAxis == 0 ? " forward" : " to the right"));
        } else if (runningTest == STOPS) {
            t.line("Trying", "slowDownIn " + FinderResults.num(autoConfig.slowDownIn, 0));
            for (String l : details) t.text(l);
        }
    }

    @Override
    boolean showsResults() {
        return runningTest < 0;
    }

    @Override
    void showDetails(RobotTelemetry t) {
        if (runningTest >= 0 || details.isEmpty()) return;
        t.section("Details of the last test");
        for (String l : details) t.text(l);
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (runningTest >= 0) {
            showRunning(t);
            return;
        }
        t.warn("{A} starts moves by itself. Stand back.");
        t.warn("{GUIDE} or {START} stops any test.");
        if (!calibrated) t.warn("Run the Drive Tuner first.");
        t.section("Move tuner: choose a test");
        if (report.isEmpty()) {
            t.text("Next: do the tests in order, from 1.");
            for (int i = 0; i < TESTS.length; i++) {
                if (!available(i)) continue;
                t.text((i == test ? "> " : "  ") + TESTS[i]);
            }
        } else {
            for (String l : report) t.text(l);
            // One line: the last result needs the screen.
            t.text("> " + TESTS[test]);
        }
        t.text("D-pad up/down = choose, {A} = start");
        t.line("Power", FinderResults.num(testPower, 1) + "  (D-pad left/right)");
        t.debug("In use", "moveMinPower " + FinderResults.num(autoConfig.moveMinPower, 2) + ", slowDownIn "
                + FinderResults.num(autoConfig.slowDownIn, 0));
        t.debug("Measures with", odo != null ? "the odometry" : "the wheel counts");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Makes moves stop in the right place.",
            "Do the Drive Tuner first. Clear 5 ft all around.",
            "{GUIDE} or {START} stops any test (not measured).",
            "D-pad left/right = power, as your autonomous.",
            "1 Creep: least power to roll (moveMinPower).",
            "2 Stops: the slide past the end (slowDownIn).",
            "3 Turns: turnKp, turnKd, turnMinPower,",
            "   turnToleranceDeg. One change each run.",
            "4 Drift: does it stay straight (check only).",
            "5 Move and turn: moveTurnScale,",
            "   moveMaxTurnPower (mecanum only).",
            "Run a test again until it says PASSED.",
            "New numbers are used at once. Copy at DONE.",
        };
    }

    @Override
    protected void pause() {
        if (runningTest >= 0) abort("Stopped for help: not measured.", "Next: run the test again.");
        if (auto != null) auto.stop();
    }

    @Override
    protected void stopAll() {
        if (auto != null) auto.stop();
    }
}
