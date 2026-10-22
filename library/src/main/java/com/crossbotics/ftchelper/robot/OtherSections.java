package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.tuning.Tunable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The {@code Tunables.java} sections that no part reads, like {@code Tunables.Auto} (the autonomous's
 * distances): every number in them goes in the tuning menu too ("Auto/parkInches"). The menu's value is
 * written into the field, so the team's code reads it as usual: {@code Tunables.Auto.parkInches}.
 *
 * <p>Why: an autonomous runs with no driver, so its numbers get tuned a lot. Change one in the TeleOp's
 * tuning menu (PRACTICE profile), save it with A, and run the autonomous again: no rebuild. The
 * autonomous's INIT lists the values that differ from the code, to copy back (the code is the record).
 *
 * <p>Only {@code public static} numbers that are not {@code final} (double, int, float, long). A
 * true/false setting or a table stays as written.
 */
final class OtherSections {
    // One field and its tunable.
    private static final class Link {
        final Field field;
        final Tunable tunable;
        // The value last written into the field (NaN: never). Written again only when the tunable
        // changes, so code that sets the field itself keeps its value until someone tunes it.
        double written = Double.NaN;

        Link(Field field, Tunable tunable) {
            this.field = field;
            this.tunable = tunable;
        }
    }

    private final List<Link> links = new ArrayList<>();

    /**
     * Links every number of every section whose name is not in {@code used}, and writes the tuned
     * (saved) values into the fields at once.
     *
     * @param tunables the team's Tunables class, or null (then nothing)
     * @param used     the sections that parts read ("Drive", "Lift", ...)
     */
    OtherSections(Class<?> tunables, Set<String> used) {
        if (tunables == null) return;
        for (Class<?> section : tunables.getDeclaredClasses()) {
            if (used.contains(section.getSimpleName())) continue;
            for (Field f : section.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!Modifier.isStatic(mod) || Modifier.isFinal(mod) || !isNumber(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    double code = ((Number) f.get(null)).doubleValue();
                    // DEFAULT_FROM_CATALOG / MEASURE in your own section: no library value to show, so not in the menu.
                    if (Starting.isDefaultFromCatalog(code) || Starting.isMeasure(code)) continue;
                    boolean whole = f.getType() == int.class || f.getType() == long.class;
                    // The code value is the default. (Made once per app run; a second robot with
                    // another code value gets it, unless the value was tuned.)
                    Tunable t = Tunable.fromCode(section.getSimpleName() + "/" + f.getName(), code, stepFor(code, whole));
                    links.add(new Link(f, t));
                } catch (IllegalAccessException | SecurityException | ClassCastException e) {
                    // A field the library cannot read stays as written.
                }
            }
        }
        copy();
    }

    /** How many fields are linked (for tests). */
    int size() {
        return links.size();
    }

    /**
     * Writes each tunable's value into its field when the value changed (tuned in the menu, or a saved
     * value at the start). Quick (no file): call it every loop.
     */
    void copy() {
        for (Link l : links) {
            double v = l.tunable.get();
            if (v == l.written) continue;
            l.written = v;
            try {
                Class<?> type = l.field.getType();
                if (type == double.class) l.field.setDouble(null, v);
                else if (type == float.class) l.field.setFloat(null, (float) v);
                else if (type == int.class) l.field.setInt(null, (int) Math.round(v));
                else l.field.setLong(null, Math.round(v));
            } catch (IllegalAccessException | IllegalArgumentException e) {
                // cannot happen for the fields linked above; skip it rather than stop the robot
            }
        }
    }

    private static boolean isNumber(Class<?> t) {
        return t == double.class || t == int.class || t == float.class || t == long.class;
    }

    // One D-pad press in the menu: about a tenth of the value (24 in -> 1 in), at least 1 for whole numbers.
    static double stepFor(double value, boolean whole) {
        double a = Math.abs(value);
        double step = a >= 10 ? 1 : a >= 1 ? 0.1 : a >= 0.1 ? 0.01 : a > 0 ? 0.001 : 0.1;
        return whole ? Math.max(1, step) : step;
    }
}
