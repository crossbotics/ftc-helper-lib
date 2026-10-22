package com.crossbotics.ftchelper.sim;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.ode4j.math.DMatrix3;
import org.ode4j.math.DVector3C;
import org.ode4j.ode.DBody;
import org.ode4j.ode.DContact;
import org.ode4j.ode.DContactBuffer;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.DJointGroup;
import org.ode4j.ode.DMass;
import org.ode4j.ode.DPlane2DJoint;
import org.ode4j.ode.DSpace;
import org.ode4j.ode.DTriMeshData;
import org.ode4j.ode.DWorld;
import org.ode4j.ode.OdeConstants;
import org.ode4j.ode.OdeHelper;

/**
 * The physics of the field: the robot as a solid body that the drivetrain pushes around, the walls
 * and the field file's solid parts as things it bumps into and slides along, and (later) game pieces.
 * It uses the ode4j physics engine (pure Java, BSD licence); only this class talks to it, so another
 * engine could replace it here.
 *
 * Units inside are metres, kilograms and seconds (what the engine likes); the rest of the simulator
 * uses inches. World frame: x and y as the field (inches x 0.0254), z up. The robot is held flat on the
 * tiles (it moves in x, y and turns about z only): its body's centre sits at z = 0, so the field is
 * placed zFloor below it.
 *
 * How the robot moves: the wheels say how fast the robot wants to go (forward, right, turning); the
 * drive pushes the body toward that speed as hard as the wheels' grip allows (traction). When the body
 * hits something it stops or slides, and the wheels keep turning (they slip), like a real robot.
 */
final class SimWorld {
    static final double M = 0.0254;                 // metres per inch
    private static final double STEP = 0.004;       // the engine's fixed time step, seconds
    private static final double G = 9.81;
    private static final int MAX_CONTACTS = 8;

    private static boolean started = false;

    // What a shape is, for the contact rules and for "bumped into ...".
    private static final class Tag {
        final String name;
        final boolean robot, floor;
        final double friction;
        final Ball ball;          // set for a game piece
        boolean piecesOnly;       // a thin floor plate: only pieces hit it, the robot rolls over
        boolean soft;             // a soft robot part (tubing roller): only pieces hit it, it bends past the field

        Tag(String name, boolean robot, double friction) {
            this(name, robot, false, friction, null);
        }

        Tag(String name, boolean robot, boolean floor, double friction, Ball ball) {
            this.name = name;
            this.robot = robot;
            this.floor = floor;
            this.friction = friction;
            this.ball = ball;
        }
    }

    /** A game piece in the world: a ball. */
    static final class Ball {
        final int id;
        final SimField.PieceType type;
        final DBody body;
        final DGeom geom;
        double ignoreRobotUntil = 0;     // just launched: it does not hit the robot that launched it yet
        final Set<String> touchingParts = new LinkedHashSet<>();   // robot parts it touched in the last step

        Ball(int id, SimField.PieceType type, DBody body, DGeom geom) {
            this.id = id;
            this.type = type;
            this.body = body;
            this.geom = geom;
        }
    }

    private final List<Ball> balls = new ArrayList<>();
    private int nextBall = 1;
    private double time = 0;

    private DWorld world;     // set once, in the constructor (not final: the contact code below refers to it)
    private final DSpace space;
    private final DJointGroup contacts = OdeHelper.createJointGroup();
    private final DContactBuffer buffer = new DContactBuffer(MAX_CONTACTS);
    private final DBody robot;
    private final double massKg, inertia, zFloor;
    private final double traction;                  // the most the wheels can push, newtons
    private final List<DGeom> robotParts = new ArrayList<>();
    private double leftOver = 0;
    // What the robot touched in the last step (names), and how hard it was pushing against it.
    private final Set<String> touching = new LinkedHashSet<>();

