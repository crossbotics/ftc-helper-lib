// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.starter;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (com.crossbotics...).
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import com.crossbotics.ftchelper.core.Profile;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.drive.AutoDriveConfig;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.hardware.ImuSetup;

/**
 * STARTER KIT, file 1 of 6: every number and name for the drivetrain, in ONE place.
 * TeleOp and every autonomous use this (each returns {@code new RobotSettings()} from
 * robotConfig()), so they always match.
 *
 * NEW SEASON / NEW ROBOT: change the names and numbers here first. See docs/starter-kit.md.
 */
// "extends RobotConfig": it fills in the library's robot description (docs/modules/core.md),
// which EasyTeleOp and EasyAuto know how to read. It is not an OpMode (no @TeleOp), so it never
// shows on the Driver Station; it only holds settings.
public class RobotSettings extends RobotConfig {

    /** The drivetrain. Use DriveConfig.tank() or tankTwoMotor() for a standard drive. */
    // Each call makes a fresh set of settings, so one program cannot change another's by mistake.
    @Override
    public DriveConfig drive() {
        // Start from the standard mecanum settings (left motors reversed), then change what differs.
        // Check the directions with MotorDirectionCheck before you drive (docs/modules/drive.md).
        DriveConfig c = DriveConfig.mecanum();
        // Names in the Driver Station configuration:
        // They must match EXACTLY (capital letters too). Change them to your robot's names.
        c.frontLeft = "front_left";
        c.frontRight = "front_right";
        c.backLeft = "back_left";
        c.backRight = "back_right";
        // Gentle in the shop, full power at events:
        // Profile.pick(practice, competition): 60% power in the PRACTICE profile (safer around
        // people and while learning), 100% in COMPETITION. The profile is chosen in the INIT menu.
        // See docs/guides/profiles.md.
        c.powerLimit = Profile.pick(0.6, 1.0);
        return c;
    }

    /** Autonomous tuning: fill in with tutorial 09 (Drive Tuner), then set calibrationConfirmed. */
    @Override
    public AutoDriveConfig auto() {
        AutoDriveConfig c = new AutoDriveConfig();
        // Encoder counts (ticks) for one turn of the motor's output shaft. 537.7 is the number for
        // the goBILDA 312 RPM motor; look up yours on the seller's page.
        c.ticksPerRev = 537.7;           // goBILDA 312 RPM
        // The wheel size turns ticks into inches. Start with the real size, then adjust until a
        // 48-inch move really moves 48 inches.
        c.wheelDiameterIn = 3.78;        // 96 mm wheels, then tune
        // Mecanum wheels slip sideways, so the robot moves less sideways than the wheels turn.
        // 1.15 makes up for that. Tune it on field tiles with the Drive Tuner.
        c.strafeCorrection = 1.15;
        // A safety switch: autonomous refuses to start (it shows an error in INIT) while this is false.
        // Set it to true only after you checked the wheel size, motor directions and IMU mounting.
        c.calibrationConfirmed = false;  // true after tuning
        return c;
    }

    /** How the Control Hub is mounted: which way its logo and USB ports point. */
    // The IMU is the hub's built-in turn sensor. If these two directions are wrong for YOUR robot,
    // the heading is wrong, and so are field-centric driving and every autonomous turn.
    // docs/modules/hardware.md has a picture.
    @Override
    public IMU imu(HardwareMap hardwareMap) {
        return ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
    }
}
