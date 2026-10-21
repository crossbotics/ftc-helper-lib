package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.tuning.TuningReset;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.ObjIntConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Gestures (tap, hold, double-tap, tap then hold, N taps, held for), pairs and buzzes in Controls.java. */
public class GesturesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final Fakes.Clock clock = new Fakes.Clock();
    private final List<String> ran = new ArrayList<>();
    private final List<Integer> buzzes = new ArrayList<>();
    private LongSupplier clockBefore;
    private ObjIntConsumer<Gamepad> buzzerBefore;

    // The robot, its plan and the two pretend gamepads.
    private RobotModulesTest.LiftBot robot;
    private ButtonPlan plan;
    private ControllerBindings driver, operator;
    private final Gamepad g1 = new Gamepad(), g2 = new Gamepad();

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        clockBefore = Pad.clock;
        buzzerBefore = ButtonPlan.buzzer;
        Pad.clock = clock::get;
        ButtonPlan.buzzer = (g, n) -> buzzes.add(n);
        robot = new RobotModulesTest.LiftBot();
        robot.setup(new RobotTestKit());
    }

    @After public void back() {
        Pad.clock = clockBefore;
        ButtonPlan.buzzer = buzzerBefore;
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    private Runnable log(String what) {
        return () -> ran.add(what);
    }

    private void make(ControlMap.TeamButtons team) {
        plan = ControlMap.makePlan(robot, false, false, team);
        driver = new ControllerBindings();
        operator = new ControllerBindings();
        plan.compile(robot, driver, operator, new ArrayList<>(), () -> g1, () -> g2);
        loop(0);   // the first loop (START): nothing held
    }

    // One loop after `sec` seconds: buttons, then the watched jobs (as in ControlMap).
    private void loop(double sec) {
        clock.addSec(sec);
        driver.update(g1, g2);
        operator.update(g1, g2);
        plan.loop(robot.isPaused(), false);
    }

    // Holds A on gamepad 2 for `sec` seconds (loops of 20 ms), then lets go.
    private void pressA(double sec) {
        g2.a = true;
        loop(0.02);
        for (double t = 0.02; t < sec - 1e-9; t += 0.02) loop(0.02);
        g2.a = false;
        loop(0.02);
    }

    private void waitSec(double sec) {
        for (double t = 0; t < sec - 1e-9; t += 0.02) loop(0.02);
    }

    @Test public void aTapAloneRunsAtOnceWhenPressed() {
        make((p1, p2) -> p2.a.tap(log("tap")));
        g2.a = true;
        loop(0.02);
        assertEquals(1, ran.size());
        g2.a = false;
        loop(0.02);
        assertEquals(1, ran.size());
    }

    @Test public void tapAndHoldOnOneButton() {
        make((p1, p2) -> p2.a.tap(log("tap")).hold(log("held"), log("let go")));
        pressA(0.1);                                       // quick: a tap, when let go
        assertEquals(java.util.Arrays.asList("tap"), ran);
        ran.clear();
        pressA(0.6);                                       // long: a hold, no tap
        assertFalse(ran.contains("tap"));
        assertTrue(ran.contains("held"));
        assertEquals("let go", ran.get(ran.size() - 1));
        // The hold starts after Tunables.Buttons.holdSec (0.3 s): about 0.3 s of the 0.6 s.
        long held = ran.stream().filter("held"::equals).count();
        assertTrue("held " + held, held >= 13 && held <= 17);
    }

    @Test public void aHoldGivenFirstJoinsTheGestures() {
        make((p1, p2) -> p2.a.hold(log("held")).says("keep going").tap(log("tap")).says("one"));
        pressA(0.1);
        assertEquals(java.util.Arrays.asList("tap"), ran);
        String help = String.join("\n", plan.help());
        assertTrue(help, help.contains("G2 A: hold: keep going; tap: one"));
    }

    @Test public void aDoubleTapRunsOnlyItsJobAndASingleTapWaitsForTheGap() {
        make((p1, p2) -> p2.a.tap(log("tap")).doubleTap(log("double")));
        pressA(0.06);
        assertTrue(ran.isEmpty());                         // maybe a second tap is coming
        waitSec(0.4);                                      // Tunables.Buttons.tapGapSec 0.35 s ran out
        assertEquals(java.util.Arrays.asList("tap"), ran);
        ran.clear();
        pressA(0.06);
        waitSec(0.1);
        pressA(0.06);
        assertEquals(java.util.Arrays.asList("double"), ran);
        waitSec(0.5);
        assertEquals(java.util.Arrays.asList("double"), ran);
    }

    @Test public void tapThenHoldIsLastSeasonsSpray() {
        make((p1, p2) -> p2.a.tap(log("one")).tapThenHold(log("spray"), log("stop")));
        pressA(0.06);
        waitSec(0.1);
        pressA(0.6);                                       // tap, then press again and hold
        assertFalse(ran.contains("one"));
        assertTrue(ran.contains("spray"));
        assertEquals("stop", ran.get(ran.size() - 1));
        ran.clear();
        pressA(0.06);                                      // two quick taps: no double-tap job, so two taps
        waitSec(0.1);
        pressA(0.06);
        assertEquals(java.util.Arrays.asList("one", "one"), ran);
    }

    @Test public void threeTapsRunTheirJobAndTwoTapsRunTheTapTwice() {
        make((p1, p2) -> p2.a.tap(log("toggle")).taps(3, log("reverse")));
        for (int i = 0; i < 3; i++) {
            pressA(0.06);
            waitSec(0.1);
        }
        assertEquals(java.util.Arrays.asList("reverse"), ran);
        ran.clear();
        pressA(0.06);
        waitSec(0.1);
        pressA(0.06);
        waitSec(0.5);
        assertEquals(java.util.Arrays.asList("toggle", "toggle"), ran);
    }

    @Test public void heldForRunsOnceAfterItsTime() {
        make((p1, p2) -> p2.a.tap(log("tap")).heldFor(1.0, log("home")));
        pressA(0.5);                                       // let go too soon: a tap
        assertEquals(java.util.Arrays.asList("tap"), ran);
        ran.clear();
        pressA(1.5);
        assertEquals(java.util.Arrays.asList("home"), ran);
    }

    @Test public void aButtonHeldAtStartDoesNothingUntilLetGo() {
        g2.a = true;
        make((p1, p2) -> p2.a.tap(log("tap")).hold(log("held")));
        waitSec(1.0);
        assertTrue(ran.isEmpty());
        g2.a = false;
        loop(0.02);
        pressA(0.06);
        assertEquals(java.util.Arrays.asList("tap"), ran);
    }

    @Test public void pausedGesturesDoNothing() {
        make((p1, p2) -> p2.a.tap(log("tap")));
        robot.togglePause();
        pressA(0.06);
        assertTrue(ran.isEmpty());
        robot.togglePause();
        loop(0.02);
        pressA(0.06);
        assertEquals(1, ran.size());
    }

    @Test public void theGapIsATunable() {
        make((p1, p2) -> p2.a.tap(log("tap")).doubleTap(log("double")));
        Tunable.number("Buttons/tapGapSec", 0, 1).set(0.6);
        pressA(0.06);
        waitSec(0.5);
        pressA(0.06);                                      // 0.5 s later: still a double-tap now
        assertEquals(java.util.Arrays.asList("double"), ran);
    }

    @Test public void twoTapJobsOnOneButtonStopAtInit() {
        try {
            make((p1, p2) -> p2.a.tap(log("a")).tap(log("b")));
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("G2 A has two \"tap\" jobs"));
        }
        try {
            make((p1, p2) -> { p2.b.press(log("a")); p2.b.tap(log("b")); });
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("G2 B has two jobs"));
        }
    }

    // ----------------------------- feedback -----------------------------

    @Test public void rumbleBuzzesWhenTheJobRunsAndAToggleSaysOnOrOff() {
        make((p1, p2) -> {
            p2.x.press(log("x")).rumble();
            p2.b.toggle(robot.intake).rumble();
            p2.a.tap(log("tap")).doubleTap(log("double")).rumble(3);
        });
        g2.x = true; loop(0.02); g2.x = false; loop(0.02);
        assertEquals(java.util.Arrays.asList(1), buzzes);
        buzzes.clear();
        g2.b = true; loop(0.02); g2.b = false; loop(0.02);   // intake on: 2 buzzes
        g2.b = true; loop(0.02); g2.b = false; loop(0.02);   // off: 1
        assertEquals(java.util.Arrays.asList(2, 1), buzzes);
        buzzes.clear();
        pressA(0.06);
        waitSec(0.5);                                         // the tap: no buzz asked
        assertTrue(buzzes.isEmpty());
        pressA(0.06);
        waitSec(0.1);
        pressA(0.06);                                         // the double-tap: 3 buzzes
        assertEquals(java.util.Arrays.asList(3), buzzes);
        String help = String.join("\n", plan.help());
        assertTrue(help, help.contains("G2 B: toggle (buzz: 2 on, 1 off)"));
        assertTrue(help, help.contains("double-tap: job (buzz)"));
    }

    @Test public void aHoldBuzzesOnceWhenItStarts() {
        make((p1, p2) -> p2.a.hold(log("held")).rumble());
        pressA(0.5);
        assertEquals(java.util.Arrays.asList(1), buzzes);
    }

    @Test public void rumbleWhenBuzzesEachTimeItBecomesTrue() {
        final boolean[] ready = {false};
        make((p1, p2) -> p2.rumbleWhen(() -> ready[0], 2));
        loop(0.02);
        ready[0] = true;
        loop(0.02);
        loop(0.02);
        assertEquals(java.util.Arrays.asList(2), buzzes);
        ready[0] = false;
        loop(0.02);
        ready[0] = true;
        loop(0.02);
        assertEquals(java.util.Arrays.asList(2, 2), buzzes);
    }

    // ----------------------------- pairs -----------------------------

    @Test public void aPairRunsItsJobAndTheOtherButtonKeepsItsOwn() {
        make((p1, p2) -> {
            p2.y.press(log("y alone"));
            p2.lb.with(p2.y, log("lb + y")).says("climb");
            p2.lb.with(p2.x, log("lb + x"));
        });
        g2.y = true; loop(0.02); g2.y = false; loop(0.02);
        assertEquals(java.util.Arrays.asList("y alone"), ran);
        ran.clear();
        g2.left_bumper = true; loop(0.02);
        g2.y = true; loop(0.02); g2.y = false; loop(0.02);
        g2.x = true; loop(0.02); g2.x = false; loop(0.02);
        g2.left_bumper = false; loop(0.02);
        assertEquals(java.util.Arrays.asList("lb + y", "lb + x"), ran);
        String help = String.join("\n", plan.help());
        assertTrue(help, help.contains("G2 LB + Y: climb"));
        assertTrue(help, help.contains("G2 LB: shift key"));
    }

    @Test public void aShiftKeyWithItsOwnJobStopsAtInit() {
        try {
            make((p1, p2) -> {
                p2.lb.press(log("lb"));
                p2.lb.with(p2.y, log("pair"));
            });
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("G2 LB has two jobs"));
        }
    }

    @Test public void aGestureButtonWaitsWhileItsShiftKeyIsDown() {
        make((p1, p2) -> {
            p2.a.tap(log("tap"));
            p2.lb.with(p2.a, log("pair"));
        });
        g2.left_bumper = true; loop(0.02);
        pressA(0.06);
        g2.left_bumper = false; loop(0.02);
        assertEquals(java.util.Arrays.asList("pair"), ran);
    }

    @Test public void anActionOnAGestureStartsAndCancels() {
        Action a = Action.named("Wiggle").waitSeconds(5);
        make((p1, p2) -> p2.a.doubleTap(a));
        robot.runner.useClock(clock::get);
        pressA(0.06);
        waitSec(0.1);
        pressA(0.06);
        assertTrue(robot.runner.isBusy());
        String help = String.join("\n", plan.help());
        assertTrue(help, help.contains("double-tap: Wiggle"));
    }
}
