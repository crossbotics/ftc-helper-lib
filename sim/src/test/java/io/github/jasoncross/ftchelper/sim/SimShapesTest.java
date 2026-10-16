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

    private SimRobot robot(String extra) throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        // drop the starter's shapes, keep its devices
        StringBuilder b = new StringBuilder();
        for (String line : base.split("\n")) if (!line.startsWith("shape.")) b.append(line).append('\n');
        File f = tmp.newFile();
        Files.write(f.toPath(), (b + extra).getBytes("UTF-8"));
        return SimRobot.load(f.toPath());
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

    @Test public void starterRobotShapes() throws Exception {
        SimRobot r = SimRobot.load(new File("robots/starter.properties").toPath());
        r.shapes.update(0);
        System.out.println("now  " + r.shapes.now());
        System.out.println("most " + r.shapes.most + "  (" + r.shapes.mostPose + ")");
        assertTrue("fits the 18 in start cube at the start: " + r.shapes.now(), r.shapes.now().fits(18, 18, 18));
        assertTrue("fits 18 x 24 x 29 at full extension: " + r.shapes.most, r.shapes.most.fits(18, 24, 29));
    }
}
