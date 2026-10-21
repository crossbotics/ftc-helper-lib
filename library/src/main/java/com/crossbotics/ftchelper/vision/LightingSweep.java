package com.crossbotics.ftchelper.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/**
 * Tries a few camera settings one after the other, counts how many pictures see AprilTags with each,
 * and keeps the best one. Used for venue lighting: a gym, a practice room and a dark event hall need
 * different camera settings. {@link LimelightTags#autoPipelines} (one Limelight pipeline per setting)
 * and {@link WebcamTags#autoExposure} (one exposure per setting) use it; you rarely make one yourself.
 *
 * <p>How it works, setting by setting: switch to it, wait a moment for the camera to settle, then
 * look at the next few pictures and count the ones that see at least one tag. After the last setting,
 * the one with the most pictures with tags wins (a tie goes to the one earlier in the list, so put
 * your usual setting first). If no setting saw enough tags (the robot was not facing any), it starts
 * over, and keeps trying. After START it tries for {@link #giveUpAfterStartMs} more, then keeps the
 * FIRST setting, so the robot is never left on a strange one.
 *
 * <p>It never waits: {@link #update(int)} is one quick step per loop. Switching a setting is done by
 * the {@code apply} code you give it; the cameras send that to a background helper (see
 * docs/guides/non-blocking.md).
 *
 * <pre>{@code
 * LightingSweep sweep = new LightingSweep(Arrays.asList("bright", "normal", "dim"),
 *         i -> worker.submitLatest("pipeline", () -> limelight.pipelineSwitch(i)), System::nanoTime);
 * // every loop, INIT and after START:
 * sweep.update(newPicture ? tagsInIt : LightingSweep.NO_PICTURE);
 * telemetry.addLine(sweep.status());     // "lighting: dim (9 of 10 pictures saw tags)"
 * }</pre>
 *
 * Docs: docs/modules/vision.md (section "Venue lighting")
 */
public final class LightingSweep {
    /** Give this to {@link #update(int)} when there is no new picture (or it is from the old setting). */
    public static final int NO_PICTURE = -1;

    private final List<String> labels;
    private final IntConsumer apply;
    private final LongSupplier clock;

    // The numbers (milliseconds and picture counts); see the setters for what each one does.
    private double settleMs = 300;
    private int pictures = 10;
    private double trialMs = 2000;
    private int minHits = 3;
    private double giveUpAfterStartMs = 3000;

    // Where we are: which setting is being tried, since when, and what it saw.
    private int trying = -1;
    private long trialStartNanos = 0;
    private int seen = 0, hits = 0;
    private final int[] hitsOf, seenOf;
    private int rounds = 0;
    private int chosen = -1;
    private boolean gaveUp = false;
    private long startedNanos = -1;

    /**
     * @param labels one short name per setting, in the order to try them (e.g. "pipeline 0 bright").
     *               Put your usual setting FIRST: it wins ties and is kept if nothing sees a tag.
     * @param apply  switches the camera to setting number i (0 = the first label). Must not wait: send
     *               slow requests to a {@link com.crossbotics.ftchelper.util.BackgroundWorker}.
     * @param clockNanos the time now in nanoseconds; {@code System::nanoTime} on the robot, a pretend
     *               clock in tests
     */
    public LightingSweep(List<String> labels, IntConsumer apply, LongSupplier clockNanos) {
        if (labels == null || labels.isEmpty()) throw new IllegalArgumentException("LightingSweep: give at least one setting to try");
        this.labels = Collections.unmodifiableList(new ArrayList<>(labels));
        this.apply = apply;
        this.clock = clockNanos;
        hitsOf = new int[labels.size()];
        seenOf = new int[labels.size()];
    }

    /** How long to wait after switching before counting pictures (milliseconds, default 300). */
    public LightingSweep settleMs(double ms) {
        settleMs = Math.max(0, ms);
        return this;
    }

    /** How many pictures to look at per setting (default 10). */
    public LightingSweep pictures(int count) {
        pictures = Math.max(1, count);
        return this;
    }

    /** The most time one setting gets, settling included (milliseconds, default 2000). A slow camera may send fewer pictures. */
    public LightingSweep trialMs(double ms) {
        trialMs = Math.max(1, ms);
        return this;
    }

    /** How many pictures must see a tag for a setting to count as good (default 3). */
    public LightingSweep minPictures(int count) {
        minHits = Math.max(1, count);
        return this;
    }

    /** After START, how long to keep trying when no setting has worked yet (milliseconds, default 3000). */
    public LightingSweep giveUpAfterStartMs(double ms) {
        giveUpAfterStartMs = Math.max(0, ms);
        return this;
    }

