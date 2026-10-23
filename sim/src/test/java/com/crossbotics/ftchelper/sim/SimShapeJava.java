package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.robot.Shape;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * One part's shape the way Robot.java writes it ({@code Shape.arm().atMm(120, 0, 150)...}), for the
 * page's Edit robot mode: read from a {@link Shape} built in Robot.java, changed by the page, and
 * written back as the Java to paste. Units are Java's: mm and degrees.
 *
 * <p>Reading a Shape uses its fields by name (the library keeps them private: students only build
 * shapes). SimShapeJavaTest builds every kind and checks the text against the real builder, so a
 * renamed field is caught there.
 */
final class SimShapeJava {
    static final double MM = 25.4;
    /** The kinds the simulator draws. Shape.java has no turret() yet: the text names it anyway (see the docs). */
    static final List<String> KINDS = java.util.Arrays.asList("arm", "slide", "claw", "box", "roller", "flywheel", "turret");

    String kind;
    final List<String> parts = new ArrayList<>();
    String name;                                   // .name(...); a box's Shape.box(name)
    String onPart, onShape;                        // .on(javaField) or .on("shape name")
    double[] atMm = {0, 0, 0};
    double[] sizeMm;                               // null: not given (a roller or flywheel from the catalog)
    double lengthMm = Double.NaN, diameterMm = Double.NaN;
    String axis;                                   // null: the kind's default
    double tiltDeg = 0;
    final Map<String, Double> angles = new LinkedHashMap<>();
    boolean passive;
    double rangeMin = Double.NaN, rangeMax = Double.NaN, startAt = Double.NaN, travelMm = Double.NaN;
    String dropBy, dropPosition;                   // dropsWhen(javaField, "position", fallsTo)
    double fallsTo = Double.NaN;
    boolean soft, reversed;
    String note, color;

    SimShapeJava(String kind) {
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("no shape kind " + kind);
        this.kind = kind;
    }

    boolean spins() {
        return kind.equals("roller") || kind.equals("flywheel");
    }

    SimShapeJava copy() {
        SimShapeJava c = new SimShapeJava(kind);
        c.parts.addAll(parts);
        c.name = name;
        c.onPart = onPart;
        c.onShape = onShape;
        c.atMm = atMm.clone();
        c.sizeMm = sizeMm == null ? null : sizeMm.clone();
        c.lengthMm = lengthMm;
        c.diameterMm = diameterMm;
        c.axis = axis;
        c.tiltDeg = tiltDeg;
        c.angles.putAll(angles);
        c.passive = passive;
        c.rangeMin = rangeMin;
        c.rangeMax = rangeMax;
        c.startAt = startAt;
        c.travelMm = travelMm;
        c.dropBy = dropBy;
        c.dropPosition = dropPosition;
        c.fallsTo = fallsTo;
        c.soft = soft;
        c.reversed = reversed;
        c.note = note;
        c.color = color;
        return c;
    }

    // ----------------------------------------------------------------------------- the Java text

    /** The builder calls, e.g. {@code Shape.arm().name("kicker").atMm(150, 90, 100).sizeMm(60, 20, 15)}. */
    String java() {
        List<String> calls = calls();
        StringBuilder b = new StringBuilder();
        for (String c : calls) b.append(c);
        return b.toString();
    }

    /** The same, cut into lines of at most {@code width} characters: the next lines start with 8 spaces. */
    String java(int firstIndent, int width) {
        StringBuilder b = new StringBuilder();
        int col = firstIndent;
        for (String c : calls()) {
            if (b.length() > 0 && col + c.length() > width && c.startsWith(".")) {
                b.append("\n        ");
                col = 8;
            }
            b.append(c);
            col += c.length();
        }
        return b.toString();
    }

