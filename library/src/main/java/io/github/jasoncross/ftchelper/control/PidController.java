package io.github.jasoncross.ftchelper.control;

import java.util.function.LongSupplier;
import io.github.jasoncross.ftchelper.util.Angles;
import io.github.jasoncross.ftchelper.util.MathUtil;

/**
 * A PID controller: it works out how much power to give so a value reaches a target.
 *
 * <p>PID stands for Proportional, Integral, Derivative:
 * <ul>
 *   <li><b>P</b> pushes harder when you are far from the target.</li>
 *   <li><b>I</b> adds up small errors that last a long time (for example gravity on a lift).</li>
 *   <li><b>D</b> slows the push when you move toward the target fast, so you do not overshoot.</li>
 *   <li><b>F</b> (feedforward) is a fixed extra push, for example to hold an arm up.</li>
 * </ul>
 *
 * <p>The "error" is how far you still have to go: target minus where you are now.
 * The "gains" (kP, kI, kD, kF) are the numbers you tune: how strongly each part pushes.
 *
 * <pre>{@code
 * PidController liftPid = new PidController(0.005, 0, 0.0002)
 *         .setOutputLimits(-0.6, 1.0)
 *         .setTolerance(15);
 * // every loop:
 * double power = liftPid.calculate(targetTicks, lift.getCurrentPosition());
 * lift.setPower(power);
 * if (liftPid.atTarget()) { ... }
 * }</pre>
 *
 * Tuning steps are in docs/modules/control.md. Tutorial 07 (docs/tutorials/07-pid-lift.md)
 * explains PID step by step on a lift.
 */
public class PidController {
    // ---- Settings (you choose these) ----
    // The gains. "k" is the usual math name for a fixed number you choose.
    private double kP, kI, kD, kF;
    // The output is always kept inside these (motor power is -1 to 1).
    private double minOutput = -1, maxOutput = 1;
    // The most power the I part may add. "Infinity" means no limit.
    private double integralLimit = Double.POSITIVE_INFINITY;
    // How close counts as "at target", in the same units as your measurement.
    private double tolerance = 0;
    // True for headings in degrees, where 179 and -179 are only 2 degrees apart.
    private boolean angleMode = false;

    // ---- Memory (changes every loop) ----
    // A clock in nanoseconds (billionths of a second). "final": set once and never changed.
    // Tests hand in a fake clock so they do not have to really wait.
    private final LongSupplier clockNanos;
    // Time of the last calculate(), or -1 for "no call yet".
    private long lastNanos = -1;
    // The error at the last calculate(), to see how fast it is changing (the D part).
    private double lastError = 0;
    // The running total of error x time (the I part).
    private double integral = 0;
    // The last error, for getLastError() and atTarget(). NaN ("not a number") = no call yet.
    private double lastErrorRead = Double.NaN;

    /**
     * Makes a controller with no feedforward.
     *
     * @param kP proportional gain (start small, e.g. 0.01)
     * @param kI integral gain (start at 0)
     * @param kD derivative gain (start at 0)
     */
    public PidController(double kP, double kI, double kD) {
        // "this(...)" hands the work to the next constructor, with kF = 0.
        this(kP, kI, kD, 0);
    }

    /**
     * Makes a controller with a fixed feedforward push.
     *
     * @param kP proportional gain
     * @param kI integral gain
     * @param kD derivative gain
     * @param kF power added every time (same sign always), e.g. 0.1 to hold up a lift
     */
    public PidController(double kP, double kI, double kD, double kF) {
        // "System::nanoTime" hands over the real clock method itself (a "method reference").
        this(kP, kI, kD, kF, System::nanoTime);
    }

    /**
     * Makes a controller with your own clock. Used by tests.
     *
     * @param clockNanos gives the current time in nanoseconds
     */
    public PidController(double kP, double kI, double kD, double kF, LongSupplier clockNanos) {
        setGains(kP, kI, kD, kF);
        this.clockNanos = clockNanos;
    }

