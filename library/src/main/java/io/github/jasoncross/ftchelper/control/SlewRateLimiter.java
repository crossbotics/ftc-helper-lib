package io.github.jasoncross.ftchelper.control;

import java.util.function.LongSupplier;

/**
 * Stops a value from changing too fast. Also called a "ramp".
 *
 * <p>Use it so a heavy robot or a tall lift does not jerk or tip when the driver slams a stick.
 *
 * <pre>{@code
 * SlewRateLimiter ramp = new SlewRateLimiter(2.0);  // full power in 0.5 s
 * // every loop:
 * double forward = ramp.calculate(-gamepad1.left_stick_y);
 * }</pre>
 *
 * <p>It uses the real time between loops, not the number of loops, so the ramp takes the same
 * time whether the loop is fast or slow.
 *
 * Docs: docs/modules/control.md
 */
public class SlewRateLimiter {
    // A clock in nanoseconds (billionths of a second). "final": set once and never changed.
    // Tests pass a fake clock so they do not have to really wait.
    private final LongSupplier clockNanos;
    // The largest change per second away from zero (speeding up) and toward zero (slowing down).
    private double risePerSecond;
    private double fallPerSecond;
    // The output we gave last time; each loop it moves a little toward what was asked for.
    private double value = 0;
    // Time of the last calculate(), or -1 for "no call yet".
    private long lastNanos = -1;

    /**
     * Same limit for speeding up and slowing down.
     *
     * @param ratePerSecond the largest change per second, e.g. 2.0 means 0 to 1 takes 0.5 s
     */
    public SlewRateLimiter(double ratePerSecond) {
        // "this(...)" hands the work to the next constructor, with the same rate both ways.
        this(ratePerSecond, ratePerSecond);
    }

    /**
     * Different limits for moving away from zero (speeding up) and toward zero (slowing down).
     * Tip: let it slow down faster than it speeds up, so the robot still stops quickly.
     *
     * @param risePerSecond largest change per second away from zero
     * @param fallPerSecond largest change per second toward zero
     */
    public SlewRateLimiter(double risePerSecond, double fallPerSecond) {
        // "System::nanoTime" hands over the real clock method itself (a "method reference").
        this(risePerSecond, fallPerSecond, System::nanoTime);
    }

    /**
     * Makes a limiter with your own clock. Used by tests.
     *
     * @param clockNanos gives the current time in nanoseconds
     */
    public SlewRateLimiter(double risePerSecond, double fallPerSecond, LongSupplier clockNanos) {
        // Rates are sizes: a negative number typed by mistake is turned positive.
        this.risePerSecond = Math.abs(risePerSecond);
        this.fallPerSecond = Math.abs(fallPerSecond);
        this.clockNanos = clockNanos;
    }

    /**
     * Moves toward the input, but no faster than the limit. Call once per loop.
     *
     * @param input the value you want
     * @return the limited value
     */
    public double calculate(double input) {
        long now = clockNanos.getAsLong();
        if (lastNanos < 0) {
            lastNanos = now;
            // First call (or first after reset): no time has passed yet, so keep the current value
            // (0 at the start, or the value given to reset) and do not jump.
            return value;
        }
        // Seconds since the last call (1 second = 1e9 nanoseconds).
        double dt = (now - lastNanos) / 1e9;
        lastNanos = now;

        // The time we may still use this loop. Slowing down can use up part of it, and the rest
        // is used for speeding up the other way.
        double timeLeft = dt;

        // Part 1: moving toward zero (slowing down) uses the fall rate.
        // That is when the input has the other sign (or is 0), or is smaller on the same side.
        boolean towardZero = value != 0
                && (Math.signum(input) != Math.signum(value) || Math.abs(input) < Math.abs(value));
        if (towardZero) {
            // Same side: slow down to the input. Other side: first slow down to 0.
            double goal = Math.signum(input) == Math.signum(value) ? input : 0;
            double need = Math.abs(goal - value);
            double can = fallPerSecond * timeLeft;
            if (can < need) {
                // Not enough time this loop to get there: go as far as allowed and stop for now.
                value += Math.signum(goal - value) * can;
                return value;
            }
            // Got there with time to spare: take off the time it took.
            value = goal;
            timeLeft -= fallPerSecond > 0 ? need / fallPerSecond : timeLeft;
        }

        // Part 2: moving away from zero (speeding up, also after crossing zero) uses the rise rate.
        double need = Math.abs(input - value);
        if (need > 0) {
            // Step toward the input, but never further than the rise rate allows in the time left.
            value += Math.signum(input - value) * Math.min(need, risePerSecond * timeLeft);
        }
        return value;
    }

    /**
     * Jumps straight to a value (for example 0 when you stop).
     *
     * @param newValue the value to start from
     */
    public void reset(double newValue) {
        value = newValue;
        // Forget the last time, so the next calculate() starts fresh from newValue.
        lastNanos = -1;
    }

    /** The last output. */
    public double getValue() {
        return value;
    }
}
