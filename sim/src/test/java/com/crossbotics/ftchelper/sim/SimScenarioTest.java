package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
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

    // A hand move right after INIT, with the physics held still until that move is done (so the order is the same
    // on every computer): the physics must not then put the robot on its INIT spot over it, or reset the field
    // under it (the page resets it at an autonomous's INIT). That lost the "set pose" of a file run in a folder,
    // where INIT shows its screen before the next physics step (10 ms): the robot was on BLUE's spot instead.
    @Test public void aHandMoveRightAfterInitIsNotUndoneByThePhysics() throws Exception {
        List<String> failed = new ArrayList<>();
        handMove(failed, sim -> sim.robot.x == 30, null,
                "init \"Main TeleOp\"",
                "set pose 30 -30 0",
                "expect robot x = 30 0.5",
                "expect robot y = -30 0.5");
        // the spot changes with the alliance chosen in INIT: the same there
        handMove(failed, sim -> sim.robot.x == 30, null,
                "init \"Main TeleOp\"",
                "press g1.b",
                "set pose 30 -30 0",
                "expect robot x = 30 0.5",
                "expect robot y = -30 0.5");
        // the page's INIT of an autonomous resets the field: before a piece is put down by hand, not after it
        handMove(failed, SimScenarioTest::pieceAt30,
                sim -> pieceAt30(sim) ? null : "the piece put down after INIT is gone",
                "init \"Score and Park\"",
                "set piece POLLEN 30 -30",
                "loops 3",
                "expect phase INIT",
                "expect error none");
        assertTrue(String.join("\n", failed), failed.isEmpty());
    }

    // A piece at x 30, y -30 (where the test put one down by hand; no piece starts there).
    private static boolean pieceAt30(SimEngine sim) {
        for (Object[] p : sim.robot.pieces.drawn()) {
            if (Math.abs((Double) p[1] - 30) < 1 && Math.abs((Double) p[2] + 30) < 1) return true;
        }
        return false;
    }

    // Runs the lines with the physics held still at its first step until "moved" is true (at most 10 s), then
    // checks "after" (null: nothing more; it returns what is wrong, or null). Adds a failure to the list.
    private static void handMove(List<String> failed, java.util.function.Predicate<SimEngine> moved,
            java.util.function.Function<SimEngine, String> after, String... lines) throws Exception {
        SimScenario.freshMatch();
        SimEngine sim = new SimEngine(SimRobot.load(Paths.get("robots/starter.properties")), SimDriverStation.Scope.LIBRARY);
        sim.freshAutoInit = true;    // as on the page
        sim.robot.setPose(-40, -40, 0);   // somewhere else than any INIT spot
        java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
        sim.onStep = () -> {
            if (held.getCount() == 0) return;
            held.countDown();
            try {
                SimRun.waitFor(() -> moved.test(sim), 10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // shutdown: the physics thread ends
            }
        };
        sim.start();
        try {
            assertTrue("the physics never ran", held.await(3, java.util.concurrent.TimeUnit.SECONDS));
            SimScenario s = new SimScenario(sim);
            s.run(java.util.Arrays.asList(lines));
            String wrong = after == null ? null : after.apply(sim);
            if (s.failures() > 0 || wrong != null) {
                failed.add(lines[1] + ":\n" + String.join("\n", s.report()) + (wrong == null ? "" : "\n" + wrong));
            }
        } finally {
            sim.shutdown();
        }
    }

    /** Sends one more screen as it stops, as a program stopped in the middle of its INIT loop does. */
    public static class ScreenAtStop extends com.qualcomm.robotcore.eventloop.opmode.LinearOpMode {
        @Override public void runOpMode() {
            telemetry.addLine("first ready");
            telemetry.update();
            waitForStart();
            telemetry.addLine("first stopping");
            telemetry.update();
        }
    }

    /** Takes a moment to make its parts, then names its start spot and shows its screen. */
    public static class SlowFirstScreen extends com.qualcomm.robotcore.eventloop.opmode.LinearOpMode {
        @Override public void runOpMode() {
            sleep(300);
            com.crossbotics.ftchelper.match.MatchMemory.setPlacement(new com.crossbotics.ftchelper.util.FieldPose(-56, -55, 0), "");
            telemetry.addLine("second ready");
            telemetry.update();
            waitForStart();
        }
    }

    // INIT over a program still in INIT: the first one's last screen is not the second one's first screen
    // (the scenario went on before the second had named its start spot, which then undid a "set pose").
    @Test public void initWaitsForItsOwnFirstScreenNotTheLastProgramsLastOne() throws Exception {
        SimScenario.freshMatch();
        SimEngine sim = new SimEngine(SimRobot.load(Paths.get("robots/starter.properties")), SimDriverStation.Scope.LIBRARY);
        sim.ds.register("First", "Autonomous", ScreenAtStop.class);
        sim.ds.register("Second", "Autonomous", SlowFirstScreen.class);
        sim.start();
        try {
            SimScenario s = new SimScenario(sim);
            s.run(java.util.Arrays.asList(
                    "init \"First\"",
                    "init \"Second\"",
                    "set pose 30 -30 0",
                    "expect within 2 screen has \"second ready\"",
                    "expect robot x = 30 0.5"));
            assertEquals(String.join("\n", s.report()), 0, s.failures());
        } finally {
            sim.shutdown();
        }
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
