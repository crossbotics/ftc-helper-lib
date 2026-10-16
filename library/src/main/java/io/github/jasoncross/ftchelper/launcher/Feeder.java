package io.github.jasoncross.ftchelper.launcher;

import io.github.jasoncross.ftchelper.core.Subsystem;

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
}
