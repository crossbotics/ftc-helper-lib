package com.crossbotics.ftchelper.examples.v2bot;

/**
 * The V2 robot, file 2 of 5: every number, one section per part. Numbers marked "example" are not
 * measured on a built robot: measure your own (the Finders and the tuning menu help; docs/guides/finders.md).
 * Where a number comes from team 8938's designs, the comment says which.
 */
public class Tunables {
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        public static boolean calibrated = false;
    }

    public static class Odometry {
        public static boolean checked = false;
    }

    /** Limelight pipelines 0, 1 and 2 find AprilTags, each tuned for a kind of light. */
    public static class Limelight {
        public static double pipeline = 0;
        public static boolean autoLighting = true;
    }

    public static class Intake {
        public static double inPower = 1.0, outPower = 0.7;
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
        public static double min = -190, max = 190;
        public static double homeAngle = 0, homingPower = 0.2, homingTimeout = 5;
        public static double kP = 0.02, kI = 0, kD = 0.001;                // example: tune your own
        public static double maxPower = 0.8, tolerance = 1.5, cameraOffset = 0;
    }

    /**
     * Distance to the goal (inches), flywheel motor speed (RPM) and hood angle (degrees) that score
     * from there. Example numbers: measure your own with the Shot Tuner. One table per game piece.
     */
    public static class Launcher {
        public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 1500;
        public static double kP = 40, kI = 0, kD = 0, kF = 12.5, nominalVolts = 12.8;
        public static double[][] speedByDistancePollen = {{24, 2200, 2}, {48, 2550, 11}, {72, 2900, 19}, {96, 3250, 23}};
        public static double[][] speedByDistanceNectar = {{24, 2400, 0}, {48, 2800, 6}, {72, 3200, 14}, {96, 3600, 18}};
    }

    /**
     * The hood (turret v8.1GB): 0 deg = its steep end, 25 deg = its flat end, between two stop cams.
     * The Torque servo turns it through 20T and 108T gears (5.4:1). posA, posB: example, the Hood
     * Finder finds yours. travelSec: 0.2 s per 60 deg x 5.4 x 25 deg = 0.45 s.
     */
    public static class Hood {
        public static double posA = 0.20, posB = 0.65, angleA = 0, angleB = 25;
        public static double travelSec = 0.45, startAngle = 0;
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
        public static double goalX = 13, goalY = -13;
        public static boolean mirrorForRed = false;
        public static boolean shootOnMove = false;
        public static double shooterForwardIn = 1, shooterLeftIn = 0;
        public static double rumbleDeg = 4;
    }

    public static class Indexer {
        public static double rest = 0.15, push = 0.55, pushSec = 0.25, returnSec = 0.25;
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
        public static double min = 0, max = 1350;
        public static double kP = 0.004, kI = 0, kD = 0.0002, kF = 0.05;
        public static double maxPower = 1.0, slowZone = 120, slowZonePower = 0.3, tolerance = 15;
        public static double pickup = 0, stowed = 44, flower = 1330;
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
        public static double folded = 0.10, pickup = 0.174, carry = 0.433, flower = 0.555;
        public static double travelSec = 0.3;
    }

    /**
     * The claw (the "pincer"): the fingers turn 1.2 times the servo's angle. Open: fingers at +17 deg
     * (servo +14 deg); closed: on a POLLEN, -10 deg (servo -8 deg); a NECTAR stops them at 0. Example
     * positions around 0.50: find yours with the Servo Finder.
     */
    public static class Claw {
        public static double open = 0.55, closed = 0.47;
        public static double travelSec = 0.15;
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
        public static double turretFrom = 147, turretTo = 217;   // degrees
        public static double wristFrom = 0, wristTo = 0.274;     // servo positions (0 to 47 degrees)
    }
}
