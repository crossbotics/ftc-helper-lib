// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.starter;

// "import" lines list the ready-made pieces this file uses.
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.auto.EasyAuto;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;

/**
 * STARTER KIT, file 4 of 6: what every autonomous shares. Each real autonomous (file 5) extends
 * this and only lists its steps. Nothing to change here for a new season except the robot class.
 *
 * EasyAuto (the library class this builds on) does the rest: the INIT menu (alliance, start delay,
 * profile), the 30-second timer, mirroring for RED, and stopping everything at the end.
 * See docs/guides/easy-autos.md.
 */
// "abstract" = this is not a whole program on its own (it has no steps and no @Autonomous), so it
// never shows on the Driver Station. Real autonomous files "extend" it and fill in the steps.
// "extends EasyAuto" = it gets everything EasyAuto already does.
public abstract class StarterAuto extends EasyAuto {
    // The robot's mechanisms. "protected" = the autonomous files built on this one can use it
    // (as robot.lift, robot.claw...). It is filled in by createSubsystems() below, during INIT.
    protected Robot robot;

    // EasyAuto asks for these four things. "@Override" = "this is my answer to a method EasyAuto
    // asks for". Each one just hands over the shared settings, so every autonomous matches TeleOp.
    // The drive settings: motor names, directions, power limit (RobotSettings.java).
    @Override protected DriveConfig driveConfig() { return RobotSettings.drive(); }
    // The autonomous tuning: wheel size, strafe correction (RobotSettings.java).
    @Override protected AutoDriveConfig autoDriveConfig() { return RobotSettings.auto(); }
    // The IMU (turn sensor), set up for how YOUR hub is mounted (RobotSettings.java).
    @Override protected IMU createImu() { return RobotSettings.imu(hardwareMap); }

    // The mechanisms. EasyAuto keeps every one of them working during every step (for example
    // the lift keeps holding its height while the robot drives), and stops them all at the end.
    @Override
    protected Subsystems createSubsystems() {
        robot = new Robot(hardwareMap);
        return robot.all;
    }
}
