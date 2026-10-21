package com.crossbotics.ftchelper.drive;

import com.crossbotics.ftchelper.util.MathUtil;

/**
 * Speed-profile math for autonomous moves. No hardware here.
 * (A "speed profile" is the plan for how fast to go at each point of a move.)
 *
 * Docs: docs/modules/drive.md (section "How a move works (cruise and taper)")
 */
public final class MotionMath {
    // Private constructor: nobody makes a MotionMath object; call MotionMath.taperSpeed(...) directly.
    private MotionMath() { }

    /**
     * The speed for this part of a move: full speed ("cruise") until the last part, then a
     * straight-line slow-down to {@code minSpeed} at the end ("taper").
     * Slowing down at the end makes the robot stop closer to the target, without skidding past it.
     *
     * <p>Example: min 0.3, max 0.8, taper 0.1. With 50% left: 0.8. With 5% left: 0.55. With 0% left: 0.3.
     *
     * @param remainingFraction how much of the move is left, 1 = all, 0 = none
     * @param minSpeed          speed at the very end
     * @param maxSpeed          cruise speed
     * @param taperStart        start slowing when this fraction is left (e.g. 0.10)
     * @return the motor power to use now, between minSpeed and maxSpeed
     */
    public static double taperSpeed(double remainingFraction, double minSpeed, double maxSpeed, double taperStart) {
        double rf = MathUtil.clamp(remainingFraction, 0, 1);
        // Never let taperStart be 0: we divide by it below, and dividing by 0 breaks the math.
        double ts = MathUtil.clamp(taperStart, 1e-3, 1);
        // "a ? b : c" means "if a then b, else c". Before the taper: full speed. Inside it: slide
        // in a straight line from maxSpeed (taper start) down to minSpeed (the end).
        double speed = rf > ts ? maxSpeed : minSpeed + (maxSpeed - minSpeed) * (rf / ts);
        // Keep the answer between the two speeds, even if someone passed min bigger than max.
        return MathUtil.clamp(speed, Math.min(minSpeed, maxSpeed), Math.max(minSpeed, maxSpeed));
    }
}
