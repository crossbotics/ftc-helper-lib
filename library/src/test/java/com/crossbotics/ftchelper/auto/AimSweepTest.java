package com.crossbotics.ftchelper.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.match.MatchTimer;
import org.junit.Test;

/** The aim sweep: a goal out of view is found by turning slowly, never more than the sweep each way. */
public class AimSweepTest {

    @Test public void aGoalOutOfViewIsFoundBySweepingAndThenAimedAt() {
        AutoRoutineTest.Sim s = new AutoRoutineTest.Sim();
        MatchTimer timer = new MatchTimer(30, s.clock::get);
        timer.start();
        // The goal is at heading 40; the camera sees it within 20 deg of where the robot faces.
        AutoRoutine r = s.routine(timer).turnTowardWithSweep("Aim",
                () -> Math.abs(40 - s.heading) < 20 ? 40 - s.heading : Double.NaN, 2, 0.5, 10, 60, 15, 0.3);
        r.runAll();
        assertEquals(40, s.heading, 2.5);
        assertEquals("Aim: done", r.getReport().get(0));
    }

    // Review (Codex): both ends are always reached; a small sweep still sweeps.
    @Test public void theSweepStopsIncludeBothEnds() {
        assertEquals(java.util.Arrays.asList(5.0, 0.0, -5.0), AutoRoutine.sweepStops(5, 15));
        assertEquals(java.util.Arrays.asList(15.0, 20.0, 5.0, -10.0, -20.0), AutoRoutine.sweepStops(20, 15));
        assertTrue(AutoRoutine.sweepStops(0, 15).isEmpty());
    }

    @Test public void neverSeenItTurnsBackAndSaysSo() {
        AutoRoutineTest.Sim s = new AutoRoutineTest.Sim();
        MatchTimer timer = new MatchTimer(30, s.clock::get);
        timer.start();
        double[] most = {0};
        AutoRoutine r = s.routine(timer).turnTowardWithSweep("Aim", () -> {
            most[0] = Math.max(most[0], Math.abs(s.heading));
            return Double.NaN;
        }, 2, 0.5, 20, 45, 15, 0.3);
        r.runAll();
        assertEquals("back where it started", 0, s.heading, 2.5);
        assertTrue("never past the sweep: " + most[0], most[0] <= 45 + 3);
        assertTrue(r.getReport().get(0), r.getReport().get(0).contains("not seen (swept)"));
    }

    // Review, 2026-10-30: a 1 s aim started a turn that ran to the drive's own 10 s limit.
    @Test public void aStuckRobotDoesNotTurnLongerThanTheAimsTimeLimit() {
        AutoRoutineTest.Sim s = new AutoRoutineTest.Sim() {
            @Override void step() { clock.addSec(0.01); }   // the motors run but the robot cannot turn
        };
        MatchTimer timer = new MatchTimer(30, s.clock::get);
        timer.start();
        AutoRoutine r = s.routine(timer).turnToward("Aim", () -> 40.0, 2, 0.5, 1);
        r.runAll();
        assertTrue("seconds: " + s.clock.nanos / 1e9, s.clock.nanos / 1e9 < 1.05);
        assertTrue(r.getReport().get(0), r.getReport().get(0).contains("not within") || r.getReport().get(0).contains("TIMED_OUT"));
    }

    @Test public void aSweepAlsoKeepsToItsTimeLimit() {
        AutoRoutineTest.Sim s = new AutoRoutineTest.Sim() {
            @Override void step() { clock.addSec(0.01); }
        };
        MatchTimer timer = new MatchTimer(30, s.clock::get);
        timer.start();
        AutoRoutine r = s.routine(timer).turnTowardWithSweep("Aim", () -> Double.NaN, 2, 0.5, 1, 60, 15, 0.3);
        r.runAll();
        // A robot that cannot turn: the sweep's turn stops at 1 s. It does not move, so no turn back.
        assertTrue("seconds: " + s.clock.nanos / 1e9, s.clock.nanos / 1e9 < 1.5);
    }
}
