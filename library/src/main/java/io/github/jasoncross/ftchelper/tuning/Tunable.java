package io.github.jasoncross.ftchelper.tuning;

import io.github.jasoncross.ftchelper.core.SettingsFile;
import io.github.jasoncross.ftchelper.util.MathUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
 * Docs: docs/guides/tuning.md
 */
public final class Tunable {
    private static final List<Tunable> ALL = new ArrayList<>();
    private static final String PREFIX = "tunable.";

    private final String name;
    private final double defaultValue;
    private final double step;
    private double min = Double.NEGATIVE_INFINITY;
    private double max = Double.POSITIVE_INFINITY;
    private double value;

    private Tunable(String name, double defaultValue, double step) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.step = Math.abs(step);
        this.value = SettingsFile.shared().getDouble(PREFIX + name, defaultValue);
    }

    /**
     * Makes a tunable number, or returns the existing one with this name.
     *
     * @param name         "Group/Name", e.g. "Lift/kP"
     * @param defaultValue the value in the code
     * @param step         how much one D-pad press changes it
     */
    public static synchronized Tunable number(String name, double defaultValue, double step) {
        for (Tunable t : ALL) if (t.name.equals(name)) return t;
        Tunable t = new Tunable(name, defaultValue, step);
        ALL.add(t);
        return t;
    }

    /** Every tunable made so far, in the order they were made. */
    public static synchronized List<Tunable> all() {
        return Collections.unmodifiableList(new ArrayList<>(ALL));
    }

    /** How many tunables use a saved value that is different from the code. Show it in INIT. */
    public static synchronized int countChanged() {
        int n = 0;
        for (Tunable t : ALL) if (t.isChanged()) n++;
        return n;
    }

    /** Saves every tunable's value on the Control Hub. @return true if it worked */
    public static synchronized boolean saveAll() {
        SettingsFile settings = SettingsFile.shared();
        for (Tunable t : ALL) {
            if (t.isChanged()) settings.put(PREFIX + t.name, Double.toString(t.value));
            else settings.remove(PREFIX + t.name);
        }
        return settings.save();
    }

    /** Puts every tunable back to its code value and deletes the saved values. */
    public static synchronized boolean resetAll() {
        for (Tunable t : ALL) t.value = t.defaultValue;
        return saveAll();
    }

    /** For tests: forget all tunables. */
    static synchronized void clearForTests() {
        ALL.clear();
    }

    /** Limits the value to a range (the menu will not go past it). */
    public Tunable range(double min, double max) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        value = MathUtil.clamp(value, this.min, this.max);
        return this;
    }

    /** The current value. Read it every loop. */
    public double get() {
        return value;
    }

    /** Sets the value (inside the range). Not saved until {@link #saveAll()}. */
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
        return Math.abs(value - defaultValue) > 1e-12;
    }

    public String name() { return name; }
    public double defaultValue() { return defaultValue; }
    public double step() { return step; }

    /** The group: the part of the name before the first "/" ("" if none). */
    public String group() {
        int slash = name.indexOf('/');
        return slash < 0 ? "" : name.substring(0, slash);
    }

    /** The value as short text, e.g. "0.0040". */
    public String display() {
        double size = Math.abs(step == 0 ? value : step);
        int decimals = size >= 1 ? 1 : size >= 0.1 ? 2 : size >= 0.01 ? 3 : size >= 0.001 ? 4 : 6;
        return String.format(Locale.US, "%." + decimals + "f", value);
    }
}
