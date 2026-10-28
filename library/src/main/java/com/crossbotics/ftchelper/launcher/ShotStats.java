package com.crossbotics.ftchelper.launcher;

import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Firing stats: what each fed piece did to the flywheel, for tuning the shot modes. The
 * {@link Launcher} fills it in; its debug lines show it (PRACTICE profile only: at an event they are
 * noise). For each piece: the shot mode, the speed when it was fed, the speed drop, the time back to
 * "ready" (the recovery) and the time since the piece before it. After a volley: one line with the
 * average and the worst.
 *
 * <pre>
 * Shot 3        QUICK  fed at 2410/2450 rpm, drop 310, back in 0.21 s, 0.30 s after the last
 * Volley (4)    drop avg 290 worst 340, recovery avg 0.20 worst 0.26 s
 * </pre>
 *
 * Docs: docs/modules/launcher.md
 */
public final class ShotStats {

    /** One fed piece. Times in seconds, speeds in RPM. NaN = not known (yet). */
    public static final class Shot {
        /** The shot mode it was fed in. */
        public final Launcher.ShotMode mode;
        /** The wheel's speed and its target when the piece was fed. */
        public final double rpmAtFeed, targetRpm;
        /** Seconds since the piece before it in the same volley (NaN for a volley's first piece). */
        public final double gapSec;
        /** How far the speed fell below the target after the feed (RPM, 0 or more). */
        public double dropRpm = 0;
        /** Seconds from the feed until the wheel was ready again (NaN while it is not, or if the next piece came first). */
        public double recoverySec = Double.NaN;
        final double fedAt;

        Shot(Launcher.ShotMode mode, double fedAt, double gapSec, double rpmAtFeed, double targetRpm) {
            this.mode = mode;
            this.fedAt = fedAt;
            this.gapSec = gapSec;
            this.rpmAtFeed = rpmAtFeed;
            this.targetRpm = targetRpm;
        }
    }

    private final List<Shot> volley = new ArrayList<>();
    private final List<Shot> lastVolley = new ArrayList<>();
    private int count = 0;
    // The piece being watched (fed, wheel not ready again yet), or null.
    private Shot watching = null;
    // True once the wheel was seen NOT ready after the feed: only then does "ready again" mean recovered.
    private boolean dipped = false;

    // A piece was fed. sameVolley: it came soon after the last one.
    void fed(Launcher.ShotMode mode, double now, boolean sameVolley, double rpm, double target) {
        if (!sameVolley && !volley.isEmpty()) {
            lastVolley.clear();
            lastVolley.addAll(volley);
            volley.clear();
        }
        double gap = volley.isEmpty() ? Double.NaN : now - volley.get(volley.size() - 1).fedAt;
        Shot s = new Shot(mode, now, gap, rpm, target);
        volley.add(s);
        count++;
        watching = s;
        dipped = false;
    }

    // Every loop: the deepest drop, and the time until the wheel is ready again.
    void watch(double now, double rpm, boolean ready) {
        if (watching == null) return;
        if (!Double.isNaN(rpm)) watching.dropRpm = Math.max(watching.dropRpm, Math.abs(watching.targetRpm) - Math.abs(rpm));
        if (!ready) dipped = true;
        else if (dipped) {
            watching.recoverySec = now - watching.fedAt;
            watching = null;
        }
    }

    /** Pieces fed since the OpMode started. */
    public int count() {
        return count;
    }

    /** The newest piece, or null before the first. */
    public Shot last() {
        return volley.isEmpty() ? null : volley.get(volley.size() - 1);
    }

    /** The pieces of the volley going on now (or the newest one), oldest first (read only). */
    public List<Shot> volley() {
        return Collections.unmodifiableList(volley);
    }

    /** The volley before it, oldest first (read only; empty until a second volley starts). */
    public List<Shot> lastVolley() {
        return Collections.unmodifiableList(lastVolley);
    }

    /** "drop avg 290 worst 340, recovery avg 0.20 worst 0.26 s" for these pieces ("" for none). */
    public static String summary(List<Shot> shots) {
        if (shots.isEmpty()) return "";
        double dropSum = 0, dropWorst = 0, recSum = 0, recWorst = 0;
        int recN = 0;
        for (Shot s : shots) {
            dropSum += s.dropRpm;
            dropWorst = Math.max(dropWorst, s.dropRpm);
            if (!Double.isNaN(s.recoverySec)) {
                recSum += s.recoverySec;
                recWorst = Math.max(recWorst, s.recoverySec);
                recN++;
            }
        }
        String rec = recN == 0 ? "recovery -" : String.format(Locale.US, "recovery avg %.2f worst %.2f s", recSum / recN, recWorst);
        return String.format(Locale.US, "drop avg %.0f worst %.0f, %s", dropSum / shots.size(), dropWorst, rec);
    }

    /** The newest piece and the volley summary, as debug lines (PRACTICE profile only). */
    public void addTelemetry(RobotTelemetry t) {
        Shot s = last();
        if (s == null) return;
        t.debug("Shot " + count, String.format(Locale.US, "%s  fed at %.0f/%.0f rpm, drop %.0f, %s%s", s.mode,
                s.rpmAtFeed, s.targetRpm, s.dropRpm,
                Double.isNaN(s.recoverySec) ? "recovering" : String.format(Locale.US, "back in %.2f s", s.recoverySec),
                Double.isNaN(s.gapSec) ? "" : String.format(Locale.US, ", %.2f s after the last", s.gapSec)));
        if (volley.size() > 1) t.debug("Volley (" + volley.size() + ")", summary(volley));
        else if (!lastVolley.isEmpty()) t.debug("Last volley (" + lastVolley.size() + ")", summary(lastVolley));
    }
}
