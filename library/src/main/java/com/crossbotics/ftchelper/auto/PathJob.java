package com.crossbotics.ftchelper.auto;

/**
 * A path that another library drives (Pedro Pathing, through the pedro module), as one autonomous
 * step: {@link AutoRoutine#followPath}. The robot layer's {@code Steps.follow(...)} makes it for you.
 *
 * <p>{@link #totalIn()} and {@link #partNow()} have defaults ("I do not know"), so a job written
 * before them still works: things to do along the path ({@link PathEvent}) then use the distance
 * left at the start as the length, and fire "part n" events at the end.
 *
 * Docs: docs/modules/pedro.md
 */
public interface PathJob {
    /** Starts the path. */
    void start();

    /** True while it is still following the path. */
    boolean isBusy();

    /** False while its pose source (the odometry) is not ready. */
    boolean isReady();

    /** Inches left to the end of the WHOLE path (all its parts), for the "stuck" check and {@link PathEvent}s. */
    double remainingIn();

    /** Stops the path and the wheels now. */
    void cancel();

    /**
     * The length of the running path, inches, all parts together. NaN (the default) when it does not
     * know: the routine then takes the distance left at the start.
     */
    default double totalIn() {
        return Double.NaN;
    }

    /** Which part of the path it drives now, counting from 1. 0 (the default) when it does not know. */
    default int partNow() {
        return 0;
    }
}
