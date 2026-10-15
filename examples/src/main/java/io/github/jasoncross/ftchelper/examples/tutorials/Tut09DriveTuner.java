package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import io.github.jasoncross.ftchelper.drive.AutoDrive;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.hardware.BulkReads;

/**
 * Tutorial 09, step 2: the tuning OpMode. Put tape on the floor, run a test, measure.
 * This one is an Autonomous, so you can choose a test in INIT with the gamepad and the
 * robot only moves after START:
 *   A = 48 in forward,  B = 48 in right (mecanum only),  X = turn 90 left,  Y = turn 90 right.
 * Press the button during INIT, then START. It runs that one test and shows the result.
 *
 * Tutorial: docs/tutorials/09-first-auto.md
 */
@Autonomous(name = "Tutorial 09: Drive Tuner", group = "Tutorials")
@Disabled
public class Tut09DriveTuner extends LinearOpMode {

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        DriveConfig driveConfig = RobotSettings.drive();
        AutoDriveConfig autoConfig = RobotSettings.auto();
        autoConfig.calibrationConfirmed = true;   // this OpMode is how you check calibration
        AutoDrive auto = new AutoDrive(this, driveConfig, autoConfig, RobotSettings.imu(hardwareMap));

        String test = "forward 48";
        while (opModeInInit()) {
            if (gamepad1.a) test = "forward 48";
            if (gamepad1.b) test = "right 48";
            if (gamepad1.x) test = "turn left 90";
            if (gamepad1.y) test = "turn right 90";
            telemetry.addData("Test (A/B/X/Y)", test);
            telemetry.addData("ticks per inch", "%.2f", autoConfig.ticksPerInch());
            telemetry.update();
        }

        AutoDrive.Result result;
        switch (test) {
            case "right 48": result = auto.move(48, 90, 0.5); break;
            case "turn left 90": result = auto.turn(90, 0.5); break;
            case "turn right 90": result = auto.turn(-90, 0.5); break;
            default: result = auto.move(48, 0, 0.5); break;
        }

        int[] ticks = auto.getWheelTicks();
        while (opModeIsActive()) {
            telemetry.addData("Test", test);
            telemetry.addData("Result", result);
            telemetry.addData("Heading", "%.1f deg", auto.headingDeg());
            telemetry.addData("Wheel ticks", "%d %d %d %d", ticks[0], ticks[1], ticks[2], ticks[3]);
            telemetry.addLine("Measure the real distance or angle now.");
            telemetry.update();
        }
    }
}
