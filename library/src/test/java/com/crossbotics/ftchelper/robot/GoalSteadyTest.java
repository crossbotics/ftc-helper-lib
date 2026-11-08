package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.vision.TargetObservation;
import org.junit.Test;

/** waitForGoal's still check: in view and still for 1 s; a swing or a gap starts the count again. */
public class GoalSteadyTest {
    private static final long MS = 1_000_000L;

    private static TargetObservation at(double deg, double in) {
        return new TargetObservation(-1, "RED SCORING", deg, in, 0);
    }

    @Test public void stillForOneSecondIsSteady() {
        GoalSteady g = new GoalSteady(1, 2, 2);
        assertFalse(g.update(at(0, 60), 0));
        assertFalse(g.update(at(1, 61), 900 * MS));     // small moves are fine
        assertTrue(g.update(at(1.5, 61.5), 1000 * MS));
    }

    @Test public void aSwingStartsTheCountAgain() {
        GoalSteady g = new GoalSteady(1, 2, 2);
        g.update(at(0, 60), 0);
        assertFalse(g.update(at(5, 60), 800 * MS));     // swung 5 deg: a new count from here
        assertFalse(g.update(at(5, 60), 1500 * MS));
        assertTrue(g.update(at(5, 60), 1800 * MS));
    }

    @Test public void aDistanceJumpStartsTheCountAgain() {
        GoalSteady g = new GoalSteady(1, 2, 2);
        g.update(at(0, 60), 0);
        assertFalse(g.update(at(0, 66), 900 * MS));     // the HIVE tipped toward us
        assertFalse(g.update(at(0, 66), 1500 * MS));
        assertTrue(g.update(at(0, 66), 1900 * MS));
    }

    @Test public void aGapStartsTheCountAgain() {
        GoalSteady g = new GoalSteady(1, 2, 2);
        g.update(at(0, 60), 0);
        assertFalse(g.update(null, 500 * MS));
        assertTrue(g.everSeen());
        assertFalse(g.update(at(0, 60), 600 * MS));
        assertFalse(g.update(at(0, 60), 1500 * MS));
        assertTrue(g.update(at(0, 60), 1600 * MS));
    }

    @Test public void zeroSecondsIsSteadyAtOnce() {
        assertTrue(new GoalSteady(0, 2, 2).update(at(0, 60), 0));
        assertFalse(new GoalSteady(0, 2, 2).everSeen());
    }
}
