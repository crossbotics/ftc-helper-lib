package com.crossbotics.ftchelper.examples.odometrybot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT (docs/guides/tunables.md)

/**
 * Lesson 7's robot, every number, one per line. DEFAULT = the library's default (in
 * brackets in the comment).
 */
public class Tunables {
    public static class Drive {
        public static double maxPower = DEFAULT;                    // top wheel power (1.0)
        public static double practiceMaxPower = DEFAULT;            // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
        public static double strafeCorrection = 1.1;                // sideways moves: from the Drive Tuner
        public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
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
        public static double resetX = -56;                          // pose spot: inches right of centre
        public static double resetY = -61;                          // pose spot: inches from centre, - = our half
        public static double resetHeading = 0;                      // heading there, degrees: 0 = facing the field
        public static boolean mirrorForRed = false;                 // BIOBUZZ: false
    }

    public static class Intake {
        public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
    }

    /** Field points for the autonomous (inches, seen from your drivers' wall, written for BLUE). */
    public static class Auto {
        public static double goalX = -30;                           // scoring spot: inches right of centre
        public static double goalY = -30;                           // scoring spot: inches from centre, - = our half
        public static double goalHeading = 45;                      // facing there, degrees
        public static double parkX = -56;                           // park spot: inches right of centre
        public static double parkY = -36;                           // park spot: inches from centre, - = our half
    }
}
