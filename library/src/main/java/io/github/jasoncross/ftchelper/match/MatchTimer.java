package io.github.jasoncross.ftchelper.match;

import com.qualcomm.robotcore.hardware.Gamepad;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Counts down a match period and runs "cues" at set times, for example a gamepad buzz with
 * 20 seconds left, so drivers do not have to watch the field clock.
 *
 * <pre>{@code
 * MatchTimer timer = MatchTimer.teleOp()            // 2:00
 *         .rumbleAt(60, gamepad1, gamepad2)         // one minute left
 *         .rumbleAt(20, gamepad1, gamepad2);        // final 20 seconds
 * waitForStart();
 * timer.start();
 * while (opModeIsActive()) {
 *     timer.update();                               // runs cues when their time comes
 *     t.line("Time", timer.format());               // "1:23"
 * }
 * }</pre>
 *
 * <p>The field clock is the official one. This timer starts when YOUR OpMode starts, which can be a
 * second off. Use it for reminders, not for split-second timing.
 *
 * Docs: docs/modules/match.md
 */
public class MatchTimer {
    /** FTC autonomous length (seconds). */
    public static final double AUTO_SECONDS = 30;
    /** FTC driver-controlled (TeleOp) length (seconds). */
    public static final double TELEOP_SECONDS = 120;

    private final double lengthSec;
    private final LongSupplier clockNanos;
    private final List<Cue> cues = new ArrayList<>();
    private long startNanos = -1;

    private static final class Cue {
        final double secondsLeft;
        final Runnable action;
        boolean done;
        Cue(double secondsLeft, Runnable action) {
            this.secondsLeft = secondsLeft;
            this.action = action;
        }
    }

    /** A timer for a period of this many seconds. */
    public MatchTimer(double lengthSec) {
        this(lengthSec, System::nanoTime);
    }

    /** For tests: your own clock. */
    public MatchTimer(double lengthSec, LongSupplier clockNanos) {
        this.lengthSec = lengthSec;
        this.clockNanos = clockNanos;
    }

    /** A 30-second autonomous timer. */
    public static MatchTimer autonomous() {
        return new MatchTimer(AUTO_SECONDS);
    }

    /** A 2-minute TeleOp timer. */
    public static MatchTimer teleOp() {
        return new MatchTimer(TELEOP_SECONDS);
    }

    /** Runs {@code action} once, when this many seconds are left. */
    public MatchTimer cueAt(double secondsLeft, Runnable action) {
        cues.add(new Cue(secondsLeft, action));
        return this;
    }

    /** Buzzes the gamepads three times when this many seconds are left. */
    public MatchTimer rumbleAt(double secondsLeft, Gamepad... gamepads) {
        return cueAt(secondsLeft, () -> {
            for (Gamepad g : gamepads) if (g != null) g.rumbleBlips(3);
        });
    }

    /** Starts counting (call right after {@code waitForStart()}). Starting again restarts it. */
    public void start() {
        startNanos = clockNanos.getAsLong();
        for (Cue c : cues) c.done = false;
    }

    /** True after {@link #start()}. */
    public boolean isRunning() {
        return startNanos >= 0;
    }

    /** Seconds since start (0 before). */
    public double elapsedSec() {
        return isRunning() ? (clockNanos.getAsLong() - startNanos) / 1e9 : 0;
    }

    /** Seconds left (the full length before start, never below 0). */
    public double secondsLeft() {
        return Math.max(0, lengthSec - elapsedSec());
    }

    /** True when the time is up. */
    public boolean isOver() {
        return isRunning() && secondsLeft() <= 0;
    }

    /** Runs any cue whose time has come. Call every loop. Cues already passed at start do not run. */
    public void update() {
        if (!isRunning()) return;
        double left = secondsLeft();
        for (Cue c : cues) {
            if (!c.done && left <= c.secondsLeft) {
                c.done = true;
                // A cue for a time that was already over at start (e.g. 200 s left in a 120 s period) never runs.
                if (c.secondsLeft < lengthSec) c.action.run();
            }
        }
    }

    /** The time left as text, like the field clock: "1:23". */
    public String format() {
        int s = (int) Math.ceil(secondsLeft());
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
