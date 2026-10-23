// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.starter;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import com.crossbotics.ftchelper.core.Profile;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.drive.AutoDriveConfig;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.hardware.ImuSetup;

/**
 * STARTER KIT, file 1 of 6: every number and name for the drivetrain, in ONE place. TeleOp and
 * every autonomous return {@code new RobotSettings()} from robotConfig(), so they always match.
 * Not an OpMode: it only holds settings. NEW SEASON / NEW ROBOT: change the names and numbers here first.
 * more: https://crossbotics.com/ftc-helper/docs/starter-kit/#robotsettingsjava-the-drivetrain
 */
public class RobotSettings extends RobotConfig {

    /** The drivetrain. Use DriveConfig.tank() or tankTwoMotor() for a standard drive. */
    @Override
    public DriveConfig drive() {
        // Mecanum, left motors reversed. Check the directions with MotorDirectionCheck before you drive.
        DriveConfig c = DriveConfig.mecanum();
        // Names in the Driver Station configuration: they must match EXACTLY (capital letters too).
        c.frontLeft = "front_left";
        c.frontRight = "front_right";
        c.backLeft = "back_left";
        c.backRight = "back_right";
        // 60% power in the PRACTICE profile, 100% in COMPETITION (picked in the INIT menu).
        c.powerLimit = Profile.pick(0.6, 1.0);
        return c;
    }

    /** Autonomous tuning: fill in with tutorial 09 (Drive Tuner), then set calibrationConfirmed. */
    @Override
    public AutoDriveConfig auto() {
        AutoDriveConfig c = new AutoDriveConfig();
        // Encoder ticks per turn of the motor's output shaft: look up yours on the seller's page.
        c.ticksPerRev = 537.7;           // goBILDA 312 RPM
        // Start with the real size, then adjust until a 48-inch move really moves 48 inches.
        c.wheelDiameterIn = 3.78;        // 96 mm wheels, then tune
        // Mecanum wheels slip sideways: tune this on field tiles with the Drive Tuner.
        c.strafeCorrection = 1.15;
        // SAFETY: autonomous refuses to start while this is false. Set it true only after you checked
        // the wheel size, motor directions and IMU mounting.
        c.calibrationConfirmed = false;  // true after tuning
        return c;
    }

    /** How the Control Hub is mounted: which way its logo and USB ports point (wrong = wrong heading). */
    @Override
    public IMU imu(HardwareMap hardwareMap) {
        return ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
    }
}
