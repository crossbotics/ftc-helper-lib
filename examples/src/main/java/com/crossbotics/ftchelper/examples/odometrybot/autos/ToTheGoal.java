package com.crossbotics.ftchelper.examples.odometrybot.autos;

import com.crossbotics.ftchelper.examples.odometrybot.Robot;
import com.crossbotics.ftchelper.examples.odometrybot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * Lesson 7: drive to field points with odometry. Each driveTo goes to a place on the field (not
 * "24 inches forward"), and keeps correcting on the way.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/07-odometry/#6-drive-to-field-points
 */
@Autonomous(name = "Odometry Bot To the Goal", group = "Examples", preselectTeleOp = "Odometry Bot TeleOp")
@Disabled
public class ToTheGoal extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ's field is turned half a turn between the alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
         .run("Intake on", robot.intake::in)
         .driveTo("To the goal", Tunables.Auto.goalX, Tunables.Auto.goalY, Tunables.Auto.goalHeading, 0.6)
         .run("Intake off", robot.intake::stop)
         .parkWithSeconds(4)
         .driveTo("Park", Tunables.Auto.parkX, Tunables.Auto.parkY, 0, 0.7);
    }
}
