package com.crossbotics.ftchelper.examples.andymarkbot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT_FROM_CATALOG (docs/guides/tunables.md)

/**
 * AndyMark starter bot, file 2 of 5: the numbers, one per line. DEFAULT_FROM_CATALOG = the library's
 * default (in brackets in the comment). If you tune a number live on the robot, copy the new value back
 * into this file, or it is lost at the next build.
 */
public class Tunables {
    /** calibrated: set true after the Drive Tuner (autonomous refuses to run before). */
    public static class Drive {
        public static double maxPower = DEFAULT_FROM_CATALOG;       // top wheel power (1.0)
        public static double practiceMaxPower = DEFAULT_FROM_CATALOG; // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT_FROM_CATALOG;      // top power with the slow trigger held (0.3)
        public static double strafeCorrection = DEFAULT_FROM_CATALOG; // not used: a tank drive cannot strafe (1.15)
        public static double turnKp = DEFAULT_FROM_CATALOG;         // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT_FROM_CATALOG;         // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT_FROM_CATALOG;   // auto: least power that moves it (0.30)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }

    /** AndyMark's code runs the rollers at full power both ways. */
    public static class RollerI {
        public static double inPower = DEFAULT_FROM_CATALOG;        // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = 1.0;                        // power pushing pieces out: AndyMark's code
    }

    /**
     * The catapult. power: 0.65, from AndyMark's code. feedSec: how long the motor runs per press. The
     * motor turns at about 130 RPM with no load, so one turn at 0.65 power takes about 0.7 s, more with
     * the rubber bands pulling. 0.8 s is a starting guess: tune it until one press throws once and the
     * arm stops ready for the next throw.
     */
    public static class Catapult {
        public static double power = 0.65;                          // motor power: AndyMark's code
        public static double feedSec = 0.8;                         // seconds per press: a starting guess
    }

    public static class Auto {
        public static double leaveInches = 20;                      // drive off the start line
        public static double leavePower = 0.5;                      // power for that move
        public static double secondsBetweenThrows = 0.5;            // wait between two throws
    }
}
