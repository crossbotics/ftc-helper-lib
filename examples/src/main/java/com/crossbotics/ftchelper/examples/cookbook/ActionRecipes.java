package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.Positioner;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Cookbook: actions (docs/cookbook.md#actions). An action is a list of steps that use several parts,
 * with waits between them. It never makes the robot wait: it does a little every loop. The action
 * methods go in Actions.java; the same action works on a button and in an autonomous.
 *
 * <p>The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class ActionRecipes {

    /** A robot with an intake, a lift, a claw and a fold-out intake. */
    public static class Robot extends RobotDefinition {
        public Intake intake;
        public Lift lift;
        public Claw claw;
        public Positioner intakeDeploy;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            intake = add(Intake.motor("intake"));
            lift = add(Lift.slide("lift").bottomSwitch("lift_bottom"));
            claw = add(Claw.servo("claw"));
            intakeDeploy = add(Positioner.servo("intake_deploy"));
            // recipe: action-at-start
            // In Robot.java's define(): this action runs once, right after START (autonomous and TeleOp).
            atStart(() -> Actions.deployIntake(this));
            // end recipe
        }
    }

    /** What goes in Actions.java: one method per action. */
    public static class Actions {
        // recipe: action-steps
        /** Lift up, open the claw, lift down: one step after the other. */
        public static Action scoreHigh(Robot r) {
            return Action.named("Score high")
                    .then(() -> r.lift.goTo("high"))
                    .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)   // at most 2 s
                    .then(r.claw::open)
                    .waitSeconds(0.3)                                        // let the piece drop
                    .then(() -> r.lift.goTo("down"));
        }
        // end recipe

        // recipe: wait-in-action
        /** Opens the claw, waits half a second, then lowers the lift. */
        public static Action dropAndLower(Robot r) {
            return Action.named("Drop and lower")
                    .then(r.claw::open)
                    .waitSeconds(0.5)                     // half a second; the robot keeps running
                    .then(() -> r.lift.goTo("down"));
        }
        // end recipe

        // recipe: action-repeat
        /** Pulses the intake 3 times: the for loop adds the same steps 3 times to the action. */
        public static Action pulseThree(Robot r) {
            Action a = Action.named("Pulse three");
            for (int i = 0; i < 3; i++) {   // runs once, when the action is made: it never waits
                a.then(r.intake::in)
                 .waitSeconds(0.4)
                 .then(r.intake::stop)
                 .waitSeconds(0.3);
            }
            return a;
        }
        // end recipe

        // recipe: action-together
        /** Two parts at once: then(a, b) starts both in the same loop. */
        public static Action grabAndLift(Robot r) {
            return Action.named("Grab and lift")
                    .then(r.claw::close, r.intake::stop)                     // both at once
                    .waitUntil("claw closed", r.claw::isDone, 1.0)           // isDone: it should be there
                    .then(() -> r.lift.goTo("low"));
        }
        // end recipe

        /** Folds the intake out (used by atStart above). */
        public static Action deployIntake(Robot r) {
            return Action.named("Deploy intake").then(() -> r.intakeDeploy.goTo("deployed"));
        }

        // recipe: action-wait-until
        /** Lowers the lift, and opens the claw only once the lift is down. */
        public static Action lowerAndRelease(Robot r) {
            return Action.named("Lower and release")
                    .then(() -> r.lift.goTo("down"))
                    // Waits until the test is true. If 2 s pass first, the action STOPS here and says why.
                    .waitUntil("lift down", () -> r.lift.isAt("down"), 2.0)
                    .then(r.claw::open);
        }
        // end recipe
    }

    /** Actions on buttons. */
    @TeleOp(name = "Cookbook Actions", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: action-button
            // Press Y: the action starts. Press Y again while it runs: it is cancelled, safely.
            g2.y.press(Actions.scoreHigh(robot)).says("score high");
            g2.x.press(Actions.grabAndLift(robot)).says("grab and lift");
            // end recipe

            // recipe: action-cancel-all
            // Stop every running action at once (each stops what it started, safely).
            g2.back.press(robot::cancelAll).says("stop all actions");
            // end recipe
        }
    }

    /** The same action in an autonomous. */
    @Autonomous(name = "Cookbook Action Auto", group = "Cookbook")
    @Disabled
    public static class ActionAuto extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            // recipe: action-in-auto
            s.start("Against our wall, facing the field", 0)
             .move("To the goal", 24, 0, 0.5)
             .action(Actions.scoreHigh(robot), 5)     // waits until it is done (at most 5 s)
             .move("Back up", 12, 180, 0.5);
            // end recipe
        }
    }
}
