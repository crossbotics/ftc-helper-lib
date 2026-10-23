package com.crossbotics.ftchelper.examples.odometrybot;

import com.crossbotics.ftchelper.finders.OdometryTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Lesson 7: the Odometry Tuner for this robot (directions, distance, offsets, a loop check).
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/07-odometry/#5-the-odometry-tuner
 */
@TeleOp(name = "Odometry Bot Odometry Tuner", group = "Finders")
@Disabled // hides it on the Driver Station: delete this line in your copy
public class OdometryTuner extends OdometryTunerOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();
    }
}
