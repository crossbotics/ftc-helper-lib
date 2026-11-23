package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.util.BackgroundWorker;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every finished check shot and group of the Group Shot Tuner, saved on the Control Hub in
 * {@code /sdcard/FIRST/ftc-helper/group-shot-tuner.txt}. The tuner saves after each one (on a helper thread,
 * never in the loop), so a stop, a dead battery or a power cycle loses at most the one that was running. It
 * reads the file back when it opens, and goes on from there: what comes next is worked out from this list.
 *
 * <p>The file is plain text, one line per shot or group. Do not edit it: the tuner checks that it was made
 * with the robot's settings as they are now ({@link #setup()}), and starts a new session when they changed.
 *
 * Docs: docs/tuning/14-group-shot-tuner.md
 */
final class GroupShotResults {

    /** Where one ball went. A ball that missed in two ways is entered as its bigger miss. */
    enum Ball {
        SCORED("scored"), SHORT("short"), LONG("long"), LEFT("to the LEFT"), RIGHT("to the RIGHT");

        final String words;

        Ball(String words) {
            this.words = words;
        }
    }

    /** One check shot: a single ball, aimed and at speed, with today's settings. */
    static final class Check {
        /** Which spot (0 = the first). */
        final int spot;
        /** Where it went. */
        final Ball ball;
        /** How far the ball pulled the wheel under its target, RPM (NaN: not measured). */
        final double dropRpm;

        Check(int spot, Ball ball, double dropRpm) {
            this.spot = spot;
            this.ball = ball;
            this.dropRpm = dropRpm;
        }
    }

    /** One group of 4 balls with one setting. An invalid group is kept (to show it) but never scored. */
    static final class Group {
        final int round, spot;
        /** The setting it fed with ({@link GroupShotSession.Variant#key()}). */
        final String variant;
        /** Why it does not count, or null when it counts. */
        final GroupShotSession.Invalid invalid;
        /** How many balls scored, went short, long, to the left, to the right (in {@link Ball} order). */
        final int[] balls;
        /** The lowest speed while it fed, and piece 1's target, RPM. */
        final double lowRpm, targetRpm;
        /** How long the feeder was told to run, seconds (not how long the balls took). */
        final double feedSec;
        /** The worst aim error while it fed, degrees (NaN: the goal was not seen). */
        final double worstAimDeg;
        /** What ended it, in plain words ("" = the planned end). */
        final String endWords;

        Group(int round, int spot, String variant, GroupShotSession.Invalid invalid, int[] balls, double lowRpm,
              double targetRpm, double feedSec, double worstAimDeg, String endWords) {
            this.round = round;
            this.spot = spot;
            this.variant = variant;
            this.invalid = invalid;
            this.balls = balls == null ? new int[Ball.values().length] : balls.clone();
            this.lowRpm = lowRpm;
            this.targetRpm = targetRpm;
            this.feedSec = feedSec;
            this.worstAimDeg = worstAimDeg;
            this.endWords = endWords == null ? "" : endWords;
        }

        /** True when it counts (valid and entered). */
        boolean valid() {
            return invalid == null;
        }

        int count(Ball b) {
            return balls[b.ordinal()];
        }

        int total() {
            int n = 0;
            for (int b : balls) n += b;
            return n;
        }
    }

    /** The file the tuner saves to and reads from. Tests use another one. */
    static File file = new File(FinderResults.folder(), "group-shot-tuner.txt");

    private final String setup;
    private final List<Check> checks = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private static BackgroundWorker saver;
    private static volatile boolean lastSaveOk = true;

    GroupShotResults(String setup) {
        this.setup = setup == null ? "" : setup;
    }

    static File file() {
        return file;
    }

    /** The robot's settings these shots were made with (one line), "" when unknown. */
    String setup() {
        return setup;
    }

    List<Check> checks() {
        return Collections.unmodifiableList(checks);
    }

    List<Group> groups() {
        return Collections.unmodifiableList(groups);
    }

    boolean isEmpty() {
        return checks.isEmpty() && groups.isEmpty();
    }

    void add(Check c) {
        checks.add(c);
    }

    void add(Group g) {
        groups.add(g);
    }

    /** Reads the saved file. It waits for the storage: call it at INIT, never in the loop. */
    static GroupShotResults load() {
        File from = file;
        File unfinished = new File(from.getParentFile(), from.getName() + ".new");
        if (!from.isFile() && unfinished.isFile()) from = unfinished;
        List<String> lines = new ArrayList<>();
        if (from.isFile()) {
            try (java.io.BufferedReader in = new java.io.BufferedReader(
                    new InputStreamReader(new FileInputStream(from), StandardCharsets.UTF_8))) {
                for (String l = in.readLine(); l != null; l = in.readLine()) lines.add(l);
            } catch (IOException | SecurityException e) {
                lines.clear();
            }
        }
        return parse(lines);
    }

