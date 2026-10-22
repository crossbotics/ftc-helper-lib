package com.crossbotics.ftchelper.sim;

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
        assertEquals("in the FLOWERs (the CELL NECTAR are loose pieces in their CELLs)", 16, r.pieces.inStacks());
        run(r, 1.0);
        assertEquals("they stay put", 30, r.pieces.drawn().size());
        int low = 0;
        // (a FLOWER's lowest POLLEN rests on its floor plate, 0.35 in up: 1.75)
        for (Object[] d : r.pieces.drawn()) if ((Double) d[3] < 2) { low++; assertEquals(1.4, (Double) d[3], 0.4); }
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

    @Test public void thePreloadIsNoMoreThanTheIntakeHolds() throws Exception {
        SimRobot r = robot("collect.intake = shape=intake, motor=intake, holds=3\n");
        assertEquals("3 of the 4 POLLEN fit", 3, r.pieces.storedCount());
    }

    @Test public void anIntakeRunningOutPushesTheHeldPiecesOut() throws Exception {
        SimRobot r = robot("");
        r.setPose(30, -30, 0);
        int before = r.pieces.drawn().size();
        r.motors.get("intake").device.setPower(-1);        // out
        run(r, 0.5);
        assertTrue("some came out: " + r.pieces.storedCount(), r.pieces.storedCount() < 4);
        run(r, 1.5);
        assertEquals("all 4 out", 0, r.pieces.storedCount());
        assertEquals("on the field now", before + 4, r.pieces.drawn().size());
        r.motors.get("intake").device.setPower(0);
        run(r, 0.5);
        assertEquals("a stopped intake takes nothing back", 0, r.pieces.storedCount());
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

    // With the rules file (biobuzz-2026.rules.json) a piece that falls into a FLOWER's top stays there, on its
    // stack (SimGameTest has the scoring); it counts as scored in that FLOWER.
    @Test public void aPieceDroppedIntoAFlowerScores() throws Exception {
        SimRobot r = robot("");
        r.pieces.place("POLLEN", 23.57, -68.04, 30);        // above the red-wall FLOWER's opening
        run(r, 1.0);
        assertEquals(1, (int) r.pieces.scored().get("FLOWER (red wall, audience half)"));
        assertEquals("it is in the FLOWER now, on top of the 4 staged POLLEN", 17, r.pieces.inStacks());
        assertEquals(5, r.pieces.flowers().get("FLOWER (red wall, audience half)").size());
        assertEquals("still on the field (drawn in the FLOWER)", 31, r.pieces.drawn().size());
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

    // Without a rules file next to the field file, the simulator works as before: a piece in a goal is counted and
    // taken off the field, and a HIVE tips at once.
    @Test public void withoutARulesFileAGoalTakesThePieceOff() throws Exception {
        File dir = tmp.newFolder("norules");
        File field = new File(dir, "plain.json");
        Files.copy(new File("fields/biobuzz-2026.json").toPath(), field.toPath());
        SimRobot r = robot("field = " + field.getAbsolutePath() + "\n");
        assertTrue(r.field.rules == null);
        r.pieces.place("POLLEN", 23.57, -68.04, 30);
        run(r, 1.0);
        assertEquals(1, (int) r.pieces.scored().get("FLOWER (red wall, audience half)"));
        assertEquals("taken off", 16, r.pieces.inStacks());
        assertEquals(30, r.pieces.drawn().size());
        assertTrue("no FLOWER stacks, no score", r.pieces.flowers().isEmpty());
        synchronized (r) { assertEquals("{\"rules\":false}", r.pieces.score.json()); }
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
    // A turning roller drags a piece with its surface: a spinner standing up (axis up) at the robot's left
    // turns counterclockwise seen from above with + power, so its outer side moves backward.
    @Test public void aSpinningRollerDragsAPieceAlong() throws Exception {
        for (int way : new int[] {1, -1}) {
            SimRobot r = robot("shape.spin = roller, at=8,10.5,1.5, size=3.5x3.5x1, axis=up, motor=intake\n");
            r.setPose(48, -45, 0);                              // facing +y (its left is -x), in the open
            run(r, 0.5);                                         // the robot settles first
            double x0 = r.x - 13.7, y0 = r.y + 8;
            r.pieces.place("POLLEN", x0, y0, 1.4);              // just beside the spinner's outer side
            r.motors.get("intake").device.setPower(0.1 * way);  // slow: a 1150 RPM motor would fling it
            run(r, 0.3);
            r.push(0, -3, 0.4);                                  // lean the spinner on it (to the left)
            run(r, 0.4);
            double y = y0;
            for (Object[] d : r.pieces.drawn()) if (Math.abs((Double) d[1] - x0) < 8 && Math.abs((Double) d[2] - y0) < 20 && (Double) d[3] < 3) y = (Double) d[2];
            if (way > 0) assertTrue("+ power: dragged back, y " + (y - y0), y < y0 - 0.5);
            else assertTrue("- power: dragged forward, y " + (y - y0), y > y0 + 0.5);
        }
    }

    // A part that reaches into the opening (intake off) knocks the bottom POLLEN out of the FLOWER onto the
    // tiles; the next one drops into its place.
    @Test public void aLowPartKnocksTheBottomPollenOutOfAFlower() throws Exception {
        SimRobot r = robot("shape.pole = box, at=11,0,1.8, size=1x1x1\n");
        r.setPose(19.39, -50, 180);                          // facing the red-wall FLOWER (-y), 4 in to its right
        r.push(10, 0, 1.0);                                  // the pole goes into the opening beside the POLLEN
        run(r, 1.2);
        r.push(0, -10, 1.5);                                 // and sweeps across it, out the other side
        run(r, 2.5);
        int out = 0;
        for (Object[] d : r.pieces.drawn()) {
            double x = (Double) d[1], y = (Double) d[2];
            if (Math.hypot(x - 23.39, y + 68.04) > 4 && x > 0 && y > -72 && y < -60 && (Double) d[3] < 2) out++;   // rolled off along the wall
        }
        assertTrue("a POLLEN knocked out of the FLOWER", out >= 1);
        assertEquals("3 left in the FLOWER (the lowest one dropped into the opening)", 15, r.pieces.inStacks());
    }

    @Test public void theIntakeTakesTheBottomPollenOutOfAFlower() throws Exception {
        SimRobot r = robot("collect.intake = shape=intake, motor=intake, holds=8\n");
        r.setPose(23.39, -68.04 + 2.41 + 9.6, 180);          // facing the FLOWER (-y), intake 0.6 in from its face
        run(r, 0.3);
        assertEquals("the intake is off: nothing taken", 16, r.pieces.inStacks());
        r.motors.get("intake").device.setPower(1);
        run(r, 0.05);
        assertEquals("the bottom one taken", 5, r.pieces.storedCount());
        int left = 0;
        for (Object[] d : r.pieces.drawn()) {
            if (Math.abs((Double) d[1] - 23.39) < 0.5 && Math.abs((Double) d[2] + 68.04) < 0.5) left++;
        }
        assertEquals("3 left in the FLOWER", 3, left);
        run(r, 0.3);
        assertTrue("the next one dropped into its place and was taken too", r.pieces.storedCount() >= 6);
        run(r, 2.0);
        assertEquals("it keeps taking them until the FLOWER is empty", 8, r.pieces.storedCount());
        assertEquals(12, r.pieces.inStacks());
        // from the far side of the field it takes nothing
        SimRobot far = robot("collect.intake = shape=intake, motor=intake, holds=8\n");
        far.setPose(0, -40, 180);
        far.motors.get("intake").device.setPower(1);
        run(far, 0.5);
        assertEquals(16, far.pieces.inStacks());
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

    // hood=: the shot angle follows where the hood servo really is (hoodAt0 at position 0, hoodAt1 at 1).
    @Test public void aHoodServoSetsTheShotAngle() throws Exception {
        String hood = "servo.hood = 0, part=gobilda-2000-torque\n"
                + "shoot.launcher = shape=flywheel, angle=45, speed=200, hood=hood, hoodAt0=80, hoodAt1=30\n";
        double[] steep = shotDirection(robot(hood), 0);
        double[] flat = shotDirection(robot(hood), 1);
        assertEquals("80 deg up at servo 0", 80, steep[0], 3);
        assertEquals("30 deg up at servo 1", 30, flat[0], 3);
        try {
            robot("shoot.launcher = shape=flywheel, angle=45, hood=nothing, hoodAt0=80, hoodAt1=30\n");
            assertTrue("a hood that is not a servo is refused", false);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("servo.nothing"));
        }
    }

    // Moves the hood servo to a position, waits until it is there, shoots one: {degrees up, speed}.
    private static double[] shotDirection(SimRobot r, double hoodPosition) {
        r.setPose(0, -40, 0);
        r.servos.get("hood").device.setPosition(hoodPosition);
        r.motors.get("flywheel").device.setPower(1);
        run(r, 2.0);
        r.pieces.shoot(r.motors.get("flywheel"));
        SimWorld.Ball last = r.world.balls().get(r.world.balls().size() - 1);
        double[] v = r.world.ballVel(last);
        double flatSpeed = Math.hypot(v[0], v[1]);
        return new double[] {Math.toDegrees(Math.atan2(v[2], flatSpeed)), Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])};
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

    // The red HIVE (pivot 0, -12.75, 43.95; the CELL toward +x up at the start, holding the 3 staged NECTAR,
    // 123 g): 3 POLLEN dropped into it make 198 g, over the 187 g that tips it. It tips (turning for 0.5 s, the rules
    // file's tipSeconds; SimGameTest watches it turn): the other CELL comes up, its pieces fall out, its tags move.
    // A piece over a wall is counted. Back to start puts it all back.
    @Test public void aHiveTipsWhenItsUpCellIsHeavyEnough() throws Exception {
        SimRobot r = robot("");
        r.setPose(40, -55, 0);
        run(r, 1.5);
        assertEquals("the 3 NECTAR rest in the red up CELL", 3, (int) r.pieces.inCells().get("Red HIVE"));
        assertEquals(0, (int) r.pieces.tips().get("Red HIVE"));
        double tagZ = r.field.tag(34).z;
        // into the up CELL's opening, from above
        SimField.Hive h = r.field.hives.get(0);
        for (double w : new double[] {-3, 2, 6}) {
            double[][] ax = h.cellAxes(1);
            double a = 17, hh = 5 + h.floor;
            r.pieces.place("POLLEN", h.pivot[0] + a * ax[0][0] + hh * ax[2][0], h.pivot[1] + w, h.pivot[2] + a * ax[0][2] + hh * ax[2][2]);
        }
        run(r, 2.0);
        assertEquals("tipped once", 1, (int) r.pieces.tips().get("Red HIVE"));
        assertEquals("now the -x CELL is up", -1, h.up);
        assertEquals("nothing in the new up CELL", 0, (int) r.pieces.inCells().get("Red HIVE"));
        assertTrue("the tags moved with it: " + r.field.tag(34).z, Math.abs(r.field.tag(34).z - tagZ) > 5);
        run(r, 2.0);
        // all 6 fell out of the red HIVE (they roll on, away from it): nothing high up on the red side any more
        int high = 0, onTiles = 0;
        for (Object[] d : r.pieces.drawn()) {
            if ((Double) d[2] < 0 && (Double) d[3] > 20) high++;
            if ((Double) d[3] < 2.5) onTiles++;
        }
        assertEquals("nothing left up in the red HIVE", 0, high);
        assertTrue("its 6 pieces are on the tiles now: " + onTiles, onTiles >= 12 + 6);
        // a piece over the wall
        r.pieces.place("POLLEN", 0, -75, 20);
        run(r, 1.0);
        assertEquals(1, r.pieces.leftField());
        r.resetPose();
        assertEquals("back as the match starts", 1, h.up);
        assertEquals(tagZ, r.field.tag(34).z, 1e-9);
        assertEquals(0, r.pieces.leftField());
        run(r, 1.0);
        assertEquals(3, (int) r.pieces.inCells().get("Red HIVE"));
    }

    // After a tip, a piece that a moved CELL wall now cuts through is pushed out of it (not left inside the wall).
    @Test public void aPieceInAMovedWallIsPushedOut() throws Exception {
        SimRobot r = robot("");
        SimField.Hive h = r.field.hives.get(0);
        r.field.tip(h);
        for (double[] pl : h.plates(1)) {
            double[] c = {pl[0], pl[1], pl[2]};
            double[] out = SimPieces.pushOut(pl, c, 1.4);
            assertTrue("a ball at a plate's centre overlaps it", out != null);
            double moved = Math.sqrt(Math.pow(out[0] - c[0], 2) + Math.pow(out[1] - c[1], 2) + Math.pow(out[2] - c[2], 2));
            assertEquals("out by the plate's half thickness and the ball's radius", pl[14] / 2 + 1.4 + 0.05, moved, 1e-6);
            assertTrue("and then clear of it", SimPieces.pushOut(pl, out, 1.4) == null);
        }
        double[] far = {0, 0, 1.4};
        assertTrue(SimPieces.pushOut(h.plates(1).get(0), far, 1.4) == null);
    }
}
