package org.octobots.ftchelper.safety;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import java.util.function.BooleanSupplier;
import org.octobots.ftchelper.control.PidController;
import org.octobots.ftchelper.util.MathUtil;

/**
 * A motor that cannot be driven past its safe range. Use it for lifts, slides, arms and turrets.
 *
 * <p>It adds three things to a normal motor:
 * <ul>
 *   <li><b>Soft limits:</b> software stops at a lowest and highest encoder position.</li>
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
 */
public class SafeMotor {
    private final DcMotorEx motor;
    private double min = Double.NEGATIVE_INFINITY;
    private double max = Double.POSITIVE_INFINITY;
    private double slowZone = 0;
    private double slowZonePower = 0.25;
    private double maxPower = 1.0;
    private BooleanSupplier lowerSwitch = null;
    private BooleanSupplier upperSwitch = null;

    private int zeroOffset = 0;
    private boolean lowerWasPressed = false;
    private boolean homed = false;
    private double lastRequested = 0;
    private double lastApplied = 0;
    private boolean sentOnce = false;
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
     */
    public SafeMotor setLimits(double min, double max) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        return this;
    }

    /**
     * Reduces power near the ends.
     *
     * @param distance      ticks from a limit where slowing starts, e.g. 200
     * @param powerAtLimit  largest power right at the limit, 0 to 1, e.g. 0.25
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
     */
    public void update() {
        boolean lowerPressed = isAtLowerSwitch();
        if (lowerPressed && !lowerWasPressed) {
            zeroHere();
        }
        lowerWasPressed = lowerPressed;
        // Re-check the last power against the new readings, so a switch stops the motor at once.
        if (lastApplied != 0) apply(lastRequested);
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
     *
     * @param target target position, ticks
     * @param pid    the controller (keep one per mechanism, do not make a new one each loop)
     * @return the power really sent
     */
    public double moveTo(double target, PidController pid) {
        double safeTarget = MathUtil.clamp(target, min, max);
        return setPower(pid.calculate(safeTarget, getPosition()));
    }

    /** Stops the motor. */
    public void stop() {
        lastRequested = 0;
        lastApplied = 0;
        motor.setPower(0);
    }

    /** Position in ticks, measured from the home (zero) position. */
    public int getPosition() {
        return motor.getCurrentPosition() - zeroOffset;
    }

    /** Makes the current position 0. Use it to home by hand (for example when there is no switch). */
    public void zeroHere() {
        zeroOffset = motor.getCurrentPosition();
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

    /** The motor inside, for anything this class does not cover. */
    public DcMotorEx getMotor() {
        return motor;
    }

    private boolean isAtLowerSwitch() {
        return lowerSwitch != null && lowerSwitch.getAsBoolean();
    }

    private boolean isAtUpperSwitch() {
        return upperSwitch != null && upperSwitch.getAsBoolean();
    }

    private double apply(double power) {
        double limited;
        if (isWaitingForHoming()) {
            // Zero is not known yet: only go down, slowly, until the lower switch is pressed.
            limited = (power < 0 && !isAtLowerSwitch()) ? Math.max(power, -homingPower) : 0;
        } else {
            limited = SoftLimits.limit(power, getPosition(), min, max, slowZone, slowZonePower,
                    isAtLowerSwitch(), isAtUpperSwitch());
        }
        limited = MathUtil.clamp(limited, -maxPower, maxPower);
        // Only send when it changes: each send to the hub takes time.
        if (limited != lastApplied || !sentOnce) motor.setPower(limited);
        sentOnce = true;
        lastApplied = limited;
        return limited;
    }
}
