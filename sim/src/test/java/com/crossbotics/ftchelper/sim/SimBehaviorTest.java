package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** The simulator itself behaves like the FTC app and SDK where OpModes would notice. */
public class SimBehaviorTest {

    /** Ignores STOP on purpose: loops forever, setting a motor. */
    public static class Stuck extends LinearOpMode {
        @Override public void runOpMode() {
            DcMotorEx m = hardwareMap.get(DcMotorEx.class, "intake");
            waitForStart();
            while (true) {
                try {
                    m.setPower(0.5);
                } catch (RuntimeException ignored) {
                    // keep going whatever happens
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ignored) {
                    // swallow the interrupt, like bad code (and keep the CPU free for the other tests)
                }
            }
        }
    }

    /** Stops itself after START. */
    public static class StopsItself extends LinearOpMode {
        @Override public void runOpMode() {
            waitForStart();
            requestOpModeStop();
            while (opModeIsActive()) idle();
        }
    }

    /** Shows whether the SDK's "was pressed" check saw A. */
    public static class EdgeCheck extends LinearOpMode {
        @Override public void runOpMode() {
            waitForStart();
            int presses = 0;
            while (opModeIsActive()) {
                if (gamepad1.aWasPressed()) presses++;
                telemetry.addData("presses", presses);
                telemetry.update();
                sleep(5);   // a real loop takes a few milliseconds (hardware calls)
            }
        }
    }

