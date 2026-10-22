package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import java.util.Locale;

/**
 * Cookbook: odometry and driveTo (docs/cookbook.md#odometry-and-driveto). Odometry tells the robot
 * where it is on the field. Field numbers: x inches right of the centre line, y inches away from your
 * wall (negative = your half), heading 0 = facing away from you, counterclockwise +.
 *
 * <p>The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class OdometryRecipes {

    /** A mecanum robot with a goBILDA Pinpoint and an intake. */
    public static class Robot extends RobotDefinition {
        public Intake intake;

        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                    .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
            // recipe: odometry-pinpoint
            // After drive(...). The offsets, mm: first how far LEFT of the centre the forward pod is
            // (right = minus: here 84 mm right), then how far FORWARD the sideways pod is (behind = minus).
            // Not sure? Write offsetsMm(0, 0): the Odometry Tuner measures them.
            odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
            // end recipe
            intake = add(Intake.motor("intake"));
        }
    }

    /** A SparkFun OTOS instead. */
    public static class OtosRobot extends RobotDefinition {
        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: odometry-otos
            // Where the OTOS sits: inches forward and left of the robot's centre, and its turn in degrees.
            odometry(Odometry.otos("otos").mountIn(3.5, 0, 0));
            // end recipe
        }
    }

    /** Three dead wheels on the drive motors' encoder ports. */
    public static class DeadWheelRobot extends RobotDefinition {
        @Override
        protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: odometry-dead-wheels
            // The configuration names of the motor ports the pods plug into: left, right, sideways pod.
            // trackWidthIn: left pod to right pod. perpForwardIn: the sideways pod, 6 in BEHIND the centre.
            odometry(Odometry.threeWheels("par_left", "par_right", "perp")
                    .trackWidthIn(13.2).perpForwardIn(-6));
            // end recipe
        }
    }

    /** Drive to field points. */
    @Autonomous(name = "Cookbook Drive To", group = "Cookbook")
    @Disabled
    public static class DriveToTheGoal extends Auto<Robot> {
        @Override
        protected boolean mirrorForRed() {
            return false;
        }

        @Override
        protected void steps(Steps s, Robot robot) {
            // recipe: drive-to
            // The start spot (x, y, heading) must be where the robot really stands: the pose starts there.
            s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
             .driveTo("To the goal", Tunables.Auto.goalX, Tunables.Auto.goalY, Tunables.Auto.goalHeading, 0.6)
             .parkWithSeconds(4)
             // The last number: "close enough", in inches (default 1).
             .driveTo("Park", Tunables.Auto.parkX, Tunables.Auto.parkY, 0, 0.7, 2.0);
            // end recipe
        }
    }

    /** The robot's place on the Driver Station, and in an action. */
    @TeleOp(name = "Cookbook Where Am I", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        public static boolean oneDriver = false;

        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: show-pose
            show("Where", () -> String.format(Locale.US, "x %.0f, y %.0f in, heading %.0f",
                    robot.pose().x, robot.pose().y, robot.pose().headingDeg));
            // end recipe
            g2.y.press(Actions.intakeOnOurHalf(robot)).says("intake on until the centre line");

            // recipe: reset-pose-button
            // A second spot of your own: hold D-pad down 1 s with the robot in the right corner tile.
            // (x, y, heading written for BLUE; RED uses them like the reset spot in Tunables.Odometry.)
            g1.dpadDown.heldFor(1.0, () -> robot.resetPose(56, -61, 0)).says("hold 1 s: pose = right corner tile");
            // An action can set it too (here: the Tunables.Odometry spot, then the intake runs; the
            // action is in Actions.java, below).
            g1.dpadUp.heldFor(1.0, Actions.poseHereThenIntake(robot)).says("hold 1 s: pose here, intake on");
            // end recipe
        }
    }

    /** Actions.java in your code. */
    public static class Actions {
        // recipe: pose-in-action
        /** Runs the intake while the robot is on our half of the field (y below 0). */
        public static Action intakeOnOurHalf(Robot r) {
            return Action.named("Intake on our half")
                    .then(r.intake::in)
                    .waitUntil("past the centre line", () -> r.pose().y > 0, 10.0)
                    .then(r.intake::stop);
        }
        // end recipe

        // recipe: pose-here-action
        /** Sets the pose to the reset spot in Tunables.Odometry, then runs the intake. */
        public static Action poseHereThenIntake(Robot r) {
            return Action.named("Pose here, intake on")
                    .then(r::resetPose)            // resetPose() with no numbers: the Tunables.Odometry spot
                    .then(r.intake::in);
        }
        // end recipe
    }
}
