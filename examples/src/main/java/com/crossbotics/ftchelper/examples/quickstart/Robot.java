// STEP 0: in your TeamCode, this first line is: package org.firstinspires.ftc.teamcode;
package com.crossbotics.ftchelper.examples.quickstart;

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
        // STEP 1: the drive type and the four motor names, in this order: front left, front right,
        //         back left, back right. A standard (tank) drive instead:
        //         drive(Drive.tank("left_drive", "right_drive") ... );
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                // STEP 2: your drive motor (its parts-catalog id) and wheel size in mm.
                .motor("gobilda-5203-312").wheelMm(96)
                // STEP 3: which way the Control Hub's logo and its USB ports face on the robot.
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
                // STEP 4 (only if a wheel turns the wrong way, see the quick start): add
                //         .reversed("front_left", "back_left") listing EVERY reversed drive motor.

        // STEP 5: your mechanisms, one line each. This robot has one intake motor called "intake".
        //         Delete it if you have none. More you can add (then add their numbers in Tunables.java):
        //           lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
        //           claw = add(Claw.servo("claw"));
        //         (and a field for each, like "public Lift lift;" above)
        intake = add(Intake.motor("intake"));
    }
}