    @Test public void aStuckOpModeCannotMoveAnythingAfterStop() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Stuck", "TeleOp", Stuck.class);
            s.init("Stuck").start();
            SimMotor intake = s.robot().motors.get("intake");
            assertTrue(SimRun.waitFor(() -> intake.userPower() > 0.4, 2));
            s.sim.ds.stop();
            assertTrue(s.error(), s.error().contains("did not stop"));
            assertEquals("its commands are blocked", 0, intake.userPower(), 1e-9);
            SimRun.pause(0.3);
            assertEquals(0, intake.userPower(), 1e-9);
        }
    }

    @Test public void anOpModeCanStopItself() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Stops itself", "TeleOp", StopsItself.class);
            s.init("Stops itself").start();
            assertTrue(SimRun.waitFor(() -> s.sim.ds.phase() == SimDriverStation.Phase.STOPPED, 4));
            assertEquals("", s.error());
        }
    }

    @Test public void buttonWasPressedChecksWork() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Edge check", "TeleOp", EdgeCheck.class);
            s.init("Edge check").start();
            Map<String, String> down = new HashMap<>();
            down.put("a", "1");
            s.sim.ds.pad1.apply(down, false);
            s.waitForLoops(3);
            String afterPress = "after the press: a=" + s.sim.ds.pad1.gamepad.a + ", screen " + s.screen();
            s.sim.ds.pad1.apply(new HashMap<String, String>(), false);
            s.waitForLoops(3);
            assertTrue(afterPress + "; at the end: " + s.screen(), s.screenHas("presses : 1"));
        }
    }

    @Test public void reversedServosScaleLikeTheSdk() throws Exception {
        SimRobot robot = SimRobot.load(java.nio.file.Paths.get("robots/starter.properties"));
        SimRobot.SimServo claw = robot.servos.get("claw");
        Servo s = claw.device;
        s.scaleRange(0.1, 0.6);
        s.setDirection(Servo.Direction.REVERSE);
        s.setPosition(0);
        assertEquals("reverse first, then scale", 0.6, claw.target(), 1e-9);
        s.setPosition(1);
        assertEquals(0.1, claw.target(), 1e-9);
        robot.resetConfig();
        s.setPosition(0);
        assertEquals("INIT resets the range and direction", 0.0, claw.target(), 1e-9);
    }

    @Test public void runToPositionWithoutATargetFailsLikeOnARobot() throws Exception {
        SimRobot robot = SimRobot.load(java.nio.file.Paths.get("robots/starter.properties"));
        DcMotorEx lift = robot.motors.get("lift").device;
        try {
            lift.setMode(com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_TO_POSITION);
            org.junit.Assert.fail("the SDK throws here");
        } catch (com.qualcomm.robotcore.exception.TargetPositionNotSetException expected) {
            // right
        }
        lift.setTargetPosition(100);
        lift.setMode(com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_TO_POSITION);
    }

    @Test public void aRunningFeederSendsAPieceEveryFewTenthsOfASecond() throws Exception {
        SimRobot robot = SimRobot.load(java.nio.file.Paths.get("robots/starter.properties"));
        robot.motors.get("flywheel").device.setPower(1);
        for (int i = 0; i < 200; i++) robot.step(0.01);            // 2 s: the wheel spins up
        robot.motors.get("feeder").device.setPower(1);
        for (int i = 0; i < 100; i++) robot.step(0.01);            // 1 s of feeding
        assertTrue("about 3-4 pieces in a second, not 1: " + robot.shots(), robot.shots() >= 3);
    }

    // A servo pusher (an indexer) sends one piece per push, however long it stays out. (A push plus a
    // slow return once lasted longer than 0.3 s and sent a second piece: "one tap fired two".)
    @Test public void aServoPusherSendsOnePiecePerPush() throws Exception {
        String base = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("robots/starter.properties")), "UTF-8");
        java.nio.file.Path f = java.nio.file.Files.createTempFile("pusher", ".properties");
        java.nio.file.Files.write(f, base.replace("feeder=feeder", "feeder=hood").getBytes("UTF-8"));
        SimRobot robot = SimRobot.load(f);
        robot.motors.get("flywheel").device.setPower(1);
        for (int i = 0; i < 200; i++) robot.step(0.01);            // 2 s: the wheel spins up
        com.qualcomm.robotcore.hardware.Servo pusher = (com.qualcomm.robotcore.hardware.Servo) robot.servos.get("hood").device;
        pusher.setPosition(0.9);                                   // out, and held out for 1 s
        for (int i = 0; i < 100; i++) robot.step(0.01);
        assertEquals("one push, one piece", 1, robot.shots());
        pusher.setPosition(0.1);                                   // back
        for (int i = 0; i < 100; i++) robot.step(0.01);
        pusher.setPosition(0.9);                                   // a second push
        for (int i = 0; i < 100; i++) robot.step(0.01);
        assertEquals("two pushes, two pieces", 2, robot.shots());
        java.nio.file.Files.delete(f);
    }

    // A feed wheel (stage=): a push sends a piece only if the feed wheel ran just before (it brought the
    // piece up to the arm). Launcher.feedWheel(...) writes it.
    @Test public void aPushNeedsTheFeedWheelToRunFirst() throws Exception {
        String base = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("robots/starter.properties")), "UTF-8");
        java.nio.file.Path f = java.nio.file.Files.createTempFile("stage", ".properties");
        java.nio.file.Files.write(f, base.replace("feeder=feeder", "feeder=hood, stage=feeder").getBytes("UTF-8"));
        SimRobot robot = SimRobot.load(f);
        robot.motors.get("flywheel").device.setPower(1);
        for (int i = 0; i < 200; i++) robot.step(0.01);            // 2 s: the wheel spins up
        com.qualcomm.robotcore.hardware.Servo arm = (com.qualcomm.robotcore.hardware.Servo) robot.servos.get("hood").device;
        arm.setPosition(0.9);                                      // a push with no staging
        for (int i = 0; i < 50; i++) robot.step(0.01);
        assertEquals("no piece at the arm: no shot", 0, robot.shots());
        arm.setPosition(0.1);
        robot.motors.get("feeder").device.setPower(1);             // the feed wheel stages a piece
        for (int i = 0; i < 50; i++) robot.step(0.01);
        arm.setPosition(0.9);
        for (int i = 0; i < 50; i++) robot.step(0.01);
        assertEquals("staged, then pushed: one shot", 1, robot.shots());
        java.nio.file.Files.delete(f);
    }

    @Test public void aWheelThatRunsOnlyAfterThePushDoesNotStageIt() throws Exception {
        String base = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("robots/starter.properties")), "UTF-8");
        java.nio.file.Path f = java.nio.file.Files.createTempFile("stage", ".properties");
        java.nio.file.Files.write(f, base.replace("feeder=feeder", "feeder=hood, stage=feeder, stop=claw, stopOpen=0.9").getBytes("UTF-8"));
        SimRobot robot = SimRobot.load(f);
        robot.motors.get("flywheel").device.setPower(1);
        for (int i = 0; i < 200; i++) robot.step(0.01);            // 2 s: the wheel spins up
        com.qualcomm.robotcore.hardware.Servo arm = (com.qualcomm.robotcore.hardware.Servo) robot.servos.get("hood").device;
        com.qualcomm.robotcore.hardware.Servo gate = (com.qualcomm.robotcore.hardware.Servo) robot.servos.get("claw").device;
        arm.setPosition(0.9);                                      // a push with no staging, the feed stop closed
        for (int i = 0; i < 50; i++) robot.step(0.01);
        robot.motors.get("feeder").device.setPower(1);             // the wheel runs only now, the arm still out
        for (int i = 0; i < 50; i++) robot.step(0.01);
        gate.setPosition(0.9);                                     // the feed stop opens
        for (int i = 0; i < 100; i++) robot.step(0.01);
        assertEquals("nothing was staged when the push began: no shot", 0, robot.shots());
        java.nio.file.Files.delete(f);
    }

    @Test public void aWheelThatStartsWhileTheArmMovesOutDoesNotStageIt() throws Exception {
        String base = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("robots/starter.properties")), "UTF-8");
        java.nio.file.Path f = java.nio.file.Files.createTempFile("stage", ".properties");
        java.nio.file.Files.write(f, base.replace("feeder=feeder", "feeder=hood, stage=feeder").getBytes("UTF-8"));
        SimRobot robot = SimRobot.load(f);
        robot.motors.get("flywheel").device.setPower(1);
        for (int i = 0; i < 200; i++) robot.step(0.01);            // 2 s: the wheel spins up
        com.qualcomm.robotcore.hardware.Servo arm = (com.qualcomm.robotcore.hardware.Servo) robot.servos.get("hood").device;
        arm.setPosition(0.9);                                      // the arm starts out, no staging yet
        robot.step(0.01);
        robot.motors.get("feeder").device.setPower(1);             // the wheel starts 0.01 s into the push
        for (int i = 0; i < 100; i++) robot.step(0.01);
        assertEquals("the wheel started after the push began: no shot", 0, robot.shots());
        java.nio.file.Files.delete(f);
    }

    @Test public void partsComeFromTheCatalog() throws Exception {
        SimParts.Motor m312 = SimParts.catalog().motor("gobilda-5203-312");
        assertEquals(537.7, m312.countsPerRev, 1e-9);
        assertEquals(312 * 537.7 / 60, m312.maxTicksPerSec(), 1e-6);
        SimRobot robot = SimRobot.load(java.nio.file.Paths.get("robots/starter.properties"));
        assertEquals("the robot file's lift uses the 312 RPM motor", m312.maxTicksPerSec(), robot.motors.get("lift").maxTicksPerSec, 1e-6);
        assertTrue(robot.servos.get("claw").partName.contains("Torque"));
        assertTrue("a servo part gives its own speed", robot.servos.get("claw").speed > 0);
    }

    /** An iterative OpMode (init/loop), like many teams write. */
    public static class Iterative extends com.qualcomm.robotcore.eventloop.opmode.OpMode {
        private DcMotorEx intake;
        private int loops = 0;
        @Override public void init() { intake = hardwareMap.get(DcMotorEx.class, "intake"); telemetry.addData("state", "init"); }
        @Override public void loop() {
            loops++;
            intake.setPower(gamepad1.a ? 1 : 0);
            telemetry.addData("loops", loops);
        }
    }

    @Test public void iterativeOpModesRun() throws Exception {
        try (SimRun s = new SimRun()) {
            s.sim.ds.register("Iterative", "TeleOp", Iterative.class);
            s.init("Iterative");
            assertTrue(s.screenHas("state : init"));
            s.start();
            SimMotor intake = s.robot().motors.get("intake");
            Map<String, String> a = new HashMap<>();
            a.put("a", "1");
            s.sim.ds.pad1.apply(a, false);
            assertTrue(SimRun.waitFor(() -> intake.userPower() > 0.9, 2));
            s.sim.ds.stop();
            assertEquals(0, intake.userPower(), 1e-9);
            assertEquals("", s.error());
        }
    }

    @Test public void scenarioTyposFailAndNotesAreIgnored() throws Exception {
        java.io.File f = java.io.File.createTempFile("typo", ".sim");
        java.nio.file.Files.write(f.toPath(), java.util.Arrays.asList(
                "init \"Motor Finder\"   # a note after a command",
                "expect screen hass \"nothing\"",
                "expect servo claw targett = 0",
                "expect motor lift power = 0 # stopped"));
        SimEngine sim = new SimEngine(SimRobot.load(java.nio.file.Paths.get("robots/starter.properties")), SimDriverStation.Scope.LIBRARY);
        sim.start();
        SimScenario s = new SimScenario(sim);
        try {
            s.run(f.toPath());
        } finally {
            sim.shutdown();
        }
        assertEquals("the two typos fail; the note is ignored: " + s.report(), 2, s.failures());
    }

    @Test public void aScenarioHoldStaysHeld() throws Exception {
        SimEngine sim = new SimEngine(SimRobot.load(java.nio.file.Paths.get("robots/starter.properties")), SimDriverStation.Scope.LIBRARY);
        sim.start();
        try {
            Map<String, String> a = new HashMap<>();
            a.put("a", "1");
            sim.ds.pad1.apply(a, false);
            SimRun.pause(3.0);                               // longer than the page's 2.5 s silence rule
            assertTrue("still held", sim.ds.pad1.gamepad.a);
        } finally {
            sim.shutdown();
        }
    }

    // A shape the code only guessed says TODO in the robot file: the size results must say they are guesses.
    @Test public void guessedShapesAreNamed() throws Exception {
        String base = new String(java.nio.file.Files.readAllBytes(new java.io.File("robots/starter.properties").toPath()), "UTF-8");
        java.io.File f = java.io.File.createTempFile("guessed", ".properties");
        f.deleteOnExit();
        java.nio.file.Files.write(f.toPath(), (base + "\nshape.guessed_box = box, at=0,0,4, size=2x2x2   # TODO measure: place and size\n").getBytes("UTF-8"));
        SimRobot r = SimRobot.load(f.toPath());
        org.junit.Assert.assertEquals(java.util.Collections.singletonList("guessed_box"), r.guessedShapes);
        org.junit.Assert.assertTrue("a measured robot file names none", SimRobot.load(java.nio.file.Paths.get("robots/starter.properties")).guessedShapes.isEmpty());
    }

    /** Runs until STOP. */
    public static class RunsUntilStopped extends com.qualcomm.robotcore.eventloop.opmode.LinearOpMode {
        @Override public void runOpMode() {
            waitForStart();
            while (opModeIsActive()) telemetry.update();
        }
    }

    // The autonomous stops when the match clock says: both read the game's rules. A game with a 1 s autonomous.
    @Test public void theAutonomousTimerReadsTheRules() throws Exception {
        String rules = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("fields/biobuzz-2026.rules.json")), "UTF-8");
        String shorter = rules.replaceFirst("\"autoSec\"\\s*:\\s*[0-9.]+", "\"autoSec\": 1");
        org.junit.Assert.assertNotEquals("the rules file has autoSec", rules, shorter);
        SimRobot r = SimRobot.load(java.nio.file.Paths.get("robots/starter.properties"));
        r.field.rules = new SimRules(shorter, "a test");
        SimEngine sim = new SimEngine(r);
        sim.start();
        try {
            sim.ds.register("Runs until stopped", "Autonomous", RunsUntilStopped.class);
            sim.ds.init("Runs until stopped");
            org.junit.Assert.assertTrue(SimRun.waitFor(() -> sim.ds.phase() == SimDriverStation.Phase.INIT, 3));
            sim.ds.start();
            org.junit.Assert.assertTrue(SimRun.waitFor(() -> sim.ds.phase() == SimDriverStation.Phase.RUNNING, 3));
            org.junit.Assert.assertTrue("it stopped at the rules' time, not 30 s", SimRun.waitFor(() -> sim.ds.phase() != SimDriverStation.Phase.RUNNING, 5));
        } finally {
            sim.shutdown();
        }
    }
}
