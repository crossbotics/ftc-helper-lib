package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.examples.cookbook.MechanismRecipes;
import com.crossbotics.ftchelper.examples.cookbook.Tunables;
import com.crossbotics.ftchelper.robot.Starting;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The scenario command "set tunable Section.field VALUE": a test stands in for a number not measured yet
 * (MEASURE), only for its own file. The cookbook's mechanism robot reads Tunables.IntakeDeploy.deployed.
 */
public class SimSetTunableTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private Path robotFile() throws Exception {
        SimFromRobot.Result res = SimFromRobot.merge(null, new MechanismRecipes.Robot().simLines(),
                MechanismRecipes.Robot.class.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile("mechanisms.properties").toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static SimScenario run(SimRun s, String... lines) throws InterruptedException {
        SimScenario sc = new SimScenario(s.sim);
        sc.run(Arrays.asList(lines));
        return sc;
    }

    @Test public void setTunableStandsInForAMeasureForOneFileOnly() throws Exception {
        CookbookSamples.fillAgain();   // every other MEASURE gets its sample number
        double sample = Tunables.IntakeDeploy.deployed;
        Tunables.IntakeDeploy.deployed = Starting.MEASURE;
        try (SimRun s = new SimRun(robotFile().toString())) {
            s.sim.ds.register("Cookbook Mechanisms", "TeleOp", MechanismRecipes.Controls.class);
            // Without the line, INIT stops on the MEASURE.
            SimScenario before = run(s, "init \"Cookbook Mechanisms\"", "expect error has \"deployed\"");
            assertEquals(String.join("\n", before.report()), 0, before.failures());

            SimScenario with = run(s,
                    "set tunable IntakeDeploy.deployed 0.6",
                    "init \"Cookbook Mechanisms\"",
                    "expect error none",
                    "start",
                    "expect phase RUNNING");
            String report = String.join("\n", with.report());
            assertEquals(report, 0, with.failures());
            assertTrue(report, report.contains("set in cookbook.Tunables"));
            // Put back at the end of the file: the next file starts with the code's value.
            assertTrue("restored", Starting.isMeasure(Tunables.IntakeDeploy.deployed));
            SimScenario after = run(s, "init \"Cookbook Mechanisms\"", "expect error has \"deployed\"");
            assertEquals(String.join("\n", after.report()), 0, after.failures());
        } finally {
            Tunables.IntakeDeploy.deployed = sample;
        }
    }

    @Test public void badNamesAndValuesSayWhatExists() throws Exception {
        try (SimRun s = new SimRun()) {
            SimScenario sc = run(s,
                    "set tunable IntakeDeploy.deployd 0.6",
                    "set tunable IntakeDeploi.deployed 0.6",
                    "set tunable IntakeDeploy.deployed open",
                    "set tunable deployed 0.6");
            List<String> r = sc.report();
            String all = String.join("\n", r);
            assertEquals(all, 4, sc.failures());
            assertTrue(all, r.get(0).contains("Tunables.IntakeDeploy has no field \"deployd\" (did you mean deployed?)"));
            assertTrue(all, r.get(0).contains("Fields: ") && r.get(0).contains("stowed"));
            assertTrue(all, r.get(1).contains("no Tunables section \"IntakeDeploi\" (did you mean IntakeDeploy?)"));
            assertTrue(all, r.get(2).contains("is a number (double), not \"open\""));
            assertTrue(all, r.get(3).contains("set tunable Section.field VALUE"));
        }
    }

    @Test public void setTunableIsRefusedWhileAnOpModeRuns() throws Exception {
        try (SimRun s = new SimRun()) {
            double before = Tunables.IntakeDeploy.travelSec;
            SimScenario sc = run(s,
                    "init \"Hardware Check\"",
                    "set tunable IntakeDeploy.travelSec 0.5",
                    "stop",
                    "set tunable IntakeDeploy.travelSec 0.5");
            String all = String.join("\n", sc.report());
            assertEquals(all, 1, sc.failures());
            assertTrue(all, sc.report().get(1).contains("goes before init (or after stop)"));
            assertEquals("restored at the end of the file (DEFAULT again)",
                    Double.doubleToRawLongBits(before), Double.doubleToRawLongBits(Tunables.IntakeDeploy.travelSec));
        }
    }
}
