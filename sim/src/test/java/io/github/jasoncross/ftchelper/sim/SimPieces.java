package io.github.jasoncross.ftchelper.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Game pieces and what the robot does with them (needs physics = engine). The field file stages the
 * pieces and names the goals; the robot file says which robot shapes handle them:
 *
 *   collect.NAME = shape=intake, motor=intake, holds=4    a roller (or any part) that takes in a piece within
 *                                                          reach= (0.75 in) while its motor (or crservo=) turns
 *                                                          inward (power after its direction setting above 0.2;
 *                                                          inward=- if negative power takes in). BIOBUZZ: a robot
 *                                                          may hold at most 4.
 *   shoot.NAME = shape=flywheel, angle=40, share=0.5      when the flywheel's feeder fires a shot (motor.NAME =
 *                                                          flywheel, feeder=...), a held piece leaves from this
 *                                                          shape, angle degrees up from the shape's forward, at
 *                                                          share x the wheel's rim speed
 *   grab.NAME = shape=claw, servo=claw, closedBelow=0.3   a claw that holds a piece it touches while the servo
 *                                                          is below closedBelow, and lets it go when it opens
 *
 * The robot starts holding the field file's preload (BIOBUZZ: 4 POLLEN) when it can collect or shoot. A
 * piece whose centre comes into a goal (a FLOWER top) scores and is taken off the field.
 *
 * Every method locks the robot (the physics step already holds it), so the page's thread and the
 * physics thread never wait for each other in the wrong order.
 */
final class SimPieces {
    private static final class Collect {
        final String name, shape, motor, crservo;
        final double threshold, reach;   // reach: a piece this close to the shape (inches) can be taken in
        final int holds;

        Collect(String name, Map<String, String> s) {
            this.name = name;
            shape = req(s, "shape", "collect." + name);
            motor = s.get("motor");
            crservo = s.get("crservo");
            if (motor == null && crservo == null) throw new IllegalArgumentException("Robot file: collect." + name + " needs motor= or crservo=");
            threshold = 0.2 * ("-".equals(s.get("inward")) ? -1 : 1);
            holds = (int) num(s, "holds", 4, "collect." + name);
            reach = num(s, "reach", 0.75, "collect." + name);
        }
    }

    private static final class Shoot {
        final String name, shape, flywheel;
        final double angleDeg, share;

        Shoot(String name, Map<String, String> s) {
            this.name = name;
            shape = req(s, "shape", "shoot." + name);
            flywheel = s.get("flywheel");     // which flywheel motor fires it (needed when there are two)
            angleDeg = num(s, "angle", 40, "shoot." + name);
            share = num(s, "share", 0.5, "shoot." + name);
        }
    }

    private static final class Grab {
        final String name, shape, servo;
        final double closedBelow, reach;
        SimField.PieceType heldType = null;   // the piece in the claw (kept off the field while held)

        Grab(String name, Map<String, String> s) {
            this.name = name;
            shape = req(s, "shape", "grab." + name);
            servo = req(s, "servo", "grab." + name);
            closedBelow = num(s, "closedBelow", 0.3, "grab." + name);
            reach = num(s, "reach", 0.5, "grab." + name);
        }
    }

    private final SimRobot robot;
    private final SimWorld world;
    private final List<Collect> collects = new ArrayList<>();
    private final List<Shoot> shoots = new ArrayList<>();
    private final List<Grab> grabs = new ArrayList<>();
    // Pieces inside the robot (collected or preloaded), by type, in the order they came in.
    private final List<SimField.PieceType> stored = new ArrayList<>();
    private final Map<String, Integer> scored = new LinkedHashMap<>();
    private int launched = 0;
    // The field's stacks (pieces inside FLOWERs and CELLs): the types in each now, lowest first. Each stays in
    // its slots from the field file: when the lowest is taken, the others drop one slot.
    private final List<List<SimField.PieceType>> stackNow = new ArrayList<>();
    // How far a stack's lowest piece sits inside its element's block (inches): an intake against the block
    // reaches in that far through the opening.
    private final List<Double> stackDepth = new ArrayList<>();
    // When each stack's next piece has dropped into the lowest slot (physics time, s).
    private final List<Double> stackReady = new ArrayList<>();

