package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * Cookbook: autonomous (docs/cookbook.md#autonomous). An autonomous is one file in autos/: a list of
 * named steps, read in INIT (for the menu and the start note, again when a choice changes; nothing runs)
 * and after START. Inches, degrees (0 forward, 90 right), power 0 to 1. The robot only drives after the
 * Drive Tuner (Tunables.Drive.calibrated = true); the simulator runs it anyway.
 * The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class AutonomousRecipes {

    /** A mecanum robot with an intake, a lift and a claw. */
    public static class Robot extends RobotDefinition {
        public Intake intake;
        public Lift lift;
        public Claw claw;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            intake = add(Intake.motor("intake"));
            lift = add(Lift.slide("lift").bottomSwitch("lift_bottom"));
            claw = add(Claw.servo("claw"));
        }
    }

    /** The actions these autos use (Actions.java in your code). */
    public static class Actions {
        public static Action scoreHigh(Robot r) {
            return Action.named("Score high")
                    .then(() -> r.lift.goTo("high"))
                    .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)
                    .then(r.claw::open)
                    .waitSeconds(0.3)
                    .then(() -> r.lift.goTo("down"));
        }
    }

    /** Driving: forward, sideways, turns. */
    @Autonomous(name = "Cookbook Moves and Turns", group = "Cookbook")
    @Disabled
    public static class MovesAndTurns extends Auto<Robot> {
        // recipe: auto-turned-field
        // BIOBUZZ's field is turned half a turn between the alliances: one route works for both.
        @Override
        protected boolean mirrorForRed() {
            return false;
        }
        // end recipe

        @Override
        protected void steps(Steps s, Robot robot) {
            // recipe: auto-moves
            s.start("Against our wall, facing the field", 0)
             .move("Forward", 24, 0, 0.5)            // inches, direction (0 = forward), max power
             .move("Sideways to the right", 12, 90, 0.5)   // mecanum only
             .turn("Turn left 90", 90, 0.4)         // turns: counterclockwise (left) is +
             .turn("Turn right 90", -90, 0.4)       // so a right turn is minus
             .turnTo("Face the start way", 0, 0.4); // to a heading: 0 = the way it faced at the start
            // end recipe
        }
    }

    /** A shorter start delay in the INIT menu, and one number that differs on RED. */
    @Autonomous(name = "Cookbook Delay and Alliance", group = "Cookbook")
    @Disabled
    public static class DelayAndAlliance extends Auto<Robot> {
        // recipe: auto-max-start-delay
        // Inside your autonomous class, next to steps(...): the INIT menu's "Start delay" line
        // offers 0 to 5 s (the default is 0 to 10 s).
        @Override
        protected double maxStartDelaySec() {
            return 5;
        }
        // end recipe

        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            // recipe: auto-alliance-pick
            // Inside steps(...): alliance() is the alliance chosen in INIT. pick(blue, red) gives the
            // first value on BLUE and the second on RED (here: RED waits 2 s for its partner).
            double waitSec = alliance().pick(0.0, 2.0);
            s.start("Against our wall, facing the field", 0)
             .waitSeconds("Let our partner go first", waitSec)
             .move("Leave the start", 24, 0, 0.4);
            // end recipe
        }
    }

    /** A choice in INIT, mechanisms while driving, and time to park. */
    @Autonomous(name = "Cookbook Score and Park", group = "Cookbook")
    @Disabled
    public static class ScoreAndPark extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            // recipe: auto-menu
            s.start("Against our wall, facing the field", 0)
             .menuYesNo("Score", true)                  // the drivers choose in INIT with the D-pad
             .menuChoice("Park", "Near", "Far")
             .onlyIf("Score")                           // these steps run only if "Score" is yes
                 .move("To the goal", Tunables.Auto.toGoalInches, 0, 0.5)
                 .action(Actions.scoreHigh(robot), 5)
             .end()                                     // never forget end()
             // end recipe
             // recipe: auto-park
             .parkWithSeconds(4)                        // the steps below always get the last 4 s
             .onlyIf("Park", "Near")
                 .move("Park near", Tunables.Auto.parkInches, 90, 0.7)
             .end()
             .onlyIf("Park", "Far")
                 .move("Park far", Tunables.Auto.parkInches * 2, 90, 0.7)
             .end();
            // end recipe
        }
    }

    /** Mechanisms during the route, a wait for a partner, and your own step. */
    @Autonomous(name = "Cookbook Mechanisms on the Way", group = "Cookbook")
    @Disabled
    public static class MechanismsOnTheWay extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            // recipe: auto-run-while-driving
            s.start("Against our wall, facing the field", 0)
             .run("Intake on", robot.intake::in)        // starts it and goes on at once
             .run("Lift up", () -> robot.lift.goTo("high"))
             .move("To the pieces", 24, 0, 0.4)         // the intake and lift work while it drives
             .waitUntil("Lift at the top", robot.lift::isAtTarget, 2.0)
             .run("Intake off", robot.intake::stop);
            // end recipe

            // recipe: wait-in-auto
            s.run("Open the claw", robot.claw::open)
             .waitSeconds("Let the piece drop", 0.5)    // a name for the report, then the seconds
             .run("Lift down", () -> robot.lift.goTo("down"));
            // end recipe

            // recipe: auto-wait-partner
            s.menuYesNo("Wait for partner", false)
             .onlyIf("Wait for partner")
                 .waitSeconds("Let our partner pass", 3)   // the robot keeps running while it waits
             .end();
            // end recipe

            // recipe: auto-own-step
            // Your own step, for anything the list cannot say. Wait with ctx, never sleep().
            s.step("Clear the intake", ctx -> {
                robot.intake.out();
                ctx.waitFor(0.3);          // every part keeps working while it waits
                robot.intake.stop();
            });
            // end recipe
        }
    }

    // recipe: auto-shared-opening
    /** The same first steps for several autos: a method that adds them to the list. */
    static Steps scorePreload(Steps s, Robot robot) {
        return s.move("To the goal", Tunables.Auto.toGoalInches, 0, 0.5)
                .action(Actions.scoreHigh(robot), 5);
    }
    // in each autonomous:  scorePreload(s.start("...", 0), robot).move("Park", 20, 90, 0.7);
    // end recipe

    @Autonomous(name = "Cookbook Shared Opening", group = "Cookbook")
    @Disabled
    public static class SharedOpening extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            scorePreload(s.start("Against our wall, facing the field", 0), robot).move("Park", 20, 90, 0.7);
        }
    }
}
