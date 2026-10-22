package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.auto.PathEvent;
import com.crossbotics.ftchelper.util.FieldPose;
import java.util.function.Predicate;

/**
 * Something to start at a place ALONG a path, while the robot keeps driving: spin up the launcher
 * 12 in before the end, start the intake halfway, open a gate where part 2 of the path starts. Give
 * them to a {@code follow} (or {@code driveTo}) step after the path:
 *
 * <pre>{@code
 * s.follow("To the goal", TO_GOAL,
 *          PathAt.fraction(0.5, "Intake on", robot.intake::in),              // halfway
 *          PathAt.inchesLeft(12, "Spin up", () -> robot.launcher.spinUp()),   // 12 in before the end
 *          PathAt.part(2, Actions.openGate(robot)));                         // an action from Actions.java
 * }</pre>
 *
 * <ul>
 *   <li>Each one happens <b>once</b>, and the path goes on: like a {@code run} step, it STARTS things.
 *       An action keeps running after the step, like {@code alongside}.</li>
 *   <li>They are checked every loop in the order you write them. Write them in the order they come
 *       along the path.</li>
 *   <li>At the end of the path, the ones reached there happen (Pedro may stop a little short of the
 *       end, so "1 in left" still happens).</li>
 *   <li>If the path ends early (timed out, STALLED, NO_ODOMETRY, cut short), the report says which did
 *       not happen: {@code To the goal: STALLED, not fired: Spin up}.</li>
 *   <li>Mirrored for RED like the path: a distance along the path is the same on both sides, and
 *       {@link #near}'s point is mirrored like {@code driveTo}'s.</li>
 * </ul>
 *
 * <p>A mistake (a fraction of 2, a part 0) stops INIT with a message, not the match.
 *
 * Docs: docs/modules/pedro.md ("Do things along the path")
 */
public final class PathAt {
    private enum Kind { INCHES_DONE, INCHES_LEFT, FRACTION, PART, NEAR }
    // The method that makes each kind, for messages.
    private static final String[] METHOD = {"inchesDone", "inchesLeft", "fraction", "part", "near"};

    private final Kind kind;
    private final double value, x, y;
    private final String label;
    private final Runnable command;   // one of these two
    private final Action action;

    private PathAt(Kind kind, double value, double x, double y, String label, Runnable command, Action action) {
        this.kind = kind;
        this.value = value;
        this.x = x;
        this.y = y;
        this.label = label;
        this.command = command;
        this.action = action;
    }

    // Checks the "what to do" half, so a mistake stops INIT with a clear message.
    private static PathAt make(Kind kind, double value, double x, double y, String label, Runnable command, Action action) {
        String what = "PathAt." + METHOD[kind.ordinal()];
        if (action == null && command == null) throw new IllegalArgumentException(what + ": give something to do (it is null)");
        if (action == null && (label == null || label.trim().isEmpty())) {
            throw new IllegalArgumentException(what + ": give a name for the report, e.g. \"Spin up\"");
        }
        return new PathAt(kind, value, x, y, action != null ? action.name() : label, command, action);
    }

    private static double inches(String what, double in) {
        if (!(in >= 0) || Double.isInfinite(in)) {
            throw new IllegalArgumentException("PathAt." + what + ": give 0 inches or more (not " + in + ")");
        }
        return in;
    }

    private static double fractionOf(double f) {
        if (!(f >= 0 && f <= 1)) {
            throw new IllegalArgumentException("PathAt.fraction: give a part of the path from 0 to 1, e.g. 0.5 = halfway (not " + f + ")");
        }
        return f;
    }

    private static int partNumber(int n) {
        if (n < 1) throw new IllegalArgumentException("PathAt.part: parts count from 1 (not " + n + ")");
        return n;
    }

    private static double radius(double r) {
        if (!(r > 0) || Double.isInfinite(r)) {
            throw new IllegalArgumentException("PathAt.near: give a distance more than 0 inches (not " + r + ")");
        }
        return r;
    }

    // ----------------------------- along the path -----------------------------

    /**
     * When the robot has driven this far along the path.
     *
     * @param inches  inches from the start of the path (0 = at once)
     * @param label   a name for the report, e.g. "Intake on"
     * @param command what to do, e.g. {@code robot.intake::in}
     */
    public static PathAt inchesDone(double inches, String label, Runnable command) {
        return make(Kind.INCHES_DONE, inches("inchesDone", inches), 0, 0, label, command, null);
    }

