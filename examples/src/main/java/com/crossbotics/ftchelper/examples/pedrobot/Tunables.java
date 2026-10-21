package com.crossbotics.ftchelper.examples.pedrobot;

/** The Pedro bot's numbers (Pedro's own numbers are in PedroConstants). */
public class Tunables {
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.1, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.25;
        public static boolean calibrated = false;
    }

    /** checked: set true after the Odometry Tuner; until then follow and driveTo steps are skipped. */
    public static class Odometry {
        public static boolean checked = false;
    }

    public static class Intake {
        public static double inPower = 1.0, outPower = 0.7;
    }
}
