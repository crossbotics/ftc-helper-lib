package io.github.jasoncross.ftchelper.core;

import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;

/**
 * One part of the robot (a lift, a claw, an intake, a launcher) that needs a little work every loop.
 *
 * <p>Every mechanism helper in this library is a Subsystem, and your own classes can be one too.
 * Put them in a {@link Subsystems} group and call {@code update()} once per loop: every part is
 * updated, in TeleOp and in autonomous.
 *
 * <pre>{@code
 * public class Bucket implements Subsystem {
 *     public void update() { ... }                       // talk to the hardware here
 *     public void stop() { motor.setPower(0); }          // optional
 *     public void addTelemetry(RobotTelemetry t) { ... } // optional
 * }
 * }</pre>
 *
 * Docs: docs/modules/core.md
 */
public interface Subsystem {

    /** Called once every loop. Do the hardware work here. Must be quick: no sleep, no waiting. */
    void update();

    /** Stops all motion. Called at the end of every OpMode. */
    default void stop() { }

    /** Adds this part's lines to the telemetry. Use {@code t.line} for drivers, {@code t.debug} for programmers. */
    default void addTelemetry(RobotTelemetry t) { }

    /** The name shown in telemetry and in the hardware check. */
    default String name() {
        return getClass().getSimpleName();
    }
}
