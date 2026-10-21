package com.crossbotics.ftchelper.examples.odometrybot;

import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/**
 * Lesson 8's robot: a mecanum drive with a goBILDA Pinpoint and two 4-bar odometry pods, and an
 * intake. The odometry line is the only new thing: with it, robot.pose() knows where the robot is on
 * the field, autonomous can driveTo(...) a field point, and TeleOp starts where autonomous ended.
 */
public class Robot extends RobotDefinition {
    public Intake intake;

    @Override
    protected void define() {
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheelMm(104)
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
        // The Pinpoint on an I2C port, named "pinpoint" in the configuration. The offsets: the forward pod
        // 84 mm RIGHT of the centre (-84), the sideways pod 168 mm BEHIND it (-168). The Odometry Tuner
        // measures them.
        odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
        intake = add(Intake.motor("intake"));
    }
}
