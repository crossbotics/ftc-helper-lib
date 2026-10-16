package io.github.jasoncross.ftchelper.auto;

import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.function.BooleanSupplier;

/**
 * What a running {@link AutoStep} can use. {@link AutoRoutine} gives it to every step.
 *
 * Docs: docs/modules/auto.md
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
     *
     * @param done       the condition, e.g. {@code lift::isAtTarget}
     * @param timeoutSec most seconds to wait
     * @return true if {@code done} became true, false if it timed out or stopped
     */
    boolean waitUntil(BooleanSupplier done, double timeoutSec);

    /** Loops for this many seconds (mechanisms keep working). Use this instead of sleep(). */
    void waitFor(double seconds);

    /** Seconds left in autonomous. */
    double secondsLeft();

    /** The telemetry (add your own lines; the routine sends them). */
    RobotTelemetry telemetry();
}
