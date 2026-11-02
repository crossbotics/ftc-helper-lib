package com.crossbotics.ftchelper.examples.odometrybot.autos;

import com.crossbotics.ftchelper.examples.odometrybot.Robot;
import com.crossbotics.ftchelper.examples.odometrybot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * Lesson 7: the park goes around the HIVE frames, from wherever the park time stopped the robot.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/07-odometry/#go-around-things-on-the-way
 */
@Autonomous(name = "Odometry Bot Park Around", group = "Examples", preselectTeleOp = "Odometry Bot TeleOp")
@Disabled
public class ParkAround extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ's field is turned half a turn between the alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
         .driveTo("To the goal", Tunables.Auto.goalX, Tunables.Auto.goalY, Tunables.Auto.goalHeading, 0.6)
         .parkWithSeconds(6)
         .routeTo("Park", Tunables.Auto.parkX, Tunables.Auto.parkY, 0, 0.8);
    }
}
