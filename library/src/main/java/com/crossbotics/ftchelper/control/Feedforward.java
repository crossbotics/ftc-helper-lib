package com.crossbotics.ftchelper.control;

/**
 * The power a motor needs for a speed, worked out from how the motor behaves ("feedforward"), instead
 * of waiting for an error to build up like a PID does. The Characterizer finds the numbers.
 *
 * <pre>{@code
 * Feedforward ff = new Feedforward(0.06, 0.00037, 0.00004);   // from the Characterizer's results
 * double power = ff.power(1500, 0);          // the power for 1500 counts per second, not speeding up
 * lift.setPower(liftFf.power(800, 0) + pid.calculate(target, lift.getCurrentPosition()));   // plus a PID for the rest
 * }</pre>
 *
 * <p>power = kS x sign(speed) + kV x speed + kA x acceleration + kG
 * <ul>
 *   <li>kS: the power just to start moving (friction).</li>
 *   <li>kV: the power for each count per second of speed.</li>
 *   <li>kA: the power for each count per second, per second, of speeding up.</li>
 *   <li>kG: the power that holds a lift against gravity (0 for wheels and flywheels).</li>
 * </ul>
 * Powers are at 12.8 V (a full battery): {@link #power(double, double, double)} scales for the battery
 * you have now. Speeds are in encoder counts per second, the same as {@code DcMotorEx.getVelocity()}.
 *
 * Docs: docs/modules/control.md
 */
public final class Feedforward {
    /** The battery voltage the numbers are for. */
    public static final double NOMINAL_VOLTS = 12.8;

    /** Power to start moving (kS), per speed (kV), per acceleration (kA), to hold against gravity (kG). */
    public final double kS, kV, kA, kG;

    /** For wheels and flywheels (no gravity). */
    public Feedforward(double kS, double kV, double kA) {
        this(kS, kV, kA, 0);
    }

    /** For a lift: kG = the power that holds it still against gravity. */
    public Feedforward(double kS, double kV, double kA, double kG) {
        this.kS = kS;
        this.kV = kV;
        this.kA = kA;
        this.kG = kG;
    }

    /** The power for this speed and acceleration (counts per second, and per second per second), at 12.8 V. */
    public double power(double velocity, double acceleration) {
        return kS * Math.signum(velocity) + kV * velocity + kA * acceleration + kG;
    }

    /**
     * The same for the battery you have now: a weaker battery needs more power for the same speed.
     * Not limited to -1..1: clip it yourself (or let setPower do it).
     *
     * @param batteryVolts the battery's voltage now (the hub's voltage sensor)
     */
    public double power(double velocity, double acceleration, double batteryVolts) {
        return power(velocity, acceleration) * NOMINAL_VOLTS / Math.max(6, batteryVolts);
    }

    /** The top speed at full power (counts per second), 0 if kV is not set. */
    public double topSpeed() {
        return kV > 0 ? (1 - kS - kG) / kV : 0;
    }

    @Override
    public String toString() {
        return "Feedforward(kS " + kS + ", kV " + kV + ", kA " + kA + ", kG " + kG + ")";
    }
}
