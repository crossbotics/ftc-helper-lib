package com.crossbotics.ftchelper.util;

import java.util.function.LongSupplier;

/**
 * Measures how fast your OpMode loop runs.
 *
 * <p>A slow loop makes the robot feel laggy. Show {@link #getHz()} on telemetry
 * to see it. Most robots run at 50 to 200 loops per second (Hz).
 * Under about 30 Hz, something is slow: see docs/guides/non-blocking.md, and Tutorial 06
 * (docs/tutorials/06-fast-clear-loop.md) for how to speed a loop up.
 *
 * <pre>{@code
 * LoopTimer loopTimer = new LoopTimer();
 * while (opModeIsActive()) {
 *     loopTimer.update();
 *     telemetry.addData("Loop", "%.0f Hz", loopTimer.getHz());
 *     ...
 * }
 * }</pre>
 *
 * Docs: docs/modules/util.md
 */
public final class LoopTimer {
    // A clock in nanoseconds (billionths of a second). Tests pass a fake one.
    private final LongSupplier clockNanos;
    // Time of the last update(), or -1 for "no call yet".
    private long lastNanos = -1;
    // How long the last loop took, and the smoothed average, in seconds.
    private double lastSeconds = 0;
    private double averageSeconds = 0;

    /** Makes a timer that uses the phone's clock. */
    public LoopTimer() {
        // "System::nanoTime" hands over the real clock method itself (a "method reference").
        this(System::nanoTime);
    }

    /**
     * Makes a timer with your own clock. Used by tests.
     *
     * @param clockNanos gives the time now, in nanoseconds
     */
    public LoopTimer(LongSupplier clockNanos) {
        this.clockNanos = clockNanos;
    }

    /**
     * Call once at the top of each loop.
     *
     * @return seconds since the last call (0 on the first call)
     */
    public double update() {
        long now = clockNanos.getAsLong();
        // First call: nothing to measure against yet, just remember the time.
        if (lastNanos < 0) {
            lastNanos = now;
            return 0;
        }
        // 1 second = 1e9 nanoseconds.
        lastSeconds = (now - lastNanos) / 1e9;
        lastNanos = now;
        // Smooth the average so the number on screen does not flicker.
        // The first measured loop is used as it is; after that each new loop counts for 10%.
        averageSeconds = averageSeconds == 0 ? lastSeconds : averageSeconds * 0.9 + lastSeconds * 0.1;
        return lastSeconds;
    }

    /** Seconds the last loop took. */
    public double getLastSeconds() {
        return lastSeconds;
    }

    /** Average loop time in milliseconds (smoothed). */
    public double getAverageMs() {
        return averageSeconds * 1000.0;
    }

    /** Loops per second (smoothed). 0 until two loops have run. */
    public double getHz() {
        // Loops per second = 1 / (seconds per loop). Check for 0 first: dividing by 0 is not allowed.
        return averageSeconds > 0 ? 1.0 / averageSeconds : 0;
    }

    /** Starts over, as if new. */
    public void reset() {
        lastNanos = -1;
        lastSeconds = 0;
        averageSeconds = 0;
    }
}
