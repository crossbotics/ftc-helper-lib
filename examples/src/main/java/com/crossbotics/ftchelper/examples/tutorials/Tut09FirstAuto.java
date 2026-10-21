// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (com.crossbotics...).
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.crossbotics.ftchelper.drive.AutoDrive;
import com.crossbotics.ftchelper.hardware.BulkReads;

/**
 * Tutorial 09: a first autonomous. Drives a square and faces the start direction again.
 *
 * Before it runs: tune with Tut09DriveTuner, then set calibrationConfirmed to true in
 * RobotSettings.auto(). Until then it stops in INIT with a message, on purpose.
 * Leave about 3 feet of space in front of and to the right of the robot.
 *
 * Tutorial: docs/tutorials/09-first-auto.md
 */
// @Autonomous puts this program in the Driver Station's Autonomous list with this name.
@Autonomous(name = "Tutorial 09: First Auto", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs its steps from top to bottom, in order.
public class Tut09FirstAuto extends LinearOpMode {

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set up. Nothing moves until START. ----

        // Read all sensors and encoders from the hub in one go: faster. (docs/modules/hardware.md)
        BulkReads.auto(hardwareMap);
        // AutoDrive does measured moves with the wheel encoders and the IMU (turn sensor).
        // All its settings come from RobotSettings.java, the same ones TeleOp uses.
        // "this" = this program, so AutoDrive can notice STOP and stop the robot at once.
        AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(),
                RobotSettings.imu(hardwareMap));

        telemetry.addLine("Ready. Press START.");
        telemetry.update();
        // Wait here until the driver presses START.
        waitForStart();

        // ---- RUN: the steps, one after another ----
        // Each move WAITS until it is finished before the next line runs. That keeps autonomous
        // simple, but never do this in TeleOp: there the robot must keep reacting to the gamepads.
        // move(inches, direction, power): direction 0 = straight ahead. Power 0.6 for driving and
        // 0.5 for turning: quick enough, but slow enough to stop where we want.
        // turn(degrees, power): positive = left, so -90 = a quarter turn right.
        auto.move(24, 0, 0.6);     // forward 24 in
        auto.turn(-90, 0.5);       // turn right 90
        auto.move(24, 0, 0.6);
        auto.turn(-90, 0.5);
        auto.move(24, 0, 0.6);
        auto.turn(-90, 0.5);
        auto.move(24, 0, 0.6);
        // turnTo(0, ...) turns to heading 0, the way the robot faced at START, whatever small
        // errors built up on the way.
        auto.turnTo(0, 0.5);       // face exactly where we started

        // Show how close we got. "%.1f deg" = one digit after the point, then "deg".
        telemetry.addData("Heading at end", "%.1f deg", auto.headingDeg());
        telemetry.update();
        // idle() lets the robot app do its own work while we wait. The wheels are already stopped:
        // every AutoDrive move stops them itself when it ends.
        while (opModeIsActive()) idle();   // keep the message on screen until STOP
    }
}
