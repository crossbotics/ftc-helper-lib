package io.github.jasoncross.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.jasoncross.ftchelper.Fakes;
import io.github.jasoncross.ftchelper.core.SettingsFile;
import io.github.jasoncross.ftchelper.tuning.TuningReset;
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
            assertTrue(e.getMessage(), e.getMessage().contains("G1 LT is the library's \"slow (hold)\""));
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

    @Test public void aSuggestionWithNoFreeButtonIsNoted() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Lift.slide("lift"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            }
        };
        r.setup(new RobotTestKit());
        ButtonPlan plan = ControlMap.makePlan(r, false, true, (g1, g2) -> { });
        String notes = plan.notes().toString();
        assertTrue(notes, notes.contains("Launcher speed up: no button (G2 D-pad up is taken by Lift)"));
    }

    @Test public void standardButtonsCanBeSwitchedOff() {
        ButtonPlan plan = ControlMap.makePlan(robot, false, false, (g1, g2) -> g2.a.press(robot.claw::open).says("open"));
        assertEquals("G2 A: open", line(plan.help(), "G2 A"));
        assertEquals("", line(plan.help(), "G2 RT"));
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
}
