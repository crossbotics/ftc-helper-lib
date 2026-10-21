package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.tuning.Tunable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One module's numbers from the team's {@code Tunables.java}: the {@code public static class Lift { ... }}
 * section. Every number becomes a {@link Tunable} named "Lift/kP", so the tuning menu can change it
 * and the robot can warn when a saved value differs from the code.
 *
 * <p>A missing section or field is not an error: the module uses the library's default and notes it,
 * so a team can delete a line it does not care about. The notes are shown in INIT.
 */
final class TunableSection {
    /** The section's name, e.g. "Lift". Also the group name in the tuning menu. */
    final String name;
    // The nested class, or null when Tunables.java has no section with this name.
    private final Class<?> section;
    // The fields this module asked for that were not in the section (for the INIT note).
    private final List<String> missing = new ArrayList<>();

    private TunableSection(String name, Class<?> section) {
        this.name = name;
        this.section = section;
    }

    /**
     * The section called {@code name} inside {@code tunables} (null = no Tunables class at all).
     *
     * @param tunables the team's Tunables class, or null
     * @param name     the section, e.g. "Lift"
     */
    static TunableSection of(Class<?> tunables, String name) {
        Class<?> found = null;
        if (tunables != null) {
            // getDeclaredClasses: the classes written inside Tunables ("public static class Lift").
            for (Class<?> c : tunables.getDeclaredClasses()) {
                if (c.getSimpleName().equals(name)) found = c;
            }
        }
        return new TunableSection(name, found);
    }

    /** True if Tunables.java has this section. */
    boolean exists() {
        return section != null;
    }

    /** True if the section has this field (for optional numbers). */
    boolean has(String field) {
        return field(field) != null;
    }

    /**
     * A number from the section as a live {@link Tunable}: the code value is the field's value (or
     * {@code fallback} if the field is missing). Read it with {@code get()} every loop.
     *
     * @param field    the field name, e.g. "kP"
     * @param fallback the library's default
     * @param step     how much one press in the tuning menu changes it
     */
    Tunable tunable(String field, double fallback, double step) {
        return Tunable.fromCode(name + "/" + field, number(field, fallback), step);
    }

    /**
     * A number from the section (not live: read once). {@code fallback} if it is missing.
     *
     * @param field    the field name
     * @param fallback the library's default
     */
    double number(String field, double fallback) {
        Field f = field(field);
        if (f == null) {
            missing.add(field);
            return fallback;
        }
        try {
            // Static field: no object needed, so "null". Works for double, int, float and long fields.
            return ((Number) f.get(null)).doubleValue();
        } catch (IllegalAccessException | ClassCastException e) {
            missing.add(field);
            return fallback;
        }
    }

    /** A true/false setting from the section, {@code fallback} if it is missing. */
    boolean flag(String field, boolean fallback) {
        Field f = field(field);
        if (f == null || f.getType() != boolean.class) {
            missing.add(field);
            return fallback;
        }
        try {
            return f.getBoolean(null);
        } catch (IllegalAccessException e) {
            return fallback;
        }
    }

    /**
     * A table of rows, e.g. {@code {{24, 2200}, {48, 2550}}}, or null if there is none.
     *
     * @param field the field name, e.g. "speedByDistance"
     */
    double[][] table(String field) {
        Field f = field(field);
        if (f == null || f.getType() != double[][].class) return null;
        try {
            return (double[][]) f.get(null);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Every number in the section that is not in {@code known}, in the order they are written. A
     * module uses these as its named positions: in {@code Lift { down = 0, low = 900, high = 2400 }}
     * the three names are positions.
     *
     * @param known the field names the module uses for its own settings (kP, min, ...)
     */
    Map<String, Double> others(Collection<String> known) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (section == null) return out;
        for (Field f : section.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || known.contains(f.getName())) continue;
            Class<?> t = f.getType();
            if (t != double.class && t != int.class && t != float.class && t != long.class) continue;
            out.put(f.getName(), number(f.getName(), 0));
        }
        return out;
    }

    /** "" or a note for INIT, e.g. "Tunables.Lift: kD, kF missing (library defaults used)". */
    String note() {
        // Nothing asked for, nothing missing (a webcam reads no numbers).
        if (missing.isEmpty()) return "";
        if (section == null) return "Tunables." + name + " is missing: library defaults used";
        return String.format(Locale.US, "Tunables.%s: %s missing (library defaults used)", name, join(missing));
    }

    private Field field(String field) {
        if (section == null) return null;
        try {
            Field f = section.getDeclaredField(field);
            return Modifier.isStatic(f.getModifiers()) ? f : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static String join(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : new java.util.LinkedHashSet<>(names)) sb.append(sb.length() == 0 ? "" : ", ").append(n);
        return sb.toString();
    }
}
