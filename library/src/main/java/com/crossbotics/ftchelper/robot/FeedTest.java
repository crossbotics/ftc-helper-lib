package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.launcher.Flywheel;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * A test shot for the Feed Stop Finder: it spins the wheel, opens the feed stop like a real shot (the
 * indexer and the pushing parts too), and measures how long the piece takes to reach the wheel. It also
 * counts the wheel's speed drops, so it sees when two pieces went through in one opening.
 *
 * <p>Made by {@link Launcher#feedTest}. Call {@link #update()} every loop.
 *
 * <pre>{@code
 * FeedTest test = robot.launcher.feedTest(0.150, 0.550, 0.20, 3.0);
 * test.spin(true);
 * // every loop:
 * test.update();
 * if (test.isReady() && !test.isBusy() && gamepad1.right_bumper) test.fire();
 * // after a shot: test.lastSec() (seconds to the shot), test.lastPieces() (1 is right)
 * }</pre>
 *
 * Docs: docs/tuning/07-feed-stop-finder.md, "Find the firing times"
 */
public final class FeedTest {

    /** After the feed, it watches this much longer than the stop takes to close, for a second piece. */
    public static final double WATCH_AFTER_SEC = 0.3;

    private final ShotGate gate;
    private final Flywheel flywheel;
    private final List<Intake> pushers;
    private final double rpm;
    private final double moveSec;
    private final LongSupplier clock;

    private boolean spinning = false;
    private boolean fired = false;          // a test shot is on its way or being watched
    private boolean below = false;          // the speed is down now (one drop counts once)
    private double watchUntil = Double.NaN; // when the watch for a second piece ends, seconds (NaN = still feeding)
    private int pieces = 0;
    private double lastSec = Double.NaN;
    private String lastEnd = "";

    // gate and flywheel null: a launcher that is missing or has no feed stop; then it does nothing.
    FeedTest(ShotGate gate, Flywheel flywheel, List<Intake> pushers, double rpm, double moveSec, LongSupplier clock) {
        this.gate = gate;
        this.flywheel = flywheel;
        this.pushers = pushers;
        this.rpm = rpm;
        this.moveSec = moveSec;
        this.clock = clock;
    }

    private double now() {
        return clock.getAsLong() / 1e9;
    }

    /**
     * Spins the wheel up to the launcher's shot speed ({@code speedRpm}), or stops it.
     *
     * @param on true = spin up, false = stop (a shot on its way is cancelled and the stop holds)
     */
    public void spin(boolean on) {
        if (gate == null) return;
        spinning = on;
        if (on) {
            flywheel.setRpm(rpm);
        } else {
            flywheel.stop();
            if (fired) {
                gate.cancel();
                fired = false;
            }
        }
    }

    /** True while the wheel is on. */
    public boolean isSpinning() {
        return spinning;
    }

    /** True when the wheel is on and at speed: a test shot may go. */
    public boolean isReady() {
        return gate != null && spinning && flywheel.isReady();
    }

    /** The wheel's speed now, RPM. */
    public double rpmNow() {
        return flywheel == null ? 0 : Math.abs(flywheel.getRpm());
    }

    /** The speed it spins to: the launcher's {@code speedRpm}, RPM. */
    public double targetRpm() {
        return rpm;
    }

    /** True while a test shot is on its way, or it still watches for a second piece. */
    public boolean isBusy() {
        return fired;
    }

    /** Fires one test piece: only when {@link #isReady()} and not {@link #isBusy()}; else it does nothing. */
    public void fire() {
        if (!isReady() || fired) return;
        fired = true;
        below = false;
        pieces = 0;
        watchUntil = Double.NaN;
        gate.feedOne();
    }

    /** Runs the wheel, the feed stop, the indexer and the pushing parts. Call it every loop. */
    public void update() {
        if (gate == null) return;
        flywheel.update();
        gate.update();
        for (Intake p : pushers) p.update();
        if (!fired) return;
        // Count the speed drops: one each time the speed falls by more than the shot drop, and comes back
        // half way before the next one counts.
        double drop = rpm - rpmNow();
        if (!below && drop > flywheel.getShotDrop()) {
            below = true;
            pieces++;
        } else if (below && drop < flywheel.getShotDrop() / 2) {
            below = false;
        }
        if (Double.isNaN(watchUntil) && !gate.isFeeding()) {
            // The feed ended (the shot, or the backup time): keep the result, and watch a little longer,
            // while the stop closes, for a second piece that slipped through.
            lastSec = gate.lastFeedSec();
            lastEnd = gate.lastEnd();
            watchUntil = now() + moveSec + WATCH_AFTER_SEC;
        }
        if (!Double.isNaN(watchUntil) && now() >= watchUntil) fired = false;
    }

    /**
     * The last test shot's time to the shot, seconds, counted the same way as {@code feedStopOpenSec}. NaN
     * when the wheel saw no shot, or before the first test shot.
     */
    public double lastSec() {
        return lastSec;
    }

    /** How the last test shot ended: "shot seen", "backup time", or "" before the first one. */
    public String lastEnd() {
        return lastEnd;
    }

    /** How many speed drops (pieces) the last test shot made: 1 is right, 2 or more = pieces slipped through. */
    public int lastPieces() {
        return pieces;
    }
}
