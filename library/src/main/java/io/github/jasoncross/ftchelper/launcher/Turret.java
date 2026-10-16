package io.github.jasoncross.ftchelper.launcher;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.TouchSensor;
import io.github.jasoncross.ftchelper.auto.AutoStep;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.hardware.Hw;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.safety.SoftLimits;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.util.Angles;
import io.github.jasoncross.ftchelper.util.MathUtil;
import java.util.function.BooleanSupplier;

/**
 * A turret: turns to an angle in degrees, never past its limits (so cables never wind up),
 * and can hold a FIELD direction while the robot drives and turns.
 *
 * <pre>{@code
 * Turret turret = Turret.create(hardwareMap, "turret")
 *         .countsPerDegree(1479 / 360.0)            // encoder counts per turret degree
 *         .limits(-190, 190)                        // degrees; 0 = straight forward
 *         .homeSwitch(hardwareMap, "turret_home", 45)   // switch closes at 45 deg
 *         .pid(0.02, 0, 0.001, 0)
 *         .tolerance(1.0);
 *
 * turret.aimAt(30);                                  // robot-relative, counterclockwise +
 * turret.aimField(90, ImuSetup.headingDeg(imu));     // hold field direction 90 while driving
 * turret.aimBy(target.bearingDeg);                   // vision: turn by the target's bearing
 * }</pre>
 *
 * <p>Angles: 0 = robot forward, counterclockwise (left) is positive, like the IMU. Positive power
 * must turn the turret counterclockwise (use {@link #reversed()} if not).
 *
 * <p><b>The safe way round:</b> with limits like -190..190 there can be two ways to reach an angle
 * (e.g. 170 and -190 are the same direction). The turret picks the one inside the limits closest to
 * where it is, and always moves there WITHOUT passing a limit. Asked for something outside the
 * limits, it goes to the nearest limit and {@link #isOutOfRange()} turns true.
 *
 * <p><b>Homing:</b> with a home switch, the turret does not know its angle until the switch is
 * seen once. Call {@link #home(double)} (it turns slowly until the switch). Without a switch, it
 * trusts {@link #startAngle} (the angle when the robot was turned on).
 *
 * Docs: docs/modules/launcher.md
 */
public class Turret implements Subsystem {
    private final String name;
    private final DcMotorEx motor;
    private final PidController pid = new PidController(0.02, 0, 0.001, 0);

    private double countsPerDegree = 1;
    private double minDeg = -180, maxDeg = 180;
    private double toleranceDeg = 1.0;
    private double maxPower = 0.8;
    private double slowZoneDeg = 10, slowZonePower = 0.25;
    private BooleanSupplier homeSwitch = null;
    private double homeAngle = 0;
    private double homingPower = 0.2;

    private double offsetDeg = 0;           // angle = counts / countsPerDegree + offset
    private boolean homed = true;
    private boolean homing = false;
    private double homingDirection = 1;
    private boolean switchWas = false;

    private double targetDeg = Double.NaN;
    private boolean outOfRange = false;
    private double manualPower = 0;
    private double sentPower = Double.NaN;

    public static Turret create(HardwareMap hardwareMap, String name) {
        return new Turret(name, MotorSetup.get(hardwareMap, name).brake().resetEncoder().runWithoutEncoder().done());
    }

    public Turret(String name, DcMotorEx motor) {
        this.name = name;
        this.motor = motor;
        pid.setAngleMode(false);   // we pick the legal target ourselves; plain error is correct
    }

    public Turret reversed() {
        MotorSetup.of(motor).reversed().brake().resetEncoder().runWithoutEncoder().done();
        return this;
    }

    /** Encoder counts per degree of TURRET rotation (motor counts x gear ratio / 360). */
    public Turret countsPerDegree(double counts) {
        countsPerDegree = counts;
        return this;
    }

    /** The allowed range, degrees. May be wider than 360 total (e.g. -190..190). */
    public Turret limits(double min, double max) {
        minDeg = Math.min(min, max);
        maxDeg = Math.max(min, max);
        return this;
    }

    /** The turret's angle when the robot is turned on (used when there is no home switch). Default 0. */
    public Turret startAngle(double deg) {
        offsetDeg = deg - rawDeg();
        return this;
    }

    /** A home switch by configuration name (touch sensor), closing at {@code angleDeg}. Skipped if missing. */
    public Turret homeSwitch(HardwareMap hardwareMap, String switchName, double angleDeg) {
        TouchSensor s = Hw.tryGet(hardwareMap, TouchSensor.class, switchName);
        if (s != null) homeSwitch(s::isPressed, angleDeg);
        return this;
    }

    /** A home switch read your own way (magnetic: {@code () -> !channel.getState()}). */
    public Turret homeSwitch(BooleanSupplier isClosed, double angleDeg) {
        homeSwitch = isClosed;
        homeAngle = angleDeg;
        homed = false;
        return this;
    }

    /** Power while homing, 0 to 1. Default 0.2. */
    public Turret homingPower(double p) {
        homingPower = Math.abs(p);
        return this;
    }

    public Turret pid(double kP, double kI, double kD, double kF) {
        pid.setGains(kP, kI, kD, kF);
        return this;
    }

    /** Aimed when within this many degrees. Default 1. */
    public Turret tolerance(double deg) {
        toleranceDeg = Math.abs(deg);
        return this;
    }

    public Turret maxPower(double p) {
        maxPower = MathUtil.clamp(Math.abs(p), 0, 1);
        return this;
    }

