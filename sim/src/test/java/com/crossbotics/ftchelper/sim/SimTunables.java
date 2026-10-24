package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.robot.Starting;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Changes Tunables numbers for the simulator only, and puts them back. Two users:
 *
 * <ul>
 *   <li>a scenario file's {@code set tunable Section.field VALUE} (for that file only);</li>
 *   <li>the robot file's stand-ins, {@code tunable.Section.field = VALUE}: at each INIT, a field written
 *       MEASURE gets the stand-in, so the code can be tried before the robot is measured. When the OpMode
 *       ends, the field is MEASURE again. The real robot never reads the robot file: it still stops.</li>
 * </ul>
 *
 * Which Tunables classes: the one each Robot class uses (Tunables next to Robot.java, or the class given with
 * tunables(...)), and every other class named Tunables. Every one that has the field gets the value; when a
 * team's own Tunables (not the library's example robots) has it, only the team's.
 * Docs: docs/guides/simulator.md (sections "Scenario files" and "MEASURE in the simulator").
 */
final class SimTunables {
    private SimTunables() { }

    // The library's example robots' package: a team's own Tunables is set instead when it has the field.
    private static final String EXAMPLES = "com.crossbotics.ftchelper.examples.";
    // The Tunables classes in the code, found once per run of the program (reading the code is slow).
    private static List<Class<?>> classes = null;

    /** The fields a name like "IntakeDeploy.deployed" means, each with the Tunables class it is in. */
    static final class Found {
        final List<Field> fields = new ArrayList<>();
        final List<Class<?>> in = new ArrayList<>();

        /** "Tunables (teamcode)" ...: where the fields are, for a note. */
        List<String> where() {
            List<String> out = new ArrayList<>();
            for (Class<?> c : in) out.add(BuiltAuto.shortName(c));
            return out;
        }
    }

    /**
     * The fields called {@code name} ("Section.field"). Throws with the names that exist (and a "did you
     * mean") when there is none.
     *
     * @param scope which OpModes the simulator lists (their Robot classes give the Tunables classes)
     * @param name  e.g. "IntakeDeploy.deployed"
     */
    static Found find(SimDriverStation.Scope scope, String name) {
        if (name.indexOf('.') < 0) throw new IllegalArgumentException("a Tunables number is written Section.field (e.g. IntakeDeploy.deployed), not \"" + name + "\"");
        String sectionName = name.substring(0, name.indexOf('.'));
        String fieldName = name.substring(name.indexOf('.') + 1);
        List<String> sections = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        Found all = new Found();
        for (Class<?> top : classes(scope)) {
            for (Class<?> section : top.getDeclaredClasses()) {
                if (!sections.contains(section.getSimpleName())) sections.add(section.getSimpleName());
                if (!section.getSimpleName().equals(sectionName)) continue;
                for (Field f : section.getDeclaredFields()) {
                    int mod = f.getModifiers();
                    if (!Modifier.isStatic(mod) || Modifier.isFinal(mod) || f.isSynthetic()) continue;
                    if (!fields.contains(f.getName())) fields.add(f.getName());
                    if (!f.getName().equals(fieldName)) continue;
                    f.setAccessible(true);
                    all.fields.add(f);
                    all.in.add(top);
                }
            }
        }
        // A team's own Tunables (TeamCode) wins: the library's example robots are on the classpath too.
        boolean teamCode = false;
        for (Class<?> c : all.in) teamCode |= !c.getName().startsWith(EXAMPLES);
        Found out = new Found();
        for (int i = 0; i < all.fields.size(); i++) {
            if (teamCode && all.in.get(i).getName().startsWith(EXAMPLES)) continue;
            out.fields.add(all.fields.get(i));
            out.in.add(all.in.get(i));
        }
        if (out.fields.isEmpty()) {
            if (!sections.contains(sectionName)) {
                throw new IllegalArgumentException("no Tunables section \"" + sectionName + "\"" + didYouMean(sectionName, sections)
                        + " (capitals count). Sections: " + String.join(", ", sections));
            }
            throw new IllegalArgumentException("Tunables." + sectionName + " has no field \"" + fieldName + "\"" + didYouMean(fieldName, fields)
                    + " (capitals count). Fields: " + String.join(", ", fields));
        }
        return out;
    }

