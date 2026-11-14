package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.examples.turretbot.Tunables;
import com.crossbotics.ftchelper.finders.TuneAndCheckOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The Move Tuner on a simulated robot, opened from "Tune and check": each test runs its real moves
 * and turns, ends, and writes its result and the lines to copy.
 *
 * <p>The simulator's loop runs much faster than a Control Hub's, so its turns are not a real robot's:
 * these tests check that each test runs and ends, not the exact numbers (MoveMathTest checks the rules).
 */
public class MoveTunerSimTest {
    static {
        CookbookSamples.fill();
    }

    private boolean calibratedBefore;

    @Before public void tunedDrive() {
        // The Move Tuner runs only after the Drive Tuner (calibrated = true).
        calibratedBefore = Tunables.Drive.calibrated;
        Tunables.Drive.calibrated = true;
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>singletonList(
                com.crossbotics.ftchelper.examples.turretbot.Robot.class));
    }

    @After public void putBack() {
        Tunables.Drive.calibrated = calibratedBefore;
        TuneAndCheckOpMode.useRobots(null);
    }

    private static String screen(SimRun s) {
        return String.join("\n", s.screen());
    }

    // Opens the Move Tuner from the menu and starts it.
    private static void open(SimRun s) throws Exception {
        s.init(TuneAndCheckOpMode.NAME);
        for (int i = 0; i < 12 && !s.screenHas("> Move Tuner"); i++) s.press(v -> s.g1().dpad_down = v);
        assertTrue(screen(s), s.screenHas("> Move Tuner"));
        s.press(v -> s.g1().a = v);
        assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("Move tuner"), 1));
        s.start();
    }

    // Chooses a test (by its name on the list), starts it, and waits until it ends.
    private static void run(SimRun s, String test, double maxSec) throws Exception {
        for (int i = 0; i < 6 && !s.screenHas("> " + test); i++) s.press(v -> s.g1().dpad_down = v);
        assertTrue(screen(s), s.screenHas("> " + test));
        s.press(v -> s.g1().a = v);
        assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("running..."), 2));
        assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("D-pad up/down = choose"), maxSec));
    }

    @Test public void theMenuListsItAfterTheDriveTuner() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init(TuneAndCheckOpMode.NAME);
            List<String> lines = s.screen();
            int drive = -1, move = -1, shot = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("Drive Tuner")) drive = i;
                if (lines.get(i).contains("Move Tuner")) move = i;
                if (lines.get(i).contains("Shot Tuner")) shot = i;
            }
            assertTrue(screen(s), drive >= 0 && move > drive && shot > move);
        }
    }

    // Creep finds moveMinPower; Stops then uses it and finds slowDownIn.
    @Test public void creepThenStopsFindTheirNumbers() throws Exception {
        try (SimRun s = new SimRun()) {
            open(s);
            run(s, "1 Creep", 30);
            assertTrue(screen(s), s.screenHas("moveMinPower now"));
            assertTrue(screen(s), s.screenHas("Tunables.Drive: moveMinPower = "));
            run(s, "2 Stops", 200);
            assertTrue(screen(s), s.screenHas("slowDownIn 0: slide"));
            assertTrue(screen(s), s.screenHas("Tunables.Drive: slowDownIn = "));
            assertEquals("", s.error());
        }
    }

    @Test public void turnsEndWithAResult() throws Exception {
        try (SimRun s = new SimRun()) {
            open(s);
            run(s, "3 Turns", 60);
            assertTrue(screen(s), s.screenHas("+90: past"));
            assertTrue(screen(s), s.screenHas("Worst: past"));
            assertTrue(screen(s), s.screenHas("OK:") || s.screenHas("Tunables.Drive: turn"));
            assertEquals("", s.error());
        }
    }

    @Test public void driftReportsTheHeadingOfEachMove() throws Exception {
        try (SimRun s = new SimRun()) {
            open(s);
            run(s, "4 Drift", 90);
            assertTrue(screen(s), s.screenHas("Forward 48 in: heading"));
            assertTrue(screen(s), s.screenHas("Left 48 in: heading"));
            assertEquals("", s.error());
        }
    }

    @Test public void moveAndTurnEndsWithAResult() throws Exception {
        try (SimRun s = new SimRun()) {
            open(s);
            run(s, "5 Move and turn", 90);
            assertTrue(screen(s), s.screenHas("Right to 90 deg:"));
            assertTrue(screen(s), s.screenHas("Worst: off"));
            assertEquals("", s.error());
        }
    }

    // GUIDE stops a moving test at once: nothing measured, the wheels stop, help opens.
    @Test public void guideStopsATest() throws Exception {
        try (SimRun s = new SimRun()) {
            open(s);
            for (int i = 0; i < 6 && !s.screenHas("> 4 Drift"); i++) s.press(v -> s.g1().dpad_down = v);
            s.press(v -> s.g1().a = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("running..."), 2));
            SimRun.pause(0.5);
            s.press(v -> s.g1().guide = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("help"), 2));
            for (SimMotor m : s.robot().motors.values()) assertEquals(m.name, 0, m.userPower(), 1e-9);
            s.press(v -> s.g1().guide = v);
            assertTrue(screen(s), SimRun.waitFor(() -> s.screenHas("not measured"), 2));
            assertEquals("", s.error());
        }
    }

    // Before the Drive Tuner: INIT works and says so, and A starts nothing.
    @Test public void beforeTheDriveTunerItSaysSo() throws Exception {
        Tunables.Drive.calibrated = false;
        try (SimRun s = new SimRun()) {
            open(s);
            assertTrue(screen(s), s.screenHas("Run the Drive Tuner first"));
            s.press(v -> s.g1().a = v);
            SimRun.pause(0.3);
            assertTrue(screen(s), !s.screenHas("running..."));
            assertEquals("", s.error());
        }
    }
}