    // "Shape.arm()", ".name(\"x\")", ".atMm(1, 2, 3)" ...
    private List<String> calls() {
        List<String> c = new ArrayList<>();
        switch (kind) {
            case "box": c.add("Shape.box(" + str(name == null ? "box" : name) + ")"); break;
            case "roller": c.add("Shape.roller(" + strs(parts) + ")"); break;
            case "flywheel": c.add("Shape.flywheel(" + strs(parts) + ")"); break;
            default: c.add("Shape." + kind + "()");
        }
        if (name != null && !kind.equals("box")) c.add(".name(" + str(name) + ")");
        if (onPart != null) c.add(".on(" + onPart + ")");
        else if (onShape != null) c.add(".on(" + str(onShape) + ")");
        c.add(".atMm(" + num(atMm[0]) + ", " + num(atMm[1]) + ", " + num(atMm[2]) + ")");
        if (sizeMm != null) c.add(".sizeMm(" + num(sizeMm[0]) + ", " + num(sizeMm[1]) + ", " + num(sizeMm[2]) + ")");
        if (!Double.isNaN(lengthMm)) c.add(".lengthMm(" + num(lengthMm) + ")");
        if (!Double.isNaN(diameterMm)) c.add(".diameterMm(" + num(diameterMm) + ")");
        if (axis != null) c.add(".axis(" + str(axis) + ")");
        if (tiltDeg != 0) c.add(".tiltDeg(" + num(tiltDeg) + ")");
        for (Map.Entry<String, Double> a : angles.entrySet()) c.add(".angle(" + str(a.getKey()) + ", " + num(a.getValue()) + ")");
        if (passive && dropBy == null) c.add(".passive()");
        if (!Double.isNaN(rangeMin)) c.add(".range(" + num(rangeMin) + ", " + num(rangeMax) + ")");
        if (!Double.isNaN(startAt)) c.add(".startAt(" + num(startAt) + ")");
        if (!Double.isNaN(travelMm)) c.add(".travelMm(" + num(travelMm) + ")");
        if (dropBy != null) c.add(".dropsWhen(" + dropBy + ", " + str(dropPosition) + ", " + num(fallsTo) + ")");
        if (soft) c.add(".soft()");
        if (reversed) c.add(".reversed()");
        if (note != null) c.add(".note(" + str(note) + ")");
        if (color != null) c.add(".color(" + str(color) + ")");
        return c;
    }

