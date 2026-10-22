package com.crossbotics.ftchelper.vision;

import java.util.Locale;

/**
 * One target the camera sees, in the same form for every camera (webcam or Limelight).
 * Because both cameras give you this same object, the rest of your code (aiming, the shot table)
 * does not care which camera you have.
 *
 * <ul>
 *   <li>{@link #bearingDeg}: how far to turn to face it, in degrees. <b>Positive = it is to the LEFT</b>
 *       (counterclockwise, like the IMU). The library converts the Limelight's opposite sign for you.</li>
 *   <li>{@link #distanceIn}: distance from the camera to the target in inches, measured flat in the
 *       camera's own view (sideways and forward from the lens, its up/down left out), or NaN ("not a
 *       number": the camera could not tell) if unknown. It equals the distance along the floor only when
 *       the camera is level; a camera tilted up reads more (with the camera 20 degrees up and the target
 *       2 ft higher: about 51 in instead of 46 in). So make the shot table with the camera mounted as it
 *       will be on the robot.
 *       Same meaning for every camera.</li>
 *   <li>{@link #timeNanos}: when the camera took the picture, on the {@code System.nanoTime()} clock
 *       (a stopwatch that counts nanoseconds, billionths of a second).</li>
 * </ul>
 *
 * <p>All the values are {@code final}: they are set once when the object is made and never change,
 * so one observation always describes the same picture.
 * The signs are in docs/conventions.md. Docs: docs/modules/vision.md
 */
public final class TargetObservation {
    /**
     * The id used for AprilTag clusters (they have a name instead of a number).
     * {@code static final} means one shared value for the whole program that never changes.
     */
    public static final int CLUSTER = -1;

    /**
     * The id of a target worked out from the ODOMETRY (where the robot is and where the goal is on the
     * field), not seen by a camera. The five-files robot uses it when the camera loses the goal
     * (see docs/modules/robot.md, section "Aim").
     */
    public static final int ODOMETRY = -2;

    /** The AprilTag's number, {@link #CLUSTER} for a group of tags, or {@link #ODOMETRY}. */
    public final int id;
    /** A name to show on the screen, e.g. "Tag 20" or a cluster's name. */
    public final String name;
    /** Degrees to turn to face the target. Positive = target is to the LEFT. NaN if unknown. */
    public final double bearingDeg;
    /** Inches from the camera to the target, flat in the camera's own view (see above). NaN if unknown. */
    public final double distanceIn;
    /** When the picture was taken ({@code System.nanoTime()} clock, nanoseconds). */
    public final long timeNanos;

    /**
     * Makes one observation. The camera classes call this for you.
     *
     * @param id         the tag number, or {@link #CLUSTER}
     * @param name       a name to show
     * @param bearingDeg degrees to turn to face it, positive = LEFT (NaN if unknown)
     * @param distanceIn inches, flat in the camera's own view (NaN if unknown)
     * @param timeNanos  when the picture was taken ({@code System.nanoTime()} clock)
     */
    public TargetObservation(int id, String name, double bearingDeg, double distanceIn, long timeNanos) {
        this.id = id;
        this.name = name;
        this.bearingDeg = bearingDeg;
        this.distanceIn = distanceIn;
        this.timeNanos = timeNanos;
    }

    /**
     * Milliseconds since the picture was taken: how old the picture is.
     *
     * @param nowNanos the time now, from {@code System.nanoTime()}
     */
    public double ageMs(long nowNanos) {
        // nanoseconds -> milliseconds: there are 1,000,000 (1e6) nanoseconds in a millisecond
        return (nowNanos - timeNanos) / 1e6;
    }

    /**
     * True if the picture is newer than {@code maxAgeMs}. Old pictures must not be used to aim:
     * the robot (or the target) has moved since, so the old bearing points the wrong way.
     *
     * @param nowNanos the time now, from {@code System.nanoTime()}
     * @param maxAgeMs the oldest picture you accept, in milliseconds
     */
    public boolean isFresh(long nowNanos, double maxAgeMs) {
        return ageMs(nowNanos) <= maxAgeMs;
    }

    /** True if the camera worked out a bearing (some detections have only an id). */
    public boolean hasBearing() {
        return !Double.isNaN(bearingDeg);
    }

    /** True if this target came from the odometry ({@link #ODOMETRY}), not from a camera. */
    public boolean fromOdometry() {
        return id == ODOMETRY;
    }

    /** True if the camera worked out a distance. */
    public boolean hasDistance() {
        return !Double.isNaN(distanceIn);
    }

    /**
     * A short line of text for the Driver Station, e.g. "Tag 20: bearing 3.5 deg, 48.2 in".
     * {@code @Override} means this replaces Java's built-in version of the method.
     */
    @Override
    public String toString() {
        // Locale.US: always write numbers with a dot (3.5), whatever language the phone is set to.
        // "a ? b : c" means "if a then b, otherwise c": show the distance, or "distance ?" if unknown.
        return String.format(Locale.US, "%s: bearing %.1f deg, %s", name, bearingDeg,
                hasDistance() ? String.format(Locale.US, "%.1f in", distanceIn) : "distance ?");
    }
}
