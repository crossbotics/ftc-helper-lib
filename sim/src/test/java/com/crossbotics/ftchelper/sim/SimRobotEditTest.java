package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The page's Edit robot mode (SimRobotEdit, through SimServer): entering it holds the simulator still, the
 * Java it prints for a changed part, a guessed shape becoming a full .looks(...), "Try it" in memory only,
 * and the page's buttons.
 */
public class SimRobotEditTest {
    static {
        CookbookSamples.fill();
    }

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // The robot file :sim:fromRobot writes for this Robot class (its first line names the class).
    private Path robotFile(Class<?> robotClass) throws Exception {
        com.crossbotics.ftchelper.robot.RobotDefinition r =
                (com.crossbotics.ftchelper.robot.RobotDefinition) robotClass.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, SimFromRobot.holdsFromPieces(r.simLines(), r), robotClass.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile(robotClass.getPackage().getName() + ".properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static Map<String, Object> json(String text) {
        return SimJsonReader.object(SimJsonReader.parse(text), "json");
    }

    private static Map<String, Object> shape(Map<String, Object> state, String name) {
        for (Object o : SimJsonReader.array(state.get("shapes"), "shapes")) {
            Map<String, Object> s = SimJsonReader.object(o, "shape");
            if (name.equals(s.get("name"))) return s;
        }
        throw new AssertionError("no shape " + name);
    }

    private static Map<String, Object> part(Map<String, Object> java, String name) {
        for (Object o : SimJsonReader.array(java.get("parts"), "parts")) {
            Map<String, Object> p = SimJsonReader.object(o, "part");
            if (name.equals(p.get("name"))) return p;
        }
        throw new AssertionError("no part " + name + " in " + java);
    }

    @Test public void editingHoldsTheSimulatorStillAndGivesTheJavaToPaste() throws Exception {
        Path file = robotFile(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            SimServer server = new SimServer(s.sim, 0, false, file);
            try {
                Map<String, Object> state = json(server.robotEdit("GET", "/robot-edit", new byte[0]));
                assertEquals(Boolean.TRUE, state.get("java"));
                assertEquals(com.crossbotics.ftchelper.examples.flywheelbot.Robot.class.getName(), state.get("robotClass"));
                Map<String, Object> arm = shape(state, "intake_arm");
                assertEquals("looks", arm.get("where"));
                assertEquals("the Intake \"intake\"", arm.get("part"));
                assertEquals(150.0, ((Number) SimJsonReader.array(arm.get("at"), "at").get(0)).doubleValue(), 0);
                Map<String, Object> kicker = shape(state, "kicker");
                assertEquals("its named angles", 90.0, SimJsonReader.number(SimJsonReader.object(kicker.get("angles"), "a"), "stowed", 0), 1e-6);
                assertTrue("its servo's positions, for the pose buttons", SimJsonReader.object(kicker.get("servoPositions"), "p").containsKey("deployed"));

                // Entering: the physics stops (the clock stands still); leaving: it goes on.
                server.robotEdit("POST", "/robot-edit/mode", "on=1".getBytes(StandardCharsets.UTF_8));
                assertTrue(s.sim.paused);
                SimRun.pause(0.1);
                double t = s.sim.simSeconds();
                SimRun.pause(0.3);
                assertEquals("held still", t, s.sim.simSeconds(), 1e-9);
                server.robotEdit("POST", "/robot-edit/mode", "on=0".getBytes(StandardCharsets.UTF_8));
                assertFalse(s.sim.paused);
                assertTrue(SimRun.waitFor(() -> s.sim.simSeconds() > t + 0.1, 2));

                // Nothing changed: no part to paste. "all": every part, each as Robot.java has it.
                assertEquals(0, SimJsonReader.array(json(server.robotEdit("POST", "/robot-edit/java", "{\"edits\":{}}".getBytes(StandardCharsets.UTF_8)))
                        .get("parts"), "parts").size());
                Map<String, Object> all = json(server.robotEdit("POST", "/robot-edit/java", "{\"edits\":{},\"all\":true}".getBytes(StandardCharsets.UTF_8)));
                assertEquals(".looks(Shape.flywheel(\"gobilda-hogback-96\").atMm(-40, 0, 170))", part(all, "launcher").get("java"));

                // Move the intake arm: its part prints both shapes as variables, keeping dropsWhen and on(...).
                String edits = "{\"edits\":{\"intake_arm\":{\"atMm\":[160,0,100]}}}";
                Map<String, Object> java = json(server.robotEdit("POST", "/robot-edit/java", edits.getBytes(StandardCharsets.UTF_8)));
                assertEquals("only the changed part", 1, SimJsonReader.array(java.get("parts"), "parts").size());
                Map<String, Object> intake = part(java, "intake");
                String lines = (String) intake.get("lines");
                assertTrue(lines, lines.startsWith("Shape intakeArm = Shape.arm().name(\"intake_arm\").atMm(160, 0, 100).sizeMm(110, 200, 20)"));
                assertTrue(lines, lines.contains(".dropsWhen(intakeDeploy, \"deployed\", -15);"));
                assertTrue(lines, lines.contains("Shape roller = Shape.roller(\"gobilda-gecko-48-rex\").on(\"intake_arm\").atMm(110, 0, 0).lengthMm(300);"));
                assertEquals(".looks(intakeArm, roller)", intake.get("looks"));
                assertTrue((String) intake.get("where"), ((String) intake.get("where")).contains("above the line that adds the Intake \"intake\""));
                // each statement, read back by the real builder: the moved arm
                Map<String, Object> refs = new LinkedHashMap<>();
                com.crossbotics.ftchelper.examples.flywheelbot.Robot fly = new com.crossbotics.ftchelper.examples.flywheelbot.Robot();
                fly.simLines();   // sets its parts up (define)
                refs.put("intakeDeploy", fly.intakeDeploy);
                String armText = lines.substring(lines.indexOf('=') + 1, lines.indexOf(';')).trim();
                com.crossbotics.ftchelper.robot.Shape back = SimShapeJavaTest.build(armText, refs);
                assertEquals(160 / 25.4, ((double[]) SimShapeJava.get(back, "at"))[0], 1e-9);

                // The preview: the moved arm, the size checks; the running simulator is not changed.
                Map<String, Object> pv = json(server.robotEdit("POST", "/robot-edit/preview",
                        "{\"edits\":{\"intake_arm\":{\"atMm\":[160,0,100]}},\"values\":{\"intake_arm\":-15}}".getBytes(StandardCharsets.UTF_8)));
                assertFalse(pv.toString(), pv.containsKey("error"));
                assertFalse(SimJsonReader.array(pv.get("pieces"), "pieces").isEmpty());
                List<Object> armNow = SimJsonReader.array(SimJsonReader.object(pv.get("values"), "v").get("intake_arm"), "arm");
                assertEquals(-15, ((Number) armNow.get(0)).doubleValue(), 1e-6);
                assertTrue(SimJsonReader.object(pv.get("most"), "most").containsKey("ok"));
                assertEquals(150 / 25.4, s.robot().shapes.shapes.get("intake_arm").at[0], 0.01);
                // The launcher's arrow: where shots leave its shape and which way (a unit vector), robot frame.
                List<Object> shots = SimJsonReader.array(pv.get("shots"), "shots");
                assertEquals(pv.toString(), 1, shots.size());
                Map<String, Object> shot = SimJsonReader.object(shots.get(0), "shot");
                assertEquals("launcher", shot.get("name"));
                List<Object> dir = SimJsonReader.array(shot.get("dir"), "dir");
                double len = 0;
                for (Object o : dir) len += Math.pow(((Number) o).doubleValue(), 2);
                assertEquals(1, Math.sqrt(len), 1e-3);
                assertTrue("up, not down", ((Number) dir.get(2)).doubleValue() > 0);

                // Try it: the simulator uses it, in memory; nothing is written.
                String before = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                assertEquals("{\"ok\":true}", server.robotEdit("POST", "/robot-edit/try", edits.getBytes(StandardCharsets.UTF_8)));
                assertEquals(160 / 25.4, s.robot().shapes.shapes.get("intake_arm").at[0], 0.01);
                assertEquals(before, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                assertTrue(json(server.robotEdit("GET", "/robot-edit", new byte[0])).get("tried").toString().contains("intake_arm"));
                server.robotEdit("POST", "/robot-edit/try", "{\"edits\":{}}".getBytes(StandardCharsets.UTF_8));
                assertEquals("back as read", 150 / 25.4, s.robot().shapes.shapes.get("intake_arm").at[0], 0.01);

                // A change the simulator cannot draw is refused with words, and nothing changes.
                Map<String, Object> bad = json(server.robotEdit("POST", "/robot-edit/try",
                        "{\"edits\":{\"intake_arm\":{\"sizeMm\":[0,10,10]}}}".getBytes(StandardCharsets.UTF_8)));
                assertTrue(bad.toString(), bad.containsKey("error"));
                // A named angle: its servo's numbers change with it.
                Map<String, Object> ang = json(server.robotEdit("POST", "/robot-edit/java",
                        "{\"edits\":{\"kicker\":{\"angles\":{\"deployed\":10}}}}".getBytes(StandardCharsets.UTF_8)));
                String kickerJava = (String) part(ang, "intake_deploy").get("java");
                assertTrue(kickerJava, kickerJava.contains(".angle(\"stowed\", 90)") && kickerJava.contains(".angle(\"deployed\", 10)"));
            } finally {
                server.close();
            }
        }
    }

    // A part with only a guessed shape (TODO measure): changed, it prints a full .looks(...) to add.
    @Test public void aGuessedShapeBecomesAFullLooks() throws Exception {
        Path file = robotFile(com.crossbotics.ftchelper.examples.v2bot.Robot.class);
        try (SimRun s = new SimRun(file.toString())) {
            SimServer server = new SimServer(s.sim, 0, false, file);
            try {
                Map<String, Object> state = json(server.robotEdit("GET", "/robot-edit", new byte[0]));
                String guessed = null;
                for (Object o : SimJsonReader.array(state.get("shapes"), "shapes")) {
                    Map<String, Object> sh = SimJsonReader.object(o, "shape");
                    if ("guess".equals(sh.get("where"))) guessed = (String) sh.get("name");
                }
                assertNotNull("v2bot has a part without looks: " + state, guessed);
                Map<String, Object> java = json(server.robotEdit("POST", "/robot-edit/java",
                        ("{\"edits\":{\"" + guessed + "\":{\"atMm\":[10,20,250]}}}").getBytes(StandardCharsets.UTF_8)));
                Map<String, Object> p = part(java, guessed);
                assertTrue(p.toString(), ((String) p.get("java")).startsWith(".looks(Shape."));
                assertTrue(p.toString(), ((String) p.get("java")).contains(".atMm(10, 20, 250)"));
                assertTrue(p.toString(), ((String) p.get("where")).contains("before the bracket that closes add(...)"));
                // the slide and the wrist on it keep what Robot.java has
                Map<String, Object> all = json(server.robotEdit("POST", "/robot-edit/java", "{\"all\":true}".getBytes(StandardCharsets.UTF_8)));
                assertTrue(all.toString(), ((String) part(all, "wrist").get("java")).contains(".on(lift)"));
            } finally {
                server.close();
            }
        }
    }

    // A robot file no Robot.java made: the editor gives robot file lines instead.
    @Test public void aHandWrittenRobotFileGetsRobotFileLines() throws Exception {
        try (SimRun s = new SimRun()) {
            SimServer server = new SimServer(s.sim, 0, false, java.nio.file.Paths.get("robots/starter.properties"));
            try {
                Map<String, Object> state = json(server.robotEdit("GET", "/robot-edit", new byte[0]));
                assertEquals(Boolean.FALSE, state.get("java"));
                List<Object> shapes = SimJsonReader.array(state.get("shapes"), "shapes");
                if (shapes.isEmpty()) return;
                String name = (String) SimJsonReader.object(shapes.get(0), "s").get("name");
                Map<String, Object> java = json(server.robotEdit("POST", "/robot-edit/java",
                        ("{\"edits\":{\"" + name + "\":{\"atMm\":[0,0,254]}}}").getBytes(StandardCharsets.UTF_8)));
                String text = (String) part(java, name).get("java");
                assertTrue(text, text.startsWith("shape." + name + " = "));
                assertTrue(text, text.contains("at=0,0,10"));
            } finally {
                server.close();
            }
        }
    }

    // The page: the buttons the mode needs are there, and its code is served.
    @Test public void thePageHasTheEditRobotButtons() throws Exception {
        String page;
        try (InputStream in = SimServer.class.getResourceAsStream("/sim/index.html")) {
            page = new String(readAll(in), StandardCharsets.UTF_8);
        }
        for (String id : new String[] {"id=\"editRobotBtn\"", "id=\"editBackBtn\"", "id=\"editPanel\"", "src=\"/robotedit.js\""}) {
            assertTrue(id, page.contains(id));
        }
        String js;
        try (InputStream in = SimServer.class.getResourceAsStream("/sim/robotedit.js")) {
            assertNotNull(in);
            js = new String(readAll(in), StandardCharsets.UTF_8);
        }
        for (String call : new String[] {"/robot-edit/mode", "/robot-edit/preview", "/robot-edit/java", "/robot-edit/try"}) {
            assertTrue(call, js.contains(call));
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
