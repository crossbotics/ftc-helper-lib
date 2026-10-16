package io.github.jasoncross.ftchelper.mechanism;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.TouchSensor;
import io.github.jasoncross.ftchelper.auto.AutoStep;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.hardware.Hw;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.safety.SafeMotor;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.tuning.PidTunables;
import io.github.jasoncross.ftchelper.tuning.Tunable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * A motor that moves something to named positions and holds it there: a lift, an arm, a slide,
 * a motor-driven claw. Everything from tutorials 07, 08 and 12 in one helper.
 *
 * <pre>{@code
 * PositionMotor lift = PositionMotor.create(hardwareMap, "lift")
 *         .limits(0, 2600)                       // safe range (encoder ticks unless you set units)
 *         .lowerSwitch(hardwareMap, "lift_bottom")
 *         .slowZone(250, 0.2)
 *         .pid(0.004, 0, 0.0002, 0.05)
 *         .position("DOWN", 0)
 *         .position("LOW", 800)
 *         .position("HIGH", 2400)
 *         .tunable("Lift");                      // gains and positions in the tuning menu
 *
 * controls.bindPress(Pad.G2, Btn.Y, () -> lift.goTo("HIGH"))       // a button
 *         .bindPress(Pad.G2, Btn.DPAD_UP, lift::stepUp);
 * routine.step("Lift up", lift.goToStep("HIGH"));                 // an autonomous step
 * // every loop (or put it in a Subsystems group):
 * lift.manual(-gamepad2.left_stick_y);   // the stick takes over; let go = hold where it is
 * lift.update();
 * }</pre>
 *
 * <p>Positive power must move toward the larger positions (up / out). Check it with the hardware check.
 * Safety comes from {@link SafeMotor}: limits, the switch, homing and the slow zone. Software limits
 * do not replace a hard stop (rule R105 B in 2026-27).
 *
 * Docs: docs/modules/mechanism.md
 */
public class PositionMotor implements Subsystem {
    private final String name;
    private final DcMotorEx motor;
    private final SafeMotor safe;
    private final PidController pid = new PidController(0.004, 0, 0, 0);
    private final Map<String, Double> positions = new LinkedHashMap<>();   // in units

    private double ticksPerUnit = 1;
    private String unit = "ticks";
    private double toleranceUnits = 20;
    private boolean holdWhenReleased = true;
    private double timeoutSec = 3.0;

    private String tunablePrefix = null;
    private PidTunables pidTunables = null;
    private final Map<String, Tunable> positionTunables = new LinkedHashMap<>();

    private Double targetTicks = null;
    private String targetName = null;
    private double manualPower = 0;
    private boolean wasManual = false;

    /** Gets the motor (brake, encoder reset, plain power) and wraps it. Wrong name = clear error. */
    public static PositionMotor create(HardwareMap hardwareMap, String name) {
        DcMotorEx m = MotorSetup.get(hardwareMap, name).brake().resetEncoder().runWithoutEncoder().done();
        return new PositionMotor(name, m);
    }

    /** Wraps a motor you already set up. */
    public PositionMotor(String name, DcMotorEx motor) {
        this.name = name;
        this.motor = motor;
        this.safe = new SafeMotor(motor);
        pid.setTolerance(toleranceUnits);
    }

    // ---------------------------------------------------------------------------------------
    // Setup (each returns this, so you can chain)
    // ---------------------------------------------------------------------------------------

    /** Positive power turns the other way. Set this BEFORE limits and positions are used. */
    public PositionMotor reversed() {
        MotorSetup.of(motor).reversed().resetEncoder().runWithoutEncoder().done();
        return this;
    }

    /**
     * Use your own units instead of encoder ticks, e.g. degrees for an arm or inches for a slide.
     * Set this FIRST: limits, positions, tolerance and slow zone are then in these units.
     *
     * @param ticksPerUnit encoder ticks per unit (measure it: move 90 degrees, read the ticks, divide)
     * @param unitName     shown in telemetry, e.g. "deg"
     */
    public PositionMotor units(double ticksPerUnit, String unitName) {
        this.ticksPerUnit = ticksPerUnit;
        this.unit = unitName;
        tolerance(20 / Math.abs(ticksPerUnit));   // keep the default at about 20 ticks
        return this;
    }

    /** The safe range, in units. */
    public PositionMotor limits(double min, double max) {
        safe.setLimits(min * ticksPerUnit, max * ticksPerUnit);
        return this;
    }

