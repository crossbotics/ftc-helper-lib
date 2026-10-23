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

    // Two scenario files in one run, in both orders, the way ./gradlew :sim:scenario runs a folder (one
    // process, SimScenario.runFile per file). The physics is made slow on purpose (about 150 ms a step,
    // instead of 10 ms), so a line that looked at the robot before the physics had caught up with INIT
    // fails every time instead of now and then: an intake whose push servo is missing was still up (it
    // starts down), and a TeleOp started at once was not on its INIT spot (it missed the piece ahead of it).
    // Each file must also start with nothing left from the one before (no OpMode or physics still running).
    @Test public void scenarioFilesInOneRunEachStartFreshAndSeeThePhysicsCatchUp() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("sim-fresh");
        java.nio.file.Path robot = dir.resolve("robot.properties");
        String text = new String(java.nio.file.Files.readAllBytes(Paths.get("robots/starter.properties")),
                java.nio.charset.StandardCharsets.UTF_8);
        // starter robot + an intake that folds up, pushed over by a servo (passive, like a team's R1 intake)
        text += "\nservo.kick = 0, part=gobilda-2000-speed\n"
                + "shape.fold = arm, at=6,0,6, size=3x8x0.5, start=90, minDeg=-10, maxDeg=90, passive=yes, drop=kick>0.6, fallsTo=-10\n";
        java.nio.file.Files.write(robot, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Path missing = dir.resolve("a-missing-servo.sim");
        java.nio.file.Files.write(missing, java.util.Arrays.asList(
                "set device kick missing",
                "init \"Main TeleOp\"",
                "expect shape fold = -10 0.1",       // put down by hand: down from INIT on
                "start",
                "expect shape fold = -10 0.1",
                "stop"));
        java.nio.file.Path placed = dir.resolve("b-start-at-once.sim");
        java.nio.file.Files.write(placed, java.util.Arrays.asList(
                "init \"Main TeleOp\"",
                "start",                             // at once: START puts it on the INIT spot first
                "expect robot x = 56 0.5",           // until an alliance is chosen, BLUE's spot (the start turned)
                "expect robot y = 61.55 0.5",
                "expect shape fold = 90 0.1",        // the servo is there: folded until it pushes
                "stop"));
        java.util.function.Consumer<SimEngine> slow = sim -> sim.onStep = () -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // shutdown: the physics thread ends
            }
        };
        List<String> failed = new ArrayList<>();
        java.nio.file.Path[][] orders = {{missing, placed}, {placed, missing}, {missing, placed}, {placed, missing}};
        for (java.nio.file.Path[] order : orders) {
            for (java.nio.file.Path f : order) {
                java.util.Set<Thread> before = new java.util.HashSet<>(Thread.getAllStackTraces().keySet());
                SimScenario s = SimScenario.runFile(f, robot, slow);
                if (s.failures() > 0) failed.add(f.getFileName() + ":\n" + String.join("\n", s.report()));
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    String n = t.getName();
                    if (!before.contains(t) && t.isAlive() && (n.equals("physics") || n.startsWith("OpMode "))) {
                        failed.add("still running after " + f.getFileName() + ": thread " + n);
                    }
                }
            }
        }
        assertTrue(String.join("\n", failed), failed.isEmpty());
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
