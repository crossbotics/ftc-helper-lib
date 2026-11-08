package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.vision.TargetObservation;

/**
 * For {@code waitForGoal}: true once the goal has been in view and still for a while. Still = its
 * bearing and distance stay within a few degrees and inches of where it was when this still spell
 * began. Out of view, or moved too much: the spell starts again.
 */
final class GoalSteady {
    private final double sec, deg, in;
    private boolean inSpell = false, everSeen = false;
    private double fromDeg, fromIn;
    private long fromNanos;

    GoalSteady(double sec, double deg, double in) {
        this.sec = sec;
        this.deg = deg;
        this.in = in;
    }

    /** The goal now (null = not seen) at this time; true once it has been still long enough. */
    boolean update(TargetObservation goal, long nowNanos) {
        if (goal == null) { inSpell = false; return false; }
        everSeen = true;
        boolean moved = inSpell && (Math.abs(goal.bearingDeg - fromDeg) > deg
                || (!Double.isNaN(goal.distanceIn) && !Double.isNaN(fromIn) && Math.abs(goal.distanceIn - fromIn) > in));
        if (!inSpell || moved) {
            inSpell = true;
            fromDeg = goal.bearingDeg;
            fromIn = goal.distanceIn;
            fromNanos = nowNanos;
        }
        return (nowNanos - fromNanos) / 1e9 >= sec;
    }

    /** True if the goal was in view at least once. */
    boolean everSeen() {
        return everSeen;
    }
}
