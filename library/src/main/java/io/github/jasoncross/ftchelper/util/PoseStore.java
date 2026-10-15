package io.github.jasoncross.ftchelper.util;

/**
 * Passes the robot's last pose from Autonomous to TeleOp.
 *
 * <p>Both OpModes run in the same app, so a static value survives between them
 * (until the app restarts). At the end of Auto call {@link #save}; in TeleOp INIT call
 * {@link #consume}.
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
    private static FieldPose lastPose = null;

    private PoseStore() { }

    /** Saves a copy of the pose. A null pose is ignored. */
    public static synchronized void save(FieldPose pose) {
        if (pose == null) return;
        lastPose = pose.copy();
    }

    /** Returns a copy of the saved pose and keeps it saved. Null if none. */
    public static synchronized FieldPose peek() {
        return lastPose == null ? null : lastPose.copy();
    }

    /** True if a pose is saved. */
    public static synchronized boolean has() {
        return lastPose != null;
    }

    /** Returns the saved pose and clears it, so it is used only once. Null if none. */
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
