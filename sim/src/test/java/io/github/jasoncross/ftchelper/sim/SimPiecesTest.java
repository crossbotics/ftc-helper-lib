package io.github.jasoncross.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Game pieces: staging and preload, the intake, the launcher, the claw, the FLOWER goals. */
public class SimPiecesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private SimRobot robot(String extra) throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File f = tmp.newFile();
        Files.write(f.toPath(), (base + "\n" + extra).getBytes("UTF-8"));
        return SimRobot.load(f.toPath());
    }

    private static void run(SimRobot r, double sec) {
        for (double t = 0; t < sec; t += 0.01) r.step(0.01);
    }

    @Test public void piecesAreStagedAndTheRobotStartsWithItsPreload() throws Exception {
        SimRobot r = robot("");
        assertEquals("BIOBUZZ preload: 4 POLLEN", 4, r.pieces.storedCount());
        assertEquals("4 POLLEN in each GARDEN", 8, r.pieces.drawn().size());
        run(r, 1.0);
        assertEquals("they stay put on the tiles", 8, r.pieces.drawn().size());
        for (Object[] d : r.pieces.drawn()) assertEquals(1.4, (Double) d[3], 0.1);
    }

    @Test public void theIntakeTakesInAPieceInFront() throws Exception {
        SimRobot r = robot("collect.intake = shape=intake, motor=intake, holds=8\n");
        r.setPose(30, -30, 0);                              // facing +y
        r.pieces.place("POLLEN", 30, -16, 0);              // 3.6 in ahead of the robot's front (y -21): out of reach
        r.motors.get("intake").device.setPower(1);
        run(r, 0.3);
        assertEquals("not taken from afar", 4, r.pieces.storedCount());
        r.push(10, 0, 1.0);                                  // roll forward into it
        run(r, 1.5);
        assertEquals("taken in", 5, r.pieces.storedCount());
        assertEquals(8, r.pieces.drawn().size());
    }

    @Test public void theLauncherShootsAHeldPieceThatFlies() throws Exception {
        SimRobot r = robot("");
        r.setPose(0, -40, 0);
        r.motors.get("flywheel").device.setPower(1);
        run(r, 2.0);                                         // spin up
        r.motors.get("feeder").device.setPower(1);
        run(r, 0.1);
        r.motors.get("feeder").device.setPower(0);
        assertEquals("one shot", 1, r.pieces.launched());
        assertEquals(3, r.pieces.storedCount());
        assertEquals(9, r.pieces.drawn().size());
        double maxZ = 0;
        for (int i = 0; i < 100; i++) {
            run(r, 0.01);
            for (Object[] d : r.pieces.drawn()) if ((Double) d[2] > -38) maxZ = Math.max(maxZ, (Double) d[3]);
        }
        assertTrue("it flies up and forward: highest " + maxZ + " in", maxZ > 15);
    }

    @Test public void aPieceDroppedIntoAFlowerScores() throws Exception {
        SimRobot r = robot("");
        r.pieces.place("POLLEN", 23.57, -68.04, 30);        // above the red-wall FLOWER's opening
        run(r, 1.0);
        assertEquals(1, (int) r.pieces.scored().get("FLOWER (red wall, audience half)"));
        // dropped 2.5 in off the centre: it lands on the rim and rolls in (it counts)
        SimRobot o = robot("");
        o.pieces.place("POLLEN", 23.57 + 2.5, -68.04, 30);
        run(o, 1.5);
        assertEquals(1, (int) o.pieces.scored().get("FLOWER (red wall, audience half)"));
        // a piece just set above the opening, not yet fallen in, has not scored
        SimRobot q = robot("");
        q.pieces.place("POLLEN", 23.57, -68.04, 26);
        q.pieces.step();
        assertEquals(0, (int) q.pieces.scored().get("FLOWER (red wall, audience half)"));
    }

    @Test public void theClawHoldsAPieceAndDropsItWhenItOpens() throws Exception {
        SimRobot r = robot("");
        r.setPose(0, -40, 0);
        r.servos.get("claw").device.setPosition(0);         // closed (a piece that comes into it is held)
        run(r, 1.0);
        // a piece in the claw: the middle of its fingers, from the shapes
        SimShapes.Piece palm = null;
        for (SimShapes.Piece p : r.shapes.pieces()) if (p.shape.equals("claw")) { palm = p; break; }
        double f = palm.center[0] + palm.eu[0] * 1.5, u = palm.center[2] + palm.eu[2] * 1.5;
        r.pieces.place("POLLEN", 0, -40 + f, Math.max(1.4, u));
        run(r, 0.05);
        assertEquals("held: 4 preload + 1 in the claw", 5, r.pieces.storedCount());
        r.servos.get("claw").device.setPosition(0.9);       // open
        run(r, 1.5);
        assertEquals("dropped", 4, r.pieces.storedCount());
        assertEquals(9, r.pieces.drawn().size());
    }
}