    /**
     * @param field          the season's field
     * @param chassisHeightIn the chassis box's height (the robot's body centre is half of it above the tiles)
     * @param massKg         the robot's mass
     * @param grip           how hard the wheels can push, as a share of the robot's weight (about 1 on tiles)
     */
    SimWorld(SimField field, double chassisHeightIn, double massKg, double grip) {
        synchronized (SimWorld.class) {
            if (!started) OdeHelper.initODE2(0);
            started = true;
        }
        this.massKg = massKg;
        this.zFloor = -chassisHeightIn / 2 * M;
        this.traction = grip * massKg * G;
        world = OdeHelper.createWorld();
        world.setGravity(0, 0, -G);
        world.setERP(0.2);
        world.setCFM(1e-5);
        world.setContactSurfaceLayer(0.0005);
        world.setContactMaxCorrectingVel(1.0);
        space = OdeHelper.createHashSpace();
        // The walls: four boxes just outside the inside line, 12 in tall.
        double half = field.wallHalf * M, t = 0.15, h = 0.3;     // thick, so a fast piece cannot pass through
        wall(0, half + t / 2, 2 * half + 2 * t, t, h);
        wall(0, -half - t / 2, 2 * half + 2 * t, t, h);
        wall(half + t / 2, 0, t, 2 * half, h);
        wall(-half - t / 2, 0, t, 2 * half, h);
        // The field file's solid and overhead parts: their footprint, from zMin to zMax.
        // A HIVE that can tip is not a block: its two CELLs are open boxes of thin plates (placeHive).
        java.util.Set<String> tipping = new java.util.HashSet<>();
        for (SimField.Hive hv : field.hives) tipping.add(hv.element);
        for (SimField.Element e : field.elements) {
            if (!(e.kind.equals("solid") || e.kind.equals("overhead")) || e.hull.size() < 3 || tipping.contains(e.name)) continue;
            prism(e.name, e.hull, e.zMin, e.zMax, e.piecesOnly);
        }
        for (SimField.Hive hv : field.hives) placeHive(hv);
        // The robot: one body, held flat on the tiles, with its parts as shapes on it.
        robot = OdeHelper.createBody(world);
        DMass mass = OdeHelper.createMass();
        mass.setBoxTotal(massKg, 0.457, 0.457, chassisHeightIn * M);
        robot.setMass(mass);
        inertia = mass.getI().get(2, 2);
        robot.setPosition(0, 0, 0);
        DPlane2DJoint flat = OdeHelper.createPlane2DJoint(world);
        flat.attach(robot, null);
        // The tiles, for game pieces (the robot is held at its height and does not touch them).
        DGeom floor = OdeHelper.createPlane(space, 0, 0, 1, zFloor);
        floor.setData(new Tag("tiles", false, true, 0.6, null));
    }

    // ---------------------------------------------------------------- game pieces

    /** Adds a game piece: centre at (x, y, z) inches (z from the tiles), moving at (vx, vy, vz) inches per second. */
    Ball addBall(SimField.PieceType type, double x, double y, double z, double vx, double vy, double vz) {
        double r = type.diameterIn / 2 * M;
        DBody b = OdeHelper.createBody(world);
        DMass m = OdeHelper.createMass();
        m.setSphereTotal(type.massG / 1000.0, r);
        b.setMass(m);
        b.setPosition(x * M, y * M, zFloor + z * M);
        b.setLinearVel(vx * M, vy * M, vz * M);
        // Slowing down on the tiles is set each step (air()): only while it touches them.
        DGeom g = OdeHelper.createSphere(space, r);
        g.setBody(b);
        Ball ball = new Ball(nextBall++, type, b, g);
        g.setData(new Tag(type.name, false, false, 0.6, ball));
        balls.add(ball);
        return ball;
    }

    /** Holds a piece still at p (inches): a staged piece stays put while nothing touches it. */
    void hold(Ball b, double[] p) {
        b.body.setPosition(p[0] * M, p[1] * M, zFloor + p[2] * M);
        b.body.setLinearVel(0, 0, 0);
        b.body.setAngularVel(0, 0, 0);
    }

    void removeBall(Ball b) {
        balls.remove(b);
        b.geom.destroy();
        b.body.destroy();
    }

    List<Ball> balls() {
        return new ArrayList<>(balls);
    }

    /** A piece's centre, inches (z from the tiles). */
    double[] ballPos(Ball b) {
        DVector3C p = b.body.getPosition();
        return new double[] {p.get0() / M, p.get1() / M, (p.get2() - zFloor) / M};
    }

    /** A piece's speed, inches per second. */
    double[] ballVel(Ball b) {
        DVector3C v = b.body.getLinearVel();
        return new double[] {v.get0() / M, v.get1() / M, v.get2() / M};
    }

    /** The robot's speed over the field, inches per second (x, y). */
    double[] robotVel() {
        DVector3C v = robot.getLinearVel();
        return new double[] {v.get0() / M, v.get1() / M};
    }

