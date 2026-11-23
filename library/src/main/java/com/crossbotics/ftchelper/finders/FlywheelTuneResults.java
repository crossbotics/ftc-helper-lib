package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.launcher.Flywheel.SpeedControl;
import com.crossbotics.ftchelper.util.BackgroundWorker;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every finished test of the Flywheel Tuner, saved on the Control Hub in
 * {@code /sdcard/FIRST/ftc-helper/flywheel-tuner.txt}. The tuner saves after each test (on a helper thread,
 * never in the loop), so a stop, a dead battery or a power cycle loses at most the test that was running.
 * It reads the file back when it opens, and goes on from there.
 *
 * <p>The Group Shot Tuner reads the two winners from it: the best PID setting and the best bang-bang setting.
 * A winner passed every check on a FULL battery, on a LOW battery and in the driving check.
 *
 * <pre>{@code
 * FlywheelTuneResults saved = FlywheelTuneResults.load();
 * FlywheelSetting pid = saved.winner(Flywheel.SpeedControl.PID);         // null: none yet
 * FlywheelSetting bang = saved.winner(Flywheel.SpeedControl.BANG_BANG);  // null: none yet
 * }</pre>
 *
 * <p>The file is plain text, one line per test. Do not edit it: the tuner checks that it was made with the
 * robot's settings as they are now ({@link #setup()}), and starts a new session when they changed.
 *
 * Docs: docs/modules/finders.md, docs/tuning/06-flywheel-tuner.md
 */
public final class FlywheelTuneResults {

    /** The two batteries a session needs, in order. */
    public enum Battery { FULL, LOW }

    /** How a test ended. PASS and FAIL judge a setting; INFO is a measurement (full power, the power fit). */
    public enum Verdict { PASS, FAIL, INFO }

    /** One finished test. Its numbers are by name ({@link #number}); a missing one is NaN. */
    public static final class Test {
        /** The battery run it belongs to. */
        public final Battery battery;
        /** Which test: "today", "fullpower", "fit", "pid:...", "bang:...", "drift", "drive:..." or "done". */
        public final String id;
        /** PASS, FAIL or INFO. */
        public final Verdict verdict;
        /** Why it failed ("" when it did not), in plain words. */
        public final String why;
        /** The setting it tested (null for a test of no setting). */
        public final FlywheelSetting setting;
        private final Map<String, Double> numbers;

        /**
         * A finished test.
         *
         * @param battery FULL or LOW
         * @param id      the test's name, no spaces
         * @param verdict PASS, FAIL or INFO
         * @param why     why it failed, or ""
         * @param setting the setting it tested, or null
         * @param numbers what it measured, by name (copied)
         */
        public Test(Battery battery, String id, Verdict verdict, String why, FlywheelSetting setting, Map<String, Double> numbers) {
            this.battery = battery;
            this.id = id;
            this.verdict = verdict;
            this.why = why == null ? "" : why;
            this.setting = setting;
            this.numbers = new LinkedHashMap<>(numbers);
        }

        /**
         * A measured number: "ready" (s from a stop to steady speed), "over" (most RPM past the target),
         * "avg" (average RPM off the target, above = positive), "wobble" (RPM), "recovery" (s back at speed
         * after a pretend shot, the average of 3), "fullSec" (s at full power), "accel" (RPM per s at full
         * power), "volts" (battery at the start), "ks", "kv" (the power fit).
         *
         * @param name the number's name
         * @return the number, or NaN when this test has none
         */
        public double number(String name) {
            Double v = numbers.get(name);
            return v == null ? Double.NaN : v;
        }

        /** Every number of this test, by name (a copy). */
        public Map<String, Double> numbers() {
            return new LinkedHashMap<>(numbers);
        }

        /** True when it tested a PID or bang-bang candidate (not today's settings, not a measurement). */
        public boolean isCandidate() {
            return id.startsWith("pid:") || id.startsWith("bang:");
        }
    }

    /** The file the tuner saves to and reads from. Tests use another one. */
    static File file = new File(FinderResults.folder(), "flywheel-tuner.txt");

    private final String setup;
    private final List<Test> tests = new ArrayList<>();
    // One helper thread for every save: the robot's loop never waits for the storage.
    private static BackgroundWorker saver;
    private static volatile boolean lastSaveOk = true;

    /**
     * An empty session for this robot.
     *
     * @param setup the robot's settings that the tests depend on, as one line (the tuner makes it)
     */
    public FlywheelTuneResults(String setup) {
        this.setup = setup == null ? "" : setup;
    }

    /** The saved file: {@code flywheel-tuner.txt} in the results folder. */
    public static File file() {
        return file;
    }

    /** Reads the saved file. With no file (or one that cannot be read): an empty session with no setup. */
    public static FlywheelTuneResults load() {
        return load(file);
    }

