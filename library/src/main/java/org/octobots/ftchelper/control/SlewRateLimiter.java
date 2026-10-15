package org.octobots.ftchelper.control;

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
 * Docs: docs/modules/control.md
 */
public class SlewRateLimiter {
    private final LongSupplier clockNanos;
    private double risePerSecond;
    private double fallPerSecond;
    private double value = 0;
    private long lastNanos = -1;

    /**
     * Same limit for speeding up and slowing down.
     *
     * @param ratePerSecond the largest change per second, e.g. 2.0 means 0 to 1 takes 0.5 s
     */
    public SlewRateLimiter(double ratePerSecond) {
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
        this(risePerSecond, fallPerSecond, System::nanoTime);
    }

    /** Makes a limiter with your own clock. Used by tests. */
    public SlewRateLimiter(double risePerSecond, double fallPerSecond, LongSupplier clockNanos) {
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
            // First call: start from 0, so the first loop does not jump.
            return value;
        }
        double dt = (now - lastNanos) / 1e9;
        lastNanos = now;

        double timeLeft = dt;

        // Part 1: moving toward zero (slowing down) uses the fall rate.
        boolean towardZero = value != 0
                && (Math.signum(input) != Math.signum(value) || Math.abs(input) < Math.abs(value));
        if (towardZero) {
            double goal = Math.signum(input) == Math.signum(value) ? input : 0;
            double need = Math.abs(goal - value);
            double can = fallPerSecond * timeLeft;
            if (can < need) {
                value += Math.signum(goal - value) * can;
                return value;
            }
            value = goal;
            timeLeft -= fallPerSecond > 0 ? need / fallPerSecond : timeLeft;
        }

        // Part 2: moving away from zero (speeding up, also after crossing zero) uses the rise rate.
        double need = Math.abs(input - value);
        if (need > 0) {
            value += Math.signum(input - value) * Math.min(need, risePerSecond * timeLeft);
        }
        return value;
    }

    /** Jumps straight to a value (for example 0 when you stop). */
    public void reset(double newValue) {
        value = newValue;
        lastNanos = -1;
    }

    /** The last output. */
    public double getValue() {
        return value;
    }
}
