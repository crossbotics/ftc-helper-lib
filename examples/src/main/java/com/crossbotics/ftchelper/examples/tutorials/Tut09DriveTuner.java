// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (com.crossbotics...).
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.crossbotics.ftchelper.drive.AutoDrive;
import com.crossbotics.ftchelper.drive.AutoDriveConfig;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.hardware.BulkReads;

/**
 * Tutorial 09, step 2: the tuning OpMode. Put tape on the floor, run a test, measure.
 * This one is an Autonomous, so you can choose a test in INIT with the gamepad and the
 * robot only moves after START:
 *   A = 48 in forward,  B = 48 in right (mecanum only),  X = turn 90 left,  Y = turn 90 right.
 * Press the button during INIT, then START. It runs that one test and shows the result.
 *
 * It uses the settings in RobotSettings.java (same folder). After each test, change the number
 * there (wheel size, strafe correction) and run the test again until it is right.
 *
 * Tutorial: docs/tutorials/09-first-auto.md
 */
// @Autonomous puts this program in the Driver Station's Autonomous list with this name.
@Autonomous(name = "Tutorial 09: Drive Tuner", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom (see tutorial 01).
public class Tut09DriveTuner extends LinearOpMode {

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set up. Nothing moves until START. ----

        // Read all sensors and encoders from the hub in one go: faster. (docs/modules/hardware.md)
        BulkReads.auto(hardwareMap);
        // The SAME settings TeleOp and every autonomous use (RobotSettings.java), so what you tune
        // here is what they get.
        DriveConfig driveConfig = RobotSettings.drive();
        AutoDriveConfig autoConfig = RobotSettings.auto();
        // AutoDrive refuses to run while calibrationConfirmed is false. This program is the one
        // exception: it is how you check the calibration, so it switches the safety on just for itself.
        autoConfig.calibrationConfirmed = true;   // this OpMode is how you check calibration
        // AutoDrive does the measured moves. "this" = this program, so it can notice STOP.
        AutoDrive auto = new AutoDrive(this, driveConfig, autoConfig, RobotSettings.imu(hardwareMap));

        // Which test to run. A piece of text ("String"); forward 48 if no button is pressed.
        String test = "forward 48";
        // Instead of a plain waitForStart(), loop during INIT so the buttons can choose a test.
        // It ends when START (or STOP) is pressed. Nothing moves in here.
        while (opModeInInit()) {
            if (gamepad1.a) test = "forward 48";
            if (gamepad1.b) test = "right 48";
            if (gamepad1.x) test = "turn left 90";
            if (gamepad1.y) test = "turn right 90";
            telemetry.addData("Test (A/B/X/Y)", test);
            // How many encoder ticks make one inch, worked out from the motor and wheel settings.
            // "%.2f" shows it with 2 digits after the point.
            telemetry.addData("ticks per inch", "%.2f", autoConfig.ticksPerInch());
            telemetry.update();
        }

        // ---- RUN: one test. ----
        // "switch" picks the line whose "case" matches the test's text; "default" is used when none
        // match (here: forward 48). "break" ends that line. Each move WAITS until it is done.
        // Half power (0.5) for every test: steady, so the measurements repeat well.
        AutoDrive.Result result;
        switch (test) {
            // 90 = to the right. Only a mecanum robot can do this one.
            case "right 48": result = auto.move(48, 90, 0.5); break;
            // Turns: positive = left (counterclockwise), negative = right.
            case "turn left 90": result = auto.turn(90, 0.5); break;
            case "turn right 90": result = auto.turn(-90, 0.5); break;
            default: result = auto.move(48, 0, 0.5); break;
        }

        // Each wheel's encoder count after the test (front-left, front-right, back-left, back-right).
        // Wheels with very different counts can point to a slipping wheel or a wrong motor setting.
        int[] ticks = auto.getWheelTicks();
        // Keep the results on the screen until STOP, so you have time to measure.
        while (opModeIsActive()) {
            telemetry.addData("Test", test);
            // How the move ended: reached the target, stuck, out of time, or stopped.
            telemetry.addData("Result", result);
            telemetry.addData("Heading", "%.1f deg", auto.headingDeg());
            telemetry.addData("Wheel ticks", "%d %d %d %d", ticks[0], ticks[1], ticks[2], ticks[3]);
            // Compare the tape measure with 48 inches (or a protractor with 90 degrees), then change
            // RobotSettings.auto() and try again.
            telemetry.addLine("Measure the real distance or angle now.");
            telemetry.update();
        }
        // No motor stop needed here: every AutoDrive move stops the wheels itself when it ends.
    }
}
