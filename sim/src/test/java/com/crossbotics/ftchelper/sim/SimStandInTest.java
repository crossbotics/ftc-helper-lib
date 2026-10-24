package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.examples.cookbook.MechanismRecipes;
import com.crossbotics.ftchelper.examples.cookbook.Tunables;
import com.crossbotics.ftchelper.robot.Starting;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The robot file's stand-ins for MEASURE numbers ("tunable.IntakeDeploy.deployed = 0.5"): :sim:fromRobot
 * writes them, and the simulator uses them at INIT (only while the code says MEASURE), so the code runs in
 * the simulator before the robot is measured. The cookbook's mechanism robot, with every MEASURE as written.
 */
public class SimStandInTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String[] SECTIONS = {"Tunables$Lift", "Tunables$Arm", "Tunables$Claw", "Tunables$Wrist", "Tunables$IntakeDeploy"};
    private static final String NOTE = "Simulator only: a stand-in for MEASURE (the library's default). The real robot stops at INIT "
            + "until you write the measured number in Tunables.java";

    @Before public void measureLikeTheCode() {
        // A fresh robot app: tuning-menu numbers from earlier tests (made with the sample numbers) forgotten.
        com.crossbotics.ftchelper.robot.RobotDefinition.freshRobotApp();
        CookbookSamples.fillAgain();
        CookbookSamples.measureAgain(SECTIONS);
    }

    @After public void sampleNumbersBack() {
        CookbookSamples.fillAgain();
    }

    private static String generate(String existing) {
        SimFromRobot.Result res = SimFromRobot.merge(existing, new MechanismRecipes.Robot().simLines(),
                MechanismRecipes.Robot.class.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        return res.text;
    }

    private Path write(String text) throws Exception {
        Path file = tmp.newFile().toPath();
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // One scenario "file": a fresh robot app first, as the scenario command does for each file.
    private static SimScenario run(SimRun s, String... lines) throws InterruptedException {
        com.crossbotics.ftchelper.robot.RobotDefinition.freshRobotApp();
        SimScenario sc = new SimScenario(s.sim);
        sc.run(Arrays.asList(lines));
        return sc;
    }

    @Test public void fromRobotWritesAStandInForEachMeasureWithTheLibrarysDefault() throws Exception {
        String text = generate(null);
        assertTrue(text, text.contains("tunable.IntakeDeploy.stowed = 0   # from Robot.java. " + NOTE + "\n"));
        assertTrue(text, text.contains("tunable.IntakeDeploy.deployed = 0.5   # from Robot.java. " + NOTE + "\n"));
        // A lift's ends: the same as its motor line in the file; a position: the library's guess of that name.
        assertTrue(text, text.contains("min=0, max=2600"));
        assertTrue(text, text.contains("tunable.Lift.min = 0   #"));
        assertTrue(text, text.contains("tunable.Lift.max = 2600   #"));
        assertTrue(text, text.contains("tunable.Lift.high = 2400   #"));
        assertTrue(text, text.contains("tunable.Claw.open = 0.2   #"));
        assertTrue(text, text.contains("tunable.Wrist.level = 0.5   #"));
        // A position the library has no guess for: the middle of the range (an arm: 0 to 180 degrees).
        assertTrue(text, text.contains("tunable.Arm.pickup = 90   #"));
        assertFalse("DEFAULT gets no stand-in", text.contains("tunable.IntakeDeploy.travelSec"));
        // The file loads, with its stand-ins.
        SimRobot r = SimRobot.load(write(text));
        assertEquals("0.5", r.standIns.get("IntakeDeploy.deployed"));
    }

    @Test public void aNumberInTheCodeRemovesTheStandInAndAHandEditedLineIsKept() throws Exception {
        String first = generate(null);
        // The team changes one stand-in by hand (no mark): it is theirs now.
        String edited = first.replaceAll("tunable\\.IntakeDeploy\\.deployed = 0\\.5   #[^\n]*", "tunable.IntakeDeploy.deployed = 0.7");
        // The Servo Finder gave stowed: the code has a number now.
        Tunables.IntakeDeploy.stowed = 0.05;
        String again = generate(edited);
        assertFalse(again, again.contains("tunable.IntakeDeploy.stowed"));
        assertTrue(again, again.contains("tunable.IntakeDeploy.deployed = 0.7\n"));
        assertFalse(again, again.contains("tunable.IntakeDeploy.deployed = 0.5"));
        // Running it again changes nothing.
        assertEquals(again, generate(again));
    }

    @Test public void initUsesTheStandInsAndSaysSoThenTheyGoBackToMeasure() throws Exception {
        try (SimRun s = new SimRun(write(generate(null)).toString())) {
            s.sim.ds.register("Cookbook Mechanisms", "TeleOp", MechanismRecipes.Controls.class);
            s.init("Cookbook Mechanisms");
            assertEquals(s.sim.ds.error(), "", s.sim.ds.error());
            assertEquals(0.5, Tunables.IntakeDeploy.deployed, 0);
            assertEquals(2600, Tunables.Lift.max, 0);
            String note = s.sim.ds.standInNote();
            assertTrue(note, note.contains("Tunables.IntakeDeploy.deployed = MEASURE: the simulator uses the stand-in 0.5 from the "
                    + "robot file (the real robot stops here)"));
            assertTrue(note, note.contains("Tunables.Lift.min = MEASURE: the simulator uses the stand-in 0"));
            // The robot deploys at START: to the stand-in.
            s.start();
            assertTrue("deployed", SimRun.waitFor(() -> Math.abs(s.robot().servos.get("intake_deploy").target() - 0.5) < 0.01, 3));
            s.sim.ds.stop();
            assertTrue("MEASURE again after STOP", Starting.isMeasure(Tunables.IntakeDeploy.deployed));
            assertTrue("MEASURE again after STOP", Starting.isMeasure(Tunables.Lift.max));
        }
    }

    @Test public void theScenarioReportShowsTheNoteAndSetTunableWins() throws Exception {
        try (SimRun s = new SimRun(write(generate(null)).toString())) {
            s.sim.ds.register("Cookbook Mechanisms", "TeleOp", MechanismRecipes.Controls.class);
            SimScenario plain = run(s, "init \"Cookbook Mechanisms\"", "expect error none", "stop");
            String report = String.join("\n", plain.report());
            assertEquals(report, 0, plain.failures());
            assertTrue(report, report.contains("Tunables.IntakeDeploy.deployed = MEASURE: the simulator uses the stand-in 0.5"));

            SimScenario set = run(s, "set tunable IntakeDeploy.deployed 0.6", "init \"Cookbook Mechanisms\"", "expect error none",
                    "start", "expect within 3 servo intake_deploy target = 0.6 0.01", "stop");
            report = String.join("\n", set.report());
            assertEquals(report, 0, set.failures());
            assertFalse(report, report.contains("IntakeDeploy.deployed = MEASURE"));
            assertTrue(report, report.contains("IntakeDeploy.stowed = MEASURE"));

            // set tunable ... MEASURE wins too: a test of the stop still stops.
            SimScenario stops = run(s, "set tunable IntakeDeploy.deployed MEASURE", "init \"Cookbook Mechanisms\"",
                    "expect error has \"deployed\"");
            assertEquals(String.join("\n", stops.report()), 0, stops.failures());
            // The next file: the stand-in again.
            SimScenario next = run(s, "init \"Cookbook Mechanisms\"", "expect error none", "stop");
            assertEquals(String.join("\n", next.report()), 0, next.failures());
            assertTrue(Starting.isMeasure(Tunables.IntakeDeploy.deployed));
        }
    }

    @Test public void nothingLeaksIntoTheNextRobotFile() throws Exception {
        String with = generate(null);
        Path withFile = write(with);
        try (SimRun s = new SimRun(withFile.toString())) {
            s.sim.ds.register("Cookbook Mechanisms", "TeleOp", MechanismRecipes.Controls.class);
            s.init("Cookbook Mechanisms");
            assertEquals("", s.sim.ds.error());
            // Left in INIT: a new simulator (as for the next scenario file) starts from the code's MEASURE.
        }
        StringBuilder without = new StringBuilder();
        for (String line : with.split("\n")) if (!line.startsWith("tunable.")) without.append(line).append('\n');
        try (SimRun s = new SimRun(write(without.toString()).toString())) {
            assertTrue("the OpMode left in INIT took its stand-ins out", Starting.isMeasure(Tunables.IntakeDeploy.deployed));
            s.sim.ds.register("Cookbook Mechanisms", "TeleOp", MechanismRecipes.Controls.class);
            s.init("Cookbook Mechanisms");
            assertTrue(s.sim.ds.error(), s.sim.ds.error().contains("= MEASURE"));
            assertEquals("", s.sim.ds.standInNote());
        }
    }

    @Test public void aWrittenNumberWinsOverAStandInLine() throws Exception {
        String text = generate(null) + "tunable.IntakeDeploy.travelSec = 0.9\n";
        Tunables.IntakeDeploy.deployed = 0.8;   // measured: the generated line would go, a hand-written one would not
        text += "tunable.IntakeDeploy.deployed = 0.7\n";
        try (SimRun s = new SimRun(write(text).toString())) {
            s.sim.ds.register("Cookbook Mechanisms", "TeleOp", MechanismRecipes.Controls.class);
            s.init("Cookbook Mechanisms");
            assertEquals(s.sim.ds.error(), "", s.sim.ds.error());
            assertEquals(0.8, Tunables.IntakeDeploy.deployed, 0);
            assertTrue("DEFAULT is not MEASURE: no stand-in", Starting.isDefaultFromCatalog(Tunables.IntakeDeploy.travelSec));
            assertFalse(s.sim.ds.standInNote(), s.sim.ds.standInNote().contains("deployed"));
            s.sim.ds.stop();
            assertEquals(0.8, Tunables.IntakeDeploy.deployed, 0);
        }
    }

    @Test public void aStandInThatIsNotANumberStopsTheRobotFile() throws Exception {
        try {
            SimRobot.load(write("battery = 12.8\ntunable.IntakeDeploy.deployed = open\n"));
            fail("should stop");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("tunable.IntakeDeploy.deployed") && e.getMessage().contains("must be a number"));
        }
    }
}
