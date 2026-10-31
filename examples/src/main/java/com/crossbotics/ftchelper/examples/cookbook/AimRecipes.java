package com.crossbotics.ftchelper.examples.cookbook;

import static com.crossbotics.ftchelper.robot.Starting.*;

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
 * The lines between "// recipe: name" and "// end recipe" are shown in docs/cookbook.md.
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
        public static class Aim {                                       // a fixed name: always Aim
            // The goal on the field, in inches, seen from your drivers' wall (the same numbers as driveTo).
            public static double goalX = 13;                            // inches right of centre (BIOBUZZ)
            public static double goalY = -13;                           // inches from centre, - = our half (BIOBUZZ)
            public static boolean mirrorForRed = false;                 // BIOBUZZ: turned, not mirrored (true)
            public static double turnKp = DEFAULT;                      // whole-robot aim: push per degree off (0.02)
            public static double turnKd = DEFAULT;                      // brake near the goal (0.002)
            public static double maxTurnPower = DEFAULT;                // the most it turns by itself (0.5)
            public static double toleranceDeg = DEFAULT;                // "aimed" within this, degrees (2)
            public static double rumbleDeg = DEFAULT;                   // rumbleWhenAimed(): buzz within this (4)
        }
        // end recipe

        public static class Turret {
            public static double min = -90, max = 90;
        }

        public static class Launcher {
            public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 1500;
            // recipe: tunables-shot-tables
            // IN PLACE OF the speedByDistance line: one table per game piece, {inches to the goal, RPM}.
            // The POLLEN table (example rows: write the Shot Tuner's).
            public static double[][] speedByDistancePollen = {{24, 2200}, {48, 2550}, {72, 2900}};
            // The NECTAR table.
            public static double[][] speedByDistanceNectar = {{24, 2400}, {48, 2800}, {72, 3200}};
            // end recipe
        }

        public static class Indexer {
            public static double rest = 0.15, push = 0.55;
        }
    }

    /** The numbers for the shoot-on-the-move recipe (its own set: it is off until tuned on a real robot). */
    public static class MovingShotNumbers {
        /** Shooting while the robot drives. Needs odometry. Off until tuned. */
        public static class Aim {
            public static double goalX = 13, goalY = -13;
            public static boolean mirrorForRed = false;
            // recipe: tunables-moving-shot
            // ADD these lines to your Tunables.Aim section (keep goalX, goalY and the others).
            public static boolean shootOnMove = false;                  // true only after testing on the real robot
            public static double flightSec = MEASURE;                   // seconds in the air (slow-motion video)
            public static double flightSecPerIn = DEFAULT;              // longer shots fly longer: per inch (0)
            public static double maxLeadIn = DEFAULT;                   // never lead more than this, inches (24)
            // Where the shooter (a turret's pivot) and a camera on the body are from the robot's centre.
            public static double shooterForwardIn = MEASURE;            // inches forward of the centre (a tape)
            public static double shooterLeftIn = MEASURE;               // inches left of the centre (a tape)
            public static double cameraForwardIn = MEASURE;             // inches forward of the centre (a tape)
            public static double cameraLeftIn = MEASURE;                // inches left of the centre (a tape)
            // end recipe
        }
    }

    /** The numbers for the speed-trim recipe (its own set, so the other recipes stay short). */
    public static class TrimNumbers {
        public static class Drive {
            public static double maxPower = 0.6, slowSpeed = 0.3;
        }

        public static class Launcher {
            public static double speedRpm = 2600, readyBandRpm = 60, idleRpm = 1500;
            public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}};
            // recipe: tunables-speed-trim
            // In your Tunables.Launcher section (change the lines you already have, add the others).
            public static double kP = MEASURE;                          // speed control (Flywheel Finder)
            public static double kI = MEASURE;                          // speed control (Flywheel Finder)
            public static double kD = DEFAULT;                          // speed control (0)
            public static double kF = MEASURE;                          // steady power (Flywheel Finder)
            public static double nominalVolts = MEASURE;                // battery volts for kF (Flywheel Finder)
            public static double trimStepPercent = DEFAULT;             // one D-pad press (1%)
            public static double trimMaxPercent = DEFAULT;              // never more than this each way (15%)
            // Battery compensation: true = F follows the battery. Try it on a real robot and tell us.
            public static boolean batteryCompensation = false;          // (off until proven on a real robot)
            // end recipe
        }

        public static class Indexer {
            public static double rest = 0.15, push = 0.55;
        }
    }

    /** A robot whose speed comes from the camera's distance: the D-pad trims it. */
    public static class TrimRobot extends RobotDefinition {
        public Camera camera;
        public Launcher launcher;

        @Override
        protected void define() {
            tunables(TrimNumbers.class);
            drive(Drive.tank("left_drive", "right_drive"));
            camera = add(Camera.webcam("Webcam 1").goal("BLUE SCORING", "RED SCORING"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
                    .speedFromDistance(camera));
        }
    }

    /** The speed trim from your own buttons and an action. */
    @TeleOp(name = "Cookbook Speed Trim", group = "Cookbook")
    @Disabled
    public static class TrimControls extends ControlMap<TrimRobot> {
        @Override
        protected void buttons(TrimRobot robot, Pad g1, Pad g2) {
            // recipe: speed-trim
            // The launcher's own buttons already trim: gamepad 2 D-pad up/down = +/- 1%.
            // Your own: a bigger step on a button, and a far shot that sets the trim first.
            g2.y.press(() -> robot.launcher.trimBy(+3)).says("speed trim +3%");
            g2.a.press(Action.named("Far shot +5%")
                    .then(() -> robot.launcher.setTrim(+5), robot.launcher::fire)
                    // The launcher fires or gives up within 3 s, so 4 s is always enough.
                    .waitUntil("fired", () -> !robot.launcher.isShooting(), 4.0)
                    .then(robot.launcher::resetTrim));
            // Last match's trim (INIT offers it as "Trim last time"): from your own button too.
            g2.x.press(robot.launcher::useLastTrim).says("use last match's trim");
            // end recipe
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
            // .part(...): the indexer's servo (optional, recommended).
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
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

            // recipe: moving-shot-button
            // Shooting on the move on/off (on from START when Tunables.Aim.shootOnMove = true): if
            // the shots go wide while driving, switch it off and shoot standing still.
            g2.a.toggle(robot.aim().movingShotMode()).says("shoot on the move on/off").rumble();
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

    /** Venue lighting: the camera tries a few settings in INIT and keeps the best. */
    public static class LightingRobot extends RobotDefinition {
        public Camera camera;
        public Camera pieceCamera;

        @Override
        protected void define() {
            tunables(Numbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            // recipe: camera-lighting
            // In INIT the Limelight tries these pipelines (your usual one FIRST) and keeps the best one.
            camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
            // A webcam: Camera.webcam("Webcam 1").autoExposure().goal(...) tries the camera's own
            // exposure, then 3, 6 and 12 ms.
            // end recipe

            // recipe: huskylens
            // At the top of the class: public Camera pieceCamera;   Colors you taught it: ID1, ID2.
            pieceCamera = add(Camera.huskyLens("huskylens").colors().label(1, "POLLEN").label(2, "NECTAR"));
            // Or AprilTags: list which real tag each taught number is, and add goal(...).
            // camera = add(Camera.huskyLens("huskylens").tag(1, 34).tag(2, 35).goal("BLUE SCORING", "RED SCORING"));
            // end recipe
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
            // Hold RB: the robot turns to face the goal by itself; the driver keeps forward and sideways.
            g1.rb.hold(robot.aimMode()::on, robot.aimMode()::off).says("hold: aim the robot at the goal");
            // Or a toggle. Careful: while it is on, the robot turns by itself, even after you let go.
            // g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();
            // end recipe
            g2.rumbleWhenAimed();
        }
    }
}
