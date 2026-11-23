package com.crossbotics.ftchelper.control;

/**
 * Bang-bang speed control for a flywheel: the arithmetic only (no motor). It has three answers:
 * full power when the wheel is well below its speed, a steady "feedforward" power near it (the power
 * that holds that speed by itself), and 0 above it, so the wheel coasts. WHY: a shot takes speed away at
 * once, and full power gives it back as fast as the motor can. It never gives negative power, so it
 * never fights the spinning wheel.
 *
 * <pre>{@code
 * FlywheelBangBang law = new FlywheelBangBang()
 *         .feedforward(0.4, 0.00205)     // volts = 0.4 + 0.00205 x target RPM
 *         .thresholds(200, 75, 50);      // full 200 below, coast 75 above, 50 of hysteresis
 * double power = law.calculate(targetRpm, measuredRpm, batteryVolts, nowSec, false);
 * }</pre>
 *
 * <p>"Hysteresis" means a state ends later than it starts: full power starts 200 RPM below and ends only
 * 150 below. WHY: the speed reading moves in steps (43 RPM on a 28 count encoder), and one threshold
 * would switch on and off at each step.
 *
 * <p>Its feedforward is in volts for a speed in RPM. {@link Feedforward} uses other units (encoder counts
 * and power): do not copy numbers from one to the other. {@code launcher.Flywheel} runs this for you.
 * The coast needs a motor on FLOAT (no brake).
 *
 * Docs: docs/modules/control.md
 */
public final class FlywheelBangBang {

    /** What the last answer was: OFF (no target), FULL power, NEAR (feedforward) or COAST (power 0). */
    public enum Zone { OFF, FULL, NEAR, COAST }

    /** The power may rise this much a second while the wheel is below half its speed (a start from rest). */
    public static final double SPIN_UP_RISE_PER_SEC = 4;
    // The first answer after a reset has no time before it: count it as one normal loop, seconds.
    private static final double FIRST_LOOP_SEC = 0.02;
    // A long loop gives no more rise than this much time, seconds (no jump to full after a stall).
    private static final double MAX_RISE_STEP_SEC = 0.1;

    private double ksVolts = 0, kvVoltsPerRpm = 0;
    private double fullBelowRpm = 200, coastAboveRpm = 75, hysteresisRpm = 50;
    private double maxPower = 1;

    private Zone zone = Zone.OFF;
    private double lastPower = 0;
    private double lastAt = Double.NaN;

    /**
     * The steady power near the target: volts = ks + kv x target RPM, then power = volts / battery.
     *
     * @param ks volts to overcome friction (0 or more)
     * @param kv volts for each RPM (0 or more; a goBILDA 6000 RPM motor: about 0.002)
     */
    public FlywheelBangBang feedforward(double ks, double kv) {
        if (!(ks >= 0 && kv >= 0) || Double.isInfinite(ks) || Double.isInfinite(kv)) {
            throw new IllegalArgumentException("Wrong holding power numbers.\nYou gave " + ks + " and " + kv + ".\nUse 0 or more for both.");
        }
        ksVolts = ks;
        kvVoltsPerRpm = kv;
        return this;
    }

    /**
     * Where the three answers change, RPM from the target.
     *
     * @param fullBelow  full power starts more than this below the target (above 0)
     * @param coastAbove coasting starts more than this above it (0 or more)
     * @param hysteresis each of them ends this much later than it starts (0 or more, less than fullBelow,
     *                   so full power always ends below the target)
     */
    public FlywheelBangBang thresholds(double fullBelow, double coastAbove, double hysteresis) {
        if (!(fullBelow > 0 && coastAbove >= 0 && hysteresis >= 0 && hysteresis < fullBelow)
                || Double.isInfinite(fullBelow) || Double.isInfinite(coastAbove)) {
            throw new IllegalArgumentException("Wrong bang-bang speed limits."
                    + "\nFull power below: " + fullBelow + " RPM (use more than 0)."
                    + "\nCoast above: " + coastAbove + " RPM (use 0 or more)."
                    + "\nSwitching gap: " + hysteresis + " RPM."
                    + "\nUse a gap less than the full power number.");
        }
        fullBelowRpm = fullBelow;
        coastAboveRpm = coastAbove;
        hysteresisRpm = hysteresis;
        return this;
    }

