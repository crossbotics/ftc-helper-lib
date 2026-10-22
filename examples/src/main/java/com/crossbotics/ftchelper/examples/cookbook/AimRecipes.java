package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.finders.ShotTunerOpMode;
import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Turret;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Cookbook: aiming and shooting (docs/cookbook.md#aiming-and-shooting). Things the robot can do for
 * the drivers, so they can focus on driving and firing: aim the whole robot, buzz when on target, keep
 * aiming from the odometry when the camera loses the goal, and one shot table per game piece.
 *
 * <p>The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
 * tools/check_docs.py checks that the page and this file say the same thing.
 */
public class AimRecipes {

    /** The numbers these recipes read. In your TeamCode these sections go in Tunables.java. */
    public static class Numbers {
        public static class Drive {
            public static double maxPower = 0.6, slowSpeed = 0.3;
        }

        public static class Odometry {
            public static boolean checked = false;
        }

        // recipe: tunables-aim
        /** Aiming (every line is optional; leave out what you do not use). */
        public static class Aim {
            // The goal on the field, in inches, seen from the BLUE drivers' wall (the same numbers as
            // driveTo): with odometry, aiming goes on when the camera loses the goal.
            public static double goalX = -58, goalY = 55;
            // Default true (RED mirrors x). BIOBUZZ's field is turned, not mirrored: false.
            public static boolean mirrorForRed = false;
            // Whole-robot aim: turn power per degree off, the most it turns by itself, "aimed" within.
            public static double turnKp = 0.02, turnKd = 0.002, maxTurnPower = 0.5, toleranceDeg = 2;
            public static double rumbleDeg = 4;           // rumbleWhenAimed(): buzz within 4 degrees
        }
        // end recipe

        public static class Turret {
            public static double min = -90, max = 90;
        }

        // recipe: tunables-shot-tables
        public static class Launcher {
            public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 1500;
            // One table per game piece ({inches to the goal, RPM}), from the Shot Tuner. The name
            // after "speedByDistance" is the table's name: "pollen" and "nectar".
            public static double[][] speedByDistancePollen = {{24, 2200}, {48, 2550}, {72, 2900}};
            public static double[][] speedByDistanceNectar = {{24, 2400}, {48, 2800}, {72, 3200}};
        }
        // end recipe

        public static class Indexer {
            public static double rest = 0.15, push = 0.55;
        }
    }

    /** A turret robot with a camera AND odometry: the odometry is the backup when the camera loses the goal. */
    public static class Robot extends RobotDefinition {
        public Camera camera;
        public Turret turret;
        public Launcher launcher;

        @Override
        protected void define() {
            tunables(Numbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: aim-odometry-backup
            // With an odometry line and Tunables.Aim goalX, goalY, the turret and the speed keep
            // working when the camera loses the goal: from where the robot is on the field.
            odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
            camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
            turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
                    .speedFromDistance(camera)
                    .startTable("pollen"));                  // the table to start with
            // end recipe
        }
    }

    /** Shot tables switched by buttons, the aim buzz. */
    @TeleOp(name = "Cookbook Aiming", group = "Cookbook")
    @Disabled
    public static class Controls extends ControlMap<Robot> {
        @Override
        protected void buttons(Robot robot, Pad g1, Pad g2) {
            // recipe: shot-table-buttons
            // One button per game piece (the launcher's D-pad right already picks the next table).
            g2.x.press(() -> robot.launcher.useTable("pollen")).says("POLLEN shot table");
            g2.b.press(() -> robot.launcher.useTable("nectar")).says("NECTAR shot table");
            // end recipe

            // recipe: aim-rumble
            // Gamepad 2 buzzes while the aim is close: the closer, the stronger (Tunables.Aim.rumbleDeg).
            // It works for a turret that follows the goal and for whole-robot aim.
            g2.rumbleWhenAimed();
            // end recipe

            g2.y.press(fireNectar(robot));
        }
    }

    // recipe: shot-table-action
    /** An action that picks the NECTAR table, then fires one (Actions.java). */
    public static Action fireNectar(Robot r) {
        return Action.named("Fire NECTAR")
                .then(() -> r.launcher.useTable("nectar"))
                .then(r.launcher::fire);
    }
    // end recipe

    /**
     * The Shot Tuner for this robot: with two tables, Y (until "> Table") and the D-pad choose the one you
     * tune; with odometry, the distance also comes from where the robot is.
     */
    @TeleOp(name = "Cookbook Aim Shot Tuner", group = "Finders")
    @Disabled
    public static class ShotTuner extends ShotTunerOpMode {
        @Override
        protected RobotDefinition robot() {
            return new Robot();
        }
    }

    /** A robot with no turret: a tank drive, a webcam and a launcher. */
    public static class WholeRobot extends RobotDefinition {
        public Camera camera;
        public Launcher launcher;

        @Override
        protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left_drive", "right_drive"));
            camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer")));
        }
    }

    /** Whole-robot aim on a button. */
    @TeleOp(name = "Cookbook Whole-Robot Aim", group = "Cookbook")
    @Disabled
    public static class WholeRobotControls extends ControlMap<WholeRobot> {
        @Override
        protected void buttons(WholeRobot robot, Pad g1, Pad g2) {
            // recipe: aim-whole-robot
            // RB: the robot turns to face the goal by itself; the driver keeps forward and sideways.
            // Moving the turn stick still turns it by hand. Two buzzes = on, one = off.
            // Not with a turret that follows the goal: separate modes (INIT stops with a message).
            g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();
            // end recipe
            g2.rumbleWhenAimed();
        }
    }
}