    /**
     * Reads a saved file. It waits for the storage: call it at INIT, never in the loop.
     *
     * @param from the file
     * @return what it holds; empty when there is no such file or it cannot be read
     */
    public static FlywheelTuneResults load(File from) {
        List<String> lines = new ArrayList<>();
        // The power went off between deleting the old file and renaming the new one: the new one is whole.
        File unfinished = new File(from.getParentFile(), from.getName() + ".new");
        if (!from.isFile() && unfinished.isFile()) from = unfinished;
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
    static FlywheelTuneResults parse(List<String> lines) {
        String setup = "";
        for (String l : lines) if (l.startsWith("setup ")) setup = l.substring(6);
        FlywheelTuneResults r = new FlywheelTuneResults(setup);
        for (String l : lines) {
            if (!l.startsWith("test ")) continue;
            String[] parts = l.substring(5).split(" \\| ", -1);
            if (parts.length != 4) continue;
            String[] head = parts[0].split(" ", 4);
            if (head.length < 3) continue;
            try {
                Battery b = Battery.valueOf(head[0]);
                Verdict v = Verdict.valueOf(head[2]);
                FlywheelSetting s = parts[1].equals("-") ? null : FlywheelSetting.parse(parts[1]);
                Map<String, Double> numbers = new LinkedHashMap<>();
                for (String kv : parts[2].trim().split(" ")) {
                    int eq = kv.indexOf('=');
                    if (eq > 0) numbers.put(kv.substring(0, eq), Double.parseDouble(kv.substring(eq + 1)));
                }
                r.tests.add(new Test(b, head[1], v, parts[3], s, numbers));
            } catch (IllegalArgumentException skip) {
                // a damaged line: the test runs again
            }
        }
        return r;
    }

    /** The robot's settings these tests were made with (one line), "" when unknown. */
    public String setup() {
        return setup;
    }

    /** Every finished test, oldest first (read only). */
    public List<Test> tests() {
        return Collections.unmodifiableList(tests);
    }

    /** Adds a finished test. */
    void add(Test t) {
        tests.add(t);
    }

    /** The test with this name on this battery, or null. */
    public Test find(Battery b, String id) {
        for (Test t : tests) if (t.battery == b && t.id.equals(id)) return t;
        return null;
    }

    /** True when the tuner finished both batteries (the driving checks done or skipped). */
    public boolean isFinished() {
        return find(Battery.LOW, "done") != null;
    }

    /**
     * The FULL battery's passing candidates of one control, best first: shortest time back at speed after a
     * pretend shot, then less wobble, then gentler (less overshoot, then less time at full power). When the
     * pretend shot is too small to time ({@link #recoveryRanks()} false), the time back at speed is skipped.
     */
    public List<Test> ranked(SpeedControl control) {
        List<Test> out = new ArrayList<>();
        for (Test t : tests) {
            if (t.battery == Battery.FULL && t.isCandidate() && t.verdict == Verdict.PASS
                    && t.setting != null && t.setting.control == control) out.add(t);
        }
        Collections.sort(out, recoveryRanks() ? BETTER_FIRST : WITHOUT_RECOVERY);
        return out;
    }

    /**
     * True when the pretend shot's drop is big enough to time the way back: the drop is more than the ready
     * band by at least 2 speed-reading steps (the full-power test measures the step). WHY: with a smaller drop,
     * the wheel wins back only one reading step or so, and the time is mostly the settle time plus loop jitter,
     * so it would rank settings by chance. True when the numbers are not known yet.
     */
    public boolean recoveryRanks() {
        double drop = setupNumber("drop"), band = setupNumber("band");
        Test full = find(Battery.FULL, "fullpower");
        double step = full == null ? Double.NaN : full.number("step");
        if (Double.isNaN(drop) || Double.isNaN(band) || Double.isNaN(step)) return true;
        return drop - band >= MIN_RECOVERY_STEPS * step - 1e-9;
    }

    /** The pretend shot's drop must be more than the ready band by this many speed-reading steps. */
    static final double MIN_RECOVERY_STEPS = 2;

    /** A number of the setup line ("drop", "band"...), NaN when it has none. */
    double setupNumber(String name) {
        for (String part : setup.split(" ")) {
            if (!part.startsWith(name + "=")) continue;
            try {
                return Double.parseDouble(part.substring(name.length() + 1));
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    // Two numbers this close count as the same, so a tiny difference does not decide (the next rule does).
    private static final double SAME_RECOVERY_SEC = 0.02, SAME_WOBBLE_RPM = 5, SAME_OVERSHOOT_RPM = 10;

    /** The winner order: back at speed sooner, then less wobble, then less overshoot, then less full power. */
    static final Comparator<Test> BETTER_FIRST = (a, b) -> {
        int c = compare(a.number("recovery"), b.number("recovery"), SAME_RECOVERY_SEC);
        if (c == 0) c = compare(a.number("wobble"), b.number("wobble"), SAME_WOBBLE_RPM);
        if (c == 0) c = compare(a.number("over"), b.number("over"), SAME_OVERSHOOT_RPM);
        if (c == 0) c = compare(a.number("fullSec"), b.number("fullSec"), 0.01);
        return c;
    };

    /** The winner order when the pretend shot is too small to time: less wobble, then gentler. */
    static final Comparator<Test> WITHOUT_RECOVERY = (a, b) -> {
        int c = compare(a.number("wobble"), b.number("wobble"), SAME_WOBBLE_RPM);
        if (c == 0) c = compare(a.number("over"), b.number("over"), SAME_OVERSHOOT_RPM);
        if (c == 0) c = compare(a.number("fullSec"), b.number("fullSec"), 0.01);
        return c;
    };

    // Smaller first; NaN last; within "same" counts as equal.
    private static int compare(double a, double b, double same) {
        if (Double.isNaN(a) || Double.isNaN(b)) return Double.isNaN(a) == Double.isNaN(b) ? 0 : Double.isNaN(a) ? 1 : -1;
        if (Math.abs(a - b) <= same) return 0;
        return a < b ? -1 : 1;
    }

    /**
     * The winning setting of one control: the best on the FULL battery that also passed on the LOW battery
     * and in the driving check.
     *
     * @param control PID or BANG_BANG
     * @return the setting, or null when none passed all three
     */
    public FlywheelSetting winner(SpeedControl control) {
        Test w = winnerTest(control);
        return w == null ? null : w.setting;
    }

    /** {@link #winner}'s FULL battery test (its numbers), or null. */
    public Test winnerTest(SpeedControl control) {
        for (Test t : ranked(control)) {
            Test low = find(Battery.LOW, t.id);
            Test drive = find(Battery.LOW, "drive:" + t.id);
            if (low != null && low.verdict == Verdict.PASS && drive != null && drive.verdict == Verdict.PASS) return t;
        }
        return null;
    }

    /** The whole file as lines: a note, the version, the setup, then one line per test. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("# Flywheel Tuner: every finished test. The tuner reads it back. Do not edit.");
        out.add("version 1");
        out.add("setup " + setup);
        for (Test t : tests) {
            StringBuilder nums = new StringBuilder();
            for (Map.Entry<String, Double> e : t.numbers.entrySet()) {
                if (nums.length() > 0) nums.append(' ');
                nums.append(e.getKey()).append('=').append(e.getValue());
            }
            out.add("test " + t.battery + " " + t.id + " " + t.verdict + " | "
                    + (t.setting == null ? "-" : t.setting.text()) + " | " + nums + " | " + t.why.replace('\n', ' '));
        }
        return out;
    }

    /**
     * Writes the file NOW and says if it worked. It writes a new file first, then puts it in place of the old
     * one, so a power cut while it writes leaves the old file whole. It waits for the storage: only at INIT or
     * the end, never in the loop ({@link #saveInBackground} is for the loop).
     */
    public boolean saveNow() {
        return write(file, lines());
    }

    /** Saves a copy of the file on the helper thread and returns at once. {@link #lastSaveWorked} says how it went. */
    public void saveInBackground() {
        final List<String> copy = lines();
        final File to = file;
        saver().submitLatest("flywheel-tuner", () -> lastSaveOk = write(to, copy));
    }

    /** False when the last save failed (the screen says so: write the numbers down). */
    public static boolean lastSaveWorked() {
        return lastSaveOk;
    }

    /**
     * Keeps the old file under another name ({@code flywheel-tuner-old.txt}) before a new session starts.
     * At INIT or on a button press only (it waits for the storage).
     */
    static boolean keepOld() {
        File old = new File(file.getParentFile(), "flywheel-tuner-old.txt");
        if (!file.isFile()) return true;
        if (old.exists() && !old.delete()) return false;
        return file.renameTo(old);
    }

    // The same lock as the finders' results file: two saves never mix.
    static boolean write(File to, List<String> lines) {
        synchronized (FinderResults.FILE_LOCK) {
            File folder = to.getParentFile();
            File tmp = new File(folder, to.getName() + ".new");
            try {
                if (folder != null && !folder.exists() && !folder.mkdirs()) return false;
                try (FileOutputStream fos = new FileOutputStream(tmp);
                     Writer out = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    for (String l : lines) out.write(l + "\n");
                    out.flush();
                    // To the storage itself, not only the phone's memory: a power cut right after must not lose it.
                    fos.getFD().sync();
                }
                if (to.exists() && !to.delete()) return false;
                return tmp.renameTo(to);
            } catch (IOException | SecurityException e) {
                return false;
            }
        }
    }

    private static synchronized BackgroundWorker saver() {
        if (saver == null || !saver.isRunning()) saver = new BackgroundWorker("flywheel-tuner");
        return saver;
    }
}
