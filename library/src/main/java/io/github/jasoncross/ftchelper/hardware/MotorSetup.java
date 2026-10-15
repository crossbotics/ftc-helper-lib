package io.github.jasoncross.ftchelper.hardware;

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
 * Docs: docs/modules/hardware.md
 */
public final class MotorSetup {
    private final DcMotorEx motor;

    private MotorSetup(DcMotorEx motor) {
        this.motor = motor;
    }

    /**
     * Starts the setup for a motor in the configuration.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @param name        the motor's configuration name
     * @throws IllegalArgumentException with the list of real motor names if the name is wrong
     */
    public static MotorSetup get(HardwareMap hardwareMap, String name) {
        return new MotorSetup(Hw.get(hardwareMap, DcMotorEx.class, name));
    }

    /** Starts the setup for a motor you already have. */
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

    /** Sets the direction from a true/false setting. {@code true} = reversed. */
    public MotorSetup reversed(boolean reversed) {
        return reversed ? reversed() : forward();
    }

    /** At power 0 the motor resists turning (holds still). Good for drive wheels and lifts. */
    public MotorSetup brake() {
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        return this;
    }

    /** At power 0 the motor spins freely. Good for flywheels. */
    public MotorSetup coast() {
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        return this;
    }

    /**
     * Sets the encoder count to 0 here. Call this BEFORE a run mode
     * ({@link #runUsingEncoder()} or {@link #runWithoutEncoder()}), because a reset stops the motor
     * until a new mode is set.
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

    /** Sets power to 0 and gives you the motor. */
    public DcMotorEx done() {
        motor.setPower(0);
        return motor;
    }
}
