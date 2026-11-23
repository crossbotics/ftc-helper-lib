package com.crossbotics.ftchelper.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.control.FlywheelBangBang.Zone;
import java.util.ArrayDeque;
import java.util.Random;
import org.junit.Test;

/** The bang-bang law, and a small wheel with speed steps, a late reading and uneven loops. */
public class FlywheelBangBangTest {
    private static final double E = 1e-9;

    private static FlywheelBangBang law() {
        return new FlywheelBangBang().feedforward(0.5, 0.002).thresholds(200, 75, 50);
    }

    private double now = 0;

    private double at(FlywheelBangBang b, double rpm) {
        now += 0.02;
        return b.calculate(3000, rpm, 13, now, false);
    }

    @Test public void fullFeedforwardAndCoast() {
        FlywheelBangBang b = law();
        assertEquals("near: volts / battery", (0.5 + 0.002 * 3000) / 13, at(b, 3000), E);
        assertEquals(Zone.NEAR, b.getZone());
        assertEquals("well below", 1, at(b, 2700), E);
        assertEquals(Zone.FULL, b.getZone());
        assertEquals("above", 0, at(b, 3100), E);
        assertEquals(Zone.COAST, b.getZone());
    }

    @Test public void fullPowerEndsLaterThanItStarts() {
        FlywheelBangBang b = law();
        at(b, 2801);
        assertEquals("not yet 200 below", Zone.NEAR, b.getZone());
        at(b, 2799);
        assertEquals(Zone.FULL, b.getZone());
        at(b, 2840);
        assertEquals("still full 160 below", Zone.FULL, b.getZone());
        at(b, 2851);
        assertEquals("ends 150 below", Zone.NEAR, b.getZone());
    }

    @Test public void coastingEndsLaterThanItStarts() {
        FlywheelBangBang b = law();
        at(b, 3074);
        assertEquals(Zone.NEAR, b.getZone());
        at(b, 3076);
        assertEquals(Zone.COAST, b.getZone());
        at(b, 3030);
        assertEquals("still coasting 30 above", Zone.COAST, b.getZone());
        at(b, 3024);
        assertEquals(Zone.NEAR, b.getZone());
    }

    @Test public void tooFastWinsOverEveryState() {
        FlywheelBangBang b = law();
        assertEquals(1, at(b, 2500), E);
        assertEquals("from full straight to 0", 0, at(b, 3200), E);
        now += 0.2;
        assertEquals("and in a late loop", 0, b.calculate(3000, 3200, 13, now, true), E);
    }

    @Test public void aLateLoopGivesFeedforwardOnly() {
        FlywheelBangBang b = law();
        assertEquals(1, at(b, 2500), E);
        now += 0.2;
        assertEquals((0.5 + 0.002 * 3000) / 13, b.calculate(3000, 2500, 13, now, true), E);
        assertEquals(Zone.NEAR, b.getZone());
    }

    @Test public void neverNegativeAndBadInputsGiveZero() {
        FlywheelBangBang b = law();
        double nan = Double.NaN, inf = Double.POSITIVE_INFINITY;
        double[][] bad = {{0, 100, 13}, {-3000, -100, 13}, {nan, 100, 13}, {inf, 100, 13}, {3000, nan, 13},
                {3000, inf, 13}, {3000, 2000, 0}, {3000, 2000, nan}};
        for (double[] v : bad) {
            assertEquals(1, at(b, 2000), E);
            assertEquals(0, b.calculate(v[0], v[1], v[2], now, false), 0);
            assertEquals(Zone.OFF, b.getZone());
        }
        assertEquals(0, b.calculate(3000, 2000, 13, nan, false), 0);
        for (double rpm = -6000; rpm <= 7000; rpm += 50) assertTrue(at(b, rpm) >= 0);
    }

    @Test public void thePowerRisesSlowlyOnlyFromRest() {
        FlywheelBangBang b = law();
        double first = at(b, 0);
        assertEquals(FlywheelBangBang.SPIN_UP_RISE_PER_SEC * 0.02, first, E);
        assertEquals(2 * first, at(b, 100), E);
        now += 2;
        assertTrue("a long gap is not a jump to full", b.calculate(3000, 100, 13, now, false) < 1);
        b.reset();
        assertEquals("above half: the push after a shot comes at once", 1, at(b, 1600), E);
    }

