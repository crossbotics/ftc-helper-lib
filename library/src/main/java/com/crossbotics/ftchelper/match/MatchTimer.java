package com.crossbotics.ftchelper.match;

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
 * <p>It never waits: {@code update()} only looks at the clock and returns. A cue is remembered as
 * "the time left when it should happen", and each loop we check whether that time has come.
 * This is the "remember WHEN, check every loop" idea from docs/guides/non-blocking.md.
 *
 * Docs: docs/modules/match.md (section "MatchTimer")
 */
public class MatchTimer {
    /** FTC autonomous length (seconds). */
    public static final double AUTO_SECONDS = 30;
    /** FTC driver-controlled (TeleOp) length (seconds). */
    public static final double TELEOP_SECONDS = 120;

    private final double lengthSec;
    // Gives the time now in nanoseconds (billionths of a second). Handed in so tests can fake it.
    private final LongSupplier clockNanos;
    private final List<Cue> cues = new ArrayList<>();
    // When start() was called, in nanoseconds (only meaningful while "running" is true).
    private long startNanos = 0;
    // True after start(). A separate flag, because any clock reading (even a negative one) is a valid start time.
    private boolean running = false;

    // One cue: what to run, and at how many seconds left. "done" makes sure it runs only once.
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
        // "System::nanoTime" = "ask the system clock each time" (the real clock).
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

    /**
     * Runs {@code action} once, when this many seconds are left.
     * The action must be quick (it runs inside your loop).
     *
     * @param secondsLeft seconds left on the clock, e.g. 20 for "final 20 seconds"
     * @param action      the code to run, e.g. {@code () -> led.flash()}
     *                    ({@code () -> ...} is a small piece of code we keep and run later)
     * @return this timer, so you can chain more cues
     */
    public MatchTimer cueAt(double secondsLeft, Runnable action) {
        cues.add(new Cue(secondsLeft, action));
        return this;
    }

    /**
     * Buzzes the gamepads three times when this many seconds are left.
     * ({@code Gamepad... gamepads} means you can pass one gamepad, two, or more.)
     */
    public MatchTimer rumbleAt(double secondsLeft, Gamepad... gamepads) {
        return cueAt(secondsLeft, () -> {
            // Skip a missing gamepad (null) instead of crashing.
            for (Gamepad g : gamepads) if (g != null) g.rumbleBlips(3);
        });
    }

    /** Starts counting (call right after {@code waitForStart()}). Starting again restarts it. */
    public void start() {
        startNanos = clockNanos.getAsLong();
        running = true;
        // A restart means every cue may run again.
        for (Cue c : cues) c.done = false;
    }

    /** True after {@link #start()}. */
    public boolean isRunning() {
        return running;
    }

    /** Seconds since start (0 before). */
    public double elapsedSec() {
        // "a ? b : c" = "if a then b, else c". Dividing nanoseconds by 1e9 (a billion) gives seconds.
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
            // "<=" not "==": the loop will almost never land on the exact moment, so run the cue
            // on the first loop at or after its time. "done" stops it running again next loop.
            if (!c.done && left <= c.secondsLeft) {
                c.done = true;
                // A cue for a time that was already over at start (e.g. 200 s left in a 120 s period)
                // never runs. A cue at exactly the full length runs on the first update after start.
                if (c.secondsLeft <= lengthSec) c.action.run();
            }
        }
    }

    /** The time left as text, like the field clock: "1:23". */
    public String format() {
        // Round UP, like the field clock: with 0.4 s left it still shows 0:01, and 0:00 means the end.
        int s = (int) Math.ceil(secondsLeft());
        // Minutes, then seconds with two digits (1:05, not 1:5).
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
