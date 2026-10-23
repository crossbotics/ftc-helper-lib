package com.crossbotics.ftchelper.examples.flywheelbot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE

/**
 * Example robot 1, file 2 of 5: every number, one per line. Section name = the part's name in
 * CamelCase ("intake_deploy" reads IntakeDeploy). DEFAULT = the library's default (in brackets).
 * Tune live, then copy the numbers back here: the code is the record.
 * more: https://crossbotics.com/ftc-helper/docs/guides/tunables/
 */
public class Tunables {
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
        public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
    }

    /** The flywheel, in RPM. kP..kF: DEFAULT until the Flywheel Finder; then write its numbers here. */
    public static class Launcher {
        public static double speedRpm = 2600;                       // shot speed (example: watch shots and tune it)
        public static double stepRpm = DEFAULT;                     // speed change per D-pad press (100)
        public static double readyBandRpm = DEFAULT;                // "at speed" = this close to the target (60)
        public static double idleRpm = DEFAULT;                     // speed between shots, 0 = stop (0)
        public static double kP = DEFAULT;                          // push per speed error (kF / 10)
        public static double kI = DEFAULT;                          // fix a steady error (kP / 10)
        public static double kD = DEFAULT;                          // brake changes (0)
        public static double kF = DEFAULT;                          // steady power for a speed (from the motor)
        public static double shotIntervalSec = DEFAULT;             // least time between two pieces, s (0.25)
    }

    public static class Indexer {
        public static double rest = 0.15;                           // servo position, out of the way (Servo Finder)
        public static double push = 0.55;                           // servo position, piece pushed (Servo Finder)
        public static double pushSec = 0.25;                        // time to push, seconds
        public static double returnSec = 0.25;                      // time to come back, seconds
    }

    public static class IntakeDeploy {
        public static double stowed = 0.0;                          // servo position, folded (Servo Finder)
        public static double deployed = 0.8;                        // servo position, folded out (Servo Finder)
        public static double travelSec = 1.0;                       // time for a full move, seconds (timed)
    }

    /** Whole-robot aim (G1 RB). more: https://crossbotics.com/ftc-helper/docs/guides/tunables/#aim */
    public static class Aim {
        public static double turnKp = DEFAULT;                      // turn power per degree off (0.02)
        public static double turnKd = DEFAULT;                      // brake the turn near the goal (0.002)
        public static double maxTurnPower = DEFAULT;                // the most turn power (0.5)
        public static double toleranceDeg = DEFAULT;                // "aimed" within this, degrees (2)
        public static double cameraOffset = DEFAULT;                // aims a little to one side: Aim Tuner (0)
        public static double rumbleDeg = DEFAULT;                   // gamepad 2 buzzes this close, degrees (4)
    }

    /** The autonomous's distances. Also in the tuning menu: tune, then copy them back here. */
    public static class Auto {
        public static double leaveInches = 24;                      // drive off the start line
        public static double leavePower = 0.6;                      // power for that move
    }
}
