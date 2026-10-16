package io.github.jasoncross.ftchelper.sim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs a scenario file: a short script that drives the simulator like a person would (INIT, START,
 * buttons, sticks, pushes) and checks what happens ("expect ..."). No browser needed, so a student, a
 * test or Claude can run it and read the report. The language is in docs/guides/simulator.md.
 *
 * <pre>
 * init "Motor Finder"
 * start
 * press g1.dpad_up
 * press g1.a
 * hold g1.right_stick_y -1
 * expect within 2 motor lift ticks > 100
 * press g1.guide
 * expect screen has "help"
 * expect motor lift power = 0
 * </pre>
 *
 * Command line: SimScenario --robot robot.properties file.sim [file2.sim ...]  (exit code 1 on a failure)
 */
public final class SimScenario {
    private final SimEngine sim;
    private final List<String> report = new ArrayList<>();
    private int failures = 0;
    private final Map<String, String> pad1 = new HashMap<>(), pad2 = new HashMap<>();

    SimScenario(SimEngine sim) {
        this.sim = sim;
    }

    public static void main(String[] args) throws Exception {
        Path robot = Paths.get("sim/robots/starter.properties");
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--robot") && i + 1 < args.length) robot = Paths.get(args[++i]);
            else files.add(Paths.get(args[i]));
        }
        if (files.isEmpty()) {
            System.out.println("No scenario file. Use: ./gradlew :sim:scenario -Pfile=path/to/test.sim");
            System.exit(2);
        }
        int failed = 0;
        for (Path f : files) {
            SimEngine sim = new SimEngine(SimRobot.load(robot));
            sim.start();
            SimScenario s = new SimScenario(sim);
            try {
                s.run(f);
            } finally {
                sim.shutdown();
            }
            System.out.println("== " + f + " (robot file " + robot + ")");
            for (String line : s.report) System.out.println(line);
            System.out.println(s.failures == 0 ? "PASSED" : "FAILED: " + s.failures + " check(s)");
            System.out.println();
            if (s.failures > 0) failed++;
        }
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Runs every line of the file. */
    void run(Path file) throws IOException, InterruptedException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                step(n + 1, line);
            } catch (RuntimeException e) {
                fail(n + 1, line, e.getMessage());
            }
        }
        sim.ds.stop();
    }

    int failures() {
        return failures;
    }

    List<String> report() {
        return report;
    }

    private void ok(int n, String line, String why) {
        String all = why.isEmpty() ? note : (note.isEmpty() ? why : why + "; " + note);
        note = "";
        report.add(String.format(Locale.US, "  ok    %3d  %s%s", n, line, all.isEmpty() ? "" : "   (" + all + ")"));
    }

    private void fail(int n, String line, String why) {
        note = "";
        failures++;
        report.add(String.format(Locale.US, "  FAIL  %3d  %s   -> %s", n, line, why));
    }

    private void step(int n, String line) throws InterruptedException {
        List<String> t = tokens(line);
        String cmd = t.get(0).toLowerCase(Locale.US);
        switch (cmd) {
            case "init": {
                int before = sim.ds.activity();
                sim.ds.init(t.get(1));
                if (!SimRun.waitFor(() -> sim.ds.activity() > before || !sim.ds.error().isEmpty()
                        || sim.ds.phase() == SimDriverStation.Phase.STOPPED, 3)) {
                    note = "waited 3 s: the OpMode shows nothing in INIT";
                }
                ok(n, line, "");
                return;
            }
            case "start": {
                sim.ds.start();
                loops(2);
                ok(n, line, "");
                return;
            }
            case "stop":
                sim.ds.stop();
                ok(n, line, "");
                return;
            case "wait":
                SimRun.pause(Double.parseDouble(t.get(1)));
                ok(n, line, "");
                return;
            case "loops":
                loops(Integer.parseInt(t.get(1)));
                ok(n, line, "");
                return;
            case "press": {
                setInput(t.get(1), "1");
                loops(2);
                setInput(t.get(1), "0");
                loops(2);
                ok(n, line, "");
                return;
            }
            case "hold":
                setInput(t.get(1), t.size() > 2 ? t.get(2) : "1");
                loops(2);
                ok(n, line, "");
                return;
            case "release":
                if (t.get(1).equals("all")) {
                    pad1.clear();
                    pad2.clear();
                    sim.ds.pad1.apply(pad1, false);
                    sim.ds.pad2.apply(pad2, false);
                } else if (t.get(1).endsWith(".all")) {
                    Map<String, String> p = t.get(1).startsWith("g2") ? pad2 : pad1;
                    p.clear();
                    (p == pad2 ? sim.ds.pad2 : sim.ds.pad1).apply(p, false);
                } else {
                    setInput(t.get(1), "0");
                }
                loops(2);
                ok(n, line, "");
                return;
            case "push": {
                // push <forward in/s> <right in/s> <seconds>
                sim.robot.push(Double.parseDouble(t.get(1)), Double.parseDouble(t.get(2)), Double.parseDouble(t.get(3)));
                SimRun.waitFor(() -> !sim.robot.pushing(), 15);
                ok(n, line, "");
                return;
            }
            case "set":
                switch (t.get(1)) {
                    case "battery": sim.robot.setBattery(Double.parseDouble(t.get(2))); break;
                    // set touch lift_bottom on|off
                    case "touch": sim.robot.setTouch(t.get(2), onOff(t.get(3))); break;
                    // set pose X Y HEADING
                    case "pose":
                        sim.robot.setPose(Double.parseDouble(t.get(2)), Double.parseDouble(t.get(3)), Double.parseDouble(t.get(4)));
                        break;
                    // set autotimer on|off (the 30 s autonomous stop)
                    case "autotimer": sim.ds.autoTimer = onOff(t.get(2)); break;
                    default: throw new IllegalArgumentException("set battery | touch NAME on|off | pose X Y HEADING | autotimer on|off");
                }
                ok(n, line, "");
                return;
            case "print":
                print(t.get(1));
                return;
            case "expect":
                expect(n, line, t.subList(1, t.size()));
                return;
            default:
                throw new IllegalArgumentException("unknown command \"" + cmd + "\"");
        }
    }

    // Notes for the current line (e.g. a wait that ran out of time).
    private String note = "";

    /**
     * Waits until the OpMode has run this many more loops (screen updates or iterative loops), at
     * most 3 s. An OpMode that never sends telemetry cannot be counted: then it notes that it waited.
     */
    private static boolean onOff(String v) {
        if (v.equals("on")) return true;
        if (v.equals("off")) return false;
        throw new IllegalArgumentException("on or off");
    }

    private void loops(int count) throws InterruptedException {
        if (sim.ds.phase() != SimDriverStation.Phase.INIT && sim.ds.phase() != SimDriverStation.Phase.RUNNING) return;
        int start = sim.ds.activity();
        if (!SimRun.waitFor(() -> sim.ds.activity() >= start + count, 3)) {
            note = "waited 3 s: the OpMode sends no telemetry, so its loops cannot be counted";
        }
    }

    // g1.a / g2.right_stick_y / g1.lt ... (SDK names and the page's short names both work)
    private void setInput(String what, String value) {
        String[] parts = what.split("\\.");
        if (parts.length != 2 || !(parts[0].equals("g1") || parts[0].equals("g2"))) {
            throw new IllegalArgumentException("a gamepad input looks like g1.a or g2.left_stick_y");
        }
        String key = ALIASES.getOrDefault(parts[1], parts[1]);
        if (!KEYS.contains(key)) throw new IllegalArgumentException("unknown gamepad input \"" + parts[1] + "\"");
        Map<String, String> p = parts[0].equals("g2") ? pad2 : pad1;
        p.put(key, value);
        (p == pad2 ? sim.ds.pad2 : sim.ds.pad1).apply(new HashMap<>(p), false);
    }

    private static final List<String> KEYS = java.util.Arrays.asList("a", "b", "x", "y", "lb", "rb", "up", "down", "left",
            "right", "back", "start", "guide", "ls", "rs", "lx", "ly", "rx", "ry", "lt", "rt");
    private static final Map<String, String> ALIASES = new HashMap<>();
    static {
        ALIASES.put("left_bumper", "lb");
        ALIASES.put("right_bumper", "rb");
        ALIASES.put("dpad_up", "up");
        ALIASES.put("dpad_down", "down");
        ALIASES.put("dpad_left", "left");
        ALIASES.put("dpad_right", "right");
        ALIASES.put("left_stick_button", "ls");
        ALIASES.put("right_stick_button", "rs");
        ALIASES.put("left_stick_x", "lx");
        ALIASES.put("left_stick_y", "ly");
        ALIASES.put("right_stick_x", "rx");
        ALIASES.put("right_stick_y", "ry");
        ALIASES.put("left_trigger", "lt");
        ALIASES.put("right_trigger", "rt");
    }

    // expect [within S] screen has "x" | screen not "x" | error none | error has "x" | phase P
    //        | motor NAME FIELD OP VALUE | servo NAME FIELD OP VALUE | robot FIELD OP VALUE
    private void expect(int n, String line, List<String> t) throws InterruptedException {
        double within = 0;
        if (t.get(0).equals("within")) {
            within = Double.parseDouble(t.get(1));
            t = t.subList(2, t.size());
        }
        final List<String> check = t;
        validate(check);
        String[] why = {""};
        boolean pass = SimRun.waitFor(() -> holds(check, why), within);
        if (pass) ok(n, line, why[0]);
        else fail(n, line, why[0]);
    }

    // Checks the words of an expect line before running it, so a typo fails instead of passing.
    private static void validate(List<String> t) {
        String what = t.isEmpty() ? "" : t.get(0);
        switch (what) {
            case "screen":
                need(t, 3, "expect screen has \"text\"  (or: screen not \"text\")");
                if (!t.get(1).equals("has") && !t.get(1).equals("not")) throw new IllegalArgumentException("screen has / screen not");
                return;
            case "error":
                if (t.size() == 2 && t.get(1).equals("none")) return;
                need(t, 3, "expect error none  (or: error has \"text\")");
                if (!t.get(1).equals("has")) throw new IllegalArgumentException("error none / error has \"text\"");
                return;
            case "phase":
                need(t, 2, "expect phase IDLE|INIT|RUNNING|STOPPED");
                SimDriverStation.Phase.valueOf(t.get(1).toUpperCase(Locale.US));
                return;
            case "bumped":
                need(t, 2, "expect bumped none  (or: bumped \"name\")");
                return;
            case "value":
                need(t, 4, "expect value \"Lift\" > 500   (the number on the screen line that starts with Lift)");
                op(t, 2);
                return;
            case "time":
                need(t, 3, "expect time < 25   (seconds since START)");
                op(t, 1);
                return;
            case "size":
                need(t, 3, "expect size start|match|most ok|fail");
                if (!java.util.Arrays.asList("start", "match", "most").contains(t.get(1))) {
                    throw new IllegalArgumentException("size start (18 in cube now), size match (18 x 24 x 29 now) or size most (at full reach)");
                }
                if (!t.get(2).equals("ok") && !t.get(2).equals("fail")) throw new IllegalArgumentException("ok or fail");
                return;
            case "camera":
                need(t, 4, "expect camera \"Webcam 1\" sees 31|none|\"RED SCORING\"");
                if (!t.get(2).equals("sees")) throw new IllegalArgumentException("expect camera NAME sees ...");
                return;
            case "touch":
                need(t, 3, "expect touch NAME pressed|open");
                if (!t.get(2).equals("pressed") && !t.get(2).equals("open")) throw new IllegalArgumentException("pressed or open");
                return;
            case "motor":
                need(t, 5, "expect motor NAME power|ticks|velocity|amps OP VALUE");
                if (!java.util.Arrays.asList("power", "ticks", "velocity", "amps").contains(t.get(2))) {
                    throw new IllegalArgumentException("motor field: power, ticks, velocity or amps");
                }
                op(t, 3);
                return;
            case "servo":
                need(t, 5, "expect servo NAME target|actual OP VALUE");
                if (!t.get(2).equals("target") && !t.get(2).equals("actual")) throw new IllegalArgumentException("servo field: target or actual");
                op(t, 3);
                return;
            case "robot":
                need(t, 4, "expect robot x|y|heading|imu|shots OP VALUE");
                if (!java.util.Arrays.asList("x", "y", "heading", "imu", "shots").contains(t.get(1))) {
                    throw new IllegalArgumentException("robot field: x, y, heading, imu or shots");
                }
                op(t, 2);
                return;
            default:
                throw new IllegalArgumentException("expect what? (screen, value, time, error, phase, motor, servo, robot, bumped, touch, camera, size)");
        }
    }

    private static void need(List<String> t, int words, String usage) {
        if (t.size() < words) throw new IllegalArgumentException("too short: " + usage);
    }

    private static void op(List<String> t, int at) {
        if (!java.util.Arrays.asList(">", "<", ">=", "<=", "=").contains(t.get(at))) {
            throw new IllegalArgumentException("compare with > < >= <= or =");
        }
        Double.parseDouble(t.get(at + 1));
        if (t.size() > at + 2) Double.parseDouble(t.get(at + 2));
        if (t.size() > at + 3) throw new IllegalArgumentException("too many words at the end");
    }

    private boolean holds(List<String> t, String[] why) {
        switch (t.get(0)) {
            case "screen": {
                boolean has = false;
                for (String l : sim.ds.screen.screen()) if (l.contains(t.get(2))) has = true;
                boolean want = t.get(1).equals("has");
                why[0] = want ? (has ? "" : "not on the screen") : (has ? "it is on the screen" : "");
                return has == want;
            }
            case "error": {
                String err = sim.ds.error();
                if (t.get(1).equals("none")) {
                    why[0] = err.isEmpty() ? "" : err.split("\n")[0];
                    return err.isEmpty();
                }
                why[0] = err.isEmpty() ? "no error" : err.split("\n")[0];
                return err.contains(t.get(2));
            }
            case "value": {
                // The first number after the label on a screen line: "Lift : 1203 ticks" -> 1203.
                for (String l : sim.ds.screen.screen()) {
                    String s = l.trim();
                    if (!s.startsWith(t.get(1))) continue;
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("-?[0-9]+(\\.[0-9]+)?")
                            .matcher(s.substring(t.get(1).length()));
                    if (m.find()) return compare(Double.parseDouble(m.group()), t.subList(2, t.size()), why);
                }
                why[0] = "no screen line starts with \"" + t.get(1) + "\" and has a number";
                return false;
            }
            case "time":
                return compare(sim.ds.runSeconds(), t.subList(1, t.size()), why);
            case "touch": {
                SimRobot.SimTouch touch = sim.robot.touches.get(t.get(1));
                if (touch == null) throw new IllegalArgumentException("no switch \"" + t.get(1) + "\" in the robot file");
                boolean pressed = touch.pressed();
                why[0] = pressed ? "pressed" : "open";
                return pressed == t.get(2).equals("pressed");
            }
            case "size": {
                // expect size start ok (fits the 18 in cube now) | size match ok (18 x 24 x 29 now) | size most ok
                SimShapes.Extent e = t.get(1).equals("most") ? sim.robot.shapes.most : sim.robot.shapes.now();
                boolean ok = t.get(1).equals("start") ? e.fits(18, 18, 18) : e.fits(18, 24, 29);
                why[0] = e.toString();
                return ok == t.get(2).equals("ok");
            }
            case "camera": {
                // expect camera "Webcam 1" sees 31 | sees none | sees "RED SCORING" (a detection's text)
                SimCamera c = sim.robot.cameras.get(t.get(1));
                if (c == null) throw new IllegalArgumentException("no camera \"" + t.get(1) + "\" in the robot file");
                java.util.List<Integer> ids = c.seenIds();
                java.util.List<String> found = c.detections();
                why[0] = c.openPortals() == 0 ? "the camera is not open (no VisionPortal)" : "sees tags " + ids + " " + found;
                String want = t.get(3);
                if (want.equals("none")) return ids.isEmpty();
                if (want.matches("[0-9]+")) return ids.contains(Integer.parseInt(want));
                for (String d : found) if (d.contains(want)) return true;
                return false;
            }
            case "bumped": {
                // expect bumped none | expect bumped "Red HIVE"
                String b = sim.robot.bumpedInto;
                why[0] = b.isEmpty() ? "nothing" : b;
                return t.get(1).equals("none") ? b.isEmpty() : b.contains(t.get(1));
            }
            case "phase":
                why[0] = "phase " + sim.ds.phase();
                return sim.ds.phase().name().equalsIgnoreCase(t.get(1));
            case "motor": {
                SimMotor m = sim.robot.motors.get(t.get(1));
                if (m == null) throw new IllegalArgumentException("no motor \"" + t.get(1) + "\" in the robot file");
                double v;
                switch (t.get(2)) {
                    case "power": v = m.userPower(); break;
                    case "ticks": v = m.encoder(); break;
                    case "velocity": v = m.velocity(); break;
                    case "amps": v = m.amps; break;
                    default: throw new IllegalArgumentException("motor field: power, ticks, velocity or amps");
                }
                return compare(v, t.subList(3, t.size()), why);
            }
            case "servo": {
                SimRobot.SimServo s = sim.robot.servos.get(t.get(1));
                if (s == null) throw new IllegalArgumentException("no servo \"" + t.get(1) + "\" in the robot file");
                double v = t.get(2).equals("target") ? s.target() : s.actual;
                return compare(v, t.subList(3, t.size()), why);
            }
            case "robot": {
                double v;
                synchronized (sim.robot) {
                    switch (t.get(1)) {
                        case "x": v = sim.robot.x; break;
                        case "y": v = sim.robot.y; break;
                        case "heading": v = sim.robot.headingDeg; break;
                        case "imu": v = sim.robot.imuYaw(); break;
                        case "shots": v = sim.robot.shots(); break;
                        default: throw new IllegalArgumentException("robot field: x, y, heading, imu or shots");
                    }
                }
                return compare(v, t.subList(2, t.size()), why);
            }
            default:
                throw new IllegalArgumentException("expect what? (screen, error, phase, motor, servo, robot, bumped)");
        }
    }

    // OP VALUE [TOLERANCE]: > < >= <= = (exact within 1e-6, or within TOLERANCE)
    private static boolean compare(double v, List<String> t, String[] why) {
        String op = t.get(0);
        double want = Double.parseDouble(t.get(1));
        double tol = t.size() > 2 ? Double.parseDouble(t.get(2)) : 1e-6;
        why[0] = "was " + String.format(Locale.US, "%.4g", v);
        switch (op) {
            case ">": return v > want;
            case "<": return v < want;
            case ">=": return v >= want;
            case "<=": return v <= want;
            case "=": return Math.abs(v - want) <= tol;
            default: throw new IllegalArgumentException("compare with > < >= <= or =");
        }
    }

    private void print(String what) {
        switch (what) {
            case "screen":
                report.add("        screen:");
                for (String l : sim.ds.screen.screen()) report.add("          | " + l);
                return;
            case "motors":
                for (SimMotor m : sim.robot.motors.values()) {
                    report.add(String.format(Locale.US, "          motor %-12s power %5.2f  ticks %7d  speed %7.0f/s  %4.1f A",
                            m.name, m.userPower(), m.encoder(), m.velocity(), m.amps));
                }
                return;
            case "servos":
                for (SimRobot.SimServo s : sim.robot.servos.values()) {
                    report.add(String.format(Locale.US, "          servo %-12s at %.3f  going to %.3f", s.name, s.actual, s.target()));
                }
                return;
            case "size":
                report.add("          size now: " + sim.robot.shapes.now() + "; at full reach: " + sim.robot.shapes.most);
                return;
            case "cameras":
                for (SimCamera c : sim.robot.cameras.values()) {
                    report.add("          camera " + c.name + (c.openPortals() == 0 ? ": not open" : ": sees tags " + c.seenIds()));
                    for (String d : c.detections()) report.add("            " + d);
                }
                return;
            case "robot":
                synchronized (sim.robot) {
                    report.add(String.format(Locale.US, "          robot x %.1f in  y %.1f in  heading %.1f deg  imu %.1f",
                            sim.robot.x, sim.robot.y, sim.robot.headingDeg, sim.robot.imuYaw()));
                }
                return;
            default:
                throw new IllegalArgumentException("print screen, motors, servos, cameras, size or robot");
        }
    }

    // Words, with "quoted text" kept together.
    static List<String> tokens(String line) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '#') break;   // the rest of the line is a note
            if (c == '"') {
                // Quoted text; \" inside it is a quote mark.
                StringBuilder b = new StringBuilder();
                int j = i + 1;
                boolean closed = false;
                while (j < line.length()) {
                    char d = line.charAt(j);
                    if (d == '\\' && j + 1 < line.length() && line.charAt(j + 1) == '"') { b.append('"'); j += 2; continue; }
                    if (d == '"') { closed = true; break; }
                    b.append(d);
                    j++;
                }
                if (!closed) throw new IllegalArgumentException("a quote is not closed");
                out.add(b.toString());
                i = j + 1;
            } else {
                int end = i;
                while (end < line.length() && !Character.isWhitespace(line.charAt(end))) end++;
                out.add(line.substring(i, end));
                i = end;
            }
        }
        return out;
    }
}
