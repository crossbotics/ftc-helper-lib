// The folder this file lives in. Change it to your TeamCode folder when you copy the file.
package com.crossbotics.ftchelper.examples;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.IMU;
import com.crossbotics.ftchelper.drive.AutoDrive;
import com.crossbotics.ftchelper.drive.AutoDriveConfig;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.hardware.BulkReads;
import com.crossbotics.ftchelper.hardware.ImuSetup;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.util.PoseStore;

/**
 * A simple autonomous with AutoDrive: drive a square, then face forward again.
 * Needs a mecanum drive (it drives sideways): front_left, front_right, back_left, back_right,
 * and the hub's "imu". Tune AutoDriveConfig first. Leave about 3 feet of space around the robot.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/09-first-auto/
 */
@Autonomous(name = "Example: Helper Auto", group = "Examples")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class ExampleAuto extends LinearOpMode {

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);

        // The same drive settings your TeleOp uses.
        DriveConfig driveConfig = DriveConfig.mecanum();
        AutoDriveConfig autoConfig = new AutoDriveConfig();
        autoConfig.wheelDiameterIn = 3.78;          // 96 mm goBILDA mecanum; tune with a 48-inch test
        // Safety: AutoDrive refuses to run until this is true.
        autoConfig.calibrationConfirmed = true;     // only after the checks in docs/modules/drive.md

        // Which way the hub's logo and USB ports face on YOUR robot.
        IMU imu = ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
        // "this" = this program, so AutoDrive can notice STOP.
        AutoDrive auto = new AutoDrive(this, driveConfig, autoConfig, imu);

        telemetry.addLine("Ready. Press START.");
        telemetry.update();
        waitForStart();

        // ---- RUN: each move WAITS until it is done (autonomous only, never in TeleOp) ----
        // move(inches, direction, power): 0 = forward, 90 = right, 180 = back, -90 = left.
        auto.move(24, 0, 0.6);      // forward
        auto.move(24, 90, 0.5);     // right
        auto.move(24, 180, 0.6);    // back
        AutoDrive.Result last = auto.move(24, -90, 0.5);   // left
        auto.turnTo(0, 0.5);        // face the start direction

        telemetry.addData("Last move", last);
        telemetry.update();

        // For TeleOp: the heading (x and y are 0: this example does not track position).
        PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
        // No stop needed: every AutoDrive move stops the wheels when it ends, even on STOP.
    }
}
