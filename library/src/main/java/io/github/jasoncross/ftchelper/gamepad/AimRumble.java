package io.github.jasoncross.ftchelper.gamepad;

import com.qualcomm.robotcore.hardware.Gamepad;
import java.util.function.LongSupplier;

/**
 * Buzzes the gamepad when an error is small, for example when the robot is aimed at a target.
 * The closer to zero, the stronger and longer each buzz.
 * (This was called RumbleNotifier last season.)
 *
 * <p>The "error" is how far you are from where you want to be, for example "the target is
 * 3 degrees to the left". The driver can feel the aim getting better without looking at a screen.
 *
 * <pre>{@code
 * AimRumble aimFeel = new AimRumble(gamepad1).setThreshold(2.5);
 * // every loop:
 * aimFeel.update(headingErrorDegrees);
 * }</pre>
 *
 * <p>It never waits: {@link #update} starts a buzz and returns at once; the gamepad times the
 * buzz by itself. See docs/guides/non-blocking.md for why that matters.
 *
 * Docs: docs/modules/gamepad.md
 */
public class AimRumble {
    // "final" fields are set once, in the constructor, and never change after.
    private final Gamepad gamepad;
    // A "clock" we can ask for the time, in nanoseconds (billionths of a second).
    // LongSupplier is "something you can ask for a whole number"; here, the current time.
    // Tests hand in a fake clock so they can pretend time has passed without really waiting.
    private final LongSupplier clockNanos;

    private boolean enabled = true;
    // Errors smaller than this (in the same units you pass to update) make a buzz.
    private double threshold = 2.5;
    // Buzz strength, 0 to 1: "min" is used at the edge of the threshold, "max" at zero error.
    private double minStrength = 0.10, maxStrength = 0.65;
    // Buzz length in milliseconds, at the edge and at zero error.
    private int minPulseMs = 120, maxPulseMs = 200;
    // Time in milliseconds from the start of one buzz to the start of the next.
    private int minCooldownMs = 120, maxCooldownMs = 350;
    // When the last buzz started. It starts far in the past (a very large negative number, but
    // only half the smallest possible, so subtracting it cannot overflow) so the first buzz can
    // happen right away.
    private long lastPulseNanos = Long.MIN_VALUE / 2;

    /**
     * Makes one that uses the real clock.
     *
     * @param gamepad the gamepad to buzz
     */
    public AimRumble(Gamepad gamepad) {
        // "System::nanoTime" hands over the System.nanoTime method itself (a "method reference"),
        // so this class can ask it for the time whenever it needs to.
        this(gamepad, System::nanoTime);
    }

    /**
     * Makes one with your own clock. Used by tests.
     *
     * @param gamepad    the gamepad to buzz
     * @param clockNanos gives the current time in nanoseconds
     */
    public AimRumble(Gamepad gamepad, LongSupplier clockNanos) {
        this.gamepad = gamepad;
        this.clockNanos = clockNanos;
    }

    /**
     * Turns the buzzing on or off.
     *
     * @param enabled true = buzz when aimed, false = never buzz
     * @return this AimRumble, so you can chain more settings
     */
    public AimRumble setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /** True if buzzing is on. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * The error size where buzzing starts. Default 2.5 (degrees, if you pass degrees).
     * Zero or negative values are ignored.
     *
     * @param threshold the largest error that still buzzes, more than 0
     * @return this AimRumble, so you can chain more settings
     */
    public AimRumble setThreshold(double threshold) {
        // Zero or less would mean "never buzz" and would divide by zero in update(), so ignore it.
        if (threshold > 0) this.threshold = threshold;
        return this;
    }

    /**
     * Changes the feel. The "min" values are used at the threshold edge, the "max" values at
     * zero error, and values in between are blended. Defaults are last season's values.
     *
     * @param minStrength   0 to 1, at the edge (default 0.10)
     * @param maxStrength   0 to 1, at zero error (default 0.65)
     * @param minPulseMs    buzz length at the edge (default 120)
     * @param maxPulseMs    buzz length at zero error (default 200)
     * @param minCooldownMs time from the START of one buzz to the start of the next, at the edge (default 120).
     *                      If it is not longer than the buzz, the buzzes run together with no gap.
     * @param maxCooldownMs the same, at zero error (default 350)
     * @return this AimRumble, so you can chain more settings
     */
    public AimRumble setFeel(double minStrength, double maxStrength, int minPulseMs, int maxPulseMs,
                             int minCooldownMs, int maxCooldownMs) {
        // Math.min/Math.max put each pair in the right order even if they were typed swapped.
        // clamp keeps each number in a sensible range: strength 0..1, buzz 20..2000 ms,
        // cooldown 20..5000 ms. "(int)" turns the decimal answer back into a whole number.
        this.minStrength = clamp(Math.min(minStrength, maxStrength), 0, 1);
        this.maxStrength = clamp(Math.max(minStrength, maxStrength), 0, 1);
        this.minPulseMs = (int) clamp(Math.min(minPulseMs, maxPulseMs), 20, 2000);
        this.maxPulseMs = (int) clamp(Math.max(minPulseMs, maxPulseMs), 20, 2000);
        this.minCooldownMs = (int) clamp(Math.min(minCooldownMs, maxCooldownMs), 20, 5000);
        this.maxCooldownMs = (int) clamp(Math.max(minCooldownMs, maxCooldownMs), 20, 5000);
        return this;
    }

    /**
     * Call every loop with the current error. Buzzes if the error is inside the threshold
     * and the last buzz was long enough ago. NaN (no target) does nothing.
     *
     * <p>NaN means "not a number": what you get when there is no reading, for example when the
     * camera cannot see the target.
     *
     * @param error the error, any sign (only its size is used)
     * @return true if it started a buzz this loop
     */
    public boolean update(double error) {
        // Nothing to do if buzzing is off, there is no gamepad, or there is no target.
        if (!enabled || gamepad == null || Double.isNaN(error)) return false;
        // Left and right count the same: only how far off we are matters.
        double size = Math.abs(error);
        // Too far off: no buzz.
        if (size > threshold) return false;

        double closeness = 1 - size / threshold;   // 1 at zero error, 0 at the edge
        long now = clockNanos.getAsLong();
        // Blend between the edge value and the zero-error value by how close we are.
        double cooldownMs = minCooldownMs + (maxCooldownMs - minCooldownMs) * closeness;
        // Too soon since the last buzz started? Wait for a later loop (1 ms = 1e6 nanoseconds).
        // We check the time each loop instead of sleeping, so the robot keeps running.
        if (now - lastPulseNanos < cooldownMs * 1e6) return false;

        // Same blending for how hard and how long to buzz.
        double strength = minStrength + (maxStrength - minStrength) * closeness;
        int pulseMs = (int) Math.round(minPulseMs + (maxPulseMs - minPulseMs) * closeness);
        // Both motors in the gamepad (left and right) buzz equally. The gamepad stops by itself.
        gamepad.rumble(strength, strength, pulseMs);
        lastPulseNanos = now;
        return true;
    }

    // Keeps v between lo and hi: below lo gives lo, above hi gives hi.
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
