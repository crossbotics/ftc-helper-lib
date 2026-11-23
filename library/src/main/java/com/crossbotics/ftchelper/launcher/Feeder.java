package com.crossbotics.ftchelper.launcher;

import com.crossbotics.ftchelper.core.Subsystem;

/**
 * Pushes ONE game piece into the launcher when asked. The {@link Launcher} decides WHEN;
 * the feeder only knows HOW.
 *
 * <p>Two ready-made feeders: {@link MotorFeeder} (a roller or belt) and {@link ServoFeeder}
 * (a kicker arm). For a new mechanism next season, write your own class with these four methods.
 *
 * <pre>{@code
 * public class GateFeeder implements Feeder {
 *     public void feedOne() { ...open the gate... }
 *     public boolean isBusy() { ...true until the piece is through... }
 *     public void update() { ...timing... }
 *     public void stop() { ...close... }
 * }
 * }</pre>
 *
 * <p><b>Continuous feed</b> is optional: a feeder that can run without a break (a roller, a wheel) also
 * writes {@link #canFeedContinuously}, {@link #startContinuous}, {@link #stopContinuous} and
 * {@link #isFeedingContinuously}. A feeder without them still works, and {@link ContinuousFire} refuses it.
 * Do not fake it with feedOne() again and again: between two feeds the piece would stop, and a release
 * would not stop the one in progress.
 *
 * <p>An "interface" is a list of methods that a class promises to have, without saying how they work.
 * Because the Launcher only talks to "a Feeder", any class that keeps this promise can feed it.
 * "extends Subsystem" adds the promises every mechanism makes (update, stop, telemetry).
 * See docs/guides/extending.md for writing your own.
 *
 * Docs: docs/modules/launcher.md
 */
public interface Feeder extends Subsystem {

    /**
     * Starts feeding one piece. Ignored while busy. Must return at once (never wait):
     * the rest of the feed happens in {@code update()}, a little every loop.
     */
    void feedOne();

    /** True until the piece is through and the feeder is ready for the next one. */
    boolean isBusy();

    /**
     * True if the feeder is jammed (then the launcher stops shooting). Default: never.
     * "default" means a class that does not write this method gets this version.
     */
    default boolean isJammed() {
        return false;
    }

    /** Forgets a jam (the launcher calls it when the driver asks to shoot again). Default: nothing. */
    default void clearJam() { }

    /** True if it can feed without a break, with {@link #startContinuous}. Default: no. */
    default boolean canFeedContinuously() {
        return false;
    }

    /**
     * Starts feeding without a break, until {@link #stopContinuous()} or {@link #stop()}. A shot seen or a
     * time per piece never ends it. Refused while a single feed is busy. It must return at once, and count
     * no piece: nobody knows how many go. {@link #isBusy()} stays true while it runs.
     *
     * @param power the power while it runs, with the feeder's own sign; NaN = the feeder's own power
     * @return true if it feeds continuously now
     */
    default boolean startContinuous(double power) {
        return false;
    }

    /**
     * Ends a continuous feed NOW. Every motor and servo gets its stop even if one of them fails.
     * Default: {@link #stop()}.
     */
    default void stopContinuous() {
        stop();
    }

    /** True while a continuous feed runs (from its start, also while a feed stop still opens). */
    default boolean isFeedingContinuously() {
        return false;
    }
}
