package com.crossbotics.ftchelper.finders;

import java.util.ArrayList;
import java.util.List;

/**
 * The math behind the Characterizer: from a log of power, battery voltage and speed, it finds the
 * motor's feedforward numbers (see control/Feedforward): power = kS x sign(speed) + kV x speed +
 * kA x acceleration (+ kG for a lift). It is a "least squares" fit: the numbers that make the formula
 * miss the logged powers by the least, over every moment the motor was moving.
 *
 * <p>Pure math, no hardware: the OpMode, the simulator's fit tool and the tests all use it.
 *
 * Docs: docs/modules/finders.md ("Characterizer")
 */
public final class CharacterizeMath {
    private CharacterizeMath() { }

    /** Battery voltage the numbers are for (a full battery). */
    public static final double NOMINAL_VOLTS = 12.8;

    /** One fit. NaN where a number could not be found. */
    public static final class Fit {
        /** Power to start moving (kS), per speed (kV), per acceleration (kA), to hold against gravity (kG). */
        public final double kS, kV, kA, kG;
        /** How well the formula matches the log: 1 = perfectly, below 0.9 = be careful. */
        public final double r2;
        /** How many moments of the log were used. */
        public final int samples;

        Fit(double kS, double kV, double kA, double kG, double r2, int samples) {
            this.kS = kS;
            this.kV = kV;
            this.kA = kA;
            this.kG = kG;
            this.r2 = r2;
            this.samples = samples;
        }

        /** Top speed per unit of power, counts per second (what the simulator calls maxTicksPerSec). */
        public double ticksPerSecPerPower() {
            return kV > 0 ? 1 / kV : Double.NaN;
        }

        /** Seconds to reach about 63% of a new speed (kA / kV): how quickly it speeds up. */
        public double accelSec() {
            return kV > 0 ? kA / kV : Double.NaN;
        }

        /** True when the fit can be used: a speed number was found and it matches the log well enough. */
        public boolean ok() {
            return kV > 0 && !Double.isNaN(kA) && r2 > 0.5;
        }
    }

    // Half the time window used to smooth speeds and work out speeding-up (seconds): wide enough to
    // average out the steps and noise in measured speeds (a noisy speed-up makes kA come out too small),
    // narrow enough to follow a quick change.
    static final double WINDOW_SEC = 0.03;

    /**
     * Speeds-up (acceleration) at each moment, counts per second per second: the slope of a straight
     * line through the speeds within WINDOW_SEC each side (a least-squares slope: it smooths the noise).
     */
    public static double[] accelerations(double[] t, double[] v) {
        int n = t.length;
        double[] a = new double[n];
        double window = window(t);
        int lo = 0, hi = 0;
        for (int i = 0; i < n; i++) {
            lo = firstInWindow(t, i, lo, window);
            hi = lastInWindow(t, i, hi, window);
            int[] w = atLeastNeighbours(i, lo, hi, n);
            double mt = 0, mv = 0;
            lo = w[0];
            hi = w[1];
            int m = hi - lo + 1;
            for (int j = lo; j <= hi; j++) { mt += t[j] / m; mv += v[j] / m; }
            double sxy = 0, sxx = 0;
            for (int j = lo; j <= hi; j++) { sxy += (t[j] - mt) * (v[j] - mv); sxx += (t[j] - mt) * (t[j] - mt); }
            a[i] = sxx > 1e-12 ? sxy / sxx : 0;
        }
        return a;
    }

    // The smoothing window: WINDOW_SEC, or 2.5 loops each side for a slower robot loop (enough moments to
    // average out a robot's noisy speed readings).
    static double window(double[] t) {
        if (t.length < 3) return WINDOW_SEC;
        double[] d = new double[t.length - 1];
        for (int i = 1; i < t.length; i++) d[i - 1] = t[i] - t[i - 1];
        java.util.Arrays.sort(d);
        return Math.max(WINDOW_SEC, 2.5 * d[d.length / 2]);
    }

    // A slow log (moments far apart) still uses one neighbour each side, so a speed-up can be seen.
    // Returns {lo, hi}; the next search goes on from the window's own edges.
    private static int[] atLeastNeighbours(int i, int lo, int hi, int n) {
        return new int[] {Math.min(lo, Math.max(0, i - 1)), Math.max(hi, Math.min(n - 1, i + 1))};
    }

