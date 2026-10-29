package com.crossbotics.ftchelper.examples.turretbot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE

/**
 * Example robot 2, file 2 of 5: every number, one per line. DEFAULT = the library's default (in
 * brackets). Tune live, then copy the numbers back here: the code is the record.
 * more: https://crossbotics.com/ftc-helper/docs/guides/tunables/
 */
public class Tunables {
    public static class Drive {
        public static double maxPower = DEFAULT;                    // top wheel power (1.0)
        public static double practiceMaxPower = DEFAULT;            // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
        public static double strafeCorrection = DEFAULT;            // sideways moves: Drive Tuner (1.15)
        public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }

    /**
     * Venue lighting.
     * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/09-shooter-and-camera/#5-a-camera-that-knows-the-goal
     */
    public static class Limelight {
        public static double pipeline = DEFAULT;                    // the pipeline when the test is off (0)
        public static boolean autoLighting = true;                  // test the pipelines at INIT, keep the best
    }

    public static class Intake {
        public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
    }

    /** Degrees: 0 = straight forward, counterclockwise +. It never turns past min and max. */
    public static class Turret {
        public static double min = -90;                             // degrees: as far right as the wires allow
        public static double max = 90;                              // degrees: as far left as the wires allow
        public static double kP = DEFAULT;                          // push per degree off (0.02)
        public static double kI = DEFAULT;                          // fix a small steady miss (0)
        public static double kD = DEFAULT;                          // brake near the target (0.001)
        public static double maxPower = DEFAULT;                    // the most power (0.8)
        public static double tolerance = 1.5;                       // "aimed" within this, degrees (example)
        public static double cameraOffset = DEFAULT;                // aims a little to one side: Aim Tuner (0)
        // Near a limit (the library's numbers):
        public static double slowZone = DEFAULT;                    // slow down this close to a limit, degrees (10)
        public static double slowZonePower = DEFAULT;               // power at the very limit (0.25)
        public static double outOfRangeHold = DEFAULT;              // goal this far past a limit: hold (10)
        // No home switch: turn it straight forward by hand before INIT.
        public static double startAngle = 0;                        // degrees: where you set it by hand before INIT
        public static boolean keepAngle = true;                     // TeleOp goes on from the last program's angle
    }

    /** The flywheel, in RPM. One shot table per game piece (they fly differently): Shot Tuner. */
    public static class Launcher {
        public static double speedRpm = 2600;                       // speed until the goal is first seen (example)
        public static double stepRpm = DEFAULT;                     // speed change per D-pad press (100)
        public static double readyBandRpm = DEFAULT;                // "at speed" = this close to the target (60)
        public static double idleRpm = 1500;                        // spins between shots: quicker first shot
        // The hub's speed control: the defaults for this motor until the Flywheel Finder (INIT shows them).
        public static double kP = DEFAULT;                          // push per speed error (kF / 10)
        public static double kI = DEFAULT;                          // fix a steady error (kP / 10)
        public static double kD = DEFAULT;                          // brake changes (0)
        public static double kF = DEFAULT;                          // steady power for a speed (from the motor)
        public static double shotIntervalSec = DEFAULT;             // least time between two pieces, s (0.25)
        // {inches to the goal, RPM, hood degrees} for POLLEN, blended between rows. Example: Shot Tuner.
        // These go in cleanly in the simulator (48 and 60 in measured there). Use your own robot's numbers.
        public static double[][] speedByDistancePollen = {{36, 2010, 5}, {48, 2290, 5}, {60, 2560, 5}, {72, 2830, 5}};
        // The same for NECTAR. Example rows: write the Shot Tuner's instead.
        public static double[][] speedByDistanceNectar = {{24, 2400, 0}, {48, 2800, 6}, {72, 3200, 14}, {96, 3600, 18}};
    }

    /**
     * The hood: 25 degrees between two stops.
     * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/09-shooter-and-camera/#a-hood-the-shot-angle
     */
    public static class Hood {
        public static double posA = 0.20;                           // servo position at the steep stop (Hood Finder)
        public static double posB = 0.65;                           // servo position at the flat stop (Hood Finder)
        public static double angleA = 0;                            // hood angle at posA, degrees: the steep end
        public static double angleB = 25;                           // hood angle at posB, degrees: the flat end
        public static double travelSec = 0.45;                      // stop to stop, seconds (worked out from the gears)
        public static double startAngle = 0;                        // where it goes at START: the steep end
    }

    /**
     * Aiming. With odometry, add goalX and goalY:
     * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/09-shooter-and-camera/#9-when-the-camera-loses-the-goal-the-odometry
     */
    public static class Aim {
        public static double rumbleDeg = DEFAULT;                   // gamepad 2 buzzes this close, degrees (4)
        public static boolean mirrorForRed = false;                 // BIOBUZZ: false
    }

    public static class Indexer {
        public static double rest = 0.15;                           // servo position, out of the way (Servo Finder)
        public static double push = 0.55;                           // servo position, piece pushed (Servo Finder)
        public static double pushSec = DEFAULT;                     // time to push, seconds (0.25)
        public static double returnSec = DEFAULT;                   // time to come back, seconds (0.25)
    }

    /** The claw's fold-out pivot: tucked in, down to grab, and up over a FLOWER. */
    public static class ClawFold {
        public static double stowed = 0.05;                         // servo position, tucked in (Servo Finder)
        public static double grab = 0.62;                           // servo position, down to grab (Servo Finder)
        public static double flower = 0.40;                         // servo position, up over a FLOWER (Servo Finder)
        public static double travelSec = 0.8;                       // about how long a fold takes, seconds
    }

    public static class Claw {
        public static double open = 0.20;                           // servo position, open (Servo Finder)
        public static double closed = 0.75;                         // servo position, closed (Servo Finder)
        public static double travelSec = 0.6;                       // time for a full move, seconds
    }
}
