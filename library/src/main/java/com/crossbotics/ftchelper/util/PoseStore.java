package com.crossbotics.ftchelper.util;

import java.util.function.LongSupplier;

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
 * <p>For field-centric driving you do not need to call it yourself:
 * {@link com.crossbotics.ftchelper.drive.DriverHeading} takes the saved heading for you.
 *
 * Docs: docs/modules/util.md
 */
public final class PoseStore {
    // The saved pose, or null ("nothing saved").
    private static FieldPose lastPose = null;
    // When the pose was saved, in nanoseconds (billionths of a second).
    private static long savedAtNanos = 0;
    // Where we get the time. Tests swap in a pretend clock.
    static LongSupplier clockNanos = System::nanoTime;

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
        savedAtNanos = clockNanos.getAsLong();
    }

    /**
     * Seconds since the pose was saved. Very large if nothing is saved. A pose more than a few
     * minutes old belongs to an earlier match.
     */
    public static synchronized double ageSec() {
        return lastPose == null ? Double.MAX_VALUE : (clockNanos.getAsLong() - savedAtNanos) / 1e9;
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
