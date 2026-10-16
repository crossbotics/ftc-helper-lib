// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (io.github.jasoncross...).
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
 * Needs: a mecanum drive (it drives sideways) with motors front_left, front_right, back_left,
 * back_right, and the hub's "imu". Leave about 3 feet of space around the robot.
 *
 * Tutorial: docs/tutorials/09-first-auto.md
 */
// @Autonomous puts this program in the Driver Station's Autonomous list, with this name.
@Autonomous(name = "Example: Helper Auto", group = "Examples")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs its steps from top to bottom, in order.
public class ExampleAuto extends LinearOpMode {

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set things up. Nothing moves until START. ----

        // Read all sensors and encoders from the hub in one go: faster. (docs/modules/hardware.md)
        BulkReads.auto(hardwareMap);

        // The same drive settings your TeleOp uses (motor names, directions, power limit).
        DriveConfig driveConfig = DriveConfig.mecanum();
        // Settings only autonomous needs: wheel size, speeds, how close is "there"...
        AutoDriveConfig autoConfig = new AutoDriveConfig();
        // The wheel size turns encoder counts into inches. If a 48-inch move goes 46 inches,
        // adjust this number until it goes 48 (docs/modules/drive.md).
        autoConfig.wheelDiameterIn = 3.78;          // 96 mm goBILDA mecanum; tune with a 48-inch test
        // A safety reminder: AutoDrive refuses to run until this is true. Set it only after you
        // checked the wheel size, motor directions and IMU mounting.
        autoConfig.calibrationConfirmed = true;     // only after the checks in docs/modules/drive.md

        // The IMU is the hub's built-in turn sensor. Say which way the hub's logo and USB port face
        // on YOUR robot, or every turn will be wrong. See docs/modules/hardware.md for a picture.
        IMU imu = ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
        // AutoDrive does the measured moves. "this" means "this program": AutoDrive uses it to
        // notice when STOP is pressed, so it can stop the robot right away.
        AutoDrive auto = new AutoDrive(this, driveConfig, autoConfig, imu);

        telemetry.addLine("Ready. Press START.");
        telemetry.update();
        // Wait here until the driver presses START.
        waitForStart();

        // ---- RUN: the moves, one after another ----
        // Each move WAITS until it is finished before the next line runs. That is fine (and
        // simple) in autonomous, but never do this in TeleOp: the robot could not react.
        // move(inches, direction, power): direction 0 = forward, 90 = right, 180 = back, -90 = left.
        // Power 0.5 to 0.6: fast enough, but slow enough to stop accurately. Sideways moves are a
        // little slower (0.5) because mecanum wheels slip more sideways.
        auto.move(24, 0, 0.6);      // forward
        auto.move(24, 90, 0.5);     // right
        auto.move(24, 180, 0.6);    // back
        // Each move gives back how it ended (reached the target, stuck, out of time, or stopped).
        // We keep the last one to show it on the screen.
        AutoDrive.Result last = auto.move(24, -90, 0.5);   // left
        // turnTo(0, ...) turns back to heading 0 = the way the robot faced at the start,
        // in case it twisted a little during the square.
        auto.turnTo(0, 0.5);        // face the start direction

        telemetry.addData("Last move", last);
        telemetry.update();

        // Hand the end pose to TeleOp (x and y are 0 here: this example does not track position).
        // PoseStore keeps it in memory, so a TeleOp started next can read which way the robot faces.
        PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
        // No "finally" with a stop here: every AutoDrive move stops the wheels itself when it ends,
        // even when STOP is pressed.
    }
}