    // The first moment at most WINDOW_SEC before moment i (searching on from the last answer).
    private static int firstInWindow(double[] t, int i, int from, double window) {
        int lo = from;
        for (int j = from; j <= i; j++) {
            lo = j;
            if (t[i] - t[j] <= window) break;
        }
        return lo;
    }

    // The last moment at most WINDOW_SEC after moment i (searching on from the last answer).
    private static int lastInWindow(double[] t, int i, int from, double window) {
        int hi = Math.max(from, i);
        for (int j = hi + 1; j < t.length && t[j] - t[i] <= window; j++) hi = j;
        return hi;
    }

    /** Speeds averaged over WINDOW_SEC each side (the same window as the speeds-up). */
    public static double[] smoothed(double[] t, double[] v) {
        int n = t.length;
        double[] out = new double[n];
        double window = window(t);
        int lo = 0, hi = 0;
        for (int i = 0; i < n; i++) {
            lo = firstInWindow(t, i, lo, window);
            hi = lastInWindow(t, i, hi, window);
            int[] w = atLeastNeighbours(i, lo, hi, n);
            double sum = 0;
            lo = w[0];
            hi = w[1];
            for (int j = lo; j <= hi; j++) sum += v[j];
            out[i] = sum / (hi - lo + 1);
        }
        return out;
    }

    /**
     * Fits kS, kV, kA (and kG when {@code gravity}) to a log.
     *
     * @param t       seconds
     * @param volts   battery volts at each moment
     * @param power   the power the motor was given (-1..1)
     * @param measured its speed, counts per second
     * @param gravity true for a lift (adds kG, the power that holds it against gravity)
     */
    public static Fit fit(double[] t, double[] volts, double[] power, double[] measured, boolean gravity) {
        double[] a = accelerations(t, measured);
        double[] v = smoothed(t, measured);
        double vMax = 0;
        for (double x : v) vMax = Math.max(vMax, Math.abs(x));
        // Only moments when it is really moving (a still motor says nothing about kV and kA), and not the
        // first and last few moments of a change of power (the speed reading lags behind a little there).
        double moving = Math.max(5, 0.03 * vMax);
        List<double[]> rows = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        double jumpSec = window(t) + 0.02;
        for (int i = 0; i < t.length; i++) {
            if (Math.abs(v[i]) < moving) continue;
            // Zero power is braking or coasting, which follows other physics (see coastSec): left out.
            if (Math.abs(power[i]) < 1e-6) continue;
            if (nearJump(t, power, i, jumpSec)) continue;
            double pEff = power[i] * volts[i] / NOMINAL_VOLTS;   // the same push as this power at 12.8 V
            rows.add(gravity ? new double[] {Math.signum(v[i]), v[i], a[i], 1} : new double[] {Math.signum(v[i]), v[i], a[i]});
            ys.add(pEff);
        }
        int cols = gravity ? 4 : 3;
        if (rows.size() < cols * 5) return new Fit(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, rows.size());
        double[][] x = rows.toArray(new double[0][]);
        double[] y = new double[ys.size()];
        for (int i = 0; i < y.length; i++) y[i] = ys.get(i);
        double[] b = leastSquares(x, y);
        if (b == null) return new Fit(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, rows.size());
        return new Fit(b[0], b[1], b[2], gravity ? b[3] : 0, r2(x, y, b), rows.size());
    }

    // True within this many seconds of a sudden change of power (the motor and the speed reading take a
    // few milliseconds to follow, so those moments would spoil the fit).
    private static boolean nearJump(double[] t, double[] power, int i, double sec) {
        for (int j = i; j >= 1 && t[i] - t[j] <= sec; j--) if (Math.abs(power[j] - power[j - 1]) > 0.05) return true;
        for (int j = i; j < t.length - 1 && t[j] - t[i] <= sec; j++) if (Math.abs(power[j + 1] - power[j]) > 0.05) return true;
        return false;
    }

