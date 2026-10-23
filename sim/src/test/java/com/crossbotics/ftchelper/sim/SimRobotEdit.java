package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Shape;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The page's Edit robot mode (docs/guides/simulator.md, "Edit robot: shape your robot"): the team moves
 * and sizes the robot's shapes in the 3D view, and copies the {@code .looks(...)} Java it prints into
 * Robot.java. Robot.java stays the one record: nothing here writes a file.
 *
 * <pre>
 * GET  /robot-edit          the shapes, their numbers (mm, degrees), named poses, and whose part each is
 * POST /robot-edit/mode     on=1|0: open or close the mode (the simulator holds still while it is open)
 * POST /robot-edit/preview  {"edits": {...}, "values": {shape: value}}: the shapes drawn with the changes,
 *                           in that pose, and the size checks
 * POST /robot-edit/java     {"edits": {...}, "all": false}: the Java to paste for each changed part
 * POST /robot-edit/try      {"edits": {...}}: the running simulator uses the changed shapes ({} = as read)
 * </pre>
 *
 * Edits are per shape name, in Java's units: {"intake_arm": {"atMm": [150, 0, 100], "range": [-15, 90]}}
 * (see {@link SimShapeJava#apply}). They are always from the robot file as it was read, so sending them
 * again gives the same result.
 */
final class SimRobotEdit {
    /** One shape and where its Java lives. */
    static final class Info {
        final String shape;
        /** looks (a part's .looks in Robot.java), guess (a part's guessed shape), fixed (fixed(...)), file (the robot file only). */
        String where = "file";
        Object module;               // the part (a Module), or null
        String field;                // the part's field in Robot.java, or null
        String partKey;              // shapes with one key are printed together
        String partWords = "";       // e.g. the Intake "intake"
        int index;                   // its place in the part's looks(...)
        SimShapeJava java;           // as Robot.java has it (or made from the guessed line); null for "file"
        // A servo arm's two named angles, and the servo positions (0..1, as the servo turns) they are at.
        final List<String> angleNames = new ArrayList<>();
        double[] angleAt;
        // Every named position of the servo that moves it (0..1 as the servo turns), for the pose buttons.
        final Map<String, Double> servoPositions = new LinkedHashMap<>();

        Info(String shape) {
            this.shape = shape;
        }
    }

    private final SimEngine sim;
    private final Path robotFile;
    private boolean ready = false;
    String robotClass = "";
    /** Why there is no Java (no Robot.java found for this robot file), or "". */
    String why = "";
    final Map<String, Info> info = new LinkedHashMap<>();
    // The shapes as the robot file was read (the ranges and counts the edits start from).
    private SimShapes base;
    // The edits "Try it" put in the simulator ("{}" = none), for a page that opens again.
    private String tried = "{}";
    private volatile boolean editing = false;

    SimRobotEdit(SimEngine sim, Path robotFile) {
        this.sim = sim;
        this.robotFile = robotFile;
    }

    // ----------------------------------------------------------------------------- finding Robot.java

    private synchronized void setUp() {
        if (ready) return;
        ready = true;
        base = sim.robot.shapes;
        for (String name : sim.robot.shapeLines.keySet()) info.put(name, new Info(name));
        Class<?> c = findRobotClass();
        if (c == null) {
            why = "No Robot.java made this robot file (its first line does not say \"made from\"), so the editor gives robot file lines.";
            return;
        }
        try {
            RobotDefinition def = (RobotDefinition) c.getDeclaredConstructor().newInstance();
            def.simLines();          // sets each part up for the simulator file (its Tunables section)
            robotClass = c.getName();
            read(def);
        } catch (Throwable e) {
            why = "Could not read " + c.getName() + ": " + e;
            robotClass = "";
            for (Info i : info.values()) {
                i.where = "file";
                i.java = null;
            }
        }
    }

    // The robot file's "made from X" (written by :sim:fromRobot), or -ProbotClass (sim.robotClass).
    private Class<?> findRobotClass() {
        String name = System.getProperty("sim.robotClass", "").trim();
        if (name.isEmpty() && robotFile != null && Files.isRegularFile(robotFile)) {
            try {
                for (String line : new String(Files.readAllBytes(robotFile), StandardCharsets.UTF_8).split("\n")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^#.*made from ([\\w.$]+)").matcher(line);
                    if (m.find()) {
                        name = m.group(1);
                        break;
                    }
                    if (!line.startsWith("#") && !line.trim().isEmpty()) break;
                }
            } catch (java.io.IOException ignored) {
                // no header: no Java
            }
        }
        if (name.isEmpty()) return null;
        try {
            Class<?> c = Class.forName(name);
            return RobotDefinition.class.isAssignableFrom(c) ? c : null;
        } catch (ClassNotFoundException e) {
            why = "The robot file was made from " + name + ", which is not in the code the simulator runs.";
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void read(RobotDefinition def) throws Exception {
        // Each part's field in Robot.java (intakeDeploy = add(...)).
        Map<Object, String> fields = new IdentityHashMap<>();
        for (Class<?> k = def.getClass(); k != null && k != RobotDefinition.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !Module.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object v = f.get(def);
                if (v != null && !fields.containsKey(v)) fields.put(v, f.getName());
            }
        }
        java.util.function.Function<Object, String> javaName = fields::get;
        List<Module<?>> all = new ArrayList<>();
        for (Module<?> m : def.modules()) {
            all.addAll((List<Module<?>>) call(m, "children"));
            all.add(m);
        }
        for (Module<?> m : all) {
            String device = deviceOf(m);
            String words = "the " + m.getClass().getSimpleName() + " \"" + device + "\"";
            List<Shape> looks = (List<Shape>) SimShapeJava.get(m, "looks");
            Map<String, Double> positions = servoPositions(m);
            for (int i = 0; i < looks.size(); i++) {
                Info in = info.get((String) call(m, "shapeName", i));
                if (in == null) continue;
                in.where = "looks";
                in.index = i;
                in.java = SimShapeJava.of(looks.get(i), javaName);
                fill(in, m, fields, words, positions);
            }
            Info g = looks.isEmpty() ? info.get(device) : null;
            if (g != null && g.where.equals("file")) {
                g.where = "guess";
                g.java = fromLine(device, sim.robot.shapeLines.get(device), fields, all);
                fill(g, m, fields, words, positions);
                // A guessed servo arm: Java needs its angle at two named positions (pos0/pos1 from the line).
                Map<String, String> line = sim.robot.shapeLines.get(device);
                if (g.java.kind.equals("arm") && line.containsKey("servo") && positions.size() >= 2) {
                    double p0 = num(line, "pos0", 0), p1 = num(line, "pos1", 1);
                    for (Map.Entry<String, Double> e : positions.entrySet()) {
                        if (g.java.angles.size() == 2) break;
                        if (!g.java.angles.isEmpty() && Math.abs(g.angleAt[0] - e.getValue()) < 1e-9) continue;
                        g.java.angles.put(e.getKey(), (double) Math.round(p0 + e.getValue() * (p1 - p0)));
                        if (g.angleAt == null) g.angleAt = new double[] {e.getValue(), 0};
                        else g.angleAt[1] = e.getValue();
                    }
                    if (g.java.angles.size() == 2) g.angleNames.addAll(g.java.angles.keySet());
                    else g.angleAt = null;
                }
            }
        }
        for (Shape s : (List<Shape>) SimShapeJava.get(def, "fixed")) {
            Info in = info.get((String) SimShapeJava.get(s, "name"));
            if (in == null) continue;
            in.where = "fixed";
            in.java = SimShapeJava.of(s, javaName);
            in.partKey = "fixed:" + in.shape;
            in.partWords = "fixed(...)";
        }
    }

    private void fill(Info in, Module<?> m, Map<Object, String> fields, String words, Map<String, Double> positions) {
        in.module = m;
        in.field = fields.get(m);
        in.partKey = "part:" + System.identityHashCode(m);
        in.partWords = words;
        Map<String, String> line = sim.robot.shapeLines.get(in.shape);
        if (line != null && line.containsKey("servo") && line.get("servo").equals(deviceOf(m))) in.servoPositions.putAll(positions);
        if (in.java != null && in.java.kind.equals("arm") && in.java.angles.size() == 2 && !positions.isEmpty()) {
            in.angleNames.addAll(in.java.angles.keySet());
            Double a = positions.get(in.angleNames.get(0)), b = positions.get(in.angleNames.get(1));
            if (a != null && b != null) in.angleAt = new double[] {a, b};
        }
    }

    // A servo part's named positions (Tunables), as the servo turns (a reversed servo's 0.2 is 0.8); empty for others.
    @SuppressWarnings("unchecked")
    private static Map<String, Double> servoPositions(Module<?> m) {
        Map<String, Double> out = new LinkedHashMap<>();
        Object section = SimShapeJava.get(m, "simSection");
        if (section == null || !hasMethod(m, "positionsFrom")) return out;
        try {
            Map<String, Double> p = (Map<String, Double>) call(m, "positionsFrom", section);
            for (Map.Entry<String, Double> e : p.entrySet()) out.put(e.getKey(), (Double) call(m, "physical", e.getValue()));
        } catch (RuntimeException e) {
            // no positions: no pose buttons for it
        }
        return out;
    }

    // A guessed shape line as Java: the numbers of the line, in mm.
    private SimShapeJava fromLine(String name, Map<String, String> line, Map<Object, String> fields, List<Module<?>> all) {
        SimShapes.Shape s = base.shapes.get(name);
        SimShapeJava j = new SimShapeJava(SimShapeJava.KINDS.contains(s.type) ? s.type : "box");
        if (j.kind.equals("box")) j.name = name;
        if (!s.parent.equals("chassis")) {
            j.onShape = s.parent;
            for (Module<?> m : all) if (deviceOf(m).equals(s.parent) && fields.get(m) != null) { j.onPart = fields.get(m); j.onShape = null; }
        }
        j.atMm = new double[] {mm(s.at[0]), mm(s.at[1]), mm(s.at[2])};
        if (j.spins()) {
            double[] dl = diaLen(s.sf, s.sl, s.su, s.axis);
            j.diameterMm = mm(dl[0]);
            j.lengthMm = mm(dl[1]);
        } else {
            j.sizeMm = new double[] {mm(s.sf), mm(s.sl), mm(s.su)};
        }
        if (line.containsKey("axis") && !s.axis.equals(j.kind.equals("slide") ? "up" : "left")) j.axis = s.axis;
        if (s.tilt != 0) j.tiltDeg = Math.round(s.tilt);
        if (j.kind.equals("slide")) j.travelMm = mm(s.max - s.min);
        j.reversed = s.reversed;
        j.soft = s.soft;
        if (line.containsKey("color")) j.color = s.color;
        return j;
    }

    // ----------------------------------------------------------------------------- the page's requests

    /** GET /robot-edit. */
    String json() {
        setUp();
        StringBuilder b = new StringBuilder("{");
        b.append("\"editing\":").append(editing).append(',');
        b.append("\"java\":").append(!robotClass.isEmpty()).append(',');
        field(b, "robotClass", robotClass).append(',');
        field(b, "why", why).append(',');
        field(b, "robotFile", robotFile == null ? "" : robotFile.toString()).append(',');
        b.append("\"tried\":").append(tried).append(',');
        SimRobot r = sim.robot;
        b.append("\"chassis\":[").append(SimShapeJava.num(Math.round(r.lengthIn * 25.4))).append(',')
                .append(SimShapeJava.num(Math.round(r.widthIn * 25.4))).append(',')
                .append(SimShapeJava.num(Math.round(base.chassisHeightIn * 25.4))).append("],");
        b.append("\"shapes\":[");
        int n = 0;
        for (Info in : info.values()) {
            SimShapes.Shape s = base.shapes.get(in.shape);
            if (n++ > 0) b.append(',');
            b.append('{');
            field(b, "name", in.shape).append(',');
            field(b, "type", s.type).append(',');
            field(b, "parent", s.parent).append(',');
            field(b, "where", in.where).append(',');
            field(b, "part", in.partWords).append(',');
            b.append("\"at\":").append(mmVec(s.at)).append(',');
            boolean spins = s.type.equals("roller") || s.type.equals("flywheel");
            b.append("\"spins\":").append(spins).append(',');
            if (spins) {
                double[] dl = diaLen(s.sf, s.sl, s.su, s.axis);
                b.append("\"diameterMm\":").append(Math.round(dl[0] * 25.4)).append(",\"lengthMm\":").append(Math.round(dl[1] * 25.4)).append(',');
                // A catalog part's diameter comes from the catalog (Robot.java cannot make it smaller).
                b.append("\"catalog\":").append(in.java != null && !in.java.parts.isEmpty()).append(',');
            } else {
                b.append("\"size\":").append(mmVec(new double[] {s.sf, s.sl, s.su})).append(',');
            }
            field(b, "axis", s.axis).append(',');
            b.append("\"tilt\":").append(SimShapeJava.num(s.tilt)).append(',');
            b.append("\"moves\":").append(s.moves()).append(',');
            field(b, "unit", s.type.equals("slide") || s.type.equals("claw") ? "in" : "deg").append(',');
            b.append("\"min\":").append(fmt(s.min)).append(",\"max\":").append(fmt(s.max)).append(",\"start\":").append(fmt(s.start)).append(',');
            b.append("\"passive\":").append(s.passive).append(',');
            boolean canTravel = s.type.equals("slide") && (s.passive || s.motor != null);
            b.append("\"travelMm\":").append(canTravel ? Long.toString(Math.round((s.max - s.min) * 25.4)) : "null").append(',');
            b.append("\"servo\":").append(s.servo != null).append(",\"pos0\":").append(fmt(s.pos0)).append(",\"pos1\":").append(fmt(s.pos1)).append(',');
            b.append("\"servoPositions\":").append(numbers(in.servoPositions)).append(',');
            b.append("\"angles\":").append(in.angleAt == null || in.java == null ? "{}" : numbers(in.java.angles)).append(',');
            b.append("\"angleAt\":").append(in.angleAt == null ? "null" : "[" + fmt(in.angleAt[0]) + "," + fmt(in.angleAt[1]) + "]").append(',');
            b.append("\"fallsTo\":").append(s.dropServo != null ? fmt(s.fallsTo) : "null");
            b.append('}');
        }
        return b.append("]}").toString();
    }

    /** POST /robot-edit/mode: the simulator holds still while the mode is open. */
    String mode(boolean on) {
        editing = on;
        sim.paused = on;
        return "{\"ok\":true,\"editing\":" + on + "}";
    }

    boolean editing() {
        return editing;
    }

    /** POST /robot-edit/preview: the changed shapes in a pose, without touching the running simulator. */
    String preview(String body) {
        setUp();
        try {
            Map<String, Object> req = SimJsonReader.object(SimJsonReader.parse(body), "the request");
            Map<String, Map<String, String>> lines = lines(edits(req));
            for (Map<String, String> l : lines.values()) {
                // boxes only: the CAD is not read again for each change
                l.remove("model");
                l.remove("baseModel");
                l.remove("cadPose");
            }
            Map<String, Object> values = req.containsKey("values") ? SimJsonReader.object(req.get("values"), "values") : new LinkedHashMap<>();
            SimShapes shapes = new SimShapes(sim.robot, withoutModel(sim.robot.props), lines, sim.robot.dir);
            synchronized (sim.robot) {
                for (Map.Entry<String, Object> e : values.entrySet()) {
                    SimShapes.Shape s = shapes.shapes.get(e.getKey());
                    if (s == null || !(e.getValue() instanceof Number) || !s.moves()) continue;
                    s.value = Math.max(s.min, Math.min(s.max, ((Number) e.getValue()).doubleValue()));
                }
            }
            List<SimShapes.Piece> pieces = shapes.pieces();
            StringBuilder b = new StringBuilder("{\"pieces\":[");
            for (int i = 0; i < pieces.size(); i++) {
                SimShapes.Piece p = pieces.get(i);
                b.append(i > 0 ? ",{" : "{");
                field(b, "name", p.shape).append(',');
                field(b, "kind", p.kind).append(',');
                field(b, "color", p.color).append(',');
                b.append("\"c\":").append(vec(p.center)).append(",\"ef\":").append(vec(p.ef)).append(",\"el\":").append(vec(p.el))
                        .append(",\"eu\":").append(vec(p.eu)).append(",\"size\":").append(vec(new double[] {p.sf, p.sl, p.su})).append('}');
            }
            b.append("],\"frames\":{");
            int n = 0;
            for (SimShapes.Shape s : shapes.shapes.values()) {
                double[][] f = shapes.parentFrame(s.name);
                SimJson.string(b.append(n++ > 0 ? "," : ""), s.name).append(":[");
                for (int k = 0; k < 9; k++) b.append(k > 0 ? "," : "").append(fmt(f[k / 3][k % 3]));
                b.append(']');
            }
            b.append("},\"values\":{");
            n = 0;
            for (SimShapes.Shape s : shapes.shapes.values()) {
                if (!s.moves()) continue;
                // [now, lowest, highest]: inches along a slide or a claw's gap, degrees for a hinge
                SimJson.string(b.append(n++ > 0 ? "," : ""), s.name).append(":[").append(fmt(s.value)).append(',')
                        .append(fmt(s.min)).append(',').append(fmt(s.max)).append(']');
            }
            b.append("},\"most\":{\"ok\":").append(shapes.mostOk).append(',');
            field(b, "pose", shapes.mostPose).append(',');
            field(b, "size", shapes.most.toString()).append(',');
            b.append("\"poses\":").append(shapes.posesTried).append("}}");
            return b.toString();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e);
        }
    }

    /** POST /robot-edit/java: the Java to paste, for each part with a change (or every part: "all": true). */
    String java(String body) {
        setUp();
        try {
            Map<String, Object> req = SimJsonReader.object(SimJsonReader.parse(body), "the request");
            Map<String, Map<String, Object>> edits = edits(req);
            boolean all = Boolean.TRUE.equals(req.get("all"));
            check(lines(edits));   // the same checks as the preview: a change the simulator cannot draw stops here
            StringBuilder b = new StringBuilder("{\"parts\":[");
            int n = 0;
            for (Map<String, String> part : parts(edits, all)) {
                b.append(n++ > 0 ? ",{" : "{");
                boolean first = true;
                for (Map.Entry<String, String> e : part.entrySet()) {
                    field(b.append(first ? "" : ","), e.getKey(), e.getValue());
                    first = false;
                }
                b.append('}');
            }
            return b.append("]}").toString();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e);
        }
    }

    /** POST /robot-edit/try: the running simulator uses the changed shapes, in memory only. */
    String tryIt(String body) {
        setUp();
        try {
            Map<String, Object> req = SimJsonReader.object(SimJsonReader.parse(body), "the request");
            Map<String, Map<String, Object>> edits = edits(req);
            sim.robot.replaceShapes(lines(edits));
            tried = req.containsKey("edits") ? toJson(req.get("edits")) : "{}";
            return "{\"ok\":true}";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e);
        }
    }

    // ----------------------------------------------------------------------------- edits -> Java

    /**
     * Each part's Java: {name, where, lines, looks, java} (lines: variables to put above the part, or "";
     * looks: the call; java: all of it as one block to copy).
     */
    List<Map<String, String>> parts(Map<String, Map<String, Object>> edits, boolean all) {
        setUp();
        Map<String, List<Info>> byPart = new LinkedHashMap<>();
        for (Info in : info.values()) {
            String key = in.where.equals("file") ? "file:" + in.shape : in.partKey;
            byPart.computeIfAbsent(key, k -> new ArrayList<>()).add(in);
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (List<Info> group : byPart.values()) {
            group.sort((a, b) -> Integer.compare(a.index, b.index));    // as the part's looks(...) has them
            boolean changed = false;
            for (Info in : group) changed |= edits.containsKey(in.shape);
            if (!changed && !all) continue;
            Info first = group.get(0);
            Map<String, String> p = new LinkedHashMap<>();
            List<String> names = new ArrayList<>();
            for (Info in : group) names.add(in.shape);
            p.put("shapes", String.join(",", names));
            if (first.where.equals("file")) {
                Map<String, String> line = lines(edits).get(first.shape);
                String text = "shape." + first.shape + " = " + SimRobot.lineText(line);
                p.put("name", first.shape);
                p.put("where", "In the robot file" + (robotFile == null ? "" : " (" + robotFile.getFileName() + ")")
                        + ": put this in place of the line shape." + first.shape + " = ...");
                p.put("lines", "");
                p.put("looks", text);
                p.put("java", text);
                out.add(p);
                continue;
            }
            List<SimShapeJava> specs = new ArrayList<>();
            for (Info in : group) {
                SimShapeJava j = in.java.copy();
                if (edits.containsKey(in.shape)) j.apply(edits.get(in.shape));
                specs.add(j);
            }
            if (first.where.equals("fixed")) {
                String text = specs.get(0).java(8, 120);
                p.put("name", first.shape);
                p.put("where", "In Robot.java, inside fixed(...): put this in place of "
                        + (specs.get(0).kind.equals("box") ? "Shape.box(\"" + first.shape + "\")..." : "the Shape with .name(\"" + first.shape + "\")") + ".");
                p.put("lines", "");
                p.put("looks", text);
                p.put("java", text);
            } else {
                String device = deviceOf(first.module);
                String[] t = SimShapeJava.looks(specs, device);
                boolean guess = first.where.equals("guess");
                String onLine = guess ? "add " + t[1] + " to it, just before the bracket that closes add(...)"
                        : "use " + t[1] + " in place of its .looks(...)";
                p.put("name", device);
                if (t[0].isEmpty()) {
                    p.put("where", "In Robot.java, on the line that adds " + first.partWords + ": "
                            + (guess ? "add this just before the bracket that closes add(...)." : "put this in place of its .looks(...)."));
                    p.put("java", t[1]);
                } else {
                    p.put("where", "Put these lines in define(), above the line that adds " + first.partWords + "; then, on that line, " + onLine + ".");
                    p.put("java", t[0] + "// then, on the line that adds " + first.partWords + ": " + t[1]);
                }
                p.put("lines", t[0]);
                p.put("looks", t[1]);
            }
            out.add(p);
        }
        return out;
    }

    // ----------------------------------------------------------------------------- edits -> robot file lines

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> edits(Map<String, Object> req) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (!req.containsKey("edits")) return out;
        for (Map.Entry<String, Object> e : SimJsonReader.object(req.get("edits"), "edits").entrySet()) {
            if (!info.containsKey(e.getKey())) throw new IllegalArgumentException("no shape \"" + e.getKey() + "\" in the robot file");
            out.put(e.getKey(), SimJsonReader.object(e.getValue(), e.getKey()));
        }
        return out;
    }

    /** Every shape line with these edits (from the robot file as it was read). */
    Map<String, Map<String, String>> lines(Map<String, Map<String, Object>> edits) {
        setUp();
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : sim.robot.shapeLines.entrySet()) {
            Map<String, String> l = new LinkedHashMap<>(e.getValue());
            Map<String, Object> ed = edits.get(e.getKey());
            if (ed != null) edit(e.getKey(), l, ed);
            out.put(e.getKey(), l);
        }
        return out;
    }

    // The lines must make a robot the simulator can draw (the same checks as reading the file): throws if not.
    private void check(Map<String, Map<String, String>> lines) {
        new SimShapes(sim.robot, withoutModel(sim.robot.props), stripped(lines), sim.robot.dir);
    }

    private void edit(String name, Map<String, String> l, Map<String, Object> e) {
        SimShapes.Shape s = base.shapes.get(name);
        Info in = info.get(name);
        // The checks and rounding of the Java side (whole mm and degrees), on a scratch copy.
        SimShapeJava scratch = in.java != null ? in.java.copy() : new SimShapeJava(SimShapeJava.KINDS.contains(s.type) ? s.type : "box");
        if (scratch.spins() && scratch.sizeMm == null && e.containsKey("sizeMm")) throw new IllegalArgumentException("a " + s.type + " takes lengthMm and diameterMm");
        scratch.apply(e);
        if (e.containsKey("atMm")) l.put("at", in3(scratch.atMm));
        boolean spins = s.type.equals("roller") || s.type.equals("flywheel");
        String axis = e.containsKey("axis") ? scratch.axis : s.axis;
        if (e.containsKey("axis")) l.put("axis", axis);
        if (spins && (e.containsKey("lengthMm") || e.containsKey("diameterMm") || e.containsKey("axis"))) {
            double[] dl = diaLen(s.sf, s.sl, s.su, s.axis);
            double d = e.containsKey("diameterMm") ? scratch.diameterMm / 25.4 : dl[0];
            double len = e.containsKey("lengthMm") ? scratch.lengthMm / 25.4 : dl[1];
            double[] size = axis.equals("up") || axis.equals("down") ? new double[] {d, d, len}
                    : axis.equals("forward") || axis.equals("back") ? new double[] {len, d, d} : new double[] {d, len, d};
            l.put("size", Shape3.n(size[0]) + "x" + Shape3.n(size[1]) + "x" + Shape3.n(size[2]));
        } else if (e.containsKey("sizeMm")) {
            l.put("size", Shape3.n(scratch.sizeMm[0] / 25.4) + "x" + Shape3.n(scratch.sizeMm[1] / 25.4) + "x" + Shape3.n(scratch.sizeMm[2] / 25.4));
        }
        if (e.containsKey("tiltDeg")) {
            if (scratch.tiltDeg == 0) l.remove("tilt");
            else l.put("tilt", Shape3.n(scratch.tiltDeg));
        }
        if (e.containsKey("angles")) {
            if (in.angleAt == null) throw new IllegalArgumentException(name + " has no named angles to change");
            double a = scratch.angles.get(in.angleNames.get(0)), b = scratch.angles.get(in.angleNames.get(1));
            double va = in.angleAt[0], vb = in.angleAt[1];
            double per = (b - a) / (vb - va), pos0 = a - per * va;
            l.put("pos0", Shape3.n(pos0));
            l.put("pos1", Shape3.n(pos0 + per));
        }
        if (e.containsKey("range")) {
            if (!(s.passive && s.type.equals("arm"))) throw new IllegalArgumentException(name + ": only a passive arm has a range to change");
            l.put("minDeg", Shape3.n(scratch.rangeMin));
            l.put("maxDeg", Shape3.n(scratch.rangeMax));
        }
        if (e.containsKey("startAt")) {
            if (!(s.passive && s.type.equals("arm"))) throw new IllegalArgumentException(name + ": only a passive arm has a start to change");
            l.put("start", Shape3.n(scratch.startAt));
        }
        if (e.containsKey("travelMm")) {
            double travel = scratch.travelMm / 25.4;
            if (s.type.equals("slide") && s.passive) {
                l.put("travel", Shape3.n(travel));
            } else if (s.type.equals("slide") && s.motor != null && s.max > s.min) {
                // the same motor counts over a longer slide: fewer counts per inch
                l.remove("spoolIn");
                l.put("ticksPerIn", Shape3.n(s.perUnit * (s.max - s.min) / travel));
            } else {
                throw new IllegalArgumentException(name + ": only a slide has a travel to change");
            }
        }
    }

    // ----------------------------------------------------------------------------- small helpers

    /** The 2-decimal numbers of the robot file (like Shape's own). */
    private static final class Shape3 {
        static String n(double v) {
            String s = String.format(Locale.US, "%.2f", v);
            if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
            return s.equals("-0") ? "0" : s;
        }
    }

    private static String in3(double[] mm) {
        return Shape3.n(mm[0] / 25.4) + "," + Shape3.n(mm[1] / 25.4) + "," + Shape3.n(mm[2] / 25.4);
    }

    // A cylinder's diameter and length (inches) from its box and axis.
    static double[] diaLen(double sf, double sl, double su, String axis) {
        if (axis.equals("up") || axis.equals("down")) return new double[] {Math.max(sf, sl), su};
        if (axis.equals("forward") || axis.equals("back")) return new double[] {Math.max(sl, su), sf};
        return new double[] {Math.max(sf, su), sl};
    }

    private static Map<String, Map<String, String>> stripped(Map<String, Map<String, String>> lines) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : lines.entrySet()) {
            Map<String, String> l = new LinkedHashMap<>(e.getValue());
            l.remove("model");
            l.remove("baseModel");
            l.remove("cadPose");
            out.put(e.getKey(), l);
        }
        return out;
    }

    private static java.util.Properties withoutModel(java.util.Properties p) {
        java.util.Properties q = new java.util.Properties();
        q.putAll(p);
        q.remove("robot.model");
        return q;
    }

    static String deviceOf(Object module) {
        return (String) SimShapeJava.get(module, "device");
    }

    static String firstShapeName(Object module) {
        return (String) call(module, "firstShapeName");
    }

    private static boolean hasMethod(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name)) return true;
        }
        return false;
    }

    // A library method the robot package keeps to itself (shapeName, children...), by name.
    static Object call(Object o, String name, Object... args) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(o, args);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable t = e.getCause();
                    if (t instanceof RuntimeException) throw (RuntimeException) t;
                    throw new IllegalStateException(t);
                }
            }
        }
        throw new IllegalStateException(o.getClass().getSimpleName() + " has no method " + name + " (the library changed: update SimRobotEdit)");
    }

    private static double num(Map<String, String> l, String key, double fallback) {
        try {
            return l.containsKey(key) ? Double.parseDouble(l.get(key).trim()) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double mm(double in) {
        return Math.round(in * 25.4);
    }

    private static String mmVec(double[] in) {
        return "[" + Math.round(in[0] * 25.4) + "," + Math.round(in[1] * 25.4) + "," + Math.round(in[2] * 25.4) + "]";
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "null";
        return String.format(Locale.US, "%.4f", v);
    }

    private static String vec(double[] v) {
        return "[" + fmt(v[0]) + "," + fmt(v[1]) + "," + fmt(v[2]) + "]";
    }

    private static String numbers(Map<String, Double> m) {
        StringBuilder b = new StringBuilder("{");
        for (Map.Entry<String, Double> e : m.entrySet()) {
            SimJson.string(b.append(b.length() > 1 ? "," : ""), e.getKey()).append(':').append(fmt(e.getValue()));
        }
        return b.append('}').toString();
    }

    private static StringBuilder field(StringBuilder b, String name, String value) {
        b.append('"').append(name).append("\":");
        return SimJson.string(b, value);
    }

    private static String error(RuntimeException e) {
        StringBuilder b = new StringBuilder("{\"error\":");
        return SimJson.string(b, String.valueOf(e.getMessage())).append('}').toString();
    }

    // JSON back to text (the edits kept for "tried").
    @SuppressWarnings("unchecked")
    static String toJson(Object o) {
        if (o == null) return "null";
        if (o instanceof String) return SimJson.string(new StringBuilder(), (String) o).toString();
        if (o instanceof Number) return fmt(((Number) o).doubleValue());
        if (o instanceof Boolean) return o.toString();
        StringBuilder b = new StringBuilder();
        if (o instanceof Map) {
            b.append('{');
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                SimJson.string(b.append(b.length() > 1 ? "," : ""), e.getKey()).append(':').append(toJson(e.getValue()));
            }
            return b.append('}').toString();
        }
        b.append('[');
        for (Object x : (List<Object>) o) b.append(b.length() > 1 ? "," : "").append(toJson(x));
        return b.append(']').toString();
    }
}