    SimPieces(SimRobot robot, SimWorld world, Properties p, Map<String, Map<String, String>> collectLines,
              Map<String, Map<String, String>> shootLines, Map<String, Map<String, String>> grabLines) {
        this.robot = robot;
        this.world = world;
        for (Map.Entry<String, Map<String, String>> e : collectLines.entrySet()) collects.add(new Collect(e.getKey(), e.getValue()));
        for (Map.Entry<String, Map<String, String>> e : shootLines.entrySet()) shoots.add(new Shoot(e.getKey(), e.getValue()));
        for (Map.Entry<String, Map<String, String>> e : grabLines.entrySet()) grabs.add(new Grab(e.getKey(), e.getValue()));
        for (Collect c : collects) {
            check(c.shape, "collect." + c.name);
            if (c.motor != null && !robot.motors.containsKey(c.motor)) {
                throw new IllegalArgumentException("Robot file: collect." + c.name + ": no motor." + c.motor
                        + (robot.crServos.containsKey(c.motor) ? " (it is a CR servo: write crservo=" + c.motor + ")" : ""));
            }
            if (c.crservo != null && !robot.crServos.containsKey(c.crservo)) throw new IllegalArgumentException("Robot file: collect." + c.name + ": no crservo." + c.crservo);
        }
        for (Shoot s : shoots) {
            check(s.shape, "shoot." + s.name);
            if (s.flywheel != null && !robot.motors.containsKey(s.flywheel)) throw new IllegalArgumentException("Robot file: shoot." + s.name + ": no motor." + s.flywheel);
            if (s.flywheel == null && shoots.size() > 1) throw new IllegalArgumentException("Robot file: shoot." + s.name + ": with two launchers, say which flywheel fires each (flywheel=NAME)");
        }
        for (Grab g : grabs) {
            check(g.shape, "grab." + g.name);
            if (!robot.servos.containsKey(g.servo)) throw new IllegalArgumentException("Robot file: grab." + g.name + ": no servo." + g.servo);
        }
        // physics = simple (or no drivetrain): no game pieces; the collect/shoot/grab lines are checked, then unused.
        for (SimField.Goal g : robot.field.goals) scored.put(g.name, 0);
        for (SimField.Stack st : robot.field.stacks) {
            stackNow.add(new ArrayList<>());
            double depth = 0;
            if (!st.pieces.isEmpty()) {
                SimField.StagedPiece low = st.pieces.get(0);
                for (SimField.Element e : robot.field.elements) {
                    if (e.hull.size() >= 3 && e.zMin <= low.z && low.z <= e.zMax && inside(e.hull, low.x, low.y)) {
                        double r = robot.field.pieceTypes.get(low.type).diameterIn / 2;
                        depth = Math.max(depth, edgeDistance(e.hull, low.x, low.y) - r);
                    }
                }
            }
            stackDepth.add(Math.max(0, depth));
            stackReady.add(0.0);
        }
        reset();
    }

    private void check(String shape, String line) {
        if (!robot.shapes.shapes.containsKey(shape)) throw new IllegalArgumentException("Robot file: " + line + ": no shape." + shape);
    }

    /** Puts the field's pieces where the match starts them and fills the robot with its preload. */
    void reset() {
        synchronized (robot) {
            if (world == null) return;
            for (SimWorld.Ball b : world.balls()) world.removeBall(b);
            for (Grab g : grabs) g.heldType = null;
            stored.clear();
            for (SimField.StagedPiece sp : robot.field.pieces) {
                world.addBall(robot.field.pieceTypes.get(sp.type), sp.x, sp.y, sp.z, 0, 0, 0);
            }
            for (int i = 0; i < stackNow.size(); i++) {
                stackNow.get(i).clear();
                stackReady.set(i, 0.0);
                for (SimField.StagedPiece sp : robot.field.stacks.get(i).pieces) stackNow.get(i).add(robot.field.pieceTypes.get(sp.type));
            }
            // The preload (manual 10.3.1: in or on the robot, or on the tiles touching it): inside a robot that
            // can take in or shoot pieces; otherwise on the tiles in a row behind it.
            if (!collects.isEmpty() || !shoots.isEmpty()) {
                for (Map.Entry<String, Integer> e : robot.field.preload.entrySet()) {
                    for (int i = 0; i < e.getValue(); i++) stored.add(robot.field.pieceTypes.get(e.getKey()));
                }
            } else if (!grabs.isEmpty()) {
                int k = 0;
                for (Map.Entry<String, Integer> e : robot.field.preload.entrySet()) {
                    SimField.PieceType t = robot.field.pieceTypes.get(e.getKey());
                    for (int i = 0; i < e.getValue(); i++, k++) {
                        double[] at = toField(new double[] {-robot.lengthIn / 2 - t.diameterIn / 2 - 0.1,
                                (k - 1.5) * (t.diameterIn + 0.2), t.diameterIn / 2});
                        world.addBall(t, at[0], at[1], at[2], 0, 0, 0);
                    }
                }
            }
            for (String k : scored.keySet()) scored.put(k, 0);
            launched = 0;
        }
    }

