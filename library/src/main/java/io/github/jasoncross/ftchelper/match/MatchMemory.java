package io.github.jasoncross.ftchelper.match;

import io.github.jasoncross.ftchelper.util.FieldPose;
import io.github.jasoncross.ftchelper.util.PoseStore;

/**
 * Remembers what autonomous knew, for TeleOp: the alliance and the robot's pose.
 * With it, ONE TeleOp works for both alliances: it reads the alliance autonomous chose.
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
 * Docs: docs/modules/match.md
 */
public final class MatchMemory {
    /** After this many seconds the memory belongs to an old match and is ignored. */
    public static final double MAX_AGE_SEC = 5 * 60;

    private static Alliance alliance = null;
    private static long savedAtNanos = 0;

    private MatchMemory() { }

    /** Saves the alliance and the pose (the pose goes to {@link PoseStore}). Pose may be null. */
    public static synchronized void remember(Alliance chosen, FieldPose pose) {
        alliance = chosen;
        savedAtNanos = System.nanoTime();
        if (pose != null) PoseStore.save(pose);
    }

    /** The remembered alliance if it is from this match, else {@code fallback}. */
    public static synchronized Alliance allianceOr(Alliance fallback) {
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
