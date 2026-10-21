package com.crossbotics.ftchelper.hardware;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

/**
 * Sets up a motor in one readable chain.
 *
 * <pre>{@code
 * DcMotorEx lift = MotorSetup.get(hardwareMap, "lift")
 *         .reversed()
 *         .brake()
 *         .resetEncoder()
 *         .runUsingEncoder()
 *         .done();
 * }</pre>
 *
 * <p>{@link #done()} always sets the power to 0, so the motor never moves during INIT.
 * Settings are sent to the motor at once, in the order you write them.
 *
 * <p>How the chain works: every step (except {@link #done()}) does its setting and then hands
 * back the same MotorSetup ({@code return this}), so you can put the next {@code .step()} right
 * after it. Tutorial 02 (docs/tutorials/02-motors-and-servos.md) walks through motor setup.
 *
 * Docs: docs/modules/hardware.md
 */
public final class MotorSetup {
    // "final" on a class: no other class can extend this one.
    // "final" on a field (below): it is set once, in the constructor, and never changed after.
    // This is the motor that every step of the chain changes.
    private final DcMotorEx motor;

    // Private: you start a chain with MotorSetup.get(...) or MotorSetup.of(...), never with "new".
    private MotorSetup(DcMotorEx motor) {
        this.motor = motor;
    }

    /**
     * Starts the setup for a motor in the configuration.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @param name        the motor's configuration name
     * @return the start of the chain
     * @throws IllegalArgumentException with the list of real motor names if the name is wrong
     */
    public static MotorSetup get(HardwareMap hardwareMap, String name) {
        return new MotorSetup(Hw.get(hardwareMap, DcMotorEx.class, name));
    }

    /**
     * Starts the setup for a motor you already have.
     *
     * @param motor the motor to set up
     * @return the start of the chain
     */
    public static MotorSetup of(DcMotorEx motor) {
        return new MotorSetup(motor);
    }

    /** Positive power turns the motor the normal way (this is the default). */
    public MotorSetup forward() {
        motor.setDirection(DcMotorSimple.Direction.FORWARD);
        return this;
    }

    /** Positive power turns the motor the other way. Use when a motor is mounted mirrored. */
    public MotorSetup reversed() {
        motor.setDirection(DcMotorSimple.Direction.REVERSE);
        return this;
    }

    /**
     * Sets the direction from a true/false setting. {@code true} = reversed.
     * Handy when the direction is a number in your settings file, not written in the code.
     *
     * @param reversed true to reverse the motor, false for the normal direction
     */
    public MotorSetup reversed(boolean reversed) {
        // "a ? b : c" is a short if/else: if "reversed" is true do reversed(), otherwise forward().
        return reversed ? reversed() : forward();
    }

    /** At power 0 the motor resists turning (holds still). Good for drive wheels and lifts. */
    public MotorSetup brake() {
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        return this;
    }

    /**
     * At power 0 the motor spins freely. Good for flywheels: a heavy spinning wheel that is
     * braked hard can strain the motor and gears, so it is better to let it slow down by itself.
     */
    public MotorSetup coast() {
        // The SDK calls "coast" FLOAT.
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        return this;
    }

    /**
     * Sets the encoder count to 0 here. Call this BEFORE a run mode
     * ({@link #runUsingEncoder()} or {@link #runWithoutEncoder()}), because a reset stops the motor
     * until a new mode is set.
     *
     * <p>An encoder counts how far the motor has turned, in "ticks" (small steps). After the reset,
     * the position the motor is in right now counts as 0 ticks.
     */
    public MotorSetup resetEncoder() {
        motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        return this;
    }

    /** Power sets the speed, and the hub uses the encoder to hold that speed. Needs an encoder cable. */
    public MotorSetup runUsingEncoder() {
        motor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        return this;
    }

    /** Power goes straight to the motor. The encoder can still be read. */
    public MotorSetup runWithoutEncoder() {
        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        return this;
    }

    /**
     * Sets power to 0 and gives you the motor. Always end the chain with this.
     *
     * @return the motor, set up and stopped
     */
    public DcMotorEx done() {
        // Safety: power 0 so the motor cannot move during INIT, before the match starts.
        motor.setPower(0);
        return motor;
    }
}
