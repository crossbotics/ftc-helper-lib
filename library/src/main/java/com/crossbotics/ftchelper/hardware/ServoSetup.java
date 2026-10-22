package com.crossbotics.ftchelper.hardware;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.PwmControl;
import com.qualcomm.robotcore.hardware.Servo;

/**
 * Sets up a servo in one readable chain.
 *
 * <p>A servo is a small motor that turns to a position you choose (0 to 1) and holds it there.
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
 * <p><b>How far 0 to 1 turns it.</b> The hub tells a servo where to go with a pulse: 600 microseconds
 * (us) for position 0 and 2400 us for 1 (the FTC SDK's normal range). Most FTC servos can take
 * 500 to 2500 us, so the normal range leaves a little of their turn unused: a goBILDA 2000 servo
 * (300 degrees over 500-2500 us) turns 270 degrees over 0 to 1. {@link #fullRange()} widens the pulse
 * to 500-2500 us, so 0 to 1 turns it all 300 degrees. Docs: docs/modules/hardware.md#full-range.
 *
 * <p>Each step hands back the same ServoSetup ({@code return this}) so the next step can follow.
 * Tutorial 02 (docs/tutorials/02-motors-and-servos.md) shows servos in a full program.
 *
 * Docs: docs/modules/hardware.md
 */
public final class ServoSetup {
    /** The FTC SDK's normal pulse for position 0, microseconds ({@code PwmControl.PwmRange.usPulseLowerDefault}). */
    public static final double SDK_PULSE_LOW_US = 600;
    /** The FTC SDK's normal pulse for position 1, microseconds ({@code PwmControl.PwmRange.usPulseUpperDefault}). */
    public static final double SDK_PULSE_HIGH_US = 2400;
    /** The pulse for position 0 with {@link #fullRange()}: what goBILDA, REV and Axon servos accept. */
    public static final double FULL_PULSE_LOW_US = 500;
    /** The pulse for position 1 with {@link #fullRange()}. */
    public static final double FULL_PULSE_HIGH_US = 2500;

    // "final" on the class: nobody can extend it. "final" on the field: set once, never changed.
    private final Servo servo;

    // Private: start a chain with ServoSetup.get(...) or ServoSetup.of(...), never with "new".
    private ServoSetup(Servo servo) {
        this.servo = servo;
    }

    /**
     * Starts the setup for a servo in the configuration.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @param name        the servo's configuration name
     * @return the start of the chain
     * @throws IllegalArgumentException with the list of real servo names if the name is wrong
     */
    public static ServoSetup get(HardwareMap hardwareMap, String name) {
        return new ServoSetup(Hw.get(hardwareMap, Servo.class, name));
    }

    /**
     * Starts the setup for a servo you already have.
     *
     * @param servo the servo to set up
     * @return the start of the chain
     */
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
        // The SDK's scaleRange does the work; it stays in force for every later setPosition.
        servo.scaleRange(min, max);
        return this;
    }

    /**
     * Uses the servo's whole turn: positions 0 to 1 send 500 to 2500 microseconds instead of the
     * SDK's normal 600 to 2400. A goBILDA 2000 servo then turns 300 degrees over 0 to 1 instead of 270.
     * Do it BEFORE you measure positions (the Servo Finder has the same switch): the same position
     * number points somewhere else after the change. A servo that cannot change its pulse (not on a
     * REV hub) keeps the normal range: check {@link #setPulseRange}'s answer if you need to know.
     *
     * <pre>{@code
     * Servo wrist = ServoSetup.get(hardwareMap, "wrist").fullRange().done();
     * }</pre>
     *
     * @return this setup, for the next step
     */
    public ServoSetup fullRange() {
        return pulseRange(FULL_PULSE_LOW_US, FULL_PULSE_HIGH_US);
    }

    /**
     * Sets the pulse the hub sends for positions 0 and 1 (see {@link #fullRange()} for the usual one).
     *
     * @param lowUs  microseconds for position 0, e.g. 500 (100 to 3000)
     * @param highUs microseconds for position 1, e.g. 2500 (more than lowUs, at most 3000)
     * @return this setup, for the next step
     * @throws IllegalArgumentException for numbers outside those ranges
     */
    public ServoSetup pulseRange(double lowUs, double highUs) {
        setPulseRange(servo, lowUs, highUs);
        return this;
    }

    /**
     * Sets a servo's pulse range if it can (a servo on a REV Control or Expansion Hub, or a Servo Hub,
     * can: it is a {@code PwmControl}). Does not move it.
     *
     * @param servo  the servo
     * @param lowUs  microseconds for position 0 (100 to 3000)
     * @param highUs microseconds for position 1 (more than lowUs, at most 3000)
     * @return true if it was set; false if this servo cannot change its pulse (it keeps its range)
     * @throws IllegalArgumentException for numbers outside those ranges
     */
    public static boolean setPulseRange(Servo servo, double lowUs, double highUs) {
        checkPulse(lowUs, highUs);
        // "instanceof": does this servo object also have the PwmControl commands? The hub's servos do.
        if (!(servo instanceof PwmControl)) return false;
        ((PwmControl) servo).setPwmRange(new PwmControl.PwmRange(lowUs, highUs));
        return true;
    }

    /**
     * Checks a pulse range: 100 to 3000 microseconds, low below high.
     *
     * @param lowUs  microseconds for position 0
     * @param highUs microseconds for position 1
     * @throws IllegalArgumentException when they are not
     */
    public static void checkPulse(double lowUs, double highUs) {
        if (!(lowUs >= 100 && highUs <= 3000 && lowUs < highUs)) {
            throw new IllegalArgumentException("Servo pulse range " + lowUs + " to " + highUs
                    + " us: give 100 to 3000 microseconds, low first (most servos: 500 to 2500)");
        }
    }

    /**
     * Gives you the servo. Always end the chain with this.
     *
     * @return the servo, set up (it has not moved yet)
     */
    public Servo done() {
        return servo;
    }
}
