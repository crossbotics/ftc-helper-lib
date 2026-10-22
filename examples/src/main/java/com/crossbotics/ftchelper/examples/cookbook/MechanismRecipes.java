package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.Arm;
import com.crossbotics.ftchelper.robot.Auto;
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
import com.crossbotics.ftchelper.robot.Steps;
import com.crossbotics.ftchelper.robot.Turret;
import com.crossbotics.ftchelper.robot.Wrist;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
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
            // .part(...) is optional: the motor's catalog id, so the simulator moves it like the real
            // motor (without it the robot works the same). No switch? Delete .bottomSwitch(...).
            lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
            // end recipe

            // recipe: arm
            // An arm in degrees. Its motor's catalog id is in the brackets (not optional, like a turret's
            // or launcher's): it needs the motor's counts per turn to work in degrees.
            // gearRatio: motor turns per arm turn. 0 degrees = where it is at INIT.
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

    /** A wrist that uses its servo's whole turn (500-2500 us instead of the SDK's 600-2400). */
    public static class WristFullRange extends RobotDefinition {
        public Wrist wrist;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: servo-full-range
            // The hub normally sends 600-2400 us for positions 0 to 1: a goBILDA 2000 servo then turns
            // 270 of its 300 degrees. fullRange() sends its whole 500-2500 us: 0 to 1 = 300 degrees.
            // Turn it on FIRST, then find the positions (Servo Finder: press Y while choosing the servo).
            wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed").fullRange());
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

    /** A lift with a motor on each side of the slide. */
    public static class TwoMotorLift extends RobotDefinition {
        public Lift lift;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: lift-two-motors
            // A slide with a motor on each side. Both get the same power, always (stop and pause too).
            // Only the FIRST motor's encoder is read: plug the encoder cable into it. The numbers are in
            // Tunables.LiftLeft (the first motor's name in CamelCase), not Tunables.Lift.
            // The motors face each other, so the second turns the other way: alsoMotorReversed.
            // (Both turn the same way for "up"? Use .alsoMotor("lift_right") instead.)
            lift = add(Lift.slide("lift_left").part("gobilda-5203-435").alsoMotorReversed("lift_right"));
            // end recipe
        }
    }

    /** Its TeleOp: the lift's standard buttons (operator A, X, Y, D-pad, left stick) move both motors. */
    @TeleOp(name = "Cookbook Two Motor Lift", group = "Cookbook")
    @Disabled
    public static class TwoMotorLiftControls extends ControlMap<TwoMotorLift> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(TwoMotorLift robot, Pad g1, Pad g2) { }
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

    /**
     * A robot built in steps: the deploy servo is written in Robot.java but not on the robot yet.
     * The simulator test runs it with "intake_deploy" left out of the configuration.
     */
    public static class NotOnTheRobotYet extends RobotDefinition {
        public Positioner intakeDeploy;
        public Intake intake;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: optional-part
            // The deploy servo is not on the robot yet. optional(): while "intake_deploy" is not in
            // the robot configuration, INIT goes on (with a note) and the part is off.
            intakeDeploy = add(Positioner.servo("intake_deploy").optional());
            intake = add(Intake.motor("intake"));
            // At START: fold the intake out, then run it (the action is in Actions.java, below). With
            // no deploy servo the action skips the servo (and the wait for it) and just starts the intake.
            atStart(() -> Actions.deployIntake(this));
            // end recipe
        }
    }

    /** What goes in Actions.java for the optional part (in your code: the class Actions in Actions.java). */
    public static class Actions {
        // recipe: optional-part-action
        /** Folds the intake out with the deploy servo, then runs the intake. */
        public static Action deployIntake(NotOnTheRobotYet r) {   // in your Actions.java: (Robot r)
            return Action.named("Deploy intake")
                    .then(() -> r.intakeDeploy.goTo("deployed"))
                    .waitUntil("the deploy servo", r.intakeDeploy::isDone, 2.0)
                    .then(r.intake::in, () -> r.intakeDeploy.goTo("stowed"));
        }
        // end recipe
    }

    /** Its buttons: the deploy button is off while the servo is not on the robot. */
    @TeleOp(name = "Cookbook Not On The Robot Yet", group = "Cookbook")
    @Disabled
    public static class NotOnTheRobotYetControls extends ControlMap<NotOnTheRobotYet> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(NotOnTheRobotYet robot, Pad g1, Pad g2) {
            // recipe: optional-part-button
            // needs(...): while the deploy servo is not on the robot, X does nothing and the
            // button map at INIT says "off: IntakeDeploy not on the robot".
            g2.x.press(Actions.deployIntake(robot)).says("deploy intake").needs(robot.intakeDeploy);
            // end recipe
        }
    }

    /** An autonomous that uses the part: it keeps going without it (the report says what was skipped). */
    @Autonomous(name = "Cookbook Not On The Robot Yet Auto", group = "Cookbook")
    @Disabled
    public static class NotOnTheRobotYetAuto extends Auto<NotOnTheRobotYet> {
        @Override
        protected void steps(Steps s, NotOnTheRobotYet robot) {
            s.start("Against our wall, facing the field", 0)
             .action(Actions.deployIntake(robot))
             .move("Forward", 12, 0, 0.5);
        }
    }
}
