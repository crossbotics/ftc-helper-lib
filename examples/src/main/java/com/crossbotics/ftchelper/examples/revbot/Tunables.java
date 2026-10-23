package com.crossbotics.ftchelper.examples.revbot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE (docs/guides/tunables.md)

/**
 * REV starter bot, file 2 of 5: the numbers, one per line. REV's pages do not give the flywheel's speed
 * or the poker's positions, so those numbers are starting guesses: find the real ones on the robot with
 * the finders (docs/guides/finders.md). DEFAULT = the library's default (in brackets in the
 * comment). If you tune a number live on the robot, copy the new value back into this file, or it is
 * lost at the next build.
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

    /**
     * The flywheel. REV's code spins it "to the target velocity", but the number is not on the page:
     * 3000 RPM is a guess. Find yours with the Flywheel Finder (kF and kP) and by watching shots.
     */
    public static class Flywheel {
        public static double speedRpm = 3000;                       // shot speed: a guess, watch shots and tune it
        public static double stepRpm = DEFAULT;                     // speed change per D-pad press (100)
        public static double readyBandRpm = 100;                    // "at speed" = this close to the target
        public static double idleRpm = DEFAULT;                     // speed between shots, 0 = stop (0)
        // The hub's speed control: the defaults for the motor until the Flywheel Finder (INIT shows them).
        // Then write its numbers in their place.
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

    /**
     * The poker. REV's code has two preset spots (A = down, B = up); the numbers are not on the page.
     * up = folded along the side (the start); down = out over a FLOWER. Both are guesses: find these
     * with the Servo Finder BEFORE the first TeleOp, so the poker does not swing into the frame.
     */
    public static class PollenServo {
        public static double up = 0.0;                              // servo position, folded along the side (a guess)
        public static double down = 0.5;                            // servo position, out over a FLOWER (a guess)
    }

    public static class Auto {
        public static double leaveInches = 20;                      // drive off the start line
        public static double leavePower = 0.5;                      // power for that move
    }
}
