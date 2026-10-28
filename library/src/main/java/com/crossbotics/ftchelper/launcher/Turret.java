package com.crossbotics.ftchelper.launcher;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.crossbotics.ftchelper.auto.AutoStep;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.safety.SoftLimits;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.MathUtil;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
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
 * <p><b>What turns it.</b> A motor with its encoder ({@link #Turret(String, DcMotorEx)}); a
 * continuous-rotation (CR) servo with any encoder ({@link #Turret(String, DoubleConsumer, DoubleSupplier)}:
 * a REV Through Bore on a motor port, or an absolute encoder read in degrees); or a standard servo that
 * goes to an angle by itself ({@link #servo(String, PositionServo)}: its angle is where it was sent, and
 * "arrived" is a time estimate). A slip ring lets it turn for ever: {@link #continuous()}.
 *
 * <p><b>Fence</b> ({@link #fence(double, double)}): a second, temporary range set every loop by the
 * robot's interlock rules (two parts that must not be in the same space). The turret never turns past
 * it; a target on the other side waits at its edge ({@link #isHeld()}), or goes the other way round if
 * the range allows.
 *
 * <p>Like every helper, the aim commands only set a target; {@link #update()} turns the motor, a little
 * every loop, using a PID controller (tutorial 07 explains PID). Tutorial 18 aims a turret with a camera.
 *
 * Docs: docs/modules/launcher.md
 */
public class Turret implements Subsystem {
    // "implements Subsystem": it has update/stop/addTelemetry/name like every mechanism helper.
    private final String name;
    // The motor (null for a CR servo or a servo turret): reversed() sets its direction.
    private final DcMotorEx motor;
    // Where the power goes and where the encoder count comes from (a motor's own, or a CR servo and
    // a separate encoder). Null for a servo turret.
    private final DoubleConsumer powerOut;
    private final DoubleSupplier countsIn;
    // A servo turret: it goes to an angle by itself (null for a motor or CR servo turret).
    private final PositionServo servo;
    private double powerSign = 1, encoderSign = 1;
    private boolean continuous = false;
    // The interlock's fence (see fence()): +/- infinity = none.
    private double fenceLo = Double.NEGATIVE_INFINITY, fenceHi = Double.POSITIVE_INFINITY;
    // The most power it may use counterclockwise / clockwise now (an interlock's caps, so it brakes in
    // time near the fence; 1 = none): see fence(lo, hi, upPower, downPower).
    private double fenceUpPower = 1, fenceDownPower = 1;
    private boolean held = false;
    // The direction last asked for (robot-relative), so a fenced target can go the other way round.
    private double wantedDeg = Double.NaN;
    private long lastUpdateNanos = -1;
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
    // Where the target sits in the camera's view when the launcher points at it (see cameraOffset).
    private double cameraOffsetDeg = 0;

    private double targetDeg = Double.NaN;  // where it should point; NaN = no target (no power)
    private boolean outOfRange = false;
    private long outOfRangeAskedAt = 0;     // when a target past the limits was last asked for (nanoseconds)
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
        this.powerOut = motor::setPower;
        this.countsIn = motor::getCurrentPosition;
        this.servo = null;
        // The PID could take "the short way round" by itself, but that might cross a limit.
        // We pick the safe target in legalTarget(), so the PID should just use the plain difference.
        pid.setAngleMode(false);   // we pick the legal target ourselves; plain error is correct
    }

    /**
     * A turret turned by something that takes a power (a CR servo) with its angle from any encoder.
     *
     * <pre>{@code
     * DcMotorEx encoderPort = hardwareMap.get(DcMotorEx.class, "turret_enc");   // a Through Bore on a motor port
     * Turret t = new Turret("turret", crServo::setPower, encoderPort::getCurrentPosition)
     *         .countsPerDegree(8192 / 360.0).limits(-90, 90);
     * }</pre>
     *
     * @param name   shown on the Driver Station
     * @param power  where the power goes, -1 to 1 (+ = counterclockwise; see {@link #reversed()})
     * @param counts the encoder now, in counts (any start: {@link #startAngle} sets which angle it is)
     */
    public Turret(String name, DoubleConsumer power, DoubleSupplier counts) {
        this.name = name;
        this.motor = null;
        this.powerOut = power;
        this.countsIn = counts;
        this.servo = null;
        pid.setAngleMode(false);
    }

    // A servo turret (see servo()).
    private Turret(String name, PositionServo servo) {
        this.name = name;
        this.motor = null;
        this.powerOut = null;
        this.countsIn = null;
        this.servo = servo;
    }

    /**
     * A turret on a standard servo that goes to an angle by itself (no encoder, no PID, no homing). The
     * servo needs its angles set up ({@link PositionServo#degrees}) and its travel time (for "arrived").
     *
     * <pre>{@code
     * PositionServo s = PositionServo.create(hardwareMap, "turret").degrees(0.1, -90, 0.9, 90).travelTime(0.6);
     * Turret t = Turret.servo("turret", s).limits(-90, 90);
     * }</pre>
     */
    public static Turret servo(String name, PositionServo servo) {
        return new Turret(name, servo);
    }

    /** True for a servo turret ({@link #servo}): its angle is where it was sent (an estimate while it moves). */
    public boolean isServo() {
        return servo != null;
    }

    /** For tests: use a pretend clock so tests do not wait for real time. */
    Turret withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** Positive power turns the other way. Use it if positive power turns the turret clockwise. */
    public Turret reversed() {
        if (motor != null) MotorSetup.of(motor).reversed().brake().resetEncoder().runWithoutEncoder().done();
        else powerSign = -powerSign;
        return this;
    }

    /**
     * The encoder counts the other way (a separate encoder whose count goes down while the turret turns
     * counterclockwise). Not needed for a motor's own encoder ({@link #reversed()} flips both).
     */
    public Turret encoderReversed() {
        encoderSign = -encoderSign;
        return this;
    }

    /**
     * No limits: the turret can turn round and round (a slip ring carries its wires). It always takes the
     * shortest way, and a direction is never "out of range". The angle keeps counting past 360
     * ({@link #getAngle()}); {@link #getWrappedAngle()} gives it as -180..180.
     */
    public Turret continuous() {
        continuous = true;
        minDeg = Double.NEGATIVE_INFINITY;
        maxDeg = Double.POSITIVE_INFINITY;
        return this;
    }

    /** True after {@link #continuous()}. */
    public boolean isContinuous() {
        return continuous;
    }

    /** Encoder counts per degree of TURRET rotation (motor counts x gear ratio / 360). */
    public Turret countsPerDegree(double counts) {
        countsPerDegree = counts;
        return this;
    }

    /** The allowed range, degrees. May be wider than 360 total (e.g. -190..190). Ends {@link #continuous()}. */
    public Turret limits(double min, double max) {
        // Math.min/max: works even if the two numbers are given the wrong way round.
        continuous = false;
        minDeg = Math.min(min, max);
        maxDeg = Math.max(min, max);
        return this;
    }

    /** The lower limit, degrees (minus infinity when {@link #continuous()}). */
    public double getMin() { return minDeg; }

    /** The upper limit, degrees (infinity when {@link #continuous()}). */
    public double getMax() { return maxDeg; }

    /**
     * A temporary range inside the limits, set every loop by an interlock rule (see the robot module's
     * Interlock): the turret does not turn past it. A target outside it goes the other way round if an
     * equal direction (+ or - 360) is inside both the limits and the fence, else it waits at the fence's
     * edge ({@link #isHeld()}). {@code fence(-inf, +inf)} or {@link #clearFence()} removes it.
     *
     * @param lo lowest allowed angle now, degrees
     * @param hi highest allowed angle now, degrees
     */
    public Turret fence(double lo, double hi) {
        return fence(lo, hi, 1, 1);
    }

    /**
     * {@link #fence(double, double)}, and the most power it may use toward each side now. An interlock
     * rule lowers these as the turret comes near its fence fast (and sets 0 to brake), so it stops before
     * the edge instead of coasting past it.
     *
     * @param lo        lowest allowed angle now, degrees
     * @param hi        highest allowed angle now, degrees
     * @param upPower   the most power counterclockwise (toward higher angles) now, 0 to 1 (1 = no cap)
     * @param downPower the most power clockwise now, 0 to 1
     */
    public Turret fence(double lo, double hi, double upPower, double downPower) {
        fenceLo = Math.min(lo, hi);
        fenceHi = Math.max(lo, hi);
        fenceUpPower = Double.isNaN(upPower) ? 1 : MathUtil.clamp(upPower, 0, 1);
        fenceDownPower = Double.isNaN(downPower) ? 1 : MathUtil.clamp(downPower, 0, 1);
        return this;
    }

    // An interlock rule says: do not move while the angle is not known (see waitUnknown).
    private boolean waitUnknown = false;

    /**
     * Set every loop by an interlock rule: while true, a turret whose angle is not known yet (not homed,
     * or a servo turret never sent anywhere) does not move at all: no homing (its time limit waits too),
     * no stick, no first servo move. From "anywhere" it could sweep through another part that is in its
     * zone. Once its angle is known this does nothing (the {@link #fence} keeps it out).
     *
     * @param wait true = hold still while the angle is unknown
     * @return this turret
     */
    public Turret waitWhileUnknown(boolean wait) {
        waitUnknown = wait;
        return this;
    }

    /** True while {@link #waitWhileUnknown} holds it still (its angle not known, the rule says wait). */
    public boolean isWaitingUnknown() {
        if (!waitUnknown) return false;
        return servo != null ? Double.isNaN(servo.getEstimatedDegrees()) : (!homed || homing);
    }

    /** Removes the {@link #fence}. */
    public Turret clearFence() {
        return fence(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    /** True while the fence keeps it from its target (it waits at the fence's edge). */
    public boolean isHeld() {
        return held;
    }

    /** Marks the angle as known now (homed), e.g. after {@link #startAngle} from a remembered angle. */
    public Turret markHomed() {
        homed = true;
        homing = false;
        return this;
    }

    /**
     * The turret's angle when the robot is turned on (used when there is no home switch). Default 0.
     * Call it after {@link #countsPerDegree} and {@link #reversed()}, since it uses the encoder reading.
     *
     * <p>A servo turret: where it counts from before its first command (a stick, {@link #aimBy}). A servo
     * says nothing about where it is, so send it there at START ({@code aimAt(startAngle)}) to make it
     * true; without startAngle it counts from the middle of its range.
     */
    public Turret startAngle(double deg) {
        // A servo turret's angle is where it was sent: this is only where it counts from until then.
        if (servo != null) {
            servoStartDeg = deg;
            return this;
        }
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
        if (servo != null) throw new IllegalStateException(name + ": a servo turret needs no home switch (its angle is where it was sent): take out .homeSwitch(...)");
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

    /**
     * Where the target should sit in the camera's view when the LAUNCHER points straight at it, in
     * degrees (positive = left of the camera's center), for a camera that is not centered on the
     * launcher. Used by {@link #aimAtTarget}. Find it with the aim tuner. Default 0.
     */
    public Turret cameraOffset(double deg) {
        cameraOffsetDeg = deg;
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
        wantedDeg = robotDeg;
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
        aimAt(angleOrStart() + deltaDeg);
    }

    // The angle now; a servo turret before its first command: its start angle (see startAngle).
    private double angleOrStart() {
        double now = getAngle();
        return Double.isNaN(now) ? servoStartAngle() : now;
    }

    // A servo turret's angle until its first command: startAngle(...), else the middle of its range (0
    // for a turret that turns round and round). It is only where the turret counts from: send it there.
    private double servoStartDeg = Double.NaN;

    /**
     * A servo turret: the angle it counts from before its first command ({@link #startAngle}, or the
     * middle of its range when none was given). Motor turrets: the same as {@link #getAngle()}.
     */
    public double servoStartAngle() {
        if (servo == null) return getAngle();
        if (!Double.isNaN(servoStartDeg)) return MathUtil.clamp(servoStartDeg, minDeg, maxDeg);
        return Double.isInfinite(maxDeg - minDeg) ? 0 : (minDeg + maxDeg) / 2;
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
    public boolean aimAtTarget(com.crossbotics.ftchelper.vision.TargetObservation target, boolean cameraOnTurret) {
        long now = clockNanos.getAsLong();
        // No target, no direction to it, or the picture is too old (the robot has moved since): skip it.
        if (target == null || !target.hasBearing() || !target.isFresh(now, maxAgeMs)) return false;
        // Same picture as last time: skip it. With a camera on the turret, "turn BY 10 deg" from the
        // same picture again would turn another 10 deg, past the target.
        if (target.timeNanos == lastFrameUsed) return false;
        lastFrameUsed = target.timeNanos;
        // Camera on the turret: the bearing is measured from where the turret points, so turn BY it.
        // Camera on the body: the bearing is measured from the robot's front, so it IS the robot angle.
        // The camera offset: the bearing the target has when the launcher is aimed.
        double error = target.bearingDeg - cameraOffsetDeg;
        if (cameraOnTurret) aimBy(error);
        else aimAt(error);
        return true;
    }

    /**
     * Manual control (a stick), -1 to 1. Limits apply once homed. Before homing the angle is not
     * known, so manual power is kept to the homing power. 0 = hold where it is.
     */
    public void manual(double power) {
        // The stick was just let go: hold the angle it is at now (if the angle is known; if not,
        // no target, so no power). "a ? b : c" means: if a use b, otherwise c.
        if (power == 0 && manualPower != 0) {
            targetDeg = homed ? Math.max(effLo(), Math.min(effHi(), getAngle())) : Double.NaN;
            wantedDeg = targetDeg;
        }
        // The stick took over: no target past the limits any more (it holds where the stick left it).
        if (power != 0 || manualPower != 0) outOfRange = false;
        // A servo turret moves its target with the stick (from where it is, the first time; before its
        // first command from its start angle, since a servo cannot say where it is).
        if (servo != null && power != 0 && Double.isNaN(targetDeg)) targetDeg = angleOrStart();
        manualPower = power;
    }

    /**
     * Starts homing: turns slowly toward the switch until it closes.
     *
     * @param direction +1 = counterclockwise, -1 = clockwise (the way the switch is)
     */
    public void home(double direction) {
        // No switch: nothing to home on (the start angle is trusted).
        if (homeSwitch == null || servo != null) return;
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
        long nowNanos = clockNanos.getAsLong();
        double dt = lastUpdateNanos < 0 ? 0 : Math.min(0.1, (nowNanos - lastUpdateNanos) / 1e9);
        lastUpdateNanos = nowNanos;
        if (servo != null) {
            updateServo(dt);
            return;
        }
        // A fresh encoder reading at the start of every loop (homing below uses it).
        encoderNow = encoderSign * countsIn.getAsDouble();
        readAt = System.nanoTime();
        boolean switchNow = homeSwitch != null && homeSwitch.getAsBoolean();
        // The moment the switch closes (or any time it is closed while homing), reset the angle.
        // This also fixes any slow drift each time the turret passes the switch in a match.
        if (switchNow && (!switchWas || homing)) {
            // Seen the switch: now we know the angle exactly.
            setHomedHere();
        }
        switchWas = switchNow;
        // An interlock rule holds it still while its angle is unknown: homing waits, and its time limit
        // starts again when it may go (waiting is not "the switch was not found").
        boolean waitNow = isWaitingUnknown();
        if (waitNow && homing) homingStartedSec = clockNanos.getAsLong() / 1e9;
        // Homing time limit: a broken or unplugged switch must not let the turret turn forever
        // and wind up its cables. Stop and show a fault instead.
        if (homing && clockNanos.getAsLong() / 1e9 - homingStartedSec > homingTimeoutSec) {
            homing = false;
            fault = "homing failed: switch not found";
        }
        double aimDeg = fencedTarget();

        // Pick the power, in order of priority.
        double power;
        if (homing) {
            // Homing: slow and steady toward the switch.
            power = homingPower * homingDirection;
        } else if (manualPower != 0) {
            // The stick: full range once homed; only homing power before (the limits cannot be
            // trusted while the angle is unknown, so keep it slow).
            power = manualPower * (homed ? maxPower : homingPower);
        } else if (!homed || Double.isNaN(aimDeg)) {
            // Angle unknown, or nothing to aim at: no power.
            power = 0;
        } else {
            // Aiming: the PID gives more power the further off it points.
            power = pid.calculate(aimDeg, getAngle());
        }
        power = MathUtil.clamp(power, -maxPower, maxPower);
        // Once the angle is known: never drive past a limit (or the interlock's fence), and slow down
        // near one (SoftLimits does both). This is what keeps the cables from winding up, even under
        // manual control.
        if (homed && !homing) {
            power = SoftLimits.limit(power, getAngle(), effLo(), effHi(), slowZoneDeg, slowZonePower, false, false);
            // An interlock's caps: less power (or none: it brakes) while it turns fast toward its fence.
            power = MathUtil.clamp(power, -fenceDownPower, fenceUpPower);
        }
        if (waitNow) power = 0;   // angle unknown and another part in its zone: no move at all
        write(power);
    }

    // The target kept inside the fence. Outside it: first try the other way round (an equal direction
    // inside the limits and the fence), else wait at the fence's edge (held).
    private double fencedTarget() {
        if (Double.isNaN(targetDeg)) {
            held = false;
            return targetDeg;
        }
        // (More than the tolerance past it: a target right at the fence's edge stays where it is.)
        if (manualPower == 0 && (targetDeg < fenceLo - toleranceDeg || targetDeg > fenceHi + toleranceDeg) && !Double.isNaN(wantedDeg)) {
            double other = equalInside(wantedDeg, effLo(), effHi());
            if (!Double.isNaN(other)) targetDeg = other;
        }
        double aim = MathUtil.clamp(targetDeg, fenceLo, fenceHi);
        held = Math.abs(aim - targetDeg) > 1e-9;
        return aim;
    }

    // The equal direction (deg + k*360) inside lo..hi closest to where it points now; NaN if none.
    private double equalInside(double deg, double lo, double hi) {
        double reference = getAngle();
        double base = reference + Angles.shortestDiff(deg, reference);
        double best = Double.NaN;
        for (int k = -3; k <= 3; k++) {
            double c = base + 360.0 * k;
            if (c >= lo - 1e-9 && c <= hi + 1e-9 && (Double.isNaN(best) || Math.abs(c - reference) < Math.abs(best - reference))) best = c;
        }
        return best;
    }

    // The range now: the limits and the fence together.
    private double effLo() {
        return Math.max(minDeg, fenceLo);
    }

    private double effHi() {
        return Math.min(maxDeg, fenceHi);
    }

    // A servo turret: the stick moves the target at about the servo's speed; the target (kept inside the
    // limits and the fence) is sent to the servo.
    private void updateServo(double dt) {
        if (manualPower != 0 && !Double.isNaN(targetDeg)) {
            double degPerSec = servoSpanDeg() / Math.max(0.05, servoTravelSec);
            targetDeg = MathUtil.clamp(targetDeg + manualPower * maxPower * degPerSec * dt, effLo(), effHi());
            wantedDeg = targetDeg;
        }
        double aim = fencedTarget();
        // Never sent anywhere and an interlock rule says wait: the first move waits (it could sweep anywhere).
        if (!Double.isNaN(aim) && !isWaitingUnknown()) servo.setDegrees(MathUtil.clamp(aim, minDeg, maxDeg));
        servo.update();
    }

    private double servoTravelSec = 0.5;

    /** A servo turret: seconds for the stick to turn it across its whole range at full stick (default 0.5). */
    public Turret servoStickSec(double seconds) {
        servoTravelSec = Math.abs(seconds);
        return this;
    }

    private double servoSpanDeg() {
        double span = maxDeg - minDeg;
        return Double.isInfinite(span) ? 360 : span;
    }

    /** Stops: no homing, no target, no power. It stays stopped until a new command. */
    @Override
    public void stop() {
        homing = false;
        manualPower = 0;
        targetDeg = Double.NaN;
        wantedDeg = Double.NaN;
        held = false;
        if (servo == null) write(0);
    }

    // ---------------------------------------------------------------------------------------
    // Questions
    // ---------------------------------------------------------------------------------------

    /**
     * The angle now (degrees, counterclockwise +). Meaningless until homed. A servo turret: where it
     * was sent, or part way there while it moves (an estimate); NaN before its first command.
     */
    public double getAngle() {
        if (servo != null) return servo.getEstimatedDegrees();
        return rawDeg() + offsetDeg;
    }

    /** {@link #getAngle()} as -180..180 (for a {@link #continuous()} turret, whose angle keeps counting). */
    public double getWrappedAngle() {
        return Angles.wrapDegrees(getAngle());
    }

    /**
     * The angles the turret may be at now, {lowest, highest}: for a motor or CR servo turret its angle
     * (both the same), for a servo turret everything between where it was and where it is going until it
     * should have arrived (it cannot say where it is). Before a servo turret's first command: its whole
     * range. Used by interlock rules.
     */
    public double[] getSweep() {
        if (servo == null) {
            double a = getAngle();
            return new double[] {a, a};
        }
        double[] raw = servo.getSweepDegrees();
        if (raw == null) return new double[] {minDeg, maxDeg};
        return raw;
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
        if (servo != null) {
            return !held && !outOfRange && !Double.isNaN(targetDeg) && servo.isDone()
                    && Math.abs(targetDeg - getAngle()) <= toleranceDeg;
        }
        return homed && !homing && !outOfRange && !held && !Double.isNaN(targetDeg)
                && Math.abs(targetDeg - getAngle()) <= toleranceDeg;
    }

    /** The tolerance, degrees: {@link #isAimed()} is true within this of the target. */
    public double getTolerance() { return toleranceDeg; }

    /**
     * How far past its limits a direction is, degrees: 0 if the turret can point there (some equal
     * angle, + or - 360, is inside the limits), otherwise how far it is from the nearest limit,
     * measured around the circle. Used to tell "just past the limit" from "behind the robot".
     *
     * @param robotDeg the direction, robot-relative, counterclockwise +
     */
    public double degreesOutOfRange(double robotDeg) {
        // The same test as legalTarget(): any of these equal directions inside the limits?
        double base = robotDeg;
        for (int k = -3; k <= 3; k++) {
            double candidate = base + 360.0 * k;
            if (candidate >= minDeg - 1e-9 && candidate <= maxDeg + 1e-9) return 0;
        }
        return Math.min(Math.abs(Angles.shortestDiff(robotDeg, minDeg)), Math.abs(Angles.shortestDiff(robotDeg, maxDeg)));
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
        addWarnings(t);
        double a = continuous ? getWrappedAngle() : getAngle();
        // A servo turret before its first command: a servo cannot say where it is.
        if (Double.isNaN(a)) t.line(name, "not sent anywhere yet");
        else t.line(name, "%.1f deg%s", a, isAimed() ? "  AIMED" : "");
        t.debug(name + " target", Double.isNaN(targetDeg) ? "-" : fmt1(targetDeg) + " deg");
    }

    /**
     * Only the warnings of {@link #addTelemetry} (a fault, not homed, a target out of range), for code
     * that writes its own line for the turret (the robot module's Turret does).
     */
    public void addWarnings(RobotTelemetry t) {
        if (!fault.isEmpty()) t.warn(name + " " + fault);
        if (!homed) t.warn(name + " not homed");
        // Only while something still asks for a target past the limits (within the last second): once
        // following stops asking, or the stick takes over, the warning goes.
        if (outOfRange && clockNanos.getAsLong() - outOfRangeAskedAt < 1_000_000_000L) t.warn(name + ": target out of range");
    }

    // ---------------------------------------------------------------------------------------
    // Helpers. (No "public" or "private" word = usable only inside this package, e.g. by tests.)
    // ---------------------------------------------------------------------------------------

    // A number with one decimal ("12.5").
    private static String fmt1(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    /** The equal direction (angle + k*360) inside the limits closest to the current angle. */
    double legalTarget(double wantedDeg) {
        // (A servo turret before its first command: reckon from its start angle.)
        double reference = angleOrStart();
        double best = Double.NaN;
        // The wanted direction, written as the angle nearest to where we are now (e.g. wanted -170
        // while at 175 becomes 190). Then also try one and two full turns either way.
        double base = reference + Angles.shortestDiff(wantedDeg, reference);
        for (int k = -2; k <= 2; k++) {
            double candidate = base + 360.0 * k;
            // Inside the limits? (1e-9 = a tiny allowance for rounding, so exactly on a limit counts.)
            if (candidate >= minDeg - 1e-9 && candidate <= maxDeg + 1e-9) {
                // Keep the one that needs the least turning from here. Since both the start and the
                // target are inside the limits, turning straight there never passes a limit. One inside
                // the interlock's fence wins over one outside it (it can be reached now).
                boolean inFence = candidate >= fenceLo - 1e-9 && candidate <= fenceHi + 1e-9;
                boolean bestInFence = !Double.isNaN(best) && best >= fenceLo - 1e-9 && best <= fenceHi + 1e-9;
                if (Double.isNaN(best) || (inFence && !bestInFence)
                        || (inFence == bestInFence && Math.abs(candidate - reference) < Math.abs(best - reference))) best = candidate;
            }
        }
        if (!Double.isNaN(best)) {
            outOfRange = false;
            return best;
        }
        // No way to point there inside the limits: go as close as allowed and say so.
        outOfRange = true;
        outOfRangeAskedAt = clockNanos.getAsLong();
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

    // The angle from the encoder alone (no offset), in degrees. update() reads it fresh; later reads
    // within 3 ms use that reading, so update(), the PID and the telemetry share one trip to the hub.
    private double rawDeg() {
        long now = System.nanoTime();
        if (readAt == Long.MIN_VALUE || now - readAt >= READ_KEEP_NANOS) {
            encoderNow = encoderSign * countsIn.getAsDouble();
            readAt = now;
        }
        return encoderNow / countsPerDegree;
    }

    private double encoderNow = 0;
    private long readAt = Long.MIN_VALUE;
    static long READ_KEEP_NANOS = 3_000_000L;

    // Sends a power only when it changes: each hardware command costs loop time.
    private void write(double power) {
        if (power != sentPower) {
            powerOut.accept(powerSign * power);
            sentPower = power;
        }
    }
}
