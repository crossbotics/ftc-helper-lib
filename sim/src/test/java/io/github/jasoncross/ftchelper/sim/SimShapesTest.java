package io.github.jasoncross.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;

/** The robot as shapes: motion from motors and servos, and the size rules. */
public class SimShapesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private SimRobot robot(String... lines) throws Exception {
        String extra = String.join("\n", lines) + "\n";
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        // drop the starter's shapes, keep its devices
        StringBuilder b = new StringBuilder();
        for (String line : base.split("\n")) {
            // keep the devices; drop the shapes and what uses them
            if (!line.startsWith("shape.") && !line.startsWith("collect.") && !line.startsWith("shoot.") && !line.startsWith("grab.")) {
                b.append(line).append('\n');
            }
        }
        File f = tmp.newFile();
        Files.write(f.toPath(), (b + extra).getBytes("UTF-8"));
        return SimRobot.load(f.toPath());
    }

    private void expectError(String words, String... lines) throws Exception {
        try {
            robot(lines);
            org.junit.Assert.fail("should be refused: " + String.join(" / ", lines));
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(words));
        }
    }

    private static double top(SimRobot r, String shape) {
        double t = -1e9;
        for (SimShapes.Piece p : r.shapes.pieces()) if (p.shape.equals(shape)) for (double[] c : p.corners()) t = Math.max(t, c[2]);
        return t;
    }

    @Test public void slideArmAndServoFollowTheirDrivers() throws Exception {
        SimRobot r = robot("shape.lift = slide, at=0,0,4, size=2x2x10, motor=lift, ticksPerIn=100\n"
                + "shape.arm = arm, on=lift, at=0,0,10, size=10x1x1, motor=arm, ticksPerDeg=10, start=0\n"
                + "shape.wrist = arm, on=arm, at=10,0,0, size=2x1x1, servo=wrist, pos0=-90, pos1=90\n");
        r.shapes.update(0);
        assertEquals("lift down: 4 + 10", 14, top(r, "lift"), 1e-6);
        r.motors.get("lift").shaftPos = 500;          // 5 in up
        r.motors.get("arm").shaftPos = 900;           // 90 degrees: straight up
        r.shapes.update(0);
        assertEquals(19, top(r, "lift"), 1e-6);
        assertEquals("arm straight up from 4 + 10 + 5: its top is 10 in higher", 29, top(r, "arm"), 1e-6);
        // the wrist at the arm's tip, servo at 0.5 = 0 degrees: it carries on straight up 2 in
        assertEquals(31, top(r, "wrist"), 1e-6);
    }

    @Test public void mistakesAreNamed() throws Exception {
        String[][] bad = {
            {"shape.a = wing", "type"},
            {"shape.a = box, on=b", "no shape.b"},
            {"shape.a = box, on=b\nshape.b = box, on=a", "loop"},
            {"shape.a = slide, motor=nope", "no motor.nope"},
            {"shape.a = slide, axis=sideways", "axis"},
            {"shape.a = slide, motor=lift", "ticksPerIn"},
            {"shape.a = box, size=4x4", "size"},
            {"shape.chassis = box", "chassis"},
        };
        for (String[] b : bad) {
            try {
                robot(b[0] + "\n");
                org.junit.Assert.fail("should be refused: " + b[0]);
            } catch (IllegalArgumentException e) {
                assertTrue(b[0] + " -> " + e.getMessage(), e.getMessage().contains(b[1]));
            }
        }
    }

    @Test public void aGivenHeightCountsForTheSizeRules() throws Exception {
        // Short shapes, but the robot file says a part reaches 31 in (one the shapes do not draw).
        SimRobot r = robot("robot.tallestIn = 31", "shape.box1 = box, at=0,0,2, size=4x4x4");
        r.shapes.update(0);
        assertTrue("never shorter than robot.tallestIn: " + r.shapes.now(), r.shapes.now().top >= 31);
        assertTrue("too tall for 29 in", !r.shapes.mostOk);
    }

    @Test public void aRollerSaysWhetherItTakesPiecesInOrPushesThemOut() throws Exception {
        SimRobot r = SimRobot.load(new File("robots/starter.properties").toPath());   // collect.intake on motor "intake"
        SimMotor intake = r.motors.get("intake");
        intake.device.setPower(1);
        for (int i = 0; i < 20; i++) r.step(0.02);
        SimShapes.Piece roller = null;
        for (SimShapes.Piece p : r.shapes.pieces()) if (p.shape.equals("intake")) roller = p;
        assertTrue("turning: " + roller.run, roller.run > 0.2);
        assertTrue("taking in", roller.flow == 1);
        intake.device.setPower(-1);
        for (int i = 0; i < 40; i++) r.step(0.02);
        for (SimShapes.Piece p : r.shapes.pieces()) if (p.shape.equals("intake")) roller = p;
        assertTrue("pushing out", roller.flow == -1);
    }

    @Test public void aReversedRollerTurnsTheOtherWayButStillTakesIn() throws Exception {
        SimRobot r = robot("shape.intake = roller, at=8,0,2, size=2x14x2, motor=intake",
                "shape.spinner = roller, at=8,6,2, size=2x2x1, axis=up, motor=intake, reversed=yes",
                "collect.intake = shape=intake, motor=intake, holds=4");
        r.motors.get("intake").device.setPower(1);
        for (int i = 0; i < 20; i++) r.step(0.02);
        SimShapes.Piece roller = null, spinner = null;
        for (SimShapes.Piece p : r.shapes.pieces()) {
            if (p.shape.equals("intake")) roller = p;
            if (p.shape.equals("spinner")) spinner = p;
        }
        assertTrue(roller.run > 0 && spinner.run < 0);
        assertTrue("both take pieces in", roller.flow == 1 && spinner.flow == 1);
        assertTrue("the picture turns slowly enough to see which way", Math.abs(roller.spinDeg) <= 360);
    }

    @Test public void starterRobotShapes() throws Exception {
        SimRobot r = SimRobot.load(new File("robots/starter.properties").toPath());
        r.shapes.update(0);
        System.out.println("now  " + r.shapes.now());
        System.out.println("most " + r.shapes.most + "  (" + r.shapes.mostPose + ", " + r.shapes.posesTried + " poses)");
        assertTrue("above the tiles now", r.shapes.now().aboveFloor());
        assertTrue("fits the 18 in start cube at the start: " + r.shapes.now(), r.shapes.now().fits(18, 18, 18));
        assertTrue("every pose fits 18 x 24 x 29 and stays above the tiles: " + r.shapes.most, r.shapes.mostOk);
    }

    @Test public void reviewersCasesAreCaught() throws Exception {
        // An arm whose highest point is part way round (84 degrees) must not slip through a coarse check:
        // 19 in hinge + 10 in arm, 2 in thick, on a servo from 0 to 180 degrees reaches 29.05 in.
        SimRobot r = robot("shape.a = arm, at=0,0,19, size=10x1x2, servo=wrist, pos0=0, pos1=180\n");
        assertTrue("29.05 in tall somewhere: " + r.shapes.most, !r.shapes.mostOk && r.shapes.most.top > 29.0);
        // A part below the tiles is shown.
        r = robot("shape.a = arm, at=0,0,4, size=10x1x1, servo=wrist, pos0=-90, pos1=0\n");
        assertTrue(!r.shapes.mostOk);
        // A hex color is not a note, and the rest of the line is still read.
        r = robot("shape.a = box, color=#ff0000, at=1,0,4\n");
        assertEquals("#ff0000", r.shapes.shapes.get("a").color);
        assertEquals(1, r.shapes.shapes.get("a").at[0], 1e-9);
        // A word with no = after the first is a mistake, not part of the value before it.
        try {
            robot("servo.extra = 0.5, part=gobilda-2000-torque, reversed\n");
            org.junit.Assert.fail("a word with no = must be refused");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("reversed") && e.getMessage().contains("name=value"));
        }
    }

    // ----------------------------- passive parts -----------------------------

    @Test public void aPassiveArmDropsWhenItsServoPassesAndStaysThere() throws Exception {
        SimRobot r = robot("servo.kick = 0, part=gobilda-2000-speed",
                "shape.kicker = arm, at=4,0,4, size=2x1x1, servo=kick, pos0=90, pos1=0",
                "shape.fold = arm, at=4,0,4, size=6x8x1, start=90, minDeg=-10, maxDeg=90, passive=yes, drop=kick>0.6, fallsTo=-10");
        SimShapes.Shape fold = r.shapes.shapes.get("fold");
        r.shapes.update(0.1);
        assertEquals("not pushed yet", 90, fold.value, 1e-9);
        r.servos.get("kick").actual = 0.7;
        for (int i = 0; i < 20; i++) r.shapes.update(0.1);
        assertEquals("fell to its stop", -10, fold.value, 1e-9);
        r.servos.get("kick").actual = 0;
        r.shapes.update(0.1);
        assertEquals("stays down when the servo goes back", -10, fold.value, 1e-9);
        r.resetPose();
        assertEquals("a new match: folded again", 90, fold.value, 1e-9);
    }

    @Test public void aPassivePartNeedsARangeAndNoDriver() throws Exception {
        expectError("needs motor= or servo=", "shape.a = arm, size=2x2x2, minDeg=0, maxDeg=90");
        expectError("no range of motion", "shape.a = arm, size=2x2x2, passive=yes");
        expectError("passive=yes", "shape.a = arm, size=2x2x2, minDeg=0, maxDeg=90, drop=x>0.5");
        expectError("drop=intake_deploy>0.6", "shape.a = arm, size=2x2x2, minDeg=0, maxDeg=90, passive=yes, drop=x=0.5");
        expectError("no servo.x", "shape.a = arm, size=2x2x2, minDeg=0, maxDeg=90, passive=yes, drop=x>0.5");
        SimRobot r = robot("shape.s = slide, size=1x1x1, travel=1, passive=yes");
        assertEquals("a passive slide rests at its start", 0, r.shapes.shapes.get("s").value, 1e-9);
    }
}
