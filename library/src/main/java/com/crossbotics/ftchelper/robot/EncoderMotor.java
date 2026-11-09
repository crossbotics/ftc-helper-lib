package com.crossbotics.ftchelper.robot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One motor of your robot as the Encoder Check sees it: its name, the direction Robot.java gives it, which way
 * to push it, the motors that always get the same power (a lift's second motor), and whether the library reads
 * its encoder. {@link RobotDefinition#encoderMotors()} makes the list; you do not make these yourself.
 *
 * <p>Why the direction: the check pushes each motor set up as Robot.java sets it up, toward the side where the
 * part has room ({@link #pushSign()}: a lift up from its bottom stop), and its count must move that way too.
 *
 * Docs: docs/modules/robot.md. Guide: docs/guides/finders.md, "Encoder Check"
 */
public final class EncoderMotor {
    /** The motor's configuration name, e.g. "lift_left". Its encoder cable must be in this motor's port. */
    public final String name;
    /** True if Robot.java turns it the other way ({@code reversed}). */
    public final boolean reversed;
    /** The part it belongs to, e.g. "Lift" or "drive" (for the screen). */
    public final String part;
    /**
     * True if the library reads this encoder (a lift, an arm, a flywheel, a motor turret or hood, a drive
     * without odometry). False: nothing needs it (an intake roller), so "no counts" is fine there.
     */
    public final boolean encoderRead;
    private final int pushSign;
    private String skip = "";
    private boolean optional = false;
    private final List<String> also = new ArrayList<>();
    private final List<Boolean> alsoReversed = new ArrayList<>();

    EncoderMotor(String name, boolean reversed, String part, boolean encoderRead) {
        this(name, reversed, part, encoderRead, 1);
    }

    EncoderMotor(String name, boolean reversed, String part, boolean encoderRead, int pushSign) {
        this.name = name;
        this.reversed = reversed;
        this.part = part;
        this.encoderRead = encoderRead;
        this.pushSign = pushSign < 0 ? -1 : 1;
    }

    // A motor that always gets the same power as this one (a lift's second motor, a second intake motor).
    EncoderMotor with(String motor, boolean motorReversed) {
        also.add(motor);
        alsoReversed.add(motorReversed);
        return this;
    }

    EncoderMotor skip(String why) {
        skip = why == null ? "" : why;
        return this;
    }

    EncoderMotor optional(boolean on) {
        optional = on;
        return this;
    }

    /**
     * Which way the check pushes it: +1 = positive power (a lift up from its bottom stop), -1 = negative power
     * (a part whose whole range is below where it starts, e.g. a hood whose other stop is at negative ticks).
     */
    public int pushSign() {
        return pushSign;
    }

    /** Why the check skips this motor ("" = it is checked), e.g. its port reads an odometry pod. */
    public String skip() {
        return skip;
    }

    /** True if its part is {@code optional()} in Robot.java (not in the configuration = fine). */
    public boolean isOptional() {
        return optional;
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
        return part + " \"" + name + "\"" + (reversed ? " (reversed)" : "") + (pushSign < 0 ? " pushed with - power" : "")
                + (also.isEmpty() ? "" : " + " + also) + (encoderRead ? "" : " (encoder not read)")
                + (skip.isEmpty() ? "" : " skipped: " + skip);
    }
}
