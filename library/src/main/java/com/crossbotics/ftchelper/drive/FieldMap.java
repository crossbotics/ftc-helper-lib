package com.crossbotics.ftchelper.drive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The places on the field the robot must not drive through, and how to get around them: your own
 * "keep-out" boxes, in {@code Robot.java}. The library knows no game, so the numbers are yours.
 *
 * <pre>{@code
 * fieldMap(FieldMap.margin(13)
 *         .keepOut("Left post", -30, 10, -20, 20)    // two opposite corners, inches
 *         .keepOut("Right post", 20, 10, 30, 20)
 *         .safePoint(0, -40));                       // optional: a good place to pass through
 * }</pre>
 *
 * <p>The numbers are like {@code driveTo}'s: written for BLUE, seen from your drivers' wall, x inches
 * right of the centre line, y inches from the centre toward the far wall.
 *
 * <p>How a route is found: first the straight line. If it crosses a box, the robot goes around the
 * boxes' corners and your safe points, by the shortest way of straight lines. It uses only where the
 * odometry says the robot is. It cannot see anything: another robot is not on this map, and a wrong
 * pose means a wrong route.
 *
 * <p>The margin: how far the robot's CENTRE must stay from a box. Half the robot's longest corner to
 * corner size, plus some for odometry drift. An 18 x 18 in robot: 13 in. A spot you choose inside a
 * margin is allowed (a place right next to a box): the robot drives to just outside the margin, then
 * straight in. A spot on the box itself is not.
 *
 * Docs: docs/modules/odometry.md, "Keep-out boxes and routes"
 */
public final class FieldMap {
    // A route's corner points stand this far outside the grown box. A robot counts a point on the way as
    // reached up to RouteDriver.PASS_POINT_IN early, and cuts the corner from there: this keeps that cut
    // outside the margin.
    private static final double CLEAR_IN = RouteDriver.PASS_POINT_IN + 0.5;

    private final double marginIn;
    private double halfFieldIn = 72;
    private final List<String> names = new ArrayList<>();
    // Each box as {minX, minY, maxX, maxY}: the size you gave, and with the margin added on every side.
    private final List<double[]> boxes = new ArrayList<>();
    private final List<double[]> grown = new ArrayList<>();
    private final List<double[]> safePoints = new ArrayList<>();

    private FieldMap(double marginIn) {
        this.marginIn = marginIn;
    }

    /**
     * Starts a map.
     *
     * @param inches how far the robot's centre stays from every box, 0 or more (half its longest corner
     *               to corner size, plus some for drift)
     */
    public static FieldMap margin(double inches) {
        if (!(inches >= 0) || Double.isInfinite(inches)) {
            throw new IllegalArgumentException("FieldMap.margin(" + inches + "): the margin must be 0 or more inches");
        }
        return new FieldMap(inches);
    }

