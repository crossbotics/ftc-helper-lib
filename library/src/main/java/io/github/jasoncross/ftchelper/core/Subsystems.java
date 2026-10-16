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
 * Docs: docs/modules/core.md
 */
public class Subsystems implements Subsystem {
    /** A part whose update() takes longer than this (milliseconds) is reported in telemetry. */
    public static final double SLOW_UPDATE_MS = 25;

    private final List<Subsystem> parts = new ArrayList<>();
    private String slowWarning = "";

    /** Adds parts. Null parts are skipped (handy for optional hardware). */
    public Subsystems add(Subsystem... subsystems) {
        for (Subsystem s : subsystems) if (s != null) parts.add(s);
        return this;
    }

    /**
     * Updates every part, in the order they were added. Each part's time is measured: an update()
     * that takes too long (it waits or sleeps) shows a warning in {@link #addTelemetry}.
     */
    @Override
    public void update() {
        for (Subsystem s : parts) {
            long start = System.nanoTime();
            s.update();
            double ms = (System.nanoTime() - start) / 1e6;
            if (ms > SLOW_UPDATE_MS) {
                slowWarning = String.format(java.util.Locale.US,
                        "%s update took %.0f ms: update() must not wait or sleep", s.name(), ms);
            }
        }
    }

    /** "" or the last slow update (see {@link #SLOW_UPDATE_MS}). */
    public String getSlowUpdateWarning() {
        return slowWarning;
    }

    /** Stops every part. Keeps going if one part throws, so every motor gets the stop. */
    @Override
    public void stop() {
        RuntimeException first = null;
        for (Subsystem s : parts) {
            try {
                s.stop();
            } catch (RuntimeException e) {
                if (first == null) first = e;
            }
        }
        if (first != null) throw first;
    }

    /** Adds every part's telemetry. */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!slowWarning.isEmpty()) t.warn(slowWarning);
        for (Subsystem s : parts) s.addTelemetry(t);
    }

    /** The parts, in order (read only). */
    public List<Subsystem> all() {
        return Collections.unmodifiableList(parts);
    }
}
