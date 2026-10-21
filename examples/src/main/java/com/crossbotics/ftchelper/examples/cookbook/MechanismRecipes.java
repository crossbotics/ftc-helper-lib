package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.Arm;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.Positioner;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Turret;
import com.crossbotics.ftchelper.robot.Wrist;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Cookbook: mechanisms (docs/cookbook.md#mechanisms). Each part is one line in Robot.java's
 * {@code define()}, plus a field for it ({@code public Lift lift;}) and its numbers in Tunables.java.
 * Its standard buttons come with it.
 *
 * <p>The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class MechanismRecipes {

    /** A robot with one of each: intake, lift, arm, claw, wrist and a servo with named positions. */
    public static class Robot extends RobotDefinition {
        public Intake intake;
        public Lift lift;
        public Arm arm;
        public Claw claw;
        public Wrist wrist;
        public Positioner intakeDeploy;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));

            // recipe: intake-motor
            // At the top of the class: public Intake intake;   Here in define():
            intake = add(Intake.motor("intake"));                  // Tunables.Intake: inPower, outPower
            // end recipe

            // recipe: lift
            // At the top of the class: public Lift lift;   Here in define():
            // a slide on a motor with a switch at the bottom (the switch sets 0 each time it is pressed).
            // part(...): the motor's name in the parts catalog. No switch? Delete .bottomSwitch(...).
            lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
            // end recipe

            // recipe: arm
            // An arm in degrees. gearRatio: motor turns per arm turn. 0 degrees = where it is at INIT.
            arm = add(Arm.motor("arm", "gobilda-5203-117").gearRatio(2));
            // end recipe

            // recipe: claw
            // At the top of the class: public Claw claw;   Here in define():
            claw = add(Claw.servo("claw"));                        // Tunables.Claw: open, closed
            // end recipe

            // recipe: wrist
            // At the top of the class: public Wrist wrist;   Here in define():
            wrist = add(Wrist.servo("wrist"));                     // Tunables.Wrist: any names (down, level, up)
            // end recipe

            // recipe: positioner
            // Any servo with named positions: a gate, a kicker, a fold-out intake.
            intakeDeploy = add(Positioner.servo("intake_deploy")); // Tunables.IntakeDeploy: stowed, deployed
            // Right after START (autonomous and TeleOp): fold the intake out.
            atStart(() -> Action.named("Deploy").then(() -> intakeDeploy.goTo("deployed")));
            // end recipe
        }
    }

    /** Commands for the parts, on buttons: a named height or angle, open and close. */
    @TeleOp(name = "Cookbook Mechanisms", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: mechanism-commands
            // The names are the ones in Tunables.java (capitals do not matter).
            g2.y.press(() -> robot.arm.goTo("score")).says("arm to score");
            g2.a.press(() -> robot.arm.goTo("pickup")).says("arm to pick up");
            g2.x.press(() -> robot.lift.goTo("high")).says("lift to high");
            g2.b.press(robot.claw::toggle).says("claw open/close");
            g2.back.press(() -> robot.intakeDeploy.goTo("stowed")).says("fold the intake in");
            // end recipe
        }
    }

    /** Intake variants: CR servos, on from START, and a lift that runs the other way. One robot each. */
    public static class IntakeOnServos extends RobotDefinition {
        public Intake intake;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: intake-cr-servos
            // At the top of the class: public Intake intake;   Here in define():
            // two continuous-rotation (CR) servos. The one that spins the wrong way is reversed.
            intake = add(Intake.crServos("left_intake", "right_intake").reversed("right_intake"));
            // end recipe
        }
    }

    public static class IntakeOnAtStart extends RobotDefinition {
        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: intake-on-at-start
            // Runs in from START until stopped. Its button (operator RT) then switches it on/off.
            add(Intake.motor("intake").onAtStart());
            // end recipe
        }
    }

    public static class LiftReversed extends RobotDefinition {
        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: lift-reversed
            // The lift goes DOWN on positive power (the Motor Finder says "add .reversed()"): flip it.
            add(Lift.slide("lift").reversed());
            // end recipe
        }
    }

    /** A flywheel with a feeder. */
    public static class ShooterRobot extends RobotDefinition {
        public Launcher launcher;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: launcher
            // A flywheel (in RPM) and the indexer that feeds it one piece at a time.
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.servo("indexer")));
            // end recipe
        }
    }

    /** A camera that knows the goal, a turret that follows it, and a launcher that waits for both. */
    public static class AimingRobot extends RobotDefinition {
        public Camera camera;
        public Turret turret;
        public Launcher launcher;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: turret-follows-camera
            // The camera knows the goal of each alliance (BLUE first, then RED).
            camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
            // 5 motor turns per turret turn. follows(camera): it follows the goal from START,
            // but never turns past Tunables.Turret min and max.
            turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
            // end recipe
            // recipe: launcher-aimed
            // The speed comes from the distance to the goal (Tunables.Launcher.speedByDistance),
            // and a piece is only fed when the turret points at the goal.
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.servo("indexer"))
                    .speedFromDistance(camera)
                    .requireReady("turret aimed", () -> turret.isAimed()));
            // end recipe
        }
    }
}