    /**
     * Changes the gains (for live tuning). Does not reset the controller.
     *
     * @return this controller, so you can chain more settings
     */
    public PidController setGains(double kP, double kI, double kD, double kF) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
        this.kF = kF;
        return this;
    }

    /**
     * Limits the output. Default -1 to 1 (motor power range).
     * Example: {@code setOutputLimits(-0.5, 1)} lets a lift go down at half power only.
     *
     * @param min the lowest output (power), e.g. -1
     * @param max the highest output (power), e.g. 1
     * @return this controller, so you can chain more settings
     */
    public PidController setOutputLimits(double min, double max) {
        // Math.min/Math.max: works even if the two numbers are typed in the wrong order.
        this.minOutput = Math.min(min, max);
        this.maxOutput = Math.max(min, max);
        return this;
    }

    /**
     * Limits how big the I part can grow. Stops "integral windup": a big build-up that
     * makes the mechanism overshoot after it was stuck. Default: no limit.
     *
     * @param maxIntegralOutput the largest power the I part can add, e.g. 0.3
     * @return this controller, so you can chain more settings
     */
    public PidController setIntegralLimit(double maxIntegralOutput) {
        // Only the size matters; the limit works the same both ways.
        this.integralLimit = Math.abs(maxIntegralOutput);
        return this;
    }

    /**
     * How close counts as "at target" for {@link #atTarget()}. Same units as your measurement.
     *
     * @param tolerance for example 15 (encoder ticks) or 2 (degrees)
     * @return this controller, so you can chain more settings
     */
    public PidController setTolerance(double tolerance) {
        this.tolerance = Math.abs(tolerance);
        return this;
    }

    /**
     * Turn on for headings in degrees. The error then takes the short way around the circle
     * (from 170 to -170 is +20, not -340).
     *
     * @param degrees true for headings in degrees, false for normal values (the default)
     * @return this controller, so you can chain more settings
     */
    public PidController setAngleMode(boolean degrees) {
        this.angleMode = degrees;
        return this;
    }

    /**
     * Works out the output. Call once per loop.
     *
     * @param target   where you want to be
     * @param measured where you are now
     * @return the power to apply, inside the output limits
     */
    public double calculate(double target, double measured) {
        // The error: how far we still have to go. "a ? b : c" is a short if/else:
        // for headings, take the short way around the circle; otherwise just subtract.
        double error = angleMode ? Angles.shortestDiff(target, measured) : target - measured;
        lastErrorRead = error;
        // How many seconds since the last call (1 second = 1e9 nanoseconds).
        // On the first call there is no "last time", so we use 0 and skip the I and D parts.
        long now = clockNanos.getAsLong();
        double dt = lastNanos < 0 ? 0 : (now - lastNanos) / 1e9;
        lastNanos = now;

        double derivative = 0;
        // Only when some time has passed: dividing by 0 seconds would give a nonsense number.
        if (dt > 0) {
            // D part: how fast the error is changing, per second.
            double change = error - lastError;
            // In angle mode, crossing the -180/180 seam must not look like a huge jump.
            if (angleMode) change = Angles.wrapDegrees(change);
            derivative = change / dt;
            // I part: add up error x time. Skipped when kI is 0, so the total does not grow
            // in secret and then kick in when someone turns kI on while tuning.
            if (kI != 0) {
                integral += error * dt;
                if (Double.isFinite(integralLimit)) {
                    // The limit is in POWER (kI x integral), so turn it into a limit on the
                    // total itself by dividing by kI. This stops windup.
                    double maxIntegral = integralLimit / Math.abs(kI);
                    integral = MathUtil.clamp(integral, -maxIntegral, maxIntegral);
                }
            }
        }
        lastError = error;

        // Add up the four parts, then keep the answer inside the output limits so the motor
        // never gets more than it can take (or more than you allowed).
        double output = kP * error + kI * integral + kD * derivative + kF;
        return MathUtil.clamp(output, minOutput, maxOutput);
    }

    /** The error from the last {@link #calculate} call (target minus measured). NaN before the first call. */
    public double getLastError() {
        return lastErrorRead;
    }

    /** True if the last error was within the tolerance. False before the first call. */
    public boolean atTarget() {
        return !Double.isNaN(lastErrorRead) && Math.abs(lastErrorRead) <= tolerance;
    }

    /**
     * Clears the memory (I and D). Call when you set a new target or start the mechanism again.
     * Without this, the I total built up for the old target would push the mechanism the wrong way.
     */
    public void reset() {
        lastNanos = -1;
        lastError = 0;
        integral = 0;
        lastErrorRead = Double.NaN;
    }

    /** The P gain now in use. */
    public double getKP() { return kP; }
    /** The I gain now in use. */
    public double getKI() { return kI; }
    /** The D gain now in use. */
    public double getKD() { return kD; }
    /** The feedforward (F) now in use. */
    public double getKF() { return kF; }
}
