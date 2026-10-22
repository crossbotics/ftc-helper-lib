package com.crossbotics.ftchelper.sim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The page's tests: the recorder, the team's scenario files (only in simulator/scenarios of the robot
 * project, by a plain name: nothing else on the computer can be read or written), a test run on the
 * page (the person watches the robot), and "the change was on purpose: update the test".
 */
final class SimTests {
    final SimEngine sim;
    final SimRecorder recorder;
    // the robot file the simulator runs, and the robot project (null: -PteamCode=none, tests are downloaded)
    private final Path robotFile;
    private final Path teamDir;
    // the robot file's own settings, for a fresh start before each test run
    private final double fileBattery;

    /** A file name: letters, digits, - and _ (no folders, no dots): "main-teleop". */
    static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,59}");

    SimTests(SimEngine sim, Path robotFile, Path teamDir) {
        this.sim = sim;
        this.recorder = new SimRecorder(sim);
        this.robotFile = robotFile == null ? null : robotFile.toAbsolutePath().normalize();
        this.teamDir = teamDir == null ? null : teamDir.toAbsolutePath().normalize();
        this.fileBattery = sim.robot.batteryVolts;
        SimFresh.readCodeValues();
    }

    /** The robot project from the sim.teamDir property (set by ./gradlew :sim:run with TeamCode), or null. */
    static Path teamDirFromProperty() {
        String d = System.getProperty("sim.teamDir", "");
        return d.isEmpty() ? null : java.nio.file.Paths.get(d);
    }

    /** The team's scenario folder (simulator/scenarios in the robot project), or null without TeamCode. */
    Path folder() {
        return teamDir == null ? null : teamDir.resolve("simulator").resolve("scenarios");
    }

    /** Where a test of this name is (only inside the folder); refuses any other name. */
    Path file(String name) {
        Path dir = folder();
        if (dir == null) throw new IllegalArgumentException("no robot project: start the simulator with your TeamCode to save tests there");
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("a test name has letters, digits, - and _ only (e.g. main-teleop)");
        }
        Path f = dir.resolve(name + ".sim").normalize();
        if (!f.getParent().equals(dir.normalize())) throw new IllegalArgumentException("not in the scenarios folder");
        return f;
    }

    /** The tests in the folder (names without .sim), in name order. */
    List<String> list() throws IOException {
        List<String> out = new ArrayList<>();
        Path dir = folder();
        if (dir == null || !Files.isDirectory(dir)) return out;
        try (java.util.stream.Stream<Path> s = Files.list(dir)) {
            s.forEach(p -> {
                String n = p.getFileName().toString();
                if (n.endsWith(".sim") && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    String base = n.substring(0, n.length() - 4);
                    if (NAME.matcher(base).matches()) out.add(base);
                }
            });
        }
        java.util.Collections.sort(out);
        return out;
    }

    String read(String name) throws IOException {
        Path f = file(name);
        if (!Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("no test called " + name);
        return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
    }

    /** Writes a test; false (nothing written) if it exists and replace is false. */
    boolean write(String name, String text, boolean replace) throws IOException {
        Path f = file(name);
        if (Files.isSymbolicLink(f)) throw new IllegalArgumentException("not a plain file: " + f);
        if (Files.exists(f, LinkOption.NOFOLLOW_LINKS) && !replace) return false;
        Files.createDirectories(f.getParent());
        Files.write(f, text.getBytes(StandardCharsets.UTF_8));
        return true;
    }

    /** "robot PATH" for a test saved in the folder when the simulator runs another robot file than the team's usual one. */
    String robotLine() {
        if (teamDir == null || robotFile == null) return "";
        Path usual = teamDir.resolve("simulator").resolve("robot.properties").normalize();
        if (robotFile.equals(usual)) return "";
        return "robot " + folder().relativize(robotFile).toString().replace('\\', '/');
    }

    /** How to run a saved test from the ftc-helper-lib folder (NAME is the test's name). */
    String runLine() {
        if (teamDir != null) return "./gradlew :sim:scenario -Pfile=../simulator/scenarios/NAME.sim   (from the ftc-helper-lib folder)";
        String robot = robotFile == null ? "" : " -Probot=" + shortPath(robotFile);
        return "./gradlew :sim:scenario -PteamCode=none" + robot + " -Pfile=PATH/TO/NAME.sim   (from the ftc-helper-lib folder)";
    }

    // A path from the folder the simulator was started in (the ftc-helper-lib folder), if it is inside it.
    private static String shortPath(Path p) {
        Path here = java.nio.file.Paths.get("").toAbsolutePath().normalize();
        return (p.startsWith(here) ? here.relativize(p) : p).toString().replace('\\', '/');
    }

    // ------------------------------------------------------------------ a test run on the page

    private volatile Thread runner = null;
    private volatile SimScenario running = null;
    private volatile boolean measuring = false, done = false;
    private volatile String runName = "", note = "";
    private volatile List<String> runLines = new ArrayList<>();

    boolean runningNow() {
        Thread t = runner;
        return t != null && t.isAlive();
    }

    /**
     * Runs a test on the page from a fresh start (like the scenario command): the page's gamepads are
     * ignored meanwhile. measure = true: the recorder's "update the test" run (see SimScenario.measure).
     */
    synchronized void startRun(String name, String text, boolean measure) {
        if (recorder.recording()) throw new IllegalArgumentException("stop the recording first");
        if (runningNow()) throw new IllegalArgumentException("a test is running");
        final List<String> lines = new ArrayList<>(java.util.Arrays.asList(text.split("\r?\n", -1)));
        runLines = lines;
        runName = name == null ? "" : name;
        measuring = measure;
        done = false;
        note = robotNote(lines);
        final SimScenario s = new SimScenario(sim);
        s.measure = measure;
        running = s;
        Thread t = new Thread(() -> runNow(s, lines), "test-run");
        t.setDaemon(true);
        runner = t;
        t.start();
    }

    /** Stops a test running on the page (the lines left are not run). */
    void stopRun() {
        SimScenario s = running;
        if (s != null) s.go = false;
        sim.ds.stopAsync();
    }

    private void runNow(SimScenario s, List<String> lines) {
        SimRobot r = sim.robot;
        List<Object[]> userTunables = SimFresh.current();
        com.crossbotics.ftchelper.core.SettingsFile userSettings = com.crossbotics.ftchelper.core.SettingsFile.shared();
        sim.ds.pad1.pageLocked = true;
        sim.ds.pad2.pageLocked = true;
        try {
            sim.ds.pad1.apply(new java.util.HashMap<String, String>(), false);
            sim.ds.pad2.apply(new java.util.HashMap<String, String>(), false);
            // fresh, like the scenario command's new simulator for each file
            sim.ds.stop();
            SimFresh.robotApp();
            r.setBattery(fileBattery);
            sim.ds.autoTimer = true;
            sim.ds.teleTimer = true;
            if (r.pieces != null) {
                r.pieces.setScatter("file");
                r.pieces.setPreloaded(true);
            }
            r.resetPose();
            r.paths.clear();
            sim.jumps.incrementAndGet();
            s.run(lines);
        } catch (RuntimeException | InterruptedException e) {
            note = (note.isEmpty() ? "" : note + " ") + "The run stopped: " + e.getMessage();
        } finally {
            sim.ds.pad1.apply(new java.util.HashMap<String, String>(), false);
            sim.ds.pad2.apply(new java.util.HashMap<String, String>(), false);
            sim.ds.pad1.pageLocked = false;
            sim.ds.pad2.pageLocked = false;
            // the person's own state back: tuned numbers and the settings file (next INIT reads them again)
            com.crossbotics.ftchelper.robot.RobotDefinition.freshRobotApp();
            SimFresh.restore(userTunables);
            com.crossbotics.ftchelper.core.SettingsFile.useForTests(userSettings);
            done = true;
        }
    }

    // A "robot" line naming another robot file than the one the page runs: said, then run anyway.
    private String robotNote(List<String> lines) {
        for (String raw : lines) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            List<String> t = SimScenario.tokens(l);
            if (t.size() >= 2 && t.get(0).equalsIgnoreCase("robot")) {
                Path dir = folder();
                Path want = dir == null ? null : dir.resolve(t.get(1)).toAbsolutePath().normalize();
                if (want == null || !want.equals(robotFile)) {
                    return "This test asks for the robot file " + t.get(1) + "; the page runs " + (robotFile == null ? "another" : shortPath(robotFile))
                            + ". It runs with the page's robot file; ./gradlew :sim:scenario uses the test's own.";
                }
            }
        }
        return "";
    }

    /** The run's state for the page: running or done, the report so far, and the update if it measured. */
    String status() {
        SimScenario s = running;
        StringBuilder b = new StringBuilder("{\"running\":").append(runningNow()).append(",\"done\":").append(done)
                .append(",\"measure\":").append(measuring).append(",\"name\":");
        SimJson.string(b, runName).append(",\"note\":");
        SimJson.string(b, note).append(",\"report\":[");
        List<String> rep = s == null ? new ArrayList<String>() : new ArrayList<>(s.report());
        for (int i = 0; i < rep.size(); i++) SimJson.string(b.append(i > 0 ? "," : ""), rep.get(i));
        b.append("],\"failures\":").append(s == null ? 0 : s.failures());
        if (s != null && done && measuring) {
            Update u = update(runLines, s.seen);
            b.append(",\"update\":").append(u.json());
        }
        return b.append('}').toString();
    }

    // ------------------------------------------------------------------ the change was on purpose: update the test

    /** A test updated from a measuring run: the new text, what changed, and the lines it could not update. */
    static final class Update {
        String text = "";
        final List<String[]> changed = new ArrayList<>();   // {line number, old, new}
        final List<String[]> stuck = new ArrayList<>();     // {line number, line, why}

        String json() {
            StringBuilder b = new StringBuilder("{\"text\":");
            SimJson.string(b, text).append(",\"changed\":[");
            for (int i = 0; i < changed.size(); i++) {
                String[] c = changed.get(i);
                b.append(i > 0 ? "," : "").append("{\"n\":").append(c[0]).append(",\"old\":");
                SimJson.string(b, c[1]).append(",\"new\":");
                SimJson.string(b, c[2]).append('}');
            }
            b.append("],\"stuck\":[");
            for (int i = 0; i < stuck.size(); i++) {
                String[] c = stuck.get(i);
                b.append(i > 0 ? "," : "").append("{\"n\":").append(c[0]).append(",\"line\":");
                SimJson.string(b, c[1]).append(",\"why\":");
                SimJson.string(b, c[2]).append('}');
            }
            return b.append("]}").toString();
        }
    }

    private static final List<String> OPS = java.util.Arrays.asList(">", "<", ">=", "<=", "=");

    /**
     * The test with each failed check's value taken from what the measuring run saw (the steps, the
     * tolerances and the checks that passed stay as they are). WHY only the failed ones: a planned change
     * changed those; the rest still describe the robot. A check of words (phase, step, screen, error)
     * cannot take a number: it is listed as stuck, for the person to change or delete.
     */
    static Update update(List<String> lines, List<SimScenario.Seen> seen) {
        Update u = new Update();
        List<String> out = new ArrayList<>(lines);
        List<Integer> nums = new ArrayList<>();
        for (SimScenario.Seen x : seen) {
            if (x.passed) continue;
            String orig = lines.get(x.n - 1);
            String why = x.broken ? "the line has a mistake: " + x.why : "";
            String updated = x.broken ? null : updatedLine(orig, x);
            if (updated == null) {
                u.stuck.add(new String[] {String.valueOf(x.n), orig.trim(), why.isEmpty()
                        ? "this check looks at words, not a number (" + x.why + "): change it by hand or delete it" : why});
                continue;
            }
            if (!updated.equals(orig)) {
                u.changed.add(new String[] {String.valueOf(x.n), orig.trim(), updated.trim()});
                out.set(x.n - 1, updated);
                nums.add(x.n);
            }
        }
        if (!nums.isEmpty()) {
            // a note under the header comments: when and which lines (the old values are in git)
            int at = 0;
            while (at < out.size() && out.get(at).trim().startsWith("#")) at++;
            StringBuilder n = new StringBuilder();
            for (int i = 0; i < nums.size(); i++) n.append(i > 0 ? ", " : "").append(nums.get(i) + (nums.get(i) > at ? 1 : 0));
            out.add(at, "# Updated by the recorder on " + java.time.LocalDate.now() + " after a planned change: line"
                    + (nums.size() > 1 ? "s " : " ") + n + ".");
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < out.size(); i++) b.append(out.get(i)).append(i < out.size() - 1 ? "\n" : "");
        u.text = b.toString();
        return u;
    }

    // One failed check with its new number (or new time limit), keeping its note after #; null: cannot.
    static String updatedLine(String line, SimScenario.Seen x) {
        int hash = commentAt(line);
        String code = hash < 0 ? line : line.substring(0, hash);
        String comment = hash < 0 ? "" : line.substring(hash);
        String indent = code.substring(0, code.length() - code.replaceAll("^\\s+", "").length());
        List<String> t = SimScenario.tokens(code);
        if (t.isEmpty() || !t.get(0).equalsIgnoreCase("expect")) return null;
        double within = 0;
        int at = 1;
        if (t.size() > 2 && t.get(1).equals("within")) {
            within = Double.parseDouble(t.get(2));
            at = 3;
        }
        int op = -1;
        for (int i = at; i < t.size(); i++) if (OPS.contains(t.get(i))) { op = i; break; }
        boolean lateButTrue = !Double.isNaN(x.took) && within > 0 && x.took > within;
        if (lateButTrue) {
            // it happens, only later: a longer time limit (half as much again as it took)
            within = SimRecorder.waitFor(x.took);
        } else if (op >= 0 && op + 1 < t.size() && !Double.isNaN(x.value)) {
            String o = t.get(op);
            double v = x.value;
            String newOp = o;
            String value;
            boolean time = t.get(at).equals("time");
            if (o.equals("=")) {
                value = SimRecorder.num(v);
            } else if (o.equals(">") || o.equals(">=")) {
                newOp = ">=";
                value = SimRecorder.num(Math.floor(v * 100) / 100.0);
            } else if (time) {
                value = SimRecorder.num(SimRecorder.waitFor(v));
            } else {
                newOp = "<=";
                value = SimRecorder.num(Math.ceil(v * 100) / 100.0);
            }
            t.set(op, newOp);
            t.set(op + 1, value);
        } else {
            return null;
        }
        StringBuilder b = new StringBuilder(indent).append("expect");
        if (within > 0) b.append(" within ").append(SimRecorder.num(within));
        for (int i = at; i < t.size(); i++) {
            String w = t.get(i);
            boolean quoteIt = w.isEmpty() || w.contains(" ") || w.contains("\"") || w.contains("#")
                    || (i > at && code.contains("\"" + w + "\""));
            b.append(' ').append(quoteIt ? SimRecorder.quote(w) : w);
        }
        if (!comment.isEmpty()) b.append("   ").append(comment.trim());
        return b.toString();
    }

    // Where a note starts (a # outside quotes), or -1.
    private static int commentAt(String line) {
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && inQuote && i + 1 < line.length()) { i++; continue; }
            if (c == '"') inQuote = !inQuote;
            else if (c == '#' && !inQuote) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ JSON for the page

    /** GET /tests: where tests are saved, the tests there, and the recorder and the run now. */
    String info() throws IOException {
        StringBuilder b = new StringBuilder("{\"canSave\":").append(teamDir != null).append(",\"folder\":");
        SimJson.string(b, teamDir == null ? "" : "simulator/scenarios (in " + teamDir.getFileName() + ")").append(",\"robotLine\":");
        SimJson.string(b, robotLine()).append(",\"runLine\":");
        SimJson.string(b, runLine()).append(",\"tests\":[");
        List<String> l = list();
        for (int i = 0; i < l.size(); i++) SimJson.string(b.append(i > 0 ? "," : ""), l.get(i));
        b.append("],\"recording\":").append(recorder.recording()).append(",\"running\":").append(runningNow());
        return b.append('}').toString();
    }

    /** A short state for the stream (the page shows the recording bar from it). */
    String brief() {
        return String.format(Locale.US, "{\"rec\":%b,\"sec\":%.1f,\"count\":%d,\"run\":%b}",
                recorder.recording(), recorder.seconds(), recorder.count(), runningNow());
    }
}
