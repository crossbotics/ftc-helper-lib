package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.launcher.Flywheel;
import java.util.function.LongSupplier;

/**
 * A test shot for the Feed Time Finder: it spins the wheel, runs the feeder (a CR servo or a motor) at its
 * {@code power}, and measures how long the piece takes to reach the wheel. The feeder stops at the shot, or
 * at the time limit when no shot comes.
 *
 * <p>Why: {@code feedSec} must be long enough for the piece that starts farthest back. Several test shots,
 * from every place a piece can sit, give the longest time. Made by {@link Launcher#feedTimeTest}. Call
 * {@link #update()} every loop.
 *
 * <pre>{@code
 * FeedTimeTest test = robot.launcher.feedTimeTest(3.0);
 * test.spin(true);
 * // every loop:
 * test.update();
 * if (test.isReady() && !test.isBusy() && gamepad1.right_bumper) test.fire();
 * // after a shot: test.lastSec() (seconds to the shot; NaN = no shot in the time limit)
 * }</pre>
 *
 * Docs: docs/tuning/07-feed-time-finder.md
 */
public final class FeedTimeTest {

    private final Flywheel flywheel;
    private final Indexer indexer;
    private final double rpm;
    private final double limitSec;
    private final LongSupplier clock;

    private boolean spinning = false;
    private boolean feeding = false;
    private double startAt = 0;            // when the test feed started, seconds
    private int shotsAtStart = 0;          // the wheel's shot count then
    private double lastSec = Double.NaN;
    private String lastEnd = "";

    // flywheel and indexer null: a launcher that is missing or has no timed feeder; then it does nothing.
    FeedTimeTest(Flywheel flywheel, Indexer indexer, double rpm, double limitSec, LongSupplier clock) {
        this.flywheel = flywheel;
        this.indexer = indexer;
        this.rpm = Math.abs(rpm);
        this.limitSec = limitSec;
        this.clock = clock;
    }

    private double now() {
        return clock.getAsLong() / 1e9;
    }

    /** True when this test can run (a launcher with a timed feeder, on the robot). */
    public boolean works() {
        return flywheel != null;
    }

    /**
     * Spins the wheel up to the launcher's shot speed ({@code speedRpm}), or stops it.
     *
     * @param on true = spin up, false = stop (a test feed going on stops too)
     */
    public void spin(boolean on) {
        if (flywheel == null) return;
        spinning = on;
        if (on) {
            flywheel.setRpm(rpm);
        } else {
            flywheel.stop();
            if (feeding) {
                // Stopped part way: no time. "stopped" tells the finder not to count it.
                lastSec = Double.NaN;
                lastEnd = "stopped";
                endFeed();
            }
        }
    }

    /** True while the wheel is on. */
    public boolean isSpinning() {
        return spinning;
    }

    /** True when the wheel is on and at speed: a test shot may go. */
    public boolean isReady() {
        return flywheel != null && spinning && flywheel.isReady();
    }

    /** The wheel's speed now, RPM. */
    public double rpmNow() {
        return flywheel == null ? 0 : Math.abs(flywheel.getRpm());
    }

    /** The speed it spins to: the launcher's {@code speedRpm}, RPM. */
    public double targetRpm() {
        return rpm;
    }

    /** The time limit of one test feed, seconds. */
    public double limitSec() {
        return limitSec;
    }

    /** True while a test feed runs. */
    public boolean isBusy() {
        return feeding;
    }

    /** Fires one test piece: only when {@link #isReady()} and not {@link #isBusy()}; else it does nothing. */
    public void fire() {
        if (!isReady() || feeding) return;
        feeding = true;
        startAt = now();
        shotsAtStart = flywheel.getShotCount();
        indexer.runWheel(indexer.wheelPower());
    }

    /** Runs the wheel, and ends a test feed at the shot or the time limit. Call it every loop. */
    public void update() {
        if (flywheel == null) return;
        flywheel.update();
        if (!feeding) return;
        double sec = now() - startAt;
        // A shot: the wheel's own shot count went up, or its speed fell more than the shot drop (shotDropRpm).
        boolean shot = flywheel.getShotCount() > shotsAtStart || rpm - rpmNow() > flywheel.getShotDrop();
        if (shot) {
            lastSec = sec;
            lastEnd = "shot seen";
            endFeed();
        } else if (sec >= limitSec) {
            lastSec = Double.NaN;
            lastEnd = "time limit";
            endFeed();
        }
    }

    private void endFeed() {
        feeding = false;
        indexer.runWheel(0);
    }

    /** The last test shot's time from the start of the feed to the shot, seconds. NaN = no shot seen, or none yet. */
    public double lastSec() {
        return lastSec;
    }

    /** How the last test shot ended: "shot seen", "time limit", "stopped" (the wheel went off), or "" before the first one. */
    public String lastEnd() {
        return lastEnd;
    }

    /** The feeder's Tunables section name (for the line to copy), or "" when it does nothing. */
    public String feederSection() {
        return indexer == null ? "" : indexer.name();
    }

    /** Tunables {@code feedSec} now, seconds (NaN when it does nothing). */
    public double feedSecNow() {
        return indexer == null ? Double.NaN : indexer.feedSecNow();
    }

    /** True when the feeder has {@code stopOnShot = true} (each feed ends at the shot; feedSec is the limit). */
    public boolean stopsOnShot() {
        return indexer != null && indexer.stopsOnShot();
    }
}
