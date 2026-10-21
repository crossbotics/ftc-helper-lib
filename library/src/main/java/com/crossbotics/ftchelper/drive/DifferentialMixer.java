package com.crossbotics.ftchelper.drive;

import com.crossbotics.ftchelper.util.MathUtil;

/**
 * The math that turns stick values into left and right powers for a standard (tank) drive.
 * No hardware here, so it is easy to test. {@link TankDrive} uses it.
 *
 * <p>Signs: forward + = forward, turnClockwise + = turn right. Same as {@link MecanumMixer}.
 * All the sign rules: docs/conventions.md.
 *
 * Docs: docs/modules/drive.md. Tutorial 05 drives a standard robot with it.
 */
public final class DifferentialMixer {
    // A "private" constructor means nobody can make a DifferentialMixer object: it is only a
    // home for the "static" methods below, which you call on the class itself
    // (DifferentialMixer.arcade(...)). "final" here means no other class can extend it.
    private DifferentialMixer() { }

    /** Index of each side in the arrays this class returns. */
    public static final int LEFT = 0, RIGHT = 1;

    /**
     * "Arcade" drive: one stick for forward, one for turning.
     *
     * <p>If a side would get more than {@code limit}, both sides are scaled down together, so the
     * robot still follows the same curve.
     *
     * @param forward       -1 to 1
     * @param turnClockwise -1 to 1
     * @param limit         the largest power a side may get, 0 to 1
     * @return {left, right}. Both zero if an input is NaN or infinite.
     */
    public static double[] arcade(double forward, double turnClockwise, double limit) {
        // A broken reading (NaN = "not a number", or infinity) must never reach a motor:
        // stop both sides instead. "new double[2]" is two zeros.
        if (!Double.isFinite(forward) || !Double.isFinite(turnClockwise) || !Double.isFinite(limit)) {
            return new double[2];
        }
        // To turn right (clockwise), the left side drives faster and the right side slower,
        // so add the turn to the left and take it away from the right.
        double[] powers = {forward + turnClockwise, forward - turnClockwise};
        // Full forward plus full turn can ask for 2.0, more than a motor can give. Divide BOTH
        // sides by the bigger one so their ratio (and so the curve the robot drives) stays the same.
        // Cutting only the big side would change the curve.
        double largest = Math.max(1.0, Math.max(Math.abs(powers[0]), Math.abs(powers[1])));
        double scale = MathUtil.clamp(limit, 0, 1) / largest;
        powers[0] *= scale;
        powers[1] *= scale;
        return powers;
    }

    /**
     * "Tank" style: one stick for each side.
     *
     * @param left  -1 to 1
     * @param right -1 to 1
     * @param limit the largest power a side may get, 0 to 1
     * @return {left, right}, each multiplied by the limit (values past 1 are cut to 1 first)
     */
    public static double[] tank(double left, double right, double limit) {
        double l = MathUtil.clamp(limit, 0, 1);
        return new double[]{MathUtil.clamp(left, -1, 1) * l, MathUtil.clamp(right, -1, 1) * l};
    }
}
