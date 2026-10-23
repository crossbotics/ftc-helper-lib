// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.drive.AutoDriveConfig;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.hardware.ImuSetup;

/**
 * Tutorials 09-12: ONE place for the robot's settings, used by every TeleOp and autonomous.
 * Change a number here and every OpMode gets it. Not an OpMode: it only holds settings.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/09-first-auto/#1-one-place-for-the-settings
 */
public final class RobotSettings {
    private RobotSettings() { }

    /** The drivetrain. Mecanum here; use DriveConfig.tank() or tankTwoMotor() for a standard drive. */
    public static DriveConfig drive() {
        // Names front_left ... back_right, left side reversed. Other names: c.frontLeft = "fl";
        DriveConfig c = DriveConfig.mecanum();
        c.powerLimit = 0.8;              // easier to control while learning
        return c;
    }

    /** Autonomous tuning. Fill these in with the steps in tutorial 09, then confirm. */
    public static AutoDriveConfig auto() {
        AutoDriveConfig c = new AutoDriveConfig();
        c.ticksPerRev = 537.7;           // goBILDA 312 RPM motor
        c.wheelDiameterIn = 3.78;        // 96 mm wheels; tune with the 48-inch test
        c.strafeCorrection = 1.15;       // tune with the sideways test (mecanum only)
        // Safety: autonomous refuses to run while this is false.
        c.calibrationConfirmed = false;  // set to true after tuning with Tut09DriveTuner
        return c;
    }

    /** The IMU, with the hub mounted logo UP and USB ports FORWARD. Change to match YOUR robot. */
    public static IMU imu(HardwareMap hardwareMap) {
        return ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
    }

    /**
     * The same three settings as one object, for EasyAuto and EasyTeleOp (tutorials 16 and 20):
     * {@code protected RobotConfig robotConfig() { return RobotSettings.config(); }}
     */
    public static RobotConfig config() {
        return new RobotConfig() {
            @Override public DriveConfig drive() { return RobotSettings.drive(); }
            @Override public AutoDriveConfig auto() { return RobotSettings.auto(); }
            @Override public IMU imu(HardwareMap hardwareMap) { return RobotSettings.imu(hardwareMap); }
        };
    }
}