    /** Slow down within {@code distance} units of a limit, down to {@code powerAtLimit}. */
    public PositionMotor slowZone(double distance, double powerAtLimit) {
        safe.setSlowZone(Math.abs(distance * ticksPerUnit), powerAtLimit);
        return this;
    }

    /** The most power it ever gets, 0 to 1. */
    public PositionMotor maxPower(double power) {
        safe.setMaxPower(power);
        return this;
    }

    /** A lower limit switch, by its configuration name (a touch sensor). Skipped if not in the configuration. */
    public PositionMotor lowerSwitch(HardwareMap hardwareMap, String switchName) {
        TouchSensor sensor = Hw.tryGet(hardwareMap, TouchSensor.class, switchName);
        if (sensor != null) safe.setLowerSwitch(sensor::isPressed);
        return this;
    }

    /** A lower limit switch, read your own way (e.g. a magnetic switch: {@code () -> !channel.getState()}). */
    public PositionMotor lowerSwitch(BooleanSupplier isPressed) {
        safe.setLowerSwitch(isPressed);
        return this;
    }

    /** An upper limit switch. */
    public PositionMotor upperSwitch(BooleanSupplier isPressed) {
        safe.setUpperSwitch(isPressed);
        return this;
    }

    /** PID gains (see docs/modules/control.md). Gains work on UNITS of error. */
    public PositionMotor pid(double kP, double kI, double kD, double kF) {
        pid.setGains(kP, kI, kD, kF);
        return this;
    }

    /** How close counts as "there", in units. Default: about 20 encoder ticks. */
    public PositionMotor tolerance(double units) {
        toleranceUnits = Math.abs(units);
        pid.setTolerance(toleranceUnits);
        return this;
    }

    /** Adds a named position, in units. */
    public PositionMotor position(String positionName, double value) {
        positions.put(positionName, value);
        return this;
    }

    /** True (default): when the driver lets go of manual control, it holds where it is. */
    public PositionMotor holdWhenReleased(boolean hold) {
        holdWhenReleased = hold;
        return this;
    }

    /** How long autonomous steps wait for a position before going on. Default 3 s. */
    public PositionMotor stepTimeout(double seconds) {
        timeoutSec = seconds;
        return this;
    }