    /** Slow down within {@code deg} of a limit. Default 10 deg, 0.25 power. */
    public Turret slowZone(double deg, double powerAtLimit) {
        slowZoneDeg = Math.abs(deg);
        slowZonePower = powerAtLimit;
        return this;
    }

    // ---------------------------------------------------------------------------------------

    /** Turns to a robot-relative angle (0 = forward, counterclockwise +), the safe way round. */
    public void aimAt(double robotDeg) {
        manualPower = 0;
        targetDeg = legalTarget(robotDeg);
    }

    /**
     * Points at a FIELD direction, whatever way the robot faces. Call it every loop with the
     * current heading so it keeps pointing there while the robot turns.
     *
     * @param fieldDeg      field direction, counterclockwise from where heading 0 points
     * @param headingCcwDeg the robot's IMU heading
     */
    public void aimField(double fieldDeg, double headingCcwDeg) {
        aimAt(fieldDeg - headingCcwDeg);
    }

    /** Turns BY an amount from where it is now (e.g. a camera's bearing to a target). */
    public void aimBy(double deltaDeg) {
        aimAt(getAngle() + deltaDeg);
    }

    /** Manual control (a stick), -1 to 1. Limits still apply. 0 = hold where it is. */
    public void manual(double power) {
        if (power == 0 && manualPower != 0) targetDeg = homed ? getAngle() : Double.NaN;
        manualPower = power;
    }

    /**
     * Starts homing: turns slowly toward the switch until it closes.
     *
     * @param direction +1 = counterclockwise, -1 = clockwise (the way the switch is)
     */
    public void home(double direction) {
        if (homeSwitch == null) return;
        homing = true;
        homingDirection = Math.signum(direction) == 0 ? 1 : Math.signum(direction);
        targetDeg = Double.NaN;
    }

    @Override
    public void update() {
        boolean switchNow = homeSwitch != null && homeSwitch.getAsBoolean();
        if (switchNow && !switchWas) {
            // Seen the switch: now we know the angle exactly.
            offsetDeg = homeAngle - rawDeg();
            homed = true;
            if (homing) {
                homing = false;
                targetDeg = homeAngle;
                pid.reset();
            }
        }
        switchWas = switchNow;

        double power;
        if (homing) {
            power = homingPower * homingDirection;
        } else if (manualPower != 0) {
            power = manualPower * maxPower;
        } else if (!homed || Double.isNaN(targetDeg)) {
            power = 0;
        } else {
            power = pid.calculate(targetDeg, getAngle());
        }
        power = MathUtil.clamp(power, -maxPower, maxPower);
        if (homed && !homing) {
            power = SoftLimits.limit(power, getAngle(), minDeg, maxDeg, slowZoneDeg, slowZonePower, false, false);
        }
        write(power);
    }

    @Override
    public void stop() {
        homing = false;
        manualPower = 0;
        targetDeg = Double.NaN;
        write(0);
    }

    // ---------------------------------------------------------------------------------------

    /** The angle now (degrees, counterclockwise +). Meaningless until homed. */
    public double getAngle() {
        return rawDeg() + offsetDeg;
    }

    /** The angle it is going to (NaN if none). */
    public double getTargetAngle() {
        return targetDeg;
    }

    /** True when homed, holding a target, and within the tolerance. */
    public boolean isAimed() {
        return homed && !homing && !Double.isNaN(targetDeg) && Math.abs(targetDeg - getAngle()) <= toleranceDeg;
    }

    /** True if the last aim was outside the limits (it went to the nearest limit instead). */
    public boolean isOutOfRange() { return outOfRange; }

    public boolean isHomed() { return homed; }
    public boolean isHoming() { return homing; }
    @Override public String name() { return name; }

    /** A step: turn to a robot-relative angle and wait until aimed. */
    public AutoStep aimStep(double robotDeg, double timeoutSec) {
        return ctx -> {
            aimAt(robotDeg);
            ctx.waitUntil(this::isAimed, timeoutSec);
        };
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!homed) t.warn(name + " not homed");
        if (outOfRange) t.warn(name + ": target out of range");
        t.line(name, "%.1f deg%s", getAngle(), isAimed() ? "  AIMED" : "");
        t.debug(name + " target", "%.1f deg", targetDeg);
    }

    // ---------------------------------------------------------------------------------------

    /** The equal direction (angle + k*360) inside the limits closest to the current angle. */
    double legalTarget(double wantedDeg) {
        double reference = getAngle();
        double best = Double.NaN;
        double base = reference + Angles.shortestDiff(wantedDeg, reference);
        for (int k = -2; k <= 2; k++) {
            double candidate = base + 360.0 * k;
            if (candidate >= minDeg - 1e-9 && candidate <= maxDeg + 1e-9) {
                if (Double.isNaN(best) || Math.abs(candidate - reference) < Math.abs(best - reference)) best = candidate;
            }
        }
        if (!Double.isNaN(best)) {
            outOfRange = false;
            return best;
        }
        outOfRange = true;
        // Nearest limit, measured around the circle.
        double toMin = Math.abs(Angles.shortestDiff(wantedDeg, minDeg));
        double toMax = Math.abs(Angles.shortestDiff(wantedDeg, maxDeg));
        return toMin <= toMax ? minDeg : maxDeg;
    }

    private double rawDeg() {
        return motor.getCurrentPosition() / countsPerDegree;
    }

    private void write(double power) {
        if (power != sentPower) {
            motor.setPower(power);
            sentPower = power;
        }
    }
}
