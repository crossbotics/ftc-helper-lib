package com.crossbotics.ftchelper.examples.studicabot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE (docs/guides/tunables.md)

/**
 * Studica starter bot, file 2 of 5: the numbers, one per line. DEFAULT = the library's
 * default (in brackets in the comment). If you tune a number live on the robot, copy the new value back
 * into this file, or it is lost at the next build.
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

    public static class Intake {
        public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = 1.0;                        // power pushing pieces out: Studica's full power
    }

    /** The flywheel. Speeds are the MOTOR's (top 6100 RPM; the wheels turn at half). Tune by watching shots. */
    public static class FlyWheel {
        public static double speedRpm = 4000;                       // shot speed (motor RPM): a guess, watch shots
        public static double stepRpm = DEFAULT;                     // speed change per D-pad press (100)
        public static double readyBandRpm = 150;                    // "at speed" = this close to the target
        public static double idleRpm = DEFAULT;                     // speed between shots, 0 = stop (0)
        // The hub's speed control: the motor's defaults until the Flywheel Finder (INIT shows them).
        public static double kP = DEFAULT;                          // push per speed error (kF / 10)
        public static double kI = DEFAULT;                          // fix a steady error (kP / 10)
        public static double kD = DEFAULT;                          // brake changes (0)
        public static double kF = DEFAULT;                          // steady power for a speed (from the motor)
    }

    /** The flap. It goes to rest at START: measure rest and push with the Servo Finder BEFORE the first TeleOp. */
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
