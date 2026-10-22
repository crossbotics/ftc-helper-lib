package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The page's test recorder (SimRecorder, SimTests): a session played the way the page sends it (commands
 * and gamepad updates), made into a scenario file, replayed (it passes), the code changed (it fails), the
 * test updated from a measuring run (it passes again). Also each part on its own: taps and holds, stick
 * sweeps, screen lines, the update of one line, and the file names a page may use.
 */
public class SimRecorderTest {
    private static final Path GOBILDA = Paths.get("robots/starter-bot-gobilda.properties");

    // A page's gamepad update: every input, with a growing number (like the page's).
    private static int seq = 0;

    private static void pad(SimEngine sim, String... keyValues) {
        Map<String, String> m = new HashMap<>();
        for (String[] in : SimRecorder.INPUTS) m.put(in[0], "0");
        for (int i = 0; i + 1 < keyValues.length; i += 2) m.put(keyValues[i], keyValues[i + 1]);
        m.put("session", "recorder-test");
        m.put("seq", String.valueOf(++seq));
        sim.ds.pad1.apply(m, true);
    }

    private static Map<String, String> cmd(String... keyValues) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) m.put(keyValues[i], keyValues[i + 1]);
        return m;
    }

    private static SimEngine engine(Path robot) throws Exception {
        SimScenario.freshMatch();
        SimEngine sim = new SimEngine(SimRobot.load(robot), SimDriverStation.Scope.LIBRARY);
        sim.start();
        return sim;
    }

    // Replays a test file like ./gradlew :sim:scenario (a fresh simulator), measuring if asked.
    private static SimScenario replay(Path file, boolean measure) throws Exception {
        SimEngine sim = engine(SimScenario.robotFor(file, GOBILDA));
        SimScenario s = new SimScenario(sim);
        s.measure = measure;
        try {
            s.run(file);
        } finally {
            sim.shutdown();
        }
        System.out.println("== " + file + (measure ? " (measuring)" : ""));
        for (String l : s.report()) System.out.println(l);
        return s;
    }

    // The ticked checks' lines.
    private static List<String> ticked(SimRecorder.Recording r) {
        List<String> out = new ArrayList<>();
        for (SimRecorder.Check c : r.checks) if (c.on) out.add(c.line());
        return out;
    }

    @Test public void aRecordedTeleOpTestPassesFailsAfterAChangeAndCanBeUpdated() throws Exception {
        Path dir = Files.createTempDirectory("recorder-test");
        Path robot = GOBILDA.toAbsolutePath().normalize();
        SimEngine sim = engine(GOBILDA);
        SimRecorder.Recording rec;
        try {
            SimServer server = new SimServer(sim, 0, false, robot);
            SimRecorder r = server.tests.recorder;
            r.start(true);
            assertEquals("ok", server.command(cmd("action", "init", "opmode", "goBILDA Bot TeleOp")));
            SimRun.waitFor(() -> sim.ds.screen.updates() > 0, 3);
            pad(sim, "b", "1");                    // RED: a quick tap
            SimRun.pause(0.1);
            pad(sim, "b", "0");
            SimRun.pause(0.3);
            assertEquals("ok", server.command(cmd("action", "start")));
            SimRun.pause(0.3);
            pad(sim, "ly", "-0.25");               // a stick sweep: only where it ends counts
            SimRun.pause(0.05);
            pad(sim, "ly", "-0.5");
            SimRun.pause(1.0);
            pad(sim);                              // let go
            SimRun.pause(0.6);
            rec = r.stop();
            assertFalse(r.recording());
        } finally {
            sim.shutdown();
        }
        System.out.println(String.join("\n", rec.steps));
        assertTrue(rec.steps.toString(), rec.steps.contains("init \"goBILDA Bot TeleOp\""));
        assertTrue(rec.steps.toString(), rec.steps.contains("press g1.b"));
        assertTrue(rec.steps.toString(), rec.steps.contains("start"));
        assertTrue(rec.steps.toString(), rec.steps.contains("hold g1.left_stick_y -0.5"));
        assertFalse(rec.steps.toString(), rec.steps.contains("hold g1.left_stick_y -0.25"));
        assertTrue(rec.steps.toString(), rec.steps.contains("release g1.left_stick_y"));
        // only the commands SimScenario knows
        for (String l : rec.steps) {
            String w = l.split(" ")[0];
            assertTrue(l, Arrays.asList("init", "start", "stop", "wait", "press", "hold", "release", "set", "push", "expect").contains(w));
        }
        // nothing but driving changed: where the robot ended is the goal (ticked), with 3 in to spare
        List<String> checks = ticked(rec);
        System.out.println(String.join("\n", checks));
        assertTrue(checks.toString(), checks.stream().anyMatch(c -> c.matches("expect within [0-9.]+ robot y = -?[0-9.]+ 3")));
        assertTrue(checks.toString(), checks.contains("expect error none"));
        assertTrue(checks.toString(), checks.contains("expect phase RUNNING"));

        Path test = dir.resolve("drive.sim");
        Files.write(test, rec.text("drive", "robot " + robot, "").getBytes(StandardCharsets.UTF_8));
        assertEquals("the recorded test passes", 0, replay(test, false).failures());

        // "Change the code": a robot file with slower gearing (the drive reaches less far in the same time).
        String slow = new String(Files.readAllBytes(GOBILDA), StandardCharsets.UTF_8).replace("drive.gearRatio = 1", "drive.gearRatio = 2");
        Path slowRobot = dir.resolve("slow.properties");
        Files.write(slowRobot, slow.getBytes(StandardCharsets.UTF_8));
        String broken = rec.text("drive", "robot " + slowRobot, "");
        Files.write(test, broken.getBytes(StandardCharsets.UTF_8));
        assertTrue("the change makes it fail", replay(test, false).failures() > 0);

        // The change was on purpose: update the test from a measuring run, then it passes.
        SimScenario measured = replay(test, true);
        SimTests.Update u = SimTests.update(Arrays.asList(broken.split("\n", -1)), measured.seen);
        assertFalse("something changed", u.changed.isEmpty());
        assertTrue(u.stuck.toString(), u.stuck.isEmpty());
        assertTrue(u.text, u.text.contains("# Updated by the recorder on"));
        Files.write(test, u.text.getBytes(StandardCharsets.UTF_8));
        assertEquals("the updated test passes", 0, replay(test, false).failures());
    }

    @Test public void aRecordedAutonomousTestChecksThatItFinishesAndItsSteps() throws Exception {
        Path dir = Files.createTempDirectory("recorder-auto");
        Path robot = GOBILDA.toAbsolutePath().normalize();
        SimEngine sim = engine(GOBILDA);
        SimRecorder.Recording rec;
        try {
            SimServer server = new SimServer(sim, 0, false, robot);
            SimRecorder r = server.tests.recorder;
            r.start(true);
            server.command(cmd("action", "init", "opmode", "goBILDA Bot Launch and Back Up"));
            SimRun.waitFor(() -> sim.ds.screen.updates() > 0, 3);
            pad(sim, "b", "1");
            SimRun.pause(0.1);
            pad(sim, "b", "0");
            SimRun.pause(0.5);
            server.command(cmd("action", "start"));
            assertTrue("the autonomous ends", SimRun.waitFor(() -> sim.ds.phase() == SimDriverStation.Phase.STOPPED, 30));
            SimRun.pause(1.0);
            rec = r.stop();
        } finally {
            sim.shutdown();
        }
        List<String> checks = ticked(rec);
        System.out.println(String.join("\n", rec.steps) + "\n" + String.join("\n", checks));
        assertTrue(checks.toString(), checks.stream().anyMatch(c -> c.matches("expect within [0-9.]+ phase STOPPED")));
        assertTrue(checks.toString(), checks.contains("expect step 1 done"));
        assertTrue(checks.toString(), checks.contains("expect step 2 done"));
        assertTrue(checks.toString(), checks.stream().anyMatch(c -> c.startsWith("expect within 3 launched >= ")));
        // a goal check changed (the pieces), so where the robot ended is a detail: offered, not ticked
        assertTrue(rec.checks.stream().anyMatch(c -> c.id.equals("y") && !c.on));
        Path test = dir.resolve("auto.sim");
        Files.write(test, rec.text("auto", "robot " + robot, "").getBytes(StandardCharsets.UTF_8));
        assertEquals(0, replay(test, false).failures());
    }

    @Test public void stepsAreMadeFromTapsHoldsAndSweeps() throws Exception {
        SimEngine sim = engine(GOBILDA);
        try {
            SimServer server = new SimServer(sim, 0, false, null);
            SimRecorder r = server.tests.recorder;
            r.start(true);
            server.command(cmd("action", "init", "opmode", "goBILDA Bot TeleOp"));
            SimRun.waitFor(() -> sim.ds.screen.updates() > 0, 3);
            server.command(cmd("action", "start"));
            SimRun.pause(0.2);
            pad(sim, "a", "1");
            SimRun.pause(0.6);                     // held: hold ... release
            pad(sim, "a", "0");
            SimRun.pause(0.2);
            pad(sim, "rt", "0.53", "lx", "0.4", "x", "1");   // 0.53 -> 0.5 (steps of 0.25)
            SimRun.pause(0.5);
            pad(sim);                               // three let go at once: release g1.all
            SimRun.pause(0.2);
            server.command(cmd("action", "pose", "x", "-40.123", "y", "-30", "heading", "90"));
            server.command(cmd("action", "pose", "x", "-38", "y", "-30", "heading", "90"));   // a drag: the last spot only
            server.command(cmd("action", "clearPaths"));                                      // not part of a test
            SimRecorder.Recording rec = r.stop();
            List<String> s = rec.steps;
            System.out.println(String.join("\n", s));
            assertTrue(s.toString(), s.contains("hold g1.a"));
            assertTrue(s.toString(), s.contains("release g1.a"));
            assertTrue(s.toString(), s.contains("hold g1.right_trigger 0.5"));
            assertTrue(s.toString(), s.contains("hold g1.left_stick_x 0.5"));
            assertTrue(s.toString(), s.contains("release g1.all"));
            assertTrue(s.toString(), s.contains("set pose -38 -30 90"));
            assertFalse(s.toString(), s.contains("set pose -40.12 -30 90"));
            // waits before START are short; after START they are the real time
            assertTrue(s.toString(), s.stream().filter(l -> l.startsWith("wait ")).allMatch(l -> Double.parseDouble(l.substring(5)) < 1.5));
        } finally {
            sim.shutdown();
        }
    }

    @Test public void screenChecksKeepOnlyTheSteadyPart() {
        assertEquals("Intake : stopped", SimRecorder.steady("Intake : stopped"));
        assertEquals("Launcher : OFF  left", SimRecorder.steady("Launcher : OFF  left 0"));
        assertEquals("", SimRecorder.steady("Time : 2:00"));
        assertEquals("", SimRecorder.steady("Lift : 1203 ticks"));
        assertEquals(0.5, SimRecorder.stick("0.53"), 1e-9);
        assertEquals(0, SimRecorder.stick("0.08"), 1e-9);
        assertEquals(-1, SimRecorder.stick("-0.97"), 1e-9);
        assertEquals("12", SimRecorder.num(12.0));
        assertEquals("-56.25", SimRecorder.num(-56.25));
        assertEquals("0.5", SimRecorder.num(0.5));
        assertEquals(4.5, SimRecorder.waitFor(3), 1e-9);
    }

    @Test public void oneFailedCheckIsUpdatedFromWhatTheRunSaw() {
        SimScenario.Seen x = new SimScenario.Seen(7, "");
        x.value = 21.37;
        assertEquals("expect within 4.5 robot y = 21.37 3   # where it ends",
                SimTests.updatedLine("expect within 4.5 robot y = 30 3   # where it ends", x));
        assertEquals("expect launched >= 2", SimTests.updatedLine("expect launched >= 3", seen(2)));
        assertEquals("expect points \"red\" >= 10", SimTests.updatedLine("expect points \"red\" > 20", seen(10)));
        assertEquals("expect time < 6", SimTests.updatedLine("expect time < 3", seen(4)));
        // it happens, only later: a longer time limit
        SimScenario.Seen late = new SimScenario.Seen(3, "");
        late.took = 6;
        assertEquals("expect within 9 phase STOPPED", SimTests.updatedLine("expect within 4 phase STOPPED", late));
        // words never true: cannot be updated by a number
        assertNull(SimTests.updatedLine("expect phase STOPPED", new SimScenario.Seen(3, "")));
    }

    private static SimScenario.Seen seen(double v) {
        SimScenario.Seen x = new SimScenario.Seen(1, "");
        x.value = v;
        return x;
    }

    @Test public void testsAreOnlyReadAndWrittenInTheScenariosFolder() throws Exception {
        Path team = Files.createTempDirectory("team");
        SimEngine sim = engine(GOBILDA);
        try {
            SimTests t = new SimTests(sim, GOBILDA, team);
            assertTrue(t.write("main-teleop", "init \"x\"\n", false));
            assertFalse("no overwrite unless asked", t.write("main-teleop", "other", false));
            assertEquals("init \"x\"\n", t.read("main-teleop"));
            assertEquals(Arrays.asList("main-teleop"), t.list());
            for (String bad : new String[] {"../robot", "a/b", "..", "x.sim", "", "a b", "/etc/passwd", "..\\x"}) {
                try {
                    t.write(bad, "x", true);
                    fail("wrote " + bad);
                } catch (IllegalArgumentException expected) {
                    // refused
                }
            }
            // a robot file other than the team's usual one is named in the test (from the scenarios folder)
            assertTrue(t.robotLine(), t.robotLine().startsWith("robot ") && t.robotLine().endsWith("starter-bot-gobilda.properties"));
            SimTests none = new SimTests(sim, GOBILDA, null);
            try {
                none.write("x", "y", true);
                fail("no robot project: nowhere to save");
            } catch (IllegalArgumentException expected) {
                // the page offers a download instead
            }
        } finally {
            sim.shutdown();
        }
    }

    @Test public void eachTestStartsWithTheCodesTunablesNumbers() {
        SimFresh.readCodeValues();
        double code = com.crossbotics.ftchelper.examples.gobildabot.Tunables.Auto.backInches;
        try {
            com.crossbotics.ftchelper.examples.gobildabot.Tunables.Auto.backInches = code + 5;   // tuned on the page
            SimScenario.freshMatch();
            assertEquals(code, com.crossbotics.ftchelper.examples.gobildabot.Tunables.Auto.backInches, 1e-9);
        } finally {
            com.crossbotics.ftchelper.examples.gobildabot.Tunables.Auto.backInches = code;
        }
    }
}
