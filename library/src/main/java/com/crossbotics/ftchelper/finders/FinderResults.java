package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.BackgroundWorker;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What a finder found, as lines of code ready to copy. Shown on the Driver Station, and saved on the
 * Control Hub in {@code /sdcard/FIRST/ftc-helper/finder-results.txt} when the finder stops (so
 * nobody has to copy numbers from the screen by hand; get the file with the REV Hardware Client or
 * Android Studio's Device Explorer).
 *
 * <p>Each line has a key: setting the same key again replaces the line, so the newest result wins.
 *
 * <p>Each number is shown once. A robot in five files gets the file, the class and the exact Java line.
 * A project without Robot.java gets code for the Java helpers ({@code PositionServo.create(...)}).
 * The tool also says DONE, or NOT DONE and what is still missing ({@link #done()}, {@link #notDone(String)}).
 *
 * Docs: docs/modules/finders.md
 */
public final class FinderResults {
    // Where results are saved. Tests use another file.
    static File file = new File(folder(), "finder-results.txt");
    // Held while the file is written, so two saves never mix their lines.
    static final Object FILE_LOCK = new Object();

    /**
     * The folder for results and logs: /sdcard/FIRST/ftc-helper on the robot. The simulator sets the
     * Java setting "ftchelper.dataDir" to keep them on the laptop instead.
     */
    public static File folder() {
        // The same folder as the settings file, so the robot and the simulator keep everything together.
        return com.crossbotics.ftchelper.core.SettingsFile.folder();
    }
    // One background thread for every save, so the robot loop never waits for the storage.
    private static BackgroundWorker saver;

    private final String finderName;
    // The battery's volts at START (NaN = not known), written in the saved file's header.
    private volatile double volts = Double.NaN;
    // key -> what was found, in the order they were first set
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    // null = the tool says nothing about it, "" = done, else what is still missing.
    private volatile String notDone = null;

    /** The heading over code for a project that has no Robot.java. */
    static final String HELPER_CODE = "For a project with no Robot.java:";
    static final String ROBOT_FILE = "Open Robot.java:";

    // What one key holds: where it goes (a file and a class, or a place in words; or neither) and its lines.
    private static final class Entry {
        final String section, where;
        final List<String> body;

        Entry(String section, String where, List<String> body) {
            this.section = section;
            this.where = where;
            this.body = body;
        }

        String group() {
            return section != null ? "class " + section : where != null ? where : "";
        }
    }

    /** @param finderName shown above the lines in the saved file, e.g. "Servo finder" */
    public FinderResults(String finderName) {
        this.finderName = finderName;
    }

    /** Notes the battery's volts at START, for the saved file's header ("battery 12.7 V"). */
    public void battery(double volts) {
        if (!Double.isNaN(volts) && volts > 1) this.volts = volts;
    }

    /** Sets (or replaces) the line with this key. */
    public synchronized void set(String key, String line) {
        entries.put(key, new Entry(null, null, java.util.Collections.singletonList(line)));
    }

    /** Sets lines under words that say where they go, such as {@link #ROBOT_FILE}. */
    synchronized void setIn(String key, String where, String... body) {
        entries.put(key, new Entry(null, where, java.util.Arrays.asList(body)));
    }

    /**
     * Sets one exact Java line for {@code Tunables.java}. The screen names the file and the class.
     *
     * <pre>{@code
     * results.setTunable("ready", "Launcher", "readyBandRpm", "100");
     * // Open Tunables.java, find class Launcher.
     * //   public static double readyBandRpm = 100;
     * }</pre>
     *
     * @param key     setting the same key again replaces the line
     * @param section the class inside Tunables.java, e.g. "Launcher"
     * @param field   the field's name, e.g. "readyBandRpm"
     * @param value   the value as Java text: "100", "true", "\"PID\"" (a word), "{{48, 2550}, {72, 2900}}"
     */
    public synchronized void setTunable(String key, String section, String field, String value) {
        entries.put(key, new Entry(section, null, javaLines(field, value)));
    }

    /** The Java for one field. A long table goes on more lines, so that it fits the screen (still valid Java). */
    static List<String> javaLines(String field, String value) {
        List<String> out = new ArrayList<>();
        if (value.startsWith("{{")) {
            String head = "public static double[][] " + field + " = ";
            if (head.length() + value.length() < SCREEN_WIDTH) {
                out.add(head + value + ";");
                return out;
            }
            out.add(head + "{");
            String inside = value.substring(1, value.length() - 1);
            StringBuilder row = new StringBuilder();
            for (String cell : inside.split("(?<=\\}), ")) {
                if (row.length() > 0 && row.length() + cell.length() > 36) {
                    out.add("    " + row + ",");
                    row.setLength(0);
                }
                if (row.length() > 0) row.append(", ");
                row.append(cell);
            }
            out.add("    " + row + "};");
            return out;
        }
        String type = value.equals("true") || value.equals("false") ? "boolean" : value.startsWith("\"") ? "String" : "double";
        out.add("public static " + type + " " + field + " = " + value + ";");
        return out;
    }

    /** About as many characters as one line of the Driver Hub shows. */
    static final int SCREEN_WIDTH = 50;

    /** Says this tool's job is finished: the screen shows DONE over the lines. */
    public void done() {
        notDone = "";
    }

    /**
     * Says the job is not finished. The screen shows NOT DONE and these words.
     *
     * @param why what is missing, or what to do next, in a few plain words
     */
    public void notDone(String why) {
        notDone = why;
    }

    /** "DONE", "NOT DONE: why", or "" when the tool did not say. */
    synchronized String status() {
        return notDone == null ? "" : notDone.isEmpty() ? "DONE" : "NOT DONE: " + notDone;
    }

    /** Removes the line with this key (nothing if there is none). */
    public synchronized void remove(String key) {
        entries.remove(key);
    }

    /** Removes every line whose key starts with this text, e.g. all of one servo's lines. */
    public synchronized void removeStartingWith(String keyStart) {
        entries.keySet().removeIf(k -> k.startsWith(keyStart));
    }

    /** True if this key has a line now. */
    synchronized boolean has(String key) {
        return entries.containsKey(key);
    }

    /** The lines to copy, each file and class once with its lines under it (a copy). */
    public synchronized List<String> lines() {
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry e : entries.values()) {
            List<Entry> g = groups.get(e.group());
            if (g == null) {
                g = new ArrayList<>();
                groups.put(e.group(), g);
            }
            g.add(e);
        }
        List<String> out = new ArrayList<>();
        for (List<Entry> g : groups.values()) {
            Entry first = g.get(0);
            if (first.section != null) {
                // Both on one line when they fit: the screen has few lines.
                String open = "Open Tunables.java, find class " + first.section + ".";
                if (open.length() <= SCREEN_WIDTH) {
                    out.add(open);
                } else {
                    out.add("Open Tunables.java. Find this class:");
                    out.add("  " + first.section);
                }
                // Not finished: the lines show what was found, and say when to copy them.
                out.add(notDone == null || notDone.isEmpty() ? "Change these lines (not there? add them):"
                        : "When it says DONE, change these lines:");
            } else if (first.where != null) {
                out.add(first.where);
            }
            String indent = first.section != null || first.where != null ? "  " : "";
            for (Entry e : g) for (String line : e.body) out.add(indent + line);
        }
        return out;
    }

    /** True if nothing was found yet. */
    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Shows DONE or NOT DONE, then the lines to copy (nothing while there are no lines). */
    public void addTelemetry(RobotTelemetry t) {
        List<String> copy = lines();
        if (copy.isEmpty()) return;
        String why = notDone;
        if (why == null) {
            t.section("Numbers found");
        } else if (why.isEmpty()) {
            t.section("DONE");
        } else {
            t.section("NOT DONE");
            t.text(why);
        }
        for (String line : copy) t.text(line);
    }

    /** The lines for the file: DONE or NOT DONE first, so an unfinished number is not trusted later. */
    private List<String> report() {
        List<String> out = new ArrayList<>(lines());
        String status = status();
        if (!status.isEmpty()) out.add(0, status);
        return out;
    }

    /**
     * Adds the lines to the results file NOW and says if it worked. It waits for the storage (a few
     * milliseconds): only for the end of a finder, never in its loop.
     */
    public boolean saveNow() {
        return append(header(), report());
    }

    // "== Servo finder, 2026-10-16 14:05, battery 12.7 V ==" above each saved group of lines.
    private String header() {
        return "== " + finderName + ", " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date())
                + (Double.isNaN(volts) ? "" : ", battery " + num(volts, 1) + " V") + " ==";
    }

    /**
     * Adds the lines to the results file on a background thread; returns at once.
     *
     * @param whenDone gets true if it worked (runs on the background thread); may be null
     */
    public void saveInBackground(Consumer<Boolean> whenDone) {
        final List<String> copy = report();
        final String header = header();
        saver().submit(() -> {
            boolean ok = append(header, copy);
            if (whenDone != null) whenDone.accept(ok);
        });
    }

    // Adds the header and lines to the end of the file. Runs on the background thread. It holds its own
    // lock while it writes, never the one saver() needs: a save pressed during a slow write must not wait.
    static boolean append(String header, List<String> copy) {
        synchronized (FILE_LOCK) {
            try {
                File folder = file.getParentFile();
                if (folder != null && !folder.exists() && !folder.mkdirs()) return false;
                // "true" = add to the end of the file, keep what is already there
                try (Writer out = new FileWriter(file, true)) {
                    out.write(header + "\n");
                    for (String line : copy) out.write(line + "\n");
                    out.write("\n");
                }
                return true;
            } catch (IOException | SecurityException e) {
                return false;
            }
        }
    }

    private static synchronized BackgroundWorker saver() {
        if (saver == null) saver = new BackgroundWorker("finder-results");
        return saver;
    }

    /**
     * A short one-line note of some numbers and their section, e.g. for a log of your own. The finders
     * show exact Java lines ({@link #setTunable}): use that for lines to copy.
     *
     * <pre>{@code
     * tunablesLine("lift", Arrays.asList("min = 0", "max = 2580"))   // "Tunables.Lift: min = 0, max = 2580"
     * }</pre>
     *
     * @param deviceName the device's configuration name; the section is that name in CamelCase
     *                   ("intake_deploy" reads Tunables.IntakeDeploy, see {@link RobotDefinition#sectionName})
     * @param fields     the numbers as "name = value" texts, in order
     * @return e.g. "Tunables.Claw: P1 = 0.210, P2 = 0.770"
     */
    public static String tunablesLine(String deviceName, List<String> fields) {
        return "Tunables." + RobotDefinition.sectionName(deviceName) + ": " + commas(fields);
    }

    // "a, b, c". (Not String.join: the blocking check reads every ".join(" as a thread wait.)
    static String commas(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p);
        }
        return sb.toString();
    }

    /** A number as text with this many decimals, always with a "." (whatever the phone's language). */
    public static String num(double value, int decimals) {
        String s = String.format(Locale.US, "%." + decimals + "f", value);
        // Never "-0" or "-0.0": a wheel at rest can read a tiny bit under 0, and that looks like a fault.
        return s.matches("-0([.]0+)?") ? s.substring(1) : s;
    }

    /**
     * A number that is copied back as it was: at least {@code decimals} decimals, more when it needs them
     * (so a kP of 0.005 does not turn into 0.01).
     */
    static String exact(double value, int decimals) {
        for (int d = decimals; d < 8; d++) {
            if (Math.abs(Double.parseDouble(num(value, d)) - value) < 1e-9) return num(value, d);
        }
        return num(value, 8);
    }
}
