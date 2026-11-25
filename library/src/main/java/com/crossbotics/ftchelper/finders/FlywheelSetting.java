package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.launcher.Flywheel.SpeedControl;
import java.util.Locale;

/**
 * One flywheel speed-control setting that the Flywheel Tuner tests: which control holds the speed (PID or
 * BANG_BANG) and the numbers of both. Each setting keeps all ten numbers, so one line saves it and one call
 * puts it on a {@link Flywheel}. It never changes: the {@code with...} methods give a new setting.
 *
 * <p>The numbers are the ones in {@code Tunables.java}, class Launcher: {@code kP}, {@code kI}, {@code kD},
 * {@code kF} (the hub's PID) and {@code bangKs}, {@code bangKv}, {@code bangFullBelowRpm},
 * {@code bangCoastAboveRpm}, {@code bangHysteresisRpm}, {@code bangMaxPower} (bang-bang).
 *
 * <pre>{@code
 * FlywheelSetting best = FlywheelTuneResults.load().winner(Flywheel.SpeedControl.BANG_BANG);
 * if (best != null) {
 *     flywheel.stop();
 *     best.applyTo(flywheel);      // bang-bang with the tuner's numbers
 * }
 * }</pre>
 *
 * Docs: docs/modules/finders.md, docs/tuning/06-flywheel-tuner.md
 */
public final class FlywheelSetting {
    /** Which control holds the speed. */
    public final SpeedControl control;
    /** The hub's PID: push strength, push for an old error, push against a fast change, steady power. */
    public final double kP, kI, kD, kF;
    /** Bang-bang's holding power: volts = ks + kv x RPM. */
    public final double ks, kv;
    /** Bang-bang: full power more than this below the target, RPM. */
    public final double fullBelowRpm;
    /** Bang-bang: power 0 (coast) more than this above the target, RPM. */
    public final double coastAboveRpm;
    /** Bang-bang: each state ends this much later than it starts, RPM. */
    public final double hysteresisRpm;
    /** Bang-bang: the most power, 0.3 to 1. */
    public final double maxPower;

    /**
     * A setting with every number.
     *
     * @param control      PID or BANG_BANG
     * @param kP           the hub's kP (0 or more)
     * @param kI           the hub's kI
     * @param kD           the hub's kD
     * @param kF           the hub's kF
     * @param ks           bang-bang volts to beat friction (0 or more)
     * @param kv           bang-bang volts for each RPM (above 0)
     * @param fullBelowRpm bang-bang full power below the target by more than this (above 0)
     * @param coastAboveRpm bang-bang coast above the target by more than this (0 or more)
     * @param hysteresisRpm bang-bang hysteresis, less than fullBelowRpm
     * @param maxPower     bang-bang most power, 0.3 to 1
     */
    public FlywheelSetting(SpeedControl control, double kP, double kI, double kD, double kF, double ks, double kv,
                           double fullBelowRpm, double coastAboveRpm, double hysteresisRpm, double maxPower) {
        if (control == null) throw new IllegalArgumentException("No speed control was given.\nUse PID or BANG_BANG.");
        this.control = control;
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
        this.kF = kF;
        this.ks = ks;
        this.kv = kv;
        this.fullBelowRpm = fullBelowRpm;
        this.coastAboveRpm = coastAboveRpm;
        this.hysteresisRpm = hysteresisRpm;
        this.maxPower = maxPower;
    }

    /**
     * The setting a flywheel has now: its control and all its numbers (PID's as given to
     * {@link Flywheel#pidf}, else 0).
     *
     * @param flywheel a set-up flywheel
     * @return its setting
     */
    public static FlywheelSetting of(Flywheel flywheel) {
        double[] g = flywheel.getPidf();
        if (g == null) g = new double[] {0, 0, 0, 0};
        return new FlywheelSetting(flywheel.getSpeedControl(), g[0], g[1], g[2], g[3], flywheel.getBangKs(),
                flywheel.getBangKv(), flywheel.getBangFullBelowRpm(), flywheel.getBangCoastAboveRpm(),
                flywheel.getBangHysteresisRpm(), flywheel.getBangMaxPower());
    }

    /** The same numbers with another control. */
    public FlywheelSetting withControl(SpeedControl c) {
        return new FlywheelSetting(c, kP, kI, kD, kF, ks, kv, fullBelowRpm, coastAboveRpm, hysteresisRpm, maxPower);
    }

