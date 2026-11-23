package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.finders.GroupShotResults.Ball;
import com.crossbotics.ftchelper.finders.GroupShotResults.Check;
import com.crossbotics.ftchelper.finders.GroupShotResults.Group;
import com.crossbotics.ftchelper.launcher.ContinuousFire;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Group Shot Tuner's brain, with no hardware: what comes next (place the robot, a check shot, a group with
 * one setting), what a finished group means (counted or not), the person's entry, the advice and the winner.
 * The OpMode ({@link GroupShotTunerOpMode}) moves the robot and tells it what happened. WHY apart: the whole
 * plan, every reason a group does not count and the winner can be tested on made-up groups in a fraction of a
 * second.
 *
 * <p>What comes next is worked out from the saved shots and groups alone ({@link GroupShotResults}), so a
 * session that was stopped, or a robot that was turned off, goes on where it was.
 *
 * <p>The plan, round by round. Round 1 tests each setting from the Flywheel Tuner (its best PID, its best
 * bang-bang, and today's settings when they differ) with no boost and the full feed. At each spot, in order:
 * 4 check shots with today's settings (3 must score, or that spot stops: fix the table or the aim first), then
 * groups of 4 balls, taking turns between the settings, until each has 3 groups that count. A setting wins when
 * every ball scored in every group at every spot. When none wins, the next round tries the next step of each
 * setting: a boost of 50, then 100 RPM, only after groups that went short while the wheel lost speed; a slower
 * feed (90%, then 80%) only when no full-pace step is left. Docs: docs/tuning/14-group-shot-tuner.md.
 */
final class GroupShotSession {

    // ---- The rules (docs/tuning/14-group-shot-tuner.md lists them for students) ----

    /** Check shots at each spot, and how many must score. */
    static final int CHECK_SHOTS = 4, CHECK_PASS = 3;
    /** Balls in one group. */
    static final int BALLS = 4;
    /** Groups that count, for each setting at each spot. */
    static final int GROUPS = 3;
    /** The camera's distance must be this close to the spot, inches. */
    static final double SPOT_TOLERANCE_IN = 1.5;
    static final double COUNTDOWN_SEC = 3;
    /** The sticks drive at this share of full power, only on the "place the robot" screen. */
    static final double PLACE_POWER = 0.3;
    /** The boosts tried, in order, RPM. */
    static final double[] BOOSTS = {0, 50, 100};
    /** The feed powers tried, in order, as a share of the feeder's own. */
    static final double[] FEEDS = {1, 0.9, 0.8};
    private static final double EPS = 1e-9;

    /** Why a group that fed does not count. Each is not scored, and the group runs again. */
    enum Invalid {
        NOT_AIMED_AT_START("NOT aimed when the feed started"),
        AIM_DRIFT("the aim moved off the goal"),
        GOAL_LOST("the camera lost the goal"),
        SETTING_CHANGED("a setting changed during it"),
        ENDED_EARLY("it stopped early");

        final String words;

        Invalid(String words) {
            this.words = words;
        }
    }

    /** One speed-control setting to test: from the Flywheel Tuner, or today's. */
    static final class Candidate {
        /** "pid", "bang" or "today": in the saved file. */
        final String key;
        final FlywheelSetting setting;
        /** Plain words: "best PID", "best bang-bang", "today's (PID)". */
        final String name;
        /** From the Flywheel Tuner: seconds back at speed after a pretend shot, overshoot RPM, seconds at full power. */
        final double recoverySec, overRpm, fullSec;

        Candidate(String key, FlywheelSetting setting, String name, double recoverySec, double overRpm, double fullSec) {
            this.key = key;
            this.setting = setting;
            this.name = name;
            this.recoverySec = recoverySec;
            this.overRpm = overRpm;
            this.fullSec = fullSec;
        }
    }

    /** A candidate with a boost and a feed power: what one group feeds with. */
    static final class Variant {
        final Candidate cand;
        /** Extra target from the moment the feed starts, RPM. */
        final double boostRpm;
        /** The feed power, as a share of the feeder's own (1 = full pace). */
        final double feedShare;

        Variant(Candidate cand, double boostRpm, double feedShare) {
            this.cand = cand;
            this.boostRpm = boostRpm;
            this.feedShare = feedShare;
        }

        /** "pid:50:100": the candidate, the boost (RPM) and the feed (percent). */
        String key() {
            return cand.key + ":" + Math.round(boostRpm) + ":" + Math.round(feedShare * 100);
        }

        /** "best PID, boost 50 RPM, feed 90%". */
        String label() {
            String s = cand.name;
            if (boostRpm > 0) s += ", boost " + Math.round(boostRpm) + " RPM";
            if (feedShare < 1 - EPS) s += ", feed " + Math.round(feedShare * 100) + "%";
            return s;
        }

        boolean fullPace() {
            return feedShare >= 1 - EPS;
        }
    }

    /** The robot's numbers the plan depends on (read by the OpMode). */
    static final class Config {
        /** The spots: the camera's distance to the goal, inches, in order. */
        double[] spots = {27, 36, 45};
        /** What to test, in order. Today's settings are first only when they are the only one. */
        final List<Candidate> candidates = new ArrayList<>();
        /** Today's settings: the check shots use them. */
        FlywheelSetting today;
        /**
         * One ball's speed drop when the check shots did not measure it (shotDropRpm), RPM. The check shots measure
         * the real drop at each spot: that one is used when there is one.
         */
        double dropRpm = 150;
        /** The ready band (readyBandRpm): the wheel's normal wobble, RPM. */
        double readyBandRpm = 60;
        /** Anything else the saved shots depend on (the table, the aim offset...), as text. */
        String extra = "";

        /** The settings the shots depend on, as one line (the saved file must match it). */
        String setup() {
            StringBuilder sb = new StringBuilder("spots=");
            for (int i = 0; i < spots.length; i++) sb.append(i == 0 ? "" : ",").append(String.format(Locale.US, "%.2f", spots[i]));
            sb.append(" drop=").append(String.format(Locale.US, "%.1f", dropRpm));
            sb.append(" band=").append(String.format(Locale.US, "%.1f", readyBandRpm));
            sb.append(" today=").append(today == null ? "-" : today.text().replace(' ', '_'));
            for (Candidate c : candidates) sb.append(" ").append(c.key).append('=').append(c.setting.text().replace(' ', '_'));
            if (!extra.isEmpty()) sb.append(" ").append(extra);
            return sb.toString();
        }
    }

    /** What the plan asks for next. */
    enum Kind { CHECK, GROUP, CHECK_FAILED, FINISHED }

    /** The next thing to do, worked out from the saved shots and groups. */
    static final class Step {
        final Kind kind;
        final int spot, round;
        /** The setting a group feeds with (null for a check shot). */
        final Variant variant;
        /** The round's settings (FINISHED: the last round's). */
        final List<Variant> variants;
        /** FINISHED: the winners, best first (empty: no winner). */
        final List<Variant> winners;

        Step(Kind kind, int spot, int round, Variant variant, List<Variant> variants, List<Variant> winners) {
            this.kind = kind;
            this.spot = spot;
            this.round = round;
            this.variant = variant;
            this.variants = variants;
            this.winners = winners;
        }
    }

    /** Where the person is. */
    enum Stage { READY, COUNTDOWN, FIRING, ENTRY, STOPPED, FINISHED }

    /** What stopped a countdown or a shot. */
    enum Stop { NONE, BUTTON, HELP, ENCODER }

    /** What happened in one group, from the OpMode's log of every loop while it fed. */
    static final class GroupRun {
        /** True when the feeder ran (else: no shot, the balls are still in). */
        boolean fed;
        /** What ended it. MAX_TIME is the planned end. */
        ContinuousFire.End end = ContinuousFire.End.NONE;
        /** The end in words (the screen's "Firing stopped: ..." line). */
        String endWords = "";
        /** True when the aim was inside its tolerance in the loop the feed started. */
        boolean aimedAtStart = true;
        /** True when a loop of the feed had no fresh goal. */
        boolean goalMissed = false;
        /** The worst aim error while it fed, and the limit (the tolerance + the lock margin), degrees. */
        double worstAimDeg = 0, aimLimitDeg = 2;
        /** The lowest speed while it fed, and piece 1's target, RPM. */
        double lowRpm = Double.NaN, targetRpm = Double.NaN;
        /** How long the feeder was told to run, seconds. */
        double feedSec = 0;
    }

    private final Config config;
    private GroupShotResults results;
    private Stage stage = Stage.READY;
    private Stop stopWhy = Stop.NONE;
    private double now = 0, countdownEnd = Double.NaN;
    // The spot the robot was placed at in this session (-1: none yet; a new OpMode places it again).
    private int placedSpot = -1;
    // A spot whose check shots failed: A asked to shoot them again.
    private int recheckSpot = -1;
    private String refusal = "", note = "";
    private int saves = 0;
    private List<List<Variant>> rounds = new ArrayList<>();
    // The step that is firing or waiting for its entry.
    private Step firing = null;
    private GroupRun pendingRun = null;
    private double pendingDrop = Double.NaN;
    // The entry: the line the arrow is on, and the balls entered on each line.
    private int cursor = 0;
    private final int[] counts = new int[Ball.values().length];

    GroupShotSession(Config config, GroupShotResults results) {
        this.config = config;
        this.results = results;
        if (plan().kind == Kind.FINISHED) stage = Stage.FINISHED;
    }

    // ---------------------------------------------------------------- the plan

    /** The settings round 1 tests: each candidate, no boost, full feed. */
    List<Variant> firstRound() {
        List<Variant> out = new ArrayList<>();
        for (Candidate c : config.candidates) out.add(new Variant(c, 0, 1));
        return out;
    }

    /** What comes next, from the saved shots and groups. */
    Step plan() {
        List<List<Variant>> all = new ArrayList<>();
        Step s = plan(all);
        rounds = all;
        return s;
    }

    /** The settings of each round so far (round 1 first), as the last {@link #plan()} worked them out. */
    List<List<Variant>> rounds() {
        plan();
        return rounds;
    }

    private Step plan(List<List<Variant>> all) {
        List<Variant> round = firstRound();
        Set<String> tried = new HashSet<>();
        for (int r = 1; ; r++) {
            all.add(round);
            for (Variant v : round) tried.add(v.key());
            for (int k = 0; k < config.spots.length; k++) {
                int check = checkState(k);
                if (check == 0 || (check < 0 && recheckSpot == k)) return new Step(Kind.CHECK, k, r, null, round, null);
                if (check < 0) return new Step(Kind.CHECK_FAILED, k, r, null, round, null);
                Variant need = null;
                int fewest = GROUPS;
                for (Variant v : round) {
                    int n = validGroups(r, k, v).size();
                    if (n < fewest) {
                        fewest = n;
                        need = v;
                    }
                }
                if (need != null) return new Step(Kind.GROUP, k, r, need, round, null);
            }
            List<Variant> won = winners(r, round);
            if (!won.isEmpty()) return new Step(Kind.FINISHED, -1, r, null, round, won);
            List<Variant> next = new ArrayList<>();
            for (Variant v : nextRound(r, round, tried)) if (!tried.contains(v.key())) next.add(v);
            if (next.isEmpty()) return new Step(Kind.FINISHED, -1, r, null, round, Collections.<Variant>emptyList());
            round = next;
        }
    }

    /**
     * The check shots at a spot: 0 = not finished, 1 = the last 4 passed (3 or more scored), -1 = the last 4
     * failed (fix the table or the aim, then shoot 4 again).
     */
    int checkState(int spot) {
        List<Check> at = checksAt(spot);
        if (at.isEmpty() || at.size() % CHECK_SHOTS != 0) return 0;
        int scored = 0;
        for (Check c : at.subList(at.size() - CHECK_SHOTS, at.size())) if (c.ball == Ball.SCORED) scored++;
        return scored >= CHECK_PASS ? 1 : -1;
    }

    List<Check> checksAt(int spot) {
        List<Check> out = new ArrayList<>();
        for (Check c : results.checks()) if (c.spot == spot) out.add(c);
        return out;
    }

    /** The check shots of the set now being shot at this spot (0 to 4), and how many of those scored. */
    int[] checkSetNow(int spot) {
        List<Check> at = checksAt(spot);
        int rest = at.size() % CHECK_SHOTS;
        // A finished set is shown until A asks for a new one (then the new set starts at 0).
        int start = rest != 0 || at.isEmpty() || recheckSpot == spot ? at.size() - rest : at.size() - CHECK_SHOTS;
        int scored = 0;
        for (Check c : at.subList(start, at.size())) if (c.ball == Ball.SCORED) scored++;
        return new int[] {at.size() - start, scored};
    }

    /** The groups that count, of one setting, in one round, at one spot. */
    List<Group> validGroups(int round, int spot, Variant v) {
        List<Group> out = new ArrayList<>();
        for (Group g : results.groups()) {
            if (g.valid() && g.round == round && g.spot == spot && g.variant.equals(v.key())) out.add(g);
        }
        return out;
    }

    /** Every group that counts, of one setting in one round (all spots). */
    List<Group> validGroups(int round, Variant v) {
        List<Group> out = new ArrayList<>();
        for (Group g : results.groups()) if (g.valid() && g.round == round && g.variant.equals(v.key())) out.add(g);
        return out;
    }

    /** True when every ball scored in every counted group at every spot (3 groups at each). */
    boolean passes(int round, Variant v) {
        for (int k = 0; k < config.spots.length; k++) {
            List<Group> gs = validGroups(round, k, v);
            if (gs.size() < GROUPS) return false;
            for (Group g : gs) if (g.count(Ball.SCORED) != BALLS) return false;
        }
        return true;
    }

    /** The settings of a round that won, best first: the faster feed, then the Flywheel Tuner's quicker recovery, then gentler. */
    List<Variant> winners(int round, List<Variant> vs) {
        List<Variant> out = new ArrayList<>();
        for (Variant v : vs) if (passes(round, v)) out.add(v);
        Collections.sort(out, BETTER_FIRST);
        return out;
    }

    /** The winner order: the faster commanded feed, the shorter Flywheel Tuner recovery, less boost, less overshoot, less full power. */
    static final Comparator<Variant> BETTER_FIRST = (a, b) -> {
        int c = Double.compare(b.feedShare, a.feedShare);
        if (c == 0) c = smallerFirst(a.cand.recoverySec, b.cand.recoverySec);
        if (c == 0) c = Double.compare(a.boostRpm, b.boostRpm);
        if (c == 0) c = smallerFirst(a.cand.overRpm, b.cand.overRpm);
        if (c == 0) c = smallerFirst(a.cand.fullSec, b.cand.fullSec);
        return c;
    };

    // Smaller first; NaN (not measured) last.
    private static int smallerFirst(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) return Double.isNaN(a) == Double.isNaN(b) ? 0 : Double.isNaN(a) ? 1 : -1;
        return Double.compare(a, b);
    }

    /** True when a counted group went short while the wheel lost more than one ball's speed. */
    boolean shortWithSag(Group g) {
        return g.count(Ball.SHORT) > 0 && sagged(g);
    }

    /**
     * True when the wheel fell more than one ball's drop (and its normal wobble, the ready band) under the target
     * while it fed: it did not get back to speed between two balls.
     */
    boolean sagged(Group g) {
        return g.lowRpm < g.targetRpm - oneBallDrop(g.spot) - config.readyBandRpm;
    }

    /**
     * One ball's drop at this spot, RPM: the average the check shots measured there (how far a ball pulled the
     * wheel under its target), or shotDropRpm when they measured none.
     */
    double oneBallDrop(int spot) {
        double sum = 0;
        int n = 0;
        for (Check c : checksAt(spot)) {
            if (c.dropRpm > 0) {
                sum += c.dropRpm;
                n++;
            }
        }
        return n == 0 ? config.dropRpm : sum / n;
    }

    /**
     * The next round's settings, when no setting of this round won. Full pace first: a boost step (0, 50, 100
     * RPM) for a setting whose groups went short while the wheel lost speed, and never went long. Only when no
     * such step is left: a slower feed (90%, then 80%), with no boost, for EVERY candidate, also one that a
     * boost round left out (a setting that missed sideways must still get its slower feed).
     *
     * @param tried the keys of every setting tried so far ({@link Variant#key()})
     */
    List<Variant> nextRound(int round, List<Variant> vs, Set<String> tried) {
        List<Variant> out = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Variant v : vs) {
            if (!v.fullPace() || passes(round, v)) continue;
            boolean shortSag = false, isLong = false;
            for (Group g : validGroups(round, v)) {
                if (shortWithSag(g)) shortSag = true;
                if (g.count(Ball.LONG) > 0) isLong = true;
            }
            int i = indexOf(BOOSTS, v.boostRpm);
            if (shortSag && !isLong && i >= 0 && i + 1 < BOOSTS.length) {
                Variant n = new Variant(v.cand, BOOSTS[i + 1], 1);
                if (keys.add(n.key())) out.add(n);
            }
        }
        if (!out.isEmpty()) return out;
        for (Candidate c : config.candidates) {
            // The slowest feed tried so far with this candidate; the next step down from there.
            int slowest = -1;
            for (int i = 0; i < FEEDS.length; i++) {
                for (double b : BOOSTS) if (tried.contains(new Variant(c, b, FEEDS[i]).key())) slowest = i;
            }
            if (slowest < 0 || slowest + 1 >= FEEDS.length) continue;
            Variant n = new Variant(c, 0, FEEDS[slowest + 1]);
            if (keys.add(n.key())) out.add(n);
        }
        return out;
    }

    private static int indexOf(double[] list, double v) {
        for (int i = 0; i < list.length; i++) if (Math.abs(list[i] - v) < 1e-6) return i;
        return -1;
    }

    /**
     * What a group's misses say, judged on whole groups (never "ball 3"), in short lines: every ball scored;
     * short while the wheel lost speed (more recovery, or a boost); long (less boost); or a miss with the speed
     * and the aim held (the feed or the launcher: do not change the speed).
     */
    List<String> advice(int round, Variant v) {
        List<String> out = new ArrayList<>();
        List<Group> gs = validGroups(round, v);
        if (gs.isEmpty()) {
            out.add("No group counted yet.");
            return out;
        }
        int[] sum = sums(gs);
        boolean shortSag = false;
        for (Group g : gs) if (shortWithSag(g)) shortSag = true;
        if (sum[Ball.SCORED.ordinal()] == total(sum)) {
            out.add("Every ball scored.");
        } else if (shortSag) {
            out.add("Short, and the wheel lost speed.");
            out.add("Needs a quicker recovery, or a boost.");
        } else if (sum[Ball.LONG.ordinal()] > 0) {
            if (v.boostRpm > 0) {
                out.add("Long: less boost.");
            } else {
                out.add("Long with no boost: not the speed setting.");
                out.add("Check the shot table at that spot.");
            }
        } else {
            out.add("Missed with the speed and aim held:");
            out.add("the feed or the launcher. Same speed.");
        }
        return out;
    }

    /** The balls of these groups: scored, short, long, left, right. */
    static int[] sums(List<Group> gs) {
        int[] sum = new int[Ball.values().length];
        for (Group g : gs) for (Ball b : Ball.values()) sum[b.ordinal()] += g.count(b);
        return sum;
    }

    static int total(int[] sum) {
        int n = 0;
        for (int s : sum) n += s;
        return n;
    }

    /** The best score of all the settings tested ("scored", "balls", the variant), for a session with no winner. */
    Object[] bestScore() {
        Variant best = null;
        int bestScored = -1, bestBalls = 0;
        List<List<Variant>> all = rounds();
        for (int r = 1; r <= all.size(); r++) {
            for (Variant v : all.get(r - 1)) {
                int[] s = sums(validGroups(r, v));
                int balls = total(s);
                if (balls == 0) continue;
                // the larger share scored; a tie: the earlier (simpler) setting
                if (best == null || (long) s[0] * bestBalls > (long) bestScored * balls) {
                    best = v;
                    bestScored = s[0];
                    bestBalls = balls;
                }
            }
        }
        return best == null ? null : new Object[] {bestScored, bestBalls, best};
    }

    // ---------------------------------------------------------------- buttons

    /** True while something may move: the countdown or a shot. Any button then stops it. */
    boolean moving() {
        return stage == Stage.COUNTDOWN || stage == Stage.FIRING;
    }

    /** True when the robot must be placed at the next step's spot first (the sticks drive then). */
    boolean needsPlace() {
        if (stage != Stage.READY) return false;
        Step s = plan();
        return (s.kind == Kind.CHECK || s.kind == Kind.GROUP) && placedSpot != s.spot;
    }

    /**
     * A: place the robot here, start a shot (after the countdown), shoot the check shots again, or go on
     * after a stop.
     *
     * @param distanceIn the camera's distance to the goal now (averaged), NaN = the goal is not seen
     * @param blocked    why no shot may count now ("" = none): a saved aim offset, a speed trim...
     */
    void pressA(double distanceIn, String blocked) {
        if (moving()) return;
        refusal = "";
        if (stage == Stage.STOPPED) {
            stage = Stage.READY;
            stopWhy = Stop.NONE;
            return;
        }
        if (stage != Stage.READY) return;
        Step s = plan();
        if (s.kind == Kind.FINISHED) {
            stage = Stage.FINISHED;
            return;
        }
        if (s.kind == Kind.CHECK_FAILED) {
            recheckSpot = s.spot;
            note = "";
            return;
        }
        double spot = config.spots[s.spot];
        if (Double.isNaN(distanceIn)) {
            refusal = "Camera does NOT see the goal.\nTurn the robot to face the goal.";
            return;
        }
        double off = distanceIn - spot;
        if (placedSpot != s.spot) {
            if (Math.abs(off) > SPOT_TOLERANCE_IN) {
                refusal = "NOT at the spot: " + num(distanceIn, 1) + " in.\n" + moveWords(off);
                return;
            }
            placedSpot = s.spot;
            note = "";
            return;
        }
        if (!blocked.isEmpty()) {
            refusal = blocked;
            return;
        }
        if (Math.abs(off) > SPOT_TOLERANCE_IN) {
            placedSpot = -1;
            refusal = "The robot moved: " + num(distanceIn, 1) + " in.\nPlace it again.";
            return;
        }
        firing = s;
        stage = Stage.COUNTDOWN;
        countdownEnd = now + COUNTDOWN_SEC;
        note = "";
    }

    // "Drive 2.0 in CLOSER to the goal." / "Back 2.0 in AWAY from the goal."
    static String moveWords(double off) {
        return off > 0 ? "Drive " + num(off, 1) + " in CLOSER to the goal."
                : "Back " + num(-off, 1) + " in AWAY from the goal.";
    }

    /** Stops a countdown or a shot at once (a button, help, the end). Nothing starts again by itself. */
    void stop(Stop why) {
        if (!moving()) return;
        stage = Stage.STOPPED;
        stopWhy = why;
        firing = null;
    }

    /** One loop: the time in. The countdown ends here. */
    void update(double nowSec) {
        now = nowSec;
        if (stage == Stage.COUNTDOWN && now >= countdownEnd - EPS) stage = Stage.FIRING;
    }

    /**
     * The check shot is over: fed (then the person enters where it went), or not (it says why).
     *
     * @param fed    true when the ball was fed
     * @param whyNot what was missing ("NOT aimed at the goal"), when it was not fed
     */
    void singleDone(boolean fed, String whyNot) {
        singleDone(fed, whyNot, Double.NaN);
    }

    /**
     * {@link #singleDone(boolean, String)} with how far the ball pulled the wheel under its target.
     *
     * @param dropRpm the target minus the lowest speed after the feed, RPM; NaN = not measured
     */
    void singleDone(boolean fed, String whyNot, double dropRpm) {
        if (stage != Stage.FIRING) return;
        pendingDrop = dropRpm;
        if (fed) {
            startEntry();
        } else {
            stage = Stage.READY;
            note = "No shot: " + whyNot + ".\nThe ball is still in. Fix that, then {A}.";
            firing = null;
        }
    }

    /**
     * The group is over. Not fed: no shot, the balls are still in. Fed but it does not count ({@link #judge}):
     * kept as not counted, and the same group runs again. Fed and counted: the person enters where the balls went.
     */
    void groupDone(GroupRun run) {
        if (stage != Stage.FIRING || firing == null || firing.kind != Kind.GROUP) return;
        if (!run.fed) {
            stage = Stage.READY;
            note = "No shot: " + (run.endWords.isEmpty() ? "it did not start" : run.endWords) + ".\nThe balls are still in. Fix that, then {A}.";
            firing = null;
            return;
        }
        Invalid why = judge(run);
        if (why != null) {
            record(new Group(firing.round, firing.spot, firing.variant.key(), why, null, run.lowRpm, run.targetRpm,
                    run.feedSec, run.worstAimDeg, run.endWords));
            stage = Stage.READY;
            boolean cause = !run.endWords.isEmpty() && !run.endWords.equals(why.words);
            note = "NOT counted: " + why.words + ".\n" + (cause ? "Cause: " + run.endWords + ".\n" : "")
                    + "Load 4 balls. {A} = the same group again.";
            firing = null;
            return;
        }
        pendingRun = run;
        startEntry();
    }

    /** Why a group that fed does not count, or null when it counts. Checked in this order. */
    static Invalid judge(GroupRun r) {
        ContinuousFire.End e = r.end;
        if (e == ContinuousFire.End.CONTROL_CHANGED || e == ContinuousFire.End.TABLE_CHANGED
                || e == ContinuousFire.End.ALLIANCE_CHANGED) return Invalid.SETTING_CHANGED;
        if (!r.aimedAtStart) return Invalid.NOT_AIMED_AT_START;
        if (r.goalMissed || e == ContinuousFire.End.GOAL_LOST) return Invalid.GOAL_LOST;
        if (!(r.worstAimDeg <= r.aimLimitDeg + EPS) || e == ContinuousFire.End.AIM_LOST) return Invalid.AIM_DRIFT;
        if (e != ContinuousFire.End.MAX_TIME) return Invalid.ENDED_EARLY;
        return null;
    }

    private void startEntry() {
        stage = Stage.ENTRY;
        cursor = 0;
        java.util.Arrays.fill(counts, 0);
        refusal = "";
    }

    /** D-pad up / down in the entry: the arrow moves. */
    void entryMove(int direction) {
        if (stage != Stage.ENTRY) return;
        int n = Ball.values().length;
        cursor = ((cursor + direction) % n + n) % n;
    }

    /** D-pad right / left in a group's entry: one ball more / less on the line with the arrow. */
    void entryChange(int direction) {
        if (stage != Stage.ENTRY || !entryIsGroup()) return;
        int left = BALLS - total(counts);
        if (direction > 0 && left <= 0) {
            refusal = "That is all " + BALLS + " balls. Take one off a line first.";
            return;
        }
        counts[cursor] = Math.max(0, counts[cursor] + direction);
        refusal = "";
    }

    /** A in the entry: saves it (a group's 4 balls must all be entered). */
    void entrySave() {
        if (stage != Stage.ENTRY || firing == null) return;
        if (!entryIsGroup()) {
            Ball b = Ball.values()[cursor];
            record(new Check(firing.spot, b, pendingDrop));
            note = "Saved: check shot " + b.words + ".";
            int state = checkState(firing.spot);
            if (state < 0) note = "Check shots: only " + checkSetNow(firing.spot)[1] + " of " + CHECK_SHOTS + " scored.";
        } else {
            int t = total(counts);
            if (t != BALLS) {
                refusal = "Enter all " + BALLS + " balls: " + t + " so far.";
                return;
            }
            GroupRun r = pendingRun;
            record(new Group(firing.round, firing.spot, firing.variant.key(), null, counts, r.lowRpm, r.targetRpm,
                    r.feedSec, r.worstAimDeg, r.endWords));
            note = "Saved: " + countWords(counts) + ".";
        }
        firing = null;
        pendingRun = null;
        if (recheckSpot >= 0 && checkState(recheckSpot) != 0) recheckSpot = -1;
        stage = plan().kind == Kind.FINISHED ? Stage.FINISHED : Stage.READY;
    }

    /** X in the entry: not sure where they went. Nothing is saved; the same shot or group runs again. */
    void entryUnsure() {
        if (stage != Stage.ENTRY) return;
        firing = null;
        pendingRun = null;
        stage = Stage.READY;
        note = "Not saved (not sure). {A} repeats it.";
        refusal = "";
    }

    /** "3 scored, 1 short". */
    static String countWords(int[] c) {
        List<String> parts = new ArrayList<>();
        for (Ball b : Ball.values()) if (c[b.ordinal()] > 0) parts.add(c[b.ordinal()] + " " + b.words);
        return parts.isEmpty() ? "none" : FinderResults.commas(parts);
    }

    private void record(Check c) {
        results.add(c);
        saves++;
    }

    private void record(Group g) {
        results.add(g);
        saves++;
    }

    /** A new, empty session with the same robot numbers (the old file is kept by the OpMode). */
    void restart(GroupShotResults fresh) {
        results = fresh;
        stage = Stage.READY;
        placedSpot = -1;
        recheckSpot = -1;
        firing = null;
        pendingRun = null;
        note = refusal = "";
    }

    // ---------------------------------------------------------------- what the screen shows

    Stage stage() { return stage; }
    Stop stopWhy() { return stopWhy; }
    Config config() { return config; }
    GroupShotResults results() { return results; }
    /** Why A did nothing ("" = it did), in short lines. */
    String refusal() { return refusal; }
    /** What happened last, in short lines ("" = nothing to say). */
    String note() { return note; }
    /** Goes up each time a shot or group is saved: the OpMode then saves the file (off the loop). */
    int saves() { return saves; }
    /** The shot or group that is counting down, firing or waiting for its entry (null: none). */
    Step firing() { return firing; }
    /** The entry's arrow line, and the balls on each line. */
    int cursor() { return cursor; }
    int[] counts() { return counts.clone(); }
    /** True when the entry is for a group of 4 (else one check shot). */
    boolean entryIsGroup() { return firing != null && firing.kind == Kind.GROUP; }
    /** Seconds left in the countdown (whole seconds, at least 1). */
    int countdownLeft() { return (int) Math.max(1, Math.ceil(countdownEnd - now - EPS)); }
    /** The spot the robot was placed at in this session, -1 for none. */
    int placedSpot() { return placedSpot; }

    /** The groups that count, out of the round's total, at this spot: {done, needed}. */
    int[] groupsAt(int round, int spot, List<Variant> vs) {
        int done = 0;
        for (Variant v : vs) done += Math.min(GROUPS, validGroups(round, spot, v).size());
        return new int[] {done, GROUPS * vs.size()};
    }

    /** How many groups did not count, in all. */
    int notCounted() {
        int n = 0;
        for (Group g : results.groups()) if (!g.valid()) n++;
        return n;
    }

    static String num(double v, int decimals) {
        return FinderResults.num(v, decimals);
    }
}
