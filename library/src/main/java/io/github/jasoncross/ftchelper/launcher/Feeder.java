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
 * Docs: docs/modules/launcher.md
 */
public interface Feeder extends Subsystem {

    /** Starts feeding one piece. Ignored while busy. */
    void feedOne();

    /** True until the piece is through and the feeder is ready for the next one. */
    boolean isBusy();

    /** True if the feeder is jammed (then the launcher stops shooting). Default: never. */
    default boolean isJammed() {
        return false;
    }

    /** Forgets a jam (the launcher calls it when the driver asks to shoot again). Default: nothing. */
    default void clearJam() { }
}
