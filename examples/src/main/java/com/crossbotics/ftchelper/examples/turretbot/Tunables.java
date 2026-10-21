package com.crossbotics.ftchelper.examples.turretbot;

/**
 * Example robot 2, file 2 of 5: every number, one section per part (see example robot 1's
 * Tunables for how tuning works).
 */
public class Tunables {
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        public static boolean calibrated = false;
    }

    /** Limelight pipeline 0 finds AprilTags. */
    public static class Limelight {
        public static double pipeline = 0;
    }

    public static class Intake {
        public static double inPower = 1.0, outPower = 0.7;
    }

    /**
     * The turret's range in degrees (0 = straight forward, counterclockwise +): it never turns past
     * these, even when the goal is further round. cameraOffset: the camera's bearing to the goal when
     * the launcher is aimed at it (0 if the camera points the same way as the launcher).
     */
    public static class Turret {
        public static double min = -90, max = 90;
        public static double kP = 0.02, kI = 0, kD = 0.001;
        public static double maxPower = 0.8, tolerance = 1.5, cameraOffset = 0;
    }

    /**
     * Distance to the goal (inches) and flywheel speed (RPM) that scores from there, measured on the
     * field (the Shot Tuner helps). Between rows the speed is blended. speedRpm: the speed until the
     * goal is first seen.
     */
    public static class Launcher {
        public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 1500;
        public static double kP = 40, kI = 0, kD = 0, kF = 12.5;   // the hub's speed control (Flywheel Finder)
        public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}, {96, 3250}};
    }

    public static class Indexer {
        public static double rest = 0.15, push = 0.55, pushSec = 0.25, returnSec = 0.25;
    }

    /** The claw's fold-out pivot: tucked in, down to grab, and up over a FLOWER. */
    public static class ClawFold {
        public static double stowed = 0.05, grab = 0.62, flower = 0.40;
        public static double travelSec = 0.8;   // about how long a fold takes
    }

    public static class Claw {
        public static double open = 0.20, closed = 0.75;
        public static double travelSec = 0.6;
    }
}
