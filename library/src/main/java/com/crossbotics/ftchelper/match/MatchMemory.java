package com.crossbotics.ftchelper.match;

import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.FieldPose;
import java.util.ArrayList;
import java.util.List;

import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.util.PoseStore;

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
    // Where the running autonomous wants the robot (null = not said), and its note.
    private static FieldPose placement = null;
    private static String placementNote = "";
    // The alliance the running program uses now (null = none said).
    private static Alliance chosen = null;
    // Autonomous's last report, and when it was saved.
    private static List<String> report = new ArrayList<>();
    private static long reportAtNanos = 0;

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

    /**
     * The alliance the program running now uses (autonomous's INIT menu, or TeleOp's), or null if none
     * said. EasyAuto and EasyTeleOp set it in INIT; the simulator turns its drivers' view to that side.
     */
    public static synchronized Alliance chosen() {
        return chosen;
    }

    /** Sets {@link #chosen()} (EasyAuto and EasyTeleOp do it for you). */
    public static synchronized void setChosen(Alliance a) {
        chosen = a;
    }

    /**
     * Where the running autonomous wants the robot placed (field frame, see {@link #fieldPose}), and its
     * note for the drive team; null if it did not say. EasyAuto sets it in INIT; the simulator puts
     * the robot there.
     */
    public static synchronized FieldPose placement() {
        return placement == null ? null : placement.copy();
    }

    /** The drive team's note for {@link #placement()} ("" if none). */
    public static synchronized String placementNote() {
        return placementNote;
    }

    /** Sets {@link #placement()} (null = none) and its note (EasyAuto does it for you). */
    public static synchronized void setPlacement(FieldPose pose, String note) {
        placement = pose == null ? null : pose.copy();
        placementNote = note == null ? "" : note;
    }

    /**
     * A start spot seen from the drivers' wall, as a field position. The drivers' frame: x inches to the
     * right of the field's centre line, y inches from the centre toward the far wall (negative = your
     * half), heading 0 = facing away from you, counterclockwise +. The field frame (the simulator's): the
     * RED drivers at the bottom (y negative), x to their right, heading 0 = facing away from them.
     *
     * @param alliance whose drivers' wall
     */
    public static FieldPose fieldPose(Alliance alliance, double x, double y, double headingDeg) {
        if (alliance == Alliance.RED) return new FieldPose(x, y, headingDeg);
        // BLUE's wall is the far one: turned half a turn.
        return new FieldPose(-x, -y, Angles.wrapDegrees(headingDeg + 180));
    }

    /**
     * Changes the alliance in use during TeleOp (the wrong one was chosen): {@link #chosen()} becomes
     * {@code a}, and autonomous's remembered alliance too, if there is one from this match (its age
     * stays), so a TeleOp started again in this match gets the corrected one. EasyTeleOp's
     * "switch alliance" buttons call it.
     */
    public static synchronized void changeAlliance(Alliance a) {
        if (a == null) return;
        chosen = a;
        if (alliance != null && ageSec() <= MAX_AGE_SEC) alliance = a;
    }

    /** True if autonomous left an alliance for this match (not older than {@link #MAX_AGE_SEC}). */
    public static synchronized boolean hasAlliance() {
        return alliance != null && ageSec() <= MAX_AGE_SEC;
    }

    /** Saves autonomous's report (one line per step), for TeleOp's INIT screen. */
    public static synchronized void rememberReport(List<String> lines) {
        report = new ArrayList<>(lines);
        reportAtNanos = System.nanoTime();
    }

    /** Autonomous's report from this match (empty if none, or if it is older than {@link #MAX_AGE_SEC}). */
    public static synchronized List<String> lastReport() {
        if (report.isEmpty() || (System.nanoTime() - reportAtNanos) / 1e9 > MAX_AGE_SEC) return new ArrayList<>();
        return new ArrayList<>(report);
    }

    /** Seconds since {@link #remember} was called (very large if never). */
    public static synchronized double ageSec() {
        return alliance == null ? Double.MAX_VALUE : (System.nanoTime() - savedAtNanos) / 1e9;
    }

    /** Forgets everything (alliance and pose). */
    public static synchronized void clear() {
        alliance = null;
        chosen = null;
        placement = null;
        placementNote = "";
        report = new ArrayList<>();
        reportAtNanos = 0;
        PoseStore.clear();
    }
}
