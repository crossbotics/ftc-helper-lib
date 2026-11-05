package com.crossbotics.ftchelper.core;

import java.util.function.LongSupplier;

/**
 * The library's clock: the time now, in nanoseconds. On the robot it is the real clock
 * ({@code System.nanoTime()}). A JUnit test can swap in a pretend clock, so it moves the time forward by
 * hand ("0.2 s later") instead of waiting: the test is quick and gives the same result on a slow computer.
 *
 * <p>Most of the robot's parts (the five-files modules, the launcher, the mechanisms, the match timer, the
 * PID and slew controllers) read it each time they need the time. A part made before the swap uses the pretend clock too.
 *
 * <pre>{@code
 * PretendClock clock = PretendClock.use();   // testkit: swaps it in
 * robot.intake.in();
 * clock.addSec(0.2);                         // 0.2 s later, at once
 * robot.all().update();
 * PretendClock.stopUsing();                  // in @After: the real clock again
 * }</pre>
 *
 * <p>Code that measures how long the loop itself takes (the slow-loop warning) keeps the real clock. So does
 * the camera aim ({@code Aim}, the Limelight fix): the camera stamps its pictures with {@code System.nanoTime()}.
 *
 * Docs: docs/modules/core.md
 */
public final class Time {
    // "volatile": every thread sees a swap at once (the camera and battery threads read the time too).
    private static volatile LongSupplier source = null;

    private Time() { }

    /** The time now, nanoseconds (only differences mean anything). Never waits. */
    public static long nanos() {
        LongSupplier s = source;
        return s == null ? System.nanoTime() : s.getAsLong();
    }

    /**
     * For JUnit tests only: use this clock (nanoseconds) instead of the real one. Pass null to go back to
     * the real clock (do it in {@code @After}, so the next test gets the real one).
     */
    public static void useForTests(LongSupplier clockNanos) {
        source = clockNanos;
    }
}
