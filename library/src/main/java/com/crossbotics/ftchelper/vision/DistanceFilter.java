package com.crossbotics.ftchelper.vision;

import java.util.Arrays;

/**
 * Keeps camera glitches out of a distance (for example the launcher's speed from the distance). A
 * camera sometimes gives one picture with a wrong distance: a tag half hidden, a blur, a reflection. Used
 * as it comes, that one picture changes the flywheel speed (and the hood) for a moment, and a shot fired
 * then goes long or short. Two checks, one after the other:
 *
 * <ol>
 * <li><b>The middle of the last few pictures</b> (the median, default the last 5): sort them and take
 *     the one in the middle. One or two bad pictures are then at the ends of the list and never used.
 *     It costs about 2 pictures of delay (0.07 s at 30 pictures a second).</li>
 * <li><b>The jump check</b>: a distance more than {@code jumpIn} (default 12 in) from the one in use is
 *     only used once it shows up in 3 pictures in a row, each within {@code agreeIn} (default 4 in) of
 *     the one before (a camera never gives the exact same number twice). A real move gets there in a
 *     moment; a glitch of a few pictures does not.</li>
 * </ol>
 *
 * <p><b>After a gap it starts over.</b> No new picture for {@code gapSec} (0.5 s): the goal was out of
 * view, and a big change is expected then (the HIVE tipped, the robot drove away for pieces). So the
 * first distance after it is used without the jump check, as soon as 3 pictures are in (so one glitch
 * as the goal comes into view is still kept out).
 *
 * <p>It never waits: {@link #add} is one quick step. Only NEW pictures count: give each picture once,
 * with its own time ({@link TargetObservation#timeNanos}); the same picture again is ignored.
 *
 * <pre>{@code
 * DistanceFilter filter = new DistanceFilter(5, 12, 4);
 * // every loop:
 * TargetObservation goal = camera.target("RED SCORING");
 * double d = filter.add(goal, System.nanoTime());     // NaN until it has a good distance
 * if (!Double.isNaN(d)) launcher.setDistance(d);       // else keep the speed you have
 * }</pre>
 *
 * Docs: docs/modules/vision.md#distancefilter
 */
public final class DistanceFilter {
    /** Pictures in a row a jump needs (each close to the one before) before it is used. */
    public static final int JUMP_PICTURES = 3;
    /** No new picture for this long (seconds): start over (no jump check for the next distance). */
    public static final double GAP_SEC = 0.5;

    private final int medianOf;
    private final double jumpIn, agreeIn;
    // The last pictures' distances (a ring: the oldest is replaced), and how many are in it.
    private final double[] last;
    private int count = 0, next = 0;
    // The distance in use (NaN: none yet, or after a gap), and a jump waiting to be sure.
    private double inUse = Double.NaN;
    private double jumpTo = Double.NaN;
    private int jumpSeen = 0;
    // The newest picture's time (to skip the same picture again) and when it came.
    private long lastPictureNanos = Long.MIN_VALUE;
    private long lastNewNanos = Long.MIN_VALUE;

    /**
     * @param medianOf how many pictures the middle is taken from, 1 to 15 (1 = off: each picture as it
     *                 comes). Default 5.
     * @param jumpIn   a change bigger than this (inches) needs {@link #JUMP_PICTURES} pictures to be
     *                 used; 0 = no jump check. Default 12.
     * @param agreeIn  how close (inches) each of those pictures must be to the one before. Default 4.
     */
    public DistanceFilter(int medianOf, double jumpIn, double agreeIn) {
        if (medianOf < 1 || medianOf > 15) throw new IllegalArgumentException("DistanceFilter: medianOf must be 1 to 15 (it is " + medianOf + ")");
        this.medianOf = medianOf;
        this.jumpIn = Math.max(0, jumpIn);
        this.agreeIn = Math.max(0, agreeIn);
        last = new double[medianOf];
    }

    /** The defaults: the middle of 5 pictures, jumps over 12 in need 3 pictures within 4 in. */
    public DistanceFilter() {
        this(5, 12, 4);
    }

    /**
     * One step: give the camera's goal now (null when it is not seen). Returns the distance to use
     * (inches), or NaN when there is none yet: keep what you had (the last speed).
     *
     * @param seen     the goal now, or null (no distance: the same as null)
     * @param nowNanos the time now (the clock the camera stamps its pictures with)
     * @return the filtered distance in inches, or NaN
     */
    public double add(TargetObservation seen, long nowNanos) {
        // A gap with no new picture: the goal was out of view. Start over.
        if (lastNewNanos != Long.MIN_VALUE && (nowNanos - lastNewNanos) / 1e9 >= GAP_SEC) reset();
        if (seen == null || !seen.hasDistance() || seen.timeNanos == lastPictureNanos) return inUse;
        lastPictureNanos = seen.timeNanos;
        lastNewNanos = nowNanos;
        last[next] = seen.distanceIn;
        next = (next + 1) % medianOf;
        if (count < medianOf) count++;
        // Not enough pictures yet to keep a glitch out (3, or fewer if medianOf is smaller).
        if (count < Math.min(JUMP_PICTURES, medianOf)) return inUse;
        double middle = median();
        if (Double.isNaN(inUse) || jumpIn <= 0 || Math.abs(middle - inUse) <= jumpIn) {
            inUse = middle;
            jumpSeen = 0;
            return inUse;
        }
        // A jump: count it while each picture agrees with the one before; use it after JUMP_PICTURES.
        if (jumpSeen > 0 && Math.abs(middle - jumpTo) <= agreeIn) jumpSeen++;
        else jumpSeen = 1;
        jumpTo = middle;
        if (jumpSeen >= JUMP_PICTURES) {
            inUse = middle;
            jumpSeen = 0;
        }
        return inUse;
    }

    /** The distance in use now (inches), NaN if none. */
    public double distance() {
        return inUse;
    }

    /** True while a jump is waiting to be sure (the distance in use is the one from before it). */
    public boolean jumpWaiting() {
        return jumpSeen > 0;
    }

    /** Forget everything (a new match, or the goal out of view): the next distance is used without the jump check. */
    public void reset() {
        count = 0;
        next = 0;
        inUse = Double.NaN;
        jumpSeen = 0;
        jumpTo = Double.NaN;
        lastNewNanos = Long.MIN_VALUE;
    }

    // The middle of the pictures in the list (for an even count: the mean of the two in the middle).
    private double median() {
        double[] sorted = Arrays.copyOf(last, count);
        Arrays.sort(sorted);
        int mid = count / 2;
        return count % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }
}
