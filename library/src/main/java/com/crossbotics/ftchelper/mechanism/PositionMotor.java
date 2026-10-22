package com.crossbotics.ftchelper.mechanism;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.crossbotics.ftchelper.auto.AutoStep;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.MathUtil;
import com.crossbotics.ftchelper.tuning.PidTunables;
import com.crossbotics.ftchelper.tuning.Tunable;
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
 * <p>How it works, in short: a button only CHOOSES a target ("go to HIGH"). Then, once per loop,
 * {@link #update()} looks at where the motor is now (from its encoder, a sensor that counts how far
 * the motor shaft has turned, in "ticks") and gives the motor just enough power to get there and stay
 * there. The part that picks that power is a PID controller (tutorial 07 explains PID step by step).
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
 * Docs: docs/modules/mechanism.md (and docs/guides/non-blocking.md for why commands never wait)
 */
public class PositionMotor implements Subsystem {
    // "implements Subsystem" means this class promises to have the methods every mechanism has
    // (update, stop, addTelemetry, name), so a Subsystems group can update all mechanisms the same way.

    // "final" means the value is set once (here or in the constructor) and never replaced.
    private final String name;
    private final DcMotorEx motor;
    // The safety layer: every power we send goes through it, so the limits and the switch always win.
    private final SafeMotor safe;
    // Starting gains: only kP (a gentle push toward the target). Teams set their own with pid(...).
    private final PidController pid = new PidController(0.004, 0, 0, 0);
    // Named positions, e.g. "HIGH" -> 2400. A LinkedHashMap is a name -> value list that keeps the
    // order you added them in, so warnings list them in your order.
    private final Map<String, Double> positions = new LinkedHashMap<>();   // in units

    // How many encoder ticks make one of YOUR units (1 = you work in plain ticks).
    private double ticksPerUnit = 1;
    private String unit = "ticks";
    private double toleranceUnits = 20;
    private boolean holdWhenReleased = true;
    private double timeoutSec = 3.0;

    // Only used after tunable(...): the live values from the tuning menu.
    private String tunablePrefix = null;
    private PidTunables pidTunables = null;
    private final Map<String, Tunable> positionTunables = new LinkedHashMap<>();

    private boolean unitsLocked = false;   // limits/positions/tolerance were set: units() may no longer change
    private String warning = "";

    // The goal right now. "Double" (capital D) can also be null, which here means "no target: no power".
    // We store it in TICKS, because that is what the encoder reports.
    private Double targetTicks = null;
    private String targetName = null;   // e.g. "HIGH"; null when the target is a plain number or "hold"
    private double manualPower = 0;     // the stick's value from the last manual(...) call
    private boolean wasManual = false;  // true while the stick was moving it last loop
    // The interlock's fence, in units (+/- infinity = none): see fence().
    private double fenceLo = Double.NEGATIVE_INFINITY, fenceHi = Double.POSITIVE_INFINITY;

    /** Gets the motor (brake, encoder reset, plain power) and wraps it. Wrong name = clear error. */
    public static PositionMotor create(HardwareMap hardwareMap, String name) {
        // "static" means you call it on the class itself (PositionMotor.create(...)), not on a lift you
        // already have: it is how you GET a lift in the first place.
        // Brake: with no power the motor resists turning, so a lift does not drop as fast.
        // Reset the encoder: wherever it is now counts as 0.
        // "Run without encoder": we send plain power; our own PID does the position work, not the motor's.
        DcMotorEx m = MotorSetup.get(hardwareMap, name).brake().resetEncoder().runWithoutEncoder().done();
        return new PositionMotor(name, m);
    }

