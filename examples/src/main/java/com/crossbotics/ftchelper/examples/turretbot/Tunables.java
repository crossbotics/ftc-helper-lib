package com.crossbotics.ftchelper.examples.turretbot;

/**
 * Example robot 2, file 2 of 5: every number, one section per part (see example robot 1's
 * Tunables for how tuning works).
 */
public class Tunables {
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        public static boolean calibrated = false;
    }

    /**
     * Limelight pipelines 0, 1 and 2 find AprilTags, each tuned for a kind of light (Robot.java's
     * autoPipelines tries them in INIT). autoLighting = false skips that test and uses pipeline.
     */
    public static class Limelight {
        public static double pipeline = 0;
        public static boolean autoLighting = true;
    }

    public static class Intake {
        public static double inPower = 1.0, outPower = 0.7;
    }

    /**
     * The turret's range in degrees (0 = straight forward, counterclockwise +): it never turns past
     * these, even when the goal is further round. cameraOffset: the camera's bearing to the goal when
     * the launcher is aimed at it (0 if the camera points the same way as the launcher).
     */
    public static class Turret {
        public static double min = -90, max = 90;
        public static double kP = 0.02, kI = 0, kD = 0.001;
        public static double maxPower = 0.8, tolerance = 1.5, cameraOffset = 0;
        // Slows down within slowZone degrees of a limit, to slowZonePower; a goal (from the odometry) more
        // than outOfRangeHold degrees past a limit: it holds where it points (the library's numbers).
        public static double slowZone = 10, slowZonePower = 0.25, outOfRangeHold = 10;
        // No home switch: set the turret straight forward by hand before INIT (startAngle 0). TeleOp keeps
        // the angle the last program ended at (keepAngle; the library's numbers).
        public static double startAngle = 0;
        public static boolean keepAngle = true;
    }

    /**
     * Distance to the goal (inches), flywheel speed (RPM) and hood angle (degrees, see Hood below) that
     * score from there, measured on the field (the Shot Tuner helps; it tunes one table at a time).
     * Between rows the speed and the hood are blended. One table per game piece, because they fly
     * differently: POLLEN is a 2.8 in ball, NECTAR a 3.6 in ball (game manual, section 9.8). Example
     * numbers: measure your own. speedRpm: the speed until the goal is first seen.
     */
    public static class Launcher {
        public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 1500;
        // No kP..kF yet: starting values from the parts catalog (INIT shows them). The Flywheel Finder's line goes here.
        public static double shotIntervalSec = 0.25;   // the least time between two pieces (seconds)
        public static double[][] speedByDistancePollen = {{24, 2200, 2}, {48, 2550, 11}, {72, 2900, 19}, {96, 3250, 23}};
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
        public static double posA = 0.20, posB = 0.65, angleA = 0, angleB = 25;
        public static double travelSec = 0.45, startAngle = 0;
    }

    /**
     * Aiming. rumbleDeg: gamepad 2 buzzes when the turret is this close to the goal (Controls.java's
     * rumbleWhenAimed). With odometry you would add goalX and goalY here: then the turret and the
     * speed keep working when the camera loses the goal (lesson 9). mirrorForRed = false: BIOBUZZ's field
     * is turned half a turn, so RED uses the same numbers from the red wall (the robot's one setting:
     * the autonomous and the aim use it).
     */
    public static class Aim {
        public static double rumbleDeg = 4;
        public static boolean mirrorForRed = false;
    }

    public static class Indexer {
        public static double rest = 0.15, push = 0.55, pushSec = 0.25, returnSec = 0.25;
    }

    /** The claw's fold-out pivot: tucked in, down to grab, and up over a FLOWER. */
    public static class ClawFold {
        public static double stowed = 0.05, grab = 0.62, flower = 0.40;
        public static double travelSec = 0.8;   // about how long a fold takes
    }

    public static class Claw {
        public static double open = 0.20, closed = 0.75;
        public static double travelSec = 0.6;
    }
}