    @Test public void maxPowerLimitsEveryAnswer() {
        FlywheelBangBang b = law().maxPower(0.4);
        assertEquals(0.4, at(b, 2500), E);
        assertEquals(0.4, at(b, 3000), E);
    }

    @Test public void resetForgetsTheState() {
        FlywheelBangBang b = law();
        at(b, 2700);
        b.reset();
        assertEquals(Zone.OFF, b.getZone());
        at(b, 2830);
        assertEquals("170 below is not full after a reset", Zone.NEAR, b.getZone());
    }

    @Test public void badSettingsAreRefused() {
        Runnable[] bad = {
                () -> law().thresholds(0, 75, 0),
                () -> law().thresholds(100, 75, 100),
                () -> law().thresholds(100, -1, 20),
                () -> law().thresholds(Double.NaN, 75, 20),
                () -> law().maxPower(0.2),
                () -> law().maxPower(1.1),
                () -> law().feedforward(-1, 0.002),
                () -> law().feedforward(0, Double.NaN)};
        for (int i = 0; i < bad.length; i++) {
            try {
                bad[i].run();
                fail("setting " + i);
            } catch (IllegalArgumentException expected) {
                assertTrue(!expected.getMessage().isEmpty());
            }
        }
    }

    // ---- A small wheel: speed follows the power with a time constant ----

    private static final double TOP = 5800, TAU = 2.0, VOLTS = 13, STEP_RPM = 43, LAG = 0.05;

    @Test public void holdsAndRecoversWithStepsLagAndUnevenLoops() {
        for (long seed = 1; seed <= 25; seed++) holdsAndRecovers(seed);
    }

    private void holdsAndRecovers(long seed) {
        Random random = new Random(seed);
        FlywheelBangBang b = new FlywheelBangBang().feedforward(0, VOLTS / TOP).thresholds(200, 75, 50);
        double target = 2630, rpm = 0, power = 0, t = 0, nextLoop = 0;
        ArrayDeque<double[]> past = new ArrayDeque<>();
        past.add(new double[] {0, 0});
        double firstAtSpeed = Double.NaN, highest = 0, worstLate = 0, back = Double.NaN;
        boolean shot = false;
        while (t < 14) {
            if (t >= nextLoop) {
                while (past.size() > 1) {
                    double[] oldest = past.removeFirst();
                    if (past.getFirst()[0] > t - LAG) {
                        past.addFirst(oldest);
                        break;
                    }
                }
                double read = Math.rint(past.getFirst()[1] / STEP_RPM) * STEP_RPM;
                power = b.calculate(target, read, VOLTS, t, false);
                assertTrue(power >= 0 && power <= 1);
                nextLoop = t + 0.02 + random.nextDouble() * 0.04;       // 20 to 60 ms loops
            }
            // power 0 on FLOAT: it coasts more slowly than it is driven
            rpm += (power * TOP - rpm) * 0.001 / (power == 0 ? 6 : TAU);
            t += 0.001;
            if (!shot && t >= 8) {
                shot = true;
                rpm -= 230;
            }
            past.addLast(new double[] {t, rpm});
            if (Double.isNaN(firstAtSpeed) && Math.abs(rpm - target) < 100) firstAtSpeed = t;
            highest = Math.max(highest, rpm);
            if (t > 4 && t < 8) worstLate = Math.max(worstLate, Math.abs(rpm - target));
            if (shot && Double.isNaN(back) && Math.abs(rpm - target) < 100) back = t - 8;
        }
        assertTrue(seed + ": at speed, took " + firstAtSpeed, firstAtSpeed < 2.5);
        assertTrue(seed + ": overshoot " + (highest - target), highest - target < 300);
        // a speed step, plus what the wheel moves in one loop and the lag
        assertTrue(seed + ": settled within " + worstLate, worstLate < 220);
        assertTrue(seed + ": back after a shot in " + back, back < 1);
    }
}
