package com.crossbotics.ftchelper.vision;

import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>How it works, setting by setting: ask the camera to switch to it; once the camera says the
 * switch is done ({@link #switched()}), wait a moment for it to settle, then look at the next few
 * pictures and count the ones that see at least one tag. After the last setting, the one with the
 * most pictures with tags wins (a tie goes to the one earlier in the list, so put your usual setting
 * first). If no setting saw enough tags (the robot was not facing any), it starts over and keeps
 * trying, all through INIT. <b>At START it stops</b> and keeps the best setting so far (the first
 * one if none saw a tag), so the robot never aims with pictures from a setting being tried. So: have
 * a tag in view at INIT, or the test cannot help.
 *
 * <p>A setting the camera refuses ({@link #switchFailed()}), or one that never sends pictures while
 * the camera still sends pictures from another setting (a Limelight pipeline that is not set up),
 * gets no score and is skipped in later rounds.
 *
 * <p>It never waits: {@link #update(int)} is one quick step per loop. Switching a setting is done by
 * the {@code apply} code you give it; the cameras send that to a background helper (see
 * docs/guides/non-blocking.md).
 *
 * <pre>{@code
 * LightingSweep sweep = new LightingSweep(Arrays.asList("bright", "normal", "dim"),
 *         i -> worker.submitLatest("pipeline", () -> limelight.pipelineSwitch(i)), System::nanoTime);
 * // every loop, INIT and after START:
 * if (firstPictureFromTheNewSetting) sweep.switched();
 * sweep.update(newPicture ? (fromTheSettingTried ? tagsInIt : LightingSweep.OTHER_SETTING) : LightingSweep.NO_PICTURE);
 * telemetry.addLine(sweep.status());     // "dim (9 of 10 pictures saw tags)"
 * }</pre>
 *
 * Docs: docs/modules/vision.md (section "Venue lighting")
 */
public final class LightingSweep {
    /** Give this to {@link #update(int)} when there is no new picture. */
    public static final int NO_PICTURE = -1;
    /** Give this to {@link #update(int)} for a new picture taken with another setting (the one before). */
    public static final int OTHER_SETTING = -2;

    private final List<String> labels;
    private final IntConsumer apply;
    private final LongSupplier clock;

    // The numbers (milliseconds and picture counts); see the setters for what each one does.
    private double settleMs = 300;
    private int pictures = 10;
    private double trialMs = 2000;
    private double switchTimeoutMs = 1500;
    private int minHits = 3;
    private double giveUpAfterStartMs = 0;

    // Where we are: which setting is being tried, since when, and what it saw.
    private int trying = -1;
    private long requestedNanos = 0;      // when the switch was asked for
    private long switchedNanos = -1;      // when the camera said it is done (-1: not yet)
    private int seen = 0, hits = 0, otherPictures = 0;
    private final int[] hitsOf, seenOf;
    // Settings that get no more tries: refused, or not there (e.g. a pipeline that is not set up).
    private final String[] skipWhy;
    private int rounds = 0;
    private int chosen = -1;
    private boolean gaveUp = false;
    private long startedNanos = -1;       // START (-1: still INIT)
    private long retryNanos = -1;         // restart() during the match

    /**
     * @param labels one short name per setting, in the order to try them (e.g. "pipeline 0").
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
        skipWhy = new String[labels.size()];
    }

    /** How long to wait after the camera has switched before counting pictures (milliseconds, default 300). */
    public LightingSweep settleMs(double ms) {
        settleMs = Math.max(0, ms);
        return this;
    }

    /** How many pictures to look at per setting (default 10). */
    public LightingSweep pictures(int count) {
        pictures = Math.max(1, count);
        return this;
    }

    /** The most time one setting gets once switched, settling included (milliseconds, default 2000). */
    public LightingSweep trialMs(double ms) {
        trialMs = Math.max(1, ms);
        return this;
    }

    /** The most time the camera gets to switch to a setting (milliseconds, default 1500). */
    public LightingSweep switchTimeoutMs(double ms) {
        switchTimeoutMs = Math.max(1, ms);
        return this;
    }

    /** How many pictures must see a tag for a setting to count as good (default 3). */
    public LightingSweep minPictures(int count) {
        minHits = Math.max(1, count);
        return this;
    }

    /**
     * After START, how long to keep trying when no setting has worked yet (milliseconds, default 0 =
     * stop at START and keep the best so far). While it tries after START, the cameras report no
     * targets, so nothing aims with a trial picture. Tunables: {@code lightingAfterStartMs}.
     */
    public LightingSweep giveUpAfterStartMs(double ms) {
        giveUpAfterStartMs = Math.max(0, ms);
        return this;
    }

    /** Tell it the match has started (the OpMode's START). With the default it stops now and keeps the best so far. */
    public void matchStarted() {
        if (startedNanos >= 0) return;
        startedNanos = clock.getAsLong();
        if (!isDone() && giveUpAfterStartMs <= 0) keepBestSoFar();
    }

    /** True after {@link #matchStarted()} (for the cameras: hide trial pictures during the match). */
    public boolean matchHasStarted() {
        return startedNanos >= 0;
    }

    /**
     * Forgets the result and tries every setting again (for a button, e.g. after moving to a new
     * field). During the match it gets {@link #giveUpAfterStartMs} from now (and the cameras report
     * no targets until it is done).
     */
    public void restart() {
        reset();
        if (startedNanos >= 0) retryNanos = clock.getAsLong();
    }

    /** A new camera session (the camera was closed and started again): everything as new, INIT again. */
    public void newSession() {
        reset();
        startedNanos = -1;
        retryNanos = -1;
    }

    private void reset() {
        trying = -1;
        chosen = -1;
        gaveUp = false;
        rounds = 0;
        Arrays.fill(hitsOf, 0);
        Arrays.fill(seenOf, 0);
        Arrays.fill(skipWhy, null);
    }

    /** The camera says it has the setting being tried now (its pictures are from it). Settling starts. */
    public void switched() {
        if (trying >= 0 && switchedNanos < 0) switchedNanos = clock.getAsLong();
    }

    /** The camera refused the setting being tried: it gets no score and is skipped from now on. */
    public void switchFailed() {
        if (trying < 0 || isDone()) return;
        skipWhy[trying] = "refused";
        finishTrial(clock.getAsLong());
    }

    /**
     * One quick step. Call it every loop, in INIT and after START, until {@link #isDone()}.
     *
     * @param tagsInNewPicture how many tags the newest picture saw; {@link #NO_PICTURE} when there is
     *                         no new picture; {@link #OTHER_SETTING} for a picture from another setting
     */
    public void update(int tagsInNewPicture) {
        if (isDone()) return;
        long now = clock.getAsLong();
        if (overTime(now)) {
            keepBestSoFar();
            return;
        }
        if (trying < 0) {          // the very first step: switch to the first setting
            next(0, now);
            return;
        }
        if (switchedNanos < 0) {
            // Not switched yet: count pictures still coming from the setting before.
            if (tagsInNewPicture != NO_PICTURE) otherPictures++;
            if (ms(now - requestedNanos) >= switchTimeoutMs) {
                // Pictures came, but never from this setting: it is not there (e.g. a pipeline not set up).
                // (3 or more, so the camera's first, empty answer at start-up does not count)
                if (otherPictures >= 3) skipWhy[trying] = "missing";
                finishTrial(now);
            }
            return;
        }
        double since = ms(now - switchedNanos);
        // Only pictures after the settle time, from this setting, count.
        if (since >= settleMs && tagsInNewPicture >= 0) {
            seen++;
            if (tagsInNewPicture > 0) hits++;
        }
        if (seen >= pictures || since >= trialMs) finishTrial(now);
    }

    /**
     * For a camera that is not ready yet (a webcam that is not streaming): no switching, only the
     * time limits are checked, so START still ends the test.
     */
    public void waitingForCamera() {
        if (!isDone() && overTime(clock.getAsLong())) keepBestSoFar();
    }

    // Past the time after START (or after an in-match restart)?
    private boolean overTime(long now) {
        long from = Math.max(startedNanos, retryNanos);
        return startedNanos >= 0 && giveUpAfterStartMs > 0 && ms(now - from) >= giveUpAfterStartMs;
    }

    // Done with one setting: note its score, then on to the next (or pick the winner).
    private void finishTrial(long now) {
        hitsOf[trying] = skipWhy[trying] == null ? hits : 0;
        seenOf[trying] = seen;
        int after = nextToTry(trying + 1);
        if (after >= 0) {
            next(after, now);
            return;
        }
        rounds++;
        int best = best();
        if (best >= 0 && hitsOf[best] >= minHits) {
            choose(best);
            return;
        }
        int first = nextToTry(0);
        if (first < 0) {           // every setting refused or missing: keep the first
            gaveUp = true;
            choose(0);
            return;
        }
        next(first, now);          // nothing good yet (no tag in view?): try them all again
    }

    // The first setting from i on that is not skipped, or -1.
    private int nextToTry(int from) {
        for (int i = from; i < labels.size(); i++) if (skipWhy[i] == null) return i;
        return -1;
    }

    // The setting with the most pictures with tags (">": a tie stays with the earlier one), or -1 if none saw any.
    private int best() {
        int best = -1;
        for (int i = 0; i < labels.size(); i++) if (hitsOf[i] > 0 && (best < 0 || hitsOf[i] > hitsOf[best])) best = i;
        return best;
    }

    // Stop now: keep the best so far, or the first setting if none saw a tag.
    private void keepBestSoFar() {
        // the setting being tried may have counted pictures already: let it compete
        if (trying >= 0 && skipWhy[trying] == null && switchedNanos >= 0 && hits > hitsOf[trying]) hitsOf[trying] = hits;
        int best = best();
        gaveUp = best < 0;
        choose(best < 0 ? 0 : best);
    }

    private void next(int index, long now) {
        trying = index;
        requestedNanos = now;
        switchedNanos = -1;
        seen = 0;
        hits = 0;
        otherPictures = 0;
        apply.accept(index);
    }

    private void choose(int index) {
        chosen = index;
        // Only switch if the camera is not already there (the setting tried last is the one it has).
        if (trying != index) apply.accept(index);
        trying = index;
    }

    private static double ms(long nanos) {
        return nanos / 1e6;
    }

    /** True once a setting is kept (found, or stopped at START). */
    public boolean isDone() {
        return chosen >= 0;
    }

    /** True if no setting saw a tag before it had to stop, so it kept the first one. */
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

    /** True while it is still looking for tags (a round found none: the robot should face a tag). */
    public boolean noTagsYet() {
        return !isDone() && rounds > 0;
    }

    /** The label of the kept setting, or null while still trying. */
    public String chosenLabel() {
        return chosen < 0 ? null : labels.get(chosen);
    }

    /** The settings, in the order they are tried. */
    public List<String> labels() {
        return labels;
    }

    /** How many pictures saw tags with setting i in its last try (0 if not tried yet, refused or missing). */
    public int hitsOf(int i) {
        return hitsOf[i];
    }

    /** How many pictures were counted with setting i in its last try. */
    public int picturesOf(int i) {
        return seenOf[i];
    }

    /** True if setting i is skipped (the camera refused it, or sent no pictures from it). */
    public boolean skipped(int i) {
        return skipWhy[i] != null;
    }

    /**
     * One line for the Driver Station, e.g. "dim (9 of 10 pictures saw tags)",
     * "trying 2 of 3: normal", "trying 1 of 3: normal (no tags yet: face a tag)", or
     * "NO TAGS SEEN: kept normal (have a tag in view at INIT next time)".
     */
    public String status() {
        if (gaveUp) return "NO TAGS SEEN: kept " + labels.get(chosen) + " (have a tag in view at INIT next time)";
        if (chosen >= 0) {
            return String.format(Locale.US, "%s (%d of %d pictures saw tags)", labels.get(chosen), hitsOf[chosen], seenOf[chosen]);
        }
        if (trying < 0) return "not started";
        String s = String.format(Locale.US, "trying %d of %d: %s", trying + 1, labels.size(), labels.get(trying));
        return rounds > 0 ? s + " (no tags yet: face a tag)" : s;
    }

    /** Problems to show, one per skipped setting, e.g. "no pictures from pipeline 2 (missing?)". */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            if ("missing".equals(skipWhy[i])) out.add("no pictures from " + labels.get(i) + " (missing?)");
            if ("refused".equals(skipWhy[i])) out.add("the camera refused " + labels.get(i));
        }
        return out;
    }

    /** Every setting's score, e.g. "bright 2/10, normal 9/10, dim 5/10" ("skipped" for a skipped one). */
    public String scores() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(labels.get(i)).append(' ');
            if (skipWhy[i] != null) b.append("skipped");
            else b.append(hitsOf[i]).append('/').append(seenOf[i]);
        }
        return b.toString();
    }
}
