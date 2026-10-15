package io.github.jasoncross.ftchelper.hardware;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

/**
 * Sets up a servo in one readable chain.
 *
 * <pre>{@code
 * Servo claw = ServoSetup.get(hardwareMap, "claw")
 *         .reversed()
 *         .range(0.20, 0.75)   // 0 now means 0.20 and 1 means 0.75
 *         .done();
 * }</pre>
 *
 * <p>This does NOT move the servo. A servo moves the first time you set a position.
 *
 * Docs: docs/modules/hardware.md
 */
public final class ServoSetup {
    private final Servo servo;

    private ServoSetup(Servo servo) {
        this.servo = servo;
    }

    /**
     * Starts the setup for a servo in the configuration.
     *
     * @throws IllegalArgumentException with the list of real servo names if the name is wrong
     */
    public static ServoSetup get(HardwareMap hardwareMap, String name) {
        return new ServoSetup(Hw.get(hardwareMap, Servo.class, name));
    }

    /** Starts the setup for a servo you already have. */
    public static ServoSetup of(Servo servo) {
        return new ServoSetup(servo);
    }

    /** Position 0 and 1 swap ends. Use when a servo is mounted mirrored. */
    public ServoSetup reversed() {
        servo.setDirection(Servo.Direction.REVERSE);
        return this;
    }

    /**
     * Limits the servo to part of its travel. After this, position 0 means {@code min} and
     * position 1 means {@code max}. Use it so code can never drive the servo into the frame.
     *
     * @param min 0 to 1, the real position used for 0
     * @param max 0 to 1, the real position used for 1 (must be bigger than min)
     */
    public ServoSetup range(double min, double max) {
        servo.scaleRange(min, max);
        return this;
    }

    /** Gives you the servo. */
    public Servo done() {
        return servo;
    }
}
