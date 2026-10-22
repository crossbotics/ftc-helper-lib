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
        public static double kP = 40, kI = 0, kD = 0, kF = 12.5;
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
        public static double travelSec = 0.45, homeAngle = 0;
    }

    /**
     * Aiming. goalX, goalY: the goal on the field (inches, from the BLUE drivers' wall): example.
     * shooterForwardIn: the turret's axis is 1 in forward of the robot's centre (turret v8.1GB), which
     * matters when shooting on the move (off until tested on the real robot).
     */
    public static class Aim {
        public static double goalX = -58, goalY = 55;
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
     * <ul>
     *   <li>pickup 0: slide down; with the wrist at 20 deg the claw reaches a ball on the tiles.</li>
     *   <li>stowed 44 (13.7 mm): the folded claw clears the tiles.</li>
     *   <li>flower 1330 (415.1 mm): the ball over the FLOWER's top opening (wrist at 122.8 deg).</li>
     *   <li>max 1350: just under the hard stop at 425.1 mm (1362). min 0: the bottom stop.</li>
     * </ul>
     * kP...tolerance: example, tune your own (tutorial 07).
     */
    public static class Lift {
        public static double min = 0, max = 1350;
        public static double kP = 0.004, kI = 0, kD = 0.0002, kF = 0.05;
        public static double maxPower = 1.0, slowZone = 120, slowZonePower = 0.3, tolerance = 15;
        public static double pickup = 0, stowed = 44, flower = 1330;
    }

    /**
     * The wrist: servo positions (0 to 1). The goBILDA servo turns 300 degrees over 0 to 1, so one
     * degree is 1/300. Angles from straight down (lifter design): folded 0, pickup 20 (the claw a
     * little out, so the ball is not under the robot), carry 90 (swing out before lifting), flower
     * 122.8. Its hard stop is at 145 (0.583). folded = 0.10 is an example: find yours with the Servo
     * Finder, then add the degrees / 300. travelSec: 0.09 s per 60 deg x 145 deg = 0.22 s, plus a bit.
     */
    public static class Wrist {
        public static double folded = 0.10, pickup = 0.167, carry = 0.40, flower = 0.509;
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
     * THE INTERLOCK RULE (Robot.java: keepApart("TurretLift", ...)). The turret must stay out of the
     * back while the slide is up. Team 8938's lifter design: the lifter's parts are from -26 to +38 deg
     * of straight back (+ toward the slide's side) and the folded claw is clear of the turret until
     * the slide is about 84 mm up. In turret angles that is 142 to 206 deg; 33 deg more on each side
     * for the turret's own width (everything farther than 190 mm from its axis is within 33 deg of where
     * it points: turret/v8.1GB reach measurements) gives 109 to 239. 84 mm = 270 counts.
     * The slide is on the robot's left (+x), so "toward the slide" is toward 90 deg. These numbers come
     * from the first lifter concept (2026-10-02) and are not yet re-checked for the pincer claw. With
     * the turret axis 1 in forward of the centre, v8.1GB (201.1 mm reach) should clear the lifter at
     * every angle: this rule is a backup until the built robot is measured with the lift up.
     * liftMargin: the lift is fast (about 2800 counts a second at full power), so it needs a bigger
     * safety distance than the default (1/50 of its range, 27 counts): in the simulator, stopped by hand at
     * full power, it went 54 counts past where it was told to stop. 80 counts = 25 mm: it waits at
     * 270 - 2 x 80 = 110. Measure your own: push the stick fully up toward the line and see how far it goes.
     * These are read once at INIT (not in the tuning menu, on purpose: see docs/guides/interlocks.md).
     */
    public static class TurretLift {
        public static double turretFrom = 109, turretTo = 239;   // degrees
        public static double liftAbove = 270;                    // counts
        public static double liftMargin = 80;                    // counts
    }
}
