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
        assertEquals("4 POLLEN in each GARDEN and FLOWER, 3 NECTAR in each up CELL", 30, r.pieces.drawn().size());
        assertEquals(22, r.pieces.inStacks());
        run(r, 1.0);
        assertEquals("they stay put", 30, r.pieces.drawn().size());
        int low = 0;
        for (Object[] d : r.pieces.drawn()) if ((Double) d[3] < 2) { low++; assertEquals(1.4, (Double) d[3], 0.1); }
        assertEquals("on the tiles: 8 in the GARDENs, the lowest in each FLOWER", 12, low);
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
        assertEquals(30, r.pieces.drawn().size());
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
        assertEquals(31, r.pieces.drawn().size());
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
        assertEquals(31, r.pieces.drawn().size());
    }

    // The red-wall FLOWER (opening centre 23.39, -68.04; its POLLEN 2.4 in in from the front face): an intake
    // against the front takes the bottom POLLEN, and the next one drops into its place.
    @Test public void theIntakeTakesTheBottomPollenOutOfAFlower() throws Exception {
        SimRobot r = robot("collect.intake = shape=intake, motor=intake, holds=8\n");
        r.setPose(23.39, -68.04 + 2.41 + 9.6, 180);          // facing the FLOWER (-y), intake 0.6 in from its face
        run(r, 0.3);
        assertEquals("the intake is off: nothing taken", 22, r.pieces.inStacks());
        r.motors.get("intake").device.setPower(1);
        run(r, 0.05);
        assertEquals("the bottom one taken", 5, r.pieces.storedCount());
        int left = 0;
        double lowest = 99;
        for (Object[] d : r.pieces.drawn()) {
            if (Math.abs((Double) d[1] - 23.39) < 0.1 && Math.abs((Double) d[2] + 68.04) < 0.1) { left++; lowest = Math.min(lowest, (Double) d[3]); }
        }
        assertTrue("the next ones dropped down: " + left + " left, lowest at " + lowest, left >= 1 && lowest < 1.5);
        run(r, 2.0);
        assertEquals("it keeps taking them until the FLOWER is empty", 8, r.pieces.storedCount());
        assertEquals(18, r.pieces.inStacks());
        // from the far side of the field it takes nothing
        SimRobot far = robot("collect.intake = shape=intake, motor=intake, holds=8\n");
        far.setPose(0, -40, 180);
        far.motors.get("intake").device.setPower(1);
        run(far, 0.5);
        assertEquals(22, far.pieces.inStacks());
    }

    // A shot table (the launcher program's, or filmed shots): each shot takes its speed, angle and exit point from it.
    @Test public void aShotTableSetsTheShot() throws Exception {
        File table = tmp.newFile("launcher.json");
        Files.write(table.toPath(), ("{\"pieces\": {\"POLLEN\": ["
                + "{\"shaftRpm\": 1000, \"shot\": 1, \"speedIn\": [100, 0], \"angleDeg\": [60, 0], \"backspin\": [0, 0], \"exit\": [3, 2]},"
                + "{\"shaftRpm\": 3000, \"shot\": 1, \"speedIn\": [300, 0], \"angleDeg\": [40, 0], \"backspin\": [0, 0], \"exit\": [3, 2]}]}}").getBytes("UTF-8"));
        SimRobot r = robot("shoot.launcher = shape=flywheel, table=" + table.getAbsolutePath() + "\n");
        r.setPose(0, -40, 0);
        SimMotor fw = r.motors.get("flywheel");
        fw.device.setPower(1);
        run(r, 2.0);
        double rpm = fw.shaftSpeed() / fw.ticksPerRev * 60;
        int before = r.pieces.drawn().size();
        r.pieces.shoot(fw);
        Object[] ball = null;
        for (Object[] d : r.pieces.drawn()) if (Math.abs((Double) d[2] + 40) < 8 && (Double) d[3] > 2.5) ball = d;
        assertEquals(before + 1, r.pieces.drawn().size());
        assertTrue("it left from the table's exit point (3 in forward and 2 up of the flywheel's centre)", ball != null);
        // its speed: blended between the rows by the wheel's speed (or grown past the last row)
        SimShotTable t = new SimShotTable(table.toPath(), "test", 1);
        double want = t.pick("POLLEN", rpm).speedIn;
        assertTrue("table speed at " + rpm + " rpm: " + want, want > 100);
        assertEquals(1, r.pieces.launched());
    }

    // A CR servo can feed a flywheel (a windmill); speed= gives a fixed exit speed (a catapult).
    @Test public void aCrServoFeedsAndSpeedFixesTheShot() throws Exception {
        SimRobot r = robot("motor.flywheel = flywheel, part=gobilda-5203-6000, feeder=spinner\n"
                + "shoot.launcher = shape=flywheel, angle=45, speed=200\n");
        r.setPose(0, -40, 0);
        r.motors.get("flywheel").device.setPower(1);
        run(r, 2.0);
        r.crServos.get("spinner")[0] = 1;
        run(r, 0.05);
        r.crServos.get("spinner")[0] = 0;
        assertEquals("one shot from the CR servo feeder", 1, r.pieces.launched());
        SimWorld.Ball last = r.world.balls().get(r.world.balls().size() - 1);
        double[] v = r.world.ballVel(last);
        assertEquals("speed=200 in/s (less what gravity and air took in the first 0.05 s)", 200, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 20);
        try {
            robot("motor.flywheel = flywheel, part=gobilda-5203-6000, feeder=nothing\n");
            assertTrue("a feeder that is not a device is refused", false);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("feeder=nothing"));
        }
    }

    // Preloaded or not (the page's checkbox, "set preload", or "preload = off" in the robot file), and the
    // field put back without moving the robot.
    @Test public void preloadCanBeTurnedOffAndTheFieldReset() throws Exception {
        SimRobot r = robot("preload = off\n");
        assertEquals("starts empty", 0, r.pieces.storedCount());
        r.pieces.setPreloaded(true);
        assertEquals("preloaded now", 4, r.pieces.storedCount());
        r.pieces.place("POLLEN", 0, 0, 0);
        r.setPose(40, -40, 90);
        r.pieces.setPreloaded(false);
        assertEquals("emptied", 0, r.pieces.storedCount());
        r.pieces.reset();
        assertEquals("the field is back: the extra piece is gone", 30, r.pieces.drawn().size());
        assertEquals("still not preloaded", 0, r.pieces.storedCount());
        assertEquals("the robot stayed", 40, r.x, 0.5);
        r.pieces.setPreloaded(true);
        r.resetPose();
        assertEquals(4, r.pieces.storedCount());
    }
}
