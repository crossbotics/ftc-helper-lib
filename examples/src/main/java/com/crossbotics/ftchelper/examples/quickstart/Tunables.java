package com.crossbotics.ftchelper.examples.quickstart;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT_FROM_CATALOG and MEASURE (docs/guides/tunables.md)

/**
 * Quick start, file 2 of 5: every number, one section per part. A section's name is the part's name in
 * CamelCase ("intake" reads Intake). DEFAULT_FROM_CATALOG = the library's default (INIT lists them); a
 * number you leave out uses it too. Write your own number in its place once you have tuned it.
 */
public class Tunables {
    public static class Drive {
        // STEP 6: keep the robot slow for the first drives (both 0.4), raise them when it all works.
        public static double maxPower = 0.4, practiceMaxPower = 0.4, slowSpeed = 0.3;
        // The library's defaults until you tune them (strafeCorrection: the Drive Tuner, lesson 6).
        public static double strafeCorrection = DEFAULT_FROM_CATALOG, turnKp = DEFAULT_FROM_CATALOG;
        public static double turnKd = DEFAULT_FROM_CATALOG, moveMinPower = DEFAULT_FROM_CATALOG;
        // Stays false until the Drive Tuner (lesson 6): until then no autonomous drives the robot.
        public static boolean calibrated = false;
    }

    public static class Intake {
        public static double inPower = 1.0, outPower = 0.7;
    }

    /** The autonomous's distance (STEP 8). In the tuning menu too: copy a good value back here. */
    public static class Auto {
        public static double leaveInches = 12;
    }
}
