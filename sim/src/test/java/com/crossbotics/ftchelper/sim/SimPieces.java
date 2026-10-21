package com.crossbotics.ftchelper.sim;

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
 *                                                          share x the wheel's rim speed; spin=1 gives it
 *                                                          backspin as if it rolled along a still hood
 *                                                          (-1 topspin); table=FILE: how the launcher really
 *                                                          throws, with scatter (SimShotTable; tools/launcher);
 *                                                          speed=IN/S: a fixed exit speed (a catapult: make
 *                                                          its motor a flywheel with feeder= itself)
 *   grab.NAME = shape=claw, servo=claw, closedBelow=0.3   a claw that holds a piece it touches while the servo
 *                                                          is below closedBelow (or above closedAbove=, for a
 *                                                          claw that closes toward 1), and lets it go when it opens
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
        final String[] shapes;   // shape=a+b+c: pieces come in at any of them
        final double threshold, reach;   // reach: a piece this close to the shape (inches) can be taken in
        final int holds;

        Collect(String name, Map<String, String> s) {
            this.name = name;
            shape = req(s, "shape", "collect." + name);
            shapes = shape.split("\\s*\\+\\s*");
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
        final double angleDeg, share, spin, fixedSpeed;
        final SimShotTable table;      // table=: how this launcher really throws (null: angle/share/spin)

        Shoot(String name, Map<String, String> s, java.nio.file.Path dir) {
            this.name = name;
            shape = req(s, "shape", "shoot." + name);
            flywheel = s.get("flywheel");     // which flywheel motor fires it (needed when there are two)
            angleDeg = num(s, "angle", 40, "shoot." + name);
            share = num(s, "share", 0.5, "shoot." + name);
            // the ball's spin, as a share of "rolling": 1 = it leaves turning like a ball rolling along a still hood
            // (speed / radius) with backspin (the wheel under the ball, the hood over it); -1 topspin; 0 none
            spin = num(s, "spin", 0, "shoot." + name);
            // speed=: a fixed exit speed (in/s) instead of share x rim speed: a catapult or a spring launcher
            fixedSpeed = num(s, "speed", Double.NaN, "shoot." + name);
            String t = s.get("table");
            table = t == null ? null : new SimShotTable(dir == null ? java.nio.file.Paths.get(t) : dir.resolve(t).normalize(),
                    "shoot." + name, name.hashCode());
        }
    }

    private static final class Grab {
        final String name, shape, servo;
        final double closedBelow, closedAbove, reach;   // closed when below closedBelow (or above closedAbove)
        SimField.PieceType heldType = null;   // the piece in the claw (kept off the field while held)

        Grab(String name, Map<String, String> s) {
            this.name = name;
            shape = req(s, "shape", "grab." + name);
            servo = req(s, "servo", "grab." + name);
            // A claw closes toward 0 (closedBelow) or toward 1 (closedAbove): give one of the two.
            closedAbove = s.containsKey("closedAbove") ? num(s, "closedAbove", 0.7, "grab." + name) : Double.NaN;
            closedBelow = Double.isNaN(closedAbove) ? num(s, "closedBelow", 0.3, "grab." + name) : Double.NaN;
            reach = num(s, "reach", 0.5, "grab." + name);
        }

        boolean isClosed(double servoPosition) {
            return Double.isNaN(closedAbove) ? servoPosition < closedBelow : servoPosition > closedAbove;
        }
    }

    private final SimRobot robot;
    private final SimWorld world;
    private final List<Collect> collects = new ArrayList<>();

    /**
     * Which way of turning takes pieces in for this motor or CR servo: +1 (positive power), -1
     * (inward=-), or 0 if no collect. line uses it. For the picture (green in, orange out).
     */
    int inwardSign(String motor, String crservo) {
        for (Collect c : collects) {
            boolean same = (motor != null && motor.equals(c.motor)) || (crservo != null && crservo.equals(c.crservo));
            if (same) return c.threshold < 0 ? -1 : 1;
        }
        return 0;
    }
    private final List<Shoot> shoots = new ArrayList<>();
    private final List<Grab> grabs = new ArrayList<>();
    // Pieces inside the robot (collected or preloaded), by type, in the order they came in.
    private final List<SimField.PieceType> stored = new ArrayList<>();
    private final Map<String, Integer> scored = new LinkedHashMap<>();
    private int launched = 0;
    private int leftField = 0;                                      // pieces that went over a wall (taken off)
    private final Map<String, Integer> inCells = new LinkedHashMap<>();   // HIVE name -> pieces in its up CELL now
    // How long each piece has been slow (seconds): a piece counts in a CELL once it has rested there a moment, so a
    // shot slowing at the top of its flight does not tip the HIVE.
    private final Map<SimWorld.Ball, Double> slowFor = new java.util.HashMap<>();
    private double lastStepTime = 0;
    // The field's stacks (pieces inside FLOWERs and CELLs): the types in each now, lowest first. Each stays in
    // its slots from the field file: when the lowest is taken, the others drop one slot.
    private final List<List<SimField.PieceType>> stackNow = new ArrayList<>();
    // How far a stack's lowest piece sits inside its element's block (inches): an intake against the block
    // reaches in that far through the opening.
    private final List<Double> stackDepth = new ArrayList<>();
    // When each stack's next piece has dropped into the lowest slot (physics time, s).
    private final List<Double> stackReady = new ArrayList<>();
    // Whether a stack's lowest slot is open (no solid over the piece: a FLOWER's Retrieval Opening). Its
    // lowest piece is then a real piece the robot can knock out; the next one drops into the slot.
    private final List<Boolean> stackOpen = new ArrayList<>();
    // The real piece in each open stack's lowest slot (null: none there now), and where it rests.
    private final List<SimWorld.Ball> stackBall = new ArrayList<>();
    private final List<double[]> stackRest = new ArrayList<>();

    SimPieces(SimRobot robot, SimWorld world, Properties p, Map<String, Map<String, String>> collectLines,
              Map<String, Map<String, String>> shootLines, Map<String, Map<String, String>> grabLines) {
        this.robot = robot;
        this.world = world;
        for (Map.Entry<String, Map<String, String>> e : collectLines.entrySet()) collects.add(new Collect(e.getKey(), e.getValue()));
        for (Map.Entry<String, Map<String, String>> e : shootLines.entrySet()) shoots.add(new Shoot(e.getKey(), e.getValue(), robot.dir));
        for (Map.Entry<String, Map<String, String>> e : grabLines.entrySet()) grabs.add(new Grab(e.getKey(), e.getValue()));
        for (Collect c : collects) {
            for (String sh : c.shapes) check(sh, "collect." + c.name);
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
        // preload = off in the robot file: the robot starts empty (the page's "Preloaded" changes it)
        String pre = p.getProperty("preload", "on").trim().toLowerCase(java.util.Locale.US);
        if (!pre.equals("on") && !pre.equals("off")) throw new IllegalArgumentException("Robot file: preload must be on or off");
        preloaded = pre.equals("on");
        for (SimField.Stack st : robot.field.stacks) {
            stackNow.add(new ArrayList<>());
            double depth = 0;
            if (!st.pieces.isEmpty()) {
                SimField.StagedPiece low = st.pieces.get(0);
                for (SimField.Element e : robot.field.elements) {
                    // How deep the piece sits inside the element's outline: a solid over it counts even when
                    // the piece is in an opening under it (a FLOWER's Retrieval Opening).
                    if (e.hull.size() >= 3 && low.z <= e.zMax && inside(e.hull, low.x, low.y)) {
                        double r = robot.field.pieceTypes.get(low.type).diameterIn / 2;
                        depth = Math.max(depth, edgeDistance(e.hull, low.x, low.y) - r);
                    }
                }
            }
            stackDepth.add(Math.max(0, depth));
            stackReady.add(0.0);
            stackOpen.add(st.take.equals("bottom") && openSlot(st));
            stackBall.add(null);
            stackRest.add(null);
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
            // the HIVEs back as the match starts them
            robot.field.resetHives();
            for (SimField.Hive h : robot.field.hives) world.placeHive(h);
            inCells.clear();
            slowFor.clear();
            leftField = 0;
            for (SimField.StagedPiece sp : robot.field.pieces) {
                world.addBall(robot.field.pieceTypes.get(sp.type), sp.x, sp.y, sp.z, 0, 0, 0);
            }
            for (int i = 0; i < stackNow.size(); i++) {
                stackNow.get(i).clear();
                stackReady.set(i, 0.0);
                SimField.Stack st = robot.field.stacks.get(i);
                if (inAHive(st)) {
                    // pieces staged in a CELL that can tip are real pieces: they rest in it and fall out when it tips
                    for (SimField.StagedPiece sp : st.pieces) world.addBall(robot.field.pieceTypes.get(sp.type), sp.x, sp.y, sp.z, 0, 0, 0);
                    continue;
                }
                for (SimField.StagedPiece sp : st.pieces) stackNow.get(i).add(robot.field.pieceTypes.get(sp.type));
                stackBall.set(i, null);
                if (stackOpen.get(i)) dropIntoSlot(i);
            }
            // The preload (manual 10.3.1: in or on the robot, or on the tiles touching it): inside a robot that
            // can take in or shoot pieces; otherwise on the tiles in a row behind it. None when it is turned off.
            if (!preloaded) {
                // no preload: the robot starts empty
            } else if (!collects.isEmpty() || !shoots.isEmpty()) {
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

    // Whether the robot starts a match holding the field file's preload (the page's "Preloaded", set preload).
    private boolean preloaded = true;

    boolean preloaded() {
        synchronized (robot) {
            return preloaded;
        }
    }

    /**
     * Preloaded or not: from the next reset, and now too: a robot that holds pieces and is set to "not
     * preloaded" is emptied; an empty one set to "preloaded" gets the preload.
     */
    void setPreloaded(boolean on) {
        synchronized (robot) {
            preloaded = on;
            if (world == null || (collects.isEmpty() && shoots.isEmpty())) return;
            if (!on) {
                stored.clear();
            } else if (stored.isEmpty()) {
                for (Map.Entry<String, Integer> e : robot.field.preload.entrySet()) {
                    for (int i = 0; i < e.getValue(); i++) stored.add(robot.field.pieceTypes.get(e.getKey()));
                }
            }
        }
    }

    // A ball (centre p, radius r) that overlaps a plate (centre, three directions, sizes; see SimField.Hive.plates):
    // where it must go to just touch the plate's face on its own side; null if it does not overlap.
    static double[] pushOut(double[] pl, double[] p, double r) {
        double[] d = {p[0] - pl[0], p[1] - pl[1], p[2] - pl[2]};
        double[] q = new double[3];
        double out2 = 0;
        for (int k = 0; k < 3; k++) {
            double c = d[0] * pl[3 + 3 * k] + d[1] * pl[4 + 3 * k] + d[2] * pl[5 + 3 * k];
            q[k] = c;
            double over = Math.abs(c) - pl[12 + k] / 2;
            if (over > 0) out2 += over * over;
        }
        if (out2 >= r * r) return null;
        // out through the thin side (the third direction), to the side the centre is on
        double side = q[2] >= 0 ? 1 : -1, move = side * (pl[14] / 2 + r + 0.05) - q[2];
        return new double[] {p[0] + move * pl[9], p[1] + move * pl[10], p[2] + move * pl[11]};
    }

    // True when nothing solid sits over the stack's lowest piece (it may rest on a thin floor plate, like a
    // FLOWER's lip): the piece is in the open and can be knocked out.
    private boolean openSlot(SimField.Stack st) {
        if (st.pieces.isEmpty() || inAHive(st)) return false;
        SimField.StagedPiece low = st.pieces.get(0);
        double r = robot.field.pieceTypes.get(low.type).diameterIn / 2;
        for (SimField.Element e : robot.field.elements) {
            if (e.hull.size() < 3 || !inside(e.hull, low.x, low.y)) continue;
            double overlap = Math.min(low.z + r, e.zMax) - Math.max(low.z - r, e.zMin);
            if (overlap > 0.5) return false;
        }
        return true;
    }

    // The stack's next piece becomes a real piece in its lowest slot.
    private void dropIntoSlot(int i) {
        List<SimField.PieceType> now = stackNow.get(i);
        if (now.isEmpty()) return;
        SimField.StagedPiece slot = robot.field.stacks.get(i).pieces.get(0);
        SimField.PieceType type = now.remove(0);
        double[] at = restingSpot(new double[] {slot.x, slot.y, slot.z}, type.diameterIn / 2);
        stackBall.set(i, world.addBall(type, at[0], at[1], at[2], 0, 0, 0));
        stackRest.set(i, at);
    }

    // Where a piece put at p rests without cutting into the field: on top of a thin floor plate under it
    // (a FLOWER's lip), and moved off a wall beside it (the FLOWER's closed back).
    private double[] restingSpot(double[] p, double r) {
        double[] q = p.clone();
        for (SimField.Element e : robot.field.elements) {
            if (e.hull.size() < 3) continue;
            if (Math.min(q[2] + r, e.zMax) - Math.max(q[2] - r, e.zMin) <= 0) continue;
            if (inside(e.hull, q[0], q[1])) {
                if (e.zMax - (q[2] - r) <= 0.5) q[2] = e.zMax + r + 0.01;
                continue;
            }
            double[] n = closestOnHull(e.hull, q[0], q[1]);
            double d = Math.hypot(q[0] - n[0], q[1] - n[1]);
            if (d < r && d > 1e-9) {
                q[0] += (q[0] - n[0]) / d * (r + 0.01 - d);
                q[1] += (q[1] - n[1]) / d * (r + 0.01 - d);
            }
        }
        return q;
    }

    // The point of an outline's edge nearest to (x, y).
    private static double[] closestOnHull(List<double[]> hull, double x, double y) {
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int k = 0; k < hull.size(); k++) {
            double[] a = hull.get(k), b = hull.get((k + 1) % hull.size());
            double dx = b[0] - a[0], dy = b[1] - a[1], len2 = dx * dx + dy * dy;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / len2));
            double px = a[0] + t * dx, py = a[1] + t * dy, d = Math.hypot(x - px, y - py);
            if (d < bestD) { bestD = d; best = new double[] {px, py}; }
        }
        return best;
    }

    // A stack in a HIVE's up CELL (the staged NECTAR), when that HIVE can tip.
    private boolean inAHive(SimField.Stack st) {
        if (st.pieces.isEmpty()) return false;
        SimField.StagedPiece p = st.pieces.get(0);
        for (SimField.Hive h : robot.field.hives) if (h.inside(h.up, new double[] {p.x, p.y, p.z})) return true;
        return false;
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
            double angle = s.angleDeg, speed = Double.isNaN(s.fixedSpeed) ? rimSpeed * s.share : s.fixedSpeed, backspin = s.spin * speed / (type.diameterIn / 2);
            double gap = dia / 2 + type.diameterIn / 2 + 0.3;
            double[] exit = null;
            SimShotTable.Shot pick = s.table == null ? null
                    : s.table.pick(type.name, flywheel.shaftSpeed() / Math.max(1, flywheel.ticksPerRev) * 60);
            if (pick != null) {
                angle = pick.angleDeg;
                speed = pick.shot ? pick.speedIn : 0;          // a feed that did not become a shot drops out
                backspin = pick.shot ? pick.backspin : 0;
                exit = new double[3];
                for (int k = 0; k < 3; k++) exit[k] = piece.center[k] + pick.exitForward * piece.ef[k] + pick.exitUp * piece.eu[k];
            }
            double a = Math.toRadians(angle);
            double[] dir = new double[3];
            for (int k = 0; k < 3; k++) dir[k] = Math.cos(a) * piece.ef[k] + Math.sin(a) * piece.eu[k];
            double[] start = exit != null ? exit
                    : new double[] {piece.center[0] + dir[0] * gap, piece.center[1] + dir[1] * gap, piece.center[2] + dir[2] * gap};
            double[] at = toField(start), d = toFieldDir(dir), rv = world.robotVel();
            SimWorld.Ball b = world.addBall(type, at[0], at[1], at[2], d[0] * speed + rv[0], d[1] * speed + rv[1], d[2] * speed);
            if (backspin != 0) {
                // backspin turns about (direction x up); its sign along the wheel's axle when the axle is square to the shot
                double[] el = toFieldDir(piece.el);
                double[] axle = {-el[0], -el[1], -el[2]};          // straight up: backspin turns about the shape's right
                double[] back = {d[1], -d[0], 0};                                   // d x up
                double n = Math.hypot(back[0], back[1]);
                double[] ax = n > 1e-6 ? new double[] {back[0] / n, back[1] / n, 0} : axle;
                world.spin(b, ax[0] * backspin, ax[1] * backspin, ax[2] * backspin);
            }
            b.ignoreRobotUntil = world.time() + 0.2;
            if (pick == null || pick.shot) launched++;
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
                    if (stored.size() >= c.holds || !(touchesAny(b, c.shapes) || nearAny(c.shapes, bp, b.type.diameterIn / 2, c.reach))) continue;
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
                    if (g.isClosed(robot.servos.get(g.servo).actual)) {
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
                // over a wall (or through the floor): off the field, counted
                double wall = robot.field.wallHalf + 2;
                if (!taken && (p[2] < -12 || Math.abs(p[0]) > wall || Math.abs(p[1]) > wall)) {
                    world.removeBall(b);
                    leftField++;
                }
            }
            // The HIVEs: what is in each up CELL; enough weight there tips it (the pieces in its CELLs turn with it
            // and, the CELL now facing down, roll out).
            double tNow = world.time(), dt = Math.max(0, tNow - lastStepTime);
            lastStepTime = tNow;
            if (!robot.field.hives.isEmpty()) {
                java.util.Set<SimWorld.Ball> alive = new java.util.HashSet<>(world.balls());
                slowFor.keySet().retainAll(alive);
                for (SimWorld.Ball b : alive) {
                    double[] v = world.ballVel(b);
                    boolean slow = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) < 10;
                    slowFor.put(b, slow ? slowFor.getOrDefault(b, 0.0) + dt : 0.0);
                }
            }
            for (SimField.Hive h : robot.field.hives) {
                double grams = 0;
                int n = 0;
                List<SimWorld.Ball> load = new ArrayList<>();
                for (SimWorld.Ball b : world.balls()) {
                    double[] bp = world.ballPos(b);
                    // counted once it has rested in the CELL for 0.3 s (a shot passing through does not tip it)
                    boolean resting = slowFor.getOrDefault(b, 0.0) >= 0.3;
                    if (h.inside(h.up, bp) && resting) { grams += b.type.massG; n++; }
                    if (h.inside(h.up, bp) || h.inside(-h.up, bp)) load.add(b);
                }
                inCells.put(h.name, n);
                if (grams >= h.tipGrams) {
                    List<double[]> to = new ArrayList<>();
                    for (SimWorld.Ball b : load) to.add(h.tipped(world.ballPos(b)));
                    robot.field.tip(h);
                    world.placeHive(h);
                    for (int k = 0; k < load.size(); k++) world.moveBall(load.get(k), to.get(k));
                    // a piece next to the HIVE (not in a CELL) that a moved plate now cuts through: out to the side
                    // the plate faces, still
                    for (SimWorld.Ball b : world.balls()) {
                        if (load.contains(b)) continue;
                        double[] bp = world.ballPos(b);
                        double r = b.type.diameterIn / 2;
                        for (int s : new int[] {1, -1}) {
                            for (double[] pl : h.plates(s)) {
                                double[] out = pushOut(pl, bp, r);
                                if (out != null) { bp = out; world.moveBall(b, bp); }
                            }
                        }
                    }
                    inCells.put(h.name, 0);
                }
            }
            // A stack's lowest piece: an intake running inward (or a closed claw) at the opening takes it.
            // In an open slot it is a real piece: knocked out (or taken), the next one drops into the slot.
            List<SimWorld.Ball> loose = world.balls();
            for (int i = 0; i < stackNow.size(); i++) {
                List<SimField.PieceType> now = stackNow.get(i);
                SimField.Stack st = robot.field.stacks.get(i);
                if (!st.take.equals("bottom") || st.pieces.isEmpty()) continue;
                SimField.StagedPiece slot = st.pieces.get(0);
                double[] p = {slot.x, slot.y, slot.z};
                // the next one falls one slot (from rest, under gravity) before it can be taken
                double drop = st.pieces.size() > 1 ? st.pieces.get(1).z - slot.z : 0;
                double fall = Math.sqrt(2 * Math.max(0, drop) / 386.1);
                SimWorld.Ball inSlot = null;
                if (stackOpen.get(i)) {
                    SimWorld.Ball sb = stackBall.get(i);
                    if (sb != null) {
                        double[] q = loose.contains(sb) ? world.ballPos(sb) : null;
                        double[] rest = stackRest.get(i);
                        if (q == null || Math.hypot(q[0] - rest[0], q[1] - rest[1]) > sb.type.diameterIn / 2) {
                            stackBall.set(i, null);                     // knocked out or taken in
                            stackReady.set(i, world.time() + fall);
                        } else {
                            inSlot = sb;
                            // it stays put in the opening until the robot or another piece comes near it
                            if (!disturbed(sb, q)) world.hold(sb, stackRest.get(i));
                        }
                    }
                    if (stackBall.get(i) == null && !now.isEmpty() && world.time() >= stackReady.get(i) && slotClear(p, now.get(0))) {
                        dropIntoSlot(i);
                        inSlot = stackBall.get(i);
                    }
                    if (inSlot == null) continue;
                } else if (now.isEmpty() || world.time() < stackReady.get(i)) {
                    continue;
                }
                SimField.PieceType type = inSlot != null ? inSlot.type : now.get(0);
                double r = type.diameterIn / 2, depth = stackDepth.get(i);
                boolean taken = false;
                for (Collect c : collects) {
                    if (stored.size() >= c.holds || !nearAny(c.shapes, p, r, c.reach + depth)) continue;
                    double power = c.motor != null ? robot.motors.get(c.motor).appliedPower
                            : robot.crServos.get(c.crservo)[0] * (robot.crServos.get(c.crservo)[1] == 1 ? -1 : 1);
                    if (c.threshold > 0 ? power >= c.threshold : power <= c.threshold) {
                        stored.add(takeFromStack(i, inSlot));
                        stackReady.set(i, world.time() + fall);
                        taken = true;
                        break;
                    }
                }
                if (taken) continue;
                for (Grab g : grabs) {
                    if (g.heldType != null || !near(g.shape, p, r, g.reach + depth)) continue;
                    if (g.isClosed(robot.servos.get(g.servo).actual)) {
                        g.heldType = takeFromStack(i, inSlot);
                        stackReady.set(i, world.time() + fall);
                        break;
                    }
                }
            }
            // A claw that opens lets its piece go where it is.
            for (Grab g : grabs) {
                if (g.heldType == null || g.isClosed(robot.servos.get(g.servo).actual)) continue;
                double[] at = toField(heldAt(g));
                double[] rv = world.robotVel();
                SimWorld.Ball b = world.addBall(g.heldType, at[0], at[1], at[2], rv[0], rv[1], 0);
                b.ignoreRobotUntil = world.time() + 0.3;
                g.heldType = null;
            }
        }
    }

    // True if a piece (centre at p, field inches; radius r) is within reach of one of the shape's boxes.
    // Takes a stack's lowest piece: the real one in its slot, or the first staged one.
    private SimField.PieceType takeFromStack(int i, SimWorld.Ball inSlot) {
        if (inSlot == null) return stackNow.get(i).remove(0);
        world.removeBall(inSlot);
        stackBall.set(i, null);
        return inSlot.type;
    }

    /**
     * A spinning part's surface speed at a field point (inches per second, field frame), or null: a roller
     * or flywheel turns about its own left-right line (right hand), as the picture shows it.
     */
    double[] surfaceSpeed(String shape, double[] at) {
        for (SimShapes.Piece pc : robot.shapes.pieces()) {
            if (!pc.shape.equals(shape) || !pc.kind.equals("cyl") || Math.abs(pc.run) < 1e-6) continue;
            double[] q = toRobot(at), d = {q[0] - pc.center[0], q[1] - pc.center[1], q[2] - pc.center[2]};
            double w = pc.run * 2 * Math.PI, ax = pc.el[0] * w, ay = pc.el[1] * w, az = pc.el[2] * w;
            return toFieldDir(new double[] {ay * d[2] - az * d[1], az * d[0] - ax * d[2], ax * d[1] - ay * d[0]});
        }
        return null;
    }

    // A robot part or another piece near this piece (it may be pushed).
    private boolean disturbed(SimWorld.Ball b, double[] q) {
        double r = b.type.diameterIn / 2;
        if (!b.touchingParts.isEmpty()) return true;
        for (SimShapes.Piece piece : robot.shapes.pieces()) if (near(piece.shape, q, r, 0.3)) return true;
        for (SimWorld.Ball o : world.balls()) {
            if (o == b) continue;
            double[] p = world.ballPos(o);
            if (Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]))
                    < r + o.type.diameterIn / 2 + 0.3) return true;
        }
        return false;
    }

    // No real piece (and no robot part) in the way of a piece dropping into this slot.
    private boolean slotClear(double[] p, SimField.PieceType next) {
        for (SimShapes.Piece piece : robot.shapes.pieces()) if (near(piece.shape, p, next.diameterIn / 2, 0)) return false;
        for (SimWorld.Ball b : world.balls()) {
            double[] q = world.ballPos(b);
            double d = Math.sqrt((q[0] - p[0]) * (q[0] - p[0]) + (q[1] - p[1]) * (q[1] - p[1]) + (q[2] - p[2]) * (q[2] - p[2]));
            if (d < (b.type.diameterIn + next.diameterIn) / 2 * 0.95) return false;
        }
        return true;
    }

    private static boolean touchesAny(SimWorld.Ball b, String[] shapes) {
        for (String s : shapes) if (b.touchingParts.contains(s)) return true;
        return false;
    }

    private boolean nearAny(String[] shapes, double[] p, double r, double reach) {
        for (String s : shapes) if (near(s, p, r, reach)) return true;
        return false;
    }

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

    /** How many pieces the robot can hold: its biggest collect. holds= (and one per claw); 0 if it cannot. */
    int capacity() {
        synchronized (robot) {
            int n = 0;
            for (Collect c : collects) n = Math.max(n, c.holds);
            if (n == 0 && !shoots.isEmpty()) for (Map.Entry<String, Integer> e : robot.field.preload.entrySet()) n += e.getValue();
            return n + grabs.size();
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

    /** Pieces in each HIVE's up CELL now (they score if they are still there at the end). */
    Map<String, Integer> inCells() {
        synchronized (robot) {
            return new LinkedHashMap<>(inCells);
        }
    }

    /** How many times each HIVE has tipped since the start. */
    Map<String, Integer> tips() {
        synchronized (robot) {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (SimField.Hive h : robot.field.hives) m.put(h.name, h.tips);
            return m;
        }
    }

    /** Pieces that went over a wall since the start (taken off the field). */
    int leftField() {
        synchronized (robot) {
            return leftField;
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
                int up = stackOpen.get(i) ? 1 : 0;      // an open stack's lowest slot holds a real piece (or none yet)
                for (int k = 0; k < stackNow.get(i).size() && k + up < slots.size(); k++) {
                    SimField.StagedPiece sp = slots.get(k + up);
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
            for (SimWorld.Ball b : stackBall) if (b != null) n++;      // still in its slot
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
