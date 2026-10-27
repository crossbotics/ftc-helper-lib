package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * On the page, INIT of an autonomous is a new match: every game piece back where the match starts them (and
 * the preload), and the robot on its start spot. A TeleOp's INIT keeps where things are.
 */
public class SimAutoInitResetTest {

    @Test public void anAutonomousInitResetsTheFieldAndTheRobot() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.freshAutoInit = true;
            int staged = s.robot().pieces.drawn().size();
            s.robot().pieces.place("POLLEN", 0, 0, 0);            // an extra piece left from the last run
            s.robot().setPose(20, -20, 45);                         // and the robot where the last run ended
            assertEquals(staged + 1, s.robot().pieces.drawn().size());
            s.init("Score and Park");
            assertTrue("the field as the match starts it",
                    SimRun.waitFor(() -> s.robot().pieces.drawn().size() == staged, 3));
            assertTrue("the robot moved off where the last run ended", SimRun.waitFor(() -> Math.abs(s.robot().x - 20) > 1, 3));
            s.sim.ds.stop();
        }
    }

    @Test public void aTeleOpInitKeepsTheField() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.freshAutoInit = true;
            int staged = s.robot().pieces.drawn().size();
            s.robot().pieces.place("POLLEN", 0, 0, 0);
            s.init("Main TeleOp");
            SimRun.pause(0.5);
            assertEquals("the extra piece stays", staged + 1, s.robot().pieces.drawn().size());
            s.sim.ds.stop();
        }
    }
}