    /** Tell it the match has started (the OpMode's START): from now on it gives up after {@link #giveUpAfterStartMs}. */
    public void matchStarted() {
        if (startedNanos < 0) startedNanos = clock.getAsLong();
    }

    /** Forgets the result and tries every setting again (for a button, e.g. after moving to a new field). */
    public void restart() {
        trying = -1;
        chosen = -1;
        gaveUp = false;
        rounds = 0;
        java.util.Arrays.fill(hitsOf, 0);
        java.util.Arrays.fill(seenOf, 0);
    }

    /**
     * One quick step. Call it every loop, in INIT and after START, until {@link #isDone()}.
     *
     * @param tagsInNewPicture how many tags the newest picture saw, or {@link #NO_PICTURE} when there
     *                         is no new picture (or it was taken with the setting before)
     */
    public void update(int tagsInNewPicture) {
        if (isDone()) return;
        long now = clock.getAsLong();
        if (trying < 0) {          // the very first step: switch to the first setting
            begin(0, now);
            return;
        }
        if (startedNanos >= 0 && ms(now - startedNanos) >= giveUpAfterStartMs) {
            // The match is on and nothing worked: keep the first setting (the team's usual one).
            gaveUp = true;
            choose(0);
            return;
        }
        double inTrial = ms(now - trialStartNanos);
        if (inTrial >= settleMs && tagsInNewPicture != NO_PICTURE) {
            seen++;
            if (tagsInNewPicture > 0) hits++;
        }
        if (seen >= pictures || inTrial >= trialMs) finishTrial(now);
    }

    // Done with one setting: note its score, then on to the next (or pick the winner).
    private void finishTrial(long now) {
        hitsOf[trying] = hits;
        seenOf[trying] = seen;
        if (trying + 1 < labels.size()) {
            begin(trying + 1, now);
            return;
        }
        rounds++;
        int best = 0;
        // ">" (not ">="): on a tie the earlier setting stays the winner.
        for (int i = 1; i < labels.size(); i++) if (hitsOf[i] > hitsOf[best]) best = i;
        if (hitsOf[best] >= minHits) {
            choose(best);
        } else {
            begin(0, now);            // nothing good yet (no tag in view?): try them all again
        }
    }

    private void begin(int index, long now) {
        trying = index;
        trialStartNanos = now;
        seen = 0;
        hits = 0;
        apply.accept(index);
    }

    private void choose(int index) {
        chosen = index;
        // Only switch if the camera is not already there (the last setting tried is the one it has).
        if (trying != index) apply.accept(index);
        trying = index;
    }

    private static double ms(long nanos) {
        return nanos / 1e6;
    }

    /** True once a setting is kept (found, or given up after START). */
    public boolean isDone() {
        return chosen >= 0;
    }

    /** True if it gave up (no setting saw tags before the time after START ran out) and kept the first one. */
    public boolean gaveUp() {
        return gaveUp;
    }

    /** The setting kept (0 = the first label), or -1 while still trying. */
    public int chosen() {
        return chosen;
    }

    /** The setting being tried now (or kept), -1 before the first update. */
    public int current() {
        return trying;
    }

    /** The label of the kept setting, or null while still trying. */
    public String chosenLabel() {
        return chosen < 0 ? null : labels.get(chosen);
    }

    /** The settings, in the order they are tried. */
    public List<String> labels() {
        return labels;
    }

    /** How many pictures saw tags with setting i in its last try (0 if not tried yet). */
    public int hitsOf(int i) {
        return hitsOf[i];
    }

    /** How many pictures were counted with setting i in its last try. */
    public int picturesOf(int i) {
        return seenOf[i];
    }

    /**
     * One line for the Driver Station, e.g. "dim (9 of 10 pictures saw tags)",
     * "trying 2 of 3: normal", or "NO TAGS SEEN: kept bright (point the camera at a tag)".
     */
    public String status() {
        if (gaveUp) return "NO TAGS SEEN: kept " + labels.get(0) + " (point the camera at a tag, then restart)";
        if (chosen >= 0) {
            return String.format(Locale.US, "%s (%d of %d pictures saw tags)", labels.get(chosen), hitsOf[chosen], seenOf[chosen]);
        }
        if (trying < 0) return "not started";
        String s = String.format(Locale.US, "trying %d of %d: %s", trying + 1, labels.size(), labels.get(trying));
        return rounds > 0 ? s + " (no tags yet: face a tag)" : s;
    }

    /** Every setting's score, e.g. "bright 2/10, normal 9/10, dim 5/10". */
    public String scores() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(labels.get(i)).append(' ').append(hitsOf[i]).append('/').append(seenOf[i]);
        }
        return b.toString();
    }
}
