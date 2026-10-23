package com.crossbotics.ftchelper.examples.v2bot;

import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT_FROM_CATALOG and MEASURE (docs/guides/tunables.md)

/**
 * The V2 robot, file 2 of 5: every number, one section per part, one number per line. Numbers marked
 * "example" are not measured on a built robot: measure your own (the Finders and the tuning menu help;
 * docs/guides/finders.md). Where a number comes from team 8938's designs, the comment says which.
 * DEFAULT_FROM_CATALOG = the library's default (in brackets in the comment).
 */
public class Tunables {
    public static class Drive {
        public static double maxPower = DEFAULT_FROM_CATALOG;       // top wheel power (1.0)
        public static double practiceMaxPower = DEFAULT_FROM_CATALOG; // top wheel power in PRACTICE (0.6)
        public static double slowSpeed = DEFAULT_FROM_CATALOG;      // top power with the slow trigger held (0.3)
        public static double strafeCorrection = DEFAULT_FROM_CATALOG; // sideways moves: Drive Tuner (1.15)
        public static double turnKp = DEFAULT_FROM_CATALOG;         // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT_FROM_CATALOG;         // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT_FROM_CATALOG;   // auto: least power that moves it (0.30)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }

    public static class Odometry {
        public static boolean checked = false;                      // true after the Odometry Tuner's loop test
    }

    /** Limelight pipelines 0, 1 and 2 find AprilTags, each tuned for a kind of light. */
    public static class Limelight {
        public static double pipeline = DEFAULT_FROM_CATALOG;       // the pipeline when the test is off (0)
        public static boolean autoLighting = true;                  // test the pipelines at INIT, keep the best
    }

    public static class Intake {
        public static double inPower = DEFAULT_FROM_CATALOG;        // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = DEFAULT_FROM_CATALOG;       // power pushing pieces out (0.7)
    }

    /**
     * The turret (team 8938's turret v8.1GB). Angles in degrees: 0 = straight forward,
     * counterclockwise +. Its range is -190 to +190: a bit more than one turn, so it can point
     * anywhere and picks the way round that stays inside the range (the wires allow it: v8.1GB's
     * checks turn it +/-190).
     * homeAngle: the turret's angle where the magnetic switch closes. In v8.1GB the switch (REV-31-1462)
     * sits at -45 deg on the bearing housing and its magnet at -45 deg on the turret, so they line up
     * when the turret points straight forward: 0 (turret/v8.1GB/turret.py HOME_ANG). Measure your own
     * (turret-setup.md says how).
     * homingPower, homingTimeout: at START it turns this slowly, for at most this many seconds.
     */
    public static class Turret {
        public static double min = -190;                            // degrees: as far as the wires allow (v8.1GB)
        public static double max = 190;                             // degrees: as far as the wires allow (v8.1GB)
        public static double homeAngle = 0;                         // angle at the home switch (v8.1GB HOME_ANG)
        public static double homingPower = DEFAULT_FROM_CATALOG;    // power while it looks for the switch (0.2)
        public static double homingTimeout = DEFAULT_FROM_CATALOG;  // stop with a fault after this, s (5)
        public static double kP = DEFAULT_FROM_CATALOG;             // push per degree off (0.02): tune your own
        public static double kI = DEFAULT_FROM_CATALOG;             // fix a small steady miss (0): tune your own
        public static double kD = DEFAULT_FROM_CATALOG;             // brake near the target (0.001): tune your own
        public static double maxPower = DEFAULT_FROM_CATALOG;       // the most power (0.8)
        public static double tolerance = 1.5;                       // "aimed" within this, degrees (example)
        public static double cameraOffset = DEFAULT_FROM_CATALOG;   // aims a little to one side: Aim Tuner (0)
    }

    /**
     * Distance to the goal (inches), flywheel motor speed (RPM) and hood angle (degrees) that score
     * from there. Example numbers: measure your own with the Shot Tuner. One table per game piece.
     */
    public static class Launcher {
        public static double speedRpm = 2600;                       // speed until the goal is first seen (example)
        public static double stepRpm = DEFAULT_FROM_CATALOG;        // speed change per D-pad press (100)
        public static double readyBandRpm = DEFAULT_FROM_CATALOG;   // "at speed" = this close to the target (60)
        public static double idleRpm = 1500;                        // spins between shots: quicker first shot
        // The hub's speed control: the defaults for this motor until the Flywheel Finder (INIT shows them).
        public static double kP = DEFAULT_FROM_CATALOG;             // push per speed error (kF / 10)
        public static double kI = DEFAULT_FROM_CATALOG;             // fix a steady error (kP / 10)
        public static double kD = DEFAULT_FROM_CATALOG;             // brake changes (0)
        public static double kF = DEFAULT_FROM_CATALOG;             // steady power for a speed (from the motor)
        // {inches to the goal, RPM, hood degrees} for POLLEN, blended between rows. Example: Shot Tuner.
        public static double[][] speedByDistancePollen = {{24, 2200, 2}, {48, 2550, 11}, {72, 2900, 19}, {96, 3250, 23}};
        // The same for NECTAR. Example rows: write the Shot Tuner's instead.
        public static double[][] speedByDistanceNectar = {{24, 2400, 0}, {48, 2800, 6}, {72, 3200, 14}, {96, 3600, 18}};
    }

