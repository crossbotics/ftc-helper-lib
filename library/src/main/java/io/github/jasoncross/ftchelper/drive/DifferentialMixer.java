package io.github.jasoncross.ftchelper.drive;

import io.github.jasoncross.ftchelper.util.MathUtil;

/**
 * The math that turns stick values into left and right powers for a standard (tank) drive.
 * No hardware here, so it is easy to test. {@link TankDrive} uses it.
 *
 * <p>Signs: forward + = forward, turnClockwise + = turn right. Same as {@link MecanumMixer}.
 *
 * Docs: docs/modules/drive.md
 */
public final class DifferentialMixer {
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
        if (!Double.isFinite(forward) || !Double.isFinite(turnClockwise) || !Double.isFinite(limit)) {
            return new double[2];
        }
        double[] powers = {forward + turnClockwise, forward - turnClockwise};
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
