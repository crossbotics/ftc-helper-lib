package com.crossbotics.ftchelper.sim;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes the simulator start like a robot that was just turned on, before each scenario file, each
 * recording and each test run on the page. WHY: the FTC app keeps some things from one program to the
 * next (the alliance, a turret's angle, a launcher's trim, numbers tuned in the tuning menu). On a robot
 * that is what you want; in a test it means the second test file sees what the first one left behind
 * (a TeleOp test after an autonomous test failed its pick-up). So before each test: forget all of it,
 * put every Tunables number back to the value written in the code, and use an empty settings file
 * (tuned numbers saved on the pretend Control Hub do not count in a test: only the code does).
 */
final class SimFresh {
    private SimFresh() { }

    /** One static field of a Tunables class and the value the code gave it. */
    private static final class Saved {
        final Field field;
        final Object value;

        Saved(Field field, Object value) {
            this.field = field;
            this.value = value;
        }
    }

    // The code values, read once (before any program could change them): null = not read yet.
    private static List<Saved> codeValues = null;

    /**
     * Reads every Tunables number as the code wrote it. Call it once at the start (the simulator page,
     * the scenario command), before any OpMode runs; later calls do nothing.
     */
    static synchronized void readCodeValues() {
        if (codeValues != null) return;
        codeValues = new ArrayList<>();
        for (Class<?> c : tunablesClasses()) {
            save(c, codeValues);
            for (Class<?> section : c.getDeclaredClasses()) save(section, codeValues);
        }
    }

    /** What the Tunables fields hold now (to put back after a test run on the page). */
    static synchronized List<Object[]> current() {
        readCodeValues();
        List<Object[]> out = new ArrayList<>();
        for (Saved s : codeValues) {
            try {
                out.add(new Object[] {s.field, copy(s.field.get(null))});
            } catch (IllegalAccessException e) {
                // a field we cannot read: left as it is
            }
        }
        return out;
    }

    /** Puts back what {@link #current()} read. */
    static synchronized void restore(List<Object[]> values) {
        for (Object[] v : values) set((Field) v[0], v[1]);
    }

    /**
     * Fresh robot app: forget what programs keep for the next one (RobotDefinition.freshRobotApp), every
     * Tunables number back to its code value, and an empty settings file.
     */
    static synchronized void robotApp() {
        readCodeValues();
        com.crossbotics.ftchelper.robot.RobotDefinition.freshRobotApp();
        for (Saved s : codeValues) set(s.field, s.value);
        try {
            File f = File.createTempFile("sim-test-settings", ".properties");
            f.deleteOnExit();
            com.crossbotics.ftchelper.core.SettingsFile.useForTests(new com.crossbotics.ftchelper.core.SettingsFile(f));
        } catch (java.io.IOException e) {
            // no temporary file: the robot's own settings file is used (saved tuned numbers then count)
        }
    }

    private static void save(Class<?> c, List<Saved> into) {
        for (Field f : c.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (!Modifier.isStatic(mod) || Modifier.isFinal(mod) || f.isSynthetic()) continue;
            try {
                f.setAccessible(true);
                into.add(new Saved(f, copy(f.get(null))));
            } catch (IllegalAccessException | RuntimeException e) {
                // a field the simulator cannot read stays as it is
            }
        }
    }

    private static void set(Field f, Object value) {
        try {
            f.set(null, copy(value));
        } catch (IllegalAccessException | IllegalArgumentException e) {
            // cannot happen for the fields read above; skip it
        }
    }

    // Arrays (shot tables: double[][]) are copied, so a change to the live one cannot change the saved one.
    private static Object copy(Object v) {
        if (v instanceof double[][]) {
            double[][] a = (double[][]) v, b = new double[a.length][];
            for (int i = 0; i < a.length; i++) b[i] = a[i] == null ? null : a[i].clone();
            return b;
        }
        if (v instanceof double[]) return ((double[]) v).clone();
        if (v instanceof int[]) return ((int[]) v).clone();
        if (v instanceof Object[]) return ((Object[]) v).clone();
        return v;
    }

    // Every class named Tunables in the code the simulator runs (the team's and the library's examples).
    private static List<Class<?>> tunablesClasses() {
        List<Class<?>> out = new ArrayList<>();
        for (String part : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            File where = new File(part);
            List<String> names = new ArrayList<>();
            if (where.isDirectory()) {
                find(where, "", names);
            } else if (part.endsWith(".jar") && where.isFile()) {
                try (java.util.jar.JarFile jar = new java.util.jar.JarFile(where)) {
                    java.util.Enumeration<java.util.jar.JarEntry> en = jar.entries();
                    while (en.hasMoreElements()) {
                        String e = en.nextElement().getName();
                        // only the team's and the library's own classes (not other libraries')
                        if (!(e.startsWith("com/crossbotics/") || e.startsWith("org/firstinspires/ftc/teamcode/"))) continue;
                        if (e.endsWith("/Tunables.class")) names.add(e.substring(0, e.length() - 6).replace('/', '.'));
                    }
                } catch (java.io.IOException ignored) {
                    // not readable: skip it
                }
            }
            for (String n : names) {
                try {
                    out.add(Class.forName(n, true, SimFresh.class.getClassLoader()));
                } catch (Throwable ignored) {
                    // a class that cannot load here (it needs Android): leave it out
                }
            }
        }
        return out;
    }

    private static void find(File dir, String pkg, List<String> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) find(f, pkg + f.getName() + ".", out);
            else if (f.getName().equals("Tunables.class") && (pkg.startsWith("com.crossbotics.") || pkg.startsWith("org.firstinspires.ftc.teamcode."))) {
                out.add(pkg + "Tunables");
            }
        }
    }
}