    /**
     * The hood (turret v8.1GB): 0 deg = its steep end, 25 deg = its flat end, between two stop cams.
     * The Torque servo turns it through 20T and 108T gears (5.4:1). posA, posB: example, the Hood
     * Finder finds yours. travelSec: 0.2 s per 60 deg x 5.4 x 25 deg = 0.45 s.
     */
    public static class Hood {
        public static double posA = 0.20;                           // servo position at the steep stop (example)
        public static double posB = 0.65;                           // servo position at the flat stop (example)
        public static double angleA = 0;                            // hood angle at posA, degrees: the steep end
        public static double angleB = 25;                           // hood angle at posB, degrees: the flat end
        public static double travelSec = 0.45;                      // stop to stop, seconds (worked out above)
        public static double startAngle = 0;                        // where it goes at START: the steep end
    }

    /**
     * Aiming. goalX, goalY: the goal on the field, inches, seen from our drivers' wall: our HIVE's CELL that
     * is up at the start, 13 in right of the centre line and 13 in from the centre toward our wall
     * (sim/fields/biobuzz-2026.json; it is the other CELL after the HIVE tips). mirrorForRed = false:
     * BIOBUZZ's field is turned half a turn, so RED uses the same numbers from the red wall.
     * shooterForwardIn: the turret's axis is 1 in forward of the robot's centre (turret v8.1GB), which
     * matters when shooting on the move (off until tested on the real robot).
     */
    public static class Aim {
        public static double goalX = 13;                            // the goal: inches right of the centre line
        public static double goalY = -13;                           // the goal: inches from the centre, - = our half
        public static boolean mirrorForRed = false;                 // BIOBUZZ: false
        public static boolean shootOnMove = false;                  // off until tested on the real robot
        public static double shooterForwardIn = 1;                  // turret axis, inches forward (v8.1GB)
        public static double shooterLeftIn = 0;                     // turret axis, inches left (v8.1GB)
        public static double rumbleDeg = DEFAULT_FROM_CATALOG;      // gamepad 2 buzzes this close, degrees (4)
    }

    public static class Indexer {
        public static double rest = 0.15;                           // servo position, out of the way (example)
        public static double push = 0.55;                           // servo position, piece pushed (example)
        public static double pushSec = DEFAULT_FROM_CATALOG;        // time to push, seconds (0.25)
        public static double returnSec = DEFAULT_FROM_CATALOG;      // time to come back, seconds (0.25)
    }

    /**
     * The lift, in encoder counts from the bottom stop (0 = where it is at INIT: start with the slide
     * down). The Viper-Slide's top stage moves 120 mm per motor turn and the 435 RPM motor counts
     * 384.5 per turn, so 3.2 counts per mm (lifter design log).
     * - pickup 0: slide down; with the wrist at 20 deg the claw reaches a ball on the tiles.
     * - stowed 44 (13.7 mm): the folded claw clears the tiles.
     * - flower 1330 (415.1 mm): the ball over the FLOWER's top opening (wrist at 122.8 deg).
     * - max 1350: just under the hard stop at 425.1 mm (1362). min 0: the bottom stop.
     * kP...tolerance: example, tune your own (tutorial 07).
     */
    public static class Lift {
        public static double min = 0;                               // counts: the bottom stop
        public static double max = 1350;                            // counts: just under the hard stop (1362)
        public static double kP = DEFAULT_FROM_CATALOG;             // push toward the height (from the motor)
        public static double kI = DEFAULT_FROM_CATALOG;             // fix a small steady miss (0)
        public static double kD = DEFAULT_FROM_CATALOG;             // brake near the height (from the motor)
        public static double kF = DEFAULT_FROM_CATALOG;             // hold up against gravity (0.05)
        public static double maxPower = DEFAULT_FROM_CATALOG;       // the most power (1.0)
        public static double slowZone = 120;                        // slow down this close to an end, counts (example)
        public static double slowZonePower = 0.3;                   // power at the very end (example)
        public static double tolerance = 15;                        // "arrived" within this, counts (example)
        public static double pickup = 0;                            // a named height, counts: slide down
        public static double stowed = 44;                           // a named height: the folded claw clears the tiles
        public static double flower = 1330;                         // a named height: the ball over the FLOWER
    }

