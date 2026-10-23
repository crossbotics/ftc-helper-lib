package com.crossbotics.ftchelper.examples.pedrobot;

import com.crossbotics.ftchelper.pedro.Pedro;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/**
 * Lesson 7's robot plus Pedro Pathing: the same drive, Pinpoint and intake, and one more line. With the
 * follower line, autonomous can follow(...) curved paths; driveTo(...) still works too.
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
        // Pedro drives the paths. It uses the drive and odometry lines above; PedroConstants holds the
        // numbers from Pedro's Foresight tuner.
        follower(Pedro.follower(PedroConstants.foresight));
        // "intake" = the motor's name in the robot configuration. Its numbers: Tunables.Intake.
        intake = add(Intake.motor("intake"));
    }
}
