package com.crossbotics.ftchelper.safety;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import java.util.function.BooleanSupplier;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.util.MathUtil;

/**
 * A motor that cannot be driven past its safe range. Use it for lifts, slides, arms and turrets.
 *
 * <p>It adds three things to a normal motor:
 * <ul>
 *   <li><b>Soft limits:</b> software stops at a lowest and highest encoder position.
 *       (The encoder is a sensor on the motor that counts how far it has turned, in "ticks".)</li>
 *   <li><b>Limit switches</b> (optional): a pressed switch always stops power toward it.
 *       Pressing the lower switch also sets the position to 0 ("homing"), which fixes
 *       encoder drift.</li>
 *   <li><b>Slow zone</b> (optional): power is reduced near the ends.</li>
 * </ul>
 *
 * <pre>{@code
 * DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift").brake().resetEncoder().runWithoutEncoder().done();
 * TouchSensor bottom = hardwareMap.get(TouchSensor.class, "lift_bottom");
 *
 * SafeMotor lift = new SafeMotor(liftMotor)
 *         .setLimits(0, 2900)              // encoder ticks
 *         .setLowerSwitch(bottom::isPressed)
 *         .setSlowZone(200, 0.25);
 *
 * // every loop:
 * lift.update();                            // read switches, re-zero at the bottom
 * lift.setPower(-gamepad2.left_stick_y);    // limits are applied for you
 * }</pre>
 *
 * <p>With a lower switch, the motor must be homed first: until the switch is pressed once, it only
 * goes DOWN, slowly (see {@link #setRequireHoming}). With no switch, position 0 is wherever the motor
 * was when the robot turned on, so turn the robot on with the mechanism at the bottom.
 *
 * <p>Soft limits are software. FTC rules also need a HARD stop (a physical block) at the
 * end of travel. Use both. See docs/modules/safety.md.
 * Tutorial 08 ("A lift that cannot hurt itself") builds a lift with it step by step.
 * {@code PositionMotor} (docs/modules/mechanism.md) wraps a SafeMotor with PID and named positions.
 *
 * <p>The setters return {@code this} (the same SafeMotor), so you can chain them in one line:
 * {@code new SafeMotor(m).setLimits(0, 2900).setSlowZone(200, 0.25)}.
 */
public class SafeMotor {
    private final DcMotorEx motor;
    // ---- Settings (see the setters) ----
    // The safe range, in ticks. Infinity at both ends means "no limits" until setLimits() is called.
    private double min = Double.NEGATIVE_INFINITY;
    private double max = Double.POSITIVE_INFINITY;
    private double slowZone = 0;
    private double slowZonePower = 0.25;
    private double maxPower = 1.0;
    // How to read each switch; null = no switch.
    private BooleanSupplier lowerSwitch = null;
    private BooleanSupplier upperSwitch = null;

    // ---- What it remembers between loops ----
    // The raw encoder count at the home position. getPosition() subtracts it, so home reads 0.
    private int zeroOffset = 0;
    // The lower switch's state last loop, to notice the moment it gets pressed.
    private boolean lowerWasPressed = false;
    private boolean homed = false;
    // The last power asked for (before limits) and the last one really sent (after limits).
    private double lastRequested = 0;
    private double lastApplied = 0;
    // False until the first power is sent, so the very first value is always sent.
    private boolean sentOnce = false;
    // The encoder, read at most once per loop: update() always reads it fresh, and later reads within
    // READ_KEEP_NANOS (3 ms) use that reading, so update(), the PID and the telemetry share one read
    // (a reading is at most 3 ms old). Each read is a trip to the hub. Code that never calls update()
    // gets a reading at most 3 ms old every time it asks.
    private int encoderNow = 0;
    private long readAt = Long.MIN_VALUE;
    static long READ_KEEP_NANOS = 3_000_000L;

    /**
     * For tests that move a pretend motor with no time passing: true = read the encoder and switches
     * every time; false = the normal 3 ms reuse. Robot code never needs it.
     */
    public static void freshReadingsForTests(boolean fresh) {
        READ_KEEP_NANOS = fresh ? 0 : 3_000_000L;
    }
    // Loops since the power was last sent; it is sent again every RESEND_LOOPS loops even if it did
    // not change, so a power written straight to the motor (getMotor()) cannot stick.
    private int loopsSinceSend = 0;
    private static final int RESEND_LOOPS = 25;
    private boolean requireHoming = true;
    private double homingPower = 0.3;

    /** @param motor the motor. Set its direction so positive power moves toward {@code max}. */
    public SafeMotor(DcMotorEx motor) {
        this.motor = motor;
    }

    /**
     * Sets the safe range, in encoder ticks, measured from the home (zero) position.
     *
     * @param min lowest allowed position, usually 0
     * @param max highest allowed position
     * @return this SafeMotor, so you can chain more setters
     */
    public SafeMotor setLimits(double min, double max) {
        // Accept the two numbers in either order: the smaller one is always the lower limit.
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        return this;
    }

