package com.crossbotics.ftchelper.auto;

import java.util.function.Predicate;

/**
 * Something to do once while a path or a {@code driveTo} runs, when the robot gets to a place along
 * it: "spin up the launcher 12 in before the end". The robot layer's {@code PathAt} makes these for
 * you ({@code PathAt.inchesLeft(12, "Spin up", robot.launcher::spinUp)}); this is the plain form
 * that {@link AutoRoutine#followPath(String, PathJob, double, java.util.List)} checks every loop.
 *
 * <ul>
 *   <li>Each event fires <b>once</b>. Every loop the routine checks the events in the order you
 *       wrote them, so two that are reached in the same loop fire in that order.</li>
 *   <li>When the path gets to its end, every event that is reached at the end fires then (a path
 *       follower may stop a little short of the end: "1 in left" still fires).</li>
 *   <li>When the path ends early (timed out, STALLED, NO_ODOMETRY, cut short), the report lists the
 *       events that did not fire: {@code To the goal: STALLED, not fired: Spin up}.</li>
 * </ul>
 *
 * Docs: docs/modules/pedro.md ("Do things along the path")
 */
public final class PathEvent {

    /** Where the robot is along the path, worked out every loop (read-only). */
    public static final class Progress {
        final double doneIn, leftIn, fraction;
        final int part;

        /**
         * @param doneIn   inches driven along the path so far
         * @param leftIn   inches left to the end
         * @param fraction how much of the path is done, 0 to 1
         * @param part     the part it drives now, from 1 (0 = unknown)
         */
        public Progress(double doneIn, double leftIn, double fraction, int part) {
            this.doneIn = doneIn;
            this.leftIn = leftIn;
            this.fraction = fraction;
            this.part = part;
        }

        /** Inches driven along the path so far. */
        public double inchesDone() {
            return doneIn;
        }

        /** Inches left to the end of the path. */
        public double inchesLeft() {
            return leftIn;
        }

        /** How much of the path is done, 0 (start) to 1 (end). */
        public double fraction() {
            return fraction;
        }

        /** The part it drives now, counting from 1 (0 when the follower does not say). */
        public int part() {
            return part;
        }
    }

    private final String name;
    private final Predicate<Progress> reached;
    private final Runnable action;

    /**
     * @param name    for the report, e.g. "Spin up"
     * @param reached true once the robot is far enough along (checked every loop until it fires)
     * @param action  what to do, once (it must not wait: start things, like a {@code run} step)
     */
    public PathEvent(String name, Predicate<Progress> reached, Runnable action) {
        if (name == null || reached == null || action == null) {
            throw new IllegalArgumentException("PathEvent: give a name, a condition and something to do");
        }
        this.name = name;
        this.reached = reached;
        this.action = action;
    }

    /** The name given, for the report. */
    public String name() {
        return name;
    }

    boolean isReached(Progress p) {
        return reached.test(p);
    }

    void fire() {
        action.run();
    }
}
