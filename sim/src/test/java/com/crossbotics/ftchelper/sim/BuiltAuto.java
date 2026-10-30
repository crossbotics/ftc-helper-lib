package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.pedro.PedroPath;
import com.crossbotics.ftchelper.robot.Action;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.PathAt;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Steps;
import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Built Auto": the simulator's own autonomous that runs a plan from the page's auto builder (Simple
 * steps or a Pedro path) with the team's REAL code: their Robot class (found by the simulator, or picked
 * on the page), the library's real {@link Auto} and {@link Steps} (the INIT menu, the 30 s timer, the
 * park time, the report), the real Pedro follower and {@link PathAt}, and their own parts' methods and
 * Actions for the named commands ({@link BuiltCode}).
 *
 * <p>It is only in the simulator (sim/src/test, never in a robot build): the page sends the plan
 * (POST /auto), and the simulator INITs this OpMode and presses START. The plan's Java (the page's
 * "Copy Java") makes the same Steps calls, so a run here is a run of that file.
 *
 * <p>Docs: docs/guides/simulator.md, "The auto builder".
 */
public class BuiltAuto extends Auto<RobotDefinition> {
    /** Its name in the Driver Hub's list. */
    static final String NAME = "Built Auto";

    /** The plan to run (set by the page through POST /auto, or by a test). */
    static volatile Program program;
    /** The team's Robot class to run it with. */
    static volatile Class<? extends RobotDefinition> robotClass;

    @Override
    protected RobotDefinition newRobot() {
        Class<? extends RobotDefinition> c = robotClass;
        if (program == null) {
            throw new IllegalStateException(NAME + " runs a plan from the auto builder: open \"Auto builder\" over the 3D view and press Run");
        }
        if (c == null) throw new IllegalStateException(NAME + ": no Robot class to run with (the auto builder found none in the code)");
        try {
            return c.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not make a " + c.getSimpleName() + ": it needs a public constructor with no inputs (" + e + ")", e);
        }
    }

    @Override
    protected boolean mirrorForRed() {
        Program p = program;
        return p != null && p.mirror;
    }

    @Override
    protected void steps(Steps s, RobotDefinition robot) {
        Program p = program;
        if (p == null) {
            throw new IllegalStateException(NAME + " runs a plan from the auto builder: open \"Auto builder\" over the 3D view and press Run");
        }
        p.addTo(s, robot);
    }

    // ----------------------------- the plan -----------------------------

    /**
     * A plan as the page sends it (simcore.js, toProgram): the start, and the steps in the order the
     * Java file has them. Numbers are written for BLUE, seen from the drivers' wall, like the Java.
     */
    static final class Program {
        String name = "Built Auto";
        String mode = "steps";          // "steps" or "pedro" (only for the messages)
        boolean mirror = false;
        String robotClass = "";        // the page's choice ("" = the simulator's guess)
        String note = "";
        double x, y, h;
        boolean spot = true;
        final List<Map<String, Object>> steps = new ArrayList<>();
        // Lines like robot.deployAtStart = false; read from the team's auto: {field, value}, set before the steps.
        final List<Map<String, Object>> setup = new ArrayList<>();

        static Program parse(String json) {
            return from(SimJsonReader.object(SimJsonReader.parse(json), "the plan"));
        }

        /** The plan from its JSON object (SimJsonReader's maps). */
        static Program from(Map<String, Object> m) {
            Program p = new Program();
            p.name = SimJsonReader.string(m, "name", p.name);
            p.mode = SimJsonReader.string(m, "mode", p.mode);
            p.mirror = Boolean.TRUE.equals(m.get("mirror"));
            p.robotClass = SimJsonReader.string(m, "robotClass", "");
            Map<String, Object> st = SimJsonReader.object(m.get("start"), "the plan's start");
            p.note = SimJsonReader.string(st, "note", "");
            p.x = need(st, "x", "start");
            p.y = need(st, "y", "start");
            p.h = need(st, "h", "start");
            p.spot = !Boolean.FALSE.equals(st.get("spot"));
            if (m.get("setup") != null) {
                for (Object o : SimJsonReader.array(m.get("setup"), "the plan's setup")) {
                    p.setup.add(SimJsonReader.object(o, "a setup line"));
                }
            }
            for (Object o : SimJsonReader.array(m.get("steps"), "the plan's steps")) {
                p.steps.add(SimJsonReader.object(o, "a step"));
            }
            return p;
        }

