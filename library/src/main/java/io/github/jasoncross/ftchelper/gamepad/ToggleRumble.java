package io.github.jasoncross.ftchelper.gamepad;

import com.qualcomm.robotcore.hardware.Gamepad;
import java.util.function.LongSupplier;

/**
 * Buzzes the gamepad to confirm a toggle, so the driver knows without looking:
 * two buzzes = ON, one buzz = OFF.
 *
 * <pre>{@code
 * ToggleRumble confirm = new ToggleRumble(gamepad1);
 * controls.bindToggle(Pad.G1, Btn.Y,
 *         () -> { slowMode = true;  confirm.play(true);  },
 *         () -> { slowMode = false; confirm.play(false); });
 * }</pre>
 *
 * <p>It never waits: the whole buzz pattern is handed to the gamepad at once, and the gamepad
 * plays it by itself while the robot keeps running. See docs/guides/non-blocking.md.
 *
 * Docs: docs/modules/gamepad.md
 */
public final class ToggleRumble {
    // "final" fields are set once, in the constructor, and never change after.
    private final Gamepad gamepad;
    // A clock that gives the time in nanoseconds (billionths of a second). Tests pass a fake one.
    private final LongSupplier clockNanos;
    // Buzz strength 0 to 1, buzz length and gap between buzzes in milliseconds.
    private double strength = 0.8;
    private int stepMs = 120;
    private int gapMs = 80;
    // When the latest pattern started and how long it lasts, so isPlaying() can answer.
    // durationNanos stays 0 until the first play(), so isPlaying() is false at the start.
    private long startedAtNanos;
    private long durationNanos;

    /**
     * Makes one that uses the real clock.
     *
     * @param gamepad the gamepad to buzz (usually gamepad1)
     */
    public ToggleRumble(Gamepad gamepad) {
        // "System::nanoTime" hands over the System.nanoTime method itself (a "method reference"),
        // so this class can ask it for the time later.
        this(gamepad, System::nanoTime);
    }

    /**
     * Makes one with your own clock. Used by tests.
     *
     * @param gamepad    the gamepad to buzz
     * @param clockNanos gives the current time in nanoseconds
     */
    public ToggleRumble(Gamepad gamepad, LongSupplier clockNanos) {
        this.gamepad = gamepad;
        this.clockNanos = clockNanos;
    }

    /**
     * Changes how the buzz feels. Defaults: strength 0.8, 120 ms buzz, 80 ms gap (last season's values).
     *
     * @param strength 0 to 1
     * @param stepMs   length of each buzz, milliseconds
     * @param gapMs    pause between the two buzzes, milliseconds
     * @return this ToggleRumble, so you can chain more settings
     */
    public ToggleRumble setPattern(double strength, int stepMs, int gapMs) {
        // Keep strength in 0..1 and the times at 0 or more; the gamepad cannot use anything else.
        this.strength = Math.max(0, Math.min(1, strength));
        this.stepMs = Math.max(0, stepMs);
        this.gapMs = Math.max(0, gapMs);
        return this;
    }

    /**
     * Plays the confirmation. A new one replaces one that is still playing.
     *
     * @param on true = two buzzes (ON), false = one buzz (OFF)
     */
    public void play(boolean on) {
        // Build the pattern step by step: first one buzz (left and right motors at the same strength).
        Gamepad.RumbleEffect.Builder builder = new Gamepad.RumbleEffect.Builder()
                .addStep(strength, strength, stepMs);
        long totalMs = stepMs;
        if (on) {
            // ON gets a pause (strength 0) and then a second buzz.
            builder.addStep(0, 0, gapMs).addStep(strength, strength, stepMs);
            totalMs += gapMs + stepMs;
        }
        // Hand the whole pattern to the gamepad; it plays it by itself, so we do not wait here.
        gamepad.runRumbleEffect(builder.build());
        // Remember when it started and how long it lasts (1 ms = 1,000,000 ns; "L" makes the
        // number a long, a whole number big enough for nanoseconds).
        startedAtNanos = clockNanos.getAsLong();
        durationNanos = totalMs * 1_000_000L;
    }

    /**
     * True while the confirmation is still playing. Use it to hold back other rumble
     * (for example {@link AimRumble}) so the two do not mix.
     */
    public boolean isPlaying() {
        return durationNanos > 0 && clockNanos.getAsLong() - startedAtNanos < durationNanos;
    }
}
