package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.core.Subsystem;

/**
 * A path follower from another library (Pedro Pathing, through the ftc-helper-lib "pedro" module), plugged
 * into the robot in five files: {@code follower(Pedro.follower(...))} in Robot.java, then
 * {@code s.follow("To the goal", path)} in an autonomous. The core library knows only this small plug, so
 * a team that does not use path following never downloads the other library.
 *
 * <p>It is updated once per loop with the rest of the robot (after the odometry). While it follows a
 * path it drives the wheels; otherwise it leaves them alone.
 *
 * Docs: docs/modules/pedro.md
 */
public interface PathFollower extends Subsystem {
    /**
     * Starts following a path.
     *
     * @param path    the path (the module says which kinds it takes)
     * @param mirrorX true on RED when routes are mirrored (Auto's mirrorForRed()): x and headings flip
     */
    void follow(Object path, boolean mirrorX);

    /** True while it is following (not yet at the end). */
    boolean isBusy();

    /**
     * True while it holds the end point of a path after its step ended (a path that asked for it). The
     * next driving step, a new path or the end of the OpMode ends the hold.
     */
    default boolean isHolding() {
        return false;
    }

    /** Stops following (or holding) now and stops the wheels. */
    void cancel();

    /** Inches left to the end of the WHOLE path, all parts together (for "stuck" checks and {@link PathAt}). */
    double remainingIn();

    /**
     * The length of the running path, inches, all parts together ({@link PathAt#fraction} and
     * {@link PathAt#inchesDone} use it). The default, NaN, means "I do not know": the step then takes
     * the distance left at the start of the path as its length.
     */
    default double totalIn() {
        return Double.NaN;
    }

    /**
     * Which part of the running path the follower drives now, counting from 1 ({@link PathAt#part}).
     * The default, 0, means "I do not know": "part n" things then happen at the end of the path.
     */
    default int partNow() {
        return 0;
    }

    /**
     * How many parts a path has (a path made with {@code then(...)} has more than one), or 0 (the
     * default) when this follower cannot tell. At INIT, {@code PathAt.part(n)} with n more than this
     * stops INIT with a clear message.
     */
    default int parts(Object path) {
        return 0;
    }

    /**
     * Checks a path at INIT (when the autonomous is built): throws IllegalArgumentException if this
     * follower cannot follow it, so a mistake stops INIT and not the match.
     */
    default void check(Object path) { }

    /** False while it cannot follow safely (its pose source is not ready). */
    default boolean isReady() {
        return true;
    }

    /** Makes the follower at INIT, from the robot's devices (after its drive and odometry are set up). */
    interface Maker {
        PathFollower make(Devices devices, RobotDefinition robot);
    }
}
