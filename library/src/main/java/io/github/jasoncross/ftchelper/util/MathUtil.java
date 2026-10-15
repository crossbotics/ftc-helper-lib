package io.github.jasoncross.ftchelper.util;

/**
 * Small math helpers that robot code needs all the time.
 *
 * <p>Every method is "pure": it only uses its inputs, so it is safe to call anywhere,
 * any number of times. Docs: docs/modules/util.md
 */
public final class MathUtil {
    private MathUtil() { }

    /**
     * Keeps a number inside a range.
     *
     * <p>Example: {@code clamp(1.3, -1, 1)} gives {@code 1.0}.
     *
     * @param value the number to limit
     * @param min   the smallest allowed result
     * @param max   the largest allowed result
     * @return {@code min} if value is below it, {@code max} if value is above it, else value.
     *         Returns {@code 0} if value is NaN (not a number), so a bad reading never reaches a motor.
     */
    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return 0;
        if (min > max) { double t = min; min = max; max = t; }
        return Math.max(min, Math.min(max, value));
    }

    /** Same as {@link #clamp(double, double, double)} for whole numbers (for example encoder ticks). */
    public static int clamp(int value, int min, int max) {
        if (min > max) { int t = min; min = max; max = t; }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Ignores small values near zero.
     *
     * <p>Joysticks rarely rest at exactly 0. Without a deadband the robot creeps.
     * Example: {@code deadband(0.03, 0.05)} gives {@code 0}; {@code deadband(0.4, 0.05)} gives {@code 0.4}.
     *
     * @param value the input, for example a stick value
     * @param band  values with a size below this become 0 (use a positive number, e.g. 0.05)
     * @return 0 inside the band, else the value unchanged
     */
    public static double deadband(double value, double band) {
        return Math.abs(value) < Math.abs(band) ? 0 : value;
    }

    /**
     * Like {@link #deadband(double, double)}, but the output starts again from 0 at the edge of the
     * band, so there is no sudden jump. Use this for smooth, slow driving.
     *
     * <p>Example with band 0.1: 0.05 gives 0, 0.1 gives 0, 0.55 gives 0.5, 1.0 gives 1.0.
     *
     * @param value the input, from -1 to 1
     * @param band  the dead zone size, from 0 to just under 1
     * @return the rescaled value, from -1 to 1
     */
    public static double deadbandScaled(double value, double band) {
        band = Math.abs(band);
        if (band >= 1) return 0;
        double size = Math.abs(value);
        if (size < band) return 0;
        return Math.signum(value) * Math.min(1, (size - band) / (1 - band));
    }

    /**
     * Finds the number part of the way from {@code a} to {@code b}.
     *
     * <p>Example: {@code lerp(0, 10, 0.25)} gives {@code 2.5}. ("lerp" means linear interpolation.)
     *
     * @param a the result when t is 0
     * @param b the result when t is 1
     * @param t how far to go, usually 0 to 1 (not limited)
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Tells if two numbers are close enough.
     *
     * <p>Example: {@code isNear(100, 103, 5)} gives {@code true}.
     *
     * @param a         first number
     * @param b         second number
     * @param tolerance the largest allowed difference
     */
    public static boolean isNear(double a, double b, double tolerance) {
        return Math.abs(a - b) <= Math.abs(tolerance);
    }

    /**
     * Scales a group of numbers down together so that none is bigger than {@code limit}.
     * The ratios between them stay the same. Used for wheel powers.
     *
     * <p>Example: {@code normalize([2, 1, -1, 0], 1)} gives {@code [1, 0.5, -0.5, 0]}.
     *
     * @param values the numbers (changed in place and also returned)
     * @param limit  the largest allowed size, usually 1
     * @return the same array, scaled
     */
    public static double[] normalize(double[] values, double limit) {
        double largest = 0;
        for (double v : values) largest = Math.max(largest, Math.abs(v));
        limit = Math.abs(limit);
        if (largest > limit && largest > 0) {
            for (int i = 0; i < values.length; i++) values[i] = values[i] * limit / largest;
        }
        return values;
    }
}
