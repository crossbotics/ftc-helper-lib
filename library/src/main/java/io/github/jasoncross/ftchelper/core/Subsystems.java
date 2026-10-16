package io.github.jasoncross.ftchelper.core;

import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A group of {@link Subsystem}s that are updated, stopped and shown together.
 *
 * <pre>{@code
 * Subsystems robot = new Subsystems().add(lift, claw, intake);
 * while (opModeIsActive()) {
 *     robot.update();               // every part, once
 *     robot.addTelemetry(telem);
 * }
 * robot.stop();
 * }</pre>
 *
 * <p>{@code AutoRoutine} and {@code EasyAuto} take a Subsystems group, so your mechanisms keep
 * working during every autonomous move.
 *
 * <p>A group is itself a Subsystem ({@code implements Subsystem}), so a group can be put inside
 * another group. See docs/guides/architecture.md for how the parts fit together.
 *
 * Docs: docs/modules/core.md
 */
public class Subsystems implements Subsystem {
    /** A part whose update() takes longer than this (milliseconds) is reported in telemetry. */
    public static final double SLOW_UPDATE_MS = 25;

    // The parts, in the order they were added.
    private final List<Subsystem> parts = new ArrayList<>();
    // The latest slow-update note, or "" if no part has been slow.
    private String slowWarning = "";

    /**
     * Adds parts. Null parts are skipped (handy for optional hardware).
     *
     * @param subsystems one or more parts; "Subsystem..." means "any number of them, comma-separated"
     * @return this group, so you can chain more calls
     */
    public Subsystems add(Subsystem... subsystems) {
        // Skipping null means a part that is missing from this robot (Hw.tryGet gave null)
        // does not crash the loop.
        for (Subsystem s : subsystems) if (s != null) parts.add(s);
        return this;
    }

    // "@Override" (below) means this is the group's own version of a method the Subsystem
    // interface asks for.
    /**
     * Updates every part, in the order they were added. Each part's time is measured: an update()
     * that takes too long (it waits or sleeps) shows a warning in {@link #addTelemetry}.
     */
    @Override
    public void update() {
        for (Subsystem s : parts) {
            // Time each part (1 ms = 1e6 nanoseconds), because one slow part freezes the whole robot.
            long start = System.nanoTime();
            s.update();
            double ms = (System.nanoTime() - start) / 1e6;
            if (ms > SLOW_UPDATE_MS) {
                // Locale.US: always "." for decimals, whatever the phone's language setting.
                slowWarning = String.format(java.util.Locale.US,
                        "%s update took %.0f ms: update() must not wait or sleep", s.name(), ms);
            }
        }
    }

    /**
     * "" or the last slow update (see {@link #SLOW_UPDATE_MS}).
     * Once set, it stays until a later slow update replaces it.
     */
    public String getSlowUpdateWarning() {
        return slowWarning;
    }

    /** Stops every part. Keeps going if one part throws, so every motor gets the stop. */
    @Override
    public void stop() {
        // Safety: if one part's stop() fails, we still must stop all the others. So we catch
        // the first error, finish stopping everything, and only then report that error.
        RuntimeException first = null;
        for (Subsystem s : parts) {
            try {
                s.stop();
            } catch (RuntimeException e) {
                if (first == null) first = e;
            }
        }
        // Report the problem now that every part has been told to stop.
        if (first != null) throw first;
    }

    /** Adds every part's telemetry. A slow-update warning goes on top. */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!slowWarning.isEmpty()) t.warn(slowWarning);
        for (Subsystem s : parts) s.addTelemetry(t);
    }

    /** The parts, in order (read only). */
    public List<Subsystem> all() {
        // A read-only view: code that gets this list cannot add or remove parts by accident.
        return Collections.unmodifiableList(parts);
    }
}
