package io.github.jasoncross.ftchelper.examples.tutorials.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import org.junit.Test;

/**
 * Tutorial 13: tests for team code. They run on a laptop in about a second, with no robot.
 * Run them in Android Studio with the green arrow next to "class", or from the Terminal:
 *   ./gradlew :examples:testDebugUnitTest
 */
public class LiftHeightsTest {

    @Test public void upAndDownStepThroughTheHeights() {
        assertEquals(LiftHeights.LOW, LiftHeights.BOTTOM.up());
        assertEquals(LiftHeights.MIDDLE, LiftHeights.LOW.up());
        assertEquals(LiftHeights.MIDDLE, LiftHeights.HIGH.down());
    }

    @Test public void endsDoNotWrapAround() {
        assertEquals(LiftHeights.HIGH, LiftHeights.HIGH.up());
        assertEquals(LiftHeights.BOTTOM, LiftHeights.BOTTOM.down());
    }

    @Test public void closestHeightAfterMovingByHand() {
        assertEquals(LiftHeights.BOTTOM, LiftHeights.closestTo(-50));
        assertEquals(LiftHeights.LOW, LiftHeights.closestTo(900));
        assertEquals(LiftHeights.HIGH, LiftHeights.closestTo(5000));
    }

    @Test public void heightsAreInOrderAndInsideTheLimit() {
        LiftHeights[] all = LiftHeights.values();
        for (int i = 1; i < all.length; i++) assertTrue(all[i].ticks > all[i - 1].ticks);
        assertTrue(LiftHeights.HIGH.ticks <= Lift.MAX_TICKS);
    }

    /** You can test how library pieces behave with YOUR numbers, too. */
    @Test public void ourLiftPidPushesUpWhenBelowTarget() {
        long[] now = {0};
        PidController pid = new PidController(0.004, 0, 0.0002, 0.05, () -> now[0]);
        double power = pid.calculate(LiftHeights.HIGH.ticks, 0);
        assertEquals(1.0, power, 1e-9);          // far below: full power up (clamped to 1)

        // A new controller that starts AT the target: only the F push remains.
        // (Do not jump the same controller from 0 to 2400 in one step: no real lift moves that
        // fast, and the D part would rightly push back very hard.)
        PidController atTop = new PidController(0.004, 0, 0.0002, 0.05, () -> 0L);
        assertEquals(0.05, atTop.calculate(LiftHeights.HIGH.ticks, LiftHeights.HIGH.ticks), 1e-9);
    }

    @Test public void ourStickSettingsIgnoreDrift() {
        assertEquals(0, StickShaping.shape(0.08, 0.1, 1), 1e-9);   // a worn stick resting at 0.08
    }
}
