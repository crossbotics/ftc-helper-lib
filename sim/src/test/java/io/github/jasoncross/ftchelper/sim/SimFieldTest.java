package io.github.jasoncross.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SimFieldTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void hullAndOverlap() {
        List<double[]> square = Arrays.asList(new double[] {0, 0}, new double[] {10, 0}, new double[] {10, 10},
                new double[] {0, 10}, new double[] {5, 5});          // an inside point is dropped
        assertEquals(4, SimField.convexHull(square).size());
        List<double[]> near = Arrays.asList(new double[] {9, 9}, new double[] {20, 9}, new double[] {20, 20}, new double[] {9, 20});
        List<double[]> far = Arrays.asList(new double[] {11, 11}, new double[] {20, 11}, new double[] {20, 20}, new double[] {11, 20});
        assertTrue(SimField.overlap(SimField.convexHull(square), near));
        assertFalse(SimField.overlap(SimField.convexHull(square), far));
    }

    @Test public void theRobotStopsAtASolidElementButNotAtAZone() throws Exception {
        File field = tmp.newFile("test-field.json");
        Files.write(field.toPath(), ("{\"season\":\"Test\",\"field\":{\"size\":144},\"elements\":["
                + "{\"name\":\"Box\",\"kind\":\"solid\",\"polygon\":[[-10,0],[10,0],[10,10],[-10,10]],\"zMin\":0,\"zMax\":12},"
                + "{\"name\":\"Tape\",\"kind\":\"zone\",\"polygon\":[[-10,-30],[10,-30],[10,-20],[-10,-20]],\"zMin\":0,\"zMax\":0}],"
                + "\"tags\":[{\"id\":20,\"x\":0,\"y\":10,\"z\":8,\"facingDeg\":-90}]}").getBytes("UTF-8"));
        File robotFile = tmp.newFile("robot.properties");
        String robot = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8")
                + "\nfield = " + field.getPath() + "\nstart.x = 0\nstart.y = -48\n";
        Files.write(robotFile.toPath(), robot.getBytes("UTF-8"));
        SimRobot r = SimRobot.load(robotFile.toPath());
        assertEquals(1, r.field.tags.size());
        r.push(24, 0, 3);                                    // 72 in forward: through the tape, into the box
        for (int i = 0; i < 400; i++) r.step(0.01);
        assertEquals("Box", r.bumpedInto);
        assertTrue("stopped before the box (robot front at y < 0): y = " + r.y, r.y + 9 <= 0.5);
        assertTrue("went past the tape zone", r.y > -20);
    }

    @Test public void theJsonReaderIsStrict() {
        assertEquals(2.5e3, (Double) SimJsonReader.parse("2.5e3"), 1e-9);
        assertEquals("a\"b\u00e9", SimJsonReader.parse("\"a\\\"b\\u00e9\""));
        for (String bad : new String[] {"+1", "01", ".5", "1e999", "\"\\q\"", "\"\\u12\"", "\"a\nb\"", "[1,]"}) {
            try {
                SimJsonReader.parse(bad);
                org.junit.Assert.fail("should refuse " + bad);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().startsWith("JSON line"));
            }
        }
    }
}
