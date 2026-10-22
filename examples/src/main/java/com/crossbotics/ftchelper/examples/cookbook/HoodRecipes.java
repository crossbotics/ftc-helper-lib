package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Hood;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Cookbook: a launcher's hood (docs/cookbook.md#how-do-i-add-a-hood-to-my-launcher). The hood tilts
 * to make the shot steeper or flatter; its angle comes from the shot table's third column. Every hood
 * design fits: a servo or a motor with an encoder, a straight gear or a linkage, real angles or plain
 * servo positions.
 *
 * <p>The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class HoodRecipes {

    /** The numbers for a servo hood with two measured angles. In your TeamCode: Tunables.java. */
    public static class Numbers {
        public static class Drive {
            public static double maxPower = 0.6, slowSpeed = 0.3;
        }

        // recipe: tunables-hood
        public static class Hood {
            // The servo positions a hair off the hood's two hard stops (Hood Finder). Never past them.
            public static double posA = 0.20, posB = 0.65;
            // Optional: the hood's angle at each stop (any scale; here 0 = steep end, 30 = flat end).
            public static double angleA = 0, angleB = 30;
            // Stop to stop, seconds: the launcher waits this share of it before firing.
            public static double travelSec = 0.5, homeAngle = 0;
        }

        public static class Launcher {
            public static double speedRpm = 2600, readyBandRpm = 60, idleRpm = 1500;
            // {inches to the goal, RPM, hood angle}: the hood is blended between rows like the speed.
            public static double[][] speedByDistance = {{24, 2200, 3}, {48, 2550, 12}, {72, 2900, 20}};
        }
        // end recipe

        public static class Indexer {
            public static double rest = 0.15, push = 0.55;
        }
    }

    /** A linkage (not a straight line): a calibration table from the Hood Finder. */
    public static class LinkageNumbers {
        public static class Drive {
            public static double maxPower = 0.6;
        }

        // recipe: tunables-hood-linkage
        public static class Hood {
            // {servo position, hood angle} rows, blended between them. The first and last rows are the
            // stops (no posA / posB then). The angles must keep going one way.
            public static double[][] calibration = {{0.20, 0}, {0.32, 8}, {0.45, 18}, {0.65, 30}};
        }
        // end recipe

        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2200, 3}, {48, 2550, 12}, {72, 2900, 20}};
        }
    }

    /** No angles measured: the hood column is a servo position. */
    public static class PositionNumbers {
        public static class Drive {
            public static double maxPower = 0.6;
        }

        // recipe: tunables-hood-positions
        public static class Hood {
            public static double posA = 0.20, posB = 0.65;   // no angleA / angleB: the "angle" IS the position
        }

        public static class Launcher {
            // {inches, RPM, servo position}: the Shot Tuner writes these for you.
            public static double[][] speedByDistance = {{24, 2200, 0.24}, {48, 2550, 0.38}, {72, 2900, 0.50}};
        }
        // end recipe
    }

    /** A hood on a motor with an encoder. */
    public static class MotorNumbers {
        public static class Drive {
            public static double maxPower = 0.6;
        }

        // recipe: tunables-hood-motor
        public static class Hood {
            // Encoder ticks at the two stops, counted from where the hood sits at INIT (start the robot
            // with the hood on stop A), and the angles there.
            public static double posA = 0, posB = 420, angleA = 0, angleB = 30;
            public static double kP = 0.005, maxPower = 0.5, tolerance = 0.5;   // power per tick, ready within 0.5 deg
        }
        // end recipe

        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2200, 3}, {48, 2550, 12}, {72, 2900, 20}};
        }
    }

    /** A launcher with a servo hood, its speed and hood from the camera's distance. */
    public static class Robot extends RobotDefinition {
        public Camera camera;
        public Launcher launcher;

        @Override
        protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left_drive", "right_drive"));
            camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));
            // recipe: robot-hood
            // The hood goes on the launcher; its numbers are in Tunables.Hood.
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.servo("indexer"))
                    .hood(Hood.servo("hood"))
                    .speedFromDistance(camera));
            // end recipe
        }
    }

    /** The same with a linkage hood. */
    public static class LinkageRobot extends RobotDefinition {
        @Override
        protected void define() {
            tunables(LinkageNumbers.class);
            drive(Drive.tank("left_drive", "right_drive"));
            Camera camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000").hood(Hood.servo("hood")).speedFromDistance(camera));
        }
    }

    /** The same with plain servo positions. */
    public static class PositionRobot extends RobotDefinition {
        @Override
        protected void define() {
            tunables(PositionNumbers.class);
            drive(Drive.tank("left_drive", "right_drive"));
            Camera camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000").hood(Hood.servo("hood")).speedFromDistance(camera));
        }
    }

    /** A motor hood. */
    public static class MotorRobot extends RobotDefinition {
        @Override
        protected void define() {
            tunables(MotorNumbers.class);
            drive(Drive.tank("left_drive", "right_drive"));
            Camera camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));
            // recipe: robot-hood-motor
            add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .hood(Hood.motor("hood", "gobilda-5203-117"))   // a motor with an encoder
                    .speedFromDistance(camera));
            // end recipe
        }
    }

    /** Your own hood buttons, on top of the suggested stick. */
    @TeleOp(name = "Cookbook Hood", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: hood-buttons
            // The launcher's hood already has gamepad 2's left stick Y (up = a bigger angle): a trim
            // while the table sets the angle, else the angle itself. Your own: steps and home.
            g2.y.press(() -> robot.launcher.hood().step(+1)).says("hood +1 step");
            g2.a.press(() -> robot.launcher.hood().step(-1)).says("hood -1 step");
            g2.x.press(() -> robot.launcher.hood().home()).says("hood home");
            // end recipe
        }
    }
}
