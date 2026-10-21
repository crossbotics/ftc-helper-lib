package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.match.MatchMemory;
import com.crossbotics.ftchelper.pedro.PedroFollower;
import com.crossbotics.ftchelper.util.FieldPose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two lines for the field map and the 3D view, to help plan paths:
 * <ul>
 *   <li>the plan: the path the running {@code follow} step drives (Pedro's path, sampled), and</li>
 *   <li>the trail: where the robot really went in its last 30 seconds of driving.</li>
 * </ul>
 * Where the two lines part, the robot did not drive the plan: a field element was in the way, or the
 * robot could not brake or turn in time (docs/modules/pedro.md, "The simulator").
 */
final class SimPaths {
    /** A trail point every this many seconds of driving. */
    static final double TRAIL_STEP_SEC = 0.1;
    /** How much driving the trail keeps, seconds (older points drop off). */
    static final double TRAIL_SEC = 30;
    static final int TRAIL_POINTS = (int) Math.round(TRAIL_SEC / TRAIL_STEP_SEC);
    // A new point only when the robot moved at least this far (a robot standing still adds nothing,
    // so its trail stays on the map after it stops).
    private static final double MIN_MOVE_IN = 0.25;

    // The trail in pieces: a new piece starts when the robot is put somewhere by hand (no line across).
    private final List<List<double[]>> trail = new ArrayList<>();
    private int trailCount = 0;
    private double sinceLast = TRAIL_STEP_SEC;
    private boolean newPiece = true;
    // The last plan seen (kept after its step ends, so it can be compared with the trail).
    private List<FieldPose> plan = Collections.emptyList();
    private boolean planRunning = false;
    // The follower's list the plan was made from (a new path gives a new list).
    private List<FieldPose> planSeen = null;

    /** Called every physics step with the robot's real position (inches). */
    synchronized void record(double x, double y, double dt) {
        sinceLast += dt;
        if (sinceLast < TRAIL_STEP_SEC) return;
        if (!newPiece && !trail.isEmpty()) {
            List<double[]> piece = trail.get(trail.size() - 1);
            double[] last = piece.get(piece.size() - 1);
            if (Math.hypot(x - last[0], y - last[1]) < MIN_MOVE_IN) return;
        }
        sinceLast = 0;
        if (newPiece || trail.isEmpty()) {
            trail.add(new ArrayList<double[]>());
            newPiece = false;
        }
        trail.get(trail.size() - 1).add(new double[] {x, y});
        trailCount++;
        // Too long: the oldest points go.
        while (trailCount > TRAIL_POINTS) {
            List<double[]> first = trail.get(0);
            first.remove(0);
            trailCount--;
            if (first.isEmpty()) trail.remove(0);
        }
    }

    /** The robot was put somewhere by hand: the trail goes on as a new piece (no line across). */
    synchronized void jumped() {
        newPiece = true;
        sinceLast = TRAIL_STEP_SEC;
    }

    /** The clear button: no trail, no plan (a plan still running comes back next loop). */
    synchronized void clear() {
        trail.clear();
        trailCount = 0;
        newPiece = true;
        plan = Collections.emptyList();
        planSeen = null;
    }

    /** The trail now: pieces of points {x, y}, oldest first. */
    synchronized List<List<double[]>> trail() {
        List<List<double[]>> copy = new ArrayList<>();
        for (List<double[]> piece : trail) copy.add(new ArrayList<>(piece));
        return copy;
    }

    /** How many points the trail has. */
    synchronized int trailPoints() {
        return trailCount;
    }

    /**
     * The plan: the running path's points (field frame, as driven: mirrored or turned for the alliance
     * the program runs for), or the last one after it ended. Empty when no path ran yet.
     */
    synchronized List<FieldPose> plan() {
        PedroFollower f = PedroFollower.lastStarted();
        List<FieldPose> now = f == null ? Collections.<FieldPose>emptyList() : f.plannedPoints();
        planRunning = !now.isEmpty();
        if (planRunning && now != planSeen) {
            planSeen = now;
            // The path is in the drivers' frame (seen from their wall); the map is in the field frame.
            Alliance a = MatchMemory.chosen();
            List<FieldPose> onField = new ArrayList<>(now.size());
            for (FieldPose p : now) onField.add(MatchMemory.fieldPose(a == null ? Alliance.RED : a, p.x, p.y, p.headingDeg));
            plan = onField;
        }
        return plan;
    }

    /** True while the plan's step is still driving (or holding its end). Call after {@link #plan()}. */
    synchronized boolean planRunning() {
        return planRunning;
    }
}
