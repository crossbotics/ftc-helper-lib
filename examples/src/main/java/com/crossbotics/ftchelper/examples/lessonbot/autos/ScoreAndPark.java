package com.crossbotics.ftchelper.examples.lessonbot.autos;

import com.crossbotics.ftchelper.examples.lessonbot.Actions;
import com.crossbotics.ftchelper.examples.lessonbot.Robot;
import com.crossbotics.ftchelper.examples.lessonbot.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * The lesson robot, file 5 of 5: an autonomous (lesson 6). Hold the piece, drive to the goal, score,
 * and always park in the last 3 seconds.
 */
@Autonomous(name = "Lesson Bot Score and Park", group = "Examples", preselectTeleOp = "Lesson Bot TeleOp")
@Disabled
public class ScoreAndPark extends Auto<Robot> {

    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ's field is turned half a turn between the alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, facing the field", 0)
         .run("Hold the piece", robot.claw::close)
         .move("To the goal", Tunables.Auto.toGoalInches, 0, 0.6)
         .action(Actions.scoreHigh(robot), 5)
         .parkWithSeconds(3)
         .move("Park", Tunables.Auto.parkInches, 90, 0.7);
    }
}
