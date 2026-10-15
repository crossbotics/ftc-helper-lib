package org.octobots.ftchelper.gamepad;

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
 * Docs: docs/modules/gamepad.md
 */
public final class ToggleRumble {
    private final Gamepad gamepad;
    private final LongSupplier clockNanos;
    private double strength = 0.8;
    private int stepMs = 120;
    private int gapMs = 80;
    private long startedAtNanos;
    private long durationNanos;

    /** @param gamepad the gamepad to buzz (usually gamepad1) */
    public ToggleRumble(Gamepad gamepad) {
        this(gamepad, System::nanoTime);
    }

    /** Makes one with your own clock. Used by tests. */
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
     */
    public ToggleRumble setPattern(double strength, int stepMs, int gapMs) {
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
        Gamepad.RumbleEffect.Builder builder = new Gamepad.RumbleEffect.Builder()
                .addStep(strength, strength, stepMs);
        long totalMs = stepMs;
        if (on) {
            builder.addStep(0, 0, gapMs).addStep(strength, strength, stepMs);
            totalMs += gapMs + stepMs;
        }
        gamepad.runRumbleEffect(builder.build());
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
