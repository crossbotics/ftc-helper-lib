package io.github.jasoncross.ftchelper.examples.starter;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.core.Profile;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;

/**
 * STARTER KIT, file 1 of 6: every number and name for the drivetrain, in ONE place.
 * TeleOp and every autonomous use this, so they always match.
 *
 * NEW SEASON / NEW ROBOT: change the names and numbers here first. See docs/starter-kit.md.
 */
public final class RobotSettings {
    private RobotSettings() { }

    /** The drivetrain. Use DriveConfig.tank() or tankTwoMotor() for a standard drive. */
    public static DriveConfig drive() {
        DriveConfig c = DriveConfig.mecanum();
        // Names in the Driver Station configuration:
        c.frontLeft = "front_left";
        c.frontRight = "front_right";
        c.backLeft = "back_left";
        c.backRight = "back_right";
        // Gentle in the shop, full power at events:
        c.powerLimit = Profile.pick(0.6, 1.0);
        return c;
    }

    /** Autonomous tuning: fill in with tutorial 09 (Drive Tuner), then set calibrationConfirmed. */
    public static AutoDriveConfig auto() {
        AutoDriveConfig c = new AutoDriveConfig();
        c.ticksPerRev = 537.7;           // goBILDA 312 RPM
        c.wheelDiameterIn = 3.78;        // 96 mm wheels, then tune
        c.strafeCorrection = 1.15;
        c.calibrationConfirmed = false;  // true after tuning
        return c;
    }

    /** How the Control Hub is mounted: which way its logo and USB ports point. */
    public static IMU imu(HardwareMap hardwareMap) {
        return ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
    }
}