    /** {@link #inchesDone(double, String, Runnable)} with an action from Actions.java (it keeps running after the step). */
    public static PathAt inchesDone(double inches, Action action) {
        return make(Kind.INCHES_DONE, inches("inchesDone", inches), 0, 0, null, null, action);
    }

    /**
     * When only this much of the path is left: {@code inchesLeft(12, ...)} = 12 in before the end.
     *
     * @param inches  inches before the end of the path (the whole path, all its parts)
     * @param label   a name for the report, e.g. "Spin up"
     * @param command what to do, e.g. {@code () -> robot.launcher.spinUp()}
     */
    public static PathAt inchesLeft(double inches, String label, Runnable command) {
        return make(Kind.INCHES_LEFT, inches("inchesLeft", inches), 0, 0, label, command, null);
    }

    /** {@link #inchesLeft(double, String, Runnable)} with an action from Actions.java. */
    public static PathAt inchesLeft(double inches, Action action) {
        return make(Kind.INCHES_LEFT, inches("inchesLeft", inches), 0, 0, null, null, action);
    }

    /**
     * When this much of the path is done: 0.5 = halfway, 0.25 = a quarter of the way.
     *
     * @param fraction 0 (the start) to 1 (the end)
     * @param label    a name for the report
     * @param command  what to do
     */
    public static PathAt fraction(double fraction, String label, Runnable command) {
        return make(Kind.FRACTION, fractionOf(fraction), 0, 0, label, command, null);
    }

    /** {@link #fraction(double, String, Runnable)} with an action from Actions.java. */
    public static PathAt fraction(double fraction, Action action) {
        return make(Kind.FRACTION, fractionOf(fraction), 0, 0, null, null, action);
    }

    /**
     * When part {@code n} of a path made with {@code then(...)} starts (part 1 = the start). Pedro looks
     * ahead: it starts steering along the next part a little before the robot gets to the join.
     *
     * @param n       the part, counting from 1 (INIT stops if the path has fewer parts)
     * @param label   a name for the report
     * @param command what to do
     */
    public static PathAt part(int n, String label, Runnable command) {
        return make(Kind.PART, partNumber(n), 0, 0, label, command, null);
    }

    /** {@link #part(int, String, Runnable)} with an action from Actions.java. */
    public static PathAt part(int n, Action action) {
        return make(Kind.PART, partNumber(n), 0, 0, null, null, action);
    }

    // ----------------------------- near a field point -----------------------------

    /**
     * When the robot (its centre, from the odometry) comes within {@code withinIn} of a field point.
     * The point is in the same numbers as {@code driveTo}, written for BLUE and mirrored for RED. If
     * the robot never comes that close, it does not happen (the report says so).
     *
     * @param xIn      inches right of the centre line
     * @param yIn      inches from the centre toward the far wall
     * @param withinIn how close, inches (more than 0; the robot's own path may pass a few inches off)
     * @param label    a name for the report
     * @param command  what to do
     */
    public static PathAt near(double xIn, double yIn, double withinIn, String label, Runnable command) {
        return make(Kind.NEAR, radius(withinIn), xIn, yIn, label, command, null);
    }

    /** {@link #near(double, double, double, String, Runnable)} with an action from Actions.java. */
    public static PathAt near(double xIn, double yIn, double withinIn, Action action) {
        return make(Kind.NEAR, radius(withinIn), xIn, yIn, null, null, action);
    }

    // ----------------------------- for Steps -----------------------------

    /** The name shown in the report (the label, or the action's name). */
    public String name() {
        return label;
    }

    // The part number of a part(n), 0 for the others (Steps checks it against the path at INIT).
    int partNumber() {
        return kind == Kind.PART ? (int) value : 0;
    }

    // The plain event the routine checks every loop. mirrorX: RED with mirroring (near's x flips).
    PathEvent event(final RobotDefinition robot, boolean mirrorX) {
        Runnable doIt = action == null ? command : () -> robot.run(action);
        final double v = value;
        Predicate<PathEvent.Progress> when;
        switch (kind) {
            case INCHES_DONE: when = p -> p.inchesDone() >= v; break;
            case INCHES_LEFT: when = p -> p.inchesLeft() <= v; break;
            case FRACTION: when = p -> p.fraction() >= v; break;
            // part 1 is the start, even when the follower does not say which part it drives.
            case PART: when = p -> v <= 1 || p.part() >= v; break;
            default: {
                final double px = mirrorX ? -x : x, py = y;
                when = p -> {
                    FieldPose at = robot.pose();
                    return at != null && Math.hypot(at.x - px, at.y - py) <= v;
                };
            }
        }
        return new PathEvent(label, when, doIt);
    }
}
