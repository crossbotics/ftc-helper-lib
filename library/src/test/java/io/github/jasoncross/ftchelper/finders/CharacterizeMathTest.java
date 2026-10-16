package io.github.jasoncross.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.jasoncross.ftchelper.control.Feedforward;
import org.junit.Test;

/** The Characterizer's fitting math, on made-up logs with known numbers. */
public class CharacterizeMathTest {
    // A motor that follows power x volts/12.8 = kS sign(v) + kV v + kA a + kG, driven by a ramp then a step.
    private static double[][] log(double kS, double kV, double kA, double kG, double volts) {
        int n = 4000;
        double dt = 0.005;
        double[] t = new double[n], p = new double[n], v = new double[n], vs = new double[n];
        double vel = 0;
        for (int i = 0; i < n; i++) {
            t[i] = i * dt;
            p[i] = t[i] < 8 ? 0.1 * t[i] : (t[i] < 14 ? 0.0 : 0.7);   // ramp to 0.8, rest, then a step
            double push = p[i] * volts / 12.8 - kG;
            double acc = Math.abs(vel) < 1e-9 && Math.abs(push) <= kS ? 0 : (push - kS * Math.signum(vel != 0 ? vel : push) - kV * vel) / kA;
            if (p[i] == 0) acc = -vel / 0.3;                                 // coasting: other physics
            vel += acc * dt;
            v[i] = vel;
            vs[i] = volts;
        }
        return new double[][] {t, vs, p, v};
    }

    @Test public void fitFindsKnownNumbers() {
        double[][] l = log(0.05, 1 / 2800.0, 0.15 / 2800.0, 0, 12.0);
        CharacterizeMath.Fit f = CharacterizeMath.fit(l[0], l[1], l[2], l[3], false);
        assertEquals(0.05, f.kS, 0.003);
        assertEquals(2800, f.ticksPerSecPerPower(), 30);
        assertEquals(0.15, f.accelSec(), 0.01);
        assertTrue("r2 " + f.r2, f.r2 > 0.99);
    }

    @Test public void liftFitFindsGravityToo() {
        double[][] l = log(0.03, 1 / 2000.0, 0.1 / 2000.0, 0.12, 12.8);
        CharacterizeMath.Fit f = CharacterizeMath.fit(l[0], l[1], l[2], l[3], true);
        assertEquals(0.12, f.kG, 0.005);
        assertEquals(0.03, f.kS, 0.005);
        assertEquals(0.1, f.accelSec(), 0.01);
    }

    @Test public void coastAndTurningAndLeastSquares() {
        double[][] l = log(0.05, 1 / 2800.0, 0.15 / 2800.0, 0, 12.8);
        assertEquals("the made-up log coasts with a 0.3 s time constant", 0.3, CharacterizeMath.coastSec(l[0], l[2], l[3]), 0.02);
        double[] turn = {100, 200, 300, 400, 500, 600, 700, 800};
        double[] yaw = new double[turn.length];
        for (int i = 0; i < yaw.length; i++) yaw[i] = -0.08 * turn[i];          // clockwise: yaw goes down
        assertEquals(0.08, CharacterizeMath.turnDegPerCount(turn, yaw), 1e-9);
        double[] b = CharacterizeMath.leastSquares(new double[][] {{1, 0}, {0, 1}, {1, 1}}, new double[] {2, 3, 5});
        assertEquals(2, b[0], 1e-9);
        assertEquals(3, b[1], 1e-9);
        double[] tt = new double[60], heading = new double[60];
        for (int i = 0; i < 60; i++) {
            tt[i] = i * 0.01;
            double h = 170 + 90 * tt[i];                          // 90 degrees per second, past +180
            heading[i] = h > 180 ? h - 360 : h;
        }
        double[] rates = CharacterizeMath.yawRates(tt, heading);
        assertEquals("across +-180", 90, rates[30], 1e-6);
    }

    @Test public void feedforwardPower() {
        Feedforward ff = new Feedforward(0.05, 0.0004, 0.00002, 0.1);
        assertEquals(0.05 + 0.4 + 0.02 + 0.1, ff.power(1000, 1000), 1e-12);
        assertEquals(-0.05 - 0.4 + 0.1, ff.power(-1000, 0), 1e-12);
        assertEquals("a weaker battery needs more", (0.05 + 0.4 + 0.1) * 12.8 / 11.0, ff.power(1000, 0, 11.0), 1e-12);
        assertEquals((1 - 0.05 - 0.1) / 0.0004, ff.topSpeed(), 1e-9);
    }
}
