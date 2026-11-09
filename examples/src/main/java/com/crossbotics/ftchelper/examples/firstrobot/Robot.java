package com.crossbotics.ftchelper.examples.firstrobot;

// recipe: first-robot
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/** The five files, file 1 of 5: what is on the robot. The smallest robot that drives. */
public class Robot extends RobotDefinition {

    @Override
    protected void define() {
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheel("gobilda-gripforce-mecanum-104")
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
    }
}
// end recipe
