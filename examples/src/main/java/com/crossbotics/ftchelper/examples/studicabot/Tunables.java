package com.crossbotics.ftchelper.examples.studicabot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT_FROM_CATALOG and MEASURE (docs/guides/tunables.md)

/**
 * Studica starter bot, file 2 of 5: the numbers, one per line. DEFAULT_FROM_CATALOG = the library's
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

    public static class Intake {
        public static double inPower = DEFAULT_FROM_CATALOG;        // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = 1.0;                        // power pushing pieces out: Studica's full power
    }

    /**
     * The flywheel. Studica's code runs it at the right trigger's power, with no set speed. The number
     * here is the MOTOR's speed as the library counts it (see Robot.java): the motor's real top speed
     * shows as about 5200, so 4000 is a starting guess with room to speed up. Tune it by watching shots.
     */
    public static class FlyWheel {
        public static double speedRpm = 4000;                       // shot speed (motor RPM): a guess, watch shots
        public static double stepRpm = DEFAULT_FROM_CATALOG;        // speed change per D-pad press (100)
        public static double readyBandRpm = 150;                    // "at speed" = this close to the target
        public static double idleRpm = DEFAULT_FROM_CATALOG;        // speed between shots, 0 = stop (0)
        // The hub's speed control: the defaults for the motor until the Flywheel Finder (INIT shows them).
        // Then write its numbers in their place.
        public static double kP = DEFAULT_FROM_CATALOG;             // push per speed error (kF / 10)
        public static double kI = DEFAULT_FROM_CATALOG;             // fix a steady error (kP / 10)
        public static double kD = DEFAULT_FROM_CATALOG;             // brake changes (0)
        public static double kF = DEFAULT_FROM_CATALOG;             // steady power for a speed (from the motor)
    }

    /**
     * The flap: rest = 0 and push = 0.2, from Studica's code (X and A). The flap goes to rest at START,
     * and your servo's horn may sit differently from Studica's: find these with the Servo Finder BEFORE
     * the first TeleOp. The times are guesses.
     */
    public static class FwActuator {
        public static double rest = 0.0;                            // servo position, out of the way: Studica (X)
        public static double push = 0.2;                            // servo position, piece pushed: Studica (A)
        public static double pushSec = 0.3;                         // time to push, seconds: a guess
        public static double returnSec = 0.3;                       // time to come back, seconds: a guess
    }

    public static class Auto {
        public static double leaveInches = 20;                      // drive off the start line
        public static double leavePower = 0.5;                      // power for that move
    }
}
