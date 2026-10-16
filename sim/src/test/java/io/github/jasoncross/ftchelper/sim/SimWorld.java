package io.github.jasoncross.ftchelper.sim;

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
        final boolean robot;
        final double friction;

        Tag(String name, boolean robot, double friction) {
            this.name = name;
            this.robot = robot;
            this.friction = friction;
        }
    }

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
        double half = field.wallHalf * M, t = 0.05, h = 0.3;
        wall(0, half + t / 2, 2 * half + 2 * t, t, h);
        wall(0, -half - t / 2, 2 * half + 2 * t, t, h);
        wall(half + t / 2, 0, t, 2 * half, h);
        wall(-half - t / 2, 0, t, 2 * half, h);
        // The field file's solid and overhead parts: their footprint, from zMin to zMax.
        for (SimField.Element e : field.elements) {
            if (!(e.kind.equals("solid") || e.kind.equals("overhead")) || e.hull.size() < 3) continue;
            prism(e.name, e.hull, e.zMin, e.zMax);
        }
        // The robot: one body, held flat on the tiles, with its parts as shapes on it.
        robot = OdeHelper.createBody(world);
        DMass mass = OdeHelper.createMass();
        mass.setBoxTotal(massKg, 0.457, 0.457, chassisHeightIn * M);
        robot.setMass(mass);
        inertia = mass.getI().get(2, 2);
        robot.setPosition(0, 0, 0);
        DPlane2DJoint flat = OdeHelper.createPlane2DJoint(world);
        flat.attach(robot, null);
    }

    private void wall(double cx, double cy, double sx, double sy, double h) {
        DGeom g = OdeHelper.createBox(space, sx, sy, h);
        g.setPosition(cx, cy, zFloor + h / 2);
        g.setData(new Tag("field wall", false, 0.3));
    }

    // A straight-sided solid: the footprint (inches, counterclockwise) from zMin to zMax, as triangles.
    private void prism(String name, List<double[]> hull, double zMin, double zMax) {
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
        g.setData(new Tag(name, false, 0.3));
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
        while (leftOver >= STEP) {
            leftOver -= STEP;
            drive(forwardIn * M, rightIn * M, -Math.toRadians(cwDeg));
            space.collide(null, near);
            world.quickStep(STEP);
            contacts.empty();
        }
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
        if (!a.robot && !b.robot) return;                               // field against field: never
        int n = OdeHelper.collide(o1, o2, MAX_CONTACTS, buffer.getGeomBuffer());
        if (n == 0) return;
        touching.add(a.robot ? b.name : a.name);
        for (int i = 0; i < n; i++) {
            DContact c = buffer.get(i);
            c.surface.mode = OdeConstants.dContactApprox1;
            c.surface.mu = Math.sqrt(a.friction * b.friction);   // Approx1: a friction coefficient
            OdeHelper.createContactJoint(world, contacts, c).attach(o1.getBody(), o2.getBody());
        }
    };

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