    /**
     * Reduces power near the ends.
     *
     * @param distance      ticks from a limit where slowing starts, e.g. 200
     * @param powerAtLimit  largest power right at the limit, 0 to 1, e.g. 0.25
     * @return this SafeMotor, so you can chain more setters
     */
    public SafeMotor setSlowZone(double distance, double powerAtLimit) {
        this.slowZone = Math.abs(distance);
        this.slowZonePower = MathUtil.clamp(powerAtLimit, 0, 1);
        return this;
    }

    /** The largest power ever sent, 0 to 1. Default 1. */
    public SafeMotor setMaxPower(double maxPower) {
        this.maxPower = MathUtil.clamp(Math.abs(maxPower), 0, 1);
        return this;
    }

    /**
     * Adds a lower limit switch. Give a way to read it, for example {@code touchSensor::isPressed}
     * or {@code () -> !digitalChannel.getState()} for a REV magnetic switch (it reads false when triggered).
     * ({@code touchSensor::isPressed} means "call touchSensor.isPressed() each time we ask";
     * {@code () -> ...} is a small piece of code we keep and run each time we ask.)
     */
    public SafeMotor setLowerSwitch(BooleanSupplier isPressed) {
        this.lowerSwitch = isPressed;
        return this;
    }

    /**
     * With a lower switch, the motor is NOT trusted until it has been homed (by default). Until then:
     * only downward power works, at most the homing power (default 0.3), and the soft limits are not used
     * (the zero is not known yet). Drive it down until the switch is pressed; then everything works.
     * If the mechanism is already on the switch at the first {@link #update()}, it homes at once.
     *
     * @param require false = trust the start position as 0, like a motor with no switch (not recommended)
     */
    public SafeMotor setRequireHoming(boolean require) {
        this.requireHoming = require;
        return this;
    }

    /** The most downward power before homing, 0 to 1. Default 0.3. See {@link #setRequireHoming}. */
    public SafeMotor setHomingPower(double power) {
        this.homingPower = MathUtil.clamp(Math.abs(power), 0, 1);
        return this;
    }

    /** Adds an upper limit switch. See {@link #setLowerSwitch}. */
    public SafeMotor setUpperSwitch(BooleanSupplier isPressed) {
        this.upperSwitch = isPressed;
        return this;
    }

    /**
     * Call once per loop, BEFORE {@link #setPower} or {@link #moveTo}. Reads the switches.
     * When the lower switch is first pressed, the position becomes 0 (homed).
     * It never waits: it reads, maybe corrects the power, and returns.
     */
    public void update() {
        // Every loop starts with fresh readings: the encoder and both switches. Later reads in the
        // same loop (the PID, telemetry) use these for up to READ_KEEP_NANOS.
        readEncoder();
        readSwitches();
        loopsSinceSend++;
        boolean lowerPressed = isAtLowerSwitch();
        // Home only at the moment the switch goes from "not pressed" to "pressed", not on every
        // loop while it stays pressed. That one moment is the true bottom; the encoder count
        // there becomes the new 0, wiping out any drift (a slipping belt, a skipped gear tooth).
        if (lowerPressed && !lowerWasPressed) {
            zeroHere();
        }
        lowerWasPressed = lowerPressed;
        // Re-check the last power against the new readings, so a switch stops the motor at once.
        // (Also every RESEND_LOOPS loops, so a power written straight to the motor is corrected even at rest.)
        // And every loop while it is at a limit or a switch is pressed, so a power written straight to the
        // motor can never push past a limit.
        if (lastApplied != 0 || loopsSinceSend >= RESEND_LOOPS || atALimit()) apply(lastRequested);
    }

    /**
     * Sets the power, with the limits applied. Positive = toward max.
     *
     * @param power -1 to 1
     * @return the power really sent to the motor
     */
    public double setPower(double power) {
        lastRequested = power;
        return apply(power);
    }

    /**
     * Moves toward a target position with a PID controller. Call every loop.
     * The limits still apply, and the target is kept inside them.
     * (PID works out a power from how far away the target is. Tutorial 07 explains it.)
     *
     * @param target target position, ticks
     * @param pid    the controller (keep one per mechanism, do not make a new one each loop)
     * @return the power really sent
     */
    public double moveTo(double target, PidController pid) {
        // A target outside the safe range is moved to the nearest limit, so PID never aims past it.
        double safeTarget = MathUtil.clamp(target, min, max);
        return setPower(pid.calculate(safeTarget, getPosition()));
    }

    /** Stops the motor. */
    public void stop() {
        // Forget the last request too, so update() does not start the motor again.
        lastRequested = 0;
        lastApplied = 0;
        motor.setPower(0);
    }

