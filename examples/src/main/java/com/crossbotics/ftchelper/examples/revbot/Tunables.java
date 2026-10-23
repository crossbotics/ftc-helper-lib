package com.crossbotics.ftchelper.examples.revbot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE (docs/guides/tunables.md)

/**
 * REV starter bot, file 2 of 5: the numbers, one per line. DEFAULT = the library's default (in
 * brackets). The flywheel speed and poker spots are guesses: measure them with the finders.
 * Tuned a number live? Copy it back here, or it is lost at the next build.
 * more: https://crossbotics.com/ftc-helper/docs/guides/finders/
 */
public class Tunables {
    /** calibrated: set true after the Drive Tuner (autonomous refuses to run before). */
    public static class Drive {
        public static double maxPower = DEFAULT;                    // top wheel power (1.0)
        public static double practiceMaxPower = DEFAULT;            // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
        public static double strafeCorrection = DEFAULT;            // not used: a tank drive cannot strafe (1.15)
        public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }

    public static class IntakeMotor {
        public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = 1.0;                        // power pushing pieces out: full, like REV's code
    }

    /** The flywheel. REV does not publish its speed: find kF and kP with the Flywheel Finder. */
    public static class Flywheel {
        public static double speedRpm = 3000;                       // shot speed: a guess, watch shots and tune it
        public static double stepRpm = DEFAULT;                     // speed change per D-pad press (100)
        public static double readyBandRpm = 100;                    // "at speed" = this close to the target
        public static double idleRpm = DEFAULT;                     // speed between shots, 0 = stop (0)
        // The hub's speed control: the motor's defaults until the Flywheel Finder (INIT shows them).
        public static double kP = DEFAULT;                          // push per speed error (kF / 10)
        public static double kI = DEFAULT;                          // fix a steady error (kP / 10)
        public static double kD = DEFAULT;                          // brake changes (0)
        public static double kF = DEFAULT;                          // steady power for a speed (from the motor)
    }

    /** The feeder roller (CR servo): full power for feedSec per piece. 0.4 s is a guess: watch it and tune it. */
    public static class CrServo {
        public static double power = DEFAULT;                       // feeder power, 0 to 1 (1.0)
        public static double feedSec = DEFAULT;                     // seconds per piece (0.4)
    }

    /** The poker. Both spots are guesses: measure them with the Servo Finder BEFORE the first TeleOp. */
    public static class PollenServo {
        public static double up = 0.0;                              // servo position, folded along the side (a guess)
        public static double down = 0.5;                            // servo position, out over a FLOWER (a guess)
    }

    public static class Auto {
        public static double leaveInches = 20;                      // drive off the start line
        public static double leavePower = 0.5;                      // power for that move
    }
}
