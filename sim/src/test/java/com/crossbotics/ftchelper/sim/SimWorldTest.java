package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The physics engine: driving, bumping, sliding, parts hitting the field. */
public class SimWorldTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private SimRobot robot(String extra) throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File f = tmp.newFile();
        Files.write(f.toPath(), (base + "\n" + extra).getBytes("UTF-8"));
        return SimRobot.load(f.toPath());
    }

    // A hand push for this long, stepped like the simulator does (10 ms).
    private static void push(SimRobot r, double forward, double right, double sec) {
        r.push(forward, right, sec);
        for (double t = 0; t < sec + 0.3; t += 0.01) r.step(0.01);
    }

    @Test public void drivesAtTheWheelsSpeedInTheOpen() throws Exception {
        SimRobot r = robot("");
        r.setPose(40, -40, 0);
        push(r, 20, 0, 1.0);                 // 20 in/s for 1 s: about 20 in forward (+y)
        assertEquals(40, r.x, 0.3);
        assertEquals(-20, r.y, 1.0);
        assertEquals(0, r.headingDeg, 0.5);
        assertEquals("", r.bumpedInto);
    }

    @Test public void stopsAtAWallAndSlidesAlongIt() throws Exception {
        SimRobot r = robot("");
        r.setPose(40, -55, 0);
        push(r, -30, 0, 1.5);                 // backward into the red wall (y = -70.6)
        assertEquals("field wall", r.bumpedInto);
        assertEquals("stops at the wall: robot centre 9 in from it", -70.585 + 9, r.y, 0.3);
        // pushing back-and-right along the wall: it slides right, still touching it
        push(r, -10, 20, 1.0);
        assertTrue("slid along the wall: x " + r.x, r.x > 55);
        assertEquals(-70.585 + 9, r.y, 0.3);
    }

    @Test public void wheelsKeepCountingWhilePinnedAgainstAWall() throws Exception {
        SimRobot r = robot("");
        r.setPose(40, -55, 0);
        // drive backward into the red wall (the left wheels are mounted mirrored: +power on them is backward)
        for (int i = 0; i < r.wheels.size(); i++) r.wheels.get(i).device.setPower(i % 2 == 0 ? 0.5 : -0.5);
        for (double t = 0; t < 1.5; t += 0.01) r.step(0.01);
        int before = r.wheels.get(1).encoder();
        for (double t = 0; t < 1.0; t += 0.01) r.step(0.01);
        assertEquals("pinned at the wall", -70.585 + 9, r.y, 0.3);
        assertTrue("its wheels still turn (they slip): " + (r.wheels.get(1).encoder() - before),
                Math.abs(r.wheels.get(1).encoder() - before) > 500);
    }

    @Test public void theRobotPushesAPiece() throws Exception {
        SimRobot r = robot("");
        r.setPose(30, -40, 0);
        r.pieces.place("POLLEN", 30, -25, 0);              // 6 in ahead of the robot's front
        push(r, 15, 0, 1.5);
        double y = (Double) r.pieces.drawn().stream().filter(d -> Math.abs((Double) d[1] - 30) < 6).findFirst().get()[2];
        assertTrue("pushed forward: y " + y, y > -15);
    }

    @Test public void aSoftPartBendsPastTheFieldButTouchesPieces() throws Exception {
        // the same low pole as below, soft (tubing): the HIVE base does not stop it, the chassis does
        SimRobot r = robot("physics = engine\nshape.pole = box, at=20,0,2, size=1x1x1, soft=yes\n");
        r.setPose(0, -60, 0);
        push(r, 15, 0, 3.0);
        assertTrue("stopped by the chassis, not the pole: y " + r.y, r.y > -24.73 - 20.5 + 5);
        // it still pushes a piece
        SimRobot q = robot("physics = engine\nshape.pole = box, at=20,0,2, size=1x1x1, soft=yes\n");
        q.setPose(45, -60, 0);
        q.pieces.place("POLLEN", 45, -36, 0);               // 4 in ahead of the pole
        push(q, 15, 0, 1.5);
        double y = (Double) q.pieces.drawn().stream().filter(d -> Math.abs((Double) d[1] - 45) < 6 && (Double) d[2] > -50).findFirst().get()[2];
        assertTrue("pushed forward: y " + y, y > -30);
    }

    @Test public void aPartHitsTheFieldTooAndThePhysicsCanBeOff() throws Exception {
        // a 1 in pole reaching 20 in in front of the robot, low (the chassis alone would stop at y -33.7)
        SimRobot r = robot("physics = engine\nshape.pole = box, at=20,0,2, size=1x1x1\n");
        r.setPose(0, -60, 0);                 // facing +y, the HIVE frame base (y -24.7..-15.5) ahead
        push(r, 15, 0, 3.0);
        assertTrue("the pole hits the HIVE: " + r.bumpedInto, r.bumpedInto.contains("HIVE"));
        assertEquals("stopped by the pole's tip, 20.5 in ahead of the robot's centre", -24.73 - 20.5, r.y, 0.5);
        // physics = simple: the old rule, a bump stops the whole move
        SimRobot s = robot("physics = simple\n");
        assertTrue(s.world == null);
    }

    // Air: a POLLEN (25 g, 2.8 in) thrown level at 10 m/s, high up, slows as drag says: dv/dt = -k v^2 with
    // k = 1/2 rho Cd A / m = 0.0477 per metre, so after 0.1 s it is at 10 / (1 + k 10 0.1) = 9.545 m/s.
    @Test public void airSlowsAFlyingPieceAndBackspinLiftsIt() throws Exception {
        SimRobot r = robot("");
        r.setPose(0, -60, 0);
        SimField.PieceType pollen = r.field.pieceTypes.get("POLLEN");
        SimWorld.Ball b = r.world.addBall(pollen, -40, 20, 200, 10 / SimWorld.M, 0, 0);
        for (int i = 0; i < 10; i++) r.world.step(0.01, 0, 0, 0);
        double vx = r.world.ballVel(b)[0] * SimWorld.M;
        assertEquals("drag: 9.545 m/s after 0.1 s", 9.545, vx, 0.01);
        // the same throw with backspin (it turns about -y when it flies along +x) rises above the plain one
        SimWorld.Ball plain = r.world.addBall(pollen, -40, 40, 200, 10 / SimWorld.M, 0, 0);
        SimWorld.Ball spun = r.world.addBall(pollen, -40, 50, 200, 10 / SimWorld.M, 0, 0);
        r.world.spin(spun, 0, -10 / (1.4 * SimWorld.M), 0);
        for (int i = 0; i < 30; i++) r.world.step(0.01, 0, 0, 0);
        double rise = r.world.ballPos(spun)[2] - r.world.ballPos(plain)[2];
        // lift coefficient r w / v = 1 is capped at 0.5: lift = 1/2 rho A 0.5 v^2 = 0.12 N on 0.245 N of weight
        assertTrue("backspin holds it up: " + rise + " in higher after 0.3 s", rise > 3 && rise < 12);
    }
}
