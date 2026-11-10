package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * The five files walkthrough, Actions.java (docs/five-files/04-actions-java.md): an action for a robot
 * with the two common parts, an intake and a launcher, on a button and in an autonomous.
 *
 * The lines between "// recipe: name" and "// end recipe" are shown in the docs.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class FiringActionRecipes {

    /** The robot of the Robot.java page with its two common parts: a mecanum drive, an intake, a launcher. */
    public static class Robot extends RobotDefinition {
        public Intake intake;
        public Launcher launcher;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                    .motor("gobilda-5203-312").wheel("gobilda-gripforce-mecanum-104"));
            intake = add(Intake.motor("intake").part("gobilda-5203-1150").holds(3));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.servo("indexer").part("gobilda-2000-speed")));
        }
    }

    /** What goes in Actions.java. */
    public static class Actions {
        // recipe: action-fire-with-intake
        /** Fires 3 pieces with the intake running, to push each one in. Then the intake stops. */
        public static Action fireWithIntake(Robot r) {
            return Action.named("Fire with intake")
                    .then(r.intake::in, () -> r.launcher.fire(3))
                    .waitUntil("the shots", () -> !r.launcher.isShooting(), 9.0)
                    .then(r.intake::stop);
        }
        // end recipe
    }

    /** The action on a button. */
    @TeleOp(name = "Cookbook Fire With Intake", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            robot.intake.inOn(g2.rt);
            robot.intake.outOn(g2.lt);
            robot.launcher.spinOn(g2.lb);
            robot.launcher.fireOn(g2.rb);
            // recipe: action-fire-button
            g2.y.press(Actions.fireWithIntake(robot)).says("fire 3 with the intake");
            g2.x.press(robot.launcher.fireAllAction()).says("fire all");   // a ready-made action
            // end recipe
        }
    }

    /** The same action in an autonomous. */
    @Autonomous(name = "Cookbook Fire With Intake Auto", group = "Cookbook")
    @Disabled
    public static class FireAuto extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            // recipe: action-fire-in-auto
            s.start("Against our wall, facing the field", 0)
             .alongside(robot.launcher.readyAction(3))          // spin up while it drives
             .forward("To the shooting spot", 24, 0.5)
             .action(Actions.fireWithIntake(robot), 9)          // waits until it is done (at most 9 s)
             .right("Park", 12, 0.5);
            // end recipe
        }
    }
}
