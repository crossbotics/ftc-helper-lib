// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.crossbotics.ftchelper.drive.AutoDrive;
import com.crossbotics.ftchelper.hardware.BulkReads;

/**
 * Tutorial 09: a first autonomous. Drives a square and faces the start direction again.
 * First tune with Tut09DriveTuner and set calibrationConfirmed = true in RobotSettings.auto().
 * Leave about 3 feet of space in front of and to the right of the robot.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/09-first-auto/
 */
@Autonomous(name = "Tutorial 09: First Auto", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut09FirstAuto extends LinearOpMode {

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        // "this" = this program, so AutoDrive can notice STOP.
        AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(),
                RobotSettings.imu(hardwareMap));

        telemetry.addLine("Ready. Press START.");
        telemetry.update();
        waitForStart();

        // ---- RUN: each move WAITS until it is done (autonomous only, never in TeleOp) ----
        // move(inches, direction, power), turn(degrees, power): positive = left.
        auto.move(24, 0, 0.6);     // forward 24 in
        auto.turn(-90, 0.5);       // turn right 90
        auto.move(24, 0, 0.6);
        auto.turn(-90, 0.5);
        auto.move(24, 0, 0.6);
        auto.turn(-90, 0.5);
        auto.move(24, 0, 0.6);
        auto.turnTo(0, 0.5);       // face exactly where we started

        telemetry.addData("Heading at end", "%.1f deg", auto.headingDeg());
        telemetry.update();
        // The wheels are already stopped: every AutoDrive move stops them when it ends.
        while (opModeIsActive()) idle();   // keep the message on screen until STOP
    }
}
