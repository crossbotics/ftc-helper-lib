package com.crossbotics.ftchelper.examples.andymarkbot;

/**
 * AndyMark starter bot, file 2 of 5: the numbers. If you tune a number live on the robot, copy the new
 * value back into this file, or it is lost at the next build.
 */
public class Tunables {
    /** calibrated: set true after the Drive Tuner (autonomous refuses to run before). */
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        public static boolean calibrated = false;
    }

    /** AndyMark's code runs the rollers at full power both ways. */
    public static class RollerI {
        public static double inPower = 1.0, outPower = 1.0;
    }

    /**
     * The catapult. power: 0.65, from AndyMark's code. feedSec: how long the motor runs per press. The
     * motor turns at about 130 RPM with no load, so one turn at 0.65 power takes about 0.7 s, more with
     * the rubber bands pulling. 0.8 s is a starting guess: tune it until one press throws once and the
     * arm stops ready for the next throw.
     */
    public static class Catapult {
        public static double power = 0.65, feedSec = 0.8;
    }

    public static class Auto {
        public static double leaveInches = 20, leavePower = 0.5;
        public static double secondsBetweenThrows = 0.5;
    }
}
