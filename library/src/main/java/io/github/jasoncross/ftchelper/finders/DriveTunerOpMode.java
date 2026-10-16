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
 * </pre>
 *
 * Use the new numbers in RobotSettings, then run the Drive and Strafe tests again until they
 * measure 48. Leave 5 ft of space in front of and to the right of the robot.
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
            if (test == 1) {
                double d = FinderMath.correctedDiameter(autoConfig.wheelDiameterIn, testDistanceIn(), measured);
                results.set("drive:diameter", "c.wheelDiameterIn = " + FinderResults.num(d, 3) + ";   // RobotSettings.auto(), from the drive test");
                lastResult = "Wheel size " + FinderResults.num(d, 3) + " in. Put it in RobotSettings and test again.";
            } else if (test == 2) {
                double s = FinderMath.correctedStrafe(autoConfig.strafeCorrection, testDistanceIn(), measured);
                results.set("drive:strafe", "c.strafeCorrection = " + FinderResults.num(s, 3) + ";   // RobotSettings.auto()");
                lastResult = "Strafe correction " + FinderResults.num(s, 3) + ". Put it in RobotSettings and test again.";
            }
            waiting = Wait.CHOOSE;
            return;
        }
        if (waiting != Wait.CHOOSE) return;
        switch (test) {
            case 0:
                // Push: wheels free to roll, remember where the counts start.
                for (DcMotorEx w : wheels) w.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
                pushStart = auto.getWheelTicks();
                waiting = Wait.PUSHING;
                break;
            case 1:
                // BLOCKS on purpose until the move is done (STOP still works).
                lastResult = "Drive: " + auto.move(testDistanceIn(), 0, 0.5);
                measured = testDistanceIn();
                waiting = Wait.MEASURE;
                break;
            case 2:
                lastResult = "Strafe: " + auto.move(testDistanceIn(), 90, 0.5);
                measured = testDistanceIn();
                waiting = Wait.MEASURE;
                break;
            default:
                auto.resetHeading();
                lastResult = "Turn: " + auto.turn(90, 0.5) + ", IMU heading "
                        + FinderResults.num(auto.headingDeg(), 1) + " deg (should be 90)";
                break;
        }
    }

    // B: the push is done. Wheel size from the counts.
    private void finishPush() {
        if (waiting != Wait.PUSHING) return;
        int[] now = auto.getWheelTicks();
        double sum = 0;
        for (int i = 0; i < now.length; i++) sum += now[i] - pushStart[i];
        double avgTicks = sum / now.length;
        for (DcMotorEx w : wheels) w.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        double d = FinderMath.wheelDiameterFromPush(avgTicks, testDistanceIn(), autoConfig.ticksPerRev, autoConfig.gearRatio);
        if (Double.isNaN(d)) {
            lastResult = "The wheels did not count. Check the encoder cables.";
        } else {
            results.set("drive:diameter", "c.wheelDiameterIn = " + FinderResults.num(d, 3) + ";   // RobotSettings.auto(), from the push test");
            lastResult = "Wheel size " + FinderResults.num(d, 3) + " in" + (avgTicks < 0 ? " (counts went DOWN: check motor directions)" : "");
        }
        waiting = Wait.CHOOSE;
    }

    @Override protected void step() { }

    @Override
    protected void show(RobotTelemetry t) {
        t.section("Drive tuner");
        if (waiting == Wait.PUSHING) {
            int[] now = auto.getWheelTicks();
            double sum = 0;
            for (int i = 0; i < now.length; i++) sum += now[i] - pushStart[i];
            t.text("Roll the robot BY HAND exactly " + FinderResults.num(testDistanceIn(), 0) + " in, straight. Then press B.");
            t.line("Wheel counts", FinderResults.num(sum / now.length, 0));
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
    protected void stopAll() {
        if (auto != null) auto.stop();
    }
}