        /**
         * Adds the plan's steps to the list, the same calls the Java file makes. First every name in the plan
         * is found and checked (an action must be a method that gives an Action) WITHOUT calling anything, so
         * a wrong plan stops INIT before any of the team's code runs.
         */
        void addTo(Steps s, RobotDefinition robot) {
            check(robot);
            for (Map<String, Object> line : setup) setField(robot, line);
            if (spot) s.start(note, x, y, h);
            else s.start(note, h);
            for (int i = 0; i < steps.size(); i++) {
                Map<String, Object> st = steps.get(i);
                String kind = SimJsonReader.string(st, "kind", "");
                String label = SimJsonReader.string(st, "label", "");
                String where = "step " + (i + 1) + (label.isEmpty() ? "" : " (\"" + label + "\")");
                try {
                    addStep(s, robot, st, kind, label, where);
                } catch (IllegalArgumentException e) {
                    // Like a part that is not on the robot: INIT stops with the message.
                    throw new IllegalStateException(NAME + ", " + where + ": " + e.getMessage(), e);
                }
            }
        }

        // Every command and action name in the plan, found and checked; nothing is called. A strafe on a
        // tank drive is refused here too, so it never runs (the page turns strafes into a turn and a drive).
        // robot.NAME = value; from the auto, on the Robot class's public field of that name.
        private static void setField(RobotDefinition robot, Map<String, Object> line) {
            String name = SimJsonReader.string(line, "field", "");
            Object value = line.get("value");
            try {
                java.lang.reflect.Field f = robot.getClass().getField(name);
                if (f.getType() == boolean.class && value instanceof Boolean) f.setBoolean(robot, (Boolean) value);
                else if (f.getType() == double.class && value instanceof Number) f.setDouble(robot, ((Number) value).doubleValue());
                else if (f.getType() == int.class && value instanceof Number) f.setInt(robot, ((Number) value).intValue());
                else throw new IllegalArgumentException("is a " + f.getType().getSimpleName());
            } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
                throw new IllegalStateException(NAME + ": robot." + name + " = " + value + " from your auto cannot be set: "
                        + robot.getClass().getSimpleName() + " needs a public boolean or number field with that name", e);
            }
        }

