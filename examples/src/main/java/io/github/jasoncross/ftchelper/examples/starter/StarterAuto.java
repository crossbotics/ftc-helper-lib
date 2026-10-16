package io.github.jasoncross.ftchelper.examples.starter;

import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.auto.EasyAuto;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;

/**
 * STARTER KIT, file 4 of 6: what every autonomous shares. Each real autonomous (file 5) extends
 * this and only lists its steps. Nothing to change here for a new season except the robot class.
 */
public abstract class StarterAuto extends EasyAuto {
    protected Robot robot;

    @Override protected DriveConfig driveConfig() { return RobotSettings.drive(); }
    @Override protected AutoDriveConfig autoDriveConfig() { return RobotSettings.auto(); }
    @Override protected IMU createImu() { return RobotSettings.imu(hardwareMap); }

    @Override
    protected Subsystems createSubsystems() {
        robot = new Robot(hardwareMap);
        return robot.all;
    }
}
