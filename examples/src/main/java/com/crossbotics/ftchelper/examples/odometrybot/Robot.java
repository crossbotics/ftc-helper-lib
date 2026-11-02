package com.crossbotics.ftchelper.examples.odometrybot;

import com.crossbotics.ftchelper.drive.FieldMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/**
 * Lesson 7's robot: a mecanum drive with a goBILDA Pinpoint and two odometry pods, and an intake.
 * With the odometry line, robot.pose() knows where the robot is on the field.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/07-odometry/
 */
public class Robot extends RobotDefinition {
    public Intake intake;

    @Override
    protected void define() {
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheelMm(104)
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
        // The Pinpoint on an I2C port. The pod offsets, mm: the Odometry Tuner measures them.
        odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
        // BIOBUZZ's two HIVE frames: routes go around them. 13: half the robot's corner to corner size, plus some.
        fieldMap(FieldMap.margin(13)
                .keepOut("HIVE frame, near", -19.5, -24.8, 19.5, -15.4)
                .keepOut("HIVE frame, far", -19.5, 15.4, 19.5, 24.8));
        // "intake" = the motor's name in the robot configuration. .part(...): the id on its label.
        intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
    }
}
