package com.crossbotics.ftchelper.examples.pedrobot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT (docs/guides/tunables.md)

/**
 * The Pedro bot's numbers, one per line (Pedro's own numbers are in PedroConstants).
 * DEFAULT = the library's default (in brackets in the comment).
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

    /** checked: set true after the Odometry Tuner; until then follow and driveTo steps are skipped. */
    public static class Odometry {
        public static boolean checked = false;                      // true after the Odometry Tuner's loop test
        // RED: the same numbers from the red wall (BIOBUZZ's turned field), like the autos' mirrorForRed().
        public static boolean mirrorForRed = false;                 // BIOBUZZ: false
    }

    public static class Intake {
        public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
    }
}
