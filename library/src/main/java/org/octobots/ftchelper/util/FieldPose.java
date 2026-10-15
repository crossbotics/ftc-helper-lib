package org.octobots.ftchelper.util;

/**
 * Where the robot is on the field: x and y in inches, heading in degrees
 * (counterclockwise is positive, like the IMU).
 *
 * <p>The library does not choose where (0, 0) is. Your team decides that for each game
 * and writes it down. Docs: docs/modules/util.md
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
        this(0, 0, 0);
    }

    /**
     * @param x          inches
     * @param y          inches
     * @param headingDeg degrees, counterclockwise positive
     */
    public FieldPose(double x, double y, double headingDeg) {
        this.x = x;
        this.y = y;
        this.headingDeg = headingDeg;
    }

    /** A new pose with the same numbers. Changing the copy does not change this one. */
    public FieldPose copy() {
        return new FieldPose(x, y, headingDeg);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US, "(%.1f in, %.1f in, %.1f deg)", x, y, headingDeg);
    }
}
