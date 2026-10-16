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

    @Test public void aStartInsideAnElementIsRefusedAndBumpsClear() throws Exception {
        File robotFile = tmp.newFile("bad-start.properties");
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        Files.write(robotFile.toPath(), (base + "\nstart.x = 0\nstart.y = -20\n").getBytes("UTF-8"));   // in the HIVE frame base
        try {
            SimRobot.load(robotFile.toPath());
            org.junit.Assert.fail("a start inside the HIVE frame base must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("HIVE frame base"));
        }
        SimRobot r = SimRobot.load(new File("robots/starter.properties").toPath());   // the legal start
        r.push(-10, 0, 0.5);                                 // back into the red wall
        for (int i = 0; i < 100; i++) r.step(0.01);
        assertEquals("field wall", r.bumpedInto);
        r.push(10, 0, 0.5);                                  // and away from it again
        for (int i = 0; i < 100; i++) r.step(0.01);
        assertEquals("moving freely clears the bump", "", r.bumpedInto);
    }

    @Test public void fieldModelIsFoundFromTheRobotFile() throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File dir = tmp.newFolder("team"), fields = new File(dir, "fields");
        assertTrue(fields.mkdir());
        Files.write(new File(fields, "test.glb").toPath(), new byte[] {'g', 'l', 'T', 'F'});
        File robotFile = new File(dir, "robot.properties");
        Files.write(robotFile.toPath(), (base + "\nfield.model = fields/test.glb\n").getBytes("UTF-8"));
        SimRobot r = SimRobot.load(robotFile.toPath());
        assertEquals(new File(fields, "test.glb").getCanonicalFile(), r.fieldModel.toFile().getCanonicalFile());
        assertEquals("no field.model: the page builds the field from the field file", null,
                SimRobot.load(new File("robots/starter.properties").toPath()).fieldModel);
        Files.write(robotFile.toPath(), (base + "\nfield.model = fields/missing.glb\n").getBytes("UTF-8"));
        try {
            SimRobot.load(robotFile.toPath());
            org.junit.Assert.fail("a missing field.model must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("field.model"));
        }
    }

    @Test public void customPartsFromTheRobotFileAndATeamPartsFile() throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        File dir = tmp.newFolder("custom");
        Files.write(new File(dir, "parts.json").toPath(), ("{\"motors\":[{\"id\":\"team-arm\",\"freeRpm\":90,\"countsPerRev\":2000}],"
                + "\"servos\":[{\"id\":\"team-gate\",\"rangeDeg\":180,\"secPer60Deg\":0.1}]}").getBytes("UTF-8"));
        File robotFile = new File(dir, "robot.properties");
        Files.write(robotFile.toPath(), (base + "\nparts = parts.json\nmotor.teamarm = lift, part=team-arm\nmotor.arm2 = free, rpm=60, ticksPerRev=1000\n"
                + "servo.gate = 0.5, part=team-gate\nservo.gate2 = 0.5, rangeDeg=300, secPer60=0.25\n").getBytes("UTF-8"));
        SimRobot r = SimRobot.load(robotFile.toPath());
        assertEquals(90 * 2000 / 60.0, r.motors.get("teamarm").maxTicksPerSec, 1e-6);
        assertEquals(60 * 1000 / 60.0, r.motors.get("arm2").maxTicksPerSec, 1e-6);
        assertEquals((60 / 0.1) / 180, r.servos.get("gate").speed, 1e-9);
        assertEquals((60 / 0.25) / 300, r.servos.get("gate2").speed, 1e-9);
        // A team part with a library id is refused.
        Files.write(new File(dir, "parts.json").toPath(), "{\"motors\":[{\"id\":\"gobilda-5203-312\",\"freeRpm\":1,\"countsPerRev\":1}]}".getBytes("UTF-8"));
        try {
            SimRobot.load(robotFile.toPath());
            org.junit.Assert.fail("a team part may not reuse a library id");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("already a part"));
        }
    }

    @Test public void servoSpeedIsTheFiveVoltRow() {
        SimParts.Servo torque = SimParts.catalog().servo("gobilda-2000-torque");
        assertEquals("the 4.8 V row (0.25 s per 60 deg), not the 6 V headline", 0.25, torque.secPer60Deg, 1e-9);
    }
}
