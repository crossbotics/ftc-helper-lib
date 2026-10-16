package io.github.jasoncross.ftchelper.util;

/**
 * Angle helpers, in degrees.
 *
 * <p>Headings go around in a circle, so plain subtraction gives wrong answers near the
 * "seam". Example: from 170 degrees to -170 degrees is a 20-degree turn, not 340.
 * These helpers fix that. Docs: docs/modules/util.md
 */
public final class Angles {
    // "final" class + private constructor: a box of "static" helpers you call on the class name
    // (Angles.wrapDegrees(...)), never an object you make with "new".
    private Angles() { }

    /**
     * Puts an angle into the range -180 (not included) to 180 (included).
     *
     * <p>Examples: 190 gives -170; -540 gives 180; 45 gives 45.
     *
     * @param degrees any angle
     * @return the same direction, written between -180 and 180
     */
    public static double wrapDegrees(double degrees) {
        // "%" is the remainder after dividing: it takes away whole turns (360 degrees),
        // leaving a number between -360 and 360 with the same sign as the input.
        double r = degrees % 360.0;
        // Then move it into -180..180 by adding or taking away one more whole turn.
        if (r <= -180) r += 360;
        if (r > 180) r -= 360;
        return r;
    }

    /**
     * The shortest turn from {@code current} to {@code target}.
     *
     * <p>Positive result = turn counterclockwise (left), the same sign as the IMU.
     * Example: {@code shortestDiff(-170, 170)} gives {@code 20}.
     * Signs are explained in docs/conventions.md.
     *
     * @param targetDeg  where you want to point
     * @param currentDeg where you point now
     * @return the turn, from -180 to 180 degrees
     */
    public static double shortestDiff(double targetDeg, double currentDeg) {
        // Plain subtraction can say "turn 340"; wrapping turns that into the short way, "-20".
        return wrapDegrees(targetDeg - currentDeg);
    }
}
