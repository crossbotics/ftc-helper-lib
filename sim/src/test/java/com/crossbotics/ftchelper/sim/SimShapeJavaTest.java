package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Shape;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The Java text the Edit robot mode prints (SimShapeJava): read back by the real Shape builder, it gives the
 * same shape, for every kind, with on(...), named angles, dropsWhen and the rest; and the page's changes land
 * in it while everything else stays.
 */
public class SimShapeJavaTest {
    // ---------------------------------------------------------------- Java text -> a real Shape

    /**
     * Builds a Shape from builder text the way javac would run it: Shape.kind(...) then each .call(...), with the
     * real methods (found by name and argument count). refs: the Java names of parts (on(intakeDeploy)).
     */
    static Shape build(String text, Map<String, Object> refs) throws Exception {
        List<String> t = tokens(text);
        int[] i = {0};
        expect(t, i, "Shape");
        expect(t, i, ".");
        String factory = t.get(i[0]++);
        Object shape = invoke(null, factory, args(t, i, refs));
        while (i[0] < t.size()) {
            if (t.get(i[0]).equals(";")) break;
            expect(t, i, ".");
            String m = t.get(i[0]++);
            shape = invoke(shape, m, args(t, i, refs));
        }
        return (Shape) shape;
    }

    private static void expect(List<String> t, int[] i, String what) {
        assertEquals("Java text: expected " + what + " at token " + i[0] + " of " + t, what, t.get(i[0]++));
    }

