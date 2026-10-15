package org.octobots.ftchelper.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ControlTest {
    private static final double E = 1e-9;
    private static final long MS = 1_000_000L;

    @Test public void proportionalOnlyAndClamped() {
        long[] now = {0};
        PidController pid = new PidController(0.01, 0, 0, 0, () -> now[0]);
        assertEquals(0.5, pid.calculate(100, 50), E);
        assertEquals(1.0, pid.calculate(1000, 0), E);           // clamped to default max 1
        pid.setOutputLimits(-0.3, 0.6);
        assertEquals(-0.3, pid.calculate(0, 1000), E);
    }

    @Test public void feedforwardIsAdded() {
        PidController pid = new PidController(0, 0, 0, 0.1, () -> 0L);
        assertEquals(0.1, pid.calculate(0, 0), E);
    }

    @Test public void derivativeUsesTimeAndIsZeroOnFirstCall() {
        long[] now = {0};
        PidController pid = new PidController(0, 0, 1.0, 0, () -> now[0]);
        assertEquals(0, pid.calculate(10, 0), E);             // first call: no derivative
        now[0] += 100 * MS;
        // error 10 -> 5 in 0.1 s: derivative -50, clamped to -1
        assertEquals(-1, pid.calculate(10, 5), E);
        pid.setOutputLimits(-100, 100);
        now[0] += 100 * MS;
        assertEquals(-20, pid.calculate(10, 7), 1e-6);        // 5 -> 3 in 0.1 s
    }

    @Test public void integralBuildsUpAndIsLimited() {
        long[] now = {0};
        PidController pid = new PidController(0, 1.0, 0, 0, () -> now[0])
                .setOutputLimits(-10, 10).setIntegralLimit(0.3);
        pid.calculate(1, 0);
        for (int i = 0; i < 10; i++) {
            now[0] += 100 * MS;
            pid.calculate(1, 0);
        }
        assertEquals(0.3, pid.calculate(1, 0), E);   // limited, not 1.0
        pid.reset();
        assertEquals(0, pid.calculate(1, 0), E);     // first call after reset
    }

    @Test public void angleModeTakesShortWayAndTolerance() {
        PidController pid = new PidController(0.01, 0, 0, 0, () -> 0L).setAngleMode(true).setTolerance(2);
        assertFalse(pid.atTarget());                  // no reading yet
        assertEquals(0.2, pid.calculate(-170, 170), E);   // +20 degrees, not -340
        assertEquals(20, pid.getLastError(), E);
        assertFalse(pid.atTarget());
        pid.calculate(90, 89);
        assertTrue(pid.atTarget());
    }

    @Test public void slewRateLimitsRiseAndFall() {
        long[] now = {0};
        SlewRateLimiter ramp = new SlewRateLimiter(2.0, 4.0, () -> now[0]);
        assertEquals(0, ramp.calculate(1), E);        // first call starts from 0
        now[0] += 100 * MS;
        assertEquals(0.2, ramp.calculate(1), 1e-9);   // rise 2/s * 0.1 s
        now[0] += 100 * MS;
        assertEquals(0.4, ramp.calculate(1), 1e-9);
        now[0] += 50 * MS;
        assertEquals(0.2, ramp.calculate(0), 1e-9);   // fall 4/s * 0.05 s
        now[0] += 1000 * MS;
        assertEquals(-1, ramp.calculate(-1), 1e-9);   // plenty of time to reach the target
        ramp.reset(0);
        assertEquals(0, ramp.getValue(), E);
    }

    @Test public void slewReversalUsesRiseRateAfterCrossingZero() {
        long[] now = {0};
        SlewRateLimiter ramp = new SlewRateLimiter(2.0, 4.0, () -> now[0]);
        ramp.reset(0.1);
        ramp.calculate(-1);                       // starts the clock
        now[0] += 100 * MS;
        // 0.1 -> 0 at fall 4/s takes 0.025 s; the other 0.075 s at rise 2/s gives -0.15.
        assertEquals(-0.15, ramp.calculate(-1), 1e-9);
    }

    @Test public void angleModeDerivativeIgnoresTheSeam() {
        long[] now = {0};
        PidController pid = new PidController(0, 0, 1.0, 0, () -> now[0])
                .setAngleMode(true).setOutputLimits(-1000, 1000);
        pid.calculate(0, -179);                   // error +179
        now[0] += 1000 * MS;
        // Real move: 2 degrees across the seam to +179 (error -179). Change is +2, not -358.
        assertEquals(2, pid.calculate(0, 179), 1e-9);
    }
}
