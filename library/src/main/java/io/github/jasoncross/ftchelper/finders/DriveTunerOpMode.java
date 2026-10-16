package io.github.jasoncross.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.drive.AutoDrive;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the autonomous drive numbers in {@code RobotSettings.auto()}: the wheel size and (mecanum)
 * the strafe correction. Put tape on the floor and a tape measure next to it.
 *
 * <pre>
 * D-pad up/down = choose a test, A = start it.
 *   Push:    roll the robot BY HAND exactly 48 in along the tape, then press B. (Wheel size.)
 *   Drive:   it drives 48 in forward. Measure, set the real distance with the D-pad, press A.
 *   Strafe:  it drives 48 in to the right (mecanum). Measure, set it, press A.
 *   Turn:    it turns 90 left. Check it with a square; the screen shows the IMU's heading.
 * Distance:  D-pad up/down = +/- 1 in, right/left = +/- 0.25 in.
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * Each new number is used at once in this tuner (no rebuild): run the Drive and Strafe tests again
 * until they measure 48, then copy the lines into RobotSettings. Leave 5 ft of space in front of and to the right of the robot.
 * In your TeamCode: {@code @TeleOp(name = "Drive Tuner", group = "Finders") public class DriveTuner
 * extends DriveTunerOpMode { protected RobotConfig robotConfig() { return new RobotSettings(); } }}
 *
 * <p>The Drive, Strafe and Turn tests WAIT while the robot moves (like autonomous): STOP still works.
 *
 * Docs: docs/modules/finders.md. Guide: docs/guides/finders.md
 */
public abstract class DriveTunerOpMode extends FinderOpMode {

    private static final String[] TESTS = {"Push (by hand)", "Drive forward", "Strafe right", "Turn 90 left"};

    /** Your robot's settings (the same as TeleOp and autonomous). */
    protected abstract RobotConfig robotConfig();

    /** How far each distance test goes, inches. Default 48 (two tiles). */
    protected double testDistanceIn() {
        return 48;
    }