    /**
     * The most power it gives, and the power when well below the target. Default 1.
     *
     * @param power 0.3 to 1. Not lower: the encoder check only judges a power of 0.3 or more.
     */
    public FlywheelBangBang maxPower(double power) {
        if (!(power >= 0.3 && power <= 1)) {
            throw new IllegalArgumentException("Wrong most power: " + power + ".\nUse 0.3 to 1 (1 is full power).");
        }
        maxPower = power;
        return this;
    }

    /**
     * The power for this loop, 0 to {@link #maxPower}. Never negative.
     *
     * @param targetRpm the speed wanted (0, negative or not a number: 0)
     * @param rpm       the speed measured, with its sign (not a number: 0)
     * @param volts     the battery to divide by (not a number, or 0: power 0)
     * @param nowSec    the time now, seconds (it only limits the rise from rest)
     * @param late      true when this loop came late (the speed is old): feedforward only, no full power.
     *                  A wheel above its speed still gets 0.
     * @return the motor power
     */
    public double calculate(double targetRpm, double rpm, double volts, double nowSec, boolean late) {
        if (!finite(targetRpm) || targetRpm <= 0 || !finite(rpm) || !finite(volts) || volts <= 0 || !finite(nowSec)) {
            reset();
            return 0;
        }
        double above = rpm - targetRpm, below = -above;
        // Too fast comes first: it wins over every other state.
        if (zone == Zone.COAST ? above > coastAboveRpm - hysteresisRpm : above > coastAboveRpm) {
            zone = Zone.COAST;
        } else if (!late && (zone == Zone.FULL ? below > fullBelowRpm - hysteresisRpm : below > fullBelowRpm)) {
            zone = Zone.FULL;
        } else {
            zone = Zone.NEAR;
        }
        double power = 0;
        if (zone == Zone.FULL) power = maxPower;
        else if (zone == Zone.NEAR) power = Math.max(0, Math.min(maxPower, (ksVolts + kvVoltsPerRpm * targetRpm) / volts));
        // From rest, ease the power in (a jump to full yanks belts and the battery). Only below half the
        // speed: the push after a shot must come at once.
        if (rpm < targetRpm / 2) {
            double dt = Double.isNaN(lastAt) ? FIRST_LOOP_SEC : Math.max(0, Math.min(MAX_RISE_STEP_SEC, nowSec - lastAt));
            power = Math.min(power, lastPower + SPIN_UP_RISE_PER_SEC * dt);
        }
        lastPower = power;
        lastAt = nowSec;
        return power;
    }

    /** Forgets everything: stopped, or another control takes over. */
    public void reset() {
        zone = Zone.OFF;
        lastPower = 0;
        lastAt = Double.NaN;
    }

    /** A new target: the thresholds judge it afresh. The rise from rest goes on from the power now. */
    public void targetChanged() {
        zone = Zone.OFF;
    }

    /** What the last answer was. */
    public Zone getZone() {
        return zone;
    }

    /** The feedforward's friction volts (kS). */
    public double getKs() { return ksVolts; }
    /** The feedforward's volts for each RPM (kV). */
    public double getKv() { return kvVoltsPerRpm; }
    /** Full power starts more than this below the target, RPM. */
    public double getFullBelowRpm() { return fullBelowRpm; }
    /** Coasting starts more than this above the target, RPM. */
    public double getCoastAboveRpm() { return coastAboveRpm; }
    /** How much later each state ends than it starts, RPM. */
    public double getHysteresisRpm() { return hysteresisRpm; }
    /** The most power it gives. */
    public double getMaxPower() { return maxPower; }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }
}
