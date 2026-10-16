package io.github.jasoncross.ftchelper.vision;

import io.github.jasoncross.ftchelper.util.MathUtil;
import java.util.function.LongSupplier;

/**
 * Turns a camera target into a turn power, safely: no target, an old picture, or no bearing all
 * give 0 (the robot never turns toward a target it cannot see now). (Last season's TagAimController.)
 *
 * <pre>{@code
 * AimController aim = new AimController(0.02, 0.003)   // P and D on the bearing (degrees)
 *         .deadband(1.5)                               // close enough
 *         .maxPower(0.5)
 *         .maxAgeMs(150);                              // ignore pictures older than this
 * // every loop, while the driver holds the aim button:
 * double ccw = aim.turnPower(camera.target(20));
 * drive.drive(forward, strafe, -ccw);                  // the drive's turn is clockwise: flip
 * }</pre>
 *
 * For a turret, you usually do not need this: {@code turret.aimBy(target.bearingDeg)}.
 * Docs: docs/modules/vision.md
 */
public class AimController {
    private final double kP, kD;
    private double deadbandDeg = 1.5;
    private double maxPower = 0.6;
    private double maxAgeMs = 150;
    private double offsetDeg = 0;
    private final LongSupplier clockNanos;

    private long lastFrame = Long.MIN_VALUE;
    private double lastError = Double.NaN;
    private double lastDerivative = 0;
    private boolean aimed = false;

    public AimController(double kP, double kD) {
        this(kP, kD, System::nanoTime);
    }

    /** For tests. */
    public AimController(double kP, double kD, LongSupplier clockNanos) {
        this.kP = kP;
        this.kD = kD;
        this.clockNanos = clockNanos;
    }

    /** Errors smaller than this (degrees) give 0 power and count as aimed. Default 1.5. */
    public AimController deadband(double deg) {
        deadbandDeg = Math.abs(deg);
        return this;
    }

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
     * Aim this many degrees to the side of the target (positive = left), e.g. when the camera is not
     * centered on the launcher.
     */
    public AimController offset(double deg) {
        offsetDeg = deg;
        return this;
    }

    /**
     * The turn power toward the target, COUNTERCLOCKWISE positive. 0 when there is no fresh target.
     * The D part only changes on a NEW picture, so a repeated picture never fakes motion.
     */
    public double turnPower(TargetObservation target) {
        long now = clockNanos.getAsLong();
        if (target == null || !target.hasBearing() || !target.isFresh(now, maxAgeMs)) {
            reset();
            return 0;
        }
        double error = target.bearingDeg - offsetDeg;
        if (target.timeNanos != lastFrame) {
            if (!Double.isNaN(lastError) && lastFrame != Long.MIN_VALUE) {
                double dt = (target.timeNanos - lastFrame) / 1e9;
                lastDerivative = dt > 0 ? (error - lastError) / dt : 0;
            }
            lastFrame = target.timeNanos;
            lastError = error;
        }
        aimed = Math.abs(error) <= deadbandDeg;
        if (aimed) return 0;
        return MathUtil.clamp(kP * error + kD * lastDerivative, -maxPower, maxPower);
    }

    /** True if the last fresh target was inside the deadband. */
    public boolean isAimed() {
        return aimed;
    }

    /** Forgets the past (call when the driver lets go of the aim button). */
    public void reset() {
        lastFrame = Long.MIN_VALUE;
        lastError = Double.NaN;
        lastDerivative = 0;
        aimed = false;
    }
}
