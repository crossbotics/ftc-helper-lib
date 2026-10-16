// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (io.github.jasoncross...).
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;

/**
 * Tutorials 09-12: ONE place for the robot's settings, used by every TeleOp and autonomous.
 * Change a number here and every OpMode gets it.
 *
 * Tutorial: docs/tutorials/09-first-auto.md
 */
// Not an OpMode (no @TeleOp), so it never shows on the Driver Station: it only holds settings.
// "final" = no other class can be built on top of this one.
public final class RobotSettings {
    // A "private" constructor: nobody can make a RobotSettings object, and nobody needs to,
    // because every method is "static" (you call it on the class itself: RobotSettings.drive()).
    private RobotSettings() { }

    /** The drivetrain. Mecanum here; use DriveConfig.tank() or tankTwoMotor() for a standard drive. */
    // Each call makes a fresh set of settings, so one program cannot change another's by mistake.
    public static DriveConfig drive() {
        // Standard names front_left, front_right, back_left, back_right, and left motors reversed.
        // If your names differ, set them here, e.g. c.frontLeft = "fl";
        DriveConfig c = DriveConfig.mecanum();
        // Never more than 80% power to a wheel: easier to control while learning.
        c.powerLimit = 0.8;
        return c;
    }

    /** Autonomous tuning. Fill these in with the steps in tutorial 09, then confirm. */
    public static AutoDriveConfig auto() {
        AutoDriveConfig c = new AutoDriveConfig();
        // Encoder counts (ticks) per turn of the motor's output shaft. Look up YOUR motor's number.
        c.ticksPerRev = 537.7;           // goBILDA 312 RPM motor
        // Turns ticks into inches. Adjust until a 48-inch move really moves 48 inches.
        c.wheelDiameterIn = 3.78;        // 96 mm wheels; tune with the 48-inch test
        // Mecanum wheels slip sideways, so the robot moves less sideways than the wheels turn.
        // This number makes up for it.
        c.strafeCorrection = 1.15;       // tune with the sideways test (mecanum only)
        // A safety switch: autonomous refuses to run while this is false. It reminds you to tune
        // first, so an untuned robot does not charge across the field.
        c.calibrationConfirmed = false;  // set to true after tuning with Tut09DriveTuner
        return c;
    }

    /** The IMU, with the hub mounted logo UP and USB ports FORWARD. Change to match YOUR robot. */
    // The IMU is the hub's built-in turn sensor. Wrong directions here = wrong heading and wrong
    // turns. docs/modules/hardware.md has a picture.
    public static IMU imu(HardwareMap hardwareMap) {
        return ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
    }

    /**
     * The same three settings as one object, for EasyAuto and EasyTeleOp (tutorials 16 and 20):
     * {@code protected RobotConfig robotConfig() { return RobotSettings.config(); }}
     */
    // "new RobotConfig() { ... }" makes a one-off object whose methods hand back the settings above.
    public static RobotConfig config() {
        return new RobotConfig() {
            @Override public DriveConfig drive() { return RobotSettings.drive(); }
            @Override public AutoDriveConfig auto() { return RobotSettings.auto(); }
            @Override public IMU imu(HardwareMap hardwareMap) { return RobotSettings.imu(hardwareMap); }
        };
    }
}
