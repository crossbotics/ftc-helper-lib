package com.crossbotics.ftchelper.examples.quickstart;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode;

import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/**
 * Quick start, file 1 of 5 (docs/quick-start.md): WHAT is on the robot. Change only the lines marked
 * STEP, in order. Every name in quotes must match the robot configuration on the Driver Station.
 */
public class Robot extends RobotDefinition {
    public Intake intake;

    @Override
    protected void define() {
        // STEP 1: the drive type and the motor names: front left, front right, back left, back right.
        //         A standard (tank) drive instead: drive(Drive.tank("left_drive", "right_drive") ... );
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                // STEP 2: your drive motor (its parts-catalog id) and wheel size in mm.
                .motor("gobilda-5203-312").wheelMm(96)
                // STEP 3: which way the Control Hub's logo and its USB ports face on the robot.
                // STEP 4 (only if a wheel turns the wrong way): on its own line HERE, before .imu(...),
                //         add .reversed("front_left", "back_left") listing EVERY reversed drive motor.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // STEP 5: your mechanisms, one line each; .part(...) = your motor's id (on its label). No intake?
        //         Delete this line, the field above, g2.dpadLeft in Controls.java, spitOut in Actions.java.
        //         A lift, a claw: https://crossbotics.com/ftc-helper/docs/quick-start/#3-change-the-step-lines
        intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
    }
}
