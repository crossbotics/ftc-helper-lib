package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.testkit.PretendTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/**
 * The team's own code at each step of an OpMode (docs/guides/your-own-code.md): atInit, duringInit,
 * atStart, everyLoop and atStop run at the right time, in order, and only once where they should.
 */
public class LifecycleHooksTest {

    @After public void back() {
        SafeMotor.freshReadingsForTests(false);
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    public static class Numbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Claw { public static double open = 0.25, closed = 0.70; }
    }

    // The steps each hook saw, in order.
    static final List<String> SEEN = new ArrayList<>();

    public static class Bot extends RobotDefinition {
        public Claw claw;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            claw = add(Claw.servo("claw"));
            atInit(() -> SEEN.add("init"));
            duringInit(() -> {
                SEEN.add("initLoop");
                show("Claw", "ready");
            });
            atStart(() -> Action.named("start").then(() -> SEEN.add("start")));
            everyLoop(() -> SEEN.add("loop"));
            atStop(() -> SEEN.add("stop"));
        }
    }

    @Test public void eachHookRunsAtItsStepInOrder() {
        SEEN.clear();
        Bot r = new Bot();
        r.setup(new PretendDevices());                     // INIT
        assertEquals(Arrays.asList("init"), SEEN);
        PretendTelemetry screen = new PretendTelemetry();
        RobotTelemetry t = new RobotTelemetry(screen.telemetry);
        r.initLoop(t);                                     // two INIT loops
        assertTrue("show() puts the line on the screen: " + t.pending(), t.pending().contains("Claw : ready"));
        r.initLoop(t);
        r.startForTests();                                 // START
        r.all().update();                                  // two loops
        r.all().update();
        r.ended();                                         // STOP
        r.ended();                                         // a second end does nothing
        assertEquals(Arrays.asList("init", "initLoop", "initLoop", "start", "loop", "loop", "stop"), SEEN);
    }

    @Test public void codeAddedAfterInitStillRuns() {
        SEEN.clear();
        Bot r = new Bot();
        r.setup(new PretendDevices());
        // As in Controls.java's buttons(...): the robot is already set up.
        r.atInit(() -> SEEN.add("late init"));
        r.everyLoop(() -> SEEN.add("late loop"));
        r.atStop(() -> SEEN.add("late stop"));
        r.startForTests();
        r.all().update();
        r.ended();
        assertTrue(SEEN.toString(), SEEN.contains("late init"));
        assertTrue(SEEN.toString(), SEEN.indexOf("late loop") > SEEN.indexOf("loop"));
        assertTrue(SEEN.toString(), SEEN.indexOf("late stop") > SEEN.indexOf("stop"));
    }

    @Test public void oneFailingAtStopDoesNotSkipTheOthers() {
        SEEN.clear();
        Bot r = new Bot();
        r.atStop(() -> { throw new IllegalStateException("broken"); });
        r.atStop(() -> SEEN.add("after the broken one"));
        r.setup(new PretendDevices());
        try {
            r.ended();
            fail("the error is not lost");
        } catch (IllegalStateException e) {
            assertEquals("broken", e.getMessage());
        }
        assertTrue(SEEN.toString(), SEEN.contains("after the broken one"));
    }

    @Test public void theOwnOpModeStepsNeedInitFirst() {
        Bot r = new Bot();
        try {
            r.update();
            fail("update() without init(this)");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("robot.init(this)"));
        }
    }

    @Test public void nullCodeIsRefusedWithItsName() {
        Bot r = new Bot();
        try {
            r.duringInit(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("duringInit(null)"));
        }
    }
}
