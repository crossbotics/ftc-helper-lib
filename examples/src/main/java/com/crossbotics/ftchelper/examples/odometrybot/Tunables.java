package com.crossbotics.ftchelper.examples.odometrybot;

/** Lesson 7's robot, every number. */
public class Tunables {
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.1, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.25;
        public static boolean calibrated = false;
    }

    /** checked: set true after the Odometry Tuner (lesson 7); until then driveTo steps are skipped. */
    public static class Odometry {
        public static boolean checked = false;
    }

    public static class Intake {
        public static double inPower = 1.0, outPower = 0.7;
    }

    /** Field points for the autonomous (inches, seen from your drivers' wall, written for BLUE). */
    public static class Auto {
        public static double goalX = -30, goalY = -30, goalHeading = 45;
        public static double parkX = -56, parkY = -36;
    }
}
