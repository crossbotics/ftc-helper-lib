package io.github.jasoncross.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The finders, run whole in the simulator with scripted gamepad presses: the safety rules the
 * reviews asked for, checked on the real OpMode code (not only its pieces).
 */
public class FinderSafetyTest {

    @Test public void motorFinderStopsForHelpAndWaitsForTheStickToBeLetGo() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Motor Finder").start();
            s.press(v -> s.g1().dpad_up = v);            // the list wraps from "arm" to "lift"
            s.press(v -> s.g1().a = v);                  // use it
            assertTrue(s.screenHas("Motor \"lift\""));
            SimMotor lift = s.robot().motors.get("lift");
            s.g1().right_stick_y = -1;                   // stick up: the lift rises
            assertTrue("the lift moves", SimRun.waitFor(() -> lift.encoder() > 100, 2));
            s.press(v -> s.g1().guide = v);              // help, with the stick still pushed
            assertTrue(SimRun.waitFor(() -> s.screenHas("help"), 1));
            assertEquals("help stops the motor", 0, lift.userPower(), 1e-9);
            s.press(v -> s.g1().guide = v);              // help closes, the stick is STILL pushed
            SimRun.pause(0.5);
            assertEquals("a stick held through help does not count", 0, lift.userPower(), 1e-9);
            assertTrue(s.screenHas("Let go"));
            s.g1().right_stick_y = 0;                    // let go...
            SimRun.pause(0.2);
            s.g1().right_stick_y = -1;                   // ...and push again: it moves
            assertTrue(SimRun.waitFor(() -> lift.userPower() > 0.1, 1));
            s.g1().right_stick_y = 0;
            assertEquals("", s.error());
        }
    }

    @Test public void flywheelFinderDoesNotStartOnTheChoosingPress() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Flywheel Finder").start();
            for (int i = 0; i < 4; i++) s.press(v -> s.g1().dpad_down = v);   // arm -> ... -> flywheel
            SimMotor fly = s.robot().motors.get("flywheel");
            s.g1().a = true;                             // choose it, and KEEP A down
            SimRun.pause(1.0);
            assertTrue(s.screenHas("Flywheel \"flywheel\""));
            assertEquals("the choosing press does not start the wheel", 0, fly.userPower(), 1e-9);
            s.g1().a = false;
            SimRun.pause(0.2);
            s.g1().a = true;                             // a NEW press: step 1, full power
            assertTrue(SimRun.waitFor(() -> fly.velocity() > 2000, 3));
            s.g1().a = false;
            assertTrue(SimRun.waitFor(() -> s.screenHas("Flywheel.create(hardwareMap, \"flywheel\")"), 1));
            assertEquals("", s.error());
        }
    }

    @Test public void servoFinderDoesNotMoveBeforeTheStartPointIsChosen() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Servo Finder").start();
            s.press(v -> s.g1().a = v);                  // "claw", first in the list
            SimRobot.SimServo claw = s.robot().servos.get("claw");
            double before = claw.target();
            for (int i = 0; i < 3; i++) s.press(v -> s.g1().dpad_right = v);   // start point 0.53, no move
            assertEquals("nothing moves before A", before, claw.target(), 1e-9);
            s.press(v -> s.g1().a = v);                  // move there
            assertEquals(0.53, claw.target(), 1e-6);
            assertEquals("", s.error());
        }
    }

    @Test public void driveTunerPushTestFindsTheRealWheelSize() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Drive Tuner").start();
            s.press(v -> s.g1().a = v);                  // the Push test
            assertTrue(s.screenHas("BY HAND"));
            s.robot().push(24, 0, 2.0);                  // roll it 48 in forward, by hand
            assertTrue(SimRun.waitFor(() -> !s.robot().pushing(), 10));
            SimRun.pause(0.2);
            s.press(v -> s.g1().b = v);                  // done
            // The robot file says the real wheel is 3.90 in (the code thinks 3.78).
            assertTrue(String.valueOf(s.screen()), SimRun.waitFor(() -> s.screenHas("c.wheelDiameterIn = "), 1));
            double found = Double.NaN;
            for (String line : s.screen()) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("wheelDiameterIn = ([0-9.]+)").matcher(line);
                if (m.find()) found = Double.parseDouble(m.group(1));
            }
            assertEquals("the push test finds the real wheel size", 3.90, found, 0.05);
            assertEquals("", s.error());
        }
    }

    @Test public void everyHelpPageFitsTheScreen() throws Exception {
        String[] finders = {"Servo Finder", "Motor Finder", "Flywheel Finder", "Drive Tuner"};
        try (SimRun s = new SimRun()) {
            for (String f : finders) {
                s.init(f);
                s.press(v -> s.g1().guide = v);          // help in INIT
                assertTrue(f, SimRun.waitFor(() -> s.screenHas("help"), 1));
                List<String> lines = s.screen();
                assertTrue(f + ": " + lines.size() + " lines", lines.size() <= 16);
                for (String line : lines) assertTrue(f + ": too wide: " + line, line.length() <= 56);
                s.sim.ds.stop();
            }
        }
    }

    @Test public void everyExampleStartsAndStopsCleanly() throws Exception {
        List<String> problems = new ArrayList<>();
        try (SimRun s = new SimRun()) {
            for (SimDriverStation.Entry e : new ArrayList<>(s.sim.ds.opModes)) {
                s.sim.ds.init(e.name);
                SimRun.pause(0.2);
                s.sim.ds.start();
                SimRun.pause(0.3);
                s.sim.ds.stop();
                String err = s.error();
                // Expected in the simulator: no camera, and autonomous refusing an untuned drive.
                boolean expected = err.isEmpty() || err.contains("Webcam") || err.contains("WebcamName")
                        || err.contains("Limelight") || err.contains("calibrationConfirmed")
                        // the tank-drive examples need a tank robot file (left_drive, right_drive)
                        || err.contains("\"left_drive\"");
                if (!expected) problems.add(e.name + ": " + err.split("\n")[0]);
                for (SimMotor m : s.robot().motors.values()) {
                    if (Math.abs(m.userPower()) > 1e-9) problems.add(e.name + ": motor " + m.name + " still on after STOP");
                }
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
        assertFalse(problems.size() > 0);
    }
}
