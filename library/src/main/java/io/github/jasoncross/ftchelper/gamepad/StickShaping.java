package io.github.jasoncross.ftchelper.gamepad;

import io.github.jasoncross.ftchelper.util.MathUtil;

/**
 * Helpers that make sticks and triggers feel good to drive with.
 *
 * <pre>{@code
 * double forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
 * double speed   = StickShaping.slowScale(gamepad1.left_trigger, 0.25);
 * drive.drive(forward, strafe, turn, speed);
 * }</pre>
 *
 * <p>The minus sign on {@code left_stick_y} is needed because the SDK gives a negative number
 * when you push the stick UP (away from you). See docs/conventions.md.
 *
 * Docs: docs/modules/gamepad.md
 */
public final class StickShaping {
    // "final" class with a private constructor: a box of "static" helper methods that you call
    // on the class name (StickShaping.shape(...)), never an object you make with "new".
    private StickShaping() { }

    /**
     * Bends the stick response so small moves give fine control and a full push still
     * gives full power. The sign is kept.
     *
     * <p>Exponent 1 = straight line (no change). Exponent 2 = "squared": half stick gives 1/4 power.
     * Exponent 3 = even softer in the middle.
     *
     * @param value    stick value, -1 to 1
     * @param exponent 1 or more (values below 1 are treated as 1)
     * @return the curved value, -1 to 1
     */
    public static double curve(double value, double exponent) {
        // Keep the stick inside -1..1 first, so the curve can never give more than full power.
        double clamped = MathUtil.clamp(value, -1, 1);
        // Curve the SIZE of the push (Math.abs drops the sign), then put the sign back
        // (Math.signum gives -1, 0 or +1). Without this, squaring would turn "back" into "forward".
        return Math.signum(clamped) * Math.pow(Math.abs(clamped), Math.max(1, exponent));
    }

    /**
     * Deadband (with a smooth start) and then a curve. The usual one-line stick setup.
     *
     * <p>A "deadband" (dead zone) ignores tiny stick values near the middle. Sticks rarely rest at
     * exactly 0, and without a dead zone the robot would creep when nobody touches the stick.
     * "Smooth start" means the power starts from 0 at the edge of the dead zone instead of jumping.
     *
     * @param value    stick value, -1 to 1
     * @param deadband dead zone, e.g. 0.05
     * @param exponent curve, e.g. 1 (none) or 2 (squared)
     * @return the shaped value, -1 to 1
     */
    public static double shape(double value, double deadband, double exponent) {
        return curve(MathUtil.deadbandScaled(value, deadband), exponent);
    }

    /**
     * Turns a trigger into a speed multiplier: not pressed = full speed, fully pressed = slowest.
     * This is last season's "trigger brake".
     *
     * <p>Example with slowest 0.25: trigger 0 gives 1.0; trigger 1 gives 0.25; trigger 0.5 gives 0.625.
     *
     * @param trigger the trigger value, 0 to 1
     * @param slowest the multiplier at full press, 0 to 1
     * @return the speed multiplier, from {@code slowest} to 1
     */
    public static double slowScale(double trigger, double slowest) {
        // Keep both inputs in 0..1 so a bad value can never make the robot faster than full speed.
        double floor = MathUtil.clamp(slowest, 0, 1);
        // Take away part of the speed: none at trigger 0, all the way down to "slowest" at trigger 1.
        return 1 - MathUtil.clamp(trigger, 0, 1) * (1 - floor);
    }
}
