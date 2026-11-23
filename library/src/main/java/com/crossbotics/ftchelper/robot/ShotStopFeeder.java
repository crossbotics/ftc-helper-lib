package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.launcher.Feeder;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.function.BooleanSupplier;

/**
 * A feeder that spins (a CR servo or a motor) and stops as soon as the flywheel sees the shot
 * (Tunables {@code stopOnShot = true}). WHY: a piece does not always start in the same place, so a fixed
 * {@code feedSec} long enough for the slowest piece runs too long for a near one and can push the next
 * piece in behind it. {@code feedSec} stays as the time limit: a shot the flywheel does not see ends at
 * {@code feedSec}, as before.
 *
 * <p>The shot: the flywheel's own shot count goes up, or, for a feed that began before the wheel was
 * "ready" (QUICK, when the count does not see shots), its speed drops by more than its shot drop from the
 * speed it had when the feed began. A feed that began "ready" uses the count only: two readings inside the
 * ready band can differ by more than the shot drop with no shot. In SPRAY it never stops early: the wheels
 * keep turning from one piece to the next.
 *
 * <p>A continuous feed ({@link #startContinuous}) is never ended by a dip: only a stop or a jam ends it.
 *
 * <p>The start of each feed is seen in {@link #update} too, so a feed that did not start through this
 * wrapper (the indexer's own feedOne) still gets its own starting count. A feed stop (ShotGate) that stops
 * the wheels on the shot calls {@link #stop}: a shot already seen then counts as fired.
 */
final class ShotStopFeeder implements Feeder {
    private final Feeder inner;          // the indexer as the launcher sees it
    private final Indexer indexer;       // the same indexer (isFeeding, endFeedOnShot)
    private final Flywheel flywheel;
    private final BooleanSupplier spray; // true: SPRAY now (no early stop)
    private int shotsAtFeed;
    private double rpmAtFeed = Double.NaN;
    private String lastEnd = "";         // for the debug line: "shot seen" or ""
    private boolean started;             // true: this feed's starting count and speed are taken

    ShotStopFeeder(Feeder inner, Indexer indexer, Flywheel flywheel, BooleanSupplier spray) {
        this.inner = inner;
        this.indexer = indexer;
        this.flywheel = flywheel;
        this.spray = spray;
    }

    @Override
    public void feedOne() {
        boolean was = indexer.isFeeding();
        inner.feedOne();
        if (!was && indexer.isFeeding()) startOfFeed();
    }

    // A new feed: remember where the wheel was, so a later drop is this piece's shot.
    private void startOfFeed() {
        started = true;
        shotsAtFeed = flywheel.getShotCount();
        rpmAtFeed = flywheel.isReady() ? Double.NaN : Math.abs(flywheel.getRpm());
        lastEnd = "";
    }

    // The drop counts from the lower of the start speed and the target: a wheel above the target at the
    // start, or a lower target during the feed (speedByDistance), is not a shot.
    private boolean shotSeen() {
        if (flywheel.getShotCount() > shotsAtFeed) return true;
        if (Double.isNaN(rpmAtFeed)) return false;
        double from = Math.min(rpmAtFeed, Math.abs(flywheel.getTargetRpm()));
        return from - Math.abs(flywheel.getRpm()) > flywheel.getShotDrop();
    }

    @Override
    public void update() {
        inner.update();   // first: a feed that reached feedSec ends there (the time limit)
        if (!indexer.isFeeding() || inner.isFeedingContinuously()) {
            started = false;
            return;
        }
        if (!started) {
            startOfFeed();   // a feed that did not start through feedOne: this loop is its start
            return;
        }
        if (spray.getAsBoolean()) {
            // SPRAY: no early stop. Keep the starting count and speed fresh, so a shot during SPRAY does not
            // stop this feed at once if the mode changes back before it ends.
            shotsAtFeed = flywheel.getShotCount();
            rpmAtFeed = flywheel.isReady() ? Double.NaN : Math.abs(flywheel.getRpm());
            return;
        }
        if (shotSeen()) {
            indexer.endFeedOnShot();
            lastEnd = "shot seen";
        }
    }

    @Override
    public boolean isBusy() {
        return inner.isBusy();
    }

    @Override
    public boolean isJammed() {
        return inner.isJammed();
    }

    @Override
    public void clearJam() {
        inner.clearJam();
    }

    @Override
    public void stop() {
        // A feed stop closing on the shot (ShotGate) stops the wheels: the piece went, so it counts as fired.
        if (!inner.isFeedingContinuously() && indexer.isFeeding() && started && shotSeen()) {
            indexer.endFeedOnShot();
            lastEnd = "shot seen";
        } else {
            inner.stop();
        }
        started = false;
    }

    @Override
    public boolean canFeedContinuously() {
        return inner.canFeedContinuously();
    }

    @Override
    public boolean startContinuous(double power) {
        started = false;    // no shot to watch for: no dip ends a continuous feed
        return inner.startContinuous(power);
    }

    @Override
    public void stopContinuous() {
        started = false;
        inner.stopContinuous();
    }

    @Override
    public boolean isFeedingContinuously() {
        return inner.isFeedingContinuously();
    }

    @Override
    public String name() {
        return inner.name();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        inner.addTelemetry(t);
        if (!lastEnd.isEmpty()) t.debug(name() + " last feed", lastEnd);
    }
}
