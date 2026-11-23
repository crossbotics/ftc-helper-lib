package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.finders.FlywheelTuneResults;
import com.crossbotics.ftchelper.finders.TuneAndCheckOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The Flywheel Tuner, run whole in the simulator from Tune and check: nothing moves in INIT, a held button
 * starts nothing, any button and help stop it in the same loop, each stop needs a fresh press, and a bad speed
 * reading or a battery under 10 V stops it.
 */
public class FlywheelTunerSimTest {
    static {
        CookbookSamples.fill();
    }

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private static final Class<? extends RobotDefinition> ROBOT = com.crossbotics.ftchelper.examples.flywheelbot.Robot.class;

    // Each test starts with no saved tests (they would make it go on from the last test's place).
    @Before @After public void noSavedTests() throws Exception {
        TuneAndCheckOpMode.useRobots(null);
        Files.deleteIfExists(FlywheelTuneResults.file().toPath());
        Files.deleteIfExists(new java.io.File(FlywheelTuneResults.file().getPath() + ".new").toPath());
        Files.deleteIfExists(new java.io.File(FlywheelTuneResults.file().getParentFile(), "flywheel-tuner-old.txt").toPath());
    }

    private Path robotFile() throws Exception {
        RobotDefinition r = ROBOT.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, SimFromRobot.holdsFromPieces(r.simLines(), r),
                ROBOT.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile().toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String screen(SimRun s) {
        return String.join("\n", s.screen());
    }

    // Tune and check, the arrow on the Flywheel Tuner, A: its INIT screen.
    private SimRun open() throws Exception {
        SimRun s = new SimRun(robotFile().toString());
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>singletonList(ROBOT));
        s.init(TuneAndCheckOpMode.NAME);
        for (int i = 0; i < 20 && !s.screenHas("> Flywheel Tuner"); i++) s.press(v -> s.g1().dpad_down = v);
        assertTrue(screen(s), s.screenHas("> Flywheel Tuner"));
        s.press(v -> s.g1().a = v);
        assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("STOP (■) on the Driver Hub"), 2));
        return s;
    }

    private static boolean anyDriven(SimRun s) {
        for (SimMotor m : s.robot().motors.values()) if (m.driven()) return true;
        return false;
    }

    private static SimMotor flywheel(SimRun s) {
        return s.robot().motors.get("launcher");
    }

    // A, then the 3 s countdown, then 1 s at rest: the flywheel runs.
    private static void startAndWaitForTheWheel(SimRun s) throws Exception {
        s.press(v -> s.g1().a = v);
        assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("Starting in"), 1));
        assertTrue("the countdown moves nothing", !anyDriven(s));
        assertTrue(screen(s), SimRun.waitFor(() -> flywheel(s).driven(), 8));
    }

    // The power is off in the next loops, and nothing starts again by itself.
    private static void stopsAndStaysStopped(SimRun s, String what) throws Exception {
        s.waitForLoops(2);
        assertFalse(what + ": power off\n" + screen(s), flywheel(s).driven());
        SimRun.pause(2);
        assertFalse(what + ": nothing restarts by itself", anyDriven(s));
    }

    @Test public void nothingMovesInInitNorAtStartAndAHeldButtonStartsNothing() throws Exception {
        try (SimRun s = open()) {
            assertTrue(screen(s), s.screenHas("The flywheel runs at up to FULL power."));
            assertTrue(screen(s), s.screenHas("ANY button stops the flywheel."));
            s.g1().a = true;                         // held in INIT, through START
            SimRun.pause(1);
            assertFalse("INIT moves nothing", anyDriven(s));
            s.start();
            SimRun.pause(4.5);
            assertFalse("a held A starts nothing\n" + screen(s), anyDriven(s));
            assertTrue(screen(s), s.screenHas("Let go of all sticks and buttons."));
            s.g1().a = false;
            SimRun.pause(0.3);
            startAndWaitForTheWheel(s);              // a fresh press does
            assertEquals("", s.error());
        }
    }

    @Test public void anyButtonStopsItAndAFreshPressGoesOn() throws Exception {
        try (SimRun s = open()) {
            s.start();
            // B in the countdown: nothing ever moves.
            s.press(v -> s.g1().a = v);
            s.press(v -> s.g1().b = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("STOPPED"), 1));
            stopsAndStaysStopped(s, "B in the countdown");
            // Each button stops the running flywheel; each time a new A goes on.
            Consumer<Boolean>[] buttons = buttons(s);
            for (Consumer<Boolean> b : buttons) {
                startAndWaitForTheWheel(s);
                assertTrue(screen(s), s.screenHas("Test running. ANY button stops it."));
                b.accept(true);                      // held: it stops at once, and a held button starts nothing
                assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("STOPPED"), 1));
                stopsAndStaysStopped(s, "a held button");
                b.accept(false);
                s.waitForLoops(2);
                assertTrue(screen(s), s.screenHas("Finished tests are saved."));
                assertTrue(screen(s), s.screenHas("go on from test 1"));
            }
            assertEquals("", s.error());
        }
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Boolean>[] buttons(SimRun s) {
        return new Consumer[] {
            (Consumer<Boolean>) v -> s.g1().x = v,
            (Consumer<Boolean>) v -> s.g1().a = v,
            (Consumer<Boolean>) v -> s.g1().right_bumper = v,
            (Consumer<Boolean>) v -> s.g1().dpad_down = v,
            (Consumer<Boolean>) v -> s.g2().y = v,
        };
    }

    @Test public void helpStopsItAndNothingRestartsWhenHelpCloses() throws Exception {
        try (SimRun s = open()) {
            s.start();
            startAndWaitForTheWheel(s);
            s.press(v -> s.g1().guide = v);          // help opens
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("help"), 1));
            stopsAndStaysStopped(s, "help");
            s.press(v -> s.g1().guide = v);          // help closes
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("Help was opened, so the test stopped."), 1));
            stopsAndStaysStopped(s, "help closed");
            startAndWaitForTheWheel(s);              // a fresh A goes on
            assertEquals("", s.error());
        }
    }

    @Test public void aSpeedThatReadsZeroStopsIt() throws Exception {
        try (SimRun s = open()) {
            s.start();
            startAndWaitForTheWheel(s);
            s.robot().setEncoder("launcher", "none");   // the encoder cable comes out
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("read 0 or backward"), 3));
            stopsAndStaysStopped(s, "a dead encoder");
            assertTrue(screen(s), s.screenHas("Encoder Check"));
            assertEquals("", s.error());
        }
    }

    @Test public void aBatteryUnder10VoltsStopsIt() throws Exception {
        try (SimRun s = open()) {
            s.start();
            startAndWaitForTheWheel(s);
            s.robot().setBattery(9.5);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("too low (under 10 V)"), 3));
            stopsAndStaysStopped(s, "a battery under 10 V");
            // Decided: the finished tests are kept (only the running test does not count).
            assertTrue(screen(s), s.screenHas("Finished tests are saved. The test that"));
            assertEquals("", s.error());
        }
    }

    @Test public void aBatteryThatIsNotFullStartsNothing() throws Exception {
        try (SimRun s = open()) {
            s.robot().setBattery(12.3);
            s.start();
            SimRun.pause(0.6);                       // the battery reader's first answer
            s.press(v -> s.g1().a = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("NOT full"), 1));
            SimRun.pause(4.5);
            assertFalse("nothing moves", anyDriven(s));
            assertEquals("", s.error());
        }
    }

    // A finished session (the flywheel bot's settings as its Tunables.java has them now): the results show,
    // and X two times starts a new one, keeping the old file.
    @Test public void xTwoTimesStartsANewSessionAfterTheEnd() throws Exception {
        String today = "PID kP=1.17025 kI=0.117025 kD=0 kF=11.7025 ks=0 kv=0.002 full=200 coast=75 hyst=50 max=1";
        Files.write(FlywheelTuneResults.file().toPath(), java.util.Arrays.asList(
                "version 1",
                "setup " + today + " mid=2300.00 low=2070.00 high=2530.00 band=60.00 drop=150.00 pace=0.25 cpr=28.00",
                "test LOW done INFO | - |  | "), StandardCharsets.UTF_8);
        try (SimRun s = open()) {
            s.start();
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("result (page 1 of 4)"), 2));
            s.press(v -> s.g1().x = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("Press X again"), 1));
            s.press(v -> s.g1().x = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("run 1 of 2, FULL battery"), 1));
            assertTrue("the old session is kept",
                    new java.io.File(FlywheelTuneResults.file().getParentFile(), "flywheel-tuner-old.txt").isFile());
            assertFalse(anyDriven(s));
            assertEquals("", s.error());
        }
    }
}
