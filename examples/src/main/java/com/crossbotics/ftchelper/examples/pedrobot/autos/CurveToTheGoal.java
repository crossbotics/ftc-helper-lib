package com.crossbotics.ftchelper.examples.pedrobot.autos;

import com.crossbotics.ftchelper.examples.pedrobot.Robot;
import com.crossbotics.ftchelper.pedro.PedroPath;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.PathAt;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * Pedro paths in the same field numbers as driveTo: a line then a curve to the goal, the intake
 * starting halfway, holding still to score, then a driveTo to park.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/08-paths-with-pedro/#5-write-your-first-path
 */
@Autonomous(name = "Pedro Bot Curve to the Goal", group = "Examples")
@Disabled
public class CurveToTheGoal extends Auto<Robot> {
    // Two parts with no stop between them, then hold the end point.
    static final PedroPath TO_GOAL = PedroPath.line(-56, -61, -48, -50).keepHeading(0)
            .then(PedroPath.curve(-48, -50, -44, -36, -30, -30)).turnTo(0, 45)
            .holdEnd();

    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ's field is turned half a turn between the alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
         // PathAt: start something along the path, while the robot keeps driving.
         .follow("To the goal", TO_GOAL, PathAt.fraction(0.5, "Intake on", robot.intake::in))
         .waitSeconds("Score (the robot holds still)", 0.5)
         .run("Intake off", robot.intake::stop)
         .parkWithSeconds(4)
         // driveTo ends the hold first: Pedro and driveTo never drive the wheels at once.
         .driveTo("Park", -56, -36, 0, 0.7);
    }
}
