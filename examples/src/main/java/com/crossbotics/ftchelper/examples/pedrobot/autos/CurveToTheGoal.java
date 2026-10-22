package com.crossbotics.ftchelper.examples.pedrobot.autos;

import com.crossbotics.ftchelper.examples.pedrobot.Robot;
import com.crossbotics.ftchelper.pedro.PedroPath;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.PathAt;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * Pedro paths in the same field numbers as driveTo: a line then a curve to the goal (no stop between
 * them), starting the intake halfway along it, holding still to score, then a driveTo to park.
 */
@Autonomous(name = "Pedro Bot Curve to the Goal", group = "Examples")
@Disabled
public class CurveToTheGoal extends Auto<Robot> {
    // The paths, made once. Our field numbers: x right, y away from your wall, heading 0 = facing away.
    // Two parts with no stop between them: a straight start, then a curve (start, a control point that
    // pulls the curve toward it, end), turning to 45 degrees. It holds the end point while it scores.
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
         // PathAt: start something ALONG the path while the robot keeps driving. Here the intake
         // starts halfway (0.5 of the path). It happens once; if the path ends early, the report
         // says "not fired: Intake on". Docs: docs/modules/pedro.md, "Do things along the path".
         .follow("To the goal", TO_GOAL, PathAt.fraction(0.5, "Intake on", robot.intake::in))
         .waitSeconds("Score (the robot holds still)", 0.5)
         .run("Intake off", robot.intake::stop)
         .parkWithSeconds(4)
         // driveTo ends the hold first: Pedro and driveTo never drive the wheels at once.
         .driveTo("Park", -56, -36, 0, 0.7);
    }
}
