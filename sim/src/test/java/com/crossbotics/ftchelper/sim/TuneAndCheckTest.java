package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.finders.TuneAndCheckOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/**
 * "Tune and check", the library's own OpMode with every check and finder: its menu fits the robot,
 * a tool opens in INIT and runs after START, BACK goes back to the menu, and START with no tool
 * opened moves nothing.
 */
public class TuneAndCheckTest {
    static {
        CookbookSamples.fill();   // the cookbook writes MEASURE: sample numbers in their place
    }

    @After public void findRobotsAgain() {
        TuneAndCheckOpMode.useRobots(null);
    }

    @Test public void withNoRobotTheMenuHasTheToolsThatNeedNone() throws Exception {
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>emptyList());
        try (SimRun s = new SimRun()) {
            s.init(TuneAndCheckOpMode.NAME);
            String screen = String.join("\n", s.screen());
            assertTrue(screen, s.screenHas("No Robot.java found"));
            for (String tool : new String[] {"Hardware Check", "Servo Finder", "Motor Finder", "Flywheel Finder"}) {
                assertTrue(tool + "\n" + screen, s.screenHas(tool));
            }
            assertFalse("the Drive Tuner needs a robot\n" + screen, s.screenHas("Drive Tuner"));
            assertEquals("", s.error());
        }
    }

    @Test public void aToolOpensInInitBackReturnsAndStartRunsIt() throws Exception {
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>emptyList());
        try (SimRun s = new SimRun()) {
            s.init(TuneAndCheckOpMode.NAME);
            s.press(v -> s.g1().dpad_down = v);          // Hardware Check -> Servo Finder
            assertTrue(String.join("\n", s.screen()), s.screenHas("> Servo Finder"));
            s.press(v -> s.g1().a = v);                  // open it: its own INIT screen
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Press START"), 1));
            s.press(v -> s.g1().back = v);               // back to the menu, on the same tool
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("> Servo Finder"), 1));
            s.press(v -> s.g1().a = v);                  // open it again
            assertTrue(SimRun.waitFor(() -> s.screenHas("Press START"), 1));
            s.start();
            // The Servo Finder itself now: choose "claw", move it to a start point.
            s.press(v -> s.g1().a = v);
            SimRobot.SimServo claw = s.robot().servos.get("claw");
            for (int i = 0; i < 3; i++) s.press(v -> s.g1().dpad_right = v);
            s.press(v -> s.g1().a = v);
            assertEquals(0.53, claw.target(), 1e-6);
            assertEquals("", s.error());
        }
    }

    @Test public void startWithNoToolOpenedMovesNothing() throws Exception {
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>emptyList());
        try (SimRun s = new SimRun()) {
            s.init(TuneAndCheckOpMode.NAME).start();
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("No tool was opened"), 1));
            s.press(v -> s.g1().a = v);
            for (SimMotor m : s.robot().motors.values()) assertEquals(0, m.userPower(), 1e-9);
            assertEquals("", s.error());
        }
    }

    @Test public void theMenuFitsTheRobot() throws Exception {
        List<Class<? extends RobotDefinition>> robots = Collections.<Class<? extends RobotDefinition>>singletonList(
                com.crossbotics.ftchelper.examples.turretbot.Robot.class);
        TuneAndCheckOpMode.useRobots(robots);
        try (SimRun s = new SimRun()) {
            s.init(TuneAndCheckOpMode.NAME);
            String screen = String.join("\n", s.screen());
            assertTrue(screen, s.screenHas("Robot: Robot"));
            for (String tool : new String[] {"Hardware Check", "Flywheel Finder", "Drive Tuner", "Shot Tuner"}) {
                assertTrue(tool + "\n" + screen, s.screenHas(tool));
            }
            assertFalse("no lift or arm on this robot\n" + screen, s.screenHas("Motor Finder"));
            assertEquals("", s.error());
        }
    }
}
