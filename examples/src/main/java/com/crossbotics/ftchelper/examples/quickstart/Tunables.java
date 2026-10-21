package com.crossbotics.ftchelper.examples.quickstart;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode;

/**
 * Quick start, file 2 of 5: every number, one section per part. A section's name is the part's name in
 * CamelCase ("intake" reads Intake). A number you leave out uses the library's default.
 */
public class Tunables {
    public static class Drive {
        // STEP 6: keep the robot slow for the first drives (both 0.4), raise them when it all works.
        public static double maxPower = 0.4, practiceMaxPower = 0.4, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
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
