package com.crossbotics.ftchelper.auto;

/**
 * A path that another library drives (Pedro Pathing, through the pedro module), as one autonomous
 * step: {@link AutoRoutine#followPath}. The robot layer's {@code Steps.follow(...)} makes it for you.
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

    /** Inches left to the end of the path (for the "stuck" check). */
    double remainingIn();

    /** Stops the path and the wheels now. */
    void cancel();
}