    /** Position in ticks, measured from the home (zero) position. */
    public int getPosition() {
        long now = System.nanoTime();
        if (readAt == Long.MIN_VALUE || now - readAt >= READ_KEEP_NANOS) readEncoder();
        return encoderNow - zeroOffset;
    }

    // Reads the encoder now (a trip to the hub) and remembers when.
    private void readEncoder() {
        encoderNow = motor.getCurrentPosition();
        readAt = System.nanoTime();
    }

    /** Makes the current position 0. Use it to home by hand (for example when there is no switch). */
    public void zeroHere() {
        // We do not reset the motor's own encoder (that would stop the motor for a moment);
        // we just remember today's count and subtract it from now on.
        readEncoder();
        zeroOffset = encoderNow;
        homed = true;
    }

    /** True while a lower switch is set, homing is required, and it has not been homed yet. */
    public boolean isWaitingForHoming() {
        return lowerSwitch != null && requireHoming && !homed;
    }

    /** True after the position was set to 0 by the lower switch or {@link #zeroHere()}. */
    public boolean isHomed() {
        return homed;
    }

    /** True at or below the lowest position, or when the lower switch is pressed. */
    public boolean isAtLower() {
        return isAtLowerSwitch() || getPosition() <= min;
    }

    /** True at or above the highest position, or when the upper switch is pressed. */
    public boolean isAtUpper() {
        return isAtUpperSwitch() || getPosition() >= max;
    }

    /** The last power asked for, before limits. */
    public double getRequestedPower() {
        return lastRequested;
    }

    /** The last power sent to the motor, after limits. If it is 0 while you asked for power, a limit stopped it. */
    public double getAppliedPower() {
        return lastApplied;
    }

    /**
     * The motor inside, for anything this class does not cover.
     * Careful: power sent straight to this motor skips all the limits. It lasts at most about half a
     * second while {@link #update()} runs every loop: the limited power is sent again every 25 loops.
     */
    public DcMotorEx getMotor() {
        return motor;
    }

    // No switch set (null) counts as "not pressed". Like the encoder, a switch reading is used again for
    // READ_KEEP_NANOS, so one loop reads each switch once.
    private boolean lowerNow, upperNow;
    private long lowerAt = Long.MIN_VALUE, upperAt = Long.MIN_VALUE;

    // Reads both switches now (the start of a loop).
    private void readSwitches() {
        lowerAt = Long.MIN_VALUE;
        upperAt = Long.MIN_VALUE;
        isAtLowerSwitch();
        isAtUpperSwitch();
    }

    // Past a soft limit: the power is checked and sent every loop (not only when it changes), so a power
    // written straight to the motor cannot keep pushing past it. (Resting exactly at a limit, like a lift
    // on its bottom switch, does not send every loop: that would cost a hub write each loop for nothing.)
    private boolean atALimit() {
        double p = getPosition();
        return p < min || p > max;
    }

    private boolean isAtLowerSwitch() {
        if (lowerSwitch == null) return false;
        long now = System.nanoTime();
        if (lowerAt == Long.MIN_VALUE || now - lowerAt >= READ_KEEP_NANOS) {
            lowerNow = lowerSwitch.getAsBoolean();
            lowerAt = now;
        }
        return lowerNow;
    }

    private boolean isAtUpperSwitch() {
        if (upperSwitch == null) return false;
        long now = System.nanoTime();
        if (upperAt == Long.MIN_VALUE || now - upperAt >= READ_KEEP_NANOS) {
            upperNow = upperSwitch.getAsBoolean();
            upperAt = now;
        }
        return upperNow;
    }

    // The one place where power reaches the motor. Every safety rule is applied here, in order.
    private double apply(double power) {
        double limited;
        if (isWaitingForHoming()) {
            // Zero is not known yet: only go down, slowly, until the lower switch is pressed.
            // ("a ? b : c" = "if a then b, else c". Math.max with a negative number keeps the
            // power from going below -homingPower, so it can never go down faster than that.)
            limited = (power < 0 && !isAtLowerSwitch()) ? Math.max(power, -homingPower) : 0;
        } else {
            // Homed (or no switch): soft limits, switches and slow zone. See SoftLimits.java.
            limited = SoftLimits.limit(power, getPosition(), min, max, slowZone, slowZonePower,
                    isAtLowerSwitch(), isAtUpperSwitch());
        }
        // Last, never more than the overall power cap.
        limited = MathUtil.clamp(limited, -maxPower, maxPower);
        // Only send when it changes (each send to the hub takes time), and again every RESEND_LOOPS
        // loops (and every time at a limit), so a power someone wrote straight to the motor cannot stay.
        if (limited != lastApplied || !sentOnce || loopsSinceSend >= RESEND_LOOPS || atALimit()) {
            motor.setPower(limited);
            loopsSinceSend = 0;
        }
        sentOnce = true;
        lastApplied = limited;
        return limited;
    }
}
