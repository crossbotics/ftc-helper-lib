package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.io.File;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ActionsAndControlsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final Fakes.Clock clock = new Fakes.Clock();
    private RobotModulesTest.LiftBot robot;
    private RobotTestKit kit;

    @Before public void setUp() {
        // The pretend lift moves with no time passing: read it fresh every time.
        com.crossbotics.ftchelper.safety.SafeMotor.freshReadingsForTests(true);
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        robot = new RobotModulesTest.LiftBot();
        kit = new RobotTestKit();
        robot.setup(kit);
        robot.runner.useClock(clock::get);
        // Home the lift: its bottom switch pressed once.
        kit.switches.get("lift_bottom")[0] = true;
        robot.all().update();
        kit.switches.get("lift_bottom")[0] = false;
    }

    @After public void back() {
        com.crossbotics.ftchelper.safety.SafeMotor.freshReadingsForTests(false);
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    // One loop of 20 ms: the actions and every part, then the pretend lift moves.
    private void loop() {
        clock.addSec(0.02);
        robot.all().update();
        kit.motors.get("lift").position += kit.motors.get("lift").power * 40;
    }

    private Action scoreHigh() {
        return Action.named("Score high")
                .then(() -> robot.lift.goTo("high"))
                .waitUntil("lift at the top", robot.lift::isAtTarget, 5.0)
                .then(robot.claw::open)
                .waitSeconds(0.3)
                .then(() -> robot.lift.goTo("down"));
    }

    // ----------------------------- actions -----------------------------

    @Test public void anActionRunsItsStepsInOrderWithoutBlocking() {
        Action a = scoreHigh();
        robot.run(a);
        assertTrue(a.isRunning());
        assertTrue(a.status(), a.status().startsWith("Score high: step 2 of 5"));
        for (int i = 0; i < 600 && a.isRunning(); i++) loop();
        assertFalse(a.isRunning());
        assertEquals("done", a.result());
        assertEquals(0.2, kit.servos.get("claw").position, 1e-9);
        assertEquals("down", robot.lift.part().getTargetName());
    }

    @Test public void theDriverWinsAManualMoveStopsTheAction() {
        Action a = scoreHigh();
        robot.run(a);
        loop();
        robot.lift.manual(0.5);       // the operator's stick
        loop();
        assertFalse(a.isRunning());
        assertEquals("stopped: Lift moved by hand", a.result());
    }

    @Test public void aStickAtRestDoesNotStopAnAction() {
        Action a = scoreHigh();
        robot.run(a);
        for (int i = 0; i < 5; i++) {
            robot.lift.manual(0);     // called every loop by the stick job
            loop();
        }
        assertTrue(a.isRunning());
    }

    @Test public void anotherActionTakingAPartStopsTheFirst() {
        Action a = scoreHigh();
        Action b = Action.named("Lift down").then(() -> robot.lift.goTo("down"));
        robot.run(a);
        loop();
        robot.run(b);
        assertFalse(a.isRunning());
        assertEquals("stopped: Lift taken by Lift down", a.result());
    }

    @Test public void aWaitThatTimesOutStopsTheActionAndSaysWhy() {
        Action a = Action.named("Wait for nothing").waitUntil("the impossible", () -> false, 1.0)
                .then(robot.claw::open);
        robot.run(a);
        for (int i = 0; i < 60; i++) loop();
        assertFalse(a.isRunning());
        assertEquals("timed out waiting for the impossible", a.result());
        assertTrue(Double.isNaN(kit.servos.get("claw").position));   // the claw never opened
    }

    @Test public void aCancelledActionStopsItsPartsSafely() {
        Action a = scoreHigh();
        robot.run(a);
        for (int i = 0; i < 5; i++) loop();      // the lift is on its way up
        robot.runner.toggle(a);                  // cancel
        double heldAt = robot.lift.position();
        for (int i = 0; i < 80; i++) loop();
        assertEquals("the lift holds where it was, it does not go on to the top", heldAt, robot.lift.position(), 30);
        assertEquals("hold", robot.lift.part().getTargetName());
    }

    @Test public void aTimedOutActionStopsItsPartsToo() {
        Action a = Action.named("Up, then wait for nothing")
                .then(() -> robot.lift.goTo("high"))
                .waitUntil("the impossible", () -> false, 0.1);
        robot.run(a);
        for (int i = 0; i < 10; i++) loop();
        assertFalse(a.isRunning());
        assertEquals("hold", robot.lift.part().getTargetName());
    }

    @Test public void aFinishedActionKeepsItsLastCommands() {
        Action a = Action.named("Up").then(() -> robot.lift.goTo("high"));
        robot.run(a);
        loop();
        assertEquals("done", a.result());
        assertEquals("high", robot.lift.part().getTargetName());
    }

    @Test public void theDriversStopCancelsTheAction() {
        Action a = scoreHigh();
        robot.run(a);
        loop();
        robot.lift.stop();
        assertFalse(a.isRunning());
        assertEquals("stopped: Lift moved by hand", a.result());
    }

    @Test public void pressingAgainCancels() {
        Action a = scoreHigh();
        robot.runner.toggle(a);
        assertTrue(a.isRunning());
        robot.runner.toggle(a);
        assertFalse(a.isRunning());
        assertEquals("cancelled", a.result());
    }

    @Test public void atStartActionsRunOnlyAfterStart() {
        final int[] ran = {0};
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                atStart(() -> Action.named("count").then(() -> ran[0]++));
            }
        };
        r.setup(new RobotTestKit());
        r.all().update();
        assertEquals(0, ran[0]);
        r.started();
        assertEquals(1, ran[0]);
    }

    // ----------------------------- the button map -----------------------------

    private static String line(List<String> help, String start) {
        for (String h : help) if (h.startsWith(start)) return h;
        return "";
    }

    @Test public void modulesSuggestTheStandardButtons() {
        ButtonPlan plan = ControlMap.makePlan(robot, false, true, (g1, g2) -> { });
        List<String> help = plan.help();
        assertEquals("G2 RT: in (hold) [Intake]", line(help, "G2 RT"));
        assertEquals("G2 A: down [Lift]", line(help, "G2 A"));
        assertEquals("G2 X: low [Lift]", line(help, "G2 X"));
        assertEquals("G2 Y: high [Lift]", line(help, "G2 Y"));
        assertEquals("G2 B: open / close [Claw]", line(help, "G2 B"));
        assertEquals("G1 Y: field-centric on/off [library]", line(help, "G1 Y"));
        assertTrue(line(help, "G1 free").contains("A"));
    }

    @Test public void controlsJavaReplacesASuggestion() {
        final Action a = scoreHigh();
        ButtonPlan plan = ControlMap.makePlan(robot, false, true, (g1, g2) -> g2.y.press(a));
        assertEquals("G2 Y: Score high", line(plan.help(), "G2 Y"));
    }

    @Test public void twoJobsOnOneButtonStopAtInit() {
        try {
            ControlMap.makePlan(robot, false, true, (g1, g2) -> {
                g2.a.press(robot.claw::open);
                g2.a.press(robot.claw::close);
            });
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("G2 A has two jobs"));
        }
    }

    @Test public void aDrivingButtonCannotBeTaken() {
        try {
            ControlMap.makePlan(robot, false, true, (g1, g2) -> g1.lt.press(robot.claw::open));
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("G1 LT is the library's \"slow (squeeze: the further, the slower)\""));
        }
    }

    @Test public void oneDriverPutsEverythingOnGamepadOne() {
        ButtonPlan plan = ControlMap.makePlan(robot, true, true, (g1, g2) -> { });
        List<String> help = plan.help();
        assertEquals("G1 RT: in (hold) [Intake]", line(help, "G1 RT"));
        assertEquals("G1 LT: out (hold) [Intake]", line(help, "G1 LT"));
        assertEquals("G1 left stick click: slow on/off [library]", line(help, "G1 left stick click"));
        assertEquals("G1 Y: high [Lift]", line(help, "G1 Y"));
    }

    @Test public void oneDriverCanPutSlowOnATrigger() {
        ButtonPlan plan = ControlMap.makePlan(robot, true, true, (g1, g2) -> g1.lt.slow());
        assertEquals("G1 LT: slow (squeeze: the further, the slower)", line(plan.help(), "G1 LT"));
        assertEquals(com.crossbotics.ftchelper.gamepad.ControllerBindings.Trigger.LT, plan.slowTrigger);
        ButtonPlan b = ControlMap.makePlan(robot, true, true, (g1, g2) -> g1.a.slow());
        assertEquals("G1 A: slow (hold)", line(b.help(), "G1 A"));
        assertFalse(b.slowHeld);
    }

    @Test public void slowIsForOneDriver() {
        try {
            ControlMap.makePlan(robot, false, true, (g1, g2) -> g2.lt.slow());
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("one driver"));
        }
    }

    @Test public void theGuideMapLeavesOutTheFreeButtons() {
        ButtonPlan plan = ControlMap.makePlan(robot, true, true, (g1, g2) -> g1.b.free());
        assertTrue(line(plan.help(), "G1 free").contains("B"));
        assertEquals("", line(plan.help(false), "G1 free"));
    }

    @Test public void aSuggestionWithNoFreeButtonIsNoted() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(RobotModulesTest.Numbers.class);   // a lift needs its min and max
                drive(Drive.tank("l", "r"));
                add(Lift.slide("lift"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            }
        };
        r.setup(new RobotTestKit());
        ButtonPlan plan = ControlMap.makePlan(r, false, true, (g1, g2) -> { });
        String notes = plan.notes().toString();
        assertTrue(notes, notes.contains("Launcher speed up, 4 a second: no button (G2 D-pad up is taken by Lift)"));
    }

    @Test public void standardButtonsCanBeSwitchedOff() {
        ButtonPlan plan = ControlMap.makePlan(robot, false, false, (g1, g2) -> g2.a.press(robot.claw::open).says("open"));
        assertEquals("G2 A: open", line(plan.help(), "G2 A"));
        assertEquals("", line(plan.help(), "G2 RT"));
    }

    @Test public void freeKeepsAButtonFreeEvenIfAModuleSuggestedIt() {
        ButtonPlan plan = ControlMap.makePlan(robot, false, true, (g1, g2) -> g2.b.free());
        assertEquals("", line(plan.help(), "G2 B:"));
        assertTrue(line(plan.help(), "G2 free"), line(plan.help(), "G2 free").contains("B"));
    }

    @Test public void aNonStaticOneDriverStopsWithAMessage() {
        try {
            ControlMap.readOneDriver(WrongOneDriver.class);
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("public static boolean oneDriver"));
        }
    }

    public abstract static class WrongOneDriver extends ControlMap<RobotModulesTest.LiftBot> {
        public boolean oneDriver = true;
    }

    @Test public void oneDriverIsReadFromTheTeamsStaticField() {
        assertTrue(ControlMap.readOneDriver(OneDriverControls.class));
        assertFalse(ControlMap.readOneDriver(TwoDriverControls.class));
    }

    /** Stand-ins for a team's Controls.java (never run here). */
    public abstract static class OneDriverControls extends ControlMap<RobotModulesTest.LiftBot> {
        public static boolean oneDriver = true;
    }

    public abstract static class TwoDriverControls extends ControlMap<RobotModulesTest.LiftBot> {
    }

    @Test public void aDoubleTapRunsItsOwnJob() {
        final int[] taps = {0}, doubles = {0};
        ButtonPlan plan = ControlMap.makePlan(robot, false, false, (g1, g2) ->
                g2.b.tapOrDoubleTap(() -> taps[0]++, () -> doubles[0]++));
        java.util.List<ButtonPlan.StickJob> sticks = new java.util.ArrayList<>();
        com.crossbotics.ftchelper.gamepad.ControllerBindings driver = new com.crossbotics.ftchelper.gamepad.ControllerBindings();
        com.crossbotics.ftchelper.gamepad.ControllerBindings operator = new com.crossbotics.ftchelper.gamepad.ControllerBindings();
        plan.compile(robot, driver, operator, sticks, () -> null, () -> null);
        com.qualcomm.robotcore.hardware.Gamepad g = new com.qualcomm.robotcore.hardware.Gamepad();
        java.util.function.LongSupplier before = Pad.clock;
        Pad.clock = clock::get;
        try {
            Runnable tap = () -> {
                g.b = true; operator.update(null, g);
                g.b = false; operator.update(null, g);
            };
            tap.run();                       // one tap
            assertEquals(1, taps[0]);
            clock.addSec(0.2);
            tap.run();                       // soon after: a double-tap
            assertEquals(1, taps[0]);
            assertEquals(1, doubles[0]);
            clock.addSec(1.0);
            tap.run();                       // much later: a tap again
            assertEquals(2, taps[0]);
        } finally {
            Pad.clock = before;
        }
    }

    @Test public void holdingFireKeepsOneShotWaitingAndATapStillFires() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            }
        };
        r.setup(new RobotTestKit());
        Launcher l = (Launcher) r.modules().get(0);
        l.fireWhileHeld();
        l.fireWhileHeld();                      // held: still one shot waiting (the wheel is not at speed)
        assertEquals(1, l.part().getShotsLeft());
        l.ceaseFire();                          // let go quickly: the tap's shot still goes
        assertEquals(1, l.part().getShotsLeft());
    }

    @Test public void pauseStopsEverythingOnBrakeAndResumeGoesOn() {
        com.crossbotics.ftchelper.FakeMotor intake = kit.motors.get("intake");
        robot.intake.in();
        robot.lift.goTo("high");
        Action a = Action.named("Wait").waitUntil("never", () -> false, 1.0);
        robot.run(a);
        loop();
        assertEquals(0.8, intake.power, 1e-9);
        robot.pauseAll();
        assertTrue(robot.isPaused());
        assertEquals(0, intake.power, 1e-9);
        assertEquals(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE, intake.zeroPower);
        assertEquals(0, kit.motors.get("lift").power, 1e-9);
        for (int i = 0; i < 100; i++) loop();          // 2 s paused: nothing moves, the wait does not time out
        assertEquals(0, intake.power, 1e-9);
        assertEquals(0, kit.motors.get("lift").power, 1e-9);
        robot.resume();
        loop();
        assertEquals(0.8, intake.power, 1e-9);
        assertEquals(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.FLOAT, intake.zeroPower);
        assertTrue("the lift goes on toward its target", kit.motors.get("lift").power > 0);
        assertTrue("the action is still waiting", a.isRunning());
    }

    @Test public void whilePausedButtonsDoNothing() {
        final int[] presses = {0};
        ButtonPlan plan = ControlMap.makePlan(robot, false, true, (g1, g2) -> g2.a.press(() -> presses[0]++));
        java.util.List<ButtonPlan.StickJob> sticks = new java.util.ArrayList<>();
        com.crossbotics.ftchelper.gamepad.ControllerBindings driver = new com.crossbotics.ftchelper.gamepad.ControllerBindings();
        com.crossbotics.ftchelper.gamepad.ControllerBindings operator = new com.crossbotics.ftchelper.gamepad.ControllerBindings();
        plan.compile(robot, driver, operator, sticks, () -> null, () -> null);
        com.qualcomm.robotcore.hardware.Gamepad g = new com.qualcomm.robotcore.hardware.Gamepad();
        Runnable press = () -> { g.a = true; operator.update(null, g); g.a = false; operator.update(null, g); };
        robot.pauseAll();
        press.run();
        assertEquals("paused: nothing", 0, presses[0]);
        robot.resume();
        press.run();
        assertEquals(1, presses[0]);
    }

    @Test public void pausedShotsAreFiredAfterResume() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            }
        };
        r.setup(new RobotTestKit());
        Launcher l = (Launcher) r.modules().get(0);
        l.fire(3);
        assertEquals(3, l.part().getShotsLeft());
        r.pauseAll();
        assertEquals(0, l.part().getShotsLeft());
        r.resume();
        assertEquals("the 3 shots wait again", 3, l.part().getShotsLeft());
    }

    @Test public void stopStopsEveryPartEvenIfOneFailsToPause() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Intake.motor("first"));
                add(Intake.motor("second"));
            }
        };
        RobotTestKit k = new RobotTestKit();
        r.setup(k);
        ((Intake) r.modules().get(0)).in();
        ((Intake) r.modules().get(1)).in();
        r.all().update();
        k.motors.get("first").failSetPower = true;     // unplugged: stopping it throws
        try {
            r.all().stop();
            fail("expected the motor's error");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("unplugged"));
        }
        assertEquals("the other intake stopped anyway", 0, k.motors.get("second").power, 1e-9);
    }

    @Test public void stopBrakesEverything() {
        robot.intake.in();
        loop();
        robot.all().stop();
        assertEquals(0, kit.motors.get("intake").power, 1e-9);
        assertEquals(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE, kit.motors.get("intake").zeroPower);
    }
}
