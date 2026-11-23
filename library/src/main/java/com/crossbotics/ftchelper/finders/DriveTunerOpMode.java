package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.drive.AutoDrive;
import com.crossbotics.ftchelper.drive.AutoDriveConfig;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the autonomous drive numbers in {@code RobotSettings.auto()}: the wheel size and (mecanum)
 * the strafe correction. Put 48 in of tape on the floor (as long as the test drive) and a tape measure next to it.
 *
 * <pre>
 * D-pad up/down = choose a test, D-pad left/right = the test power (0.3 to 1.0, starts 0.5), A = start it.
 *   Push:    roll the robot BY HAND exactly 48 in along the tape, then press B. (Wheel size.)
 *   Drive:   it drives 48 in forward. Measure, set the real distance with the D-pad, press A.
 *   Strafe:  it drives 48 in to the right (mecanum). Measure, set it, press A.
 *   Strafe left: the same, to the left (only if the robot slips differently that way: strafeCorrectionLeft).
 *   Turn:    it turns 90 left. Check it with a square; the screen shows the IMU's heading.
 * Distance:  D-pad up/down = +/- 1 in, right/left = +/- 0.25 in.
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * Each new number is used at once in this tuner (no rebuild): run the Drive and Strafe tests again
 * until they measure 48 (within 0.5 in: then it also shows {@code public static boolean calibrated = true;}),
 * then copy the lines into your code. The screen keeps updating while a test moves. Leave 5 ft of space in front of and to
 * the sides of the robot. A robot in five files gets lines for Robot.java ({@code .wheelMm(100.0)}) and
 * Tunables.java, class Drive. With {@code robotConfig()} they are for {@code RobotSettings.auto()}
 * ({@code c.wheelDiameterIn = 3.937;}).
 *
 * <p>In your TeamCode, the robot in five files (next to Robot.java):
 * <pre>{@code
 * @TeleOp(name = "Drive Tuner", group = "Finders")
 * public class DriveTuner extends DriveTunerOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 * With a RobotSettings class instead: {@code @Override protected RobotConfig robotConfig() { return new RobotSettings(); }}
 *
 * <p>The Drive, Strafe and Turn tests WAIT while the robot moves (like autonomous): STOP still works.
 *
 * Docs: docs/modules/finders.md. Guide: docs/tuning/README.md
 */
public abstract class DriveTunerOpMode extends FinderOpMode {

    // Its numbers are powers or speeds: they depend on the battery.
    @Override
    protected boolean batteryMatters() {
        return true;
    }

    private static final String[] TESTS = {"1 Push (by hand)", "2 Drive forward", "3 Strafe right (sideways)",
            "4 Strafe left (only if needed)", "5 Turn 90 left (check only)"};

    /**
     * Your robot in five files: {@code return new Robot();}. The tuner uses its drive (motors, wheel,
     * IMU and {@code Tunables.Drive}). Default null: then {@link #robotConfig()} must be given.
     */
    protected RobotDefinition robot() {
        return null;
    }

    /**
     * Your robot's settings (the same as TeleOp and autonomous), e.g. {@code return new RobotSettings();}.
     * Default: the drive of {@link #robot()}. Give one of the two.
     */
    protected RobotConfig robotConfig() {
        RobotDefinition r = robot();
        if (r == null) {
            throw new IllegalStateException("The drive tuner needs your robot: add "
                    + "\"@Override protected RobotDefinition robot() { return new Robot(); }\" (or robotConfig()).");
        }
        return r.robotConfig();
    }

    /** How far each distance test goes, inches. Default 48 (two tiles). */
    protected double testDistanceIn() {
        return 48;
    }

    private DriveConfig driveConfig;
    private AutoDriveConfig autoConfig;
    private AutoDrive auto;
    private boolean fiveFiles;   // the lines are for Robot.java and Tunables.java (else RobotSettings)
    private final List<DcMotorEx> wheels = new ArrayList<>();
    private int test = 0;
    // What the screen is waiting for: nothing, the end of a push, or a measured distance.
    private enum Wait { CHOOSE, PUSHING, MEASURE }
    private Wait waiting = Wait.CHOOSE;
    // The power the Drive and Strafe tests drive at (D-pad left/right on the test list). WHY: a faster
    // robot slides further when it stops, so the numbers fit the power they were measured at. Test at
    // the power your autonomous moves use.
    private double testPower = DEFAULT_POWER;
    /** The test power at START, and its range and step (D-pad left/right). */
    static final double DEFAULT_POWER = 0.5, MIN_POWER = 0.3, MAX_POWER = 1.0, POWER_STEP = 0.1;
    private int[] pushStart;
    private double measured;
    private String lastResult = "";
    // A test asked for by A, run in step() (not inside the button action, because it waits).
    private int pendingTest = -1;
    // The wheels' settings before the push test, given back after it.
    private final List<DcMotor.RunMode> savedModes = new ArrayList<>();
    private final List<DcMotor.ZeroPowerBehavior> savedZero = new ArrayList<>();
    // The Drive (and, mecanum, Strafe) test measured the test distance (within CLOSE_ENOUGH_IN): calibrated.
    private boolean driveRight = false, strafeRight = false;
    /** A test that measures this close to the test distance (inches) is right: no change needed. */
    static final double CLOSE_ENOUGH_IN = 0.5;
    // While a test moves: the last screen update (System.nanoTime), so the screen is not sent every loop.
    private long movingShownAt = 0;

    @Override protected String finderName() { return "Drive tuner"; }

    @Override
    protected void setup() {
        RobotConfig config = robotConfig();
        driveConfig = config.drive();
        autoConfig = config.auto();
        // AutoDrive refuses to run until the calibration is confirmed. This program is how you
        // check it, so it switches that safety off for itself only.
        autoConfig.calibrationConfirmed = true;
        IMU imu = config.imu(hardwareMap);
        if (imu == null) throw new IllegalStateException("The drive tuner needs the IMU: add .imu(...) to the drive in Robot.java (or RobotSettings.imu()).");
        // running(), not opModeIsActive(): inside "Tune and check" this finder is not the running OpMode.
        auto = new AutoDrive(this, driveConfig, autoConfig, imu, this::running);
        // The wheel motors, so the push test can let them roll freely.
        wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.frontLeft));
        wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.frontRight));
        if (driveConfig.hasBackMotors()) {
            wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.backLeft));
            wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.backRight));
        }
        measured = testDistanceIn();
        fiveFiles = hasRobotFiles() || robot() != null;
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.warn("The robot will DRIVE BY ITSELF after {A}.");
        t.warn("Clear 5 ft ahead and on both sides. Stand back.");
        t.warn("{GUIDE} or {START} stops a move.");
        t.text("Drive tuner: how far do the wheels really go?");
        // Two different lengths: the TAPE is as long as the test drive; the clear FLOOR is longer (it may overshoot).
        t.text("You need: a tape line " + FinderResults.num(testDistanceIn(), 0) + " in long on the floor.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> { if (waiting == Wait.MEASURE) measured += 1; else choosePrevious(); })
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> { if (waiting == Wait.MEASURE) measured = Math.max(1, measured - 1); else chooseNext(); })
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> { if (waiting == Wait.MEASURE) measured += 0.25; else changePower(+1); })
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> { if (waiting == Wait.MEASURE) measured = Math.max(1, measured - 0.25); else changePower(-1); })
         .bindPress(Pad.G1, Btn.A, this::pressA)
         .bindPress(Pad.G1, Btn.B, this::finishPush);
    }

    private boolean canStrafe() {
        return driveConfig.type == DriveConfig.DriveType.MECANUM;
    }

    // D-pad left/right on the test list: the test power, 0.3 to 1.0 in steps of 0.1. A new power needs
    // the Drive (and Strafe) test to measure right again before "calibrated" shows.
    private void changePower(int direction) {
        if (waiting != Wait.CHOOSE) return;
        double p = nextPower(testPower, direction);
        if (p == testPower) return;
        testPower = p;
        driveRight = false;
        strafeRight = false;
        writeCalibrated();
    }

    /** One step of the test power: up (direction +1) or down (-1), kept inside 0.3..1.0. */
    static double nextPower(double power, int direction) {
        double p = Math.round((power + direction * POWER_STEP) * 10) / 10.0;
        return Math.max(MIN_POWER, Math.min(MAX_POWER, p));
    }

    private void chooseNext() {
        if (waiting != Wait.CHOOSE) return;
        do { test = (test + 1) % TESTS.length; } while (isStrafe(test) && !canStrafe());
    }

    // The two strafe tests: right (2) and left (3).
    private static boolean isStrafe(int which) {
        return which == 2 || which == 3;
    }

    private void choosePrevious() {
        if (waiting != Wait.CHOOSE) return;
        do { test = (test + TESTS.length - 1) % TESTS.length; } while (isStrafe(test) && !canStrafe());
    }

    // A: start the chosen test, or confirm the measured distance.
    private void pressA() {
        if (waiting == Wait.MEASURE) {
            // The results file says which power the numbers were measured at: written when a measurement
            // is set (not when the move ends: a STOP before A would label the old numbers).
            results.set("drive:power", "Measured at power " + FinderResults.num(testPower, 1) + ": use it in autonomous.");
            // The new number is used at once (AutoDrive reads these settings on every move), so the
            // next test already uses it.
            boolean right = Math.abs(measured - testDistanceIn()) <= CLOSE_ENOUGH_IN;
            if (test == 1) driveRight = right;
            if (test == 2) strafeRight = right;
            if (right) {
                lastResult = (test == 1 ? "Drive" : test == 2 ? "Strafe" : "Strafe left") + ": " + FinderResults.num(measured, 2)
                        + " in. OK (within " + FinderResults.num(CLOSE_ENOUGH_IN, 1) + " in of " + FinderResults.num(testDistanceIn(), 0) + ")."
                        + (!driveRight ? "\nNext: 2 Drive forward."
                           : canStrafe() && !strafeRight ? "\nNext: 3 Strafe right." : "\nNext: copy the lines below.");
                waiting = Wait.CHOOSE;
                writeCalibrated();
                return;
            }
            if (test == 1) {
                double d = FinderMath.correctedDiameter(autoConfig.wheelDiameterIn, testDistanceIn(), measured);
                autoConfig.wheelDiameterIn = d;
                strafeRight = false;   // it was measured with the old wheel size
                setDiameterResult(d);
                lastResult = "Wheel size is now " + FinderResults.num(d, 3) + " in (not checked).\nNext: run 2 Drive forward again.";
            } else if (test == 2) {
                double s = FinderMath.correctedStrafe(autoConfig.strafeCorrection, testDistanceIn(), measured);
                autoConfig.strafeCorrection = s;
                setNumber("strafeCorrection", s);
                lastResult = "Strafe correction is now " + FinderResults.num(s, 3) + ".\nNext: run 3 Strafe right again.";
            } else if (test == 3) {
                // Left starts from the right's number until it has its own.
                double from = Double.isNaN(autoConfig.strafeCorrectionLeft) ? autoConfig.strafeCorrection : autoConfig.strafeCorrectionLeft;
                double s = FinderMath.correctedStrafe(from, testDistanceIn(), measured);
                autoConfig.strafeCorrectionLeft = s;
                setNumber("strafeCorrectionLeft", s);
                lastResult = "Strafe left correction is now " + FinderResults.num(s, 3) + ".\nNext: run 4 Strafe left again.";
            }
            waiting = Wait.CHOOSE;
            writeCalibrated();
            return;
        }
        if (waiting != Wait.CHOOSE) return;
        if (test == 0) {
            // Push: wheels free to roll (no speed control holding them, no brake), counts from here.
            savedModes.clear();
            savedZero.clear();
            for (DcMotorEx w : wheels) {
                savedModes.add(w.getMode());
                savedZero.add(w.getZeroPowerBehavior());
                w.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
                w.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
                w.setPower(0);
            }
            pushStart = auto.getWheelTicks();
            waiting = Wait.PUSHING;
        } else {
            pendingTest = test;   // step() runs it
        }
    }

    // Once the Drive test (and on a mecanum the Strafe test) measures right: the line that lets autonomous run.
    private void writeCalibrated() {
        results.remove("drive:calibrated");
        if (!driveRight) {
            results.notDone("run 2 Drive forward until it says OK.");
        } else if (canStrafe() && !strafeRight) {
            results.notDone("run 3 Strafe right until it says OK.");
        } else {
            results.done();
            // It lets autonomous run: only while the last measurements are right.
            if (fiveFiles) results.setTunable("drive:calibrated", "Drive", "calibrated", "true");
            else results.setIn("drive:calibrated", SETTINGS, "c.calibrationConfirmed = true;");
        }
    }

    private static final String SETTINGS = "In RobotSettings.java, in auto():";

    // A found number, as a line for the place this project keeps it.
    private void setNumber(String field, double value) {
        String v = FinderResults.num(value, 3);
        if (fiveFiles) results.setTunable("drive:" + field, "Drive", field, v);
        else results.setIn("drive:" + field, SETTINGS, "c." + field + " = " + v + ";");
    }

    // Runs a test that moves the robot. BLOCKS on purpose until the move is done. STOP and GUIDE
    // both end it at once (GUIDE then opens the help page; the test is not measured). The screen keeps
    // updating while it moves (the move's loop calls showMoving).
    private void runTest(int which) {
        auto.setStopCondition(this::helpKeyDown);
        final String name = TESTS[which];
        auto.setLoopHook(() -> showMoving(name));
        try {
            runMove(which);
        } finally {
            auto.setStopCondition(null);
            auto.setLoopHook(null);
            // SAFETY: a button pressed during the move must not confirm or start anything when it ends.
            waitForRelease();
        }
        if (helpKeyDown() && running()) {
            lastResult = "You stopped it: not measured.\nNext: clear the path and run it again.";
            waiting = Wait.CHOOSE;
            openHelp();
        }
    }

    private void runMove(int which) {
        // A test run again is not right until it is measured right again.
        if (which == 1) driveRight = false;
        if (which == 2) strafeRight = false;
        if (which == 1 || which == 2) writeCalibrated();
        switch (which) {
            case 1:
                afterMove("Drive", auto.move(testDistanceIn(), 0, testPower));
                break;
            case 2:
                afterMove("Strafe", auto.move(testDistanceIn(), -90, testPower));
                break;
            case 3:
                afterMove("Strafe left", auto.move(testDistanceIn(), 90, testPower));
                break;
            default:
                auto.resetHeading();
                AutoDrive.Result turned = auto.turn(90, 0.5);
                lastResult = turned == AutoDrive.Result.DONE
                        ? "Turn finished. The robot read " + FinderResults.num(auto.headingDeg(), 1) + " deg (90 is right)."
                          + "\nNext: check it with a square on the floor."
                        : "The turn " + howItEnded(turned) + ".\nNext: clear the floor and run it again.";
                break;
        }
    }

    // The screen while a test moves (about 10 times a second): what it does, the heading, the wheel counts.
    private void showMoving(String name) {
        long now = System.nanoTime();
        if (now - movingShownAt < 100_000_000L) return;
        movingShownAt = now;
        t.warn("The robot is moving. {GUIDE} or {START} = stop.");
        t.section("Drive tuner: moving");
        t.text(name);
        t.line("Turn angle", FinderResults.num(auto.headingDeg(), 1) + " deg");
        t.update();
    }

    // Only a move that finished normally can be measured: a stuck or timed-out move did not try to
    // go the whole distance, so measuring it would give a wrong number.
    private void afterMove(String name, AutoDrive.Result result) {
        measured = testDistanceIn();
        if (result == AutoDrive.Result.DONE) {
            measuring = name;
            lastResult = "";
            waiting = Wait.MEASURE;
        } else {
            lastResult = name + " " + howItEnded(result) + ": not measured.\nNext: clear the path and run it again.";
        }
    }

    private String measuring = "";   // the move that is being measured, for the screen

    // The wheel size, for RobotSettings and for the robot in five files (Robot.java takes millimeters).
    private void setDiameterResult(double inches) {
        if (fiveFiles) {
            results.setIn("drive:diameter", FinderResults.ROBOT_FILE,
                    "In drive(...): .wheelMm(" + FinderResults.num(inches * 25.4, 1) + ")");
        } else {
            setNumber("wheelDiameterIn", inches);
        }
    }

    // B: the push is done. Wheel size from the counts.
    private void finishPush() {
        if (waiting != Wait.PUSHING) return;
        int[] now = auto.getWheelTicks();
        int[] deltas = new int[now.length];
        for (int i = 0; i < now.length; i++) deltas[i] = now[i] - pushStart[i];
        // The wheels get their settings back.
        for (int i = 0; i < wheels.size(); i++) {
            wheels.get(i).setMode(savedModes.get(i));
            wheels.get(i).setZeroPowerBehavior(savedZero.get(i));
        }
        waiting = Wait.CHOOSE;
        double avgTicks = FinderMath.pushTicks(deltas);
        if (Double.isNaN(avgTicks)) {
            lastResult = "The wheels disagree: not measured."
                    + "\nCounts " + java.util.Arrays.toString(deltas)
                    + "\nNext: run Motor Direction Check, Encoder Check.";
            return;
        }
        if (avgTicks < 0) {
            lastResult = "Every wheel counted DOWN: not measured."
                    + "\nPush it FORWARD. The same again?"
                    + "\nNext: run Motor Direction Check.";
            return;
        }
        double d = FinderMath.wheelDiameterFromPush(avgTicks, testDistanceIn(), autoConfig.ticksPerRev, autoConfig.gearRatio);
        autoConfig.wheelDiameterIn = d;
        // A new wheel size: the moves measured with the old one prove nothing now.
        driveRight = false;
        strafeRight = false;
        setDiameterResult(d);
        writeCalibrated();
        lastResult = "Wheel size is now " + FinderResults.num(d, 3) + " in.\nNext: run 2 Drive forward to check it.";
    }

    @Override
    protected void step() {
        if (pendingTest < 0) return;
        int which = pendingTest;
        pendingTest = -1;
        runTest(which);
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (waiting == Wait.PUSHING) {
            t.warn("No motor power: you move the robot by hand.");
            t.section("Drive tuner: push");
            t.text("Next: roll the robot BY HAND along the tape,");
            t.text("exactly " + FinderResults.num(testDistanceIn(), 0) + " in. Then press {B}.");
            int[] now = auto.getWheelTicks();
            int[] deltas = new int[now.length];
            for (int i = 0; i < now.length; i++) deltas[i] = now[i] - pushStart[i];
            // Each wheel: they should all go up by about the same amount.
            t.line("Wheel counts", java.util.Arrays.toString(deltas));
            t.text("All should go up by about the same.");
        } else if (waiting == Wait.MEASURE) {
            t.warn("It stopped. No move starts until you measure.");
            t.section(measuring + ": done. Now measure it");
            if (isStrafe(test)) {
                t.text("Next: measure straight across, from the tape to");
                t.text("the " + (test == 2 ? "left" : "right") + " wheels. Set that number.");
            } else {
                t.text("Next: measure from the start mark to the same");
                t.text("edge of the same wheel now. Set that number.");
            }
            t.line("Your number", FinderResults.num(measured, 2) + " in");
            t.text("D-pad up/down = 1 in, right/left = 0.25 in.");
            t.text("{A} = use your number.");
        } else {
            t.warn("{A} starts a move by itself. Stand back.");
            t.warn("{GUIDE} or {START} stops a move.");
            t.section("Drive tuner: choose a test");
            if (lastResult.isEmpty()) t.text("Next: put it on the start mark. Do test 1.");
            else textLines(t, lastResult);
            for (int i = 0; i < TESTS.length; i++) {
                if (isStrafe(i) && !canStrafe()) continue;
                t.text((i == test ? "> " : "  ") + TESTS[i]);
            }
            t.text("D-pad up/down = choose, {A} = start");
            t.line("Power", FinderResults.num(testPower, 1) + "  (D-pad left/right)");
        }
        t.debug("In use now", "wheel " + FinderResults.num(autoConfig.wheelDiameterIn, 3) + " in, strafe "
                + FinderResults.num(autoConfig.strafeCorrection, 3));
    }

    @Override
    protected String[] helpLines() {
        String d = FinderResults.num(testDistanceIn(), 0) + " in";
        return new String[] {
            "Finds how far the wheels really go.",
            "Clear floor 5 ft ahead and on both sides.",
            "{GUIDE} or {START} stops a move (not measured).",
            "Start each test with a wheel edge on the mark.",
            "1 Push: {A}, roll it by hand " + d + ", {B}.",
            "2 Drive: it drives " + d + ". You measure to",
            "  the same wheel edge, set that, press {A}.",
            "3 Strafe = drive sideways (mecanum only).",
            "4 Left: only if it slips more that way.",
            "5 Turn: it turns 90 left. Check with a square.",
            "Repeat 2 and 3 until they say OK.",
            "Power: the same as your autonomous moves.",
            "Copy the lines when it says DONE.",
        };
    }

    // Help open: stop the wheels, but during a push leave them free to roll (nothing is powered).
    @Override
    protected void pause() {
        if (waiting != Wait.PUSHING && auto != null) auto.stop();
    }

    @Override
    protected void stopAll() {
        if (auto != null) auto.stop();
    }
}
