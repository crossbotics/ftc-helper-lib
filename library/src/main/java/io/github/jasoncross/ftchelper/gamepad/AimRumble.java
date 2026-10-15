package io.github.jasoncross.ftchelper.gamepad;

import com.qualcomm.robotcore.hardware.Gamepad;
import java.util.function.LongSupplier;

/**
 * Buzzes the gamepad when an error is small, for example when the robot is aimed at a target.
 * The closer to zero, the stronger and longer each buzz.
 * (This was called RumbleNotifier last season.)
 *
 * <pre>{@code
 * AimRumble aimFeel = new AimRumble(gamepad1).setThreshold(2.5);
 * // every loop:
 * aimFeel.update(headingErrorDegrees);
 * }</pre>
 *
 * Docs: docs/modules/gamepad.md
 */
public class AimRumble {
    private final Gamepad gamepad;
    private final LongSupplier clockNanos;

    private boolean enabled = true;
    private double threshold = 2.5;
    private double minStrength = 0.10, maxStrength = 0.65;
    private int minPulseMs = 120, maxPulseMs = 200;
    private int minCooldownMs = 120, maxCooldownMs = 350;
    private long lastPulseNanos = Long.MIN_VALUE / 2;

    /** @param gamepad the gamepad to buzz */
    public AimRumble(Gamepad gamepad) {
        this(gamepad, System::nanoTime);
    }

    /** Makes one with your own clock. Used by tests. */
    public AimRumble(Gamepad gamepad, LongSupplier clockNanos) {
        this.gamepad = gamepad;
        this.clockNanos = clockNanos;
    }

    /** Turns the buzzing on or off. */
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
     */
    public AimRumble setThreshold(double threshold) {
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
     */
    public AimRumble setFeel(double minStrength, double maxStrength, int minPulseMs, int maxPulseMs,
                             int minCooldownMs, int maxCooldownMs) {
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
     * @param error the error, any sign (only its size is used)
     * @return true if it started a buzz this loop
     */
    public boolean update(double error) {
        if (!enabled || gamepad == null || Double.isNaN(error)) return false;
        double size = Math.abs(error);
        if (size > threshold) return false;

        double closeness = 1 - size / threshold;   // 1 at zero error, 0 at the edge
        long now = clockNanos.getAsLong();
        double cooldownMs = minCooldownMs + (maxCooldownMs - minCooldownMs) * closeness;
        if (now - lastPulseNanos < cooldownMs * 1e6) return false;

        double strength = minStrength + (maxStrength - minStrength) * closeness;
        int pulseMs = (int) Math.round(minPulseMs + (maxPulseMs - minPulseMs) * closeness);
        gamepad.rumble(strength, strength, pulseMs);
        lastPulseNanos = now;
        return true;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