        void check(RobotDefinition robot) {
            boolean tank = robot.drive() != null && !robot.drive().isMecanum();
            for (int i = 0; i < steps.size(); i++) {
                Map<String, Object> st = steps.get(i);
                String kind = SimJsonReader.string(st, "kind", "");
                String label = SimJsonReader.string(st, "label", "");
                String where = "step " + (i + 1) + (label.isEmpty() ? "" : " (\"" + label + "\")");
                try {
                    double dir = SimJsonReader.number(st, "dir", 0);
                    if (tank && kind.equals("move") && Math.abs(Math.sin(Math.toRadians(dir))) > 1e-6) {
                        throw new IllegalArgumentException("a strafe (a move at " + (dir == Math.rint(dir) ? String.valueOf((long) dir) : String.valueOf(dir))
                                + " degrees), not possible on a tank drive: " + robot.getClass().getSimpleName()
                                + " has a tank drive, which only moves at 0 (forward) or 180 (back). Turn first, then move "
                                + "(the auto builder does this for a tank drive)");
                    }
                    String code = SimJsonReader.string(st, "code", "");
                    if (kind.equals("run")) BuiltCode.checkCommand(code, robot);
                    if (kind.equals("action") || kind.equals("alongside")) BuiltCode.checkAction(code, robot);
                    Object list = st.get("along");
                    if (list != null) {
                        for (Object o : SimJsonReader.array(list, where + ": the things along the path")) {
                            Map<String, Object> a = SimJsonReader.object(o, where + ": a PathAt");
                            String c = SimJsonReader.string(a, "code", "");
                            if ("action".equals(SimJsonReader.string(a, "what", "command"))) BuiltCode.checkAction(c, robot);
                            else BuiltCode.checkCommand(c, robot);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException(NAME + ", " + where + ": " + e.getMessage(), e);
                }
            }
        }

        private void addStep(Steps s, RobotDefinition robot, Map<String, Object> st, String kind, String label, String where) {
            switch (kind) {
                case "move": s.move(label, need(st, "inches", where), need(st, "dir", where), need(st, "power", where)); break;
                case "turn": s.turn(label, need(st, "deg", where), need(st, "power", where)); break;
                case "turnTo": s.turnTo(label, need(st, "heading", where), need(st, "power", where)); break;
                case "wait": s.waitSeconds(label, need(st, "sec", where)); break;
                case "run": s.run(label, BuiltCode.command(SimJsonReader.string(st, "code", ""), robot)); break;
                case "aim":
                    if (st.get("sweep") instanceof Number && ((Number) st.get("sweep")).doubleValue() > 0) {
                        s.aimAtGoal(label, need(st, "timeout", where), need(st, "sweep", where));
                    }
                    else s.aimAtGoal(label, need(st, "timeout", where));
                    break;
                case "remember": s.rememberHeading(label); break;
                case "back": s.turnBack(label, need(st, "power", where)); break;
                case "ready": s.readyToLaunch(label, need(st, "timeout", where)); break;
                case "action": s.action(BuiltCode.action(SimJsonReader.string(st, "code", ""), robot), need(st, "timeout", where)); break;
                case "alongside": s.alongside(BuiltCode.action(SimJsonReader.string(st, "code", ""), robot)); break;
                case "park": s.parkWithSeconds(need(st, "sec", where)); break;
                case "follow": {
                    PedroPath path = path(SimJsonReader.object(st.get("path"), where + ": the path"), where);
                    PathAt[] along = along(st, robot, where);
                    Object limit = st.get("timeoutSec");
                    if (limit instanceof Number) s.follow(label, path, ((Number) limit).doubleValue(), along);
                    else s.follow(label, path, along);
                    break;
                }
                case "driveTo":
                    s.driveTo(label, need(st, "x", where), need(st, "y", where), need(st, "h", where), need(st, "power", where),
                            along(st, robot, where));
                    break;
                default: throw new IllegalArgumentException("there is no step \"" + kind + "\"");
            }
        }

        // A Pedro path from the page's parts, like the Java: PedroPath.line/curve(...) with its heading
        // rule and speed limit, the next parts joined with .then(...), then .holdEnd(...).
        private static PedroPath path(Map<String, Object> m, String where) {
            List<Object> parts = SimJsonReader.array(m.get("parts"), where + ": the path's parts");
            if (parts.isEmpty()) throw new IllegalArgumentException("the path has no parts");
            PedroPath all = null;
            for (Object o : parts) {
                Map<String, Object> part = SimJsonReader.object(o, where + ": a part");
                List<Object> pts = SimJsonReader.array(part.get("pts"), where + ": a part's points");
                double[] xy = new double[pts.size() * 2];
                for (int k = 0; k < pts.size(); k++) {
                    List<Object> pt = SimJsonReader.array(pts.get(k), where + ": a point");
                    xy[2 * k] = ((Number) pt.get(0)).doubleValue();
                    xy[2 * k + 1] = ((Number) pt.get(1)).doubleValue();
                }
                PedroPath p = xy.length == 4 ? PedroPath.line(xy[0], xy[1], xy[2], xy[3]) : PedroPath.curve(xy);
                Map<String, Object> h = SimJsonReader.object(part.get("heading"), where + ": a heading");
                switch (SimJsonReader.string(h, "mode", "path")) {
                    case "keep": p = p.keepHeading(need(h, "a", where)); break;
                    case "turn": p = p.turnTo(need(h, "a", where), need(h, "b", where)); break;
                    case "back": p = p.facePathBackwards(); break;
                    case "point": p = p.facePoint(need(h, "x", where), need(h, "y", where)); break;
                    default: break;   // facePath(), the default
                }
                if (part.get("maxSpeed") instanceof Number) p = p.maxSpeed(((Number) part.get("maxSpeed")).doubleValue());
                all = all == null ? p : all.then(p);
            }
            double hold = SimJsonReader.number(m, "holdSec", 0);
            if (hold > 0) all = hold == PedroPath.DEFAULT_HOLD_SEC ? all.holdEnd() : all.holdEnd(hold);
            return all;
        }

        // The PathAt things along a follow or driveTo step.
        private static PathAt[] along(Map<String, Object> st, RobotDefinition robot, String where) {
            Object list = st.get("along");
            if (list == null) return new PathAt[0];
            List<PathAt> out = new ArrayList<>();
            for (Object o : SimJsonReader.array(list, where + ": the things along the path")) {
                Map<String, Object> a = SimJsonReader.object(o, where + ": a PathAt");
                String kind = SimJsonReader.string(a, "kind", "");
                double v = need(a, "value", where);
                boolean isAction = "action".equals(SimJsonReader.string(a, "what", "command"));
                String code = SimJsonReader.string(a, "code", "");
                String label = SimJsonReader.string(a, "label", "");
                Action action = isAction ? BuiltCode.action(code, robot) : null;
                Runnable command = isAction ? null : BuiltCode.command(code, robot);
                switch (kind) {
                    case "inchesDone": out.add(isAction ? PathAt.inchesDone(v, action) : PathAt.inchesDone(v, label, command)); break;
                    case "inchesLeft": out.add(isAction ? PathAt.inchesLeft(v, action) : PathAt.inchesLeft(v, label, command)); break;
                    case "fraction": out.add(isAction ? PathAt.fraction(v, action) : PathAt.fraction(v, label, command)); break;
                    case "part": out.add(isAction ? PathAt.part((int) Math.round(v), action) : PathAt.part((int) Math.round(v), label, command)); break;
                    case "near": {
                        double x = need(a, "x", where), y = need(a, "y", where);
                        out.add(isAction ? PathAt.near(x, y, v, action) : PathAt.near(x, y, v, label, command));
                        break;
                    }
                    default: throw new IllegalArgumentException("there is no PathAt." + kind);
                }
            }
            return out.toArray(new PathAt[0]);
        }

        private static double need(Map<String, Object> m, String key, String where) {
            Object v = m.get(key);
            if (!(v instanceof Number)) throw new IllegalArgumentException(where + ": give " + key + " as a number");
            return ((Number) v).doubleValue();
        }
    }

    // ----------------------------- the team's Robot class -----------------------------

    /**
     * Every Robot class (a subclass of RobotDefinition with a constructor with no inputs) in the code the
     * simulator lists: the team's TeamCode, or the library's examples.
     */
    @SuppressWarnings("unchecked")
    static List<Class<? extends RobotDefinition>> robotClasses(SimDriverStation.Scope scope) {
        List<Class<? extends RobotDefinition>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rel : classFiles()) {
            boolean ours = scope == SimDriverStation.Scope.TEAM ? rel.startsWith("org/firstinspires/ftc/teamcode/")
                    : rel.startsWith("com/crossbotics/ftchelper/examples/");
            if (!ours || rel.contains("$")) continue;
            String name = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
            if (!seen.add(name)) continue;
            try {
                Class<?> c = Class.forName(name, false, BuiltAuto.class.getClassLoader());
                if (!RobotDefinition.class.isAssignableFrom(c) || Modifier.isAbstract(c.getModifiers())
                        || !Modifier.isPublic(c.getModifiers())) continue;
                c.getDeclaredConstructor();
                out.add((Class<? extends RobotDefinition>) c);
            } catch (Throwable ignored) {
                // not a robot, or it cannot load here
            }
        }
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    private static List<String> classFiles() {
        List<String> out = new ArrayList<>();
        for (String part : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File where = new File(part);
            if (where.isDirectory()) collect(where, where, out);
            else if (part.endsWith(".jar") && where.isFile()) {
                try (java.util.jar.JarFile jar = new java.util.jar.JarFile(where)) {
                    java.util.Enumeration<java.util.jar.JarEntry> en = jar.entries();
                    while (en.hasMoreElements()) {
                        String n = en.nextElement().getName();
                        if (n.endsWith(".class")) out.add(n);
                    }
                } catch (java.io.IOException ignored) {
                    // not readable: skip it
                }
            }
        }
        return out;
    }

    private static void collect(File root, File dir, List<String> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collect(root, f, out);
            else if (f.getName().endsWith(".class")) out.add(root.toURI().relativize(f.toURI()).getPath());
        }
    }

