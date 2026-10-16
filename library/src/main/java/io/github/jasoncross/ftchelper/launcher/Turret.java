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
import java.util.function.LongSupplier;

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
 * must turn the turret counterclockwise (use {@link #reversed()} if not). The signs are in
 * docs/conventions.md.
 *
 * <p><b>The safe way round:</b> with limits like -190..190 there can be two ways to reach an angle
 * (e.g. 170 and -190 are the same direction). The turret picks the one inside the limits closest to
 * where it is, and always moves there WITHOUT passing a limit. Asked for something outside the
 * limits, it goes to the nearest limit and {@link #isOutOfRange()} turns true.
 * (Picture: docs/images/turret-limits.svg.)
 *
 * <p><b>Homing:</b> with a home switch, the turret does not know its angle until the switch is
 * seen once. Call {@link #home(double)} (it turns slowly until the switch). Without a switch, it
 * trusts {@link #startAngle} (the angle when the robot was turned on).
 *
 * <p>Like every helper, the aim commands only set a target; {@link #update()} turns the motor, a little
 * every loop, using a PID controller (tutorial 07 explains PID). Tutorial 18 aims a turret with a camera.
 *
 * Docs: docs/modules/launcher.md
 */
public class Turret implements Subsystem {
    // "implements Subsystem": it has update/stop/addTelemetry/name like every mechanism helper.
    private final String name;
    private final DcMotorEx motor;
    private final PidController pid = new PidController(0.02, 0, 0.001, 0);

    private double countsPerDegree = 1;
    private double minDeg = -180, maxDeg = 180;
    private double toleranceDeg = 1.0;
    private double maxPower = 0.8;
    private double slowZoneDeg = 10, slowZonePower = 0.25;
    private BooleanSupplier homeSwitch = null;   // null = no home switch
    private double homeAngle = 0;                // the turret angle (degrees) where the switch closes
    private double homingPower = 0.2;
    private double homingTimeoutSec = 5.0;
    private double maxAgeMs = 150;
    // Where we get the time (nanoseconds). Tests swap in a fake clock.
    private LongSupplier clockNanos = System::nanoTime;

    private double offsetDeg = 0;           // angle = counts / countsPerDegree + offset
    private boolean homed = true;           // true until a home switch is added: then we must home first
    private boolean homing = false;         // true while turning slowly to find the switch
    private double homingDirection = 1;     // +1 counterclockwise, -1 clockwise
    private boolean switchWas = false;      // the switch last loop, to notice the moment it closes
    private double homingStartedSec = 0;
    private String fault = "";
    // The time stamp of the last camera picture used, so the same picture is never used twice.
    // Long.MIN_VALUE = the smallest possible number: "none used yet".
    private long lastFrameUsed = Long.MIN_VALUE;

    private double targetDeg = Double.NaN;  // where it should point; NaN = no target (no power)
    private boolean outOfRange = false;
    private double manualPower = 0;
    private double sentPower = Double.NaN;  // the power sent last; NaN = nothing sent yet

    /** Gets the motor (brake, encoder reset, plain power) and wraps it. */
    public static Turret create(HardwareMap hardwareMap, String name) {
        // Brake: it stays put when the power is 0. Encoder reset: the angle starts from startAngle.
        // Plain power: our own PID does the aiming.
        return new Turret(name, MotorSetup.get(hardwareMap, name).brake().resetEncoder().runWithoutEncoder().done());
    }

    /** Wraps a motor you already set up (brake, encoder reset). */
    public Turret(String name, DcMotorEx motor) {
        this.name = name;
        this.motor = motor;
        // The PID could take "the short way round" by itself, but that might cross a limit.
        // We pick the safe target in legalTarget(), so the PID should just use the plain difference.
        pid.setAngleMode(false);   // we pick the legal target ourselves; plain error is correct
    }

    /** For tests: use a pretend clock so tests do not wait for real time. */
    Turret withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** Positive power turns the other way. Use it if positive power turns the turret clockwise. */
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
        // Math.min/max: works even if the two numbers are given the wrong way round.
        minDeg = Math.min(min, max);
        maxDeg = Math.max(min, max);
        return this;
    }

    /**
     * The turret's angle when the robot is turned on (used when there is no home switch). Default 0.
     * Call it after {@link #countsPerDegree} and {@link #reversed()}, since it uses the encoder reading.
     */
    public Turret startAngle(double deg) {
        // Choose the offset so that "encoder angle + offset" = deg right now.
        offsetDeg = deg - rawDeg();
        return this;
    }

    /** A home switch by configuration name (touch sensor), closing at {@code angleDeg}. Skipped if missing. */
    public Turret homeSwitch(HardwareMap hardwareMap, String switchName, double angleDeg) {
        // tryGet gives null instead of crashing when the name is missing, so the robot still runs.
        TouchSensor s = Hw.tryGet(hardwareMap, TouchSensor.class, switchName);
        // "s::isPressed" hands over "how to ask the switch", to be asked every loop.
        if (s != null) homeSwitch(s::isPressed, angleDeg);
        else fault = "no switch \"" + switchName + "\" in the configuration (trusting the start angle)";
        return this;
    }

    /**
     * A home switch read your own way (magnetic: {@code () -> !channel.getState()}).
     * {@code () -> ...} is a small piece of code handed over to run later (here: every loop).
     */
    public Turret homeSwitch(BooleanSupplier isClosed, double angleDeg) {
        homeSwitch = isClosed;
        homeAngle = angleDeg;
        // With a switch, the angle is not trusted until the switch has been seen.
        homed = false;
        return this;
    }

    /** Power while homing, 0 to 1. Default 0.2. Also the most manual power before homing. */
    public Turret homingPower(double p) {
        homingPower = Math.abs(p);
        return this;
    }

    /** Homing stops with a fault if the switch is not found within this many seconds. Default 5. */
    public Turret homingTimeout(double seconds) {
        homingTimeoutSec = Math.abs(seconds);
        return this;
    }

    /** {@link #aimAtTarget} ignores camera pictures older than this (milliseconds). Default 150. */
    public Turret maxTargetAgeMs(double ms) {
        maxAgeMs = Math.abs(ms);
        return this;
    }

    /**
     * PID gains (see docs/modules/control.md). They work on degrees of error:
     * kP = push harder the further off it points, kI = push more if it stays a little off,
     * kD = calm it down when it turns fast (stops overshoot), kF = a steady extra push.
     */
    public Turret pid(double kP, double kI, double kD, double kF) {
        pid.setGains(kP, kI, kD, kF);
        return this;
    }

    /** Aimed when within this many degrees. Default 1. */
    public Turret tolerance(double deg) {
        toleranceDeg = Math.abs(deg);
        return this;
    }

    /** The most power it ever gets, 0 to 1. Default 0.8. */
    public Turret maxPower(double p) {
        maxPower = MathUtil.clamp(Math.abs(p), 0, 1);
        return this;
    }

    /** Slow down within {@code deg} of a limit, down to {@code powerAtLimit} (0 to 1). Default 10 deg, 0.25 power. */
    public Turret slowZone(double deg, double powerAtLimit) {
        slowZoneDeg = Math.abs(deg);
        slowZonePower = powerAtLimit;
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Commands (quick: they only set the target; update() turns the motor)
    // ---------------------------------------------------------------------------------------

    /** Turns to a robot-relative angle (0 = forward, counterclockwise +), the safe way round. */
    public void aimAt(double robotDeg) {
        // An aim command takes over from the stick.
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
        // Example: to point at field 90 while the robot faces field 30, turn 60 left of the robot's front.
        aimAt(fieldDeg - headingCcwDeg);
    }

    /** Turns BY an amount from where it is now. For camera targets use {@link #aimAtTarget} instead. */
    public void aimBy(double deltaDeg) {
        aimAt(getAngle() + deltaDeg);
    }

    /**
     * Aims at a camera target, safely: only a FRESH picture is used, and each picture only once
     * (an old or repeated picture would push the turret further and further). No fresh target =
     * the turret holds where it is.
     *
     * @param target         from {@code camera.target(...)} (may be null)
     * @param cameraOnTurret true if the camera turns with the turret, false if it is on the robot body
     * @return true if this picture was used
     */
    public boolean aimAtTarget(io.github.jasoncross.ftchelper.vision.TargetObservation target, boolean cameraOnTurret) {
        long now = clockNanos.getAsLong();
        // No target, no direction to it, or the picture is too old (the robot has moved since): skip it.
        if (target == null || !target.hasBearing() || !target.isFresh(now, maxAgeMs)) return false;
        // Same picture as last time: skip it. With a camera on the turret, "turn BY 10 deg" from the
        // same picture again would turn another 10 deg, past the target.
        if (target.timeNanos == lastFrameUsed) return false;
        lastFrameUsed = target.timeNanos;
        // Camera on the turret: the bearing is measured from where the turret points, so turn BY it.
        // Camera on the body: the bearing is measured from the robot's front, so it IS the robot angle.
        if (cameraOnTurret) aimBy(target.bearingDeg);
        else aimAt(target.bearingDeg);
        return true;
    }

    /**
     * Manual control (a stick), -1 to 1. Limits apply once homed. Before homing the angle is not
     * known, so manual power is kept to the homing power. 0 = hold where it is.
     */
    public void manual(double power) {
        // The stick was just let go: hold the angle it is at now (if the angle is known; if not,
        // no target, so no power). "a ? b : c" means: if a use b, otherwise c.
        if (power == 0 && manualPower != 0) targetDeg = homed ? getAngle() : Double.NaN;
        manualPower = power;
    }

    /**
     * Starts homing: turns slowly toward the switch until it closes.
     *
     * @param direction +1 = counterclockwise, -1 = clockwise (the way the switch is)
     */
    public void home(double direction) {
        // No switch: nothing to home on (the start angle is trusted).
        if (homeSwitch == null) return;
        fault = "";
        if (homeSwitch.getAsBoolean()) {        // already on the switch: home at once, no motion
            setHomedHere();
            return;
        }
        // We do not turn and wait here: we only switch "homing" on. update() turns it slowly each
        // loop and stops when the switch closes or the time limit runs out.
        homing = true;
        homingStartedSec = clockNanos.getAsLong() / 1e9;
        // Only the sign of direction matters; 0 counts as +1.
        homingDirection = Math.signum(direction) == 0 ? 1 : Math.signum(direction);
        targetDeg = Double.NaN;
    }

    // ---------------------------------------------------------------------------------------
    // Every loop
    // ---------------------------------------------------------------------------------------

    /** Once per loop: checks the home switch, picks the power (homing, stick or PID), applies the limits. */
    @Override
    public void update() {
        boolean switchNow = homeSwitch != null && homeSwitch.getAsBoolean();
        // The moment the switch closes (or any time it is closed while homing), reset the angle.
        // This also fixes any slow drift each time the turret passes the switch in a match.
        if (switchNow && (!switchWas || homing)) {
            // Seen the switch: now we know the angle exactly.
            setHomedHere();
        }
        switchWas = switchNow;
        // Homing time limit: a broken or unplugged switch must not let the turret turn forever
        // and wind up its cables. Stop and show a fault instead.
        if (homing && clockNanos.getAsLong() / 1e9 - homingStartedSec > homingTimeoutSec) {
            homing = false;
            fault = "homing failed: switch not found";
        }

        // Pick the power, in order of priority.
        double power;
        if (homing) {
            // Homing: slow and steady toward the switch.
            power = homingPower * homingDirection;
        } else if (manualPower != 0) {
            // The stick: full range once homed; only homing power before (the limits cannot be
            // trusted while the angle is unknown, so keep it slow).
            power = manualPower * (homed ? maxPower : homingPower);
        } else if (!homed || Double.isNaN(targetDeg)) {
            // Angle unknown, or nothing to aim at: no power.
            power = 0;
        } else {
            // Aiming: the PID gives more power the further off it points.
            power = pid.calculate(targetDeg, getAngle());
        }
        power = MathUtil.clamp(power, -maxPower, maxPower);
        // Once the angle is known: never drive past a limit, and slow down near one (SoftLimits does
        // both). This is what keeps the cables from winding up, even under manual control.
        if (homed && !homing) {
            power = SoftLimits.limit(power, getAngle(), minDeg, maxDeg, slowZoneDeg, slowZonePower, false, false);
        }
        write(power);
    }

    /** Stops: no homing, no target, no power. It stays stopped until a new command. */
    @Override
    public void stop() {
        homing = false;
        manualPower = 0;
        targetDeg = Double.NaN;
        write(0);
    }

    // ---------------------------------------------------------------------------------------
    // Questions
    // ---------------------------------------------------------------------------------------

    /** The angle now (degrees, counterclockwise +). Meaningless until homed. */
    public double getAngle() {
        return rawDeg() + offsetDeg;
    }

    /** The angle it is going to (NaN if none). */
    public double getTargetAngle() {
        return targetDeg;
    }

    /**
     * True when homed, holding a target that is REACHABLE (not out of range), and within the tolerance.
     * Safe to use as a launcher ready check.
     */
    public boolean isAimed() {
        return homed && !homing && !outOfRange && !Double.isNaN(targetDeg)
                && Math.abs(targetDeg - getAngle()) <= toleranceDeg;
    }

    /** "" or a problem, e.g. "homing failed: switch not found". */
    public String getFault() { return fault; }

    /** True if the last aim was outside the limits (it went to the nearest limit instead). */
    public boolean isOutOfRange() { return outOfRange; }

    /** True once the angle is known (switch seen, or there is no switch). */
    public boolean isHomed() { return homed; }
    /** True while turning slowly to find the home switch. */
    public boolean isHoming() { return homing; }
    @Override public String name() { return name; }

    /** A step (autonomous): turn to a robot-relative angle and wait until aimed (or {@code timeoutSec}). */
    public AutoStep aimStep(double robotDeg, double timeoutSec) {
        // "ctx -> { ... }" is a small piece of code handed over to run later, when the routine reaches it.
        return ctx -> {
            aimAt(robotDeg);
            ctx.waitUntil(this::isAimed, timeoutSec);
        };
    }

    /** Shows the angle and AIMED; warnings for faults, not homed, and out of range; the target in debug mode. */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!fault.isEmpty()) t.warn(name + " " + fault);
        if (!homed) t.warn(name + " not homed");
        if (outOfRange) t.warn(name + ": target out of range");
        t.line(name, "%.1f deg%s", getAngle(), isAimed() ? "  AIMED" : "");
        t.debug(name + " target", "%.1f deg", targetDeg);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers. (No "public" or "private" word = usable only inside this package, e.g. by tests.)
    // ---------------------------------------------------------------------------------------

    /** The equal direction (angle + k*360) inside the limits closest to the current angle. */
    double legalTarget(double wantedDeg) {
        double reference = getAngle();
        double best = Double.NaN;
        // The wanted direction, written as the angle nearest to where we are now (e.g. wanted -170
        // while at 175 becomes 190). Then also try one and two full turns either way.
        double base = reference + Angles.shortestDiff(wantedDeg, reference);
        for (int k = -2; k <= 2; k++) {
            double candidate = base + 360.0 * k;
            // Inside the limits? (1e-9 = a tiny allowance for rounding, so exactly on a limit counts.)
            if (candidate >= minDeg - 1e-9 && candidate <= maxDeg + 1e-9) {
                // Keep the one that needs the least turning from here. Since both the start and the
                // target are inside the limits, turning straight there never passes a limit.
                if (Double.isNaN(best) || Math.abs(candidate - reference) < Math.abs(best - reference)) best = candidate;
            }
        }
        if (!Double.isNaN(best)) {
            outOfRange = false;
            return best;
        }
        // No way to point there inside the limits: go as close as allowed and say so.
        outOfRange = true;
        // Nearest limit, measured around the circle.
        double toMin = Math.abs(Angles.shortestDiff(wantedDeg, minDeg));
        double toMax = Math.abs(Angles.shortestDiff(wantedDeg, maxDeg));
        return toMin <= toMax ? minDeg : maxDeg;
    }

    // The switch is closed right now, so the turret is at homeAngle: set the offset to make that true.
    private void setHomedHere() {
        offsetDeg = homeAngle - rawDeg();
        homed = true;
        if (homing) {
            // Homing is done: stop there and hold the home angle, with a fresh PID.
            homing = false;
            targetDeg = homeAngle;
            pid.reset();
        }
    }

    // The angle from the encoder alone (no offset), in degrees.
    private double rawDeg() {
        return motor.getCurrentPosition() / countsPerDegree;
    }

    // Sends a power only when it changes: each hardware command costs loop time.
    private void write(double power) {
        if (power != sentPower) {
            motor.setPower(power);
            sentPower = power;
        }
    }
}
