package com.crossbotics.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// The Move Tuner's rules (MoveMath): each one by itself, with made-up measurements.
public class MoveMathTest {

    @Test public void creepPowerGetsAMarginAndRoundsUp() {
        // 0.10 x 1.3 = 0.13 -> up to 0.14
        assertEquals(0.14, MoveMath.moveMinPowerFromCreep(0.10, Double.NaN), 1e-9);
        // strafing needs more: the larger one counts
        assertEquals(0.20, MoveMath.moveMinPowerFromCreep(0.10, 0.15), 1e-9);
        // never below 0.10, never above 0.60
        assertEquals(0.10, MoveMath.moveMinPowerFromCreep(0.02, Double.NaN), 1e-9);
        assertEquals(0.60, MoveMath.moveMinPowerFromCreep(0.55, Double.NaN), 1e-9);
        assertTrue(Double.isNaN(MoveMath.moveMinPowerFromCreep(Double.NaN, Double.NaN)));
    }

    @Test public void travelAlongTheMovesDirection() {
        assertEquals(24.5, MoveMath.along(24.5, 0.3, 0), 1e-9);
        assertEquals(24.5, MoveMath.along(-24.5, 0.3, 180), 1e-9);
        assertEquals(12.0, MoveMath.along(0.1, 12.0, -90), 1e-9);   // right
        assertEquals(12.0, MoveMath.along(0.1, -12.0, 90), 1e-9);   // left
    }

    @Test public void odometryTravelSeenFromTheRobot() {
        // Facing away (heading 0): +y is forward, +x is right.
        double[] t = MoveMath.robotTravel(0, 0, 0, 2, 10);
        assertEquals(10, t[0], 1e-9);
        assertEquals(2, t[1], 1e-9);
        // Facing left (heading 90): -x is forward.
        t = MoveMath.robotTravel(0, 0, 90, -10, 0);
        assertEquals(10, t[0], 1e-9);
        assertEquals(0, t[1], 1e-9);
    }

    @Test public void stopsAreGoodOnlyWithASmallSlideAndNoStall() {
        assertTrue(MoveMath.stopsOk(0.5, false));
        assertFalse(MoveMath.stopsOk(0.6, false));
        assertFalse(MoveMath.stopsOk(0.1, true));
    }

    @Test public void aBiggerSlowDownMustStopClearlyBetter() {
        assertTrue(MoveMath.clearlyBetter(0.6, Double.NaN));
        assertTrue(MoveMath.clearlyBetter(0.6, Double.MAX_VALUE));
        assertTrue(MoveMath.clearlyBetter(0.5, 0.7));
        assertFalse(MoveMath.clearlyBetter(0.62, 0.7));   // only a little smaller: keep the smaller slowDownIn
    }

    @Test public void aTurnTraceCountsOvershootAndSwings() {
        MoveMath.TurnTrace t = new MoveMath.TurnTrace(1.0);
        for (double e : new double[] {90, 40, 5, -4, -2, 0.5, 3, 0.2}) t.add(e);
        assertEquals(4, t.overshootDeg, 1e-9);
        assertEquals(2, t.swings);   // to -4, back to +3
        // A clean turn: no overshoot, no swings.
        MoveMath.TurnTrace clean = new MoveMath.TurnTrace(1.0);
        for (double e : new double[] {-90, -30, -5, -0.5, 0.3}) clean.add(e);
        assertEquals(0, clean.swings);
        assertEquals(0.3, clean.overshootDeg, 1e-9);
    }

    @Test public void overshootRaisesKdFirstThenLowersKp() {
        // It settled, but went 6 deg past first.
        MoveMath.Suggestion s = MoveMath.suggestTurn(6, 1, 0.5, false, 1.0, 0.012, 0.003, 0, 1);
        assertEquals("turnKd", s.field);
        assertEquals(0.0039, s.value, 1e-9);
        // kD already half of kP: less kP
        s = MoveMath.suggestTurn(6, 1, 0.5, false, 1.0, 0.012, 0.006, 0, 1);
        assertEquals("turnKp", s.field);
        assertEquals(0.0096, s.value, 1e-9);
        // two swings count as overshoot even when small
        assertEquals("turnKd", MoveMath.suggestTurn(2, 2, 0.5, false, 1.0, 0.012, 0.003, 0, 1).field);
        // back and forth until it ran out of time: less swing too
        assertEquals("turnKd", MoveMath.suggestTurn(5, 3, 2.0, true, 4.0, 0.012, 0.003, 0, 1).field);
    }