    /** True when shots need a piece (the robot shoots real pieces), false when shots are only counted. */
    boolean shootsPieces() {
        return world != null && !shoots.isEmpty();
    }

    /** A shot from a flywheel: a stored piece leaves from the shoot shape. False if there was none to shoot. */
    boolean shoot(SimMotor flywheel) {
        synchronized (robot) {
            if (!shootsPieces() || stored.isEmpty()) return false;
            Shoot s = null;
            for (Shoot x : shoots) if (flywheel.name.equals(x.flywheel)) s = x;
            if (s == null && shoots.size() == 1) s = shoots.get(0);
            if (s == null) return false;
            SimShapes.Piece piece = pieceOf(s.shape);
            if (piece == null) return false;
            SimField.PieceType type = stored.remove(0);
            double dia = Math.max(piece.sf, piece.su);
            double rimSpeed = Math.abs(flywheel.shaftSpeed()) / Math.max(1, flywheel.ticksPerRev) * Math.PI * dia;   // in/s
            double a = Math.toRadians(s.angleDeg);
            double[] dir = new double[3];
            for (int k = 0; k < 3; k++) dir[k] = Math.cos(a) * piece.ef[k] + Math.sin(a) * piece.eu[k];
            double gap = dia / 2 + type.diameterIn / 2 + 0.3;
            double[] start = {piece.center[0] + dir[0] * gap, piece.center[1] + dir[1] * gap, piece.center[2] + dir[2] * gap};
            double[] at = toField(start), d = toFieldDir(dir), rv = world.robotVel();
            double speed = rimSpeed * s.share;
            SimWorld.Ball b = world.addBall(type, at[0], at[1], at[2], d[0] * speed + rv[0], d[1] * speed + rv[1], d[2] * speed);
            b.ignoreRobotUntil = world.time() + 0.2;
            launched++;
            return true;
        }
    }

    /** After each physics step: intakes take in pieces, claws hold and let go, goals score. */
    void step() {
        synchronized (robot) {
            if (world == null) return;
            for (SimWorld.Ball b : world.balls()) {
                boolean taken = false;
                double[] bp = world.ballPos(b);
                for (Collect c : collects) {
                    if (stored.size() >= c.holds || !(b.touchingParts.contains(c.shape) || near(c.shape, bp, b.type.diameterIn / 2, c.reach))) continue;
                    // the power after the motor's (or CR servo's) direction setting, for both
                    double power = c.motor != null ? robot.motors.get(c.motor).appliedPower
                            : robot.crServos.get(c.crservo)[0] * (robot.crServos.get(c.crservo)[1] == 1 ? -1 : 1);
                    if (c.threshold > 0 ? power >= c.threshold : power <= c.threshold) {
                        stored.add(b.type);
                        world.removeBall(b);
                        taken = true;
                        break;
                    }
                }
                if (taken) continue;
                for (Grab g : grabs) {
                    if (g.heldType != null || !(b.touchingParts.contains(g.shape) || near(g.shape, bp, b.type.diameterIn / 2, g.reach))) continue;
                    if (robot.servos.get(g.servo).actual < g.closedBelow) {
                        g.heldType = b.type;
                        world.removeBall(b);
                        taken = true;
                        break;
                    }
                }
                if (taken) continue;
                double[] p = world.ballPos(b);
                for (SimField.Goal goal : robot.field.goals) {
                    // In the opening: its centre over the opening, and low enough to be sitting in it or on
                    // its rim (the simulator's FLOWER is a solid block whose backstop stands 1.3 in above the
                    // opening; a piece there would drop in), not going up.
                    // A piece resting on the block with its edge over the opening would roll in: it counts.
                    // A shot flying past (fast) does not.
                    double r = b.type.diameterIn / 2;
                    double[] v = world.ballVel(b);
                    double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                    if (Math.hypot(p[0] - goal.x, p[1] - goal.y) <= goal.radiusIn + r && p[2] <= goal.z + r + 1.3
                            && p[2] >= goal.z - 6 && v[2] <= 1 && speed <= 60) {
                        scored.put(goal.name, scored.get(goal.name) + 1);
                        world.removeBall(b);
                        taken = true;
                        break;
                    }
                }
                if (!taken && (p[2] < -12 || Math.abs(p[0]) > 120 || Math.abs(p[1]) > 120)) world.removeBall(b);   // left the field
            }
            // A stack's lowest piece: an intake running inward (or a closed claw) at the opening takes it.
            for (int i = 0; i < stackNow.size(); i++) {
                List<SimField.PieceType> now = stackNow.get(i);
                SimField.Stack st = robot.field.stacks.get(i);
                if (now.isEmpty() || !st.take.equals("bottom") || world.time() < stackReady.get(i)) continue;
                SimField.StagedPiece slot = st.pieces.get(0);
                double[] p = {slot.x, slot.y, slot.z};
                double r = now.get(0).diameterIn / 2, depth = stackDepth.get(i);
                boolean taken = false;
                // the next one falls one slot (from rest, under gravity) before it can be taken
                double drop = st.pieces.size() > 1 ? st.pieces.get(1).z - slot.z : 0;
                double fall = Math.sqrt(2 * Math.max(0, drop) / 386.1);
                for (Collect c : collects) {
                    if (stored.size() >= c.holds || !near(c.shape, p, r, c.reach + depth)) continue;
                    double power = c.motor != null ? robot.motors.get(c.motor).appliedPower
                            : robot.crServos.get(c.crservo)[0] * (robot.crServos.get(c.crservo)[1] == 1 ? -1 : 1);
                    if (c.threshold > 0 ? power >= c.threshold : power <= c.threshold) {
                        stored.add(now.remove(0));
                        stackReady.set(i, world.time() + fall);
                        taken = true;
                        break;
                    }
                }
                if (taken) continue;
                for (Grab g : grabs) {
                    if (g.heldType != null || !near(g.shape, p, r, g.reach + depth)) continue;
                    if (robot.servos.get(g.servo).actual < g.closedBelow) {
                        g.heldType = now.remove(0);
                        stackReady.set(i, world.time() + fall);
                        break;
                    }
                }
            }
            // A claw that opens lets its piece go where it is.
            for (Grab g : grabs) {
                if (g.heldType == null || robot.servos.get(g.servo).actual < g.closedBelow) continue;
                double[] at = toField(heldAt(g));
                double[] rv = world.robotVel();
                SimWorld.Ball b = world.addBall(g.heldType, at[0], at[1], at[2], rv[0], rv[1], 0);
                b.ignoreRobotUntil = world.time() + 0.3;
                g.heldType = null;
            }
        }
    }