    /** Wraps a motor you already set up (brake mode, encoder reset). Most teams use {@link #create} instead. */
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
        // Flipping the direction also flips the encoder count, so we reset it to 0 again.
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
        // Numbers given earlier were read as ticks. Changing units now would silently make them wrong,
        // so we stop the program with a clear message instead.
        if (unitsLocked) {
            throw new IllegalStateException(name + ": call units(...) FIRST, before limits, slowZone, tolerance and positions");
        }
        this.ticksPerUnit = ticksPerUnit;
        this.unit = unitName;
        toleranceUnits = 20 / Math.abs(ticksPerUnit);   // keep the default at about 20 ticks
        pid.setTolerance(toleranceUnits);
        return this;
    }

    /** The safe range, from {@code min} to {@code max}, in units. The motor will not drive past it. */
    public PositionMotor limits(double min, double max) {
        unitsLocked = true;
        // SafeMotor works in ticks, so we turn units into ticks here.
        safe.setLimits(min * ticksPerUnit, max * ticksPerUnit);
        return this;
    }

    /**
     * Slow down within {@code distance} units of a limit, down to {@code powerAtLimit} (0 to 1),
     * so it does not slam into the end of its travel.
     */
    public PositionMotor slowZone(double distance, double powerAtLimit) {
        unitsLocked = true;
        safe.setSlowZone(Math.abs(distance * ticksPerUnit), powerAtLimit);
        return this;
    }

    /** The most power it ever gets, 0 to 1. */
    public PositionMotor maxPower(double power) {
        safe.setMaxPower(power);
        return this;
    }

    /**
     * A lower limit switch, by its configuration name (a touch sensor). Skipped if not in the configuration.
     * Pressing the switch also sets the position to 0 ("homing"); until that happens the motor may only go
     * down, slowly (see {@link SafeMotor}).
     */
    public PositionMotor lowerSwitch(HardwareMap hardwareMap, String switchName) {
        // tryGet gives null instead of crashing when the name is missing, so a robot with an unplugged
        // or renamed switch still runs (with a warning) instead of stopping the whole OpMode.
        TouchSensor sensor = Hw.tryGet(hardwareMap, TouchSensor.class, switchName);
        // "sensor::isPressed" is a method reference: we hand over "how to ask the switch",
        // and SafeMotor asks it every loop.
        if (sensor != null) safe.setLowerSwitch(sensor::isPressed);
        else warning = "no switch \"" + switchName + "\" in the configuration (running without it)";
        return this;
    }

    /**
     * A lower limit switch, read your own way (e.g. a magnetic switch: {@code () -> !channel.getState()}).
     * {@code () -> ...} is a small piece of code we hand over to run later: SafeMotor runs it every loop.
     */
    public PositionMotor lowerSwitch(BooleanSupplier isPressed) {
        safe.setLowerSwitch(isPressed);
        return this;
    }

    /** An upper limit switch: while it is pressed, the motor will not go further up. */
    public PositionMotor upperSwitch(BooleanSupplier isPressed) {
        safe.setUpperSwitch(isPressed);
        return this;
    }

    /**
     * PID gains (see docs/modules/control.md; tutorial 07 tunes them on a lift). Gains work on UNITS of
     * error, so after {@code units(...)} the same gains suit a motor with a different encoder.
     *
     * @param kP push harder the further away it is
     * @param kI push harder the longer it stays a little short
     * @param kD calm it down when it moves fast (stops overshoot)
     * @param kF a steady extra push, e.g. to hold a lift up against gravity
     */
    public PositionMotor pid(double kP, double kI, double kD, double kF) {
        pid.setGains(kP, kI, kD, kF);
        return this;
    }

    /** How close counts as "there", in units. Default: about 20 encoder ticks. */
    public PositionMotor tolerance(double units) {
        unitsLocked = true;
        toleranceUnits = Math.abs(units);
        pid.setTolerance(toleranceUnits);
        return this;
    }

    /** Adds a named position, in units, e.g. {@code position("HIGH", 2400)}. */
    public PositionMotor position(String positionName, double value) {
        unitsLocked = true;
        positions.put(positionName, value);
        return this;
    }

    /**
     * True (default): when the driver lets go of manual control, it holds where it is.
     * False: it gets no power when let go (it may drift or fall).
     */
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
     * Call it LAST in the setup chain (after all positions). See docs/modules/tuning.md.
     */
    public PositionMotor tunable(String prefix) {
        tunablePrefix = prefix;
        pidTunables = PidTunables.of(prefix, pid.getKP(), pid.getKI(), pid.getKD(), pid.getKF());
        // One menu line per named position. Each press in the menu changes it by about 1/20 of its
        // value (at least 1), so big and small positions both tune in sensible steps.
        for (Map.Entry<String, Double> e : positions.entrySet()) {
            double step = Math.max(1, Math.abs(e.getValue()) / 20);
            positionTunables.put(e.getKey(), Tunable.number(prefix + "/" + e.getKey(), e.getValue(), step));
        }
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Commands (quick: safe to call from buttons)
    // A command only changes the goal. It never waits and never moves the motor itself;
    // update() does that, a little every loop. See docs/guides/non-blocking.md.
    // ---------------------------------------------------------------------------------------

    /**
     * Go to a named position and hold it. An unknown name (a typo) does nothing and shows a warning,
     * so a typo in a button never stops the OpMode in a match.
     */
    public void goTo(String positionName) {
        Double value = positionValue(positionName);
        if (value == null) {
            warning = "no position called \"" + positionName + "\" (known: " + positions.keySet() + ")";
            return;
        }
        // We keep the name too, so a value changed in the tuning menu is picked up while it moves.
        setTarget(value * ticksPerUnit, positionName);
    }

    /** "" or the last setup/command problem (missing switch, unknown position name). */
    public String getWarning() {
        return warning;
    }

    /** Go to a position in units and hold it. */
    public void goTo(double value) {
        setTarget(value * ticksPerUnit, null);
    }

    /** One named position up (by value). From between positions, the next one above. */
    public void stepUp() {
        // The names, lowest position first.
        List<String> order = sortedNames();
        // Count from where it is GOING (if it has a target), so pressing twice fast moves up two positions.
        double now = currentReference();
        for (String n : order) {
            // The first one clearly above (more than the tolerance), so the one we are at does not count.
            if (positionValue(n) > now + toleranceUnits) { goTo(n); return; }
        }
        // Already at the top: nothing to do.
    }

    /** One named position down. From between positions, the next one below. */
    public void stepDown() {
        // Same as stepUp, with the list turned around: highest first.
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
     * When it goes back to 0, it holds where it is (unless {@code holdWhenReleased(false)}).
     */
    public void manual(double power) {
        // Only remembered here; update() acts on it.
        manualPower = power;
    }

    /** Holds the current position. */
    public void hold() {
        setTarget(motorTicks(), null);
    }

    /** Stops: no target, no power. It stays stopped until a new command. */
    @Override
    public void stop() {
        // "@Override" (above) marks that this method fills in one promised by Subsystem.
        targetTicks = null;
        targetName = null;
        manualPower = 0;
        wasManual = false;
        safe.stop();
    }

    /**
     * A second, temporary range inside the limits, in units, set every loop by an interlock rule (two
     * parts that must not be in the same space). It does not move past it: a target beyond it waits at its
     * edge ({@link #isHeld()}) and goes on when the fence moves. {@code fence(-inf, +inf)} removes it.
     */
    public PositionMotor fence(double lo, double hi) {
        fenceLo = Math.min(lo, hi);
        fenceHi = Math.max(lo, hi);
        double a = fenceLo * ticksPerUnit, b = fenceHi * ticksPerUnit;
        safe.setFence(Double.isNaN(a) ? Double.NEGATIVE_INFINITY : a, Double.isNaN(b) ? Double.POSITIVE_INFINITY : b);
        return this;
    }

    /** True while the fence keeps it from its target (it waits at the fence's edge). */
    public boolean isHeld() {
        if (targetTicks == null) return false;
        double t = targetTicks / ticksPerUnit;
        return t < fenceLo - toleranceUnits || t > fenceHi + toleranceUnits;
    }

    /**
     * The target, in units (NaN = none: no target, or the stick is moving it). For interlock rules.
     */
    public double getTarget() {
        if (targetTicks == null || manualPower != 0) return Double.NaN;
        if (targetName != null && positionTunables.containsKey(targetName)) return positionTunables.get(targetName).get();
        return targetTicks / ticksPerUnit;
    }

    /** The stick's power from the last {@link #manual} call (0 = not moving it by hand). */
    public double getManualPower() {
        return manualPower;
    }

    /** Makes the current position zero (home by hand). */
    public void zeroHere() {
        safe.zeroHere();
    }

    // ---------------------------------------------------------------------------------------
    // Every loop
    // ---------------------------------------------------------------------------------------

    /**
     * Does the real work, once per loop: reads the switches, then sends the motor the power that moves it
     * toward the target (or the stick's power, or none). Never waits.
     */
    @Override
    public void update() {
        // Let the safety layer read the switches first (and home on the lower switch if pressed).
        safe.update();
        // Pick up gains changed in the tuning menu, if tunable(...) was used.
        if (pidTunables != null) pidTunables.applyTo(pid);
        // Not homed yet: the position number cannot be trusted, so going to a position would be a guess.
        // Drop the target; only the stick (downward, slowly) can move it until the switch is pressed.
        if (safe.isWaitingForHoming()) {
            targetTicks = null;
            targetName = null;
        }
        // The stick is being used: the driver is in charge. Forget the target and pass the stick on.
        if (manualPower != 0) {
            targetTicks = null;
            targetName = null;
            wasManual = true;
            safe.setPower(manualPower);
            return;
        }
        // The stick was just let go (it moved last loop, it is 0 now): hold right here,
        // so a lift does not slide back down when the driver lets go.
        if (wasManual) {
            wasManual = false;
            if (holdWhenReleased && !safe.isWaitingForHoming()) hold();
        }
        // No target: no power.
        if (targetTicks == null) {
            safe.setPower(0);
            return;
        }
        // Going to a named position that is in the tuning menu: use the menu's latest value.
        if (targetName != null && positionTunables.containsKey(targetName)) {
            targetTicks = positionTunables.get(targetName).get() * ticksPerUnit;
        }
        // The PID works in units, so its gains do not depend on the encoder.
        // It returns a power: big when far away, small when close. Sending it every loop is what
        // HOLDS a lift at a height: if it sags a little, the error grows and the power pushes it back up.
        // Kept inside the interlock's fence: it waits (and holds, with kF) at the fence's edge.
        double goal = MathUtil.clamp(targetTicks / ticksPerUnit, fenceLo, fenceHi);
        double power = pid.calculate(goal, motorTicks() / ticksPerUnit);
        // Through the safety layer, so limits, the slow zone and maxPower still apply.
        safe.setPower(power);
    }

    // ---------------------------------------------------------------------------------------
    // Autonomous steps
    // ---------------------------------------------------------------------------------------

    /**
     * A step: go to a named position and wait until there (or the step timeout). Only for autonomous:
     * the step waits on purpose, and keeps updating every mechanism while it waits.
     */
    public AutoStep goToStep(String positionName) {
        // "ctx -> { ... }" is a small piece of code handed over to run later, when the routine reaches
        // this step. ctx is the routine's helper for waiting safely.
        return ctx -> {
            goTo(positionName);
            // The time limit means a stuck lift cannot use up the whole autonomous.
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

    /**
     * What it is doing, for telemetry: the target's name, "hand" (the stick is moving it),
     * "hold" (holding a spot with no name) or "none" (no power).
     */
    public String getTargetName() {
        if (manualPower != 0) return "hand";
        if (targetTicks == null) return "none";
        // "a ? b : c" means: if a is true use b, otherwise use c.
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

    /** Shows its state on the Driver Station: warnings first, then target and position; more in debug mode. */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (safe.isWaitingForHoming()) t.warn(name + ": push it DOWN to the switch to home it");
        if (!warning.isEmpty()) t.warn(name + ": " + warning);
        t.line(name, "%s  %.0f %s%s", getTargetName(), getPosition(), unit, isAtTarget() ? "  OK" : "");
        t.debug(name + " target", targetTicks == null ? "-" : String.format(java.util.Locale.US, "%.0f %s", targetTicks / ticksPerUnit, unit));
        // "asked" is what the PID or stick wanted; "sent" is what the safety layer let through.
        t.debug(name + " power", "asked %.2f  sent %.2f", safe.getRequestedPower(), safe.getAppliedPower());
        t.debug(name + " limits", "lower %s  upper %s  homed %s", safe.isAtLower(), safe.isAtUpper(), safe.isHomed());
    }

    // ---------------------------------------------------------------------------------------
    // Small helpers used only inside this class ("private")
    // ---------------------------------------------------------------------------------------

    // Every way of setting a target ends here, so they all behave the same.
    private void setTarget(double ticks, String positionName) {
        targetTicks = ticks;
        targetName = positionName;
        // A button press wins over the stick until the stick moves again.
        manualPower = 0;
        wasManual = false;
        // Clear the PID's memory of the old target, so it starts fresh (no leftover push).
        pid.reset();
    }

    private double motorTicks() {
        return safe.getPosition();
    }

    // The value of a named position in units: the tuning menu's value if it has one, otherwise the
    // value from setup, or null if there is no such name.
    private Double positionValue(String positionName) {
        Tunable t = positionTunables.get(positionName);
        if (t != null) return t.get();
        return positions.get(positionName);
    }

    // Where stepUp/stepDown count from, in units: the target if there is one, otherwise where it is now.
    private double currentReference() {
        return targetTicks != null ? targetTicks / ticksPerUnit : getPosition();
    }

    // The position names, sorted from the lowest value to the highest.
    private List<String> sortedNames() {
        List<String> names = new ArrayList<>(positions.keySet());
        // "(a, b) -> ..." tells the sort how to compare two names: by their position values.
        Collections.sort(names, (a, b) -> Double.compare(positionValue(a), positionValue(b)));
        return names;
    }
}
