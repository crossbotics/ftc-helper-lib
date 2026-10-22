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
     * Every table whose field name starts with {@code prefix}, by name: the rest of the field name in
     * lower case ({@code speedByDistancePollen} -> "pollen"), or "main" for the field called just
     * {@code prefix}. In a fixed order on every phone and laptop: "main" first, then by name (Java
     * does not promise the order fields are written in). The value is the field's name and its rows.
     *
     * @param prefix e.g. "speedByDistance"
     */
    Map<String, Map.Entry<String, double[][]>> tables(String prefix) {
        java.util.TreeMap<String, Map.Entry<String, double[][]>> sorted = new java.util.TreeMap<>();
        if (section != null) {
            for (Field f : section.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType() != double[][].class) continue;
                if (!f.getName().startsWith(prefix)) continue;
                String rest = f.getName().substring(prefix.length());
                String key = rest.isEmpty() ? "main" : rest.toLowerCase(Locale.US);
                try {
                    f.setAccessible(true);
                    double[][] rows = (double[][]) f.get(null);
                    if (rows != null) sorted.put(key, new java.util.AbstractMap.SimpleImmutableEntry<>(f.getName(), rows));
                } catch (IllegalAccessException e) {
                    // a field the library cannot read is left out
                }
            }
        }
        Map<String, Map.Entry<String, double[][]>> out = new LinkedHashMap<>();
        if (sorted.containsKey("main")) out.put("main", sorted.remove("main"));
        out.putAll(sorted);
        return out;
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
            String typo = lookalike(f.getName(), known, this::has);
            if (typo != null) {
                // "Max" would quietly become a position named Max, and max would stay the library's
                // default: a lift could drive past its real top. Stop instead.
                throw new IllegalStateException("Tunables." + name + "." + f.getName() + " looks like \"" + typo
                        + "\" (capitals and spelling count). Fix the name in Tunables.java; a position must not "
                        + "look like a setting.");
            }
            out.put(f.getName(), number(f.getName(), 0));
        }
        return out;
    }

    /**
     * Stops at INIT if the section does not write {@code field} itself (a safety number, like a
     * lift's max, must never come from a library default).
     *
     * @param field the field name, e.g. "max"
     * @param why   the end of the message, e.g. "the top of the safe range (Motor Finder: B)"
     */
    void require(String field, String why) {
        Field f = field(field);
        if (f != null) {
            Class<?> t = f.getType();
            boolean numeric = t == double.class || t == int.class || t == float.class || t == long.class;
            double v = Double.NaN;
            if (numeric) {
                try {
                    v = ((Number) f.get(null)).doubleValue();
                } catch (IllegalAccessException | RuntimeException e) {
                    v = Double.NaN;
                }
            }
            if (numeric && !Double.isNaN(v) && !Double.isInfinite(v)) return;
            throw new IllegalStateException("Tunables." + name + "." + field + " must be a public static number (like "
                    + "\"public static double " + field + " = 1000;\"): " + why + ". A safety limit never comes from a library default.");
        }
        throw new IllegalStateException((section == null ? "Tunables.java has no " + name + " section" : "Tunables." + name
                + " has no " + field) + ": write " + field + " (" + why + "). A safety limit never comes from a "
                + "library default.");
    }

    // A known setting this name looks like, or null. Always: the same but for capitals or "_" ("Max",
    // "k_P"). Only when the real setting is NOT written (so "slowZone2" next to "slowZone" is a position):
    // up to two letters off for names of 5 letters or more ("maxPowr", "maxPwr" -> "maxPower").
    static String lookalike(String name, Collection<String> known, java.util.function.Predicate<String> written) {
        String n = squash(name);
        for (String k : known) {
            if (squash(k).equals(n)) return k;
        }
        for (String k : known) {
            if (written.test(k)) continue;
            if (k.length() >= 5 && n.length() >= 5 && editDistance(squash(k), n) <= 2) return k;
        }
        return null;
    }

    static String lookalike(String name, Collection<String> known) {
        return lookalike(name, known, k -> false);
    }

    // Lower case without "_": the forms a typo hides in.
    private static String squash(String s) {
        return s.replace("_", "").toLowerCase(Locale.US);
    }

    // How many letters must be added, removed or changed to turn a into b (no loops that wait: short names).
    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int change = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                cur[j] = Math.min(change, Math.min(prev[j] + 1, cur[j - 1] + 1));
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /**
     * "" or a note for INIT, e.g. "Tunables.Lift: kD, kF not written: library defaults used (fine)". Worded as
     * harmless: a missing number is not a problem, the library's default is used (add the line to change it).
     */
    String note() {
        // Nothing asked for, nothing missing (a webcam reads no numbers).
        if (missing.isEmpty()) return "";
        if (section == null) return "Tunables." + name + " not written: library defaults used (fine)";
        return String.format(Locale.US, "Tunables.%s: %s not written: library defaults used (fine)", name, join(missing));
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
