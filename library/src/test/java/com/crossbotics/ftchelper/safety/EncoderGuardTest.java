package com.crossbotics.ftchelper.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The flywheel encoder check: what trips it, and that a normal spin-up does not. */
public class EncoderGuardTest {

    @Test public void powerWithNoSpeedTripsAfterHalfASecond() {
        EncoderGuard g = new EncoderGuard();
        for (int i = 0; i < 25; i++) assertEquals("at " + i * 0.02, "", g.update(0.3, 43, i * 0.02));
        assertTrue(g.update(0.3, 43, 0.5).contains("stays near 0"));
    }

    @Test public void aNormalSpinUpWithStepsAndLagDoesNotTrip() {
        EncoderGuard g = new EncoderGuard();
        for (int i = 0; i < 150; i++) {
            double t = i * 0.02;
            double rpm = Math.rint(Math.max(0, t - 0.3) * 1500 / 43) * 43;        // still for 0.3 s, then slow steps
            assertEquals("at " + t, "", g.update(Math.min(1, 4 * t), rpm, t));
        }
    }

    @Test public void lowPowerAndAStillWheelAreFine() {
        EncoderGuard g = new EncoderGuard();
        for (int i = 0; i < 100; i++) assertEquals("", g.update(0.29, 0, i * 0.05));
    }

    @Test public void coastingAndGoingAgainStartsTheTimeAfresh() {
        EncoderGuard g = new EncoderGuard();
        assertEquals("", g.update(1, 0, 0));
        assertEquals("", g.update(1, 0, 0.4));
        assertEquals("", g.update(1, 60, 0.45));
        assertEquals("", g.update(1, 0, 0.5));
        assertEquals("", g.update(1, 0, 0.9));
        assertTrue(!g.update(1, 0, 1.0).isEmpty());
    }

    @Test public void theOppositeSignTripsButNotOneOddReading() {
        EncoderGuard g = new EncoderGuard();
        assertEquals("", g.update(0.5, -500, 0));
        assertEquals("", g.update(0.5, 500, 0.02));
        assertEquals("", g.update(0.5, -500, 0.04));
        assertTrue(g.update(0.5, -500, 0.06).contains("backward"));
        g.reset();
        assertEquals("", g.update(0.1, -43, 0.08));
        assertEquals("a wobble at rest is no sign", "", g.update(0.1, -43, 0.10));
        assertEquals("", g.update(0, -500, 0.12));
        assertEquals("no command, no sign to compare", "", g.update(0, -500, 0.14));
    }

    @Test public void aReadingThatIsNoNumberTripsAtOnce() {
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertTrue(new EncoderGuard().update(0, bad, 0).contains("not a number"));
        }
    }
}
