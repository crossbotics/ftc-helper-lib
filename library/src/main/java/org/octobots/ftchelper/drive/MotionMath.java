package org.octobots.ftchelper.drive;

import org.octobots.ftchelper.util.MathUtil;

/**
 * Speed-profile math for autonomous moves. No hardware here.
 *
 * Docs: docs/modules/drive.md
 */
public final class MotionMath {
    private MotionMath() { }

    /**
     * The speed for this part of a move: full speed ("cruise") until the last part, then a
     * straight-line slow-down to {@code minSpeed} at the end ("taper").
     *
     * <p>Example: min 0.3, max 0.8, taper 0.1. With 50% left: 0.8. With 5% left: 0.55. With 0% left: 0.3.
     *
     * @param remainingFraction how much of the move is left, 1 = all, 0 = none
     * @param minSpeed          speed at the very end
     * @param maxSpeed          cruise speed
     * @param taperStart        start slowing when this fraction is left (e.g. 0.10)
     */
    public static double taperSpeed(double remainingFraction, double minSpeed, double maxSpeed, double taperStart) {
        double rf = MathUtil.clamp(remainingFraction, 0, 1);
        double ts = MathUtil.clamp(taperStart, 1e-3, 1);
        double speed = rf > ts ? maxSpeed : minSpeed + (maxSpeed - minSpeed) * (rf / ts);
        return MathUtil.clamp(speed, Math.min(minSpeed, maxSpeed), Math.max(minSpeed, maxSpeed));
    }
}
