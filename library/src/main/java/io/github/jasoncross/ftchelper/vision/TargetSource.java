package io.github.jasoncross.ftchelper.vision;

import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.List;

/**
 * A camera that finds targets. {@link WebcamTags} and {@link LimelightTags} are two kinds; the rest
 * of your code only talks to this interface, so you can switch cameras (or seasons) by changing
 * ONE line. A new kind of camera next year? Write a class with these methods.
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
 * Docs: docs/modules/vision.md
 */
public interface TargetSource extends Subsystem {

    /** Starts the camera. Call in INIT (a webcam needs about 1-2 s). */
    void start();

    /** Everything seen in the latest picture (empty if nothing). */
    List<TargetObservation> targets();

    /** The target with this id in the latest picture, or null. */
    default TargetObservation target(int id) {
        for (TargetObservation t : targets()) if (t.id == id) return t;
        return null;
    }

    /** The target with this name (e.g. an AprilTag cluster's name), or null. */
    default TargetObservation target(String name) {
        for (TargetObservation t : targets()) if (name.equals(t.name)) return t;
        return null;
    }

    /** True if the camera is working (connected and streaming). */
    boolean isWorking();

    /** A short status for telemetry, e.g. "STREAMING 28 fps" or "not connected". */
    String status();

    /** Stops the camera and frees it. Called by {@link #stop()}. */
    void close();

    @Override
    default void stop() {
        close();
    }

    @Override
    default void addTelemetry(RobotTelemetry t) {
        if (!isWorking()) t.warn(name() + ": " + status());
        t.debug(name(), status());
        for (TargetObservation o : targets()) t.debug("  sees", o.toString());
    }
}
