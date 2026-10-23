package com.crossbotics.ftchelper.examples.v2bot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE

/**
 * The V2 robot, file 2 of 5: every number, one per line. "example" = not measured on a built robot:
 * measure your own. DEFAULT = the library's default (in brackets).
 * more: https://crossbotics.com/ftc-helper/docs/guides/interlocks/#where-the-v2-robots-numbers-come-from
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

    public static class Odometry {
        public static boolean checked = false;                      // true after the Odometry Tuner's loop test
    }

    /** Limelight pipelines 0, 1 and 2 find AprilTags, each tuned for a kind of light. */
    public static class Limelight {
        public static double pipeline = DEFAULT;                    // the pipeline when the test is off (0)
        public static boolean autoLighting = true;                  // test the pipelines at INIT, keep the best
    }

    public static class Intake {
        public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
    }

    /** Team 8938's turret v8.1GB. Degrees: 0 = straight forward, counterclockwise +. */
    public static class Turret {
        public static double min = -190;                            // degrees: as far as the wires allow (v8.1GB)
        public static double max = 190;                             // degrees: as far as the wires allow (v8.1GB)
        public static double homeAngle = 0;                         // angle at the home switch (v8.1GB HOME_ANG)
        public static double homingPower = DEFAULT;                 // power while it looks for the switch (0.2)
        public static double homingTimeout = DEFAULT;               // stop with a fault after this, s (5)
        public static double kP = DEFAULT;                          // push per degree off (0.02): tune your own
        public static double kI = DEFAULT;                          // fix a small steady miss (0): tune your own
        public static double kD = DEFAULT;                          // brake near the target (0.001): tune your own
        public static double maxPower = DEFAULT;                    // the most power (0.8)
        public static double tolerance = 1.5;                       // "aimed" within this, degrees (example)
        public static double cameraOffset = DEFAULT;                // aims a little to one side: Aim Tuner (0)
    }

    /** The flywheel, in motor RPM. One shot table per game piece: measure your own (Shot Tuner). */
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
        // {inches to the goal, RPM, hood degrees} for POLLEN, blended between rows. Example: Shot Tuner.
        public static double[][] speedByDistancePollen = {{24, 2200, 2}, {48, 2550, 11}, {72, 2900, 19}, {96, 3250, 23}};
        // The same for NECTAR. Example rows: write the Shot Tuner's instead.
        public static double[][] speedByDistanceNectar = {{24, 2400, 0}, {48, 2800, 6}, {72, 3200, 14}, {96, 3600, 18}};
    }

    /**
     * The hood (turret v8.1GB): 25 degrees between two stops.
     * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/09-shooter-and-camera/#a-hood-the-shot-angle
     */
    public static class Hood {
        public static double posA = 0.20;                           // servo position at the steep stop (example)
        public static double posB = 0.65;                           // servo position at the flat stop (example)
        public static double angleA = 0;                            // hood angle at posA, degrees: the steep end
        public static double angleB = 25;                           // hood angle at posB, degrees: the flat end
        public static double travelSec = 0.45;                      // stop to stop, seconds (worked out from the gears)
        public static double startAngle = 0;                        // where it goes at START: the steep end
    }

    /**
     * Aiming. The goal: our HIVE's CELL that is up at the start.
     * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/09-shooter-and-camera/#9-when-the-camera-loses-the-goal-the-odometry
     */
    public static class Aim {
        public static double goalX = 13;                            // the goal: inches right of the centre line
        public static double goalY = -13;                           // the goal: inches from the centre, - = our half
        public static boolean mirrorForRed = false;                 // BIOBUZZ: false
        public static boolean shootOnMove = false;                  // off until tested on the real robot
        public static double shooterForwardIn = 1;                  // turret axis, inches forward (v8.1GB)
        public static double shooterLeftIn = 0;                     // turret axis, inches left (v8.1GB)
        public static double rumbleDeg = DEFAULT;                   // gamepad 2 buzzes this close, degrees (4)
    }

    public static class Indexer {
        public static double rest = 0.15;                           // servo position, out of the way (example)
        public static double push = 0.55;                           // servo position, piece pushed (example)
        public static double pushSec = DEFAULT;                     // time to push, seconds (0.25)
        public static double returnSec = DEFAULT;                   // time to come back, seconds (0.25)
    }

    /** Encoder counts from the bottom stop (start with the slide down): 3.2 counts per mm. */
    public static class Lift {
        public static double min = 0;                               // counts: the bottom stop
        public static double max = 1350;                            // counts: just under the hard stop (1362)
        public static double kP = DEFAULT;                          // push toward the height (from the motor)
        public static double kI = DEFAULT;                          // fix a small steady miss (0)
        public static double kD = DEFAULT;                          // brake near the height (from the motor)
        public static double kF = DEFAULT;                          // hold up against gravity (0.05)
        public static double maxPower = DEFAULT;                    // the most power (1.0)
        public static double slowZone = 120;                        // slow down this close to an end, counts (example)
        public static double slowZonePower = 0.3;                   // power at the very end (example)
        public static double tolerance = 15;                        // "arrived" within this, counts (example)
        public static double pickup = 0;                            // a named height, counts: slide down
        public static double stowed = 44;                           // a named height: the folded claw clears the tiles
        public static double flower = 1330;                         // a named height: the ball over the FLOWER
    }

    /** Servo positions: 0 to 1 = 270 degrees, so each angle = folded + degrees / 270. */
    public static class Wrist {
        public static double folded = 0.10;                         // servo position, 0 deg (example: Servo Finder)
        public static double pickup = 0.174;                        // servo position, 20 deg (folded + 20 / 270)
        public static double carry = 0.433;                         // servo position, 90 deg (folded + 90 / 270)
        public static double flower = 0.555;                        // servo position, 122.8 deg (folded + 122.8 / 270)
        public static double travelSec = 0.3;                       // time for a full move, seconds (worked out)
    }

    /** The claw (the "pincer"): two fingers geared to one servo. */
    public static class Claw {
        public static double open = 0.55;                           // servo position, open (example: Servo Finder)
        public static double closed = 0.47;                         // servo position, closed on a POLLEN (example)
        public static double travelSec = 0.15;                      // time for a full move, seconds
    }

    /**
     * THE INTERLOCK RULE (Robot.java: keepApart). Read once at INIT, not live. Change folded? Change
     * wristTo too (folded + 0.174).
     * more: https://crossbotics.com/ftc-helper/docs/guides/interlocks/#the-turretclaw-rule
     */
    public static class TurretClaw {
        public static double turretFrom = 147;                      // degrees: the turret's zone starts here
        public static double turretTo = 217;                        // degrees: the turret's zone ends here
        public static double wristFrom = 0;                         // servo position: the wrist's zone starts at 0
        public static double wristTo = 0.274;                       // servo position: 47 degrees (folded + 0.174)
    }
}
