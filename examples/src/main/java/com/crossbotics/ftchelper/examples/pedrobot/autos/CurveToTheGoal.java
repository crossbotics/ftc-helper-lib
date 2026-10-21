package com.crossbotics.ftchelper.examples.pedrobot.autos;

import com.crossbotics.ftchelper.examples.pedrobot.Robot;
import com.crossbotics.ftchelper.pedro.PedroPath;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * Pedro paths in the same field numbers as driveTo: a straight line to the goal, then a curve to park.
 */
@Autonomous(name = "Pedro Bot Curve to the Goal", group = "Examples")
@Disabled
public class CurveToTheGoal extends Auto<Robot> {
    // The paths, made once. Our field numbers: x right, y away from your wall, heading 0 = facing away.
    static final PedroPath TO_GOAL = PedroPath.line(-56, -61, -30, -30).turnTo(0, 45);
    // A curve: start, a control point that pulls the path toward it, end. Keeps facing away.
    static final PedroPath TO_PARK = PedroPath.curve(-30, -30, -50, -24, -56, -36).keepHeading(0);

    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ's field is turned half a turn between the alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
         .run("Intake on", robot.intake::in)
         .follow("To the goal", TO_GOAL)
         .run("Intake off", robot.intake::stop)
         .parkWithSeconds(4)
         .follow("Park", TO_PARK);
    }
}