    /** Seconds of physics so far. */
    double time() {
        return time;
    }

    private void wall(double cx, double cy, double sx, double sy, double h) {
        DGeom g = OdeHelper.createBox(space, sx, sy, h);
        g.setPosition(cx, cy, zFloor + h / 2);
        g.setData(new Tag("field wall", false, 0.3));
    }

    // A straight-sided solid: the footprint (inches, counterclockwise) from zMin to zMax, as triangles.
    // The plates of each HIVE's two CELLs, where the HIVE is now.
    private final java.util.Map<String, List<DGeom>> hivePlates = new java.util.HashMap<>();

    // Each HIVE's CELL plates ride on one body that the simulator turns (a "kinematic" body: it moves as told,
    // nothing pushes it), so a tipping HIVE carries its pieces and pushes what it meets, instead of jumping.
    private final java.util.Map<String, DBody> hiveBodies = new java.util.HashMap<>();
    // The HIVE's turn (degrees) when its plates were last placed: turnHive() turns the body from there.
    private final java.util.Map<String, Double> hiveBase = new java.util.HashMap<>();

    /** Puts a HIVE's CELLs where the HIVE is now (at the start, and after it tips). */
    void placeHive(SimField.Hive h) {
        List<DGeom> old = hivePlates.remove(h.name);
        if (old != null) for (DGeom g : old) g.destroy();
        DBody body = hiveBodies.get(h.name);
        if (body == null) {
            body = OdeHelper.createBody(world);
            body.setKinematic();
            hiveBodies.put(h.name, body);
        }
        double px = h.pivot[0] * M, py = h.pivot[1] * M, pz = zFloor + h.pivot[2] * M;
        body.setPosition(px, py, pz);
        body.setRotation(new DMatrix3(1, 0, 0, 0, 1, 0, 0, 0, 1));
        body.setLinearVel(0, 0, 0);
        body.setAngularVel(0, 0, 0);
        hiveBase.put(h.name, h.turnDeg);
        List<DGeom> now = new ArrayList<>();
        for (int s : new int[] {1, -1}) {
            for (double[] p : h.plates(s)) {
                DGeom g = OdeHelper.createBox(space, p[12] * M, p[13] * M, p[14] * M);
                g.setBody(body);
                g.setOffsetPosition(p[0] * M - px, p[1] * M - py, zFloor + p[2] * M - pz);
                DMatrix3 r = new DMatrix3();
                // a turn, not a mirror: the third direction is the first two's cross product (a box is the same
                // whichever way its thickness points)
                double[] e1 = {p[3], p[4], p[5]}, e2 = {p[6], p[7], p[8]};
                double[] e3 = {e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]};
                for (int k = 0; k < 3; k++) {
                    r.set(k, 0, e1[k]);
                    r.set(k, 1, e2[k]);
                    r.set(k, 2, e3[k]);
                }
                g.setOffsetRotation(r);
                g.setData(new Tag(h.name, false, 0.3));
                now.add(g);
            }
        }
        hivePlates.put(h.name, now);
    }

    /**
     * Turns a HIVE's CELLs (while it tips): to turnDeg about its pivot's line (along y; the same sense as
     * SimField.Hive.turnY), turning on at degPerSec, so the pieces on its plates are carried along.
     */
    void turnHive(SimField.Hive h, double turnDeg, double degPerSec) {
        DBody body = hiveBodies.get(h.name);
        if (body == null) return;
        double a = Math.toRadians(turnDeg - hiveBase.getOrDefault(h.name, 0.0)), c = Math.cos(a), sn = Math.sin(a);
        // turnY: x' = x cos + z sin, z' = -x sin + z cos (a turn about +y by a)
        body.setRotation(new DMatrix3(c, 0, sn, 0, 1, 0, -sn, 0, c));
        body.setAngularVel(0, Math.toRadians(degPerSec), 0);
    }

    /** Moves a piece to a place (field inches), still. */
    void moveBall(Ball b, double[] p) {
        b.body.setPosition(p[0] * M, p[1] * M, zFloor + p[2] * M);
        b.body.setLinearVel(0, 0, 0);
        b.body.setAngularVel(0, 0, 0);
    }

    private void prism(String name, List<double[]> hull, double zMin, double zMax, boolean piecesOnly) {
        int n = hull.size();
        float[] v = new float[n * 2 * 3];
        double z0 = zFloor + zMin * M, z1 = zFloor + Math.max(zMax, zMin + 0.1) * M;
        for (int i = 0; i < n; i++) {
            double x = hull.get(i)[0] * M, y = hull.get(i)[1] * M;
            v[i * 3] = (float) x; v[i * 3 + 1] = (float) y; v[i * 3 + 2] = (float) z0;
            v[(n + i) * 3] = (float) x; v[(n + i) * 3 + 1] = (float) y; v[(n + i) * 3 + 2] = (float) z1;
        }
        List<Integer> idx = new ArrayList<>();
        for (int i = 1; i + 1 < n; i++) {                       // bottom (facing down) and top (facing up)
            idx.add(0); idx.add(i + 1); idx.add(i);
            idx.add(n); idx.add(n + i); idx.add(n + i + 1);
        }
        for (int i = 0; i < n; i++) {                           // the sides, facing out
            int a = i, b = (i + 1) % n;
            idx.add(a); idx.add(b); idx.add(n + b);
            idx.add(a); idx.add(n + b); idx.add(n + a);
        }
        int[] ix = new int[idx.size()];
        for (int i = 0; i < ix.length; i++) ix[i] = idx.get(i);
        DTriMeshData data = OdeHelper.createTriMeshData();
        data.build(v, ix);
        data.preprocess();
        DGeom g = OdeHelper.createTriMesh(space, data, null, null, null);
        Tag t = new Tag(name, false, 0.3);
        t.piecesOnly = piecesOnly;
        g.setData(t);
    }

    /**
     * The robot's parts as solid boxes on its body (the chassis first): each from the robot's shapes,
     * in the robot frame (f forward, l left, u up from the tiles; inches). Called when the parts move.
     */
    void setRobotParts(List<SimShapes.Piece> pieces) {
        // the same number of boxes: just move them; otherwise make them again
        if (pieces.size() != robotParts.size()) {
            for (DGeom g : robotParts) g.destroy();
            robotParts.clear();
            for (SimShapes.Piece p : pieces) {
                DGeom g = OdeHelper.createBox(space, Math.max(0.005, p.sf * M), Math.max(0.005, p.sl * M), Math.max(0.005, p.su * M));
                g.setBody(robot);
                g.setData(new Tag(p.shape, true, 0.3));
                robotParts.add(g);
            }
        }
        for (int i = 0; i < pieces.size(); i++) {
            SimShapes.Piece p = pieces.get(i);
            DGeom g = robotParts.get(i);
            ((Tag) g.getData()).soft = p.soft;
            // its size too (a claw's palm widens as it opens)
            ((org.ode4j.ode.DBox) g).setLengths(Math.max(0.005, p.sf * M), Math.max(0.005, p.sl * M), Math.max(0.005, p.su * M));
            // robot frame (f, l, u) -> body frame (x = right = -l, y = forward = f, z = up from the body centre)
            g.setOffsetPosition(-p.center[1] * M, p.center[0] * M, p.center[2] * M + zFloor);
            DMatrix3 r = new DMatrix3();
            double[][] axes = {p.ef, p.el, p.eu};        // the box's own forward, left, up
            for (int c = 0; c < 3; c++) {
                r.set(0, c, -axes[c][1]);
                r.set(1, c, axes[c][0]);
                r.set(2, c, axes[c][2]);
            }
            g.setOffsetRotation(r);
        }
    }

    /** Puts the robot somewhere (inches, heading in degrees CCW, 0 = facing +y), standing still. */
    void place(double xIn, double yIn, double headingDeg) {
        robot.setPosition(xIn * M, yIn * M, 0);
        DMatrix3 r = new DMatrix3();
        double h = Math.toRadians(headingDeg);
        r.set(0, 0, Math.cos(h)); r.set(0, 1, -Math.sin(h)); r.set(0, 2, 0);
        r.set(1, 0, Math.sin(h)); r.set(1, 1, Math.cos(h)); r.set(1, 2, 0);
        r.set(2, 0, 0); r.set(2, 1, 0); r.set(2, 2, 1);
        robot.setRotation(r);
        robot.setLinearVel(0, 0, 0);
        robot.setAngularVel(0, 0, 0);
        touching.clear();
    }

    /**
     * Moves time on by dt seconds, the drive pushing toward the wheels' speeds: forward and right in
     * inches per second, turning in degrees per second clockwise.
     */
    void step(double dt, double forwardIn, double rightIn, double cwDeg) {
        leftOver += dt;
        touching.clear();
        for (Ball b : balls) b.touchingParts.clear();
        while (leftOver >= STEP) {
            leftOver -= STEP;
            // A fast piece (a shot) would move several inches in one step and could pass through a thin
            // part: then the step is cut into smaller ones (each piece moves at most about 0.5 in).
            double fastest = 0;
            for (Ball b : balls) {
                DVector3C v = b.body.getLinearVel();
                fastest = Math.max(fastest, Math.sqrt(v.get0() * v.get0() + v.get1() * v.get1() + v.get2() * v.get2()));
            }
            int parts = (int) Math.min(8, Math.max(1, Math.ceil(fastest * STEP / 0.0127)));
            for (int k = 0; k < parts; k++) {
                time += STEP / parts;
                drive(forwardIn * M, rightIn * M, -Math.toRadians(cwDeg));
                air(STEP / parts);
                space.collide(null, near);
                world.quickStep(STEP / parts);
                contacts.empty();
            }
        }
    }

    // Air on each piece: drag against its motion, F = 1/2 rho Cd A v^2; and the lift from its spin (Magnus), F =
    // 1/2 rho A r (w x v), the usual rule for a slowly spinning ball (lift coefficient r w / v, at most 0.5).
    // On the tiles it also slows down rolling: rates per second turned into the engine's per-step share (a ball
    // at 1 m/s rolls about 2 m: a guess to check against a real roll).
    static final double AIR_DENSITY = 1.2;          // kg/m^3, at about 20 C near sea level
    private static final double ROLL_SLOW = 0.05, SPIN_SLOW = 0.5;   // per second, on the tiles

    private void air(double dt) {
        for (Ball b : balls) {
            double r = b.type.diameterIn / 2 * M;
            DVector3C p = b.body.getPosition(), v = b.body.getLinearVel(), w = b.body.getAngularVel();
            boolean onTiles = p.get2() - zFloor < r + 0.003;
            b.body.setLinearDamping(onTiles ? 1 - Math.exp(-ROLL_SLOW * dt) : 0);
            b.body.setAngularDamping(onTiles ? 1 - Math.exp(-SPIN_SLOW * dt) : 0);
            double speed = Math.sqrt(v.get0() * v.get0() + v.get1() * v.get1() + v.get2() * v.get2());
            if (speed < 1e-6) continue;
            double area = Math.PI * r * r, drag = 0.5 * AIR_DENSITY * b.type.dragCd * area * speed;
            double[] lift = {w.get1() * v.get2() - w.get2() * v.get1(), w.get2() * v.get0() - w.get0() * v.get2(),
                    w.get0() * v.get1() - w.get1() * v.get0()};
            double spin = Math.sqrt(w.get0() * w.get0() + w.get1() * w.get1() + w.get2() * w.get2());
            double cl = r * spin / speed, k = 0.5 * AIR_DENSITY * area * r * (cl > 0.5 ? 0.5 / cl : 1);
            b.body.addForce(-drag * v.get0() + k * lift[0], -drag * v.get1() + k * lift[1], -drag * v.get2() + k * lift[2]);
        }
    }

    /** Sets a piece's spin, radians per second about (x, y, z) in the field frame. */
    void spin(Ball b, double wx, double wy, double wz) {
        b.body.setAngularVel(wx, wy, wz);
    }

    // The wheels' grip: a push toward the wanted speed, never more than the traction allows. The same for
    // turning (with a 0.2 m lever: about where the wheels are).
    private void drive(double forward, double right, double ccw) {
        double h = heading();
        double wantX = -Math.sin(h) * forward + Math.cos(h) * right, wantY = Math.cos(h) * forward + Math.sin(h) * right;
        DVector3C v = robot.getLinearVel();
        double tau = 0.02;                                    // how quickly the grip catches up, seconds
        double fx = massKg * (wantX - v.get0()) / tau, fy = massKg * (wantY - v.get1()) / tau;
        double f = Math.hypot(fx, fy);
        if (f > traction) { fx *= traction / f; fy *= traction / f; }
        robot.addForce(fx, fy, 0);
        double tq = inertia * (ccw - robot.getAngularVel().get2()) / tau, maxTq = traction * 0.2;
        robot.addTorque(0, 0, Math.max(-maxTq, Math.min(maxTq, tq)));
    }

    // Called for every two shapes whose boxes overlap: the contacts between them.
    private final DGeom.DNearCallback near = (data, o1, o2) -> {
        Tag a = (Tag) o1.getData(), b = (Tag) o2.getData();
        if (a == null || b == null || (a.robot && b.robot)) return;    // the robot's parts do not hit each other
        boolean ballA = a.ball != null, ballB = b.ball != null;
        if (!a.robot && !b.robot && !ballA && !ballB) return;          // field against field: never
        if ((a.robot && b.floor) || (b.robot && a.floor)) return;      // the robot is held at its height
        if ((a.robot && b.piecesOnly) || (b.robot && a.piecesOnly)) return;
        if ((a.soft && !ballB) || (b.soft && !ballA)) return;           // soft tubing bends past the field
        Ball ball = ballA ? a.ball : ballB ? b.ball : null;
        boolean robotPart = a.robot || b.robot;
        if (ball != null && robotPart && time < ball.ignoreRobotUntil) return;
        int n = OdeHelper.collide(o1, o2, MAX_CONTACTS, buffer.getGeomBuffer());
        if (n == 0) return;
        if (ball != null && robotPart) ball.touchingParts.add(a.robot ? a.name : b.name);
        else if (ball == null) touching.add(a.robot ? b.name : a.name);
        for (int i = 0; i < n; i++) {
            DContact c = buffer.get(i);
            c.surface.mode = OdeConstants.dContactApprox1;
            c.surface.mu = Math.sqrt(a.friction * b.friction);   // Approx1: a friction coefficient
            if (ball != null) {
                c.surface.mode |= OdeConstants.dContactBounce;
                c.surface.bounce = ball.type.bounce;
                c.surface.bounce_vel = 0.1;
                if (robotPart && this.surfaceSpeed != null) roll(c, a.robot ? a.name : b.name, a.robot);
            }
            OdeHelper.createContactJoint(world, contacts, c).attach(o1.getBody(), o2.getBody());
        }
    };

    /**
     * A spinning robot part's surface speed (inches per second, field frame) at a field point (inches), or
     * null if the part does not spin. Set by the robot's pieces.
     */
    interface SurfaceSpeed {
        double[] at(String part, double[] fieldPointIn);
    }

    SurfaceSpeed surfaceSpeed = null;

    // A turning roller drags a piece along with its surface (as far as the grip allows): the contact's
    // surface moves at the roller's surface speed, minus the part along the contact's normal.
    private void roll(DContact c, String part, boolean robotFirst) {
        DVector3C p = c.geom.pos, n = c.geom.normal;
        double[] v = surfaceSpeed.at(part, new double[] {p.get0() / M, p.get1() / M, (p.get2() - zFloor) / M});
        if (v == null) return;
        double dot = v[0] * n.get0() + v[1] * n.get1() + v[2] * n.get2();
        double tx = v[0] - dot * n.get0(), ty = v[1] - dot * n.get1(), tz = v[2] - dot * n.get2();
        double t = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (t < 0.01) return;
        c.fdir1.set(tx / t, ty / t, tz / t);
        c.surface.mode |= OdeConstants.dContactFDir1 | OdeConstants.dContactMotion1;
        // ode4j's contact joint holds (body 1 - body 2) along fdir1 at motion1 (sign checked by
        // SimPiecesTest.aSpinningRollerDragsAPieceAlong)
        c.surface.motion1 = (robotFirst ? -t : t) * M;
    }

    double xIn() { return robot.getPosition().get0() / M; }
    double yIn() { return robot.getPosition().get1() / M; }
    double headingDeg() { return Math.toDegrees(heading()); }

    /** The robot's turning speed, degrees per second, counterclockwise = +. */
    double yawRateDeg() {
        return Math.toDegrees(robot.getAngularVel().get2());
    }

    /** What the robot touched in the last step, by name (empty if nothing). */
    List<String> touching() {
        return new ArrayList<>(touching);
    }

    private double heading() {
        // the body's forward (its y axis) in the world: (-sin h, cos h)
        org.ode4j.math.DMatrix3C r = robot.getRotation();
        return Math.atan2(-r.get(0, 1), r.get(1, 1));
    }

    void close() {
        contacts.destroy();
        space.destroy();
        world.destroy();
    }
}