    /**
     * The Robot class that fits the simulator's robot best: the one whose device names (from its
     * simLines(), the same lines :sim:fromRobot writes) are most often in the robot file, fewest missing.
     * A team project has one Robot class, so that one.
     */
    static Class<? extends RobotDefinition> guess(Collection<Class<? extends RobotDefinition>> classes, SimRobot sim) {
        Class<? extends RobotDefinition> best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Class<? extends RobotDefinition> c : classes) {
            int score = score(c, sim);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** How well a Robot class fits the robot file: +2 per device name in it, -1 per one missing. */
    static int score(Class<? extends RobotDefinition> c, SimRobot sim) {
        Set<String> have = new LinkedHashSet<>();
        have.addAll(sim.motors.keySet());
        have.addAll(sim.servos.keySet());
        have.addAll(sim.crServos.keySet());
        int score = 0;
        for (String name : deviceNames(c)) score += have.contains(name) ? 2 : -1;
        return score;
    }

    // The motor and servo names a Robot class uses (from its simulator lines; empty if it cannot be made).
    static Set<String> deviceNames(Class<? extends RobotDefinition> c) {
        Set<String> out = new LinkedHashSet<>();
        try {
            for (String line : c.getDeclaredConstructor().newInstance().simLines()) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).split("#")[0].trim();
                if (key.equals("drive.motors")) {
                    for (String n : value.split(",")) out.add(n.trim());
                } else if (key.matches("(motor|servo|crservo)\\..+")) {
                    out.add(key.substring(key.indexOf('.') + 1));
                }
            }
        } catch (Throwable ignored) {
            // a Robot that cannot be described here: no names
        }
        return out;
    }

    /** What the page shows about a Robot class: its drive, odometry and path follower. */
    static String describe(Class<? extends RobotDefinition> c) {
        try {
            RobotDefinition r = c.getDeclaredConstructor().newInstance();
            boolean mecanum = r.drive() != null && r.drive().isMecanum();
            return "{\"name\":\"" + c.getName() + "\",\"short\":\"" + shortName(c) + "\",\"drive\":\"" + (mecanum ? "mecanum" : "tank")
                    + "\",\"odometry\":" + (r.odometrySpec() != null) + ",\"follower\":" + r.hasFollower() + "}";
        } catch (Throwable e) {
            return "{\"name\":\"" + c.getName() + "\",\"short\":\"" + shortName(c) + "\",\"drive\":\"\",\"odometry\":false,\"follower\":false}";
        }
    }

    /**
     * What the auto builder offers for a Robot class, as JSON fields (no braces): the commands its parts
     * have ({@code robot.intake::in}: each part's public methods with no inputs that give nothing, and
     * {@code () -> robot.lift.goTo("high")} for each named position in Tunables), its Actions
     * ({@code Actions.fireAll(robot)}: public static methods of the Actions class next to it that take the
     * robot and give an Action), and the TeleOp that drives it (for "TeleOp next"). Found by reflection:
     * nothing is called but define() (no hardware) and nothing moves.
     */
    static String codeInfo(Class<? extends RobotDefinition> c, List<SimDriverStation.Entry> opModes) {
        Set<String> commands = new LinkedHashSet<>(), actions = new LinkedHashSet<>();
        List<com.crossbotics.ftchelper.robot.Feature> features = new ArrayList<>();
        try {
            // The library's ready-made features: its ready actions come first in the list ("Fire 2").
            features = c.getDeclaredConstructor().newInstance().features();
            for (com.crossbotics.ftchelper.robot.Feature f : features) {
                if (f.ready() && f.kind == com.crossbotics.ftchelper.robot.Feature.Kind.ACTION) actions.add(f.code);
            }
        } catch (Throwable ignored) {
            // a Robot that cannot be made here: no features
        }
        try {
            RobotDefinition r = c.getDeclaredConstructor().newInstance();
            r.modules();   // reads define(): the parts are made (no hardware)
            for (java.lang.reflect.Field f : c.getFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Object part = f.get(r);
                if (!(part instanceof com.crossbotics.ftchelper.robot.Module)) continue;
                for (Class<?> k = part.getClass(); k != null && k != com.crossbotics.ftchelper.robot.Module.class && k != Object.class; k = k.getSuperclass()) {
                    for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                        if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers()) || m.isSynthetic()) continue;
                        if (m.getParameterCount() != 0 || m.getReturnType() != void.class || m.getName().equals("update")) continue;
                        commands.add("robot." + f.getName() + "::" + m.getName());
                    }
                }
                if (part instanceof com.crossbotics.ftchelper.robot.MotorPositions || part instanceof com.crossbotics.ftchelper.robot.ServoPositions) {
                    for (String pos : namedPositions(c, ((com.crossbotics.ftchelper.robot.Module<?>) part).name())) {
                        commands.add("() -> robot." + f.getName() + ".goTo(\"" + pos + "\")");
                    }
                }
            }
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                        && m.getReturnType() == void.class) commands.add("robot::" + m.getName());
            }
        } catch (Throwable ignored) {
            // a Robot that cannot be made here: no commands
        }
        try {
            Class<?> a = BuiltCode.actionsClass(c.getDeclaredConstructor().newInstance(), "");
            for (java.lang.reflect.Method m : a.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || !Action.class.isAssignableFrom(m.getReturnType())) continue;
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(c)) actions.add("Actions." + m.getName() + "(robot)");
            }
        } catch (Throwable ignored) {
            // no Actions class: no actions
        }
        StringBuilder b = new StringBuilder("\"commands\":[");
        int i = 0;
        for (String s : commands) SimJson.string(b.append(i++ > 0 ? "," : ""), s);
        b.append("],\"actions\":[");
        i = 0;
        for (String s : actions) SimJson.string(b.append(i++ > 0 ? "," : ""), s);
        b.append("],\"features\":[");
        i = 0;
        for (com.crossbotics.ftchelper.robot.Feature f : features) {
            b.append(i++ > 0 ? "," : "").append("{\"kind\":");
            SimJson.string(b, f.kind.name());
            SimJson.string(b.append(",\"words\":"), f.kind.words());
            SimJson.string(b.append(",\"name\":"), f.name);
            SimJson.string(b.append(",\"code\":"), f.code);
            SimJson.string(b.append(",\"needs\":"), f.needs);
            SimJson.string(b.append(",\"missing\":"), f.missing);
            SimJson.string(b.append(",\"note\":"), f.note);
            b.append('}');
        }
        b.append("],\"teleop\":");
        return SimJson.string(b, teleOpFor(c, opModes)).toString();
    }

    // Named positions in Tunables.<Part> (the numbers that are not settings): a lift's "high", a claw's "open".
    private static final Set<String> SETTINGS = new java.util.HashSet<>(java.util.Arrays.asList(
            "min", "max", "kP", "kI", "kD", "kF", "maxPower", "slowZone", "slowZonePower", "tolerance", "speed", "rest", "start"));

    private static List<String> namedPositions(Class<?> robotClass, String partName) {
        List<String> out = new ArrayList<>();
        String pkg = robotClass.getPackage() == null ? "" : robotClass.getPackage().getName() + ".";
        try {
            Class<?> t = Class.forName(pkg + "Tunables$" + partName, false, robotClass.getClassLoader());
            for (java.lang.reflect.Field f : t.getFields()) {
                boolean number = f.getType() == double.class || f.getType() == Double.class;
                if (Modifier.isStatic(f.getModifiers()) && number && !SETTINGS.contains(f.getName())) out.add(f.getName());
            }
        } catch (Throwable ignored) {
            // no such section: none
        }
        return out;
    }

    /**
     * The TeleOp that drives this Robot class: one whose class is {@code ControlMap<Robot>} (or any OpMode
     * whose generic type is the Robot); else one in the same package; "" if none.
     */
    static String teleOpFor(Class<? extends RobotDefinition> c, List<SimDriverStation.Entry> opModes) {
        String samePackage = "";
        for (SimDriverStation.Entry e : opModes) {
            if (!"TeleOp".equals(e.kind)) continue;
            if (robotOf(e.type) == c) return e.name;
            if (samePackage.isEmpty() && e.type.getPackage() == c.getPackage()) samePackage = e.name;
        }
        return samePackage;
    }

    /** The Robot class an OpMode is written for (its {@code Auto<Robot>} or {@code ControlMap<Robot>}), or null. */
    static Class<?> robotOf(Class<?> opMode) {
        for (Class<?> k = opMode; k != null && k != Object.class; k = k.getSuperclass()) {
            java.lang.reflect.Type t = k.getGenericSuperclass();
            if (!(t instanceof java.lang.reflect.ParameterizedType)) continue;
            for (java.lang.reflect.Type arg : ((java.lang.reflect.ParameterizedType) t).getActualTypeArguments()) {
                if (arg instanceof Class && RobotDefinition.class.isAssignableFrom((Class<?>) arg)) return (Class<?>) arg;
            }
        }
        return null;
    }

    // examples.gobildabot.Robot -> gobildabot.Robot (the package's last word tells them apart)
    static String shortName(Class<?> c) {
        String pkg = c.getPackage() == null ? "" : c.getPackage().getName();
        String last = pkg.substring(pkg.lastIndexOf('.') + 1);
        return (last.isEmpty() ? "" : last + ".") + c.getSimpleName();
    }
}