    /** A number as Java text: whole numbers without ".0", otherwise at most 2 decimals. */
    static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            long r = Math.round(v);
            return Long.toString(r);
        }
        String s = String.format(Locale.US, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    static String str(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String strs(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String s : list) b.append(b.length() == 0 ? "" : ", ").append(str(s));
        return b.toString();
    }

    /** camelCase from a robot file name: intake_arm -> intakeArm; a name that is not Java-safe gets "shape". */
    static String camel(String name) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (char ch : name.toCharArray()) {
            if (!Character.isLetterOrDigit(ch)) {
                up = b.length() > 0;
                continue;
            }
            b.append(up ? Character.toUpperCase(ch) : b.length() == 0 ? Character.toLowerCase(ch) : ch);
            up = false;
        }
        if (b.length() == 0 || !Character.isJavaIdentifierStart(b.charAt(0))) b.insert(0, "shape");
        return b.toString();
    }

    // ----------------------------------------------------------------------------- changes from the page

    /**
     * The page's changes to this shape (JSON numbers, mm and degrees, rounded to whole numbers):
     * atMm, sizeMm, lengthMm, diameterMm, axis, tiltDeg, angles {name: deg}, range [min, max], startAt, travelMm.
     */
    void apply(Map<String, Object> e) {
        if (e.containsKey("atMm")) atMm = triple(e.get("atMm"), "atMm");
        if (e.containsKey("sizeMm")) {
            if (spins()) throw new IllegalArgumentException("a " + kind + " takes lengthMm and diameterMm, not sizeMm");
            sizeMm = triple(e.get("sizeMm"), "sizeMm");
            for (double v : sizeMm) if (v < 1) throw new IllegalArgumentException("each size must be at least 1 mm");
        }
        if (e.containsKey("lengthMm")) lengthMm = atLeast1(whole(e.get("lengthMm"), "lengthMm"), "lengthMm");
        if (e.containsKey("diameterMm")) diameterMm = atLeast1(whole(e.get("diameterMm"), "diameterMm"), "diameterMm");
        if (e.containsKey("axis")) {
            String a = String.valueOf(e.get("axis")).toLowerCase(Locale.US);
            if (!java.util.Arrays.asList("up", "down", "forward", "back", "left", "right").contains(a)) {
                throw new IllegalArgumentException("axis: up, down, forward, back, left or right");
            }
            axis = a;
        }
        if (e.containsKey("tiltDeg")) tiltDeg = whole(e.get("tiltDeg"), "tiltDeg");
        if (e.containsKey("angles")) {
            for (Map.Entry<String, Object> a : SimJsonReader.object(e.get("angles"), "angles").entrySet()) {
                if (!angles.containsKey(a.getKey())) throw new IllegalArgumentException("no angle \"" + a.getKey() + "\" on this shape");
                angles.put(a.getKey(), whole(a.getValue(), "angle " + a.getKey()));
            }
        }
        if (e.containsKey("range")) {
            double[] r = pair(e.get("range"), "range");
            rangeMin = Math.min(r[0], r[1]);
            rangeMax = Math.max(r[0], r[1]);
        }
        if (e.containsKey("startAt")) startAt = whole(e.get("startAt"), "startAt");
        if (e.containsKey("travelMm")) travelMm = atLeast1(whole(e.get("travelMm"), "travelMm"), "travelMm");
    }

    private static double atLeast1(double v, String what) {
        if (v < 1) throw new IllegalArgumentException(what + " must be at least 1");
        return v;
    }

    static double whole(Object o, String what) {
        if (!(o instanceof Number)) throw new IllegalArgumentException(what + ": a number");
        double v = ((Number) o).doubleValue();
        if (Double.isNaN(v) || Double.isInfinite(v) || Math.abs(v) > 100000) throw new IllegalArgumentException(what + ": a number");
        return Math.rint(v);
    }

    private static double[] triple(Object o, String what) {
        List<Object> l = SimJsonReader.array(o, what);
        if (l.size() != 3) throw new IllegalArgumentException(what + ": 3 numbers");
        return new double[] {whole(l.get(0), what), whole(l.get(1), what), whole(l.get(2), what)};
    }

    private static double[] pair(Object o, String what) {
        List<Object> l = SimJsonReader.array(o, what);
        if (l.size() != 2) throw new IllegalArgumentException(what + ": 2 numbers");
        return new double[] {whole(l.get(0), what), whole(l.get(1), what)};
    }

    // ----------------------------------------------------------------------------- from a Shape in Robot.java

    /**
     * Reads a Shape as Robot.java built it.
     *
     * @param javaName the Java field that holds a part (for on(part) and dropsWhen(part, ...)), or null
     *                 when the part is not in a field: on(...) then names its shape, dropsWhen its camelCase name
     */
    static SimShapeJava of(Shape s, Function<Object, String> javaName) {
        SimShapeJava j = new SimShapeJava((String) get(s, "type"));
        @SuppressWarnings("unchecked") List<String> parts = (List<String>) get(s, "parts");
        j.parts.addAll(parts);
        j.name = (String) get(s, "name");
        double[] at = (double[]) get(s, "at");
        j.atMm = mm(at);
        double[] size = (double[]) get(s, "size");
        j.sizeMm = size == null ? null : mm(size);
        j.lengthMm = mm((double) get(s, "lengthIn"));
        j.diameterMm = mm((double) get(s, "diameterIn"));
        j.axis = (String) get(s, "axis");
        j.tiltDeg = (double) get(s, "tiltDeg");
        @SuppressWarnings("unchecked") Map<String, Double> angles = (Map<String, Double>) get(s, "angles");
        j.angles.putAll(angles);
        j.passive = (boolean) get(s, "passive");
        j.rangeMin = (double) get(s, "rangeMin");
        j.rangeMax = (double) get(s, "rangeMax");
        j.startAt = (double) get(s, "startAt");
        j.travelMm = mm((double) get(s, "travelIn"));
        j.soft = (boolean) get(s, "soft");
        j.reversed = (boolean) get(s, "reversed");
        j.note = (String) get(s, "note");
        j.color = (String) get(s, "color");
        Object on = get(s, "onModule");
        if (on != null) {
            j.onPart = javaName.apply(on);
            if (j.onPart == null) j.onShape = SimRobotEdit.firstShapeName(on);
        } else {
            j.onShape = (String) get(s, "onShape");
        }
        Object drop = get(s, "dropBy");
        if (drop != null) {
            String n = javaName.apply(drop);
            j.dropBy = n != null ? n : camel(SimRobotEdit.deviceOf(drop));
            j.dropPosition = (String) get(s, "dropPosition");
            j.fallsTo = (double) get(s, "fallsTo");
        }
        return j;
    }

    // inches -> mm, without the float noise (7.40157... in -> 188 mm, not 187.99999999999997)
    private static double[] mm(double[] in) {
        return new double[] {mm(in[0]), mm(in[1]), mm(in[2])};
    }

    private static double mm(double in) {
        return Double.isNaN(in) ? in : Math.round(in * MM * 1e6) / 1e6;
    }

    static Object get(Object o, String field) {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException(o.getClass().getSimpleName() + " has no field " + field + " (the library changed: update SimShapeJava)");
    }

    // ----------------------------------------------------------------------------- one part's text

    /**
     * The Java for one part's looks: one short shape inline ({@code .looks(Shape...)}); two or more
     * (or one too long for a 120 character line) as named variables, one per statement, then
     * {@code .looks(a, b)}. [0] = the lines to put above the part (or ""), [1] = the looks call.
     *
     * @param partWords e.g. "intake", to name a variable for a shape without a name (intakeRoller)
     */
    static String[] looks(List<SimShapeJava> shapes, String partWords) {
        if (shapes.size() == 1) {
            String inline = ".looks(" + shapes.get(0).java() + ")";
            if (inline.length() + 8 <= 120) return new String[] {"", inline};
        }
        Set<String> used = new LinkedHashSet<>();
        StringBuilder decl = new StringBuilder();
        for (SimShapeJava s : shapes) {
            String v = s.name != null ? camel(s.name) : s.kind;
            if (used.contains(v) && s.name == null && partWords != null) v = camel(partWords + "_" + s.kind);
            String base = v;
            for (int k = 2; used.contains(v); k++) v = base + k;
            used.add(v);
            String head = "Shape " + v + " = ";
            decl.append(head).append(s.java(head.length(), 119)).append(";\n");   // 119: room for the ";"
        }
        return new String[] {decl.toString(), ".looks(" + String.join(", ", used) + ")"};
    }
}
