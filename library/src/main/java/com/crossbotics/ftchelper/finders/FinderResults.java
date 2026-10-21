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
 * <p>Each finder shows its numbers twice: as code for the older helpers ({@code PositionServo.create(...)})
 * and as lines for the robot in five files ({@code Tunables.Claw: P1 = 0.210, P2 = 0.770}): copy each
 * {@code name = value} into that section of {@code Tunables.java}.
 *
 * Docs: docs/modules/finders.md
 */
public final class FinderResults {
    // Where results are saved. Tests use another file.
    static File file = new File(folder(), "finder-results.txt");

    /**
     * The folder for results and logs: /sdcard/FIRST/ftc-helper on the robot. The simulator sets the
     * Java setting "ftchelper.dataDir" to keep them on the laptop instead.
     */
    public static File folder() {
        return new File(System.getProperty("ftchelper.dataDir", "/sdcard/FIRST/ftc-helper"));
    }
    // One background thread for every save, so the robot loop never waits for the storage.
    private static BackgroundWorker saver;

    private final String finderName;
    // key -> line, in the order they were first set
    private final Map<String, String> lines = new LinkedHashMap<>();

    /** @param finderName shown above the lines in the saved file, e.g. "Servo finder" */
    public FinderResults(String finderName) {
        this.finderName = finderName;
    }

    /** Sets (or replaces) the line with this key. */
    public synchronized void set(String key, String line) {
        lines.put(key, line);
    }

    /** Removes the line with this key (nothing if there is none). */
    public synchronized void remove(String key) {
        lines.remove(key);
    }

    /** Removes every line whose key starts with this text, e.g. all of one servo's lines. */
    public synchronized void removeStartingWith(String keyStart) {
        lines.keySet().removeIf(k -> k.startsWith(keyStart));
    }

    /** The lines, in order (a copy). */
    public synchronized List<String> lines() {
        return new ArrayList<>(lines.values());
    }

    /** True if nothing was found yet. */
    public synchronized boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Shows the lines under a "Copy into your code" heading (nothing if empty). */
    public void addTelemetry(RobotTelemetry t) {
        List<String> copy = lines();
        if (copy.isEmpty()) return;
        t.section("Copy into your code");
        for (String line : copy) t.text(line);
    }

    /**
     * Adds the lines to the results file NOW and says if it worked. It waits for the storage (a few
     * milliseconds): only for the end of a finder, never in its loop.
     */
    public boolean saveNow() {
        return append(header(), lines());
    }

    // "== Servo finder, 2026-10-16 14:05 ==" above each saved group of lines.
    private String header() {
        return "== " + finderName + ", " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()) + " ==";
    }

    /**
     * Adds the lines to the results file on a background thread; returns at once.
     *
     * @param whenDone gets true if it worked (runs on the background thread); may be null
     */
    public void saveInBackground(Consumer<Boolean> whenDone) {
        final List<String> copy = lines();
        final String header = header();
        saver().submit(() -> {
            boolean ok = append(header, copy);
            if (whenDone != null) whenDone.accept(ok);
        });
    }

    // Adds the header and lines to the end of the file. Runs on the background thread.
    static synchronized boolean append(String header, List<String> copy) {
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

    private static synchronized BackgroundWorker saver() {
        if (saver == null) saver = new BackgroundWorker("finder-results");
        return saver;
    }

    /**
     * A line for the robot in five files: the section of {@code Tunables.java} and its numbers.
     * Copy each {@code name = value} into that section ({@code public static double ...}).
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
        return String.format(Locale.US, "%." + decimals + "f", value);
    }
}