    /** The value as the field's type: a number (double, float, int, long), true/false, or MEASURE / DEFAULT. */
    static Object value(Field f, String value, String name) {
        Class<?> type = f.getType();
        try {
            if (type == double.class) {
                if (value.equals("MEASURE")) return Starting.MEASURE;
                if (value.equals("DEFAULT")) return Starting.DEFAULT;
                return Double.parseDouble(value);
            }
            if (type == float.class) return Float.parseFloat(value);
            if (type == int.class) return Integer.parseInt(value);
            if (type == long.class) return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tunables." + name + " is a number (" + type.getSimpleName() + "), not \"" + value + "\"");
        }
        if (type == boolean.class) {
            if (value.equals("true")) return true;
            if (value.equals("false")) return false;
            throw new IllegalArgumentException("Tunables." + name + " is true or false, not \"" + value + "\"");
        }
        throw new IllegalArgumentException("Tunables." + name + " is a " + type.getSimpleName() + ": only numbers and true/false can be set");
    }

    /**
     * Sets the field, remembering in {@code before} what it had (only the first time), so {@link #restore}
     * can put it back.
     */
    static void set(Field f, Object v, Map<Field, Object> before, String name) {
        try {
            if (!before.containsKey(f)) before.put(f, f.get(null));
            f.set(null, v);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("cannot set Tunables." + name + ": " + e.getMessage());
        }
    }

    /** Puts back every field in {@code before}, then forgets them. */
    static void restore(Map<Field, Object> before) {
        for (Map.Entry<Field, Object> e : before.entrySet()) {
            try {
                e.getKey().set(null, e.getValue());
            } catch (IllegalAccessException ignored) {
                // it was set before, so it can be set back
            }
        }
        before.clear();
    }

    // ---- the robot file's stand-ins ----

    /** The stand-ins put in at one INIT: what each field had before, and the stand-in it got. */
    static final class StandIns {
        private final Map<Field, Object> before = new LinkedHashMap<>();
        private final Map<Field, Object> put = new LinkedHashMap<>();
        /** The notes for INIT, one per field that got a stand-in. */
        final List<String> notes = new ArrayList<>();

        /**
         * At INIT: every robot file stand-in whose field is MEASURE now gets the stand-in. A field in
         * {@code skip} (a scenario's set tunable) keeps its value: set tunable wins. A stand-in for a
         * field that is not in the code is noted, not an error (the line may be older than the code).
         *
         * @param lines "Section.field" -> the robot file's value
         */
        void apply(SimDriverStation.Scope scope, Map<String, String> lines, Set<Field> skip) {
            for (Map.Entry<String, String> e : lines.entrySet()) {
                String name = e.getKey();
                Found found;
                try {
                    found = find(scope, name);
                } catch (IllegalArgumentException ex) {
                    notes.add("Robot file: tunable." + name + ": " + ex.getMessage() + ". Not used");
                    continue;
                }
                for (Field f : found.fields) {
                    if (skip.contains(f) || f.getType() != double.class) continue;
                    double now;
                    try {
                        now = f.getDouble(null);
                    } catch (IllegalAccessException ex) {
                        continue;
                    }
                    if (!Starting.isMeasure(now)) continue;   // a number is written (or set tunable gave one): it wins
                    Object v;
                    try {
                        v = value(f, e.getValue(), name);
                    } catch (IllegalArgumentException ex) {
                        notes.add("Robot file: tunable." + name + ": " + ex.getMessage() + ". Not used");
                        continue;
                    }
                    set(f, v, before, name);
                    put.put(f, v);
                    String note = "Tunables." + name + " = MEASURE: the simulator uses the stand-in " + e.getValue()
                            + " from the robot file (the real robot stops here)";
                    if (!notes.contains(note)) notes.add(note);
                }
            }
        }

        /** When the OpMode ends: each field still holding its stand-in goes back to MEASURE. */
        void restore() {
            for (Map.Entry<Field, Object> e : before.entrySet()) {
                try {
                    // Only if nothing else changed it since (SimFresh, a test's restore): never undo theirs.
                    if (java.util.Objects.equals(e.getKey().get(null), put.get(e.getKey()))) e.getKey().set(null, e.getValue());
                } catch (IllegalAccessException ignored) {
                    // it was set at INIT, so it can be set back
                }
            }
            before.clear();
            put.clear();
            notes.clear();
        }
    }

    // ---- shared ----

    // " (did you mean X?)" for a name spelled almost like a known one, or "".
    static String didYouMean(String name, List<String> known) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String k : known) {
            int d = k.equalsIgnoreCase(name) ? 0 : com.crossbotics.ftchelper.util.Spelling.distance(k.toLowerCase(Locale.US), name.toLowerCase(Locale.US));
            if (d < bestDistance) {
                bestDistance = d;
                best = k;
            }
        }
        return best != null && bestDistance <= 2 ? " (did you mean " + best + "?)" : "";
    }

    // Every Tunables class in the code: each Robot class's (the way INIT finds it) and every class named Tunables.
    static synchronized List<Class<?>> classes(SimDriverStation.Scope scope) {
        if (classes != null) return classes;
        Set<Class<?>> all = new LinkedHashSet<>();
        for (Class<? extends com.crossbotics.ftchelper.robot.RobotDefinition> c : BuiltAuto.robotClasses(scope)) {
            try {
                Class<?> k = c.getDeclaredConstructor().newInstance().describe().tunablesClass();
                if (k != null) all.add(k);
            } catch (Throwable ignored) {
                // a Robot class that cannot be made here: INIT says why
            }
        }
        all.addAll(SimFresh.tunablesClasses());
        classes = new ArrayList<>(all);
        return classes;
    }
}
