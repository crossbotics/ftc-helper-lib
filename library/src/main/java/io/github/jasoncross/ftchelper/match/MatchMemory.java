package io.github.jasoncross.ftchelper.match;

import io.github.jasoncross.ftchelper.util.FieldPose;
import io.github.jasoncross.ftchelper.util.PoseStore;

/**
 * Remembers what autonomous knew, for TeleOp: the alliance and the robot's pose.
 * With it, ONE TeleOp works for both alliances: it reads the alliance autonomous chose.
 * (The "pose" is where the robot is on the field and which way it faces.)
 *
 * <pre>{@code
 * // End of autonomous (EasyAuto does this for you):
 * MatchMemory.remember(Alliance.RED, new FieldPose(0, 0, heading));
 *
 * // TeleOp INIT:
 * Alliance alliance = MatchMemory.allianceOr(Alliance.BLUE);
 * }</pre>
 *
 * <p>The memory lasts until the robot app restarts. It is old after a few minutes (a new match),
 * so {@link #allianceOr} ignores it after {@link #MAX_AGE_SEC}.
 *
 * <p>Java notes: everything here is {@code static}, which means there is ONE shared copy for the
 * whole robot app, not one per object. That is how autonomous and TeleOp (two different OpModes)
 * see the same memory. {@code synchronized} means only one piece of code at a time may use it,
 * so two parts of the app can never read it half-written.
 *
 * Docs: docs/modules/match.md (section "MatchMemory")
 */
public final class MatchMemory {
    /** After this many seconds the memory belongs to an old match and is ignored. */
    public static final double MAX_AGE_SEC = 5 * 60;

    // null = nothing remembered.
    private static Alliance alliance = null;
    // When remember() was called, in nanoseconds (billionths of a second) on the system clock.
    private static long savedAtNanos = 0;

    // Private constructor: nobody makes a MatchMemory object; use MatchMemory.remember(...) etc.
    private MatchMemory() { }

    /** Saves the alliance and the pose (the pose goes to {@link PoseStore}). Pose may be null. */
    public static synchronized void remember(Alliance chosen, FieldPose pose) {
        alliance = chosen;
        savedAtNanos = System.nanoTime();
        if (pose != null) PoseStore.save(pose);
    }

    /** The remembered alliance if it is from this match, else {@code fallback}. */
    public static synchronized Alliance allianceOr(Alliance fallback) {
        // Nothing saved, or saved so long ago it must be from an earlier match: use the fallback,
        // so an old match's color never sneaks into a new one.
        if (alliance == null || ageSec() > MAX_AGE_SEC) return fallback;
        return alliance;
    }

    /** Seconds since {@link #remember} was called (very large if never). */
    public static synchronized double ageSec() {
        return alliance == null ? Double.MAX_VALUE : (System.nanoTime() - savedAtNanos) / 1e9;
    }

    /** Forgets everything (alliance and pose). */
    public static synchronized void clear() {
        alliance = null;
        PoseStore.clear();
    }
}
