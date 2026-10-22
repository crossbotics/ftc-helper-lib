package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the auto builder's "code" texts into things the real {@code Steps} can run, for the simulator's
 * Built Auto. The builder writes them for the Java file ({@code robot.claw::close},
 * {@code () -> robot.lift.goTo("high")}, {@code Actions.scoreHigh(robot)}); here the same text is read and
 * the team's own public method is found by its name, with Java reflection (asking a class, while the
 * program runs, for its fields and methods by name).
 *
 * <p>Why not compile the text, or run it as a script? Safety and plainness: only these shapes are read,
 * and only public members of the team's Robot class, its parts (the Robot's public fields) and the
 * Actions class next to it can be reached. Arguments must be plain values (text in quotes, numbers,
 * true/false). Nothing else in the program, or on the laptop, can be called from the page. Anything the
 * builder cannot read is refused at INIT with a message, like a part that is not on the robot.
 *
 * <p>Shapes read:
 * <ul>
 *   <li>a command: {@code robot.PART::METHOD}, {@code robot::METHOD},
 *       {@code () -> robot.PART.METHOD(args)}, {@code () -> robot.METHOD(args)}</li>
 *   <li>an action: {@code Actions.METHOD(robot, args)} (a public static method of the Actions class in
 *       the Robot's package), {@code robot.PART.METHOD(args)} or {@code robot.METHOD(args)} returning an Action</li>
 * </ul>
 */
final class BuiltCode {
    private BuiltCode() { }

    private static final String NAME = "[A-Za-z_$][A-Za-z0-9_$]*";
    private static final Pattern METHOD_REF = Pattern.compile("^robot(?:\\.(" + NAME + "))?::(" + NAME + ")$");
    private static final Pattern LAMBDA = Pattern.compile("^\\(\\s*\\)\\s*->\\s*(.+)$", Pattern.DOTALL);
    private static final Pattern CALL = Pattern.compile("^(robot|Actions)((?:\\s*\\.\\s*" + NAME + ")+)\\s*\\((.*)\\)$", Pattern.DOTALL);

    /** A command (a Runnable) from its text, for {@code run} and {@code PathAt} with a name. */
    static Runnable command(String code, RobotDefinition robot) {
        String c = clean(code);
        Matcher m = METHOD_REF.matcher(c);
        if (m.matches()) {
            Object target = m.group(1) == null ? robot : part(robot, m.group(1), c);
            Method method = find(target.getClass(), m.group(2), new ArrayList<>(), robot, c);
            return () -> call(method, target, new Object[0], c);
        }
        Matcher l = LAMBDA.matcher(c);
        if (l.matches()) {
            final Call call = parseCall(clean(l.group(1)), robot, c, false);
            return () -> call.invoke();
        }
        throw new IllegalArgumentException(c + ": the simulator can run a command written as robot.part::method, "
                + "robot::method, or () -> robot.part.method(...) with plain values (text in quotes, numbers, true/false). "
                + "Write anything else as a method in Robot.java or Actions.java and name that here");
    }

    /**
     * An action from its text, for {@code action}, {@code alongside} and {@code PathAt} with an action. The
     * method is found and its return type checked BEFORE it is called: a method that is not an Action
     * maker (a command like {@code robot.claw.open()}) is refused without running it.
     */
    static Action action(String code, RobotDefinition robot) {
        Call call = actionCall(code, robot);
        Object made = call.invoke();
        if (!(made instanceof Action)) {
            throw new IllegalArgumentException(call.code + ": " + call.method.getName() + " gave nothing (null), not an Action");
        }
        return (Action) made;
    }

    /** Checks an action's text (the method, its values, that it gives an Action) without calling anything. */
    static void checkAction(String code, RobotDefinition robot) {
        actionCall(code, robot);
    }

    /** Checks a command's text (the method and its values) without calling anything. */
    static void checkCommand(String code, RobotDefinition robot) {
        command(code, robot);   // only finds the method: the Runnable it gives is not run here
    }

    private static Call actionCall(String code, RobotDefinition robot) {
        String c = clean(code);
        Call call = parseCall(c, robot, c, true);
        if (!Action.class.isAssignableFrom(call.method.getReturnType())) {
            Class<?> r = call.method.getReturnType();
            throw new IllegalArgumentException(c + ": " + call.method.getName() + " does not give an Action (it gives "
                    + (r == void.class ? "nothing" : r.getSimpleName()) + "). A method that does something at once is a "
                    + "command: use it in a \"run\" step");
        }
        return call;
    }

    // ----------------------------- reading the text -----------------------------

    private static String clean(String code) {
        String c = code == null ? "" : code.trim();
        while (c.endsWith(";")) c = c.substring(0, c.length() - 1).trim();
        if (c.isEmpty() || c.equals("null")) throw new IllegalArgumentException("nothing to do: give a command or an action");
        return c;
    }

    // A call: robot.m(...), robot.part.m(...), Actions.m(...). Who to call, the method and its values.
    private static final class Call {
        Object target;   // null for a static method
        Method method;
        Object[] args;
        String code;

        Object invoke() {
            return call(method, target, args, code);
        }
    }

    private static Call parseCall(String expr, RobotDefinition robot, String code, boolean actionWanted) {
        Matcher m = CALL.matcher(expr);
        if (!m.matches()) {
            throw new IllegalArgumentException(code + ": the simulator can run "
                    + (actionWanted ? "Actions.name(robot, ...) or robot.part.method(...) that gives an Action"
                    : "robot.part::method or () -> robot.part.method(...)")
                    + ", with plain values (text in quotes, numbers, true/false)");
        }
        String[] names = m.group(2).replaceAll("\\s", "").substring(1).split("\\.");
        List<Object> values = values(m.group(3), robot, code);
        Call call = new Call();
        call.code = code;
        Class<?> type;
        if (m.group(1).equals("Actions")) {
            if (names.length != 1) throw new IllegalArgumentException(code + ": write Actions.name(robot, ...)");
            type = actionsClass(robot, code);
            call.target = null;
        } else {
            if (names.length > 2) throw new IllegalArgumentException(code + ": one part at most: robot.part.method(...)");
            call.target = names.length == 2 ? part(robot, names[0], code) : robot;
            type = call.target.getClass();
        }
        String methodName = names[names.length - 1];
        call.method = find(type, methodName, values, robot, code);
        boolean isStatic = Modifier.isStatic(call.method.getModifiers());
        if (m.group(1).equals("Actions") && !isStatic) {
            throw new IllegalArgumentException(code + ": Actions." + methodName + " must be static (public static Action ...)");
        }
        call.args = convert(call.method.getParameterTypes(), values);
        return call;
    }

    // The values between the brackets: robot, "text", numbers, true, false. Kept as Java objects
    // (robot = the robot object itself; numbers as Double until the method says which kind it wants).
    private static List<Object> values(String text, RobotDefinition robot, String code) {
        List<Object> out = new ArrayList<>();
        String s = text.trim();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '"') {
                StringBuilder b = new StringBuilder();
                int k = i + 1;
                for (; k < s.length() && s.charAt(k) != '"'; k++) {
                    char ch = s.charAt(k);
                    if (ch == '\\' && k + 1 < s.length()) {
                        char e = s.charAt(++k);
                        b.append(e == 'n' ? '\n' : e == 't' ? '\t' : e);
                    } else {
                        b.append(ch);
                    }
                }
                if (k >= s.length()) throw new IllegalArgumentException(code + ": a text value has no closing \"");
                out.add(b.toString());
                i = k + 1;
            } else {
                int k = i;
                while (k < s.length() && s.charAt(k) != ',') k++;
                String word = s.substring(i, k).trim();
                if (word.equals("robot")) out.add(robot);
                else if (word.equals("true") || word.equals("false")) out.add(Boolean.valueOf(word));
                else {
                    try {
                        out.add(Double.parseDouble(word.replaceAll("[dDfF]$", "")));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(code + ": \"" + word + "\" is not a plain value. The simulator reads "
                                + "text in quotes, numbers, true/false and robot. Put the rest in a method of your own");
                    }
                }
                i = k;
            }
            // the comma between values
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            if (i < s.length()) {
                if (s.charAt(i) != ',') throw new IllegalArgumentException(code + ": could not read the values in the brackets");
                i++;
            }
        }
        return out;
    }

    // ----------------------------- finding the team's members -----------------------------

    /** The Robot's public field (a part), e.g. robot.claw. */
    private static Object part(RobotDefinition robot, String name, String code) {
        Field f;
        try {
            f = robot.getClass().getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(code + ": " + robot.getClass().getSimpleName() + ".java has no part called "
                    + name + " (its parts: " + parts(robot) + ")");
        }
        Object value;
        try {
            value = f.get(robot);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(code + ": " + name + " cannot be read (" + e.getMessage() + ")");
        }
        if (value == null) {
            throw new IllegalArgumentException(code + ": robot." + name + " is not set up (null). Is it added in define()?");
        }
        return value;
    }

    /** The Robot's public fields, for the messages. */
    static String parts(RobotDefinition robot) {
        Set<String> names = new TreeSet<>();
        for (Field f : robot.getClass().getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) names.add(f.getName());
        }
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    /** The team's Actions class: Actions in the same package as its Robot class. */
    static Class<?> actionsClass(RobotDefinition robot, String code) {
        String pkg = robot.getClass().getPackage() == null ? "" : robot.getClass().getPackage().getName();
        String name = (pkg.isEmpty() ? "" : pkg + ".") + "Actions";
        try {
            return Class.forName(name, true, robot.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(code + ": there is no Actions class next to " + robot.getClass().getSimpleName()
                    + " (" + name + ")");
        }
    }

    // A public method by name whose inputs take these values. Messages list the names there are.
    private static Method find(Class<?> type, String name, List<Object> values, RobotDefinition robot, String code) {
        List<Method> sameName = new ArrayList<>();
        for (Method m : type.getMethods()) {
            if (m.getDeclaringClass() == Object.class || !m.getName().equals(name)) continue;
            sameName.add(m);
            if (fits(m.getParameterTypes(), values)) return m;
        }
        if (!sameName.isEmpty()) {
            throw new IllegalArgumentException(code + ": " + type.getSimpleName() + "." + name + " does not take "
                    + (values.isEmpty() ? "no values" : values.size() == 1 ? "this value" : "these " + values.size() + " values")
                    + " (it takes " + shapes(sameName) + ")");
        }
        Set<String> names = new TreeSet<>();
        for (Method m : type.getMethods()) if (m.getDeclaringClass() != Object.class) names.add(m.getName());
        throw new IllegalArgumentException(code + ": " + type.getSimpleName() + " has no public method called " + name
                + " (some it has: " + first(names, 14) + ")");
    }

    private static String shapes(List<Method> methods) {
        List<String> out = new ArrayList<>();
        for (Method m : methods) {
            List<String> types = new ArrayList<>();
            for (Class<?> t : m.getParameterTypes()) types.add(t.getSimpleName());
            out.add("(" + String.join(", ", types) + ")");
        }
        return String.join(" or ", out);
    }

    private static String first(Set<String> names, int n) {
        List<String> out = new ArrayList<>();
        for (String s : names) {
            if (out.size() == n) { out.add("..."); break; }
            out.add(s);
        }
        return String.join(", ", out);
    }

    private static boolean fits(Class<?>[] types, List<Object> values) {
        if (types.length != values.size()) return false;
        for (int i = 0; i < types.length; i++) if (!fits(types[i], values.get(i))) return false;
        return true;
    }

    private static boolean fits(Class<?> t, Object v) {
        if (v instanceof RobotDefinition) return t.isInstance(v);
        if (v instanceof String) return t == String.class || t == CharSequence.class || t == Object.class || t.isEnum();
        if (v instanceof Boolean) return t == boolean.class || t == Boolean.class;
        if (v instanceof Double) {
            double d = (Double) v;
            boolean whole = d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE;
            return t == double.class || t == Double.class || t == float.class || t == Float.class
                    || ((t == int.class || t == Integer.class || t == long.class || t == Long.class) && whole);
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object[] convert(Class<?>[] types, List<Object> values) {
        Object[] out = new Object[values.size()];
        for (int i = 0; i < out.length; i++) {
            Object v = values.get(i);
            Class<?> t = types[i];
            if (v instanceof Double) {
                double d = (Double) v;
                if (t == int.class || t == Integer.class) v = (int) d;
                else if (t == long.class || t == Long.class) v = (long) d;
                else if (t == float.class || t == Float.class) v = (float) d;
            } else if (v instanceof String && t.isEnum()) {
                v = Enum.valueOf((Class<? extends Enum>) t, (String) v);
            }
            out[i] = v;
        }
        return out;
    }

    // Calls the method; an error inside the team's own code comes out as itself (the page shows it).
    private static Object call(Method m, Object target, Object[] args, String code) {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            if (t instanceof Error) throw (Error) t;
            throw new IllegalStateException(code + ": " + t, t);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(code + ": cannot call it (" + e.getMessage() + ")", e);
        }
    }
}
