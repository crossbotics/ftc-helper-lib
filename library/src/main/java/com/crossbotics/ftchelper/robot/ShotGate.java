package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.launcher.Feeder;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * How a piece gets into the launcher, for a launcher with a feed stop or a piece path
 * ({@code Launcher.feedStop(...)}, {@code Launcher.pushedBy(...)}): one feed opens the feed stop, runs
 * the parts that push the piece (even when the driver has them off), and feeds with the indexer if
 * there is one. The feed ends when the wheel sees the shot (its speed drops), or after the backup time.
 * Then the feed stop closes, unless more pieces follow at once in a QUICK or SPRAY stream: then it
 * stays open until the last one. The pushing parts go back to what they were doing.
 *
 * <p>The feed stop goes to "hold" at START, never in INIT (nothing moves before the match), and gets a
 * position only when its position changes.
 */
final class ShotGate implements Feeder {
    private final Servo stop;              // null = no feed stop
    private final Feeder indexer;          // null = no indexer
    private final List<Intake> pushers;
    private final Flywheel flywheel;
    private final BooleanSupplier stream;  // true: more pieces follow at once (keep the feed stop open)
    private final double hold, release, openSec, assistSec, moveSec;
    LongSupplier clock = System::nanoTime;

    private boolean started = false;       // START was pressed: the feed stop may move
    private double sent = Double.NaN;      // the last position sent to the feed stop
    private boolean open = false;
    private boolean feeding = false;
    private double feedAt = 0;             // when the feed began, seconds
    private boolean indexerStarted = false;
    private boolean mustOpen = false;      // the feed stop was closed at the feed: give it time to open
    private int shotsAtFeed = 0;
    private String lastEnd = "";           // "shot seen" or "backup time": how the last feed ended

    ShotGate(Servo stop, double hold, double release, double openSec, Feeder indexer, List<Intake> pushers,
             double assistSec, double moveSec, Flywheel flywheel, BooleanSupplier stream) {
        this.stop = stop;
        this.hold = hold;
        this.release = release;
        this.openSec = openSec;
        this.indexer = indexer;
        this.pushers = pushers;
        this.assistSec = assistSec;
        this.moveSec = stop == null ? 0 : moveSec;
        this.flywheel = flywheel;
        this.stream = stream;
    }

    private double now() {
        return clock.getAsLong() / 1e9;
    }

    /** START: the feed stop goes to hold. */
    void start() {
        started = true;
        open = false;
        send(hold);
    }

    @Override
    public void feedOne() {
        if (feeding) return;
        feeding = true;
        feedAt = now();
        indexerStarted = false;
        shotsAtFeed = flywheel.getShotCount();
        mustOpen = stop != null && !(started && sent == release);
        open = stop != null;
        // At once, not at the next update: the stop starts opening and the pushers start this loop.
        if (stop != null && started) send(release);
        if (assistSec > 0) for (Intake p : pushers) p.pushForShot(true);
    }

    @Override
    public boolean isBusy() {
        return feeding || (indexer != null && indexer.isBusy());
    }

    @Override
    public void update() {
        double t = now();
        if (feeding) {
            // The indexer pushes once the feed stop has had time to open.
            if (indexer != null && !indexerStarted && t - feedAt >= (mustOpen ? moveSec : 0)) {
                indexer.feedOne();
                indexerStarted = true;
            }
            boolean shot = flywheel.getShotCount() > shotsAtFeed;
            double limit = stop != null ? openSec : assistSec;
            // The shot (or the backup time) ends the feed and closes the stop at once, even while the
            // indexer is still coming back: isBusy() still waits for it before the next piece.
            boolean indexerGoing = indexer == null || indexerStarted;
            if ((shot || t - feedAt >= limit) && indexerGoing) {
                feeding = false;
                lastEnd = shot ? "shot seen" : "backup time";
            }
        }
        // The feed stop: open while feeding, and between pieces of a stream; else closed.
        if (!feeding) open = open && stream.getAsBoolean();
        if (stop != null && started) send(open ? release : hold);
        // The pushing parts run during the feed, at most assistSec (a stream reopens them for each piece).
        boolean push = feeding && t - feedAt < Math.max(assistSec, 0);
        for (Intake p : pushers) p.pushForShot(push);
        if (indexer != null) indexer.update();
    }

    // Sends a position only when it changes (each servo write takes time on the hub).
    private void send(double position) {
        if (stop == null || position == sent) return;
        sent = position;
        stop.setPosition(position);
    }

    @Override
    public boolean isJammed() {
        return indexer != null && indexer.isJammed();
    }

    @Override
    public void clearJam() {
        if (indexer != null) indexer.clearJam();
    }

    @Override
    public void stop() {
        feeding = false;
        open = false;
        for (Intake p : pushers) p.pushForShot(false);
        if (stop != null && started) send(hold);
        if (indexer != null) indexer.stop();
    }

    /**
     * An action was cancelled: the feed ends now (an indexer that has not pushed yet never pushes), the
     * stop closes, the pushers go back.
     */
    void cancel() {
        feeding = false;
        open = false;
        for (Intake p : pushers) p.pushForShot(false);
        if (stop != null && started) send(hold);
    }

    /** True while the feed stop is open (or opening). */
    boolean isOpen() {
        return stop != null && open;
    }

    @Override
    public String name() {
        return "Feed";
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (stop != null) t.debug("Feed stop", open ? "open" : "hold");
        if (!lastEnd.isEmpty()) t.debug("Last feed ended", lastEnd);
        if (indexer != null) indexer.addTelemetry(t);
    }
}
