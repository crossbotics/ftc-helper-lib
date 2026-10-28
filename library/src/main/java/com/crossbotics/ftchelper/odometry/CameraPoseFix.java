package com.crossbotics.ftchelper.odometry;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;

/**
 * <b>ALPHA, NOT TESTED ON A ROBOT.</b> Corrects the odometry's x and y with the field position a
 * Limelight works out from AprilTags (MegaTag2). Off by default. <b>Not for BIOBUZZ:</b> its only
 * field tags are under the CELLs, and the HIVEs tip, so the tags move during the match; a position
 * worked out from them is wrong whenever a HIVE tips. It is for a season with fixed wall tags.
 *
 * <p>Built from last season's lessons (docs/from-last-season.md): the odometry must be right on its
 * own; only the camera's correction is smoothed, never the wheel moves; each camera position is
 * compared with where the robot was <i>when the picture was taken</i> (not now); old and repeated
 * pictures are skipped; a big jump is refused; nothing is used while the robot moves fast. Every
 * decision is said on a debug line: "used +1.2, -0.4 in" or "refused: moving".
 *
 * <p>The Limelight's field frame is the field's own; ours is from the drivers' wall (x right, y away,
 * heading 0 = facing away). {@code turnDeg} turns one into the other (a multiple of 90 for a square
 * field, found with the Camera Fix Check in Tune and check): our (x, y) = the Limelight's turned
 * {@code turnDeg} counterclockwise, and our heading = the Limelight's yaw + turnDeg - 90.
 *
 * <p>It never waits and never moves anything: the robot in five files calls {@link #record} every
 * loop and {@link #offer} for each new picture, and applies what it returns.
 *
 * Docs: docs/modules/odometry.md, "Camera fix (alpha)"
 */
public final class CameraPoseFix {
    /** The settings, all from {@code Tunables.CameraFix}. */
    public static final class Settings {
        /** Off unless true. */
        public boolean enabled = false;
        /** Degrees from the Limelight's field frame to ours (BLUE and RED may differ). */
        public double turnDeg = 0;
        /** A picture older than this is skipped, milliseconds. */
        public double maxAgeMs = 100;
        /** Faster than this (inches per second) or turning faster than maxTurnDegPerSec: skipped. */
        public double maxSpeedInPerSec = 20, maxTurnDegPerSec = 30;
        /** A camera position farther than this from the odometry's is refused (inches). */
        public double maxJumpIn = 12;
        /** The share of the difference applied per picture (0.2 = a fifth). */
        public double blend = 0.2;
        /** At least this many tags in the picture. */
        public int minTags = 2;
    }

    /** What one picture did: the correction to add (0, 0 when refused) and the words for the debug line. */
    public static final class Result {
        public final boolean used;
        public final double dxIn, dyIn;
        public final String words;

        Result(boolean used, double dx, double dy, String words) {
            this.used = used;
            this.dxIn = dx;
            this.dyIn = dy;
            this.words = words;
        }
    }

    private final Settings s;
    // The odometry's poses of the last second: {nanos, x, y}, oldest first.
    private final ArrayDeque<double[]> history = new ArrayDeque<>();
    private Result last = new Result(false, 0, 0, "no picture yet");

    public CameraPoseFix(Settings settings) {
        s = settings;
    }

    /** Every loop: the odometry's pose now (our frame, inches), so a picture can be compared with its own moment. */
    public void record(long nanos, double xIn, double yIn) {
        history.addLast(new double[] {nanos, xIn, yIn});
        history.removeIf(h -> nanos - (long) h[0] > 1_000_000_000L);
    }

    /**
     * Our frame from the Limelight's: (x, y) turned {@code turnDeg} counterclockwise.
     *
     * @return {x, y} in inches
     */
    public double[] toOurs(double llXIn, double llYIn) {
        double a = Math.toRadians(s.turnDeg);
        return new double[] {llXIn * Math.cos(a) - llYIn * Math.sin(a), llXIn * Math.sin(a) + llYIn * Math.cos(a)};
    }

