package io.github.jasoncross.ftchelper.util;

/**
 * Passes the robot's last pose from Autonomous to TeleOp.
 *
 * <p>Both OpModes run in the same app, so a static value survives between them
 * (until the app restarts). At the end of Auto call {@link #save}; in TeleOp INIT call
 * {@link #consume}.
 *
 * <p>"static" means the value belongs to the class itself, not to one object, so there is only
 * one copy and it stays while the robot app keeps running, even after the Auto OpMode ends.
 *
 * <pre>{@code
 * // End of Autonomous:
 * PoseStore.save(new FieldPose(x, y, heading));
 *
 * // TeleOp INIT:
 * FieldPose start = PoseStore.consume();   // null if Auto did not save one
 * }</pre>
 *
 * Docs: docs/modules/util.md
 */
public final class PoseStore {
    // The saved pose, or null ("nothing saved").
    private static FieldPose lastPose = null;

    // Private constructor: nobody makes a PoseStore object; use PoseStore.save(...) and friends.
    private PoseStore() { }

    // Every method is "synchronized": only one piece of code may use the saved pose at a time,
    // in case two threads (for example a background job and the loop) call these together.

    /**
     * Saves a copy of the pose. A null pose is ignored.
     *
     * @param pose the robot's pose at the end of Autonomous
     */
    public static synchronized void save(FieldPose pose) {
        if (pose == null) return;
        // Save a COPY, so later changes to the caller's pose do not change the saved one.
        lastPose = pose.copy();
    }

    /** Returns a copy of the saved pose and keeps it saved. Null if none. */
    public static synchronized FieldPose peek() {
        // A copy again, so whoever gets it cannot change the saved one by accident.
        return lastPose == null ? null : lastPose.copy();
    }

    /** True if a pose is saved. */
    public static synchronized boolean has() {
        return lastPose != null;
    }

    /**
     * Returns the saved pose and clears it, so it is used only once. Null if none.
     * Why only once: an old pose from a match long ago must not be used as the start of the next one.
     */
    public static synchronized FieldPose consume() {
        FieldPose out = peek();
        lastPose = null;
        return out;
    }

    /** Forgets the saved pose. */
    public static synchronized void clear() {
        lastPose = null;
    }
}
