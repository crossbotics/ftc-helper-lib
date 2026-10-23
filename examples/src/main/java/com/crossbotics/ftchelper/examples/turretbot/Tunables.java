package com.crossbotics.ftchelper.examples.turretbot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE (docs/guides/tunables.md)

/**
 * Example robot 2, file 2 of 5: every number, one section per part, one number per line (see example
 * robot 1's Tunables for how tuning works). DEFAULT = the library's default (in brackets).
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
     * Limelight pipelines 0, 1 and 2 find AprilTags, each tuned for a kind of light (Robot.java's
     * autoPipelines tries them in INIT). autoLighting = false skips that test and uses pipeline.
     */
    public static class Limelight {
        public static double pipeline = DEFAULT;                    // the pipeline when the test is off (0)
        public static boolean autoLighting = true;                  // test the pipelines at INIT, keep the best
    }

    public static class Intake {
        public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
    }

    /**
     * The turret's range in degrees (0 = straight forward, counterclockwise +): it never turns past
     * these, even when the goal is further round. cameraOffset: the camera's bearing to the goal when
     * the launcher is aimed at it (0 if the camera points the same way as the launcher).
     */
    public static class Turret {
        public static double min = -90;                             // degrees: as far right as the wires allow
        public static double max = 90;                              // degrees: as far left as the wires allow
        public static double kP = DEFAULT;                          // push per degree off (0.02)
        public static double kI = DEFAULT;                          // fix a small steady miss (0)
        public static double kD = DEFAULT;                          // brake near the target (0.001)
        public static double maxPower = DEFAULT;                    // the most power (0.8)
        public static double tolerance = 1.5;                       // "aimed" within this, degrees (example)
        public static double cameraOffset = DEFAULT;                // aims a little to one side: Aim Tuner (0)
        // Slows down within slowZone degrees of a limit, to slowZonePower; a goal (from the odometry) more
        // than outOfRangeHold degrees past a limit: it holds where it points (the library's numbers).
        public static double slowZone = DEFAULT;                    // slow down this close to a limit, degrees (10)
        public static double slowZonePower = DEFAULT;               // power at the very limit (0.25)
        public static double outOfRangeHold = DEFAULT;              // goal this far past a limit: hold (10)
        // No home switch: set the turret straight forward by hand before INIT (startAngle 0). TeleOp keeps
        // the angle the last program ended at (keepAngle; the library's numbers).
        public static double startAngle = 0;                        // degrees: where you set it by hand before INIT
        public static boolean keepAngle = true;                     // TeleOp goes on from the last program's angle
    }

    /**
     * Distance to the goal (inches), flywheel speed (RPM) and hood angle (degrees, see Hood below) that
     * score from there, measured on the field (the Shot Tuner helps; it tunes one table at a time).
     * Between rows the speed and the hood are blended. One table per game piece, because they fly
     * differently: POLLEN is a 2.8 in ball, NECTAR a 3.6 in ball (game manual, section 9.8). Example
     * numbers: measure your own. speedRpm: the speed until the goal is first seen.
     */
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
        public static double[][] speedByDistancePollen = {{24, 2200, 2}, {48, 2550, 11}, {72, 2900, 19}, {96, 3250, 23}};
        // The same for NECTAR. Example rows: write the Shot Tuner's instead.
        public static double[][] speedByDistanceNectar = {{24, 2400, 0}, {48, 2800, 6}, {72, 3200, 14}, {96, 3600, 18}};
    }

    /**
     * The hood (team 8938's turret v8.1GB): 0 deg = its steep end, 25 deg = its flat end, between two stop
     * cams. A goBILDA Torque servo (300 deg mode) turns it through a 20T pinion and a 108T gear (5.4:1),
     * so the 25 deg take 135 deg of the servo: about 0.45 of its 0 to 1 range. posA, posB: the servo
     * positions a hair off each stop (Hood Finder: example numbers here). travelSec: stop to stop
     * (the servo's 0.2 s per 60 deg x 5.4 x 25 deg = 0.45 s). startAngle 0: parked at the steep end.
     */
    public static class Hood {
        public static double posA = 0.20;                           // servo position at the steep stop (Hood Finder)
        public static double posB = 0.65;                           // servo position at the flat stop (Hood Finder)
        public static double angleA = 0;                            // hood angle at posA, degrees: the steep end
        public static double angleB = 25;                           // hood angle at posB, degrees: the flat end
        public static double travelSec = 0.45;                      // stop to stop, seconds (worked out above)
        public static double startAngle = 0;                        // where it goes at START: the steep end
    }

    /**
     * Aiming. rumbleDeg: gamepad 2 buzzes when the turret is this close to the goal (Controls.java's
     * rumbleWhenAimed). With odometry you would add goalX and goalY here: then the turret and the
     * speed keep working when the camera loses the goal (lesson 9). mirrorForRed = false: BIOBUZZ's field
     * is turned half a turn, so RED uses the same numbers from the red wall (the robot's one setting:
     * the autonomous and the aim use it).
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
