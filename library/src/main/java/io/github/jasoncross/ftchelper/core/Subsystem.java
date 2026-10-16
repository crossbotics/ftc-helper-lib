package io.github.jasoncross.ftchelper.core;

import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;

/**
 * One part of the robot (a lift, a claw, an intake, a launcher) that needs a little work every loop.
 *
 * <p>Every mechanism helper in this library is a Subsystem, and your own classes can be one too.
 * Put them in a {@link Subsystems} group and call {@code update()} once per loop: every part is
 * updated, in TeleOp and in autonomous.
 *
 * <p>Java note: an "interface" is a promise, a list of methods a class says it has. Any class that
 * writes {@code implements Subsystem} promises to have {@code update()}, so a {@link Subsystems}
 * group can call it without knowing what the part is. Methods marked "default" come with a ready-made
 * version (here: do nothing), so you only write them if your part needs them.
 *
 * <pre>{@code
 * public class Bucket implements Subsystem {
 *     public void update() { ... }                       // talk to the hardware here
 *     public void stop() { motor.setPower(0); }          // optional
 *     public void addTelemetry(RobotTelemetry t) { ... } // optional
 * }
 * }</pre>
 *
 * <p>Making your own: docs/guides/extending.md. Why update() must never wait: docs/guides/non-blocking.md.
 *
 * Docs: docs/modules/core.md
 */
public interface Subsystem {

    /**
     * Called once every loop. Do the hardware work here. Must be quick: no sleep, no waiting.
     * While update() waits, the whole robot is frozen (no driving, no limits, no STOP).
     */
    void update();

    /** Stops all motion. Called at the end of every OpMode. */
    default void stop() { }

    /** Adds this part's lines to the telemetry. Use {@code t.line} for drivers, {@code t.debug} for programmers. */
    default void addTelemetry(RobotTelemetry t) { }

    /** The name shown in telemetry and in the hardware check. */
    default String name() {
        // The class's own name, for example "Bucket". Write your own name() to show something else.
        return getClass().getSimpleName();
    }
}