    // True if a piece (centre at p, field inches; radius r) is within reach of one of the shape's boxes.
    private boolean near(String shape, double[] p, double r, double reach) {
        double[] q = toRobot(p);
        for (SimShapes.Piece piece : robot.shapes.pieces()) {
            if (!piece.shape.equals(shape)) continue;
            double[] d = {q[0] - piece.center[0], q[1] - piece.center[1], q[2] - piece.center[2]};
            double[][] axes = {piece.ef, piece.el, piece.eu};
            double[] half = {piece.sf / 2, piece.sl / 2, piece.su / 2};
            double out = 0;
            for (int k = 0; k < 3; k++) {
                double a = d[0] * axes[k][0] + d[1] * axes[k][1] + d[2] * axes[k][2];
                double over = Math.abs(a) - half[k];
                if (over > 0) out += over * over;
            }
            if (Math.sqrt(out) - r <= reach) return true;
        }
        return false;
    }

    // Field (inches) to the robot frame (f, l, u).
    private double[] toRobot(double[] p) {
        double h, x, y;
        synchronized (robot) {
            h = Math.toRadians(robot.headingDeg);
            x = robot.x;
            y = robot.y;
        }
        double dx = p[0] - x, dy = p[1] - y;
        // forward = (-sin h, cos h); left = (-cos h, -sin h)
        return new double[] {-Math.sin(h) * dx + Math.cos(h) * dy, -Math.cos(h) * dx - Math.sin(h) * dy, p[2]};
    }

    // Where a claw holds its piece: the middle of its fingers (robot frame).
    private double[] heldAt(Grab g) {
        List<SimShapes.Piece> ps = new ArrayList<>();
        for (SimShapes.Piece p : robot.shapes.pieces()) if (p.shape.equals(g.shape)) ps.add(p);
        if (ps.isEmpty()) return new double[] {0, 0, 0};
        SimShapes.Piece palm = ps.get(0);
        double up = palm.su / 2 + (ps.size() > 1 ? ps.get(1).su / 2 : 1);
        return new double[] {palm.center[0] + palm.eu[0] * up, palm.center[1] + palm.eu[1] * up, palm.center[2] + palm.eu[2] * up};
    }

    private SimShapes.Piece pieceOf(String shape) {
        for (SimShapes.Piece p : robot.shapes.pieces()) if (p.shape.equals(shape)) return p;
        return null;
    }

