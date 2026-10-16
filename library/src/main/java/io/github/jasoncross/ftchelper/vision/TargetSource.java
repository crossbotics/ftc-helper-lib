package io.github.jasoncross.ftchelper.vision;

import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.List;

/**
 * A camera that finds targets. {@link WebcamTags} and {@link LimelightTags} are two kinds; the rest
 * of your code only talks to this interface, so you can switch cameras (or seasons) by changing
 * ONE line. A new kind of camera next year? Write a class with these methods.
 *
 * <p>An {@code interface} is a list of methods that a class promises to have, without saying how
 * they work. Any class that "implements TargetSource" can be used wherever a TargetSource is
 * asked for. It also "extends Subsystem", so a camera can be added to your robot's group of
 * mechanisms and gets {@code update()}, {@code stop()} and telemetry like the others
 * (see core/Subsystem.java and docs/guides/architecture.md).
 *
 * <pre>{@code
 * TargetSource camera = useLimelight
 *         ? LimelightTags.create(hardwareMap, "limelight").pipeline(0)
 *         : WebcamTags.create(hardwareMap, "Webcam 1");
 * camera.start();
 * // every loop:
 * camera.update();
 * TargetObservation goal = camera.target(20);      // by tag id (null if not seen)
 * }</pre>
 *
 * Docs: docs/modules/vision.md. How to write your own: docs/guides/extending.md
 */
public interface TargetSource extends Subsystem {

    /** Starts the camera. Call in INIT (a webcam needs about 1-2 s). */
    void start();

    /** Everything seen in the latest picture (empty if nothing, or if the latest picture is too old). */
    List<TargetObservation> targets();

    /**
     * The target with this id in the latest picture, or null (nothing: not seen now).
     * Always check for null before using it.
     * ({@code default} means the interface already gives this method a body, so each camera class
     * gets it for free and does not have to write it.)
     */
    default TargetObservation target(int id) {
        // look through everything seen now and give back the first one with this id
        for (TargetObservation t : targets()) if (t.id == id) return t;
        return null;
    }

    /** The target with this name (e.g. an AprilTag cluster's name), or null. */
    default TargetObservation target(String name) {
        // name.equals(...) compares the letters; "==" would only check if it is the very same object
        for (TargetObservation t : targets()) if (name.equals(t.name)) return t;
        return null;
    }

    /** True if the camera is working (connected and streaming). */
    boolean isWorking();

    /** A short status for telemetry, e.g. "STREAMING 28 fps" or "not connected". */
    String status();

    /** Stops the camera and frees it. Called by {@link #stop()}. */
    void close();

    /** Stopping a camera means closing it, so the next OpMode can use it. */
    @Override
    default void stop() {
        close();
    }

    /**
     * Shows a warning if the camera is not working, and, when debug lines are on (the PRACTICE
     * profile), its status and every target it sees.
     * {@code @Override} means this fills in a method that Subsystem asks for.
     */
    @Override
    default void addTelemetry(RobotTelemetry t) {
        // a camera that is not working is a big deal (aiming will do nothing), so it is a warning
        if (!isWorking()) t.warn(name() + ": " + status());
        t.debug(name(), status());
        for (TargetObservation o : targets()) t.debug("  sees", o.toString());
    }
}
