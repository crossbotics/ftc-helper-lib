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
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Press ▶"), 1));
            s.press(v -> s.g1().back = v);               // back to the menu, on the same tool
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("> Servo Finder"), 1));
            s.press(v -> s.g1().a = v);                  // open it again
            assertTrue(SimRun.waitFor(() -> s.screenHas("Press ▶"), 1));
            s.start();
            // The Servo Finder itself now: choose "claw", move it to a start point.
            s.press(v -> s.g1().a = v);
            SimRobot.SimServo claw = s.robot().servos.get("claw");
            s.press(v -> s.g1().dpad_left = v);          // step 0.01
            for (int i = 0; i < 3; i++) s.press(v -> s.g1().dpad_up = v);   // start point 0.53
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
            for (String tool : new String[] {"Hardware Check", "Motor Direction Check", "Flywheel Finder", "Drive Tuner", "Shot Tuner"}) {
                assertTrue(tool + "\n" + screen, s.screenHas(tool));
            }
            assertFalse("no lift or arm on this robot\n" + screen, s.screenHas("Motor Finder"));
            assertEquals("", s.error());
        }
    }

    // The Motor Direction Check: one drive motor runs while its button is held, set up as Robot.java says.
    @Test public void theMotorDirectionCheckSpinsOneMotorWhileHeld() throws Exception {
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>singletonList(
                com.crossbotics.ftchelper.examples.turretbot.Robot.class));
        try (SimRun s = new SimRun()) {
            s.init(TuneAndCheckOpMode.NAME);
            // The menu starts on the last tool opened (and wraps around): step down until it is chosen.
            for (int i = 0; i < 10 && !s.screenHas("> Motor Direction Check"); i++) s.press(v -> s.g1().dpad_down = v);
            assertTrue(String.join("\n", s.screen()), s.screenHas("> Motor Direction Check"));
            s.press(v -> s.g1().a = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Wheels off the ground"), 1));
            s.start();
            SimMotor fl = s.robot().motors.get("front_left"), fr = s.robot().motors.get("front_right");
            s.g1().x = true;                              // hold X: front-left
            assertTrue("front-left runs", SimRun.waitFor(() -> Math.abs(fl.userPower()) > 0.25, 1));
            assertEquals("only that one", 0, fr.userPower(), 1e-9);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("RUNNING"), 1));
            s.g1().y = true;                              // two held: none runs (one wheel at a time)
            assertTrue("two buttons: none runs", SimRun.waitFor(() -> fl.userPower() == 0 && fr.userPower() == 0, 1));
            s.g1().y = false;
            s.g1().x = false;
            assertTrue("it stops when let go", SimRun.waitFor(() -> fl.userPower() == 0, 1));
            assertEquals("", s.error());
        }
    }

    // The Drive Tuner's moves run inside the menu too. They once ended at once with STOPPED: the move
    // asked the finder's own opModeIsActive(), which is false when "Tune and check" is the OpMode running.
    @Test public void theDriveTunersDriveTestRunsFromTheMenu() throws Exception {
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>singletonList(
                com.crossbotics.ftchelper.examples.turretbot.Robot.class));
        try (SimRun s = new SimRun()) {
            s.init(TuneAndCheckOpMode.NAME);
            for (int i = 0; i < 10 && !s.screenHas("> Drive Tuner"); i++) s.press(v -> s.g1().dpad_down = v);
            assertTrue(String.join("\n", s.screen()), s.screenHas("> Drive Tuner"));
            s.press(v -> s.g1().a = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Drive tuner"), 1));
            s.start();
            s.press(v -> s.g1().dpad_down = v);           // Push -> Drive forward
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("> Drive forward"), 1));
            s.press(v -> s.g1().a = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Drive: done"), 15));
            assertFalse(String.join("\n", s.screen()), s.screenHas("STOPPED"));
            assertEquals("", s.error());
        }
    }

    // A PS controller: the menu and the finder name its buttons (× to open, ○ to stop), and ▶ is the Driver Hub's start.
    @Test public void aPsControllerSeesItsOwnButtonNames() throws Exception {
        TuneAndCheckOpMode.useRobots(Collections.<Class<? extends RobotDefinition>>emptyList());
        try (SimRun s = new SimRun()) {
            s.sim.ds.pad1.kind = com.qualcomm.robotcore.hardware.Gamepad.Type.SONY_PS4;
            s.g1().type = s.sim.ds.pad1.kind;
            s.init(TuneAndCheckOpMode.NAME);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("× = open it. Then ▶."), 1));
            assertTrue(String.join("\n", s.screen()), s.screenHas("SHARE = back to this menu"));
            s.press(v -> s.g1().dpad_down = v);          // Hardware Check -> Servo Finder
            s.press(v -> s.g1().a = v);                  // × opens it
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("PS (center button), or hold OPTIONS 1 s = help."), 1));
            assertFalse(String.join("\n", s.screen()), s.screenHas("{"));
            assertEquals("", s.error());
        } finally {
            com.crossbotics.ftchelper.gamepad.ButtonNames.watch(null, null);
        }
    }
}
