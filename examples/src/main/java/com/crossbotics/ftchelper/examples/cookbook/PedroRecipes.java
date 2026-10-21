package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.examples.pedrobot.PedroConstants;
import com.crossbotics.ftchelper.pedro.Pedro;
import com.crossbotics.ftchelper.pedro.PedroPath;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * Cookbook: Pedro Pathing paths (docs/cookbook.md#pedro-paths). Needs the pedro folder in your project
 * (docs/modules/pedro.md) and a mecanum drive. Paths use our field numbers, the same as driveTo.
 *
 * <p>The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class PedroRecipes {

    /** Lesson 8's robot plus the follower line. */
    public static class Robot extends RobotDefinition {
        public Intake intake;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: pedro-follower
            odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));   // Pedro needs odometry
            // Pedro drives the paths with the drive and odometry lines above. PedroConstants holds the
            // numbers from Pedro's Foresight tuner (docs/modules/pedro.md#tuning-pedro).
            follower(Pedro.follower(PedroConstants.foresight));
            // end recipe
            intake = add(Intake.motor("intake"));
        }
    }

    @Autonomous(name = "Cookbook Pedro Paths", group = "Cookbook")
    @Disabled
    public static class Paths extends Auto<Robot> {
        // recipe: pedro-line-curve
        // Make each path once. A line: from (x1, y1) to (x2, y2). A curve: start, a control point
        // that pulls the curve toward it (the robot does not drive through it), end.
        static final PedroPath OUT = PedroPath.line(-56, -61, -56, -36).keepHeading(0);
        static final PedroPath TO_GOAL = PedroPath.curve(-56, -36, -44, -36, -30, -30).turnTo(0, 45);
        // end recipe

        // recipe: pedro-chain
        // Two parts with NO stop between them (then), a slow part near the goal (maxSpeed),
        // and hold the end point while it scores (holdEnd: at most 5 s).
        static final PedroPath IN_ONE_GO = PedroPath.line(-56, -61, -48, -50).keepHeading(0)
                .then(PedroPath.curve(-48, -50, -44, -36, -30, -30)).turnTo(0, 45)
                .maxSpeed(0.6)
                .holdEnd();
        // end recipe

        // recipe: pedro-face-point
        // Keep facing one field point the whole way (here x -60, y 60: put your goal's x and y).
        static final PedroPath WATCH_GOAL = PedroPath.line(-30, -30, -56, -36).facePoint(-60, 60);
        // end recipe

        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            // recipe: pedro-follow
            s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
             .follow("Out", OUT)                     // brakes to a stop at its end
             .follow("To the goal", TO_GOAL)
             .run("Intake on", robot.intake::in)
             .follow("Back", WATCH_GOAL)
             .parkWithSeconds(4)
             .driveTo("Park", -56, -36, 0, 0.7);     // driveTo works too (it ends any hold first)
            // end recipe
        }
    }

    @Autonomous(name = "Cookbook Pedro In One Go", group = "Cookbook")
    @Disabled
    public static class InOneGo extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
             .follow("To the goal", Paths.IN_ONE_GO)
             .waitSeconds("Score (the robot holds still)", 0.5);
        }
    }
}