    // Robot frame (f, l, u inches from the robot's centre on the tiles) to the field.
    private double[] toField(double[] r) {
        double h, x, y;
        synchronized (robot) {
            h = Math.toRadians(robot.headingDeg);
            x = robot.x;
            y = robot.y;
        }
        double[] d = dir(h, r);
        return new double[] {x + d[0], y + d[1], r[2]};
    }

    private double[] toFieldDir(double[] r) {
        double h;
        synchronized (robot) { h = Math.toRadians(robot.headingDeg); }
        return dir(h, r);
    }

    private static double[] dir(double h, double[] r) {
        // forward = (-sin h, cos h); left = (-cos h, -sin h)
        return new double[] {-Math.sin(h) * r[0] - Math.cos(h) * r[1], Math.cos(h) * r[0] - Math.sin(h) * r[1], r[2]};
    }

    // ---------------------------------------------------------------- for the page and scenarios

    /** Pieces held inside the robot, by type. */
    Map<String, Integer> stored() {
        synchronized (robot) {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (SimField.PieceType t : stored) m.merge(t.name, 1, Integer::sum);
            for (Grab g : grabs) if (g.heldType != null) m.merge(g.heldType.name, 1, Integer::sum);
            return m;
        }
    }

    int storedCount() {
        synchronized (robot) {
            int n = stored.size();
            for (Grab g : grabs) if (g.heldType != null) n++;
            return n;
        }
    }

    Map<String, Integer> scored() {
        synchronized (robot) {
            return new LinkedHashMap<>(scored);
        }
    }

    int launched() {
        synchronized (robot) {
            return launched;
        }
    }

    /** Every piece to draw: {type, x, y, z} (field inches), including pieces held in a claw. */
    List<Object[]> drawn() {
        synchronized (robot) {
            List<Object[]> out = new ArrayList<>();
            if (world == null) return out;
            for (SimWorld.Ball b : world.balls()) {
                double[] p = world.ballPos(b);
                out.add(new Object[] {b.type, p[0], p[1], p[2]});
            }
            for (Grab g : grabs) {
                if (g.heldType == null) continue;
                double[] p = toField(heldAt(g));
                out.add(new Object[] {g.heldType, p[0], p[1], p[2]});
            }
            for (int i = 0; i < stackNow.size(); i++) {
                List<SimField.StagedPiece> slots = robot.field.stacks.get(i).pieces;
                for (int k = 0; k < stackNow.get(i).size(); k++) {
                    SimField.StagedPiece sp = slots.get(k);
                    out.add(new Object[] {stackNow.get(i).get(k), sp.x, sp.y, sp.z});
                }
            }
            return out;
        }
    }

    /** How many pieces are still in the field's stacks (inside FLOWERs and CELLs). */
    int inStacks() {
        synchronized (robot) {
            int n = 0;
            for (List<SimField.PieceType> l : stackNow) n += l.size();
            return n;
        }
    }

    // Inside a counterclockwise convex outline?
    private static boolean inside(List<double[]> hull, double x, double y) {
        for (int i = 0; i < hull.size(); i++) {
            double[] a = hull.get(i), b = hull.get((i + 1) % hull.size());
            if ((b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]) < 0) return false;
        }
        return true;
    }

    // From a point inside a convex outline to its nearest edge.
    private static double edgeDistance(List<double[]> hull, double x, double y) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < hull.size(); i++) {
            double[] a = hull.get(i), b = hull.get((i + 1) % hull.size());
            double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
            if (len < 1e-9) continue;
            best = Math.min(best, Math.abs(dx * (y - a[1]) - dy * (x - a[0])) / len);
        }
        return best;
    }

    /** Puts a piece on the field (scenarios and the page). */
    void place(String type, double x, double y, double z) {
        synchronized (robot) {
            SimField.PieceType t = robot.field.pieceTypes.get(type);
            if (t == null) throw new IllegalArgumentException("no piece type \"" + type + "\" in the field file (" + robot.field.pieceTypes.keySet() + ")");
            if (world == null) throw new IllegalArgumentException("game pieces need physics = engine and a drivetrain");
            world.addBall(t, x, y, Math.max(z, t.diameterIn / 2), 0, 0, 0);
        }
    }

    private static String req(Map<String, String> s, String key, String line) {
        String v = s.get(key);
        if (v == null || v.isEmpty()) throw new IllegalArgumentException("Robot file: " + line + " needs " + key + "=");
        return v;
    }

    private static double num(Map<String, String> s, String key, double fallback, String line) {
        if (!s.containsKey(key)) return fallback;
        try {
            return Double.parseDouble(s.get(key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: " + line + ": " + key + "=" + s.get(key) + " needs a number");
        }
    }
}
