package io.github.jasoncross.ftchelper.sim;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Runs every scenario file: the library's (sim/scenarios) and, inside a robot project, the team's
 * (simulator/scenarios next to TeamCode). Each must pass.
 */
public class SimScenarioTest {

    @Test public void everyScenarioPasses() throws Exception {
        List<File> files = new ArrayList<>();
        for (String dir : new String[] {"scenarios", "../../simulator/scenarios"}) {
            File[] list = new File(dir).listFiles((d, n) -> n.endsWith(".sim"));
            if (list != null) for (File f : list) files.add(f);
        }
        List<String> failed = new ArrayList<>();
        for (File f : files) {
            boolean team = f.getPath().contains("simulator");
            String robot = team && new File("../../simulator/robot.properties").isFile()
                    ? "../../simulator/robot.properties" : "robots/starter.properties";
            SimEngine sim = new SimEngine(SimRobot.load(Paths.get(robot)));
            sim.start();
            SimScenario s = new SimScenario(sim);
            try {
                s.run(f.toPath());
            } finally {
                sim.shutdown();
            }
            System.out.println("== " + f);
            for (String line : s.report()) System.out.println(line);
            if (s.failures() > 0) failed.add(f + ": " + s.failures() + " failed check(s)");
        }
        assertTrue(String.join("\n", failed), failed.isEmpty());
    }
}
