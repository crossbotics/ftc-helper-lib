package io.github.jasoncross.ftchelper.tuning;

import io.github.jasoncross.ftchelper.control.PidController;

/**
 * The four PID gains of one controller as {@link Tunable}s, in one line.
 *
 * <pre>{@code
 * static final PidTunables LIFT = PidTunables.of("Lift", 0.004, 0, 0.0002, 0.05);
 * // every loop:
 * LIFT.applyTo(liftPid);
 * }</pre>
 *
 * The menu then shows "Lift/kP", "Lift/kI", "Lift/kD" and "Lift/kF". Steps are 1/8 of each
 * starting value (or small defaults when a value is 0).
 *
 * Docs: docs/guides/tuning.md
 */
public final class PidTunables {
    public final Tunable kP, kI, kD, kF;

    private PidTunables(Tunable kP, Tunable kI, Tunable kD, Tunable kF) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
        this.kF = kF;
    }

    /** Makes the four tunables "group/kP" ... "group/kF". */
    public static PidTunables of(String group, double kP, double kI, double kD, double kF) {
        return new PidTunables(
                Tunable.number(group + "/kP", kP, stepFor(kP, 0.001)),
                Tunable.number(group + "/kI", kI, stepFor(kI, 0.0001)),
                Tunable.number(group + "/kD", kD, stepFor(kD, 0.0001)),
                Tunable.number(group + "/kF", kF, stepFor(kF, 0.01)));
    }

    /** Copies the current values into the controller. Call every loop. */
    public void applyTo(PidController pid) {
        pid.setGains(kP.get(), kI.get(), kD.get(), kF.get());
    }

    private static double stepFor(double value, double fallback) {
        return value == 0 ? fallback : Math.abs(value) / 8;
    }
}
