package io.github.jasoncross.ftchelper.examples.tutorials.robot;

// "import static" lets us write assertEquals(...) instead of Assert.assertEquals(...).
// assertEquals(expected, actual) fails the test if the two differ; assertTrue(x) fails if x is false.
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import org.junit.Test;

/**
 * Tutorial 13: tests for team code. They run on a laptop in about a second, with no robot.
 * Run them in Android Studio with the green arrow next to "class", or from the Terminal:
 *   ./gradlew :examples:testDebugUnitTest
 *
 * A test is a small method that runs a piece of code and CHECKS the answer. If a check fails,
 * the test turns red and tells you which number was wrong. Run them after every change: a red
 * test on the laptop is much cheaper than a lift that drives into its top at a competition.
 * More in docs/guides/testing.md and docs/tutorials/13-testing.md.
 *
 * What we test here: LiftHeights (robot/LiftHeights.java), the list of set lift heights.
 */
public class LiftHeightsTest {

    // "@Test" marks a method as a test, so the test runner finds and runs it.
    // The long method names say what should be true; that name is what you read when it fails.

    // up() goes to the next height, down() to the one below.
    @Test public void upAndDownStepThroughTheHeights() {
        assertEquals(LiftHeights.LOW, LiftHeights.BOTTOM.up());
        assertEquals(LiftHeights.MIDDLE, LiftHeights.LOW.up());
        assertEquals(LiftHeights.MIDDLE, LiftHeights.HIGH.down());
    }

    // At the ends it must STAY put: "up" from HIGH must never jump round to BOTTOM (that would
    // slam the lift down when a driver presses up one time too many).
    @Test public void endsDoNotWrapAround() {
        assertEquals(LiftHeights.HIGH, LiftHeights.HIGH.up());
        assertEquals(LiftHeights.BOTTOM, LiftHeights.BOTTOM.down());
    }

    // After the driver moves the lift by hand, closestTo() finds the nearest set height
    // (positions in encoder ticks). Also checks values outside the range (-50, 5000).
    @Test public void closestHeightAfterMovingByHand() {
        assertEquals(LiftHeights.BOTTOM, LiftHeights.closestTo(-50));
        assertEquals(LiftHeights.LOW, LiftHeights.closestTo(900));
        assertEquals(LiftHeights.HIGH, LiftHeights.closestTo(5000));
    }

    // Catches a typo in the height numbers: each height must be above the one before it, and
    // HIGH must be inside the lift's safe limit (Lift.MAX_TICKS), or the lift could never reach it.
    @Test public void heightsAreInOrderAndInsideTheLimit() {
        // values() gives every height in the order they are written in LiftHeights.java.
        LiftHeights[] all = LiftHeights.values();
        for (int i = 1; i < all.length; i++) assertTrue(all[i].ticks > all[i - 1].ticks);
        assertTrue(LiftHeights.HIGH.ticks <= Lift.MAX_TICKS);
    }

    /** You can test how library pieces behave with YOUR numbers, too. */
    @Test public void ourLiftPidPushesUpWhenBelowTarget() {
        // A pretend clock for the PID, so the test does not depend on real time.
        // It is an array of one number because a lambda ("() -> now[0]", a small piece of code
        // handed to the PID to call later) cannot use a plain local number that might change;
        // the contents of an array are allowed to change.
        long[] now = {0};
        // The same gains as the real lift (kP, kI, kD, kF), plus our pretend clock.
        PidController pid = new PidController(0.004, 0, 0.0002, 0.05, () -> now[0]);
        // Target HIGH (2400 ticks), lift at 0: 2400 * 0.004 + 0.05 is far more than 1.
        double power = pid.calculate(LiftHeights.HIGH.ticks, 0);
        // The third number (1e-9 = 0.000000001) is how close counts as equal: decimal numbers
        // in a computer are rarely EXACTLY equal.
        assertEquals(1.0, power, 1e-9);          // far below: full power up (clamped to 1)

        // A new controller that starts AT the target: only the F push remains.
        // (Do not jump the same controller from 0 to 2400 in one step: no real lift moves that
        // fast, and the D part would rightly push back very hard.)
        // "() -> 0L" is a clock stuck at 0 (the L means a "long", a whole number type).
        PidController atTop = new PidController(0.004, 0, 0.0002, 0.05, () -> 0L);
        assertEquals(0.05, atTop.calculate(LiftHeights.HIGH.ticks, LiftHeights.HIGH.ticks), 1e-9);
    }

    // Our stick settings (dead zone 0.1, no curve) must ignore a worn stick that does not return
    // all the way to 0, or the lift would creep by itself.
    @Test public void ourStickSettingsIgnoreDrift() {
        assertEquals(0, StickShaping.shape(0.08, 0.1, 1), 1e-9);   // a worn stick resting at 0.08
    }
}
