package io.github.jasoncross.ftchelper.vision;

import java.util.Locale;

/**
 * One target the camera sees, in the same form for every camera (webcam or Limelight).
 *
 * <ul>
 *   <li>{@link #bearingDeg}: how far to turn to face it. <b>Positive = it is to the LEFT</b>
 *       (counterclockwise, like the IMU). The library converts the Limelight's opposite sign for you.</li>
 *   <li>{@link #distanceIn}: straight-line distance in inches, or NaN if the camera cannot tell.</li>
 *   <li>{@link #timeNanos}: when the camera took the picture ({@code System.nanoTime()} clock).</li>
 * </ul>
 *
 * Docs: docs/modules/vision.md
 */
public final class TargetObservation {
    /** The id used for AprilTag clusters (they have a name instead of a number). */
    public static final int CLUSTER = -1;

    public final int id;
    public final String name;
    public final double bearingDeg;
    public final double distanceIn;
    public final long timeNanos;

    public TargetObservation(int id, String name, double bearingDeg, double distanceIn, long timeNanos) {
        this.id = id;
        this.name = name;
        this.bearingDeg = bearingDeg;
        this.distanceIn = distanceIn;
        this.timeNanos = timeNanos;
    }

    /** Milliseconds since the picture was taken. */
    public double ageMs(long nowNanos) {
        return (nowNanos - timeNanos) / 1e6;
    }

    /** True if the picture is newer than {@code maxAgeMs}. Old pictures must not be used to aim. */
    public boolean isFresh(long nowNanos, double maxAgeMs) {
        return ageMs(nowNanos) <= maxAgeMs;
    }

    /** True if the camera worked out a bearing (some detections have only an id). */
    public boolean hasBearing() {
        return !Double.isNaN(bearingDeg);
    }

    /** True if the camera worked out a distance. */
    public boolean hasDistance() {
        return !Double.isNaN(distanceIn);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s: bearing %.1f deg, %s", name, bearingDeg,
                hasDistance() ? String.format(Locale.US, "%.1f in", distanceIn) : "distance ?");
    }
}
