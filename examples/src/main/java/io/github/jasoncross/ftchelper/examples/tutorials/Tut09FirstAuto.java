package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import io.github.jasoncross.ftchelper.drive.AutoDrive;
import io.github.jasoncross.ftchelper.hardware.BulkReads;

/**
 * Tutorial 09: a first autonomous. Drives a square and faces the start direction again.
 *
 * Tutorial: docs/tutorials/09-first-auto.md
 */
@Autonomous(name = "Tutorial 09: First Auto", group = "Tutorials")
@Disabled
public class Tut09FirstAuto extends LinearOpMode {

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(),
                RobotSettings.imu(hardwareMap));

        telemetry.addLine("Ready. Press START.");
        telemetry.update();
        waitForStart();

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
        while (opModeIsActive()) idle();   // keep the message on screen until STOP
    }
}
