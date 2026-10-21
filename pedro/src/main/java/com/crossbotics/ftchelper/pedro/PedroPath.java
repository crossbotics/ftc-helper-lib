package com.crossbotics.ftchelper.pedro;

import com.crossbotics.ftchelper.util.FieldPose;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.AtomicPath;
import com.pedropathing.paths.Path;
import com.pedropathing.paths.curves.Curve;
import com.pedropathing.paths.curves.Line;
import com.pedropathing.paths.curves.bezier.BezierCurve;

/**
 * A path in our frame (x right, y away, inches; heading degrees, 0 = away, counterclockwise +), the
 * same numbers as {@code driveTo}. Give it to {@code Steps.follow(...)}:
 *
 * <pre>
 * PedroPath.line(-56, -61, -30, -30).turnTo(0, 45)        // straight, turning from 0 to 45 on the way
 * PedroPath.curve(-30, -30,  -50, -20,  -56, -36).facePath()  // curved through (-50, -20), facing along it
 * </pre>
 *
 * Written once, for the alliance whose wall the numbers are measured from. With mirroring on
 * ({@code mirrorFor(RED)}), x and headings flip for the other one, like every other step.
 */
public final class PedroPath {
    private enum Heading { LINEAR, CONSTANT, TANGENT, REVERSE_TANGENT, FACE_POINT }

    private final double[] xs, ys;   // the points: start, control points, end
    private Heading heading = Heading.TANGENT;
    private double h1, h2;

    private PedroPath(double[] xs, double[] ys) {
        this.xs = xs;
        this.ys = ys;
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

    /** Turns from {@code startDeg} to {@code endDeg} evenly along the path. */
    public PedroPath turnTo(double startDeg, double endDeg) {
        heading = Heading.LINEAR;
        h1 = startDeg;
        h2 = endDeg;
        return this;
    }

    /** Keeps one heading the whole way (a mecanum robot slides sideways as needed). */
    public PedroPath keepHeading(double deg) {
        heading = Heading.CONSTANT;
        h1 = deg;
        return this;
    }

    /** Faces along the path, front first (the default). */
    public PedroPath facePath() {
        heading = Heading.TANGENT;
        return this;
    }

    /** Faces along the path, back first (drives backwards). */
    public PedroPath facePathBackwards() {
        heading = Heading.REVERSE_TANGENT;
        return this;
    }

    /** Keeps facing a field point (x, y) the whole way, e.g. the goal. */
    public PedroPath facePoint(double x, double y) {
        heading = Heading.FACE_POINT;
        h1 = x;
        h2 = y;
        return this;
    }

    /** Where the path ends, our frame (the heading is the end heading, or NaN when it faces the path). */
    public FieldPose end() {
        int n = xs.length - 1;
        double h = heading == Heading.LINEAR ? h2 : heading == Heading.CONSTANT ? h1 : Double.NaN;   // NaN: it depends on the path
        return new FieldPose(xs[n], ys[n], h);
    }

    /** Pedro's path, mirrored (x and headings flipped) when {@code mirrorX}. */
    public Path build(boolean mirrorX) {
        double s = mirrorX ? -1 : 1;
        Pose[] pts = new Pose[xs.length];
        for (int i = 0; i < xs.length; i++) pts[i] = new Pose(s * xs[i], ys[i]);
        Curve curve = pts.length == 2 ? new Line(pts[0], pts[1]) : new BezierCurve(pts);
        Path p = new AtomicPath(curve);
        switch (heading) {
            case LINEAR: return p.linear(PedroFrame.headingToPedro(s * h1), PedroFrame.headingToPedro(s * h2));
            case CONSTANT: return p.constant(PedroFrame.headingToPedro(s * h1));
            case REVERSE_TANGENT: return p.reverseTangent();
            case FACE_POINT: return p.facingPoint(new Pose(s * h1, h2));
            default: return p.tangent();
        }
    }
}