    /** PID with this kP and kF (kI and kD stay). */
    public FlywheelSetting withPid(double p, double f) {
        return new FlywheelSetting(SpeedControl.PID, p, kI, kD, f, ks, kv, fullBelowRpm, coastAboveRpm, hysteresisRpm, maxPower);
    }

    /** PID with this kI (the other numbers stay). */
    public FlywheelSetting withKi(double i) {
        return new FlywheelSetting(SpeedControl.PID, kP, i, kD, kF, ks, kv, fullBelowRpm, coastAboveRpm, hysteresisRpm, maxPower);
    }

    /** BANG_BANG with these numbers (the PID numbers stay). */
    public FlywheelSetting withBang(double s, double v, double fullBelow, double coastAbove, double hysteresis, double most) {
        return new FlywheelSetting(SpeedControl.BANG_BANG, kP, kI, kD, kF, s, v, fullBelow, coastAbove, hysteresis, most);
    }

    /**
     * Puts this setting on the flywheel. A change of control needs a stopped wheel: call
     * {@link Flywheel#stop()} first. Numbers alone (same control) can change while it spins.
     *
     * @param flywheel the flywheel
     * @return true when the flywheel now uses this control; false when it refused (it was not stopped)
     */
    public boolean applyTo(Flywheel flywheel) {
        if (flywheel.getSpeedControl() != control && !flywheel.speedControl(control)) return false;
        double[] g = flywheel.getPidf();
        if (g == null || g[0] != kP || g[1] != kI || g[2] != kD || g[3] != kF) flywheel.pidf(kP, kI, kD, kF);
        flywheel.bangFeedforward(ks, kv).bangThresholds(fullBelowRpm, coastAboveRpm, hysteresisRpm).bangMaxPower(maxPower);
        return true;
    }

    /** True when the other setting has the same control and numbers. */
    public boolean sameAs(FlywheelSetting o) {
        return o != null && text().equals(o.text());
    }

    /**
     * Short plain words for the screen (at most about 40 characters): "PID, push strength 50" or
     * "bang-bang: full power when 150 RPM slow".
     */
    public String label() {
        if (control == SpeedControl.PID) return "PID, push strength (kP) " + n(kP, 1);
        return "bang-bang: full power when " + n(fullBelowRpm, 0) + " RPM slow";
    }

    /**
     * The setting as one line of text, read back by {@link #parse}: every number with all its digits.
     * "PID kP=10 kI=0 kD=0 kF=12.08 ks=0 kv=0.002237 full=200 coast=75 hyst=50 max=1".
     */
    public String text() {
        return control + " kP=" + d(kP) + " kI=" + d(kI) + " kD=" + d(kD) + " kF=" + d(kF) + " ks=" + d(ks)
                + " kv=" + d(kv) + " full=" + d(fullBelowRpm) + " coast=" + d(coastAboveRpm) + " hyst=" + d(hysteresisRpm)
                + " max=" + d(maxPower);
    }

    /**
     * Reads a line written by {@link #text()}.
     *
     * @param line the text
     * @return the setting, or null when the line is not one
     */
    public static FlywheelSetting parse(String line) {
        if (line == null) return null;
        String[] parts = line.trim().split(" +");
        if (parts.length != 11) return null;
        try {
            SpeedControl c = SpeedControl.valueOf(parts[0]);
            double[] v = new double[10];
            String[] names = {"kP", "kI", "kD", "kF", "ks", "kv", "full", "coast", "hyst", "max"};
            for (int i = 0; i < 10; i++) {
                String p = parts[i + 1];
                if (!p.startsWith(names[i] + "=")) return null;
                v[i] = Double.parseDouble(p.substring(names[i].length() + 1));
            }
            return new FlywheelSetting(c, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9]);
        } catch (IllegalArgumentException notASetting) {
            return null;
        }
    }

    @Override public String toString() {
        return text();
    }

    // Every digit (so a setting read back is exactly the same), never "1.0E-4".
    private static String d(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return String.valueOf((long) v);
        // BigDecimal.valueOf: the shortest text that reads back as exactly this number.
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private static String n(double v, int decimals) {
        String s = String.format(Locale.US, "%." + decimals + "f", v);
        // "50.0" reads as "50".
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