    /**
     * Adds a box the robot must not drive through.
     *
     * @param name a name for messages, e.g. "Left post"
     * @param x1   one corner: inches right of the centre line
     * @param y1   one corner: inches from the centre toward the far wall
     * @param x2   the opposite corner: inches right
     * @param y2   the opposite corner: inches toward the far wall
     */
    public FieldMap keepOut(String name, double x1, double y1, double x2, double y2) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("FieldMap.keepOut: the name is empty");
        checkNumbers("keepOut(\"" + name + "\")", x1, y1, x2, y2);
        double[] box = {Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2)};
        names.add(name);
        boxes.add(box);
        grown.add(new double[] {box[0] - marginIn, box[1] - marginIn, box[2] + marginIn, box[3] + marginIn});
        for (double[] s : safePoints) checkSafePoint(s[0], s[1]);
        return this;
    }

    /**
     * Adds a place a route may pass through (optional: the boxes' corners are used without this). Use
     * one to make routes go your way, e.g. through a gap you trust. A gap less than 2.5 in wide (after the
     * margins) needs one at each end, because the boxes' corners do not fit in it. It must be at least 0.5 in
     * outside every margin, and inside the walls by the margin.
     *
     * @param x inches right of the centre line
     * @param y inches from the centre toward the far wall
     */
    public FieldMap safePoint(double x, double y) {
        checkNumbers("safePoint", x, y);
        checkSafePoint(x, y);
        safePoints.add(new double[] {x, y});
        return this;
    }

    /**
     * The field's size. Default 144 in, an FTC field.
     *
     * @param inches one side of the square field
     */
    public FieldMap fieldSize(double inches) {
        if (!(inches > 0) || Double.isInfinite(inches)) {
            throw new IllegalArgumentException("FieldMap.fieldSize(" + inches + "): the size must be more than 0 inches");
        }
        halfFieldIn = inches / 2;
        for (double[] s : safePoints) checkSafePoint(s[0], s[1]);
        return this;
    }

    /** How far the robot's centre stays from every box, inches. */
    public double marginIn() {
        return marginIn;
    }

    /** How many boxes the map has. */
    public int boxCount() {
        return boxes.size();
    }

    /**
     * Why a route cannot end at this spot: on a box, boxed in, or off the field. A spot inside a margin
     * is fine if the robot can get to it from outside.
     *
     * @param x inches right of the centre line
     * @param y inches from the centre toward the far wall
     * @return the reason, or "" if the spot is good
     */
    public String whyBadSpot(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y) || Math.abs(x) > halfFieldIn || Math.abs(y) > halfFieldIn) {
            return "x " + x + ", y " + y + " is off the field";
        }
        for (int i = 0; i < boxes.size(); i++) {
            if (inside(boxes.get(i), x, y)) return "x " + x + ", y " + y + " is on \"" + names.get(i) + "\"";
        }
        int box = boxAt(x, y);
        if (box >= 0 && wayOut(x, y) == null) {
            return "x " + x + ", y " + y + " is within the " + marginIn + " in margin of \"" + names.get(box)
                    + "\", and a robot cannot drive in from outside";
        }
        return "";
    }

    /**
     * Finds the shortest route of straight lines that misses every box. Quick: it is safe to call in
     * the loop once, when the robot sets off. If the robot stands within a box's margin (or inside it,
     * from a wrong pose), the route's first point takes it out by the nearest side. A spot inside a
     * margin is reached the same way backwards: the last line goes straight in from just outside.
     *
     * @param fromX where the robot is: inches right
     * @param fromY where the robot is: inches toward the far wall
     * @param toX   where to go: inches right
     * @param toY   where to go: inches toward the far wall
     * @return the points to drive to, in order, the last one being (toX, toY); each is {x, y}. Empty if
     *         there is no route, so the robot must not set off.
     */
    public List<double[]> route(double fromX, double fromY, double toX, double toY) {
        if (!whyBadSpot(toX, toY).isEmpty()) return Collections.emptyList();
        List<double[]> out = new ArrayList<>();
        double[] goal = {toX, toY};
        // Already there: no trip out of a margin and back for this (but never across a box).
        if (Math.hypot(toX - fromX, toY - fromY) <= RouteDriver.PASS_POINT_IN && !crossesABox(fromX, fromY, toX, toY)) {
            out.add(goal);
            return out;
        }
        double[] start = {fromX, fromY};
        // Inside a margin: first out by the nearest side, then plan from there.
        if (boxAt(fromX, fromY) >= 0) {
            start = wayOut(fromX, fromY);
            if (start == null) return Collections.emptyList();
            out.add(start);
        }
        // A spot inside a margin: plan to just outside it. (whyBadSpot said there is such a place.)
        double[] end = boxAt(toX, toY) >= 0 ? wayOut(toX, toY) : goal;
        if (isClear(start, end)) {
            out.add(end);
            if (end != goal) out.add(goal);
            return out;
        }
        List<double[]> points = new ArrayList<>();
        points.add(start);
        points.add(end);
        addCorners(points);
        for (double[] s : safePoints) points.add(s);
        List<double[]> middle = shortest(points);
        if (middle == null) return Collections.emptyList();
        out.addAll(middle);
        if (end != goal) out.add(goal);
        return out;
    }

    /**
     * True if the robot's centre may drive straight from one place to another: the line misses every box
     * with its margin. A box whose margin holds the END is checked without its margin, because a route's
     * last line goes straight in to a spot next to a box. {@link RouteDriver#checkLines} uses this.
     *
     * @param fromX from: inches right of the centre line
     * @param fromY from: inches toward the far wall
     * @param toX   to: inches right
     * @param toY   to: inches toward the far wall
     */
    public boolean lineIsClear(double fromX, double fromY, double toX, double toY) {
        double[] a = {fromX, fromY}, b = {toX, toY};
        for (int i = 0; i < grown.size(); i++) {
            double[] g = grown.get(i);
            if (lineCrosses(a, b, inside(g, toX, toY) ? boxes.get(i) : g)) return false;
        }
        return true;
    }

    // True if the line goes through a box itself (the margins not counted).
    private boolean crossesABox(double fromX, double fromY, double toX, double toY) {
        double[] a = {fromX, fromY}, b = {toX, toY};
        for (double[] box : boxes) if (lineCrosses(a, b, box)) return true;
        return false;
    }

    // The index of the box whose margin holds this point, or -1.
    private int boxAt(double x, double y) {
        for (int i = 0; i < grown.size(); i++) {
            if (inside(grown.get(i), x, y)) return i;
        }
        return -1;
    }

    private static boolean inside(double[] box, double x, double y) {
        return x > box[0] && x < box[2] && y > box[1] && y < box[3];
    }

    // The nearest point just outside the margin that holds (x, y), that the robot's centre can reach
    // (inside the walls by the margin, in no other margin, and with a clear line from here). Null if
    // there is none.
    private double[] wayOut(double x, double y) {
        double[] g = grown.get(boxAt(x, y));
        double[][] sides = {
            {g[0] - CLEAR_IN, y}, {g[2] + CLEAR_IN, y}, {x, g[1] - CLEAR_IN}, {x, g[3] + CLEAR_IN},
        };
        Arrays.sort(sides, (a, b) -> Double.compare(Math.hypot(a[0] - x, a[1] - y), Math.hypot(b[0] - x, b[1] - y)));
        double limit = wallLimit();
        for (double[] s : sides) {
            // Only the way it moves counts: a robot parked closer to a wall than the margin may still leave along it.
            boolean wallsOk = (Math.abs(s[0]) <= limit || s[0] == x) && (Math.abs(s[1]) <= limit || s[1] == y);
            if (wallsOk && boxAt(s[0], s[1]) < 0 && wayOutIsClear(x, y, s)) return s;
        }
        return null;
    }

    // The line out of a margin. A box it stands in the margin of: it may cross that margin, not the box.
    // Any other box: not even the margin. (A wrong pose inside a box: out is the only way to go.)
    private boolean wayOutIsClear(double x, double y, double[] to) {
        double[] from = {x, y};
        for (int i = 0; i < boxes.size(); i++) {
            if (inside(boxes.get(i), x, y)) continue;
            if (lineCrosses(from, to, inside(grown.get(i), x, y) ? boxes.get(i) : grown.get(i))) return false;
        }
        return true;
    }

    // How far from the centre the robot's centre may plan to go: a point closer to a wall puts the robot in it.
    private double wallLimit() {
        return Math.max(0, halfFieldIn - marginIn);
    }

    // Every grown box's four corners that the robot's centre can reach: inside the walls by the margin,
    // and in no other box.
    private void addCorners(List<double[]> points) {
        double limit = wallLimit();
        for (double[] g : grown) {
            double[][] corners = {
                {g[0] - CLEAR_IN, g[1] - CLEAR_IN}, {g[2] + CLEAR_IN, g[1] - CLEAR_IN},
                {g[0] - CLEAR_IN, g[3] + CLEAR_IN}, {g[2] + CLEAR_IN, g[3] + CLEAR_IN},
            };
            for (double[] c : corners) {
                if (Math.abs(c[0]) <= limit && Math.abs(c[1]) <= limit && boxAt(c[0], c[1]) < 0) points.add(c);
            }
        }
    }

    // True if the straight line misses every grown box.
    private boolean isClear(double[] a, double[] b) {
        for (double[] g : grown) if (lineCrosses(a, b, g)) return false;
        return true;
    }

    // True if the line from a to b goes through the box (cutting the line down to the box's four
    // sides leaves a piece of it). Running along a side does not count.
    private static boolean lineCrosses(double[] a, double[] b, double[] box) {
        double in = 0, out = 1;
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double[][] sides = {{-dx, a[0] - box[0]}, {dx, box[2] - a[0]}, {-dy, a[1] - box[1]}, {dy, box[3] - a[1]}};
        for (double[] s : sides) {
            if (s[0] == 0) {
                if (s[1] <= 0) return false;   // along or outside this side
            } else {
                double t = s[1] / s[0];
                if (s[0] < 0) in = Math.max(in, t);
                else out = Math.min(out, t);
            }
        }
        return out - in > 1e-9;
    }

    // Dijkstra's shortest way from points[0] to points[1], along clear straight lines. The points after
    // the start, in order, or null if the goal cannot be reached.
    private List<double[]> shortest(List<double[]> points) {
        int n = points.size();
        double[] dist = new double[n];
        int[] from = new int[n];
        boolean[] done = new boolean[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(from, -1);
        dist[0] = 0;
        for (int round = 0; round < n; round++) {
            int u = -1;
            for (int i = 0; i < n; i++) if (!done[i] && (u < 0 || dist[i] < dist[u])) u = i;
            if (u < 0 || Double.isInfinite(dist[u]) || u == 1) break;
            done[u] = true;
            double[] p = points.get(u);
            for (int v = 0; v < n; v++) {
                if (done[v]) continue;
                double[] q = points.get(v);
                double d = dist[u] + Math.hypot(q[0] - p[0], q[1] - p[1]);
                if (d < dist[v] && isClear(p, q)) {
                    dist[v] = d;
                    from[v] = u;
                }
            }
        }
        if (Double.isInfinite(dist[1])) return null;
        List<double[]> out = new ArrayList<>();
        for (int i = 1; i != 0; i = from[i]) out.add(points.get(i));
        Collections.reverse(out);
        return out;
    }

    private void checkSafePoint(double x, double y) {
        // A robot counts a safe point as reached RouteDriver.ON_POINT_IN early: that much room outside the margin.
        double room = RouteDriver.ON_POINT_IN;
        for (int i = 0; i < grown.size(); i++) {
            double[] g = grown.get(i);
            if (inside(new double[] {g[0] - room, g[1] - room, g[2] + room, g[3] + room}, x, y)) {
                throw new IllegalArgumentException("FieldMap.safePoint(" + x + ", " + y + ") is within the " + marginIn
                        + " in margin of \"" + names.get(i) + "\" (or less than " + room + " in outside it): move it, or use a smaller margin");
            }
        }
        if (Math.abs(x) > wallLimit() || Math.abs(y) > wallLimit()) {
            throw new IllegalArgumentException("FieldMap.safePoint(" + x + ", " + y + ") is too near a wall: the robot's centre "
                    + "stays " + marginIn + " in from the walls of a " + (2 * halfFieldIn) + " in field");
        }
    }

    private static void checkNumbers(String where, double... numbers) {
        for (double v : numbers) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                throw new IllegalArgumentException("FieldMap." + where + ": every number must be a real number, not " + v);
            }
        }
    }
}
