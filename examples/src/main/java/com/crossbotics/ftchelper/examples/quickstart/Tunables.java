package com.crossbotics.ftchelper.examples.quickstart;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT_FROM_CATALOG and MEASURE (docs/guides/tunables.md)

/**
 * Quick start, file 2 of 5: every number, one section per part, one number per line. A section's name
 * is the part's name in CamelCase ("intake" reads Intake). DEFAULT_FROM_CATALOG = the library's default
 * (in brackets in the comment; INIT lists them); a number you leave out uses it too. Write your own
 * number in its place once you have tuned it.
 */
public class Tunables {
    public static class Drive {
        // STEP 6: keep the robot slow for the first drives (both 0.4), raise them when it all works.
        public static double maxPower = 0.4;                        // top wheel power: slow for the first drives (1.0)
        public static double practiceMaxPower = 0.4;                // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT_FROM_CATALOG;      // top power with the slow trigger held (0.3)
        // The library's defaults until you tune them (strafeCorrection: the Drive Tuner, lesson 6).
        public static double strafeCorrection = DEFAULT_FROM_CATALOG; // sideways moves: Drive Tuner (1.15)
        public static double turnKp = DEFAULT_FROM_CATALOG;         // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT_FROM_CATALOG;         // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT_FROM_CATALOG;   // auto: least power that moves it (0.30)
        // Stays false until the Drive Tuner (lesson 6): until then no autonomous drives the robot.
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }

    public static class Intake {
        public static double inPower = DEFAULT_FROM_CATALOG;        // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = DEFAULT_FROM_CATALOG;       // power pushing pieces out (0.7)
    }

    /** The autonomous's distance (STEP 8). In the tuning menu too: copy a good value back here. */
    public static class Auto {
        public static double leaveInches = 12;                      // drive off the start line
    }
}
