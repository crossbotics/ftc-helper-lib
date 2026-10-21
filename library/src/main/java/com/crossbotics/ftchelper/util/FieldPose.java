package com.crossbotics.ftchelper.util;

/**
 * Where the robot is on the field: x and y in inches, heading in degrees
 * (counterclockwise is positive, like the IMU).
 *
 * <p>A "pose" is the robot's place AND the way it is pointing, together.
 *
 * <p>The library does not choose where (0, 0) is. Your team decides that for each game
 * and writes it down. Docs: docs/modules/util.md. Signs: docs/conventions.md.
 */
public class FieldPose {
    /** Inches. */
    public double x;
    /** Inches. */
    public double y;
    /** Degrees, counterclockwise positive. */
    public double headingDeg;

    /** A pose at (0, 0), heading 0. */
    public FieldPose() {
        // "this(...)" hands the work to the other constructor below.
        this(0, 0, 0);
    }

    /**
     * Makes a pose from three numbers.
     *
     * @param x          inches
     * @param y          inches
     * @param headingDeg degrees, counterclockwise positive
     */
    public FieldPose(double x, double y, double headingDeg) {
        this.x = x;
        this.y = y;
        this.headingDeg = headingDeg;
    }

    /**
     * A new pose with the same numbers. Changing the copy does not change this one.
     * (In Java, passing an object around shares the SAME object; a copy keeps them apart.)
     */
    public FieldPose copy() {
        return new FieldPose(x, y, headingDeg);
    }

    /**
     * The pose as text for telemetry, e.g. "(12.0 in, -36.5 in, 90.0 deg)".
     * "@Override" means this replaces Java's built-in toString, which would print gibberish.
     */
    @Override
    public String toString() {
        // Locale.US: always a "." for decimals, whatever the phone's language setting.
        return String.format(java.util.Locale.US, "(%.1f in, %.1f in, %.1f deg)", x, y, headingDeg);
    }
}
