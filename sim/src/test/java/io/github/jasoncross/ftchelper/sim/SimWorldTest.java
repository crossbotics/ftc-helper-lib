package io.github.jasoncross.ftchelper.sim;

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

    @Test public void aPartHitsTheFieldTooAndThePhysicsCanBeOff() throws Exception {
        // a 10 in tall "mast" 10 in in front of the robot, at the height of the HIVE frame top (34-44 in)
        SimRobot r = robot("shape.mast = box, at=10,0,4, size=1x1x36\n");
        r.setPose(0, -40, 0);                 // facing +y, the HIVE frame top (x +-4.3, y +-15.1) ahead
        push(r, 15, 0, 2.5);
        assertTrue("the mast hits something of the HIVE: " + r.bumpedInto, r.bumpedInto.contains("HIVE"));
        assertTrue("stopped before the HIVE: y " + r.y, r.y < -24);
        // physics = simple: the old rule, a bump stops the whole move
        SimRobot s = robot("physics = simple\n");
        assertTrue(s.world == null);
    }
}
