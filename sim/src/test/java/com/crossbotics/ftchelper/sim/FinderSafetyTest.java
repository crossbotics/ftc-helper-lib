package com.crossbotics.ftchelper.sim;

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
    static {
        CookbookSamples.fill();   // the cookbook writes MEASURE: sample numbers in their place
    }


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
            s.press(v -> s.g1().dpad_left = v);          // a smaller step: 0.05 -> 0.01
            for (int i = 0; i < 3; i++) s.press(v -> s.g1().dpad_up = v);   // start point 0.53, no move
            assertEquals("nothing moves before A", before, claw.target(), 1e-9);
            s.press(v -> s.g1().a = v);                  // move there
            assertEquals(0.53, claw.target(), 1e-6);
            // The smallest step, for an exact spot: 0.01 -> 0.005 -> 0.001 (a third press stays at 0.001).
            for (int i = 0; i < 3; i++) s.press(v -> s.g1().dpad_left = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Step") && s.screenHas("0.001"), 1));
            s.press(v -> s.g1().dpad_down = v);          // 0.529
            assertEquals(0.529, claw.target(), 1e-6);
            s.press(v -> s.g1().dpad_right = v);         // back to 0.005
            s.press(v -> s.g1().dpad_up = v);            // 0.534
            assertEquals(0.534, claw.target(), 1e-6);
            s.press(v -> s.g1().a = v);                  // save it as P1
            // The line for the older helpers, and the one for the robot in five files.
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas(".position(\"P1\", 0.534)"), 1));
            assertTrue(String.join("\n", s.screen()), s.screenHas("Tunables.Claw: P1 = 0.534"));
            assertEquals("", s.error());
        }
    }

    /** A Servo Finder that knows a two-servo part: "claw" with "wrist" as its mirrored second servo. */
    public static class PairFinder extends com.crossbotics.ftchelper.finders.ServoFinderOpMode {
        @Override protected java.util.Map<String, java.util.Map.Entry<String, Boolean>> pairedServos() {
            return java.util.Collections.singletonMap("claw", new java.util.AbstractMap.SimpleEntry<>("wrist", true));
        }
    }

    /** A Servo Finder for a robot whose "claw" has reversed() and part("gobilda-2000-torque") in Robot.java. */
    public static class ReversedFinder extends com.crossbotics.ftchelper.finders.ServoFinderOpMode {
        @Override protected java.util.Map<String, Boolean> servoDirections() { return java.util.Collections.singletonMap("claw", true); }
        @Override protected java.util.Map<String, String> servoParts() { return java.util.Collections.singletonMap("claw", "gobilda-2000-torque"); }
    }

    // reversed() in Robot.java: the finder turns the servo the same way, so its numbers fit Tunables.java.
    @Test public void servoFinderTurnsAReversedServoTheSameWayAsRobotJava() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Reversed Finder", "TeleOp", ReversedFinder.class);
            s.init("Reversed Finder").start();
            s.press(v -> s.g1().a = v);                  // "claw"
            s.press(v -> s.g1().dpad_left = v);          // step 0.01
            for (int i = 0; i < 3; i++) s.press(v -> s.g1().dpad_up = v);   // start point 0.53
            s.press(v -> s.g1().a = v);
            assertEquals("0.53 reversed is 0.47 at the servo", 0.47, s.robot().servos.get("claw").target(), 1e-6);
            // goBILDA says clockwise for a bigger number; reversed, it is counterclockwise.
            assertTrue(String.join("\n", s.screen()), s.screenHas("bigger = counterclockwise (seller, reversed)"));
            assertEquals("", s.error());
        }
    }

    // A two-servo part: the second servo moves with the first in the finder (mirrored), so they never fight.
    @Test public void servoFinderMovesTheSecondServoOfAPairToo() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Pair Finder", "TeleOp", PairFinder.class);
            s.init("Pair Finder").start();
            s.press(v -> s.g1().a = v);                  // "claw", first in the list
            SimRobot.SimServo claw = s.robot().servos.get("claw"), wrist = s.robot().servos.get("wrist");
            s.press(v -> s.g1().dpad_left = v);          // step 0.01
            for (int i = 0; i < 3; i++) s.press(v -> s.g1().dpad_up = v);   // start point 0.53
            s.press(v -> s.g1().a = v);                  // move there
            assertEquals(0.53, claw.target(), 1e-6);
            assertEquals("the second servo, mirrored", 0.47, wrist.target(), 1e-6);
            assertTrue(String.join("\n", s.screen()), s.screenHas("wrist (mirrored)"));
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

    // The Hardware Check has a help page like the finders: GUIDE opens it, and the motor stops while it is open.
    @Test public void hardwareCheckHasAHelpPageAndStopsForIt() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Hardware Check").start();
            assertTrue(String.join("\n", s.screen()), s.screenHas("GUIDE (or hold START 1 s) = help"));
            String first = null;
            for (String l : s.screen()) if (l.startsWith("> ")) first = l;
            assertTrue(String.join("\n", s.screen()), first != null && first.contains("(motor)"));
            String name = first.substring(2, first.indexOf("  ("));
            SimMotor m = s.robot().motors.get(name);
            s.g1().right_stick_y = -1;
            assertTrue("it runs", SimRun.waitFor(() -> m.userPower() > 0.4, 1));
            s.press(v -> s.g1().guide = v);
            assertTrue(SimRun.waitFor(() -> s.screenHas("Hardware Check help"), 1));
            assertEquals("help stops the motor", 0, m.userPower(), 1e-9);
            s.g1().right_stick_y = 0;
            s.press(v -> s.g1().guide = v);
            assertTrue(SimRun.waitFor(() -> s.screenHas("Devices"), 1));
            assertEquals("", s.error());
        }
    }

    // A servo cannot report where it is: before the first move, the Hardware Check only changes the start
    // point (nothing is sent, not even 0.5). A moves the servo there.
    @Test public void hardwareCheckDoesNotMoveAServoBeforeTheStartPointIsConfirmed() throws Exception {
        try (SimRun s = new SimRun()) {
            s.init("Hardware Check").start();
            String chosen = null;
            for (int i = 0; i < 40 && chosen == null; i++) {
                for (String l : s.screen()) if (l.startsWith("> ") && l.contains("(servo)")) chosen = l;
                if (chosen == null) s.press(v -> s.g1().dpad_down = v);
            }
            assertTrue(String.join("\n", s.screen()), chosen != null);
            SimRobot.SimServo servo = s.robot().servos.get(chosen.substring(2, chosen.indexOf("  (")));
            double before = servo.target();
            s.press(v -> s.g1().dpad_right = v);         // the first press: start point 0.51
            assertTrue("the first press sends nothing", Double.isNaN(servo.commanded()));
            s.press(v -> s.g1().right_bumper = v);       // 0.61
            s.press(v -> s.g1().x = v);                  // 0.50
            s.press(v -> s.g1().dpad_left = v);          // 0.49
            assertTrue("nothing is sent before A", Double.isNaN(servo.commanded()));
            assertEquals("nothing moves before A", before, servo.target(), 1e-9);
            assertTrue(String.join("\n", s.screen()), s.screenHas("Start point") && s.screenHas("0.49"));
            s.press(v -> s.g1().a = v);                  // move there
            assertEquals(0.49, servo.commanded(), 1e-6);
            s.press(v -> s.g1().dpad_right = v);         // a normal nudge now
            assertEquals(0.50, servo.commanded(), 1e-6);
            assertEquals("", s.error());
        }
    }

    @Test public void everyHelpPageFitsTheScreen() throws Exception {
        String[] finders = {"Servo Finder", "Motor Finder", "Flywheel Finder", "Drive Tuner", "Hardware Check"};
        try (SimRun s = new SimRun()) {
            for (String f : finders) {
                s.init(f);
                s.press(v -> s.g1().guide = v);          // help in INIT
                assertTrue(f, SimRun.waitFor(() -> s.screenHas("help"), 1));
                List<String> lines = s.screen();
                assertTrue(f + ": " + lines.size() + " lines", lines.size() <= 16);
                // 54 characters: the page's screen width, close to a Driver Hub's in landscape.
                for (String line : lines) assertTrue(f + ": too wide: " + line, line.length() <= 54);
                s.sim.ds.stop();
            }
        }
    }

    // OpModes that cannot run here on purpose, and the reason that must show. Every other one must
    // run with no error at all.
    private static final java.util.Map<String, String> EXPECTED = new java.util.HashMap<>();
    static {
        EXPECTED.put("Tutorial 05: Tank Drive", "\"left_drive\"");        // needs a tank robot file
        EXPECTED.put("Example: Helper Tank TeleOp", "\"left_drive\"");
        EXPECTED.put("Tutorial 18: Vision Aim", "Webcam");                 // no camera in the simulator
        EXPECTED.put("Shot Tuner", "Webcam");
        EXPECTED.put("Aim Tuner", "Webcam");
        // The Robot.java examples are other robots (SimFromRobotTest runs them on their own files).
        EXPECTED.put("FlywheelBot TeleOp", "\"left_drive\"");
        EXPECTED.put("FlywheelBot Shoot and Leave", "\"left_drive\"");
        EXPECTED.put("TurretBot TeleOp", "\"turret\"");
        EXPECTED.put("TurretBot Shoot from Start", "\"turret\"");
        EXPECTED.put("TurretBot Shot Tuner", "\"turret\"");
        EXPECTED.put("V2 Bot TeleOp", "\"pinpoint\"");
        EXPECTED.put("V2 Bot Shoot and Stow", "\"pinpoint\"");
        EXPECTED.put("goBILDA Bot TeleOp", "\"left_drive\"");
        EXPECTED.put("goBILDA Bot Launch and Back Up", "\"left_drive\"");
        EXPECTED.put("goBILDA Bot Auto Cookbook", "\"left_drive\"");
        EXPECTED.put("goBILDA Strafer Bot TeleOp", "\"left_front_drive\"");
        EXPECTED.put("goBILDA Strafer Bot Launch and Leave", "\"left_front_drive\"");
        EXPECTED.put("REV Bot TeleOp", "\"leftDrive\"");
        EXPECTED.put("REV Bot Shoot and Leave", "\"leftDrive\"");
        EXPECTED.put("AndyMark Bot TeleOp", "\"left_drive\"");
        EXPECTED.put("AndyMark Bot Throw and Leave", "\"left_drive\"");
        EXPECTED.put("Studica Bot TeleOp", "\"leftDrive\"");
        EXPECTED.put("Studica Bot Shoot and Leave", "\"leftDrive\"");
        EXPECTED.put("Odometry Bot TeleOp", "\"pinpoint\"");
        EXPECTED.put("Odometry Bot To the Goal", "\"pinpoint\"");
        EXPECTED.put("Odometry Bot Park Around", "\"pinpoint\"");
        EXPECTED.put("Odometry Bot Odometry Tuner", "\"pinpoint\"");
        EXPECTED.put("Pedro Bot TeleOp", "\"pinpoint\"");
        EXPECTED.put("Pedro Bot Curve to the Goal", "\"pinpoint\"");
        // The cookbook's robot has a Pinpoint (CookbookTest runs its files on their own robot file).
        EXPECTED.put("Cookbook TeleOp", "\"pinpoint\"");
        EXPECTED.put("Cookbook Leave the Start", "\"pinpoint\"");
        EXPECTED.put("Near: Preload + Park", "\"pinpoint\"");
        EXPECTED.put("Near: Preload + Cycle + Park", "\"pinpoint\"");
        EXPECTED.put("Choose in INIT", "\"pinpoint\"");
        EXPECTED.put("Cookbook Odometry Tuner", "\"pinpoint\"");
        EXPECTED.put("Cookbook Shot Tuner", "\"pinpoint\"");
        // The cookbook's Piece Tuner uses the game-pieces recipe robot (CookbookTest runs it on its own file).
        EXPECTED.put("Cookbook Piece Tuner", "\"indexer\"");
        // The auto builder's Built Auto with no plan sent: it says where to make one.
        EXPECTED.put(BuiltAuto.NAME, "auto builder");
    }

    // The reason is on the message's first line, in the words of the library's or the SDK's own check: not a
    // crash whose text happens to hold the same word. A reason in quotes is a device: the SDK's "No X named".
    static boolean isExpected(String err, String reason) {
        String first = err.split("\n")[0];
        boolean ownCheck = first.startsWith("java.lang.IllegalArgumentException:") || first.startsWith("java.lang.IllegalStateException:")
                || !first.matches("^[\\w.$]+(Exception|Error)\\b.*");
        if (!ownCheck || !first.contains(reason)) return false;
        return !reason.startsWith("\"") || first.contains("named " + reason);
    }

    @Test public void aCrashWithTheRightWordIsNotExpected() {
        assertTrue(isExpected("java.lang.IllegalArgumentException: No DcMotorEx named \"turret\" in the active robot configuration.", "\"turret\""));
        assertTrue(isExpected("java.lang.IllegalStateException: AutoDriveConfig.calibrationConfirmed is false.", "calibrationConfirmed"));
        assertFalse(isExpected("java.lang.NullPointerException: \"turret\" is null", "\"turret\""));
        assertFalse(isExpected("java.lang.IllegalStateException: a bad \"turret\" value", "\"turret\""));
        assertFalse(isExpected("It crashed\n  Webcam", "Webcam"));
    }

    @Test public void everyExampleStartsAndStopsCleanly() throws Exception {
        List<String> problems = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        try (SimRun s = new SimRun()) {
            for (SimDriverStation.Entry e : new ArrayList<>(s.sim.ds.opModes)) {
                s.init(e.name);
                s.start();
                // The sticks and a trigger held, so a TeleOp really has its motors on at STOP.
                s.g1().left_stick_y = -0.5f;
                s.g1().right_stick_x = 0.3f;
                s.g1().right_trigger = 0.8f;
                SimRun.pause(0.1);
                s.sim.ds.stop();
                s.g1().left_stick_y = 0;
                s.g1().right_stick_x = 0;
                s.g1().right_trigger = 0;
                String err = s.error();
                String expected = EXPECTED.get(e.name);
                // An autonomous refusing an untuned drivetrain is right (calibrationConfirmed is false).
                boolean ok = err.isEmpty() || (expected != null && isExpected(err, expected))
                        || (e.kind.equals("Autonomous") && isExpected(err, "calibrationConfirmed"));
                if (!ok) problems.add(e.name + ": " + err.split("\n")[0]);
                if (!err.isEmpty()) seen.add(e.name + " -> " + err.split("\n")[0]);
                // What the OpMode left on itself: after stop() the simulator has switched everything off.
                for (String on : s.sim.ds.leftOnAtStop()) problems.add(e.name + ": motor " + on + " still on after STOP");
            }
        }
        System.out.println("Expected errors seen:\n" + String.join("\n", seen));
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    // A distance sensor can wait up to 200 ms for a measurement: the Hardware Check's screen must not.
    @Test public void aSlowDistanceSensorDoesNotStopTheHardwareCheck() throws Exception {
        String base = new String(java.nio.file.Files.readAllBytes(new java.io.File("robots/starter.properties").toPath()), "UTF-8");
        java.io.File f = java.io.File.createTempFile("slow-distance", ".properties");
        f.deleteOnExit();
        java.nio.file.Files.write(f.toPath(), (base + "\ndistance.zzz_slow = at=intake, acrossMm=150\n").getBytes("UTF-8"));
        try (SimRun s = new SimRun(f.getPath())) {
            s.robot().pieceSensors.get("zzz_slow").readDelayMs = 400;
            s.init("Hardware Check").start();
            for (int i = 0; i < 30 && !s.screenHas("> zzz_slow"); i++) s.press(v -> s.g1().dpad_down = v);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas("Distance"), 3));
            int before = s.sim.ds.screen.updates();
            SimRun.pause(0.4);
            int shown = s.sim.ds.screen.updates() - before;
            assertTrue("screen updates in 0.4 s with a 400 ms sensor: " + shown, shown > 5);
            assertTrue(String.join("\n", s.screen()), SimRun.waitFor(() -> s.screenHas(" in"), 3));
            assertEquals("", s.error());
        }
    }

    /** Sets a motor going and ends without stopping it. */
    public static class LeavesAMotorOn extends com.qualcomm.robotcore.eventloop.opmode.LinearOpMode {
        @Override public void runOpMode() {
            com.qualcomm.robotcore.hardware.DcMotor m = hardwareMap.get(com.qualcomm.robotcore.hardware.DcMotor.class, "intake");
            waitForStart();
            m.setPower(0.5);
            while (opModeIsActive()) telemetry.update();
        }
    }

    // The check above must be able to fail.
    @Test public void aMotorLeftOnIsSeen() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Leaves a motor on", "TeleOp", LeavesAMotorOn.class);
            s.init("Leaves a motor on").start();
            assertTrue(SimRun.waitFor(() -> s.robot().motors.get("intake").userPower() > 0.4, 2));
            s.sim.ds.stop();
            assertTrue(s.sim.ds.leftOnAtStop().toString(), s.sim.ds.leftOnAtStop().contains("intake"));
            assertEquals("the simulator still stops it", 0, s.robot().motors.get("intake").userPower(), 1e-9);
        }
    }
}
