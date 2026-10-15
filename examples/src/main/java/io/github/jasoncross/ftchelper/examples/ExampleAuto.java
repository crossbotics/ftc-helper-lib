package io.github.jasoncross.ftchelper.examples;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.drive.AutoDrive;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import io.github.jasoncross.ftchelper.util.FieldPose;
import io.github.jasoncross.ftchelper.util.PoseStore;

/**
 * A simple autonomous with AutoDrive: drive a square, then face forward again.
 * Copy it into TeamCode, tune AutoDriveConfig, and remove the {@code @Disabled} line.
 *
 * Walk-through: docs/04-first-auto.md
 */
@Autonomous(name = "Example: Helper Auto", group = "Examples")
@Disabled
public class ExampleAuto extends LinearOpMode {

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);

        DriveConfig driveConfig = new DriveConfig();
        AutoDriveConfig autoConfig = new AutoDriveConfig();
        autoConfig.wheelDiameterIn = 3.78;          // 96 mm goBILDA mecanum; tune with a 48-inch test
        autoConfig.calibrationConfirmed = true;     // only after the checks in docs/modules/drive.md

        IMU imu = ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
        AutoDrive auto = new AutoDrive(this, driveConfig, autoConfig, imu);

        telemetry.addLine("Ready. Press START.");
        telemetry.update();
        waitForStart();

        auto.move(24, 0, 0.6);      // forward
        auto.move(24, 90, 0.5);     // right
        auto.move(24, 180, 0.6);    // back
        AutoDrive.Result last = auto.move(24, -90, 0.5);   // left
        auto.turnTo(0, 0.5);        // face the start direction

        telemetry.addData("Last move", last);
        telemetry.update();

        // Hand the end pose to TeleOp (x and y are 0 here: this example does not track position).
        PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
    }
}
