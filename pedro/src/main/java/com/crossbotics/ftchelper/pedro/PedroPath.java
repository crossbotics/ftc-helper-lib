package com.crossbotics.ftchelper.pedro;

import com.crossbotics.ftchelper.util.FieldPose;
import com.pedropathing.config.Modifier;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.AtomicPath;
import com.pedropathing.paths.CompoundPath;
import com.pedropathing.paths.Path;
import com.pedropathing.paths.curves.Curve;
import com.pedropathing.paths.curves.Line;
import com.pedropathing.paths.curves.bezier.BezierCurve;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * A path in our frame (x right, y away, inches; heading degrees, 0 = away, counterclockwise +), the
 * same numbers as {@code driveTo}. Give it to {@code Steps.follow(...)}:
 *
 * <pre>
 * PedroPath.line(-56, -61, -30, -30).turnTo(0, 45)            // straight, turning from 0 to 45 on the way
 * PedroPath.curve(-30, -30,  -50, -24,  -56, -36).facePath()  // curved toward (-50, -24), facing along it
 * PedroPath.line(-56, -61, -40, -40).then(PedroPath.line(-40, -40, -30, -30)).holdEnd()   // two parts, no stop
 * </pre>
 *
 * A path can have several parts ({@link #then}): the robot drives them with no stop between them. The
 * heading and speed settings apply to the LAST part added so far; {@link #holdEnd()} to the whole path.
 *
 * <p>A PedroPath never changes: every method gives back a new path. So a {@code static final} path is
 * safe to build on, e.g. {@code TO_GOAL.then(...)} inside {@code steps()} leaves {@code TO_GOAL} as it was.
 *
 * <p>Written once, for the alliance whose wall the numbers are measured from. With mirroring on
 * ({@code mirrorFor(RED)}), x and headings flip for the other one, like every other step.
 */
public final class PedroPath {
    private enum Heading { LINEAR, CONSTANT, TANGENT, REVERSE_TANGENT, FACE_POINT }

    // One part: its points (start, control points, end), heading and speed.
    private static final class Part {
        final double[] xs, ys;
        Heading heading = Heading.TANGENT;
        double h1, h2;
        double maxSpeed = Double.NaN;   // NaN: Pedro's own setting

        Part(double[] xs, double[] ys) {
            this.xs = xs;
            this.ys = ys;
        }

        Part copy() {
            Part p = new Part(xs, ys);
            p.heading = heading;
            p.h1 = h1;
            p.h2 = h2;
            p.maxSpeed = maxSpeed;
            return p;
        }
    }

    /** How long a hold lasts at most, unless you say ({@link #holdEnd(double)}). */
    public static final double DEFAULT_HOLD_SEC = 5;

    private final List<Part> parts = new ArrayList<>();
    private double holdSec = 0;   // 0: no hold

    private PedroPath(double[] xs, double[] ys) {
        parts.add(new Part(xs, ys));
    }

    private PedroPath(PedroPath from) {
        for (Part p : from.parts) parts.add(p.copy());
        holdSec = from.holdSec;
    }

    /** A straight line from (x1, y1) to (x2, y2). It faces along the line unless you say otherwise. */
    public static PedroPath line(double x1, double y1, double x2, double y2) {
        return new PedroPath(new double[]{x1, x2}, new double[]{y1, y2});
    }

    /** A straight line between two poses, turning from the first heading to the second on the way. */
    public static PedroPath line(FieldPose from, FieldPose to) {
        return line(from.x, from.y, to.x, to.y).turnTo(from.headingDeg, to.headingDeg);
    }

    /**
     * A smooth curve (Bezier): start x, y, then one or more control points, then end x, y. The robot
     * does not drive through the control points: they pull the curve toward them.
     */
    public static PedroPath curve(double... xy) {
        if (xy.length < 6 || xy.length % 2 != 0) {
            throw new IllegalArgumentException("PedroPath.curve: give x, y pairs: start, at least one control point, end");
        }
        double[] xs = new double[xy.length / 2], ys = new double[xy.length / 2];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = xy[2 * i];
            ys[i] = xy[2 * i + 1];
        }
        return new PedroPath(xs, ys);
    }

    private Part last() {
        return parts.get(parts.size() - 1);
    }

    /** Turns from {@code startDeg} to {@code endDeg} evenly along the (last) part. */
    public PedroPath turnTo(double startDeg, double endDeg) {
        PedroPath c = new PedroPath(this);
        c.last().heading = Heading.LINEAR;
        c.last().h1 = startDeg;
        c.last().h2 = endDeg;
        return c;
    }

    /** Keeps one heading the whole (last) part (a mecanum robot slides sideways as needed). */
    public PedroPath keepHeading(double deg) {
        PedroPath c = new PedroPath(this);
        c.last().heading = Heading.CONSTANT;
        c.last().h1 = deg;
        return c;
    }

    /** Faces along the path, front first (the default). */
    public PedroPath facePath() {
        PedroPath c = new PedroPath(this);
        c.last().heading = Heading.TANGENT;
        return c;
    }

    /** Faces along the path, back first (drives backwards). */
    public PedroPath facePathBackwards() {
        PedroPath c = new PedroPath(this);
        c.last().heading = Heading.REVERSE_TANGENT;
        return c;
    }

    /** Keeps facing a field point (x, y) the whole (last) part, e.g. the goal. */
    public PedroPath facePoint(double x, double y) {
        PedroPath c = new PedroPath(this);
        c.last().heading = Heading.FACE_POINT;
        c.last().h1 = x;
        c.last().h2 = y;
        return c;
    }

    /**
     * At most this part of the robot's top speed on the (last) part: 0.5 = half (Pedro's
     * {@code maxPathSpeed}). For a careful part, e.g. near a field element. The limit starts where the
     * part starts: the robot brakes inside the part, so give a slow part some room.
     */
    public PedroPath maxSpeed(double fraction) {
        if (!(fraction > 0 && fraction <= 1)) {
            throw new IllegalArgumentException("PedroPath.maxSpeed: give a part of the top speed, more than 0 and at most 1 (not " + fraction + ")");
        }
        PedroPath c = new PedroPath(this);
        c.last().maxSpeed = fraction;
        return c;
    }

    /**
     * Adds the parts of {@code next} after this path's: the robot drives on with no stop between them.
     * Start {@code next} where this path ends, at the heading this path ends with. Settings after
     * {@code then} apply to {@code next}'s last part. If {@code next} holds its end, so does this path.
     */
    public PedroPath then(PedroPath next) {
        if (next == null) throw new IllegalArgumentException("PedroPath.then: give another path");
        Part end = last(), start = next.parts.get(0);
        double gap = Math.hypot(start.xs[0] - end.xs[end.xs.length - 1], start.ys[0] - end.ys[end.ys.length - 1]);
        if (gap > 0.5) {
            throw new IllegalArgumentException(String.format(java.util.Locale.US,
                    "PedroPath.then: the next part starts at (%.1f, %.1f), but this path ends at (%.1f, %.1f): start it where this one ends",
                    start.xs[0], start.ys[0], end.xs[end.xs.length - 1], end.ys[end.ys.length - 1]));
        }
        PedroPath c = new PedroPath(this);
        for (Part p : next.parts) c.parts.add(p.copy());
        if (next.holdSec > 0) c.holdSec = next.holdSec;
        return c;
    }

    /**
     * At the end, keep holding the end point (Pedro pushes back if the robot is bumped). The step still
     * ends when the robot gets there; the hold goes on until the next driving step, the next path, the
     * end of the OpMode, or 5 s ({@link #DEFAULT_HOLD_SEC}). Use it before a step that needs the robot
     * still, e.g. scoring.
     */
    public PedroPath holdEnd() {
        return holdEnd(DEFAULT_HOLD_SEC);
    }

    /** {@link #holdEnd()} for at most {@code seconds} (a wedged robot does not push for the rest of autonomous). */
    public PedroPath holdEnd(double seconds) {
        if (!(seconds > 0)) throw new IllegalArgumentException("PedroPath.holdEnd: give more than 0 seconds");
        PedroPath c = new PedroPath(this);
        c.holdSec = seconds;
        return c;
    }

    /** True if the path holds its end point ({@link #holdEnd()}). */
    public boolean holdsEnd() {
        return holdSec > 0;
    }

    /** How long the hold lasts at most, seconds (0: no hold). */
    public double holdSeconds() {
        return holdSec;
    }

    /** How many parts the path has. */
    public int parts() {
        return parts.size();
    }

    /** Where the path ends, our frame (the heading is the end heading, or NaN when it depends on the path). */
    public FieldPose end() {
        Part p = last();
        int n = p.xs.length - 1;
        double h = p.heading == Heading.LINEAR ? p.h2 : p.heading == Heading.CONSTANT ? p.h1 : Double.NaN;
        return new FieldPose(p.xs[n], p.ys[n], h);
    }

    /** True if a part has its own speed limit ({@link #maxSpeed}). */
    public boolean hasSpeedLimit() {
        for (Part p : parts) if (!Double.isNaN(p.maxSpeed)) return true;
        return false;
    }

    /** Pedro's path, mirrored (x and headings flipped) when {@code mirrorX}. No speed limits. */
    public Path build(boolean mirrorX) {
        return build(mirrorX, null);
    }

    /**
     * Pedro's path, mirrored when {@code mirrorX}. {@code speedLimit} turns a part's max speed into
     * Pedro's modifier (the follower knows its config); null ignores the speed limits.
     */
    public Path build(boolean mirrorX, DoubleFunction<Modifier> speedLimit) {
        Path[] built = new Path[parts.size()];
        for (int i = 0; i < built.length; i++) {
            Part part = parts.get(i);
            Path p = build(part, mirrorX);
            if (speedLimit != null && !Double.isNaN(part.maxSpeed)) p = p.with(speedLimit.apply(part.maxSpeed));
            built[i] = p;
        }
        return built.length == 1 ? built[0] : new CompoundPath(built);
    }

    private static Path build(Part part, boolean mirrorX) {
        double s = mirrorX ? -1 : 1;
        Pose[] pts = new Pose[part.xs.length];
        for (int i = 0; i < pts.length; i++) pts[i] = new Pose(s * part.xs[i], part.ys[i]);
        Curve curve = pts.length == 2 ? new Line(pts[0], pts[1]) : new BezierCurve(pts);
        Path p = new AtomicPath(curve);
        switch (part.heading) {
            case LINEAR: return p.linear(PedroFrame.headingToPedro(s * part.h1), PedroFrame.headingToPedro(s * part.h2));
            case CONSTANT: return p.constant(PedroFrame.headingToPedro(s * part.h1));
            case REVERSE_TANGENT: return p.reverseTangent();
            case FACE_POINT: return p.facingPoint(new Pose(s * part.h1, part.h2));
            default: return p.tangent();
        }
    }
}
