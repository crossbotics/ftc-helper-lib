package com.crossbotics.ftchelper.tuning;

import com.crossbotics.ftchelper.control.PidController;

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
 * <p>What the four gains do: see {@link PidController} and Tutorial 07 (docs/tutorials/07-pid-lift.md).
 *
 * Docs: docs/guides/tuning.md
 */
public final class PidTunables {
    /** The four gains, each one changeable from the {@link TuningMenu}. "final": set once, never replaced. */
    public final Tunable kP, kI, kD, kF;

    // Private: make one with PidTunables.of(...).
    private PidTunables(Tunable kP, Tunable kI, Tunable kD, Tunable kF) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
        this.kF = kF;
    }

    /**
     * Makes the four tunables "group/kP" ... "group/kF".
     *
     * @param group the name shown in the menu, e.g. "Lift"
     * @param kP    the P gain in your code
     * @param kI    the I gain in your code
     * @param kD    the D gain in your code
     * @param kF    the feedforward in your code
     * @return the four tunables together
     */
    public static PidTunables of(String group, double kP, double kI, double kD, double kF) {
        // Each gain gets its own step size. The fallbacks are used when a gain starts at 0
        // (1/8 of 0 would be a step of 0, and the D-pad could never change it).
        return new PidTunables(
                Tunable.number(group + "/kP", kP, stepFor(kP, 0.001)),
                Tunable.number(group + "/kI", kI, stepFor(kI, 0.0001)),
                Tunable.number(group + "/kD", kD, stepFor(kD, 0.0001)),
                Tunable.number(group + "/kF", kF, stepFor(kF, 0.01)));
    }

    /**
     * Copies the current values into the controller. Call every loop, so a change in the menu
     * is felt on the robot right away.
     *
     * @param pid the controller to update
     */
    public void applyTo(PidController pid) {
        pid.setGains(kP.get(), kI.get(), kD.get(), kF.get());
    }

    // A step that suits the size of the number: 1/8 of it, so about 8 presses doubles or zeroes it.
    // "a ? b : c" is a short if/else: use the fallback when the value is 0.
    private static double stepFor(double value, double fallback) {
        return value == 0 ? fallback : Math.abs(value) / 8;
    }
}
