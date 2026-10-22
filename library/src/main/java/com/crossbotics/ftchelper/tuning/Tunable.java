package com.crossbotics.ftchelper.tuning;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.util.MathUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A number you can change while the robot runs, from the gamepad ({@link TuningMenu}),
 * without rebuilding. Saved values stay on the Control Hub until you reset them.
 *
 * <pre>{@code
 * // Make it once (a static field is easiest):
 * static final Tunable LIFT_KP = Tunable.number("Lift/kP", 0.004, 0.0005);
 *
 * // Read it every loop, so a change takes effect at once:
 * pid.setGains(LIFT_KP.get(), 0, 0, 0);
 * }</pre>
 *
 * <p>The part before "/" is the group shown in the menu ("Lift"). When you are happy with a value,
 * <b>copy it into the code</b> as the new default, then reset the saved value: the code is the
 * record, the saved file is only for tuning.
 *
 * <p>Saved values are kept in the robot's {@link SettingsFile}, under names that start with
 * "tunable.". Tutorial 15 (docs/tutorials/15-telemetry-profiles-tuning.md) walks through it.
 *
 * Docs: docs/guides/tuning.md
 */
public final class Tunable {
    // Every tunable made so far. "static": one shared list for the whole app, so the menu can
    // find tunables made anywhere in the code.
    private static final List<Tunable> ALL = new ArrayList<>();
    // Added in front of each name in the settings file, so tunables never clash with other settings.
    private static final String PREFIX = "tunable.";

    // "final" fields are set once, when the tunable is made, and never change.
    private final String name;
    // The value written in the code.
    private double defaultValue;
    // How much one D-pad press changes the value (always positive).
    private final double step;
    // The allowed range. "Infinity" means no limit until range(...) is called.
    private double min = Double.NEGATIVE_INFINITY;
    private double max = Double.POSITIVE_INFINITY;
    // The value in use right now.
    private double value;
    // True while its part is not on the robot (Module.optional, device missing): left out of the menu.
    private boolean hidden = false;
    // Where the code value came from when Tunables.java says DEFAULT_FROM_CATALOG ("from the catalog (gobilda-5203-435)"); "" = written.
    private String source = "";
    // The code value when it is outside range(...) (NaN = fine): the range's end is used instead.
    private double codeOutside = Double.NaN;

