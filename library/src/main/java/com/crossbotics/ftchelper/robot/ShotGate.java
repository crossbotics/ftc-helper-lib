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
 * <p>A feed wheel ({@code Launcher.feedWheel(...)}) moves the pieces up to the indexer. Only while firing
 * (the default): each feed starts it, the indexer pushes {@code stageSec} later, and it stops
 * {@code stopSec} after that push starts (below 0: before it). Or {@code alwaysOn}: it runs while the
 * launcher is spun up or ejects (not while idle), and the indexer pushes at once. With a feed stop that must open,
 * the indexer also waits for it ({@code feedStopMoveSec}).
 * The next feed waits until the wheel stops.
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
    private final Indexer wheel;           // the feed wheel (Launcher.feedWheel), or null
    private final BooleanSupplier spunUp;  // the launcher is spun up (not idle) or ejecting: an always-on wheel runs
    private final double hold, release, openSec, assistSec, moveSec;
    // True for a servo indexer (it pushes, then comes back by itself); false for a roller (CR servo, motor).
    private final boolean pusherIndexer;
    LongSupplier clock = com.crossbotics.ftchelper.core.Time::nanos;

    private boolean started = false;       // START was pressed: the feed stop may move
    private double sent = Double.NaN;      // the last position sent to the feed stop
    private boolean open = false;
    private boolean feeding = false;
    private double feedAt = 0;             // when the feed began, seconds
    private boolean indexerStarted = false;
    private boolean mustOpen = false;      // the feed stop was closed at the feed: give it time to open
    private double rpmAtFeed = Double.NaN; // the wheel's speed when the feed began (a drop from it = the shot)
    private int shotsAtFeed = 0;
    private String lastEnd = "";           // "shot seen" or "backup time": how the last feed ended
    private double pushAfter = 0;          // seconds after the feed began that the indexer pushes
    private double pushAt = 0;             // when the indexer pushed, seconds
    private double wheelOffAt = -1;        // only while firing: when the feed wheel stops, seconds (-1 = off)
    private double wheelFrom = 0;          // only while firing: when the feed wheel starts, seconds
    private double wheelSent = Double.NaN; // the feed wheel's last power sent (NaN = none yet)

    ShotGate(Servo stop, double hold, double release, double openSec, Feeder indexer, boolean pusherIndexer,
             List<Intake> pushers, double assistSec, double moveSec, Flywheel flywheel, BooleanSupplier stream,
             Indexer wheel, BooleanSupplier spunUp) {
        this.wheel = wheel;
        this.spunUp = spunUp;
        this.pusherIndexer = pusherIndexer;
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
        rpmAtFeed = Math.abs(flywheel.getRpm());
        mustOpen = stop != null && !(started && sent == release);
        open = stop != null;
        // The indexer pushes once the feed stop has had time to open, and (a feed wheel only while firing)
        // once the feed wheel has had stageSec to bring the piece up.
        pushAfter = mustOpen ? moveSec : 0;
        if (wheel != null && !wheel.wheelAlwaysOn()) {
            pushAfter = Math.max(pushAfter, wheel.stageSec());
            wheelFrom = feedAt;
            // Planned from the planned push; a stopSec of 0 or more is set again from the real push (it can
            // come later, after a slow loop), so the wheel always runs that long with the arm.
            wheelOffAt = feedAt + pushAfter + wheel.stopSec();
            // At once, not at the next update: the staging time counts from now, so the wheel must start now.
            // (A stopSec below -stageSec: it never runs.)
            if (wheelOffAt > feedAt) sendWheel(true);
        }
        // At once, not at the next update: the stop starts opening. The pushers wait until it is open
        // (they would push the pieces against a closed stop).
        if (stop != null && started) send(release);
        if (assistSec > 0 && !mustOpen) for (Intake p : pushers) p.pushForShot(true);
    }

    @Override
    public boolean isBusy() {
        // A feed wheel only while firing: busy until it stops, so the next shot cannot cut its stopSec short.
        return feeding || (indexer != null && indexer.isBusy())
                || (wheel != null && !wheel.wheelAlwaysOn() && wheelOffAt >= 0 && now() < wheelOffAt);
    }

    @Override
    public void update() {
        double t = now();
        if (feeding) {
            // The indexer pushes once the feed stop is open and the feed wheel has staged the piece.
            if (indexer != null && !indexerStarted && t - feedAt >= pushAfter) {
                indexer.feedOne();
                indexerStarted = true;
                pushAt = t;
                if (wheel != null && !wheel.wheelAlwaysOn() && wheel.stopSec() >= 0) wheelOffAt = t + wheel.stopSec();
            }
            // The shot: the wheel's own counter, or (QUICK and SPRAY feed before the wheel is "ready", when
            // the counter is not armed) a drop from the speed it had when the feed began.
            boolean shot = flywheel.getShotCount() > shotsAtFeed
                    || (!Double.isNaN(rpmAtFeed) && rpmAtFeed - Math.abs(flywheel.getRpm()) > flywheel.getShotDrop());
            double limit = stop != null ? openSec : assistSec;
            // A feed wheel with a push arm and no feed stop: the arm takes longer than assistSec to lift the
            // piece into the wheel, so wait at least openSec (0.5 s) for the shot.
            if (wheel != null && pusherIndexer) limit = Math.max(limit, openSec);
            // With a feed wheel the backup time counts from the push (the staging time is not part of it).
            double since = wheel != null && indexerStarted ? t - pushAt : t - feedAt;
            // The shot (or the backup time) ends the feed and closes the stop at once, even while the
            // indexer is still coming back: isBusy() still waits for it before the next piece.
            boolean indexerGoing = indexer == null || indexerStarted;
            if ((shot || since >= limit) && indexerGoing) {
                feeding = false;
                lastEnd = shot ? "shot seen" : "backup time";
                // A feed stop closing: a roller or belt indexer (a CR servo, a motor) stops now, so it does
                // not push the next piece against the closed stop. A servo pusher finishes its return.
                if (stop != null && indexer != null && indexer.isBusy() && !pusherIndexer) indexer.stop();
            }
        }
        // The feed stop: open while feeding, and between pieces of a stream; else closed.
        if (!feeding) open = open && stream.getAsBoolean();
        if (stop != null && started) send(open ? release : hold);
        // The pushing parts run during the feed once the stop is open, for at most assistSec.
        double pushFrom = mustOpen ? moveSec : 0;
        boolean push = feeding && t - feedAt >= pushFrom && t - feedAt < pushFrom + Math.max(assistSec, 0);
        for (Intake p : pushers) p.pushForShot(push);
        if (indexer != null) indexer.update();
        // The feed wheel: always on while the flywheel is on, or only from the feed's start to stopSec
        // after the push.
        if (wheel != null) {
            boolean on = wheel.wheelAlwaysOn() ? started && spunUp.getAsBoolean()
                    : wheelOffAt >= 0 && t >= wheelFrom && t < wheelOffAt;
            sendWheel(on);
        }
    }

    // Sends the feed wheel's power only when it changes (on/off, or a new power from the tuning menu).
    private void sendWheel(boolean on) {
        if (wheel == null) return;
        double p = on ? wheel.wheelPower() : 0;
        if (p == wheelSent) return;
        wheelSent = p;
        wheel.runWheel(p);
    }

    /** True while a feed waits to push (the feed stop opening, the feed wheel staging): its piece has not gone. */
    boolean waitingToPush() {
        return feeding && indexer != null && !indexerStarted;
    }

    /** True while the feed wheel runs (false without one). */
    boolean isWheelOn() {
        return !Double.isNaN(wheelSent) && wheelSent != 0;
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
        wheelOffAt = -1;
        wheelSent = Double.NaN;
        if (wheel != null) wheel.runWheel(0);
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
        wheelOffAt = -1;   // only while firing: it stops (always on: it goes on running with the flywheel)
        if (wheel != null && !wheel.wheelAlwaysOn()) sendWheel(false);
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
        if (wheel != null) t.debug("Feed wheel", (isWheelOn() ? "on" : "off") + (wheel.wheelAlwaysOn() ? " (always on)" : " (while firing)"));
        if (!lastEnd.isEmpty()) t.debug("Last feed ended", lastEnd);
        if (indexer != null) indexer.addTelemetry(t);
    }
}
