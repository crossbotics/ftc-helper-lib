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
    private final List<Subsystem> parts = new ArrayList<>();

    /** Adds parts. Null parts are skipped (handy for optional hardware). */
    public Subsystems add(Subsystem... subsystems) {
        for (Subsystem s : subsystems) if (s != null) parts.add(s);
        return this;
    }

    /** Updates every part, in the order they were added. */
    @Override
    public void update() {
        for (Subsystem s : parts) s.update();
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
        for (Subsystem s : parts) s.addTelemetry(t);
    }

    /** The parts, in order (read only). */
    public List<Subsystem> all() {
        return Collections.unmodifiableList(parts);
    }
}
