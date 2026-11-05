package com.crossbotics.ftchelper.testkit;

import com.crossbotics.ftchelper.core.Time;
import java.util.function.LongSupplier;

/**
 * A pretend clock for JUnit tests: the time only moves when your test says so. The robot's parts read
 * it (through {@link Time}) instead of the real clock, so a test of timing does not wait and gives the
 * same result on every computer.
 *
 * <pre>{@code
 * @After public void realClock() { PretendClock.stopUsing(); }
 *
 * @Test public void theArmPushesAfterStaging() {
 *     PretendClock clock = PretendClock.use();
 *     PretendMotor fly = devices.motor("launcher");
 *     robot.launcher.spinUp();
 *     for (int i = 0; i < 100; i++) {     // 0.5 s, 5 ms a loop
 *         clock.addSec(0.005);
 *         fly.velocity = fly.velocityCommand;   // the pretend wheel reaches the speed asked
 *         robot.all().update();
 *         if (i == 60) robot.launcher.fire();   // at 0.3 s: the wheel is ready
 *     }
 *     assertEquals(0.7, devices.servo("feeder_arm").position, 1e-9);
 * }
 * }</pre>
 *
 * Docs: docs/modules/testkit.md, docs/guides/testing.md
 */
public final class PretendClock implements LongSupplier {
    // Starts at 1 s, not 0 (some parts treat a time of 0 as "never").
    private long nanos = 1_000_000_000L;

    // Only through use(): a clock made but not in use would do nothing.
    private PretendClock() { }

    /** Makes a pretend clock and has the robot's parts use it. Call {@link #stopUsing()} in {@code @After}. */
    public static PretendClock use() {
        PretendClock c = new PretendClock();
        Time.useForTests(c);
        return c;
    }

    /** The real clock again (put it in {@code @After}). */
    public static void stopUsing() {
        Time.useForTests(null);
    }

    /** Moves the time forward. @param sec seconds (0 or more) */
    public void addSec(double sec) {
        if (sec < 0) throw new IllegalArgumentException("addSec(" + sec + "): time only goes forward");
        nanos += Math.round(sec * 1e9);
    }

    /** The time now, seconds since this clock was made (it starts at 1). */
    public double sec() {
        return nanos / 1e9;
    }

    @Override
    public long getAsLong() {
        return nanos;
    }
}
