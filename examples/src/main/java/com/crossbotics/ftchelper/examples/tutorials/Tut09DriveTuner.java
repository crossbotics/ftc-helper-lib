// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.crossbotics.ftchelper.drive.AutoDrive;
import com.crossbotics.ftchelper.drive.AutoDriveConfig;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.hardware.BulkReads;

/**
 * Tutorial 09, step 2: the drive tuner. Choose a test in INIT, then START. It runs it once.
 * A = 48 in forward, B = 48 in right (mecanum only), X = turn 90 left, Y = turn 90 right.
 * Measure, change the number in RobotSettings.auto(), and run the test again.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/09-first-auto/#3-tune-it
 */
@Autonomous(name = "Tutorial 09: Drive Tuner", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut09DriveTuner extends LinearOpMode {

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        // The SAME settings TeleOp and every autonomous use.
        DriveConfig driveConfig = RobotSettings.drive();
        AutoDriveConfig autoConfig = RobotSettings.auto();
        autoConfig.calibrationConfirmed = true;   // this OpMode is how you check calibration
        AutoDrive auto = new AutoDrive(this, driveConfig, autoConfig, RobotSettings.imu(hardwareMap));

        String test = "forward 48";
        // Instead of waitForStart(): the buttons choose a test during INIT. Nothing moves here.
        while (opModeInInit()) {
            if (gamepad1.a) test = "forward 48";
            if (gamepad1.b) test = "right 48";
            if (gamepad1.x) test = "turn left 90";
            if (gamepad1.y) test = "turn right 90";
            telemetry.addData("Test (A/B/X/Y)", test);
            telemetry.addData("ticks per inch", "%.2f", autoConfig.ticksPerInch());
            telemetry.update();
        }

        // ---- RUN: one test, at half power so the measurements repeat well ----
        AutoDrive.Result result;
        switch (test) {
            case "right 48": result = auto.move(48, 90, 0.5); break;   // 90 = right (mecanum only)
            case "turn left 90": result = auto.turn(90, 0.5); break;
            case "turn right 90": result = auto.turn(-90, 0.5); break;
            default: result = auto.move(48, 0, 0.5); break;
        }

        int[] ticks = auto.getWheelTicks();   // FL, FR, BL, BR
        // Keep the results on the screen until STOP.
        while (opModeIsActive()) {
            telemetry.addData("Test", test);
            telemetry.addData("Result", result);
            telemetry.addData("Heading", "%.1f deg", auto.headingDeg());
            telemetry.addData("Wheel ticks", "%d %d %d %d", ticks[0], ticks[1], ticks[2], ticks[3]);
            telemetry.addLine("Measure the real distance or angle now.");
            telemetry.update();
        }
        // No motor stop needed: every AutoDrive move stops the wheels when it ends.
    }
}
