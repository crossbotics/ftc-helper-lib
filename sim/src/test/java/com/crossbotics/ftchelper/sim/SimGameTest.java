package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The game's rules (sim/fields/biobuzz-2026.rules.json): FLOWER stacks and who owns them, the bottom NECTAR,
 * G410 fouls, HIVE tips that take time, each shot's clean-entry verdict, the hit rate, and the score.
 */
public class SimGameTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    static final String FLOWER = "FLOWER (red wall, audience half)";   // opening centre 23.57, -68.04

    @Before public void noAllianceChosen() {
        com.crossbotics.ftchelper.match.MatchMemory.clear();      // the robot counts as red
    }

    private SimRobot robot(String extra) throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File f = tmp.newFile();
        Files.write(f.toPath(), (base + "\n" + extra).getBytes("UTF-8"));
        return SimRobot.load(f.toPath());
    }

    private static void run(SimRobot r, double sec) {
        for (double t = 0; t < sec; t += 0.01) r.step(0.01);
    }

    // A match clock stopped at one moment (the simulator's comes from the Driver Station).
    private static SimPieces.MatchClock at(final String period, final double secondsLeft) {
        return new SimPieces.MatchClock() {
            @Override public String period() { return period; }
            @Override public double secondsLeft() { return secondsLeft; }
        };
    }

    private static SimScore.Side side(SimRobot r, String alliance) {
        synchronized (r) {
            return r.pieces.score.sides().get(alliance);
        }
    }

    // Drops a piece into the red-wall FLOWER from above and lets it fall in.
    private static void drop(SimRobot r, String type) {
        r.pieces.place(type, 23.57, -68.04, 30);
        run(r, 1.0);
    }

    @Test public void theRulesFileIsReadWithTheField() throws Exception {
        SimRobot r = robot("");
        assertTrue(r.field.rules != null);
        assertEquals(20, r.field.rules.tipAuto, 0);
        assertEquals("red", r.field.rules.nectarOf("red NECTAR"));
        assertEquals(null, r.field.rules.nectarOf("POLLEN"));
    }

    // 10.5.2: pieces partly between the middle ring and the top ring score; the bottom POLLEN (in the lower ring)
    // does not. Nobody owns a FLOWER without NECTAR. A red NECTAR on top in the last minute: red owns it (2 points
    // for each of its 4 pieces in the volume) and has the bottom NECTAR (5). No foul in the last 60 s.
    @Test public void aNectarOnTopOwnsTheFlower() throws Exception {
        SimRobot r = robot("");
        List<Object[]> f = r.pieces.flowers().get(FLOWER);
        assertEquals("4 staged POLLEN", 4, f.size());
        assertEquals("the bottom one is under the middle ring", false, f.get(0)[2]);
        for (int k = 1; k < 4; k++) assertEquals("the others reach into the scoring volume", true, f.get(k)[2]);
        assertEquals("no owner: no FLOWER points", 0, side(r, "red").flower, 0);
        r.pieces.clock = at("TELEOP", 30);
        drop(r, "red NECTAR");
        f = r.pieces.flowers().get(FLOWER);
        assertEquals("it stays in the FLOWER, on top", 5, f.size());
        assertEquals("red NECTAR", f.get(4)[0]);
        // resting on the top POLLEN: 1.4 + 1.8 + 0.09 above its centre
        assertEquals(3.29, (Double) f.get(4)[1] - (Double) f.get(3)[1], 1e-9);
        assertEquals(1, (int) r.pieces.scored().get(FLOWER));
        assertEquals(17, r.pieces.inStacks());
        SimScore.Side red = side(r, "red");
        assertEquals("4 pieces in the owned FLOWER", 8, red.flower, 0);
        assertEquals("the bottom NECTAR bonus", 5, red.bottomNectar, 0);
        assertEquals("no foul with 30 s left", 0, side(r, "blue").fouls, 0);
        // a blue NECTAR on top of it: blue owns it now; red's NECTAR is still the bottom one
        drop(r, "blue NECTAR");
        assertEquals(10, side(r, "blue").flower, 0);
        assertEquals(0, side(r, "red").flower, 0);
        assertEquals(5, side(r, "red").bottomNectar, 0);
    }

    // G410: NECTAR into a FLOWER before the last 60 s is a MAJOR FOUL (20 points to the other alliance). It still
    // counts for the FLOWER. Free play (no OpMode running) has no fouls.
    @Test public void nectarTooEarlyIsAMajorFoul() throws Exception {
        SimRobot r = robot("");
        r.pieces.clock = at("TELEOP", 90);
        drop(r, "red NECTAR");
        assertEquals(20, side(r, "blue").fouls, 0);
        assertEquals(1, r.pieces.score.fouls().size());
        assertEquals("G410", r.pieces.score.fouls().get(0).rule);
        assertEquals("still in the FLOWER", 8, side(r, "red").flower, 0);
        SimRobot free = robot("");
        drop(free, "red NECTAR");
        assertEquals(0, side(free, "blue").fouls, 0);
    }

    // A FLOWER holds what fits under its top: 8 POLLEN. One more stays on top (it is not taken in).
    @Test public void aFullFlowerTakesNoMore() throws Exception {
        SimRobot r = robot("");
        for (int i = 0; i < 4; i++) drop(r, "POLLEN");
        assertEquals(8, r.pieces.flowers().get(FLOWER).size());
        drop(r, "POLLEN");
        assertEquals("full: the 9th is not in it", 8, r.pieces.flowers().get(FLOWER).size());
        assertEquals(4, (int) r.pieces.scored().get(FLOWER));
    }

    // 9.7 / G418: NECTAR is too big for the Retrieval Opening. An intake takes the 4 POLLEN from under a NECTAR; the
    // NECTAR then drops to the bottom and stays there (it blocks the bottom for good).
    @Test public void aNectarAtTheBottomStaysIn() throws Exception {
        SimRobot r = robot("collect.intake = shape=intake, motor=intake, holds=8\npreload = off\n");
        r.pieces.clock = at("TELEOP", 30);
        drop(r, "red NECTAR");
        r.setPose(23.39, -68.04 + 2.41 + 9.6, 180);          // facing the FLOWER (-y), intake 0.6 in from its face
        r.motors.get("intake").device.setPower(1);
        run(r, 3.0);
        assertEquals("the 4 POLLEN taken", 4, r.pieces.storedCount());
        List<Object[]> f = r.pieces.flowers().get(FLOWER);
        assertEquals(1, f.size());
        assertEquals("red NECTAR", f.get(0)[0]);
        assertEquals("on the tiles through the lower ring", 1.8, (Double) f.get(0)[1], 0.01);
        assertEquals("under the middle ring: it does not score", false, f.get(0)[2]);
        run(r, 1.0);
        assertEquals("still not taken", 4, r.pieces.storedCount());
    }

    // A HIVE takes tipSeconds (0.5 s) to turn: halfway through it has turned part of the way, not tipped yet, and
    // the pieces in its CELL move with it (no jump). Then it is TIPPED, and 20 points for red.
    @Test public void aHiveTurnsOverHalfASecond() throws Exception {
        SimRobot r = robot("");
        r.setPose(40, -55, 0);
        run(r, 1.5);
        SimField.Hive h = r.field.hives.get(0);
        assertEquals(6, side(r, "red").cell, 0);       // the 3 staged NECTAR, 2 points each
        double[][] ax = h.cellAxes(1);
        for (double w : new double[] {-3, 2, 6}) {
            double a = 17, hh = 5 + h.floor;
            r.pieces.place("POLLEN", h.pivot[0] + a * ax[0][0] + hh * ax[2][0], h.pivot[1] + w, h.pivot[2] + a * ax[0][2] + hh * ax[2][2]);
        }
        double maxJump = 0, maxProgress = 0;
        boolean sawMiddle = false;
        Map<Integer, double[]> last = new java.util.HashMap<>();
        for (int i = 0; i < 300 && h.tips == 0; i++) {
            run(r, 0.01);
            double p = r.pieces.tipProgress().get("Red HIVE");
            maxProgress = Math.max(maxProgress, p);
            if (p > 0.3 && p < 0.7) {
                sawMiddle = true;
                assertEquals("not tipped yet while it turns", 0, h.tips);
                assertTrue("turned part of the way: " + h.turnDeg, Math.abs(h.turnDeg) > 5 && Math.abs(h.turnDeg) < 55);
            }
            if (p > 0) {
                for (SimWorld.Ball b : r.world.balls()) {
                    double[] q = r.world.ballPos(b), was = last.get(b.id);
                    if (was != null) maxJump = Math.max(maxJump, Math.sqrt(Math.pow(q[0] - was[0], 2) + Math.pow(q[1] - was[1], 2) + Math.pow(q[2] - was[2], 2)));
                }
            }
            for (SimWorld.Ball b : r.world.balls()) last.put(b.id, r.world.ballPos(b));
        }
        assertTrue("it was seen turning", sawMiddle);
        assertEquals(1, h.tips);
        assertEquals(0, r.pieces.tipProgress().get("Red HIVE"), 0);
        assertTrue("the pieces moved smoothly (most in one 0.01 s step: " + maxJump + " in)", maxJump < 3);
        assertEquals("a TELEOP tip (free play counts as TELEOP)", 20, side(r, "red").tipTeleop, 0);
    }

    // Each launched shot gets a verdict: a steep shot from 60 in out into the red HIVE's up CELL goes in cleanly;
    // the same launcher turned away misses.
    @Test public void eachShotGetsAVerdict() throws Exception {
        SimRobot r = robot("shoot.launcher = shape=flywheel, angle=70, speed=215\n");
        r.setPose(60, -12.75, 90);                           // facing -x, at the red HIVE's up CELL
        r.motors.get("flywheel").device.setPower(1);
        run(r, 1.0);
        assertTrue(r.pieces.shoot(r.motors.get("flywheel")));
        run(r, 3.0);
        List<SimPieces.ShotVerdict> v = r.pieces.verdicts();
        assertEquals(1, v.size());
        assertEquals("clean", v.get(0).verdict);
        assertEquals("Red HIVE", v.get(0).hive);
        r.setPose(60, -12.75, 270);                          // facing away
        run(r, 0.2);
        r.pieces.shoot(r.motors.get("flywheel"));
        run(r, 3.0);
        assertEquals("miss", r.pieces.verdicts().get(1).verdict);
        assertEquals(1, (int) r.pieces.verdictCounts().get("clean"));
    }

    // The hit rate: 400 pretend shots with realistic scatter from a spot where the plain shot is clean: most go in,
    // not all; with rough scatter fewer. It moves nothing, and the same robot gives the same answer.
    @Test public void theHitRateRatesTheSpot() throws Exception {
        SimRobot r = robot("shoot.launcher = shape=flywheel, angle=70, speed=215, scatter=realistic\n");
        r.setPose(60, -12.75, 90);
        r.motors.get("flywheel").device.setPower(1);
        run(r, 1.0);
        int pieces = r.pieces.drawn().size();
        SimPieces.HitRate real = r.pieces.hitRate(400, null, 12345);
        assertEquals("POLLEN", real.piece);
        assertEquals("Red HIVE", real.hive);
        assertTrue("realistic: " + real.rate, real.rate > 0.2 && real.rate < 1);
        assertEquals(real.rate >= 0.8 ? "Good" : real.rate >= 0.5 ? "Risky" : "Poor", real.verdict);
        assertEquals(real.rate, r.pieces.hitRate(400, null, 12345).rate, 0);
        assertEquals("nothing was shot", pieces, r.pieces.drawn().size());
        assertEquals(0, r.pieces.launched());
        r.pieces.setScatter("rough");
        SimPieces.HitRate rough = r.pieces.hitRate(400, null, 12345);
        assertTrue("rough " + rough.rate + " < realistic " + real.rate, rough.rate < real.rate);
        assertTrue(SimScore.json(rough).contains("\"verdict\""));
    }

    // The score at the start (free play): each up CELL's 3 NECTAR (6) and each GARDEN's 4 POLLEN (4); the robot
    // on its start spot, in no LOADING ZONE. The page's JSON has it.
    @Test public void theScoreCountsWhatIsOnTheField() throws Exception {
        SimRobot r = robot("");
        run(r, 1.0);
        SimScore.Side red = side(r, "red"), blue = side(r, "blue");
        assertEquals(6, red.cell, 0);
        assertEquals(4, red.garden, 0);
        assertEquals(10, red.total(), 0);
        assertEquals(10, blue.total(), 0);
        // park in the red LOADING ZONE (x -46.6..-23.91, y -70.58..-59.1)
        r.setPose(-35, -60, 0);
        assertEquals(5, side(r, "red").parkTeleop, 0);
        String json;
        synchronized (r) { json = r.pieces.score.json(); }
        assertTrue(json, json.startsWith("{\"rules\":true,\"clock\":{\"period\":\"\""));
        assertTrue(json, json.contains("\"red\":{\"total\":15"));
        assertTrue(json, json.contains("\"name\":\"" + FLOWER + "\",\"owner\":\"\""));
    }

    // The end of AUTO: LEAVE (not touching the wall) and AUTO PARK count then. An autonomous starting clears the score.
    @Test public void leaveAndParkCountAtTheEndOfAuto() throws Exception {
        SimRobot r = robot("");
        r.setPose(-35, -60, 0);                              // off the wall, in the red LOADING ZONE
        r.pieces.clock = at("AUTO", 10);
        run(r, 0.1);
        r.pieces.clock = at("TELEOP", 120);
        run(r, 0.1);
        SimScore.Side red = side(r, "red");
        assertEquals(3, red.leave, 0);
        assertEquals(5, red.parkAuto, 0);
    }

    // 10.5: CELL, FLOWER and GARDEN pieces and TELEOP PARK count only at the end of the match. Until then the page
    // gets them apart ("atEnd", shown grey); when TeleOp stops, the score is held: moving after that changes nothing.
    @Test public void endOfMatchPointsCountWhenTeleOpEnds() throws Exception {
        SimRobot r = robot("");
        run(r, 1.0);
        String json;
        synchronized (r) { json = r.pieces.score.json(); }
        assertTrue(json, json.contains("\"red\":{\"total\":10,\"counted\":0,\"atEnd\":10"));
        assertTrue(json, json.contains("\"match\":{\"state\":\"none\""));
        assertTrue(json, json.contains("\"rp\":{\"swarm\":16,\"pollinator1\":4,\"pollinator2\":7}"));
        r.pieces.clock = at("TELEOP", 60);
        run(r, 0.1);
        r.setPose(-35, -60, 0);                              // in the red LOADING ZONE: TELEOP PARK, at the end
        run(r, 0.1);
        synchronized (r) { json = r.pieces.score.json(); }
        assertTrue(json, json.contains("\"match\":{\"state\":\"teleop\""));
        assertTrue(json, json.contains("\"red\":{\"total\":15,\"counted\":0,\"atEnd\":15"));
        r.pieces.clock = at("", Double.NaN);                 // TeleOp stopped with 60 s left
        run(r, 0.1);
        r.setPose(0, -60, 0);                                // out of the zone after the end: no change
        run(r, 0.1);
        synchronized (r) { json = r.pieces.score.json(); }
        assertTrue(json, json.contains("\"match\":{\"state\":\"ended\",\"endedLeft\":60}"));
        assertTrue(json, json.contains("\"red\":{\"total\":15,\"counted\":15,\"atEnd\":15"));
        assertTrue(json, json.contains("\"rp\":{\"swarm\":5,\"tips\":0}"));
        r.pieces.clock = at("TELEOP", 120);                  // a new TeleOp after the end: a new match
        run(r, 0.1);
        synchronized (r) { json = r.pieces.score.json(); }
        assertTrue(json, json.contains("\"match\":{\"state\":\"teleop\""));
        assertTrue(json, json.contains("\"red\":{\"total\":10,"));
    }
}