    private DriveConfig driveConfig;
    private AutoDriveConfig autoConfig;
    private AutoDrive auto;
    private final List<DcMotorEx> wheels = new ArrayList<>();
    private int test = 0;
    // What the screen is waiting for: nothing, the end of a push, or a measured distance.
    private enum Wait { CHOOSE, PUSHING, MEASURE }
    private Wait waiting = Wait.CHOOSE;
    private int[] pushStart;
    private double measured;
    private String lastResult = "";
    // A test asked for by A, run in step() (not inside the button action, because it waits).
    private int pendingTest = -1;
    // The wheels' settings before the push test, given back after it.
    private final List<DcMotor.RunMode> savedModes = new ArrayList<>();
    private final List<DcMotor.ZeroPowerBehavior> savedZero = new ArrayList<>();

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
        if (imu == null) throw new IllegalStateException("The drive tuner needs the IMU: RobotSettings.imu() returned null.");
        auto = new AutoDrive(this, driveConfig, autoConfig, imu);
        // The wheel motors, so the push test can let them roll freely.
        wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.frontLeft));
        wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.frontRight));
        if (driveConfig.hasBackMotors()) {
            wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.backLeft));
            wheels.add(hardwareMap.get(DcMotorEx.class, driveConfig.backRight));
        }
        measured = testDistanceIn();
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.text("Drive tuner: wheel size and strafe correction for autonomous.");
        t.text("Tape on the floor, 5 ft of space ahead and to the right. Press START.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> { if (waiting == Wait.MEASURE) measured += 1; else choosePrevious(); })
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> { if (waiting == Wait.MEASURE) measured = Math.max(1, measured - 1); else chooseNext(); })
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> { if (waiting == Wait.MEASURE) measured += 0.25; })
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> { if (waiting == Wait.MEASURE) measured = Math.max(1, measured - 0.25); })
         .bindPress(Pad.G1, Btn.A, this::pressA)
         .bindPress(Pad.G1, Btn.B, this::finishPush);
    }

    private boolean canStrafe() {
        return driveConfig.type == DriveConfig.DriveType.MECANUM;
    }

    private void chooseNext() {
        if (waiting != Wait.CHOOSE) return;
        do { test = (test + 1) % TESTS.length; } while (test == 2 && !canStrafe());
    }

    private void choosePrevious() {
        if (waiting != Wait.CHOOSE) return;
        do { test = (test + TESTS.length - 1) % TESTS.length; } while (test == 2 && !canStrafe());
    }

    // A: start the chosen test, or confirm the measured distance.
    private void pressA() {
        if (waiting == Wait.MEASURE) {
            // The new number is used at once (AutoDrive reads these settings on every move), so the
            // next test already uses it.
            if (test == 1) {
                double d = FinderMath.correctedDiameter(autoConfig.wheelDiameterIn, testDistanceIn(), measured);
                autoConfig.wheelDiameterIn = d;
                results.set("drive:diameter", "c.wheelDiameterIn = " + FinderResults.num(d, 3) + ";   // RobotSettings.auto()");
                lastResult = "Wheel size now " + FinderResults.num(d, 3) + " in. Run the Drive test again.";
            } else if (test == 2) {
                double s = FinderMath.correctedStrafe(autoConfig.strafeCorrection, testDistanceIn(), measured);
                autoConfig.strafeCorrection = s;
                results.set("drive:strafe", "c.strafeCorrection = " + FinderResults.num(s, 3) + ";   // RobotSettings.auto()");
                lastResult = "Strafe correction now " + FinderResults.num(s, 3) + ". Run the Strafe test again.";
            }
            waiting = Wait.CHOOSE;
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

    // Runs a test that moves the robot. BLOCKS on purpose until the move is done. STOP and GUIDE
    // both end it at once (GUIDE then opens the help page; the test is not measured).
    private void runTest(int which) {
        auto.setStopCondition(this::helpKeyDown);
        try {
            runMove(which);
        } finally {
            auto.setStopCondition(null);
        }
        if (helpKeyDown() && opModeIsActive()) {
            lastResult = "Stopped by GUIDE/START: not measured.";
            waiting = Wait.CHOOSE;
            openHelp();
        }
    }

    private void runMove(int which) {
        switch (which) {
            case 1:
                afterMove("Drive", auto.move(testDistanceIn(), 0, 0.5));
                break;
            case 2:
                afterMove("Strafe", auto.move(testDistanceIn(), 90, 0.5));
                break;
            default:
                auto.resetHeading();
                lastResult = "Turn: " + auto.turn(90, 0.5) + ", IMU heading "
                        + FinderResults.num(auto.headingDeg(), 1) + " deg (should be 90)";
                break;
        }
    }

    // Only a move that finished normally can be measured: a stuck or timed-out move did not try to
    // go the whole distance, so measuring it would give a wrong number.
    private void afterMove(String name, AutoDrive.Result result) {
        measured = testDistanceIn();
        if (result == AutoDrive.Result.DONE) {
            lastResult = name + ": done.";
            waiting = Wait.MEASURE;
        } else {
            lastResult = name + ": " + result + ". Not measured: clear the path and run it again.";
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
            lastResult = "The wheels disagree (" + java.util.Arrays.toString(deltas) + "): a wheel did not count "
                    + "or turns the wrong way. Check the motor directions (Hardware Check) and the encoder cables.";
            return;
        }
        if (avgTicks < 0) {
            lastResult = "Every wheel counted DOWN: the motor directions are wrong, or you pushed backward.";
            return;
        }
        double d = FinderMath.wheelDiameterFromPush(avgTicks, testDistanceIn(), autoConfig.ticksPerRev, autoConfig.gearRatio);
        autoConfig.wheelDiameterIn = d;
        results.set("drive:diameter", "c.wheelDiameterIn = " + FinderResults.num(d, 3) + ";   // RobotSettings.auto()");
        lastResult = "Wheel size now " + FinderResults.num(d, 3) + " in. Next: the Drive test.";
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
        t.section("Drive tuner");
        if (waiting == Wait.PUSHING) {
            int[] now = auto.getWheelTicks();
            int[] deltas = new int[now.length];
            for (int i = 0; i < now.length; i++) deltas[i] = now[i] - pushStart[i];
            t.text("Roll the robot BY HAND exactly " + FinderResults.num(testDistanceIn(), 0) + " in, straight. Then press B.");
            // Each wheel: they should all go up by about the same amount.
            t.line("Wheel counts", java.util.Arrays.toString(deltas));
        } else if (waiting == Wait.MEASURE) {
            t.text("Measure how far it really went. Set it with the D-pad, then press A.");
            t.line("Measured", FinderResults.num(measured, 2) + " in");
        } else {
            for (int i = 0; i < TESTS.length; i++) {
                if (i == 2 && !canStrafe()) continue;
                t.text((i == test ? "> " : "  ") + TESTS[i]);
            }
            t.text("D-pad = choose, A = start");
        }
        t.line("Now using", "wheel " + FinderResults.num(autoConfig.wheelDiameterIn, 3) + " in, strafe "
                + FinderResults.num(autoConfig.strafeCorrection, 3));
        if (!lastResult.isEmpty()) t.text(lastResult);
    }

    @Override
    protected String[] helpLines() {
        String d = FinderResults.num(testDistanceIn(), 0) + " in";
        return new String[] {
            "Wheel size + strafe correction. Tape, 5 ft space.",
            "D-pad up/down = choose a test, A = start it.",
            "GUIDE or START stops a moving test (not measured).",
            "Push: A, roll the robot BY HAND exactly " + d + ",",
            "  straight along the tape, then B.",
            "Drive: drives " + d + ". Measure it, set the real",
            "  distance (D-pad up/down 1 in, l/r 0.25 in), A.",
            "Strafe (mecanum): the same, to the right.",
            "Turn: turns 90 left: check it with a square.",
            "Each new number is used at once: repeat Drive and",
            "  Strafe until they measure " + d + ".",
            "Then copy the lines into RobotSettings.auto().",
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
