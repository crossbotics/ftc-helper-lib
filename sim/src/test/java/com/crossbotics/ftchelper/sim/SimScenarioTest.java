package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Runs every scenario file: the library's (sim/scenarios, with the library's OpModes and the starter
 * robot file) and, when the team's TeamCode is built in, the team's (simulator/scenarios in the robot
 * project, with the team's OpModes and robot file). Each must pass. A scenario with a "robot PATH" line
 * uses that robot file instead (the starter bots in sim/robots).
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

    @Test public void eachScenarioFileStartsWithAFreshMatch() {
        // One file's autonomous must not carry over to the next file's TeleOp (alliance, pose, report).
        com.crossbotics.ftchelper.match.MatchMemory.remember(com.crossbotics.ftchelper.match.Alliance.RED,
                com.crossbotics.ftchelper.match.MatchMemory.fieldPose(com.crossbotics.ftchelper.match.Alliance.RED, 10, 20, 90));
        com.crossbotics.ftchelper.match.MatchMemory.rememberReport(java.util.Arrays.asList("Back up: done"));
        SimScenario.freshMatch();
        assertFalse(com.crossbotics.ftchelper.match.MatchMemory.hasAlliance());
        assertTrue(com.crossbotics.ftchelper.match.MatchMemory.lastReport().isEmpty());
        assertFalse(com.crossbotics.ftchelper.util.PoseStore.has());
    }

    private static void run(File dir, String robot, SimDriverStation.Scope scope, List<String> failed) throws Exception {
        File[] list = dir.listFiles((d, n) -> n.endsWith(".sim"));
        if (list == null) return;
        for (File f : list) {
            SimScenario.freshMatch();
            SimEngine sim = new SimEngine(SimRobot.load(SimScenario.robotFor(f.toPath(), Paths.get(robot))), scope);
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