    /** Reads the lines of a saved file (see {@link #lines()}). Lines it does not know are skipped. */
    static GroupShotResults parse(List<String> lines) {
        String setup = "";
        for (String l : lines) if (l.startsWith("setup ")) setup = l.substring(6);
        GroupShotResults r = new GroupShotResults(setup);
        for (String l : lines) {
            try {
                if (l.startsWith("check ")) {
                    Map<String, String> m = fields(l.substring(6));
                    String drop = m.get("drop");
                    r.checks.add(new Check(Integer.parseInt(m.get("spot")), Ball.valueOf(m.get("ball")),
                            drop == null ? Double.NaN : Double.parseDouble(drop)));
                } else if (l.startsWith("group ")) {
                    int bar = l.indexOf(" | ");
                    Map<String, String> m = fields(bar < 0 ? l.substring(6) : l.substring(6, bar));
                    String why = m.get("invalid");
                    int[] balls = new int[Ball.values().length];
                    for (Ball b : Ball.values()) balls[b.ordinal()] = Integer.parseInt(m.get(b.name().toLowerCase(Locale.US)));
                    r.groups.add(new Group(Integer.parseInt(m.get("round")), Integer.parseInt(m.get("spot")), m.get("variant"),
                            why == null || why.equals("-") ? null : GroupShotSession.Invalid.valueOf(why), balls,
                            Double.parseDouble(m.get("low")), Double.parseDouble(m.get("target")),
                            Double.parseDouble(m.get("feed")), Double.parseDouble(m.get("aim")),
                            bar < 0 ? "" : l.substring(bar + 3)));
                }
            } catch (RuntimeException damaged) {
                // a damaged line: that shot or group runs again
            }
        }
        return r;
    }

    // "spot=0 ball=SCORED" -> {spot: 0, ball: SCORED}
    private static Map<String, String> fields(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String kv : text.trim().split(" +")) {
            int eq = kv.indexOf('=');
            if (eq > 0) out.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        return out;
    }

    /** The whole file as lines: a note, the version, the setup, then one line per check shot and group. */
    List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("# Group Shot Tuner: every finished shot and group. The tuner reads it back. Do not edit.");
        out.add("version 1");
        out.add("setup " + setup);
        for (Check c : checks) out.add("check spot=" + c.spot + " ball=" + c.ball + " drop=" + c.dropRpm);
        for (Group g : groups) {
            StringBuilder sb = new StringBuilder("group round=" + g.round + " spot=" + g.spot + " variant=" + g.variant
                    + " invalid=" + (g.invalid == null ? "-" : g.invalid.name()));
            for (Ball b : Ball.values()) sb.append(' ').append(b.name().toLowerCase(Locale.US)).append('=').append(g.count(b));
            sb.append(" low=").append(g.lowRpm).append(" target=").append(g.targetRpm).append(" feed=").append(g.feedSec)
                    .append(" aim=").append(g.worstAimDeg);
            if (!g.endWords.isEmpty()) sb.append(" | ").append(g.endWords.replace('\n', ' '));
            out.add(sb.toString());
        }
        return out;
    }

    /** Writes the file NOW (at INIT or the end only: it waits for the storage). */
    boolean saveNow() {
        return FlywheelTuneResults.write(file, lines());
    }

    /** Saves a copy of the file on the helper thread and returns at once. */
    void saveInBackground() {
        final List<String> copy = lines();
        final File to = file;
        saver().submitLatest("group-shot-tuner", () -> lastSaveOk = FlywheelTuneResults.write(to, copy));
    }

    /** False when the last save failed (the screen says so: take photos of the screens). */
    static boolean lastSaveWorked() {
        return lastSaveOk;
    }

    /** Keeps the old file as {@code group-shot-tuner-old.txt} before a new session (INIT or a button press). */
    static boolean keepOld() {
        File old = new File(file.getParentFile(), "group-shot-tuner-old.txt");
        if (!file.isFile()) return true;
        if (old.exists() && !old.delete()) return false;
        return file.renameTo(old);
    }

    private static synchronized BackgroundWorker saver() {
        if (saver == null || !saver.isRunning()) saver = new BackgroundWorker("group-shot-tuner");
        return saver;
    }
}