    /**
     * Puts the PID gains and every named position in the tuning menu, as "prefix/kP", "prefix/HIGH"...
     * Call it LAST in the setup chain (after all positions).
     */
    public PositionMotor tunable(String prefix) {
        tunablePrefix = prefix;
        pidTunables = PidTunables.of(prefix, pid.getKP(), pid.getKI(), pid.getKD(), pid.getKF());
        for (Map.Entry<String, Double> e : positions.entrySet()) {
            double step = Math.max(1, Math.abs(e.getValue()) / 20);
            positionTunables.put(e.getKey(), Tunable.number(prefix + "/" + e.getKey(), e.getValue(), step));
        }
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Commands (quick: safe to call from buttons)
    // ---------------------------------------------------------------------------------------

    /** Go to a named position and hold it. */
    public void goTo(String positionName) {
        Double value = positionValue(positionName);
        if (value == null) throw new IllegalArgumentException("No position called \"" + positionName + "\" on " + name);
        setTarget(value * ticksPerUnit, positionName);
    }

    /** Go to a position in units and hold it. */
    public void goTo(double value) {
        setTarget(value * ticksPerUnit, null);
    }

    /** One named position up (by value). From between positions, the next one above. */
    public void stepUp() {
        List<String> order = sortedNames();
        double now = currentReference();
        for (String n : order) {
            if (positionValue(n) > now + toleranceUnits) { goTo(n); return; }
        }
    }

    /** One named position down. */
    public void stepDown() {
        List<String> order = sortedNames();
        Collections.reverse(order);
        double now = currentReference();
        for (String n : order) {
            if (positionValue(n) < now - toleranceUnits) { goTo(n); return; }
        }
    }

    /**
     * Manual control, e.g. a stick, -1 to 1. Any non-zero value cancels the target.
     * Call it every loop with the stick value (0 when the stick is let go).
     */
    public void manual(double power) {
        manualPower = power;
    }

    /** Holds the current position. */
    public void hold() {
        setTarget(motorTicks(), null);
    }

    /** Stops: no target, no power. */
    @Override
    public void stop() {
        targetTicks = null;
        targetName = null;
        manualPower = 0;
        wasManual = false;
        safe.stop();
    }

    /** Makes the current position zero (home by hand). */
    public void zeroHere() {
        safe.zeroHere();
    }

    // ---------------------------------------------------------------------------------------
    // Every loop
    // ---------------------------------------------------------------------------------------

    @Override
    public void update() {
        safe.update();
        if (pidTunables != null) pidTunables.applyTo(pid);
        if (safe.isWaitingForHoming()) {
            targetTicks = null;
            targetName = null;
        }
        if (manualPower != 0) {
            targetTicks = null;
            targetName = null;
            wasManual = true;
            safe.setPower(manualPower);
            return;
        }
        if (wasManual) {
            wasManual = false;
            if (holdWhenReleased && !safe.isWaitingForHoming()) hold();
        }
        if (targetTicks == null) {
            safe.setPower(0);
            return;
        }
        if (targetName != null && positionTunables.containsKey(targetName)) {
            targetTicks = positionTunables.get(targetName).get() * ticksPerUnit;
        }
        // The PID works in units, so its gains do not depend on the encoder.
        double power = pid.calculate(targetTicks / ticksPerUnit, motorTicks() / ticksPerUnit);
        safe.setPower(power);
    }

    // ---------------------------------------------------------------------------------------
    // Autonomous steps
    // ---------------------------------------------------------------------------------------

    /** A step: go to a named position and wait until there (or the step timeout). */
    public AutoStep goToStep(String positionName) {
        return ctx -> {
            goTo(positionName);
            ctx.waitUntil(this::isAtTarget, timeoutSec);
        };
    }

    /** A step: start moving to a named position and go straight on (it keeps moving during the next steps). */
    public AutoStep startStep(String positionName) {
        return ctx -> goTo(positionName);
    }

    // ---------------------------------------------------------------------------------------
    // Questions
    // ---------------------------------------------------------------------------------------

    /** True when holding a target and within the tolerance. */
    public boolean isAtTarget() {
        return targetTicks != null && Math.abs(targetTicks - motorTicks()) <= toleranceUnits * ticksPerUnit;
    }

    /** True when at this named position (within the tolerance). */
    public boolean isAt(String positionName) {
        Double v = positionValue(positionName);
        return v != null && Math.abs(v * ticksPerUnit - motorTicks()) <= toleranceUnits * ticksPerUnit;
    }

    /** The position now, in units. */
    public double getPosition() {
        return motorTicks() / ticksPerUnit;
    }

    /** The target name, or "hold"/"hand"/"none". */
    public String getTargetName() {
        if (manualPower != 0) return "hand";
        if (targetTicks == null) return "none";
        return targetName != null ? targetName : "hold";
    }

    /** True while waiting to be homed on the lower switch. */
    public boolean isWaitingForHoming() {
        return safe.isWaitingForHoming();
    }

    /** The safety layer inside (limits, switches, powers). */
    public SafeMotor getSafeMotor() {
        return safe;
    }

    /** The PID controller inside. */
    public PidController getPid() {
        return pid;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (safe.isWaitingForHoming()) t.warn(name + ": push it DOWN to the switch to home it");
        t.line(name, "%s  %.0f %s%s", getTargetName(), getPosition(), unit, isAtTarget() ? "  OK" : "");
        t.debug(name + " target", targetTicks == null ? "-" : String.format(java.util.Locale.US, "%.0f %s", targetTicks / ticksPerUnit, unit));
        t.debug(name + " power", "asked %.2f  sent %.2f", safe.getRequestedPower(), safe.getAppliedPower());
        t.debug(name + " limits", "lower %s  upper %s  homed %s", safe.isAtLower(), safe.isAtUpper(), safe.isHomed());
    }

    // ---------------------------------------------------------------------------------------

    private void setTarget(double ticks, String positionName) {
        targetTicks = ticks;
        targetName = positionName;
        manualPower = 0;
        wasManual = false;
        pid.reset();
    }

    private double motorTicks() {
        return safe.getPosition();
    }

    private Double positionValue(String positionName) {
        Tunable t = positionTunables.get(positionName);
        if (t != null) return t.get();
        return positions.get(positionName);
    }

    private double currentReference() {
        return targetTicks != null ? targetTicks / ticksPerUnit : getPosition();
    }

    private List<String> sortedNames() {
        List<String> names = new ArrayList<>(positions.keySet());
        Collections.sort(names, (a, b) -> Double.compare(positionValue(a), positionValue(b)));
        return names;
    }
}
