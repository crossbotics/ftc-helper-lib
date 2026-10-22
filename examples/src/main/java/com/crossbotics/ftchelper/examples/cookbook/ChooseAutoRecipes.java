package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/**
 * Cookbook: several autonomous modes, and choosing one (docs/guides/autos.md,
 * docs/cookbook.md#several-autos-and-choosing-one). The routes are made from shared pieces in
 * {@link Routes} (Routes.java in your code), so each piece is written once:
 *
 * <ul>
 *   <li>{@link NearPreloadPark} and {@link NearCycle}: two OpModes from the same pieces;</li>
 *   <li>{@link ChooseInInit}: ONE OpMode where the drivers choose the start spot and the plan in INIT.</li>
 * </ul>
 *
 * The numbers are made up for the example: measure your own field. The lines between
 * "// recipe: name" and "// end recipe" are shown in docs/cookbook.md; tools/check_docs.py keeps them
 * the same.
 */
public class ChooseAutoRecipes {

    /** A mecanum robot with a lift and a claw (it scores one piece at a time). */
    public static class Robot extends RobotDefinition {
        public Lift lift;
        public Claw claw;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
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

    // recipe: autos-routes
    /** The pieces every autonomous is made from (Routes.java). Write each piece once. */
    public static class Routes {
        /** Score the piece the robot starts with: hold it, drive to the goal, score. */
        public static Steps scorePreload(Steps s, Robot robot) {
            return s.run("Hold the preload", robot.claw::close)
                    .move("To the goal", Tunables.Auto.toGoalInches, 0, 0.6)
                    .action(Actions.scoreHigh(robot), 5);
        }

        /** One more piece: to the pieces, grab one, back to the goal, score. */
        public static Steps cycle(Steps s, Robot robot) {
            return s.move("To the pieces", Tunables.Auto.pickupInches, 270, 0.6)   // 270 = left
                    .run("Grab", robot.claw::close)
                    .waitSeconds("Let the claw close", 0.3)
                    .move("Back to the goal", Tunables.Auto.pickupInches, 90, 0.6)
                    .action(Actions.scoreHigh(robot), 5);
        }

        /** The park. It keeps the last 3 seconds for itself, so it always runs. */
        public static Steps park(Steps s) {
            return s.parkWithSeconds(3)
                    .move("Park", Tunables.Auto.parkInches, 90, 0.7);
        }
    }
    // end recipe

    // recipe: autos-two-opmodes
    /** NearPreloadPark.java: score the preload, then park. */
    @Autonomous(name = "Near: Preload + Park", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
    @Disabled   // delete this line in your copy
    public static class NearPreloadPark extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;   // BIOBUZZ: the field is turned half a turn, one route for both alliances
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            s.start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0);
            Routes.scorePreload(s, robot);
            Routes.park(s);
        }
    }

    /** NearCycle.java: score the preload, one more piece, then park. Only one line is new. */
    @Autonomous(name = "Near: Preload + Cycle + Park", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
    @Disabled   // delete this line in your copy
    public static class NearCycle extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            s.start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0);
            Routes.scorePreload(s, robot);
            Routes.cycle(s, robot);
            Routes.park(s);
        }
    }
    // end recipe

    // recipe: autos-choose-in-init
    /** ChooseInInit.java: ONE autonomous; the drivers choose the start and the plan in INIT. */
    @Autonomous(name = "Choose in INIT", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
    @Disabled   // delete this line in your copy
    public static class ChooseInInit extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            // The menu lines. The FIRST option of each is chosen at INIT: make it your safest plan.
            s.menuChoice("Start", "Near", "Far")
             .menuChoice("Plan", "Preload + Park", "Preload + Cycle + Park", "Park only");

            // The start spot follows the "Start" line: the note and the simulator's robot change with it.
            s.onlyIf("Start", "Near")
                 .start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0)
             .end()
             .onlyIf("Start", "Far")
                 .start("FAR: against our wall, 2nd tile right of centre, facing the field", 36, -61, 0)
                 .move("Across to the near side", Tunables.Auto.farToNearInches, 270, 0.7)
             .end();

            // The plan: plain Java "if", because the pieces are methods. choice(...) is the option chosen.
            String plan = s.choice("Plan");
            if (!plan.equals("Park only")) Routes.scorePreload(s, robot);
            if (plan.equals("Preload + Cycle + Park")) Routes.cycle(s, robot);
            Routes.park(s);
        }
    }
    // end recipe
}
