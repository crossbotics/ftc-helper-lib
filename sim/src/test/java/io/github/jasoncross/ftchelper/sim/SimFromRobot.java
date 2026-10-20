package io.github.jasoncross.ftchelper.sim;

import io.github.jasoncross.ftchelper.robot.RobotDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the simulator robot file from the team's {@code Robot.java} (a {@link RobotDefinition}), so the
 * device names and the drive always match the code.
 *
 * <pre>
 * ./gradlew :sim:fromRobot -PteamCode=../TeamCode [-ProbotClass=org.firstinspires.ftc.teamcode.Robot]
 *                          [-Pout=simulator/robot.properties] [-Pfield=biobuzz-2026]
 * </pre>
 *
 * <p>Lines it writes end with "# from Robot.java" and are rewritten every time. Every other line is
 * the team's (measured, from photos or CAD) and is kept, even for the same setting: a measured
 * {@code shape.lift} wins over the generated guess. Generated lines whose part is gone from the code
 * are removed. It stops (exit 1, nothing written) when the file describes a device differently from
 * the code: a motor in the file that the code uses as a servo, or other drive motors.
 *
 * <p>It is a starting point: the code does not know the robot's sizes, so the generated shapes are
 * guesses marked TODO until someone measures them.
 */
public final class SimFromRobot {
    private SimFromRobot() { }

    static final String MARK = "# from Robot.java";
    private static final String[] DEVICE_KINDS = {"motor.", "servo.", "crservo.", "touch."};

    /** The result of a merge: the new file, or the problems that stopped it. */
    static final class Result {
        final String text;
        final List<String> problems;
        final List<String> kept;

        Result(String text, List<String> problems, List<String> kept) {
            this.text = text;
            this.problems = problems;
            this.kept = kept;
        }
    }