    /** The heading to send the Limelight (its yaw, degrees) for our heading: ours - turnDeg + 90. */
    public double limelightYaw(double ourHeadingDeg) {
        return wrap(ourHeadingDeg - s.turnDeg + 90);
    }

    /** Our heading for a Limelight yaw: yaw + turnDeg - 90. */
    public double ourHeading(double limelightYawDeg) {
        return wrap(limelightYawDeg + s.turnDeg - 90);
    }

    /**
     * The turnDeg that makes a Limelight yaw match our heading, to the nearest 90: for the Camera Fix
     * Check (the robot standing still, its heading right).
     */
    public static double suggestTurn(double ourHeadingDeg, double limelightYawDeg) {
        return wrap(Math.round((ourHeadingDeg - limelightYawDeg + 90) / 90.0) * 90.0);
    }

    /**
     * One NEW picture's position (the Limelight's frame, inches): the correction to add to the odometry,
     * or why not.
     *
     * @param pictureNanos when the picture was taken (System.nanoTime clock)
     * @param nowNanos     now
     * @param tags         tags in the picture
     * @param speedInPerSec  the robot's speed now
     * @param turnDegPerSec  its turn rate now
     */
    public Result offer(double llXIn, double llYIn, long pictureNanos, long nowNanos, int tags,
                        double speedInPerSec, double turnDegPerSec) {
        last = decide(llXIn, llYIn, pictureNanos, nowNanos, tags, speedInPerSec, turnDegPerSec);
        return last;
    }

    private Result decide(double llXIn, double llYIn, long pictureNanos, long nowNanos, int tags,
                          double speed, double turn) {
        if (!s.enabled) return refused("off (Tunables.CameraFix.enabled)");
        if (Double.isNaN(llXIn) || Double.isNaN(llYIn)) return refused("no position in the picture");
        if (tags < s.minTags) return refused(fmt("%d tag(s), needs %d", tags, s.minTags));
        double ageMs = (nowNanos - pictureNanos) / 1e6;
        if (ageMs > s.maxAgeMs) return refused(fmt("old picture (%.0f ms)", ageMs));
        if (Math.abs(speed) > s.maxSpeedInPerSec || Math.abs(turn) > s.maxTurnDegPerSec) return refused("moving");
        double[] then = poseAt(pictureNanos);
        if (then == null) return refused("no odometry pose from that moment");
        double[] cam = toOurs(llXIn, llYIn);
        double ex = cam[0] - then[0], ey = cam[1] - then[1];
        double err = Math.hypot(ex, ey);
        if (err > s.maxJumpIn) return refused(fmt("jump %.0f in (more than %.0f)", err, s.maxJumpIn));
        double b = Math.max(0, Math.min(1, s.blend));
        return new Result(true, ex * b, ey * b, fmt("used %+.1f, %+.1f in (%d tags, %.1f in off)", ex * b, ey * b, tags, err));
    }

    /**
     * A correction was applied to the odometry: the recorded poses move with it, so a later picture
     * (taken before it) does not count the same error again.
     */
    public void applied(double dxIn, double dyIn) {
        for (double[] h : history) {
            h[1] += dxIn;
            h[2] += dyIn;
        }
    }

    /** The last picture's decision (for the debug line). */
    public Result last() {
        return last;
    }

    // The recorded pose nearest to that moment (a loop is short: the nearest one is close enough).
    private double[] poseAt(long nanos) {
        double[] best = null;
        long bestGap = Long.MAX_VALUE;
        for (Iterator<double[]> it = history.iterator(); it.hasNext(); ) {
            double[] h = it.next();
            long gap = Math.abs((long) h[0] - nanos);
            if (gap < bestGap) {
                bestGap = gap;
                best = h;
            }
        }
        // Nothing within 0.1 s of the picture: we cannot say where the robot was.
        return best == null || bestGap > 100_000_000L ? null : new double[] {best[1], best[2]};
    }

    private static Result refused(String why) {
        return new Result(false, 0, 0, "refused: " + why);
    }

    private static double wrap(double deg) {
        double d = deg % 360;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return d;
    }

    private static String fmt(String f, Object... a) {
        return String.format(Locale.US, f, a);
    }
}
