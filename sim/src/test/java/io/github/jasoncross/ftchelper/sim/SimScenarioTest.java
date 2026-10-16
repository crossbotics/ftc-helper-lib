package io.github.jasoncross.ftchelper.sim;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Runs every scenario file: the library's (sim/scenarios, with the library's OpModes and the starter
 * robot file) and, when the team's TeamCode is built in, the team's (simulator/scenarios in the robot
 * project, with the team's OpModes and robot file). Each must pass.
 */
public class SimScenarioTest {

    @Test public void everyScenarioPasses() throws Exception {
        List<String> failed = new ArrayList<>();
        run(new File("scenarios"), "robots/starter.properties", SimDriverStation.Scope.LIBRARY, failed);
        String teamDir = System.getProperty("sim.teamDir", "");
        if (!teamDir.isEmpty()) {
            run(new File(teamDir, "simulator/scenarios"), new File(teamDir, "simulator/robot.properties").getPath(),
                    SimDriverStation.Scope.TEAM, failed);
        }
        assertTrue(String.join("\n", failed), failed.isEmpty());
    }

    private static void run(File dir, String robot, SimDriverStation.Scope scope, List<String> failed) throws Exception {
        File[] list = dir.listFiles((d, n) -> n.endsWith(".sim"));
        if (list == null) return;
        for (File f : list) {
            SimEngine sim = new SimEngine(SimRobot.load(Paths.get(robot)), scope);
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
    }
}
