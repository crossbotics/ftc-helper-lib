package com.crossbotics.ftchelper.examples.pedrobot;

import com.crossbotics.ftchelper.pedro.Pedro;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/**
 * Lesson 7's robot plus Pedro Pathing: with the follower line, autonomous can follow(...) curved
 * paths; driveTo(...) still works too.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/08-paths-with-pedro/
 */
public class Robot extends RobotDefinition {
    public Intake intake;

    @Override
    protected void define() {
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheelMm(104)
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
        // "pinpoint" = the Pinpoint's name in the robot configuration.
        odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
        // Pedro drives the paths, with the drive and odometry above. Its numbers: PedroConstants.
        follower(Pedro.follower(PedroConstants.foresight));
        // "intake" = the motor's name in the robot configuration. .part(...): the id on its label.
        intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
    }
}
