package io.github.jasoncross.ftchelper.sim;

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
}
