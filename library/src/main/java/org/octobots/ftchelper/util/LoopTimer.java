package org.octobots.ftchelper.util;

import java.util.function.LongSupplier;

/**
 * Measures how fast your OpMode loop runs.
 *
 * <p>A slow loop makes the robot feel laggy. Show {@link #getHz()} on telemetry
 * to see it. Most robots run at 50 to 200 loops per second (Hz).
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
    private final LongSupplier clockNanos;
    private long lastNanos = -1;
    private double lastSeconds = 0;
    private double averageSeconds = 0;

    /** Makes a timer that uses the phone's clock. */
    public LoopTimer() {
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
        if (lastNanos < 0) {
            lastNanos = now;
            return 0;
        }
        lastSeconds = (now - lastNanos) / 1e9;
        lastNanos = now;
        // Smooth the average so the number on screen does not flicker.
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
        return averageSeconds > 0 ? 1.0 / averageSeconds : 0;
    }

    /** Starts over, as if new. */
    public void reset() {
        lastNanos = -1;
        lastSeconds = 0;
        averageSeconds = 0;
    }
}