    public static void main(String[] args) throws Exception {
        String robotClass = "org.firstinspires.ftc.teamcode.Robot";
        String out = "";
        String field = "biobuzz-2026";
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (args[i].equals("--robot")) robotClass = args[i + 1].isEmpty() ? robotClass : args[i + 1];
            if (args[i].equals("--out")) out = args[i + 1];
            if (args[i].equals("--field")) field = args[i + 1].isEmpty() ? field : args[i + 1];
        }
        if (out.isEmpty()) {
            System.err.println("fromRobot: say where to write: -Pout=path/to/robot.properties (or build with -PteamCode=...)");
            System.exit(1);
        }
        RobotDefinition robot;
        try {
            robot = (RobotDefinition) Class.forName(robotClass).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            System.err.println("fromRobot: no class " + robotClass + " (build with -PteamCode=..., or give -ProbotClass=...)");
            System.exit(1);
            return;
        }
        Path file = Paths.get(out);
        String existing = Files.isRegularFile(file) ? new String(Files.readAllBytes(file), StandardCharsets.UTF_8) : null;
        Result r = merge(existing, robot.simLines(), robotClass, field);
        if (!r.problems.isEmpty()) {
            System.err.println("fromRobot: the robot file and Robot.java disagree (nothing written):");
            for (String p : r.problems) System.err.println("  " + p);
            System.exit(1);
        }
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.write(file, r.text.getBytes(StandardCharsets.UTF_8));
        // Load it at once: a file the simulator cannot read is caught here, not later.
        SimRobot.load(file);
        System.out.println("Wrote " + file + (existing == null ? " (new)" : "") + ".");
        for (String k : r.kept) System.out.println("  kept your " + k + " (it is not marked " + MARK + ")");
        if (r.text.contains("TODO")) System.out.println("  Lines with TODO are guesses: measure them (docs/guides/simulator.md).");
    }

    /**
     * Merges the generated lines into an existing robot file (null = a new file).
     *
     * @param existing  the file now, or null
     * @param generated "key = value   # note" lines from {@link RobotDefinition#simLines()}
     * @param robotName for the header of a new file
     * @param field     the season's field, for a new file
     */
    static Result merge(String existing, List<String> generated, String robotName, String field) {
        List<String> problems = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        // The generated lines by key, marked.
        Map<String, String> gen = new LinkedHashMap<>();
        for (String g : generated) gen.put(key(g), mark(g));

        if (existing == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("# The simulator's robot, made from ").append(robotName).append(" by ./gradlew :sim:fromRobot.\n");
            sb.append("# Lines marked \"").append(MARK).append("\" come from the code and are rewritten each time.\n");
            sb.append("# Change any other line (or unmark a generated one to keep your own value): measure the\n");
            sb.append("# robot, or ask the robot-file skill (skills/robot-file). See docs/guides/simulator.md.\n\n");
            sb.append("battery = 12.8\n");
            sb.append("field = ").append(field).append("\n");
            // The size and start lines, unless the code gives them (then they come with the code's lines).
            String[][] defaults = {
                {"# TODO measure: the robot's size in inches, and a legal start (inches from the field centre).", null},
                {"robot.lengthIn = 18", "robot.lengthIn"}, {"robot.widthIn = 18", "robot.widthIn"},
                {"robot.heightIn = 18", "robot.heightIn"}, {"robot.chassisHeightIn = 4", "robot.chassisHeightIn"},
                {"start.x = -56", null}, {"start.y = -61.55", null}, {"start.heading = 0", null},
                {"# TODO measure: wheel centres side to side, and front to back.", "drive.trackWidthIn"},
                {"drive.trackWidthIn = 15", "drive.trackWidthIn"}, {"drive.wheelBaseIn = 13", "drive.wheelBaseIn"}};
            for (String[] d : defaults) if (d[1] == null || !gen.containsKey(d[1])) sb.append(d[0]).append('\n');
            sb.append('\n');
            sb.append("# ---- from Robot.java ----\n");
            for (String line : gen.values()) sb.append(line).append('\n');
            return new Result(sb.toString(), problems, kept);
        }

        // Existing file: keep the team's lines, refresh ours.
        String[] lines = existing.split("\n", -1);
        Map<String, Integer> teamKeys = new LinkedHashMap<>();   // the team's own (unmarked) settings
        for (int i = 0; i < lines.length; i++) {
            String k = key(lines[i]);
            if (k != null && !lines[i].contains(MARK)) teamKeys.put(k, i);
        }
        check(gen, teamKeys, lines, problems);

        Set<String> written = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String k = key(line);
            if (k != null && line.contains(MARK)) {
                // One of ours: the new version, or nothing if the part is gone from the code.
                if (gen.containsKey(k) && !teamKeys.containsKey(k) && written.add(k)) sb.append(gen.get(k)).append('\n');
                continue;
            }
            if (k != null && gen.containsKey(k)) {
                kept.add(k);
                written.add(k);
            }
            sb.append(line).append('\n');
        }
        // New parts: added at the end.
        boolean header = false;
        for (Map.Entry<String, String> e : gen.entrySet()) {
            if (written.contains(e.getKey())) continue;
            if (!header) {
                sb.append("\n# ---- added from Robot.java ----\n");
                header = true;
            }
            sb.append(e.getValue()).append('\n');
        }
        // One newline at the end, however many there were.
        String text = sb.toString().replaceAll("\n+$", "\n");
        return new Result(text, problems, kept);
    }

    // A device the code names as one kind (motor, servo...) but the file has as another; other drive motors.
    private static void check(Map<String, String> gen, Map<String, Integer> teamKeys, String[] lines, List<String> problems) {
        for (String g : gen.keySet()) {
            String kind = kindOf(g);
            if (kind == null) continue;
            String name = g.substring(kind.length());
            for (String other : DEVICE_KINDS) {
                if (!other.equals(kind) && teamKeys.containsKey(other + name)) {
                    problems.add("Robot.java uses \"" + name + "\" as a " + kind.replace(".", "")
                            + ", the robot file has " + other + name);
                }
            }
        }
        Integer driveMotors = teamKeys.get("drive.motors");
        if (driveMotors != null && gen.containsKey("drive.motors")) {
            String fileValue = value(lines[driveMotors]);
            String codeValue = value(gen.get("drive.motors"));
            // Compare the names only, not the spaces.
            if (!fileValue.replace(" ", "").equals(codeValue.replace(" ", ""))) {
                problems.add("drive.motors: Robot.java has " + codeValue + ", the robot file has " + fileValue);
            }
        }
    }

    private static String kindOf(String key) {
        for (String k : DEVICE_KINDS) if (key.startsWith(k)) return k;
        return null;
    }

    // "motor.lift = lift, ..." -> "motor.lift"; null for a comment or a blank line. The key ends where the
    // simulator's reader (java.util.Properties) ends it: at the first =, : or space.
    static String key(String line) {
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) return null;
        int end = keyEnd(t);
        return end == 0 ? null : t.substring(0, end);
    }

    private static int keyEnd(String t) {
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '=' || c == ':' || Character.isWhitespace(c)) return i;
        }
        return t.length();
    }

    // The value without its note.
    static String value(String line) {
        String t = line.trim();
        String v = t.substring(keyEnd(t)).trim();
        if (v.startsWith("=") || v.startsWith(":")) v = v.substring(1);
        int hash = v.indexOf(" #");
        return (hash >= 0 ? v.substring(0, hash) : v).trim();
    }

    // Adds the mark, keeping the line's own note: "x = 1   # from Robot.java. TODO measure".
    static String mark(String line) {
        int hash = line.indexOf("   #");
        if (hash < 0) return line + "   " + MARK;
        String note = line.substring(hash + 4).trim();
        return line.substring(0, hash) + "   " + MARK + ". " + note;
    }
}
