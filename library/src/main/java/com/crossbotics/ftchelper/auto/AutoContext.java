package com.crossbotics.ftchelper.auto;

import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.function.BooleanSupplier;

/**
 * What a running {@link AutoStep} can use. {@link AutoRoutine} gives it to every step.
 *
 * <p>An {@code interface} is a list of methods without their insides: AutoRoutine fills them in.
 * Your step only sees these few methods, so it cannot, for example, add new steps while running.
 *
 * Docs: docs/modules/auto.md. Why we wait this way and never sleep(): docs/guides/non-blocking.md
 */
public interface AutoContext {

    /** True while the step should keep going (the OpMode runs, and the time for this part is not up). */
    boolean isActive();

    /**
     * One loop: updates every mechanism, shows telemetry, and lets the robot breathe.
     * Call it inside any waiting loop you write.
     */
    void loop();

    /**
     * Loops until {@code done} is true, the time runs out, or the routine stops.
     * <b>BLOCKS</b> (on purpose, autonomous only), but keeps every mechanism updating while it waits.
     *
     * @param done       the condition, e.g. {@code lift::isAtTarget}
     * @param timeoutSec most seconds to wait
     * @return true if {@code done} became true, false if it timed out or stopped
     */
    boolean waitUntil(BooleanSupplier done, double timeoutSec);

    /** Loops for this many seconds (mechanisms keep working). Use this instead of sleep(). <b>BLOCKS</b>. */
    void waitFor(double seconds);

    /** Seconds left in autonomous. */
    double secondsLeft();

    /** The telemetry (add your own lines; the routine sends them). */
    RobotTelemetry telemetry();

    /**
     * Adds a note to this step's line in the end-of-autonomous report, e.g. "timed out", instead of
     * "done". Default: nothing (a context that keeps no report).
     *
     * @param text a few words
     */
    default void note(String text) { }
}
