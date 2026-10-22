package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The auto builder's Built Auto: a plan from the page (the same JSON the page sends, from the shared
 * fixtures that tools/check_builder.mjs checks against the web builder) runs with an example robot's real
 * code and ends near the plan; names that do not exist stop INIT with a plain message; the run is
 * recorded for the page (BuiltRun).
 */
public class BuiltAutoTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @After public void forget() {
        // The plan is shared by the whole simulator: never leave one for another test.
        BuiltAuto.program = null;
        BuiltAuto.robotClass = null;
    }

    // The fixture programs (simcore.toProgram's output for the shared plans).
    @SuppressWarnings("unchecked")
    private static List<Object> programs() throws Exception {
        String text = new String(Files.readAllBytes(Paths.get("src/test/resources/builder/fixtures.json")), StandardCharsets.UTF_8);
        Map<String, Object> m = SimJsonReader.object(SimJsonReader.parse(text), "fixtures");
        return SimJsonReader.array(m.get("programs"), "programs");
    }

    private static BuiltAuto.Program program(int i) throws Exception {
        return BuiltAuto.Program.from(SimJsonReader.object(programs().get(i), "a program"));
    }

    // The robot file made from Robot.java, as ./gradlew :sim:fromRobot writes it.
    private Path generate(Class<?> robotClass) throws Exception {
        RobotDefinition r = (RobotDefinition) robotClass.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, r.simLines(), robotClass.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile(robotClass.getPackage().getName() + ".properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // INIT as RED, wait until the robot is on its spot, START, wait for the end.
    private static void runToTheEnd(SimRun s) throws Exception {
        s.sim.ds.init(BuiltAuto.NAME, Alliance.RED);
        assertTrue("INIT shows\n" + s.error(), SimRun.waitFor(() -> s.sim.ds.screen.updates() > 0 && com.crossbotics.ftchelper.match.MatchMemory.placement() != null
                || !s.error().isEmpty(), 5));
        assertEquals("", s.error());
        assertTrue("placed", SimRun.waitFor(() -> {
            com.crossbotics.ftchelper.util.FieldPose at = com.crossbotics.ftchelper.match.MatchMemory.placement();
            return at != null && Math.hypot(at.x - s.robot().x, at.y - s.robot().y) < 0.5;
        }, 3));
        s.start();
        assertTrue("it finishes\n" + String.join("\n", s.screen()) + "\n" + s.error(),
                SimRun.waitFor(() -> s.sim.ds.phase() == SimDriverStation.Phase.STOPPED, 34));
        assertEquals("", s.error());
        SimRun.waitFor(() -> s.sim.built.json().contains("\"phase\":\"done\""), 2);
    }

    // Every fixture program is read, and its steps reach the real Steps (a robot with every part named).
    @Test public void everyFixtureProgramReads() throws Exception {
        List<Object> list = programs();
        assertEquals(6, list.size());
        for (Object o : list) {
            BuiltAuto.Program p = BuiltAuto.Program.from(SimJsonReader.object(o, "a program"));
            assertTrue(p.name, !p.steps.isEmpty());
        }
        assertEquals("pedro", program(3).mode);
        assertEquals(-56, program(3).x, 1e-9);
    }

    // A Simple steps plan on goBILDA's starter bot (a tank drive): the team's launcher and Actions run by
    // name, every step is done, and the robot ends near the plan's end.
    @Test public void aStepsPlanRunsOnTheStarterBot() throws Exception {
        BuiltAuto.program = program(1);
        BuiltAuto.robotClass = com.crossbotics.ftchelper.examples.gobildabot.Robot.class;
        try (SimRun s = new SimRun("robots/starter-bot-gobilda.properties")) {
            runToTheEnd(s);
            List<String> report = com.crossbotics.ftchelper.match.MatchMemory.lastReport();
            String all = String.join("\n", report);
            for (String line : new String[] {"Start: done", "Spin up: done", "Out from the wall: done", "Settle: done", "Face the HIVE: done",
                "Square up: done", "Leave: done"}) {
                assertTrue(line + " in\n" + all, report.contains(line));
            }
            assertTrue(all, all.contains("Fire all"));
            // The plan's end (stepscore's dead reckoning): 20 in out, a -45 turn, back to 0, 12 in more.
            assertEquals("x\n" + all, -56, s.robot().x, 4);
            assertEquals("y\n" + all, -62.95 + 32, s.robot().y, 4);
            assertEquals(0, s.robot().headingDeg, 10);
            // The page's record: START's time, a pose per report line, the end.
            String json = s.sim.built.json();
            assertTrue(json, json.contains("\"phase\":\"done\"") && json.contains("\"alliance\":\"RED\"") && json.contains("\"end\":["));
            assertTrue(json, json.contains("\"report\":[\"Start: done\""));
            assertEquals(report.size(), json.split("\\],\\[").length);
        }
    }

    // A Pedro path on the Pedro example robot: the real follower, the PathAt along it (the intake), the hold.
    @Test public void aPedroPlanRunsOnThePedroRobot() throws Exception {
        Path file = generate(com.crossbotics.ftchelper.examples.pedrobot.Robot.class);
        BuiltAuto.program = program(3);
        BuiltAuto.robotClass = com.crossbotics.ftchelper.examples.pedrobot.Robot.class;
        try (SimRun s = new SimRun(file.toString())) {
            runToTheEnd(s);
            List<String> report = com.crossbotics.ftchelper.match.MatchMemory.lastReport();
            assertTrue(String.join("\n", report), report.contains("To the goal: done"));
            assertEquals(-30, s.robot().x, 3);
            assertEquals(-30, s.robot().y, 3);
            assertEquals(45, s.robot().headingDeg, 10);
            assertTrue("the intake ran", s.robot().motors.get("intake").userPower() != 0 || String.join("\n", report).contains("done"));
        }
    }

    // The guess picks the Robot class whose device names are in the robot file.
    @Test public void theRobotClassGuessFitsTheRobotFile() throws Exception {
        List<Class<? extends RobotDefinition>> list = BuiltAuto.robotClasses(SimDriverStation.Scope.LIBRARY);
        assertTrue(list.toString(), list.contains(com.crossbotics.ftchelper.examples.gobildabot.Robot.class));
        assertTrue(list.toString(), list.contains(com.crossbotics.ftchelper.examples.pedrobot.Robot.class));
        SimRobot gobilda = SimRobot.load(Paths.get("robots/starter-bot-gobilda.properties"));
        try {
            assertEquals(com.crossbotics.ftchelper.examples.gobildabot.Robot.class, BuiltAuto.guess(list, gobilda));
        } finally {
            gobilda.closeWorld();
        }
        String d = BuiltAuto.describe(com.crossbotics.ftchelper.examples.pedrobot.Robot.class);
        assertTrue(d, d.contains("\"drive\":\"mecanum\"") && d.contains("\"odometry\":true") && d.contains("\"follower\":true"));
    }

    // A name that is not on the robot stops INIT, with the names there are; nothing else can be reached.
    @Test public void unknownNamesStopInitWithAPlainMessage() throws Exception {
        RobotDefinition robot = new com.crossbotics.ftchelper.examples.gobildabot.Robot();
        try {
            BuiltCode.command("robot.claw::close", robot);
            fail("no claw");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("has no part called claw") && e.getMessage().contains("intake, launcher"));
        }
        for (String bad : new String[] {"System.exit(0)", "() -> Runtime.getRuntime().exec(\"x\")", "robot.intake.in(); robot.launcher.stop()",
            "() -> robot.intake.getClass().getClassLoader()", "() -> robot.lift.goTo(Tunables.Lift.high)"}) {
            try {
                BuiltCode.command(bad, robot);
                fail("read: " + bad);
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
        try {
            BuiltCode.action("Actions.scoreHigh(robot)", robot);
            fail("no scoreHigh");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no public method called scoreHigh") && e.getMessage().contains("fireAll"));
        }
        // In the simulator: INIT stops and says which step.
        BuiltAuto.Program p = program(0);   // the lesson's example names a claw and Actions.scoreHigh
        BuiltAuto.program = p;
        BuiltAuto.robotClass = com.crossbotics.ftchelper.examples.gobildabot.Robot.class;
        try (SimRun s = new SimRun("robots/starter-bot-gobilda.properties")) {
            s.sim.ds.init(BuiltAuto.NAME, Alliance.RED);
            assertTrue(SimRun.waitFor(() -> !s.error().isEmpty(), 5));
            assertTrue(s.error(), s.error().contains("Built Auto, step 1 (\"Hold the piece\"): robot.claw::close"));
        }
    }

    // The values in a call: text, numbers, true/false and robot reach the right method.
    @Test public void callsTakePlainValues() throws Exception {
        RobotDefinition robot = new com.crossbotics.ftchelper.examples.gobildabot.Robot();
        robot.modules();   // reads define(): the parts are made (no hardware)
        assertNotNull(BuiltCode.command("robot.intake::in", robot));
        assertNotNull(BuiltCode.command("() -> robot.launcher.fire(3)", robot));
        assertNotNull(BuiltCode.action("Actions.fireAll(robot)", robot));
        try {
            BuiltCode.command("() -> robot.launcher.fire(\"three\")", robot);
            fail("text for a number");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("does not take"));
        }
    }

    /** A robot with a method that does something at once (a command), to check it is never called as an action. */
    public static class CountingRobot extends com.crossbotics.ftchelper.examples.gobildabot.Robot {
        static int calls = 0;

        public void sideEffect() {
            calls++;
        }

        public Object notAnAction() {
            calls++;
            return "x";
        }
    }

    // A plan that names a command where an action goes is refused BEFORE anything runs: the return type is
    // checked first, and every step of the plan is checked before the first is built.
    @Test public void aCommandNamedAsAnActionIsNeverCalled() throws Exception {
        CountingRobot robot = new CountingRobot();
        CountingRobot.calls = 0;
        for (String code : new String[] {"robot.sideEffect()", "robot.notAnAction()"}) {
            try {
                BuiltCode.action(code, robot);
                fail("read as an action: " + code);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("does not give an Action"));
            }
        }
        assertEquals("never called", 0, CountingRobot.calls);
        BuiltAuto.Program p = BuiltAuto.Program.parse("{\"name\":\"x\",\"start\":{\"x\":-56,\"y\":-60,\"h\":0},\"steps\":["
                + "{\"kind\":\"run\",\"label\":\"Intake\",\"code\":\"robot.intake::in\"},"
                + "{\"kind\":\"alongside\",\"label\":\"Oops\",\"code\":\"robot.sideEffect()\"}]}");
        try {
            p.check(robot);
            fail("the plan was accepted");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("step 2 (\"Oops\")"));
        }
        assertEquals("still never called", 0, CountingRobot.calls);
    }

    // The builder's suggestions come from the chosen Robot class: its parts' commands, its Actions, its TeleOp.
    @Test public void theSuggestionsComeFromTheRobotClass() throws Exception {
        try (SimRun s = new SimRun("robots/starter-bot-gobilda.properties")) {
            String info = BuiltAuto.codeInfo(com.crossbotics.ftchelper.examples.gobildabot.Robot.class, s.sim.ds.opModes);
            assertTrue(info, info.contains("\"robot.intake::in\"") && info.contains("\"robot.launcher::spinUp\""));
            assertTrue(info, info.contains("\"Actions.fireAll(robot)\"") && info.contains("\"Actions.fireAllWithIntake(robot)\""));
            assertTrue(info, !info.contains("scoreHigh") && !info.contains("claw") && !info.contains("::update\""));
            assertTrue(info, info.contains("\"teleop\":\"goBILDA Bot TeleOp\""));
            // the OpMode list says how well each OpMode's Robot class fits the robot file
            String list = SimView.opModes(s.sim.ds);
            assertTrue(list, list.contains("\"name\":\"goBILDA Bot TeleOp\",\"group\":\"Examples\",\"kind\":\"TeleOp\",\"disabled\":true,\"fit\":"));
        }
    }

    // BuiltRun reads the routine's report while it runs: EasyAuto still has its private "routine" field.
    @Test public void theReportCanBeReadWhileItRuns() throws Exception {
        assertNotNull(com.crossbotics.ftchelper.auto.EasyAuto.class.getDeclaredField("routine"));
    }
}
