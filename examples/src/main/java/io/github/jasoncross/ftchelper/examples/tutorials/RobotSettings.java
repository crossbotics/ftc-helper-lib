package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;

/**
 * Tutorials 09-12: ONE place for the robot's settings, used by every TeleOp and autonomous.
 * Change a number here and every OpMode gets it.
 *
 * Tutorial: docs/tutorials/09-first-auto.md
 */
public final class RobotSettings {
    private RobotSettings() { }

    /** The drivetrain. Mecanum here; use DriveConfig.tank() or tankTwoMotor() for a standard drive. */
    public static DriveConfig drive() {
        DriveConfig c = DriveConfig.mecanum();
        c.powerLimit = 0.8;
        return c;
    }

    /** Autonomous tuning. Fill these in with the steps in tutorial 09, then confirm. */
    public static AutoDriveConfig auto() {
        AutoDriveConfig c = new AutoDriveConfig();
        c.ticksPerRev = 537.7;           // goBILDA 312 RPM motor
        c.wheelDiameterIn = 3.78;        // 96 mm wheels; tune with the 48-inch test
        c.strafeCorrection = 1.15;       // tune with the sideways test (mecanum only)
        c.calibrationConfirmed = false;  // set to true after tuning with Tut09DriveTuner
        return c;
    }

    /** The IMU, with the hub mounted logo UP and USB ports FORWARD. Change to match YOUR robot. */
    public static IMU imu(HardwareMap hardwareMap) {
        return ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
    }
}