    // The simulator showed it: a turn that ends 7 deg PAST its target and stays there. More kD does
    // nothing for that; a smallest turn power pushes it back.
    @Test public void stuckOffTargetRaisesTheMinPowerInStepsThenKp() {
        MoveMath.Suggestion s = MoveMath.suggestTurn(7.6, 1, 7.4, true, 4.0, 0.012, 0.003, 0, 1);
        assertEquals("turnMinPower", s.field);
        assertEquals(0.05, s.value, 1e-9);
        assertEquals(0.08, MoveMath.suggestTurn(0, 0, 2.5, false, 1.0, 0.012, 0.003, 0.05, 1).value, 1e-9);
        assertEquals(0.11, MoveMath.suggestTurn(0, 0, 2.5, false, 1.0, 0.012, 0.003, 0.08, 1).value, 1e-9);
        // all three tried, still 2.5 deg short: more push
        s = MoveMath.suggestTurn(0, 0, 2.5, true, 1.0, 0.012, 0.003, 0.11, 1);
        assertEquals("turnKp", s.field);
        assertEquals(0.0144, s.value, 1e-9);
    }

    // Also seen in the simulator: with turnMinPower 0.11 every turn ended 1.0 to 1.4 deg off and ran out of
    // time, rocking in and out of the 1 deg tolerance.
    @Test public void huntingJustOutsideTheToleranceWidensIt() {
        MoveMath.Suggestion s = MoveMath.suggestTurn(2.9, 1, 1.4, true, 4.0, 0.012, 0.003, 0.11, 1.0);
        assertEquals("turnToleranceDeg", s.field);
        assertEquals(1.5, s.value, 1e-9);
        // without a smallest turn power it is stuck, not hunting
        assertEquals("turnMinPower", MoveMath.suggestTurn(0, 0, 1.4, true, 4.0, 0.012, 0.003, 0, 1.0).field);
        // never wider than 3 deg
        assertEquals("turnKp", MoveMath.suggestTurn(0, 0, 5.0, true, 4.0, 0.012, 0.003, 0.11, 3.0).field);
    }

    @Test public void slowTurnsGetMoreKpAndGoodTurnsAreOk() {
        assertEquals("turnKp", MoveMath.suggestTurn(0, 0, 0.5, false, 2.5, 0.012, 0.003, 0, 1).field);
        MoveMath.Suggestion ok = MoveMath.suggestTurn(1, 1, 0.5, false, 1.2, 0.012, 0.003, 0, 1);
        assertTrue(ok.ok());
        assertNull(ok.field);
    }

    @Test public void moveAndTurnRules() {
        MoveMath.Suggestion s = MoveMath.suggestMoveTurn(3, 8, 0.6, 0.35);
        assertEquals("moveTurnScale", s.field);
        assertEquals(0.48, s.value, 1e-9);
        s = MoveMath.suggestMoveTurn(20, 0, 0.6, 0.35);
        assertEquals("moveTurnScale", s.field);
        assertEquals(0.75, s.value, 1e-9);
        s = MoveMath.suggestMoveTurn(20, 0, 1.0, 0.35);
        assertEquals("moveMaxTurnPower", s.field);
        assertEquals(0.45, s.value, 1e-9);
        assertTrue(MoveMath.suggestMoveTurn(20, 0, 1.0, 0.8).ok());
        assertTrue(MoveMath.suggestMoveTurn(4, 2, 0.6, 0.35).ok());
    }

    @Test public void stillWatchWaitsForAQuietWindow() {
        MoveMath.StillWatch w = new MoveMath.StillWatch(0.15, 1.0);
        double[] tol = {0.05};
        assertFalse(w.update(0.00, new double[] {10.0}, tol));
        assertFalse(w.update(0.05, new double[] {10.5}, tol));   // still rolling: the window starts again
        assertFalse(w.update(0.10, new double[] {10.52}, tol));
        assertFalse(w.update(0.15, new double[] {10.53}, tol));
        assertTrue(w.update(0.21, new double[] {10.53}, tol));   // quiet since 0.05
        // Out of time: true even while it moves.
        MoveMath.StillWatch late = new MoveMath.StillWatch(0.15, 1.0);
        late.update(0, new double[] {0}, tol);
        assertTrue(late.update(1.0, new double[] {5}, tol));
    }
}
