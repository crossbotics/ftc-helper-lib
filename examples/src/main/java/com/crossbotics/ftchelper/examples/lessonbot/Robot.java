package com.crossbotics.ftchelper.examples.lessonbot;

import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/**
 * The lesson robot, file 1 of 5: what is on the robot. A mecanum drive, an intake, a lift with a
 * switch at the bottom, and a claw. Their numbers are in Tunables.java.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/01-robot-in-five-files/
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Lift lift;
    public Claw claw;

    @Override
    protected void define() {
        // Lesson 2: the drive. The Control Hub is mounted logo up, USB ports forward (for the IMU).
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheelMm(96)
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // Lesson 4: the mechanisms. The names in quotes = the devices' names in the robot configuration.
        // .part(...) = which motor or servo it is (the id on its label; these are examples).
        intake = add(Intake.motor("intake").part("gobilda-5203-1150"));                    // Tunables.Intake
        lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom")); // Tunables.Lift
        claw = add(Claw.servo("claw").part("gobilda-2000-torque"));                         // Tunables.Claw
    }
}
