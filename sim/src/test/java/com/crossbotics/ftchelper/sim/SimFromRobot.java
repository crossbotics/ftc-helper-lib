package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.robot.RobotDefinition;
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
 * <p>:sim:run and :sim:scenario do the same first ({@link #auto}), so a team seldom runs it alone.
 *
 * <p>Lines it writes end with "# from Robot.java" and are rewritten every time. Every other line is
 * the team's (measured, from photos or CAD) and is kept, even for the same setting: a measured
 * {@code shape.lift} wins over the generated guess. Generated lines whose part is gone from the code
 * are removed. It stops (exit 1, nothing written) when the file describes a device differently from
 * the code: a motor in the file that the code uses as a servo, or other drive motors. A size or start
 * guess a new file got (for example {@code robot.lengthIn = 18}, left as it was) is replaced once the code
 * gives that number ({@code chassisMm}). New parts go right after the last generated line, so running it
 * again and again never adds another block. An intake's {@code holds=} comes from {@code Pieces.max(n)}
 * when the intake does not say.
 *
 * <p>A Tunables number written MEASURE gets a stand-in line, {@code tunable.IntakeDeploy.deployed = 0.5}: the
 * simulator uses it in place of MEASURE (the real robot never reads this file, so it still stops at INIT).
 * The value is the library's default for that field. Once the code has a number, the line goes away.
 *
 * <p>It is a starting point: a part with no {@code looks(...)} gets a guessed shape (from the chassis, the
 * other parts and the parts catalog, inside the size rules), marked TODO measure until someone measures it.
 */
public final class SimFromRobot {
    private SimFromRobot() { }

    static final String MARK = "# from Robot.java";
    private static final String[] DEVICE_KINDS = {"motor.", "servo.", "crservo.", "touch.", "color.", "distance.", "light.", "analog."};

    /** The result of a merge: the new file, or the problems that stopped it. */
    static final class Result {
        final String text;
        final List<String> problems;
        final List<String> kept;
        /** Marked lines the code changed: "key: old -> new" (the old file is kept as .bak). */
        final List<String> changed = new ArrayList<>();

        Result(String text, List<String> problems, List<String> kept) {
            this.text = text;
            this.problems = problems;
            this.kept = kept;
        }
    }

    public static void main(String[] args) throws Exception {
        String robotClass = DEFAULT_CLASS;
        String out = "";
        String field = DEFAULT_FIELD;
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
        boolean existed = Files.isRegularFile(file);
        Result r = make(robot, robotClass, file, field);
        if (!r.problems.isEmpty()) {
            System.err.println("fromRobot: the robot file and Robot.java disagree (nothing written):");
            for (String p : r.problems) System.err.println("  " + p);
            System.exit(1);
        }
        System.out.println("Wrote " + file + (existed ? "" : " (new)") + ".");
        for (String k : r.kept) {
            System.out.println("  kept your " + k + (k.contains(" = ") ? "" : " (it is not marked " + MARK + ")"));
        }
        printChanged(r, file);
        if (r.text.contains("TODO")) System.out.println("  Lines with TODO are guesses: measure them (docs/guides/simulator.md).");
        int standIns = 0;
        for (String line : r.text.split("\n")) if (line.startsWith("tunable.")) standIns++;
        if (standIns > 0) {
            System.out.println("  " + standIns + " tunable. line(s): stand-ins for MEASURE numbers, so the simulator can INIT "
                    + "(the real robot still stops until you write the numbers in Tunables.java).");
        }
        // The same Tunables name checks as INIT (a field no part reads, a section meant for a part that no
        // part reads), so a misnamed number is found now, not at the robot. The file is written anyway.
        List<String> tunables = robot.checkTunables();
        if (tunables.isEmpty()) {
            System.out.println("Tunables.java: names checked like INIT does: fine.");
        } else {
            System.out.println("Tunables.java: names checked like INIT does (INIT stops on a field no part reads, and warns about a section):");
            for (String p : tunables) System.out.println("  " + p);
        }
    }

    static final String DEFAULT_CLASS = "org.firstinspires.ftc.teamcode.Robot";
    static final String DEFAULT_FIELD = "biobuzz-2026";

    /**
     * Merges the code's lines into the file and writes it (only when it changed), then loads it once, so a
     * file the simulator cannot read is caught here. Writes nothing when the file and the code disagree
     * (the result's problems).
     */
    static Result make(RobotDefinition robot, String robotClass, Path file, String field) throws java.io.IOException {
        String existing = Files.isRegularFile(file) ? new String(Files.readAllBytes(file), StandardCharsets.UTF_8) : null;
        Result r = merge(existing, holdsFromPieces(robot.simLines(), robot), robotClass, field);
        if (!r.problems.isEmpty()) return r;
        if (!r.text.equals(existing)) {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            if (existing != null && !r.changed.isEmpty()) {
                Files.write(backup(file), existing.getBytes(StandardCharsets.UTF_8));
            }
            Files.write(file, r.text.getBytes(StandardCharsets.UTF_8));
        }
        SimRobot.load(file);
        return r;
    }

    /**
     * The first step of ./gradlew :sim:run and :sim:scenario: the robot file made again from the team's
     * Robot class, the same way as :sim:fromRobot (the team's own lines are kept). sim.fromRobot is the
     * robot file (empty: -PnoFromRobot or -Probot=..., nothing is done). Prints one line.
     *
     * @param robotFile the robot file the simulator would use now
     * @return the robot file to use: the one made, or robotFile when there is no Robot class or it failed
     */
    static Path auto(Path robotFile) {
        String where = System.getProperty("sim.fromRobot", "");
        if (where.isEmpty()) return robotFile;
        Path file = Paths.get(where);
        String shown = (file.getParent() == null ? "" : file.getParent().getFileName() + "/") + file.getFileName();
        if (Files.isRegularFile(file) && madeFrom(file) == null) {
            // A robot file the team wrote (or copied): not changed without asking.
            System.out.println("Robot file: " + shown + " is your own (not made from Robot.java): used as it is. "
                    + "./gradlew :sim:fromRobot adds the code's lines to it.");
            return file;
        }
        String name = robotClassFor(file, System.getProperty("sim.robotClass", ""), System.getProperty("sim.teamCode", ""));
        if (name == null) return robotFile;
        String javaFile = name.substring(name.lastIndexOf('.') + 1) + ".java";
        String field = System.getProperty("sim.field", "");
        try {
            RobotDefinition robot = (RobotDefinition) Class.forName(name).getDeclaredConstructor().newInstance();
            Result r = make(robot, name, file, field.isEmpty() ? DEFAULT_FIELD : field);
            if (r.problems.isEmpty()) {
                System.out.println("Robot file made from " + javaFile + ": " + shown);
                printChanged(r, file);
                return file;
            }
            System.out.println("Robot file NOT made from " + javaFile + ": it and " + shown + " disagree:");
            for (String p : r.problems) System.out.println("  " + p);
            System.out.println("  The simulator uses " + shown + " as it is. Fix one of them (docs/guides/simulator.md).");
        } catch (Exception | LinkageError e) {
            System.out.println("Robot file NOT made from " + javaFile + " (" + e + "). The simulator uses " + shown + " as it is.");
        }
        return Files.isRegularFile(file) ? file : robotFile;
    }

    // The old robot file, kept when the code changed a marked line: robot.properties.bak.
    static Path backup(Path file) {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    private static void printChanged(Result r, Path file) {
        if (r.changed.isEmpty()) return;
        for (String c : r.changed) System.out.println("  changed from Robot.java: " + c);
        System.out.println("  The old file is " + backup(file).getFileName() + ". To keep a number of your own on a line, "
                + "delete that line's \"" + MARK + "\" note.");
    }

    /**
     * The team's Robot class: the one given (-ProbotClass), the one the robot file was made from (its first
     * line), org.firstinspires.ftc.teamcode.Robot, or a class in TeamCode that extends RobotDefinition
     * (Robot.java first). Null when there is none.
     */
    static String robotClassFor(Path robotFile, String given, String teamCode) {
        if (!given.trim().isEmpty()) return given.trim();
        List<String> names = new ArrayList<>();
        String made = madeFrom(robotFile);
        if (made != null) names.add(made);
        names.add(DEFAULT_CLASS);
        names.addAll(robotSources(teamCode));
        for (String n : names) {
            try {
                if (RobotDefinition.class.isAssignableFrom(Class.forName(n, false, SimFromRobot.class.getClassLoader()))) return n;
            } catch (ClassNotFoundException | LinkageError e) {
                // not in the code the simulator runs: the next one
            }
        }
        return null;
    }

    // "# The simulator's robot, made from X ..." at the top of a robot file: X, or null.
    private static String madeFrom(Path robotFile) {
        if (!Files.isRegularFile(robotFile)) return null;
        try {
            for (String line : Files.readAllLines(robotFile, StandardCharsets.UTF_8)) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^#.*made from ([\\w.$]+)").matcher(line);
                if (m.find()) return m.group(1).replaceAll("\\.$", "");
                if (!line.startsWith("#") && !line.trim().isEmpty()) return null;
            }
        } catch (java.io.IOException | RuntimeException e) {
            // unreadable: no name from it
        }
        return null;
    }

    // Classes in TeamCode's Java files that say "extends RobotDefinition", Robot.java first.
    static List<String> robotSources(String teamCode) {
        List<String> out = new ArrayList<>();
        if (teamCode.isEmpty()) return out;
        Path src = Paths.get(teamCode, "src", "main", "java");
        if (!Files.isDirectory(src)) return out;
        try (java.util.stream.Stream<Path> files = Files.walk(src)) {
            files.filter(f -> f.toString().endsWith(".java")).sorted((a, b) -> Boolean.compare(
                    !a.getFileName().toString().equals("Robot.java"), !b.getFileName().toString().equals("Robot.java"))).forEach(f -> {
                try {
                    String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                    if (!text.contains("extends RobotDefinition")) return;
                    java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(text);
                    String cls = f.getFileName().toString().replace(".java", "");
                    out.add(pkg.find() ? pkg.group(1) + "." + cls : cls);
                } catch (java.io.IOException e) {
                    // unreadable: not a candidate
                }
            });
        } catch (java.io.IOException e) {
            // no list: no candidates
        }
        return out;
    }

    /**
     * An intake without {@code .holds(n)} gets the game's limit as a guess ("holds=4 ... # TODO") from the
     * code; when the robot counts its pieces ({@code Pieces.max(n)}), that limit is how many it holds, so use
     * it instead of the guess.
     */
    static List<String> holdsFromPieces(List<String> lines, RobotDefinition robot) {
        int max = 0;
        for (com.crossbotics.ftchelper.robot.Module<?> m : robot.modules()) {
            if (m instanceof com.crossbotics.ftchelper.robot.Pieces) max = Math.max(max, ((com.crossbotics.ftchelper.robot.Pieces) m).maxPieces());
        }
        if (max <= 0) return lines;
        java.util.regex.Pattern guess = java.util.regex.Pattern.compile(", holds=\\d+((?:, limits=\\S+)?)   # TODO how many it holds.*$");
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            if (l.startsWith("collect.")) l = guess.matcher(l).replaceFirst(", holds=" + max + "$1   # Pieces.max");
            out.add(l);
        }
        return out;
    }

    // The size and start lines a new file gets when the code does not give them: guesses. A later run
    // replaces a guess (the exact same text, unmarked) when the code gives that number (chassisMm, heightMm).
    private static final String[][] GUESSES = {
        {"# TODO measure: the robot's size in inches, and a legal start (inches from the field centre).", null},
        {"robot.lengthIn = 18", "robot.lengthIn"}, {"robot.widthIn = 18", "robot.widthIn"},
        {"robot.heightIn = 18", "robot.heightIn"}, {"robot.chassisHeightIn = 4", "robot.chassisHeightIn"},
        {"start.x = -56", null}, {"start.y = -61.55", null}, {"start.heading = 0", null},
        {"# TODO measure: wheel centres side to side, and front to back.", "drive.trackWidthIn"},
        {"drive.trackWidthIn = 15", "drive.trackWidthIn"}, {"drive.wheelBaseIn = 13", "drive.wheelBaseIn"}};

    // True for an unmarked line that is still exactly a guess a new file got, now given by the code.
    // A generated line whose number the code does not know: "TODO" in its note, or a MEASURE stand-in.
    private static boolean codeGuesses(String generatedLine) {
        return generatedLine.trim().startsWith("tunable.") || generatedLine.contains("TODO");
    }

    private static boolean isGuess(String line, Map<String, String> gen) {
        String t = line.trim();
        for (String[] g : GUESSES) if (g[1] != null && !g[0].startsWith("#") && g[0].equals(t) && gen.containsKey(g[1])) return true;
        return false;
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
            sb.append("# The simulator's robot, made from ").append(robotName).append(" (again at each start of the simulator).\n");
            sb.append("# Lines marked \"").append(MARK).append("\" come from the code and are rewritten each time.\n");
            sb.append("# Change any other line (or unmark a generated one to keep your own value): measure the\n");
            sb.append("# robot, or ask the robot-file skill (skills/robot-file). See docs/guides/simulator.md.\n\n");
            sb.append("battery = 12.8\n");
            sb.append("field = ").append(field).append("\n");
            // The size and start lines, unless the code gives them (then they come with the code's lines).
            for (String[] d : GUESSES) if (d[1] == null || !gen.containsKey(d[1])) sb.append(d[0]).append('\n');
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
            if (k != null && !lines[i].contains(MARK) && !isGuess(lines[i], gen)) teamKeys.put(k, i);
        }
        check(gen, teamKeys, lines, problems);

        Set<String> written = new LinkedHashSet<>();
        List<String> changed = new ArrayList<>();
        List<String> outLines = new ArrayList<>();
        int lastOurs = -1;   // where our last line went: new parts go right after it (no new block each run)
        for (String line : lines) {
            String k = key(line);
            boolean guess = k != null && !line.contains(MARK) && isGuess(line, gen);
            if (k != null && (line.contains(MARK) || guess)) {
                // One of ours (or a guess the code now gives): the new version, or nothing if the part is gone.
                if (gen.containsKey(k) && !teamKeys.containsKey(k) && written.add(k)) {
                    String g = gen.get(k);
                    String was = value(line), now = value(g);
                    if (!guess && !was.equals(now) && codeGuesses(g) && codeGuesses(line)) {
                        // The code only guesses this number (TODO, or a MEASURE stand-in): the team's edit wins.
                        outLines.add(line);
                        kept.add(k + " = " + was + " (the code guesses " + now + ")");
                    } else {
                        if (!guess && !was.equals(now)) changed.add(k + ": " + was + " -> " + now);
                        outLines.add(g);
                    }
                    if (!guess) lastOurs = outLines.size() - 1;
                }
                continue;
            }
            if (k != null && gen.containsKey(k)) {
                kept.add(k);
                written.add(k);
            }
            outLines.add(line);
        }
        // New parts: after our last line, or in a new block at the end when the file has none of ours.
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, String> e : gen.entrySet()) if (!written.contains(e.getKey())) added.add(e.getValue());
        if (!added.isEmpty()) {
            if (lastOurs >= 0) {
                outLines.addAll(lastOurs + 1, added);
            } else {
                while (!outLines.isEmpty() && outLines.get(outLines.size() - 1).trim().isEmpty()) outLines.remove(outLines.size() - 1);
                outLines.add("");
                outLines.add("# ---- added from Robot.java ----");
                outLines.addAll(added);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String l : outLines) sb.append(l).append('\n');
        // One newline at the end, however many there were.
        String text = sb.toString().replaceAll("\n+$", "\n");
        Result result = new Result(text, problems, kept);
        result.changed.addAll(changed);
        return result;
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