    private static List<Object> args(List<String> t, int[] i, Map<String, Object> refs) {
        expect(t, i, "(");
        List<Object> out = new ArrayList<>();
        while (!t.get(i[0]).equals(")")) {
            String a = t.get(i[0]++);
            if (a.startsWith("\"")) out.add(a.substring(1, a.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\"));
            else if (a.matches("-?[0-9.]+")) out.add(Double.parseDouble(a));
            else {
                assertTrue("a part named " + a + " in Robot.java", refs.containsKey(a));
                out.add(refs.get(a));
            }
            if (t.get(i[0]).equals(",")) i[0]++;
        }
        i[0]++;
        return out;
    }

    private static Object invoke(Object target, String name, List<Object> args) throws Exception {
        for (Method m : Shape.class.getMethods()) {
            if (!m.getName().equals(name) || (target == null) != java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (m.isVarArgs()) {
                return m.invoke(target, (Object) args.toArray(new String[0]));
            }
            if (p.length != args.size()) continue;
            boolean fits = true;
            Object[] call = new Object[p.length];
            for (int k = 0; k < p.length; k++) {
                Object a = args.get(k);
                if (p[k] == double.class && a instanceof Double) call[k] = a;
                else if (!p[k].isPrimitive() && p[k].isInstance(a)) call[k] = a;
                else fits = false;
            }
            if (fits) return m.invoke(target, call);
        }
        throw new AssertionError("Shape has no " + name + args + ": the Java text would not compile");
    }

    private static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '"') {
                int j = i + 1;
                while (text.charAt(j) != '"') j += text.charAt(j) == '\\' ? 2 : 1;
                out.add(text.substring(i, j + 1));
                i = j + 1;
            } else if (Character.isDigit(c) || (c == '-' && Character.isDigit(text.charAt(i + 1)))) {
                int j = i + 1;
                while (j < text.length() && (Character.isDigit(text.charAt(j)) || text.charAt(j) == '.')) j++;
                out.add(text.substring(i, j));
                i = j;
            } else if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < text.length() && Character.isJavaIdentifierPart(text.charAt(j))) j++;
                out.add(text.substring(i, j));
                i = j;
            } else {
                out.add(String.valueOf(c));
                i++;
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- the robots in the examples

    // Each part's field name -> the part, and the reverse.
    private static Map<String, Object> refs(RobotDefinition def) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Field f : def.getClass().getDeclaredFields()) {
            if (!Module.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            if (f.get(def) != null) out.put(f.getName(), f.get(def));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void roundTripEveryShape(RobotDefinition def, int atLeast) throws Exception {
        def.simLines();
        Map<String, Object> refs = refs(def);
        Map<Object, String> names = new IdentityHashMap<>();
        for (Map.Entry<String, Object> e : refs.entrySet()) names.put(e.getValue(), e.getKey());
        int n = 0;
        for (Module<?> m : def.modules()) {
            for (Shape s : (List<Shape>) SimShapeJava.get(m, "looks")) {
                String text = SimShapeJava.of(s, names::get).java();
                Shape back = build(text, refs);
                assertEquals("read back by the real builder: the same shape", text, SimShapeJava.of(back, names::get).java());
                n++;
            }
        }
        assertTrue("shapes tried: " + n, n >= atLeast);
    }

    @Test public void theFlywheelBotsShapesComeBackTheSame() throws Exception {
        RobotDefinition def = new com.crossbotics.ftchelper.examples.flywheelbot.Robot();
        roundTripEveryShape(def, 4);
        // and the text is what Robot.java has (its numbers in mm, its names, its part ids)
        Map<Object, String> names = new IdentityHashMap<>();
        for (Map.Entry<String, Object> e : refs(def).entrySet()) names.put(e.getValue(), e.getKey());
        List<String> all = new ArrayList<>();
        for (Module<?> m : def.modules()) for (Object s : (List<?>) SimShapeJava.get(m, "looks")) all.add(SimShapeJava.of((Shape) s, names::get).java());
        assertTrue(all.toString(), all.contains("Shape.arm().name(\"kicker\").atMm(150, 90, 100).sizeMm(60, 20, 15).angle(\"stowed\", 90).angle(\"deployed\", 20)"));
        assertTrue(all.toString(), all.contains("Shape.arm().name(\"intake_arm\").atMm(150, 0, 100).sizeMm(110, 200, 20).range(-15, 90).startAt(90)"
                + ".dropsWhen(intakeDeploy, \"deployed\", -15)"));
        assertTrue(all.toString(), all.contains("Shape.roller(\"gobilda-gecko-48-rex\").on(\"intake_arm\").atMm(110, 0, 0).lengthMm(300)"));
        assertTrue(all.toString(), all.contains("Shape.flywheel(\"gobilda-hogback-96\").atMm(-40, 0, 170)"));
    }

    @Test public void theV2BotsSlideArmAndClawComeBackTheSame() throws Exception {
        roundTripEveryShape(new com.crossbotics.ftchelper.examples.v2bot.Robot(), 3);
    }

    // Every setting there is, on shapes built here: box, roller (no catalog), flywheel, passive slide, claw, turret.
    @Test public void everySettingComesBackTheSame() throws Exception {
        Shape[] shapes = {
            Shape.box("ramp").atMm(-50, 10, 120).sizeMm(80, 300, 6).tiltDeg(-30).color("#2fa84f").note("measured 2026-10-20, \"ramp\""),
            Shape.roller().name("spinner").on("ramp").atMm(0, 120, 0).diameterMm(48).lengthMm(30).axis("up").reversed().soft(),
            Shape.flywheel().atMm(-40, 0, 170).diameterMm(96).lengthMm(38).axis("forward"),
            Shape.slide().name("tower").atMm(-100, -150, 12).sizeMm(30, 30, 300).axis("forward").passive().travelMm(200),
            Shape.claw().atMm(115, 0, 0).sizeMm(60, 120, 50),
            Shape.arm().atMm(1.5, -2.25, 0).sizeMm(10, 10, 10).passive().range(-40, 95),
        };
        for (Shape s : shapes) {
            String text = SimShapeJava.of(s, x -> null).java();
            assertEquals(text, SimShapeJava.of(build(text, new LinkedHashMap<>()), x -> null).java());
        }
        assertEquals("Shape.box(\"ramp\").atMm(-50, 10, 120).sizeMm(80, 300, 6).tiltDeg(-30).note(\"measured 2026-10-20, \\\"ramp\\\"\").color(\"#2fa84f\")",
                SimShapeJava.of(shapes[0], x -> null).java());
        assertEquals("not whole mm: two decimals at most", "Shape.arm().atMm(1.5, -2.25, 0).sizeMm(10, 10, 10).passive().range(-40, 95)",
                SimShapeJava.of(shapes[5], x -> null).java());
    }

    // The page's changes: rounded to whole mm and degrees; the rest of the shape stays as Robot.java has it.
    @Test public void changesLandAndTheRestStays() throws Exception {
        RobotDefinition def = new com.crossbotics.ftchelper.examples.flywheelbot.Robot();
        def.simLines();
        Map<String, Object> refs = refs(def);
        Map<Object, String> names = new IdentityHashMap<>();
        for (Map.Entry<String, Object> e : refs.entrySet()) names.put(e.getValue(), e.getKey());
        Module<?> intake = (Module<?>) refs.get("intake");
        Shape arm = (Shape) ((List<?>) SimShapeJava.get(intake, "looks")).get(0);
        SimShapeJava j = SimShapeJava.of(arm, names::get);
        j.apply(SimJsonReader.object(SimJsonReader.parse("{\"atMm\":[160.4,0,99.6],\"sizeMm\":[120,200,20],\"range\":[95,-40],\"startAt\":95}"), "e"));
        String text = j.java();
        assertEquals("Shape.arm().name(\"intake_arm\").atMm(160, 0, 100).sizeMm(120, 200, 20).range(-40, 95).startAt(95)"
                + ".dropsWhen(intakeDeploy, \"deployed\", -15)", text);
        Shape back = build(text, refs);
        assertEquals(text, SimShapeJava.of(back, names::get).java());
        assertEquals("at: inches inside", 160 / 25.4, ((double[]) SimShapeJava.get(back, "at"))[0], 1e-9);

        Module<?> deploy = (Module<?>) refs.get("intakeDeploy");
        SimShapeJava kicker = SimShapeJava.of((Shape) ((List<?>) SimShapeJava.get(deploy, "looks")).get(0), names::get);
        kicker.apply(SimJsonReader.object(SimJsonReader.parse("{\"angles\":{\"deployed\":15},\"tiltDeg\":5,\"axis\":\"right\"}"), "e"));
        assertEquals("Shape.arm().name(\"kicker\").atMm(150, 90, 100).sizeMm(60, 20, 15).axis(\"right\").tiltDeg(5).angle(\"stowed\", 90)"
                + ".angle(\"deployed\", 15)", kicker.java());
        try {
            kicker.apply(SimJsonReader.object(SimJsonReader.parse("{\"angles\":{\"up\":15}}"), "e"));
            org.junit.Assert.fail("a name the shape does not have");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("up"));
        }
    }

    // One short shape stays inline; two or more become named variables (camelCase from name(), or kind + part).
    @Test public void oneShapeInlineSeveralAsVariables() throws Exception {
        SimShapeJava fly = SimShapeJava.of(Shape.flywheel("gobilda-hogback-96").atMm(-40, 0, 200), x -> null);
        String[] one = SimShapeJava.looks(java.util.Collections.singletonList(fly), "launcher");
        assertEquals("", one[0]);
        assertEquals(".looks(Shape.flywheel(\"gobilda-hogback-96\").atMm(-40, 0, 200))", one[1]);

        SimShapeJava arm = SimShapeJava.of(Shape.arm().name("intake_arm").atMm(120, 0, 150).sizeMm(95, 240, 15).passive().range(-40, 95).startAt(95), x -> null);
        SimShapeJava r1 = SimShapeJava.of(Shape.roller("offset-tubing-90", "offset-tubing-60").on("intake_arm").atMm(88, 0, 0).lengthMm(240).soft(), x -> null);
        SimShapeJava r2 = SimShapeJava.of(Shape.roller("offset-tubing-60").on("intake_arm").atMm(40, 0, 0).lengthMm(240), x -> null);
        String[] three = SimShapeJava.looks(java.util.Arrays.asList(arm, r1, r2), "intake");
        assertEquals(".looks(intakeArm, roller, intakeRoller)", three[1]);
        String[] lines = three[0].split("\n");
        assertTrue(three[0], lines[0].startsWith("Shape intakeArm = Shape.arm().name(\"intake_arm\")"));
        for (String l : lines) {
            assertTrue("at most 120 characters: " + l, l.length() <= 120);
            assertTrue(l, l.startsWith("Shape ") || l.startsWith("        ."));
        }
        // each statement, read back by the real builder
        for (String statement : three[0].split(";\n")) {
            if (statement.trim().isEmpty()) continue;
            String text = statement.substring(statement.indexOf('=') + 1).trim();
            assertNotNull(build(text, new LinkedHashMap<>()));
        }
        assertFalse(SimShapeJava.camel("intake_arm").contains("_"));
        assertEquals("intakeArm", SimShapeJava.camel("intake_arm"));
        assertEquals("shape2nd", SimShapeJava.camel("2nd"));
    }
}
