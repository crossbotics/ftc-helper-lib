package com.crossbotics.ftchelper.safety;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SoftLimitsTest {
    private static final double E = 1e-9;

    private static double limit(double power, double position) {
        return SoftLimits.limit(power, position, 0, 1000, 0, 0.25, false, false);
    }

    @Test public void blocksPowerPastTheLimitsOnly() {
        assertEquals(0.5, limit(0.5, 500), E);
        assertEquals(0, limit(0.5, 1000), E);      // at max, cannot go up
        assertEquals(-0.5, limit(-0.5, 1000), E);  // at max, can go down
        assertEquals(0, limit(-0.5, 0), E);        // at min, cannot go down
        assertEquals(0, limit(-0.5, -20), E);      // below min
        assertEquals(0.5, limit(0.5, -20), E);     // below min, can go up
        assertEquals(1, limit(3, 500), E);         // power clamped to 1
    }

    @Test public void switchesStopPowerTowardThem() {
        assertEquals(0, SoftLimits.limit(-0.5, 500, 0, 1000, 0, 0, true, false), E);
        assertEquals(0.5, SoftLimits.limit(0.5, 500, 0, 1000, 0, 0, true, false), E);
        assertEquals(0, SoftLimits.limit(0.5, 500, 0, 1000, 0, 0, false, true), E);
        assertEquals(-0.5, SoftLimits.limit(-0.5, 500, 0, 1000, 0, 0, false, true), E);
    }

    @Test public void slowZoneReducesPowerTowardTheNearLimit() {
        // 200-tick slow zone, 0.25 power at the very edge.
        assertEquals(1.0, SoftLimits.limit(1, 500, 0, 1000, 200, 0.25, false, false), E);
        assertEquals(0.625, SoftLimits.limit(1, 900, 0, 1000, 200, 0.25, false, false), E);  // halfway in
        assertEquals(0.25, SoftLimits.limit(1, 999.9999999, 0, 1000, 200, 0.25, false, false), 1e-6);
        assertEquals(-1.0, SoftLimits.limit(-1, 900, 0, 1000, 200, 0.25, false, false), E); // away: full
        assertEquals(-0.625, SoftLimits.limit(-1, 100, 0, 1000, 200, 0.25, false, false), E);
        assertEquals(0.3, SoftLimits.limit(0.3, 950, 0, 1000, 200, 0.25, false, false), E);  // already slow
    }
}
