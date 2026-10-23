package com.crossbotics.ftchelper.examples.odometrybot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT_FROM_CATALOG (docs/guides/tunables.md)

/**
 * Lesson 7's robot, every number, one per line. DEFAULT_FROM_CATALOG = the library's default (in
 * brackets in the comment).
 */
public class Tunables {
    public static class Drive {
        public static double maxPower = DEFAULT_FROM_CATALOG;       // top wheel power (1.0)
        public static double practiceMaxPower = DEFAULT_FROM_CATALOG; // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT_FROM_CATALOG;      // top power with the slow trigger held (0.3)
        public static double strafeCorrection = 1.1;                // sideways moves: from the Drive Tuner
        public static double turnKp = DEFAULT_FROM_CATALOG;         // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT_FROM_CATALOG;         // auto turns: brake near the end (0.003)
        public static double moveMinPower = 0.25;                   // auto: least power that moves it (tested)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }

    /**
     * checked: set true after the Odometry Tuner (lesson 7); until then driveTo steps are skipped.
     * resetX, resetY, resetHeading: the "set pose here" spot for TeleOp (hold G1 START 1 s with the robot
     * there), written for BLUE like the start spot: here the same corner tile, facing the field.
     * mirrorForRed: false = RED uses the same numbers from the red wall (BIOBUZZ's field is turned half
     * a turn, like the autos' mirrorForRed()); true = mirrored left-right (a mirrored field).
     */
    public static class Odometry {
        public static boolean checked = false;                      // true after the Odometry Tuner's loop test
        public static double resetX = -56;                          // "set pose here" spot: inches across the field
        public static double resetY = -61;                          // "set pose here" spot: inches from the centre
        public static double resetHeading = 0;                      // heading there, degrees: 0 = facing the field
        public static boolean mirrorForRed = false;                 // BIOBUZZ: false
    }

    public static class Intake {
        public static double inPower = DEFAULT_FROM_CATALOG;        // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = DEFAULT_FROM_CATALOG;       // power pushing pieces out (0.7)
    }

    /** Field points for the autonomous (inches, seen from your drivers' wall, written for BLUE). */
    public static class Auto {
        public static double goalX = -30;                           // the scoring spot: inches across the field
        public static double goalY = -30;                           // the scoring spot: inches from the centre
        public static double goalHeading = 45;                      // facing there, degrees
        public static double parkX = -56;                           // the park spot: inches across the field
        public static double parkY = -36;                           // the park spot: inches from the centre
    }
}