    // Private: make one with Tunable.number(...), so the same name never gives two tunables.
    private Tunable(String name, double defaultValue, double step) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.step = Math.abs(step);
        // Start from the saved value on the hub if there is one, otherwise the code value.
        this.value = SettingsFile.shared().getDouble(PREFIX + name, defaultValue);
    }

    // "synchronized" (here and on the other static methods): only one piece of code at a time may
    // use the shared list.
    /**
     * Like {@link #number}, for a number written in the team's code (the robot in five files uses it
     * for every {@code Tunables.java} field). If a tunable with this name already exists, was not
     * changed (not tuned, nothing saved) and has another code value, it takes this code value: the
     * code is the record. Example: two robots in one app with {@code Tunables.Auto.leaveInches} 24 and
     * 12; each robot drives its own distance. The step of an existing one is kept.
     *
     * @param name         "Group/Name", e.g. "Auto/leaveInches"
     * @param defaultValue the value in the code
     * @param step         how much one D-pad press changes it, used only when the tunable is new
     * @return the tunable
     */
    public static synchronized Tunable fromCode(String name, double defaultValue, double step) {
        Tunable t = number(name, defaultValue, step);
        // The code value changed since (or was outside the range): start from it again.
        double code = Double.isNaN(t.codeOutside) ? t.defaultValue : t.codeOutside;
        if (!t.isChanged() && Math.abs(code - defaultValue) > 1e-12) {
            t.defaultValue = defaultValue;
            t.value = defaultValue;
            t.codeOutside = Double.NaN;   // checked again by range(...)
        }
        return t;
    }

    /**
     * Makes a tunable number, or returns the existing one with this name.
     * (If one with this name already exists, the default and step given here are not used.)
     *
     * @param name         "Group/Name", e.g. "Lift/kP"
     * @param defaultValue the value in the code
     * @param step         how much one D-pad press changes it
     * @return the tunable
     */
    public static synchronized Tunable number(String name, double defaultValue, double step) {
        // Already made (for example the OpMode was run before)? Hand back that one.
        for (Tunable t : ALL) if (t.name.equals(name)) return t;
        Tunable t = new Tunable(name, defaultValue, step);
        ALL.add(t);
        return t;
    }

    /** Every tunable made so far, in the order they were made. */
    public static synchronized List<Tunable> all() {
        // A read-only copy: the caller cannot change the real list, and it does not change under them.
        return Collections.unmodifiableList(new ArrayList<>(ALL));
    }

    /**
     * The tunables the tuning menu shows: every one but those of parts that are not on the robot
     * (see {@link #hideGroup}).
     */
    public static synchronized List<Tunable> shown() {
        List<Tunable> out = new ArrayList<>();
        for (Tunable t : ALL) if (!t.hidden) out.add(t);
        return Collections.unmodifiableList(out);
    }

    /**
     * Hides (or shows again) every tunable of one group, e.g. "IntakeDeploy" for "IntakeDeploy/deployed".
     * The robot in five files hides the numbers of a part that is not on the robot (marked
     * {@code optional()}, its device not in the configuration): changing them would do nothing. Their
     * saved values are kept, for the day the part is there. Set again at every INIT.
     *
     * @param group  the group (a part's Tunables section)
     * @param hidden true = left out of the menu
     */
    public static synchronized void hideGroup(String group, boolean hidden) {
        for (Tunable t : ALL) if (t.group().equals(group)) t.hidden = hidden;
    }

    /** The groups hidden now (parts not on the robot), in order, e.g. ["IntakeDeploy"]. */
    public static synchronized List<String> hiddenGroups() {
        List<String> out = new ArrayList<>();
        for (Tunable t : ALL) if (t.hidden && !out.contains(t.group())) out.add(t.group());
        return out;
    }

    /** True while it is hidden from the menu (its part is not on the robot). */
    public synchronized boolean isHidden() {
        return hidden;
    }

    /** How many tunables use a saved value that is different from the code. Show it in INIT. */
    public static synchronized int countChanged() {
        int n = 0;
        for (Tunable t : ALL) if (t.isChanged()) n++;
        return n;
    }

    /**
     * The values that differ from the code, as lines to copy into it: "Lift: kP = 0.0050" means
     * {@code kP = 0.0050} in {@code Tunables.java}, class {@code Lift}. Empty if none differ. The
     * code is the record: a value saved only on the hub is lost with a new Control Hub.
     */
    public static synchronized List<String> changedAsCode() {
        List<String> out = new ArrayList<>();
        for (Tunable t : ALL) {
            if (!t.isChanged()) continue;
            String group = t.group();
            String field = group.isEmpty() ? t.name : t.name.substring(group.length() + 1);
            // The shown value without its trailing zeros ("0.004500" -> "0.0045"), as one writes it in code.
            String value = t.display();
            if (value.contains(".")) value = value.replaceAll("0+$", "").replaceAll("\\.$", ".0");
            out.add((group.isEmpty() ? "" : group + ": ") + field + " = " + value);
        }
        return out;
    }

    /**
     * Saves every tunable's value on the Control Hub. WAITS for the storage: in a running OpMode use
     * {@link #saveAllInBackground} (the tuning menu does).
     *
     * @return true if it worked
     */
    public static boolean saveAll() {
        copyToSettings();
        // The slow part (writing the file) runs without the lock, so the loop can still read tunables.
        return SettingsFile.shared().save();
    }

    /**
     * Saves every tunable's value on a background thread; returns at once.
     *
     * @param whenDone gets true if it worked (runs on the background thread); may be null
     */
    public static void saveAllInBackground(Consumer<Boolean> whenDone) {
        copyToSettings();
        SettingsFile.shared().saveInBackground(whenDone);
    }

    /**
     * Puts every tunable back to its code value and deletes the saved values. WAITS for the storage:
     * in a running OpMode use {@link #resetAllInBackground}.
     *
     * @return true if the file was saved
     */
    public static boolean resetAll() {
        resetValues();
        // Saving now removes every tunable from the file (none is "changed" any more).
        return saveAll();
    }

    /** {@link #resetAll()}, with the file written on a background thread. The values change at once. */
    public static void resetAllInBackground(Consumer<Boolean> whenDone) {
        resetValues();
        saveAllInBackground(whenDone);
    }

    // Every value back to its code value (quick: no file).
    private static synchronized void resetValues() {
        for (Tunable t : ALL) t.value = t.defaultValue;
    }

    // Puts the values into the settings in memory (quick: no file).
    private static synchronized void copyToSettings() {
        SettingsFile settings = SettingsFile.shared();
        for (Tunable t : ALL) {
            // Only changed values are written. A value equal to the code is removed from the file,
            // so a later change to the code default is not hidden by an old saved copy.
            if (t.isChanged()) settings.put(PREFIX + t.name, Double.toString(t.value));
            else settings.remove(PREFIX + t.name);
        }
    }

    /** For tests: forget all tunables. */
    static synchronized void clearForTests() {
        ALL.clear();
    }

    /**
     * For JUnit tests only: forgets every tunable in memory (they are shared by the whole app, so one
     * test's robot would otherwise see another's numbers). {@code new PretendDevices()} calls it. Never in
     * robot code: the tuning menu would lose its numbers.
     */
    public static void forgetAllForTests() {
        clearForTests();
    }

    /**
     * Limits the value to a range (the menu will not go past it).
     * Use it so tuning can never reach a dangerous number, for example a negative gain.
     *
     * @param min the smallest allowed value
     * @param max the largest allowed value
     * @return this tunable, so you can write {@code Tunable.number(...).range(0, 1)}
     */
    public Tunable range(double min, double max) {
        // Works even if min and max are typed the wrong way round.
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        // A code value outside the range (Claw.open = 1.2 for a servo, 0 to 1): the nearest end is used,
        // and INIT says so plainly (outsideRangeNotes). It is not a "tuned value" to copy into the code.
        if (defaultValue < this.min - 1e-12 || defaultValue > this.max + 1e-12) {
            codeOutside = defaultValue;
            defaultValue = MathUtil.clamp(defaultValue, this.min, this.max);
        }
        // A saved value outside the new range is pulled back inside it.
        value = MathUtil.clamp(value, this.min, this.max);
        return this;
    }

    /**
     * INIT notes for code values outside their allowed range, e.g. "Tunables.Claw.open = 1.2 is outside
     * its allowed range 0 to 1: 1 is used. Fix it in Tunables.java". Only for these groups (a robot's
     * Tunables sections); empty when all are fine.
     *
     * @param groups the sections to look at, e.g. ["Claw", "Lift"]
     */
    public static synchronized List<String> outsideRangeNotes(java.util.Collection<String> groups) {
        List<String> out = new ArrayList<>();
        for (Tunable t : ALL) {
            if (Double.isNaN(t.codeOutside) || !groups.contains(t.group())) continue;
            out.add("Tunables." + t.name.replace('/', '.') + " = " + plain(t.codeOutside) + " is outside its allowed range "
                    + plain(t.min) + " to " + plain(t.max) + ": " + plain(t.defaultValue) + " is used. Fix it in Tunables.java");
        }
        return out;
    }

    // A number without trailing zeros: 1.2, 0.5, 3000.
    private static String plain(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    /** The current value. Read it every loop. */
    public double get() {
        return value;
    }

    /**
     * Sets the value (inside the range). Not saved until {@link #saveAll()}.
     *
     * @param newValue the new value; outside the range it is pulled back to the nearest end
     */
    public void set(double newValue) {
        value = MathUtil.clamp(newValue, min, max);
    }

    /** One step up. */
    public void increase() {
        set(value + step);
    }

    /** One step down. */
    public void decrease() {
        set(value - step);
    }

    /** Back to the code value. */
    public void reset() {
        value = defaultValue;
    }

    /** True if the value is not the code value. */
    public boolean isChanged() {
        // Compare with a tiny allowance (1e-12), because adding and taking away steps can leave
        // a decimal number a hair off, like 0.30000000000000004 instead of 0.3.
        return Math.abs(value - defaultValue) > 1e-12;
    }

    /** The full name, e.g. "Lift/kP". */
    public String name() { return name; }
    /** The value written in the code. */
    public double defaultValue() { return defaultValue; }
    /** How much one D-pad press changes the value. */
    public double step() { return step; }

    /** The group: the part of the name before the first "/" ("" if none). */
    public String group() {
        // indexOf gives the position of the first "/", or -1 if there is none.
        int slash = name.indexOf('/');
        return slash < 0 ? "" : name.substring(0, slash);
    }

    /**
     * Says where the code value came from, shown next to it in the tuning menu while it is not changed:
     * Tunables.java wrote DEFAULT_FROM_CATALOG, so the library worked it out ("from the catalog (gobilda-5203-435)",
     * "general starting value (no part in Robot.java)"). The "copy into Tunables.java" lines stay real numbers.
     *
     * @param words the words, "" for none
     * @return this tunable
     */
    public synchronized Tunable source(String words) {
        source = words == null ? "" : words;
        return this;
    }

    /** Where the code value came from (see {@link #source(String)}), "" when it was written as a number. */
    public synchronized String source() {
        return source;
    }

    /** The value as short text, e.g. "0.0040". */
    public String display() {
        // Choose how many decimals to show from the step size: enough to see one step change,
        // not so many that the screen fills with digits.
        double size = Math.abs(step == 0 ? value : step);
        int decimals = size >= 1 ? 1 : size >= 0.1 ? 2 : size >= 0.01 ? 3 : size >= 0.001 ? 4 : 6;
        return String.format(Locale.US, "%." + decimals + "f", value);
    }
}