    /**
     * How long a coasting motor (power 0, FLOAT) takes to slow down: the time for its speed to drop to
     * about 37% (one "time constant"), seconds. NaN if the log has no coasting.
     */
    public static double coastSec(double[] t, double[] power, double[] v) {
        double[] a = accelerations(t, v);
        double vMax = 0;
        for (double x : v) vMax = Math.max(vMax, Math.abs(x));
        List<double[]> rows = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (int i = 2; i < t.length - 2; i++) {
            if (power[i - 2] != 0 || power[i] != 0 || power[i + 2] != 0) continue;
            if (Math.abs(v[i]) < Math.max(5, 0.05 * vMax)) continue;
            // a = c0 x sign(v) + c1 x v: friction plus a drag that grows with speed
            rows.add(new double[] {Math.signum(v[i]), v[i]});
            ys.add(a[i]);
        }
        if (rows.size() < 10) return Double.NaN;
        double[] y = new double[ys.size()];
        for (int i = 0; i < y.length; i++) y[i] = ys.get(i);
        double[] b = leastSquares(rows.toArray(new double[0][]), y);
        return b == null || b[1] >= 0 ? Double.NaN : -1 / b[1];
    }

    /**
     * Degrees the robot turns for each wheel count of turning (from the IMU and the wheels), so the
     * simulator turns like the real robot (wheels slip a little when a robot turns).
     *
     * @param turnCountsPerSec the wheels' turning speed: (left - right) / 2, counts per second
     * @param yawRateDeg       the IMU's turning speed, degrees per second, counterclockwise = +
     */
    public static double turnDegPerCount(double[] turnCountsPerSec, double[] yawRateDeg) {
        double sxy = 0, sxx = 0;
        double vMax = 0;
        for (double x : turnCountsPerSec) vMax = Math.max(vMax, Math.abs(x));
        for (int i = 0; i < turnCountsPerSec.length; i++) {
            if (Math.abs(turnCountsPerSec[i]) < Math.max(5, 0.05 * vMax)) continue;
            // left faster than right = turning clockwise = the IMU's yaw going down
            sxy += turnCountsPerSec[i] * -yawRateDeg[i];
            sxx += turnCountsPerSec[i] * turnCountsPerSec[i];
        }
        return sxx > 0 ? sxy / sxx : Double.NaN;
    }

    /** Turning speed from headings (degrees per second), with the jump at +-180 taken out. */
    public static double[] yawRates(double[] t, double[] headingDeg) {
        int n = t.length;
        double[] unwrapped = new double[n];
        for (int i = 0; i < n; i++) {
            if (i == 0) { unwrapped[i] = headingDeg[i]; continue; }
            double d = headingDeg[i] - headingDeg[i - 1];
            d -= 360 * Math.round(d / 360);
            unwrapped[i] = unwrapped[i - 1] + d;
        }
        return accelerations(t, unwrapped);   // the same smoothed difference, of heading instead of speed
    }

    /** The b that makes x b closest to y (least squares). Null if the columns do not tell the numbers apart. */
    public static double[] leastSquares(double[][] x, double[] y) {
        int m = x[0].length;
        double[][] a = new double[m][m + 1];
        for (int r = 0; r < x.length; r++) {
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < m; j++) a[i][j] += x[r][i] * x[r][j];
                a[i][m] += x[r][i] * y[r];
            }
        }
        // Gaussian elimination with the biggest pivot in each column.
        for (int c = 0; c < m; c++) {
            int best = c;
            for (int r = c + 1; r < m; r++) if (Math.abs(a[r][c]) > Math.abs(a[best][c])) best = r;
            double[] tmp = a[c];
            a[c] = a[best];
            a[best] = tmp;
            if (Math.abs(a[c][c]) < 1e-12) return null;
            for (int r = 0; r < m; r++) {
                if (r == c) continue;
                double f = a[r][c] / a[c][c];
                for (int k = c; k <= m; k++) a[r][k] -= f * a[c][k];
            }
        }
        double[] b = new double[m];
        for (int i = 0; i < m; i++) b[i] = a[i][m] / a[i][i];
        return b;
    }

    private static double r2(double[][] x, double[] y, double[] b) {
        double mean = 0;
        for (double v : y) mean += v / y.length;
        double ssRes = 0, ssTot = 0;
        for (int r = 0; r < y.length; r++) {
            double guess = 0;
            for (int i = 0; i < b.length; i++) guess += x[r][i] * b[i];
            ssRes += (y[r] - guess) * (y[r] - guess);
            ssTot += (y[r] - mean) * (y[r] - mean);
        }
        return ssTot > 0 ? 1 - ssRes / ssTot : 0;
    }
}
