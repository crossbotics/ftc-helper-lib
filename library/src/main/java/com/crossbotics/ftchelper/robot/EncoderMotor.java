package com.crossbotics.ftchelper.robot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One motor of your robot as the Encoder Check sees it: its name, the direction Robot.java gives it, the
 * motors that always get the same power (a lift's second motor), and whether the library reads its encoder.
 * {@link RobotDefinition#encoderMotors()} makes the list; you do not make these yourself.
 *
 * <p>Why the direction: the check gives each motor a short push of POSITIVE power, set up as Robot.java
 * sets it up, so a lift goes up (away from its bottom stop), and the encoder must count UP.
 *
 * Docs: docs/modules/robot.md. Guide: docs/guides/finders.md, "Encoder Check"
 */
public final class EncoderMotor {
    /** The motor's configuration name, e.g. "lift_left". Its encoder cable must be in this motor's port. */
    public final String name;
    /** True if Robot.java turns it the other way ({@code reversed}). */
    public final boolean reversed;
    /** The part it belongs to, e.g. "lift" or "drive" (for the screen). */
    public final String part;
    /**
     * True if the library reads this encoder (a lift, an arm, a flywheel, a motor turret or hood, a drive
     * without odometry). False: nothing needs it (an intake roller), so "no counts" is fine there.
     */
    public final boolean encoderRead;
    /** Why the check skips this motor ("" = it is checked), e.g. its port reads an odometry pod. */
    public final String skip;
    private final List<String> also = new ArrayList<>();
    private final List<Boolean> alsoReversed = new ArrayList<>();

    EncoderMotor(String name, boolean reversed, String part, boolean encoderRead, String skip) {
        this.name = name;
        this.reversed = reversed;
        this.part = part;
        this.encoderRead = encoderRead;
        this.skip = skip == null ? "" : skip;
    }

    // A motor that always gets the same power as this one (a lift's second motor, a second intake motor).
    EncoderMotor with(String motor, boolean motorReversed) {
        also.add(motor);
        alsoReversed.add(motorReversed);
        return this;
    }

    /** The motors that get the same power at the same moment (pushed together with it), maybe none. */
    public List<String> alsoMotors() {
        return Collections.unmodifiableList(also);
    }

    /**
     * True if that motor (one of {@link #alsoMotors()}) turns the other way in Robot.java.
     *
     * @param i its place in {@link #alsoMotors()}, from 0
     * @return true if it is reversed
     */
    public boolean alsoReversed(int i) {
        return alsoReversed.get(i);
    }

    @Override
    public String toString() {
        return part + " \"" + name + "\"" + (reversed ? " (reversed)" : "") + (also.isEmpty() ? "" : " + " + also)
                + (encoderRead ? "" : " (encoder not read)") + (skip.isEmpty() ? "" : " skipped: " + skip);
    }
}
