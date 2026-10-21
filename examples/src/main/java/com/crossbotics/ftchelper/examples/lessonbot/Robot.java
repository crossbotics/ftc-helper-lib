package com.crossbotics.ftchelper.examples.lessonbot;

import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/**
 * The lesson robot, file 1 of 5 (docs/tutorials/start/): what is on the robot. A mecanum drive, an
 * intake, a lift with a switch at the bottom, and a claw. Each line names the part's device in the
 * robot configuration; its numbers are in Tunables.java, its buttons come with it (Controls.java).
 */
public class Robot extends RobotDefinition {
    public Intake intake;
    public Lift lift;
    public Claw claw;

    @Override
    protected void define() {
        // Lesson 2: the drive. The motors' names, the motor and wheel, and how the Control Hub is mounted
        // (for the IMU: the logo faces up, the USB ports face forward).
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheelMm(96)
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        // Lesson 4: the mechanisms.
        intake = add(Intake.motor("intake"));                                             // Tunables.Intake
        lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom")); // Tunables.Lift
        claw = add(Claw.servo("claw"));                                                    // Tunables.Claw
    }
}
