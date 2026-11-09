package com.crossbotics.ftchelper.examples.allbuttons;

import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/**
 * Every pre-made button, written out: the robot. A mecanum drive, an intake and a launcher, the parts
 * whose own buttons Controls.java writes out.
 * more: https://crossbotics.com/ftc-helper/docs/five-files/03-controls-java/#a-whole-robots-buttons
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Launcher launcher;

    @Override
    protected void define() {
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheel("gobilda-gripforce-mecanum-104")
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
        intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
        launcher = add(Launcher.flywheel("flywheel", "gobilda-5203-6000")
                .feeder(Indexer.motor("feeder")));
    }
}