    /**
     * The wrist: servo positions (0 to 1). The goBILDA 2000 servo turns 0.15 degrees per microsecond of
     * signal (goBILDA's page: 300 degrees over 500-2500 us). The control hub's normal signal is
     * 600-2400 us, so positions 0 to 1 turn it 270 degrees: one degree is 1/270. That is plenty: the
     * wrist only needs 145 degrees (its hard stop), so it keeps the normal range (no .fullRange()).
     * Angles from straight down (lifter design): folded 0, pickup 20 (the claw a little out, so the
     * ball is not under the robot), carry 90 (swing out before lifting), flower 122.8. Its hard stop
     * is at 145 (0.637). folded = 0.10 is an example: find yours with the Servo Finder, then add the
     * degrees / 270. travelSec: 0.09 s per 60 deg x 145 deg = 0.22 s, plus a bit.
     */
    public static class Wrist {
        public static double folded = 0.10;                         // servo position, 0 deg (example: Servo Finder)
        public static double pickup = 0.174;                        // servo position, 20 deg (folded + 20 / 270)
        public static double carry = 0.433;                         // servo position, 90 deg (folded + 90 / 270)
        public static double flower = 0.555;                        // servo position, 122.8 deg (folded + 122.8 / 270)
        public static double travelSec = 0.3;                       // time for a full move, seconds (worked out above)
    }

    /**
     * The claw (the "pincer"): the fingers turn 1.2 times the servo's angle. Open: fingers at +17 deg
     * (servo +14 deg); closed: on a POLLEN, -10 deg (servo -8 deg); a NECTAR stops them at 0. Example
     * positions around 0.50: find yours with the Servo Finder.
     */
    public static class Claw {
        public static double open = 0.55;                           // servo position, open (example: Servo Finder)
        public static double closed = 0.47;                         // servo position, closed on a POLLEN (example)
        public static double travelSec = 0.15;                      // time for a full move, seconds
    }

    /**
     * THE INTERLOCK RULE (Robot.java: keepApart("TurretClaw", ...)). Team 8938's pincer model run
     * (2026-10-22) against turret v8.1GB: no lifter part ever comes inside the turret's reach
     * (201.1 mm from its axis), but with the wrist between 3 and 44.5 degrees the claw body comes within
     * 2 mm of it, at +/-4 degrees of straight back, at EVERY lift height. With the wrist above 47 degrees
     * (carry 90, flower 122.8) every lifter part is at least 206.7 mm away: clear.
     * - The turret's zone: its parts farther than 190 mm from the axis sit from -32.3 to +28.8
     *   degrees of where it points (turret/v8.1GB reach measurements). The claw is 176 to 184 deg, so
     *   they meet when the turret points 176 - 28.8 = 147 to 184 + 32.3 = 217 degrees (turret
     *   angles: 0 forward, counterclockwise +, back = 180). turretFrom = 147, turretTo = 217.
     * - The wrist's zone: 0 to 47 degrees (the measured 3 to 44.5, with a margin). In servo
     *   positions: wristTo = folded + 47 / 270 = 0.10 + 0.174 = 0.274. wristFrom = 0: the wrist
     *   cannot go below folded (its stop), so the zone simply starts at the bottom.
     * The lift needs no zone: the wrist's rule covers every lift height.
     * No turretMargin or turretStopSec: the defaults fit. The turret is fast (a 312 RPM motor, 2.75:1:
     * about 680 deg a second), but the rule measures its speed every loop and slows it down as it comes
     * near the zone, braking in time even when loops are slow (docs/guides/interlocks.md, "Stop
     * distance"). It waits two margins (2 x 3 deg) from the zone: at 147 - 6 = 141 (or -143 + 6 = -137).
     * On the built robot: full stick toward the back; if it ever ends past its waiting point, raise
     * turretStopSec (how long it takes to stop, default 0.15 s).
     * What it means on the field: while the wrist is folded or at pick-up, the turret aims anywhere
     * from -137 to 141 degrees; once the wrist swings out to carry, it may point anywhere again (its
     * -190 to 190). Change folded? Change wristTo too (folded + 0.174).
     * These are read once at INIT (not in the tuning menu, on purpose: see docs/guides/interlocks.md).
     */
    public static class TurretClaw {
        public static double turretFrom = 147;                      // degrees: the turret's zone starts here
        public static double turretTo = 217;                        // degrees: the turret's zone ends here
        public static double wristFrom = 0;                         // servo position: the wrist's zone starts at 0
        public static double wristTo = 0.274;                       // servo position: 47 degrees (folded + 0.174)
    }
}
