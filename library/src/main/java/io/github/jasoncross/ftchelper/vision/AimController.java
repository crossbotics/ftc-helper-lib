package io.github.jasoncross.ftchelper.vision;

import io.github.jasoncross.ftchelper.util.MathUtil;
import java.util.function.LongSupplier;

/**
 * Turns a camera target into a turn power, safely: no target, an old picture, or no bearing all
 * give 0 (the robot never turns toward a target it cannot see now). (Last season's TagAimController.)
 *
 * <p>It is a small "PD" controller. P (proportional): the farther off the target is, the harder
 * it turns. D (derivative): if the error is shrinking fast, it eases off so the robot does not
 * swing past the target. Tutorial 07 (docs/tutorials/07-pid-lift.md) explains P, I and D step by step,
 * and Tutorial 18 (docs/tutorials/18-vision-aim.md) uses this class.
 *
 * <pre>{@code
 * AimController aim = new AimController(0.02, 0.003)   // P and D on the bearing (degrees)
 *         .deadband(1.5)                               // close enough
 *         .maxPower(0.5)
 *         .maxAgeMs(150);                              // ignore pictures older than this
 * // every loop, while the driver holds the aim button:
 * drive.drive(forward, strafe, aim.turnPower(camera.target(20)));   // ready for the drive's turn
 * }</pre>
 *
 * For a turret, use {@code turret.aimAtTarget(target, cameraOnTurret)} instead.
 * Docs: docs/modules/vision.md
 */
public class AimController {
    // "final": set once in the constructor and never changed afterwards.
    private final double kP, kD;
    private double deadbandDeg = 1.5;
    private double maxPower = 0.6;
    private double maxAgeMs = 150;
    private double offsetDeg = 0;
    // Where we read the time from. Normally the real clock; tests hand in a fake clock they control.
    private final LongSupplier clockNanos;

    // Memory of the last picture, used for the D part. Long.MIN_VALUE / NaN mean "no picture yet".
    private long lastFrame = Long.MIN_VALUE;
    private double lastError = Double.NaN;
    private double lastDerivative = 0;
    private boolean aimed = false;

    /**
     * Makes an aim controller.
     *
     * @param kP turn power per degree of error (e.g. 0.02: 10 degrees off gives 0.2 power)
     * @param kD turn power per (degree per second) of change in the error; 0 turns the D part off
     */
    public AimController(double kP, double kD) {
        // System::nanoTime is a "method reference": we hand over the clock itself, to be read later
        this(kP, kD, System::nanoTime);
    }

    /** For tests: the same, with a clock you control (nanoseconds). */
    public AimController(double kP, double kD, LongSupplier clockNanos) {
        this.kP = kP;
        this.kD = kD;
        this.clockNanos = clockNanos;
    }

    /**
     * Errors smaller than this (degrees) give 0 power and count as aimed. Default 1.5.
     * Without it the robot would twitch back and forth around the target forever.
     * Returns this controller, so you can chain settings: {@code .deadband(1.5).maxPower(0.5)}.
     */
    public AimController deadband(double deg) {
        deadbandDeg = Math.abs(deg);
        return this;
    }

    /** The most turn power the aim may use, 0 to 1. Default 0.6. Lower = gentler, slower aiming. */
    public AimController maxPower(double p) {
        maxPower = Math.abs(p);
        return this;
    }

    /** Pictures older than this are ignored (milliseconds). Default 150. */
    public AimController maxAgeMs(double ms) {
        maxAgeMs = Math.abs(ms);
        return this;
    }

    /**
     * Where the target should sit in the camera's view when the LAUNCHER points straight at it, in
     * degrees (positive = the target appears left of the camera's center). Use it when the camera is
     * not centered on the launcher. Example: a camera mounted left of the launcher sees the target a
     * little to its right when the launcher is aimed, so the offset is negative. Find it with the aim
     * tuner (docs/modules/finders.md). Default 0.
     */
    public AimController offset(double deg) {
        offsetDeg = deg;
        return this;
    }

    /**
     * The turn power toward the target, CLOCKWISE positive: the same sign as every drive's turn
     * ({@code drive.drive(f, s, aim.turnPower(t))}, {@code tank.arcade(f, aim.turnPower(t))}).
     * 0 when there is no fresh target. Call it every loop while aiming.
     *
     * @param target the target from the camera, e.g. {@code camera.target(20)}; null is fine (gives 0)
     */
    public double turnPower(TargetObservation target) {
        // The math below works counterclockwise-positive (like the bearing and the IMU).
        // The drives want clockwise-positive turns, so we flip the sign. See docs/conventions.md.
        return -ccwPower(target);
    }

    /**
     * The same power, COUNTERCLOCKWISE positive (the IMU's sign), e.g. for your own heading math.
     * The D part only changes on a NEW picture, so a repeated picture never fakes motion.
     */
    public double ccwPower(TargetObservation target) {
        long now = clockNanos.getAsLong();
        // Safety first: never aim at a target we cannot see NOW. No target, no bearing, or a picture
        // older than maxAgeMs -> forget the past and give 0, so the robot stops turning.
        if (target == null || !target.hasBearing() || !target.isFresh(now, maxAgeMs)) {
            reset();
            return 0;
        }
        // The error: how many degrees we still have to turn (positive = turn left).
        double error = target.bearingDeg - offsetDeg;
        // The camera sends fewer pictures than our loop runs, so we often see the SAME picture again.
        // Only work out the D part when the picture is new; otherwise keep the last value.
        if (target.timeNanos != lastFrame) {
            // We need an earlier picture to measure how fast the error changes.
            if (!Double.isNaN(lastError) && lastFrame != Long.MIN_VALUE) {
                // dt = seconds between the two pictures (1e9 nanoseconds in a second)
                double dt = (target.timeNanos - lastFrame) / 1e9;
                // degrees per second; "a ? b : c" = if a then b else c (guards against dividing by 0)
                lastDerivative = dt > 0 ? (error - lastError) / dt : 0;
            }
            lastFrame = target.timeNanos;
            lastError = error;
        }
        // Close enough: stop turning, and remember that we are aimed.
        aimed = Math.abs(error) <= deadbandDeg;
        if (aimed) return 0;
        // P + D, then clamp (keep it between -maxPower and +maxPower) so it never turns too hard.
        return MathUtil.clamp(kP * error + kD * lastDerivative, -maxPower, maxPower);
    }

    /** True if the last fresh target was inside the deadband. */
    public boolean isAimed() {
        return aimed;
    }

    /**
     * Forgets the past (call when the driver lets go of the aim button). Without this, the D part
     * would compare the next picture with one from long ago and give a wrong kick.
     */
    public void reset() {
        lastFrame = Long.MIN_VALUE;
        lastError = Double.NaN;
        lastDerivative = 0;
        aimed = false;
    }
}
