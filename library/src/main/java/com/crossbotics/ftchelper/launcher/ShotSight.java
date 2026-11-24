package com.crossbotics.ftchelper.launcher;

/**
 * What the robot sees of the goal at one moment, for a group of continuous fire ({@link ContinuousFire}):
 * is there a fresh goal, how far, and how far off is the aim. The numbers never change once made.
 *
 * <pre>{@code
 * launcher.continuous().start(() -> robot.aim().shotSight());       // the robot's aim answers each loop
 * }</pre>
 *
 * Docs: docs/modules/launcher.md
 */
public final class ShotSight {
    /** Nothing seen. */
    public static final ShotSight NONE = new ShotSight(false, Double.NaN, Double.NaN, false, 0, 0, Double.NaN);

    /** True with a fresh goal now (never an old picture): the camera's, or the odometry backup's. */
    public final boolean goalSeen;
    /**
     * True when the camera itself sees the goal now (not the odometry backup). A group starts only on the
     * camera, and only the camera resets its "goal lost" clock.
     */
    public final boolean fromCamera;
    /** The distance to it, inches; NaN = not known. */
    public final double distanceIn;
    /** How far off the aim is, degrees (counterclockwise +); NaN = not known. */
    public final double aimErrorDeg;
    /** True when the aim is inside its tolerance now (no extra margin, no time limit that lets it pass). */
    public final boolean aimed;
    /** The aim's tolerance, degrees. */
    public final double toleranceDeg;
    /** A running group may be this much further off than the tolerance, degrees. */
    public final double lockMarginDeg;
    /** The camera offset the aim uses, degrees; NaN = none (a turret aims). */
    public final double aimOffsetDeg;

    /**
     * @param goalSeen      a fresh goal now
     * @param distanceIn    inches, or NaN
     * @param aimErrorDeg   degrees off, or NaN
     * @param aimed         inside the tolerance now
     * @param toleranceDeg  the tolerance, degrees
     * @param lockMarginDeg the extra room while a group runs, degrees
     * @param aimOffsetDeg  the camera offset in use, degrees, or NaN
     */
    public ShotSight(boolean goalSeen, double distanceIn, double aimErrorDeg, boolean aimed, double toleranceDeg,
                     double lockMarginDeg, double aimOffsetDeg) {
        this(goalSeen, goalSeen, distanceIn, aimErrorDeg, aimed, toleranceDeg, lockMarginDeg, aimOffsetDeg);
    }

    /**
     * A sight that says where the goal came from.
     *
     * @param goalSeen      a fresh goal now (the camera's or the odometry backup's)
     * @param fromCamera    the camera itself sees it now (false for the odometry backup)
     * @param distanceIn    inches, or NaN
     * @param aimErrorDeg   degrees off, or NaN
     * @param aimed         inside the tolerance now
     * @param toleranceDeg  the tolerance, degrees
     * @param lockMarginDeg the extra room while a group runs, degrees
     * @param aimOffsetDeg  the camera offset in use, degrees, or NaN
     */
    public ShotSight(boolean goalSeen, boolean fromCamera, double distanceIn, double aimErrorDeg, boolean aimed,
                     double toleranceDeg, double lockMarginDeg, double aimOffsetDeg) {
        this.goalSeen = goalSeen;
        this.fromCamera = goalSeen && fromCamera;
        this.distanceIn = distanceIn;
        this.aimErrorDeg = aimErrorDeg;
        this.aimed = aimed;
        this.toleranceDeg = toleranceDeg;
        this.lockMarginDeg = lockMarginDeg;
        this.aimOffsetDeg = aimOffsetDeg;
    }

    /**
     * The same sight with another distance (e.g. the filtered distance that sets the speed).
     *
     * @param inches the distance, or NaN
     */
    public ShotSight withDistance(double inches) {
        return new ShotSight(goalSeen, fromCamera, inches, aimErrorDeg, aimed, toleranceDeg, lockMarginDeg, aimOffsetDeg);
    }

    // A goal and a distance: enough to judge a shot by.
    boolean usable() {
        return goalSeen && !Double.isNaN(distanceIn) && !Double.isInfinite(distanceIn);
    }
}
