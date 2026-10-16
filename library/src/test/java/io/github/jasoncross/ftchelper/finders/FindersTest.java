package io.github.jasoncross.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.jasoncross.ftchelper.Fakes;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FindersTest {
    private static final double E = 1e-9;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void flywheelGainsFromTopSpeed() {
        double[] g = FinderMath.flywheelPidf(2800);            // ~6000 RPM at 28 counts per turn
        assertEquals(32767 / 2800.0, g[3], E);                 // F
        assertEquals(g[3] / 10, g[0], E);                      // P
        assertEquals(g[0] / 10, g[1], E);                      // I
        assertEquals(0, g[2], E);                              // D
        assertEquals(g[3], FinderMath.flywheelPidf(-2800)[3], E);   // a reversed motor gives the same gains
        assertEquals(0, FinderMath.flywheelPidf(0)[3], E);
        assertEquals(6000, FinderMath.ticksPerSecToRpm(2800, 28), E);
    }

    @Test public void wheelSizeFromPushAndDriveTests() {
        // 537.7 counts per turn, 1:1; a 96 mm (3.78 in) wheel rolls pi x 3.78 in per turn.
        double ticks = 48 / (Math.PI * 3.78) * 537.7;
        assertEquals(3.78, FinderMath.wheelDiameterFromPush(ticks, 48, 537.7, 1), 1e-6);
        assertTrue(Double.isNaN(FinderMath.wheelDiameterFromPush(0, 48, 537.7, 1)));
        // Asked 48, went 50: the wheels are really bigger.
        assertEquals(3.78 * 50 / 48, FinderMath.correctedDiameter(3.78, 48, 50), E);
        // Strafe asked 48, went only 40: the correction must grow.
        assertEquals(1.15 * 48 / 40, FinderMath.correctedStrafe(1.15, 48, 40), E);
    }

    @Test public void motorLimitsHandleACountThatGoesDown() {
        assertTrue(Arrays.equals(new double[] {0, 2580, 0}, FinderMath.limits(0, 2580)));
        // Counts go DOWN as it goes up: reversed, and the signs flip.
        assertTrue(Arrays.equals(new double[] {0, 2580, 1}, FinderMath.limits(0, -2580)));
        assertEquals(0.5, FinderMath.servoPosition(Double.NaN), E);
        assertEquals(1, FinderMath.servoPosition(1.2), E);
    }

    @Test public void resultsReplaceByKeyAndSaveToAFile() throws Exception {
        FinderResults r = new FinderResults("Test finder");
        r.set("servo:claw:0", "a");
        r.set("servo:claw:1", "b");
        r.set("servo:wrist:0", "c");
        r.set("servo:claw:0", "A");                            // same key: replaced, same place
        assertEquals(Arrays.asList("A", "b", "c"), r.lines());
        r.removeStartingWith("servo:claw");
        assertEquals(Arrays.asList("c"), r.lines());
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry);
        r.addTelemetry(t);
        assertTrue(t.pending().contains("c"));

        FinderResults.file = new File(tmp.getRoot(), "results.txt");
        assertTrue(FinderResults.append("== header ==", r.lines()));
        assertTrue(FinderResults.append("== again ==", r.lines()));   // adds, keeps the first
        List<String> saved = Files.readAllLines(FinderResults.file.toPath());
        assertEquals("== header ==", saved.get(0));
        assertEquals("c", saved.get(1));
        assertEquals("== again ==", saved.get(3));
    }

    @Test public void chooserWrapsAround() {
        Chooser c = new Chooser(Arrays.asList("lift", "claw", "arm"));
        assertEquals("arm", c.current());                      // sorted A to Z
        c.previous();
        assertEquals("lift", c.current());
        c.next();
        assertEquals("arm", c.current());
        Chooser empty = new Chooser(Arrays.<String>asList());
        assertTrue(empty.isEmpty());
        empty.next();
        assertEquals(null, empty.current());
    }

    @Test public void shotPointsSortReplaceAndWriteCode() {
        ShotPoints p = new ShotPoints(true);
        p.save(72, 3600, 20);
        p.save(36, 2900, 10);
        p.save(36.4, 2950, 11);                                // within 1 in: replaces the 36
        assertEquals(2, p.points().size());
        assertEquals(36.4, p.points().get(0).distance, E);
        assertEquals(Arrays.asList("new ShotTable()", "    .add(36.4, 2950, 11.0)", "    .add(72.0, 3600, 20.0)"),
                p.codeLines());
        assertTrue(p.removeNear(72.5));
        assertFalse(p.removeNear(50));
        ShotPoints noHood = new ShotPoints(false);
        noHood.save(36.4, 2950, 0);
        assertEquals(Arrays.asList("new ShotTable()", "    .add(36.4, 2950)"), noHood.codeLines());
    }
}
