package com.crossbotics.ftchelper.sim;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The numbers behind the names in an autonomous's steps, for the auto builder's "Load an existing auto":
 * {@code START_X} (a constant in the auto), {@code Tunables.Auto.offWallInches}, {@code turnToField(PARK_HEADING)}
 * (a static method of the auto that gives a number), and + - * / and brackets between them. Worked out from the
 * compiled code, as it is now (the Tunables' values in the code, not a live tuning). Anything else: null.
 */
final class SimAutoValues {
    private SimAutoValues() { }

    /** {"values": {"the text": number or null, ...}} for an autonomous class. */
    static String json(Class<?> auto, List<String> exprs) {
        StringBuilder b = new StringBuilder("{\"values\":{");
        int i = 0;
        for (String e : exprs) {
            Double v;
            try {
                v = new Reader(auto, e).read();
            } catch (RuntimeException ex) {
                v = null;
            }
            SimJson.string(b.append(i++ > 0 ? "," : ""), e).append(':')
                    .append(v == null || v.isNaN() || v.isInfinite() ? "null" : String.valueOf(v));
        }
        return b.append("}}").toString();
    }

    /** POST /auto/values: {"className": "...", "exprs": [...]}; the class must be one of the simulator's OpModes. */
    static String answer(String body, List<SimDriverStation.Entry> opModes) {
        Map<String, Object> m = SimJsonReader.object(SimJsonReader.parse(body), "the request");
        String name = SimJsonReader.string(m, "className", "");
        List<String> exprs = new ArrayList<>();
        for (Object o : SimJsonReader.array(m.get("exprs"), "exprs")) exprs.add(String.valueOf(o));
        for (SimDriverStation.Entry e : opModes) {
            if (e.type.getName().equals(name) || e.type.getSimpleName().equals(name)) return json(e.type, exprs);
        }
        return "{\"error\":\"no OpMode " + name.replace("\"", "") + "\"}";
    }

    // A tiny reader: sum := product (('+'|'-') product)*; product := unary (('*'|'/') unary)*;
    // unary := '-' unary | atom; atom := number | name ('(' sum (',' sum)* ')')? | '(' sum ')'.
    private static final class Reader {
        final Class<?> auto;
        final String s;
        int at = 0;

        Reader(Class<?> auto, String s) {
            this.auto = auto;
            this.s = s.trim();
        }

        Double read() {
            double v = sum();
            space();
            if (at != s.length()) throw new IllegalArgumentException("more after " + s.substring(0, at));
            return v;
        }

        private void space() {
            while (at < s.length() && Character.isWhitespace(s.charAt(at))) at++;
        }

        private boolean eat(char c) {
            space();
            if (at < s.length() && s.charAt(at) == c) { at++; return true; }
            return false;
        }

        private double sum() {
            double v = product();
            while (true) {
                if (eat('+')) v += product();
                else if (eat('-')) v -= product();
                else return v;
            }
        }

        private double product() {
            double v = unary();
            while (true) {
                if (eat('*')) v *= unary();
                else if (eat('/')) v /= unary();
                else return v;
            }
        }

        private double unary() {
            if (eat('-')) return -unary();
            if (eat('+')) return unary();
            return atom();
        }

        private double atom() {
            space();
            if (eat('(')) {
                double v = sum();
                if (!eat(')')) throw new IllegalArgumentException("no )");
                return v;
            }
            int start = at;
            if (at < s.length() && (Character.isDigit(s.charAt(at)) || s.charAt(at) == '.')) {
                while (at < s.length() && (Character.isLetterOrDigit(s.charAt(at)) || s.charAt(at) == '.')) at++;
                return Double.parseDouble(s.substring(start, at).replaceAll("[dDfF]$", ""));
            }
            while (at < s.length() && (Character.isJavaIdentifierPart(s.charAt(at)) || s.charAt(at) == '.')) at++;
            String name = s.substring(start, at);
            if (name.isEmpty()) throw new IllegalArgumentException("nothing at " + at);
            if (eat('(')) {
                List<Double> args = new ArrayList<>();
                if (!eat(')')) {
                    do args.add(sum()); while (eat(','));
                    if (!eat(')')) throw new IllegalArgumentException("no )");
                }
                return call(name, args);
            }
            return field(name);
        }

        // A static method of the auto (any access) that takes numbers and gives a number: turnToField(PARK_HEADING).
        private double call(String name, List<Double> args) {
            if (name.contains(".")) throw new IllegalArgumentException(name + ": only the auto's own methods");
            for (Class<?> k = auto; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) {
                    if (!m.getName().equals(name) || !Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != args.size()) continue;
                    if (!isNumber(m.getReturnType())) continue;
                    Class<?>[] p = m.getParameterTypes();
                    Object[] values = new Object[p.length];
                    boolean ok = true;
                    for (int i = 0; i < p.length && ok; i++) {
                        if (p[i] == double.class || p[i] == Double.class) values[i] = args.get(i);
                        else if (p[i] == int.class || p[i] == Integer.class) values[i] = (int) Math.round(args.get(i));
                        else ok = false;
                    }
                    if (!ok) continue;
                    try {
                        m.setAccessible(true);
                        return ((Number) m.invoke(null, values)).doubleValue();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalArgumentException(name, e);
                    }
                }
            }
            throw new IllegalArgumentException("no method " + name);
        }

        // START_X (the auto's own), Tunables.Auto.offWallInches, Other.NAME (a class in the auto's package or above).
        private double field(String name) {
            String[] parts = name.split("\\.");
            if (parts.length == 1) return value(auto, parts[0]);
            String fieldName = parts[parts.length - 1];
            String cls = String.join("$", java.util.Arrays.copyOf(parts, parts.length - 1));
            for (String pkg = auto.getPackage() == null ? "" : auto.getPackage().getName(); ; pkg = pkg.substring(0, Math.max(0, pkg.lastIndexOf('.')))) {
                try {
                    return value(Class.forName((pkg.isEmpty() ? "" : pkg + ".") + cls, true, auto.getClassLoader()), fieldName);
                } catch (ClassNotFoundException ignored) {
                    // look one package up
                }
                if (pkg.isEmpty()) break;
            }
            throw new IllegalArgumentException("no class for " + name);
        }

        private double value(Class<?> k, String name) {
            for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name);
                    if (!Modifier.isStatic(f.getModifiers()) || !isNumber(f.getType())) break;
                    f.setAccessible(true);
                    return ((Number) f.get(null)).doubleValue();
                } catch (NoSuchFieldException ignored) {
                    // the class above
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException(name, e);
                }
            }
            throw new IllegalArgumentException("no number " + name);
        }

        private static boolean isNumber(Class<?> t) {
            return t == double.class || t == int.class || t == float.class || t == long.class || Number.class.isAssignableFrom(t);
        }
    }
}
