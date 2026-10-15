package org.octobots.ftchelper.control;

import java.util.function.LongSupplier;
import org.octobots.ftchelper.util.Angles;
import org.octobots.ftchelper.util.MathUtil;

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
 * Tuning steps are in docs/modules/control.md.
 */
public class PidController {
    private double kP, kI, kD, kF;
    private double minOutput = -1, maxOutput = 1;
    private double integralLimit = Double.POSITIVE_INFINITY;
    private double tolerance = 0;
    private boolean angleMode = false;

    private final LongSupplier clockNanos;
    private long lastNanos = -1;
    private double lastError = 0;
    private double integral = 0;
    private double lastErrorRead = Double.NaN;

    /**
     * Makes a controller with no feedforward.
     *
     * @param kP proportional gain (start small, e.g. 0.01)
     * @param kI integral gain (start at 0)
     * @param kD derivative gain (start at 0)
     */
    public PidController(double kP, double kI, double kD) {
        this(kP, kI, kD, 0);
    }

    /**
     * Makes a controller with a fixed feedforward push.
     *
     * @param kF power added every time (same sign always), e.g. 0.1 to hold up a lift
     */
    public PidController(double kP, double kI, double kD, double kF) {
        this(kP, kI, kD, kF, System::nanoTime);
    }

    /** Makes a controller with your own clock. Used by tests. */
    public PidController(double kP, double kI, double kD, double kF, LongSupplier clockNanos) {
        setGains(kP, kI, kD, kF);
        this.clockNanos = clockNanos;
    }

    /** Changes the gains (for live tuning). Does not reset the controller. */
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
     */
    public PidController setOutputLimits(double min, double max) {
        this.minOutput = Math.min(min, max);
        this.maxOutput = Math.max(min, max);
        return this;
    }

    /**
     * Limits how big the I part can grow. Stops "integral windup": a big build-up that
     * makes the mechanism overshoot after it was stuck. Default: no limit.
     *
     * @param maxIntegralOutput the largest power the I part can add, e.g. 0.3
     */
    public PidController setIntegralLimit(double maxIntegralOutput) {
        this.integralLimit = Math.abs(maxIntegralOutput);
        return this;
    }

    /** How close counts as "at target" for {@link #atTarget()}. Same units as your measurement. */
    public PidController setTolerance(double tolerance) {
        this.tolerance = Math.abs(tolerance);
        return this;
    }

    /**
     * Turn on for headings in degrees. The error then takes the short way around the circle
     * (from 170 to -170 is +20, not -340).
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
        double error = angleMode ? Angles.shortestDiff(target, measured) : target - measured;
        lastErrorRead = error;
        long now = clockNanos.getAsLong();
        double dt = lastNanos < 0 ? 0 : (now - lastNanos) / 1e9;
        lastNanos = now;

        double derivative = 0;
        if (dt > 0) {
            double change = error - lastError;
            // In angle mode, crossing the -180/180 seam must not look like a huge jump.
            if (angleMode) change = Angles.wrapDegrees(change);
            derivative = change / dt;
            if (kI != 0) {
                integral += error * dt;
                if (Double.isFinite(integralLimit)) {
                    double maxIntegral = integralLimit / Math.abs(kI);
                    integral = MathUtil.clamp(integral, -maxIntegral, maxIntegral);
                }
            }
        }
        lastError = error;

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

    /** Clears the memory (I and D). Call when you set a new target or start the mechanism again. */
    public void reset() {
        lastNanos = -1;
        lastError = 0;
        integral = 0;
        lastErrorRead = Double.NaN;
    }

    public double getKP() { return kP; }
    public double getKI() { return kI; }
    public double getKD() { return kD; }
    public double getKF() { return kF; }
}
