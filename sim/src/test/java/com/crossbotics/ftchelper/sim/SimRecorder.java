package com.crossbotics.ftchelper.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The page's test recorder: while it records, it writes down what a person does on the simulator page
 * (the OpMode chosen, INIT, START, STOP, every gamepad button and stick, how long they waited, the robot
 * or a game piece moved by hand), as scenario lines. When it stops, it suggests checks from what the
 * simulator sees now (did the autonomous finish, any error, the pieces scored, where the robot is), so a
 * student can make a test without writing one. Start lesson 10 teaches it.
 *
 * <p>Only scenario commands that already exist are written (SimScenario reads them), so a recorded test
 * is a normal scenario file: anyone can read it, change it and run it with ./gradlew :sim:scenario.
 *
 * <p>Gamepad changes come from SimGamepad.onPage (the page's on-screen gamepad, the keyboard, a real
 * controller and a phone all arrive there); commands from SimServer.command; an OpMode that stops by
 * itself is seen by SimEngine.onStep.
 */
final class SimRecorder {
    /** What happened, at what time (seconds since Record). */
    private static final class Ev {
        final double t;
        final Kind kind;
        String line;
        // the phase when it happened (waits before START are kept short: they do not change the test)
        final SimDriverStation.Phase phase;
        // a gamepad change: which pad (1, 2), which input (short name: a, lx ...) and its new value
        int pad;
        String key = "";
        double value;
        boolean removed;

        Ev(double t, Kind kind, String line, SimDriverStation.Phase phase) {
            this.t = t;
            this.kind = kind;
            this.line = line;
            this.phase = phase;
        }
    }

    private enum Kind { PAD, COMMAND, POSE, SELF_STOP }

    // The inputs a scenario knows: the page's short name and the FTC name written in the file.
    static final String[][] INPUTS = {
        {"a", "a"}, {"b", "b"}, {"x", "x"}, {"y", "y"}, {"lb", "left_bumper"}, {"rb", "right_bumper"},
        {"up", "dpad_up"}, {"down", "dpad_down"}, {"left", "dpad_left"}, {"right", "dpad_right"},
        {"back", "back"}, {"start", "start"}, {"guide", "guide"}, {"ls", "left_stick_button"}, {"rs", "right_stick_button"},
        {"lx", "left_stick_x"}, {"ly", "left_stick_y"}, {"rx", "right_stick_x"}, {"ry", "right_stick_y"},
        {"lt", "left_trigger"}, {"rt", "right_trigger"},
    };
    private static final int BUTTONS = 15;
    /** A button down and up within this time is one "press" (a tap). */
    static final double TAP_SEC = 0.35;
    /** Stick moves closer together than this are one move (a person sweeping a stick makes many). */
    static final double STICK_MERGE_SEC = 0.15;
    /** Waits before START (choosing, reading the screen) are cut to this: they do not change what is tested. */
    static final double IDLE_WAIT_MAX = 1.0;

    final SimEngine sim;
    // the robot file's battery (a "set battery" line only when the page changed it)
    private final double fileBattery;
    private boolean on = false;
    private long t0;
    private final List<Ev> events = new ArrayList<>();
    private final List<Map<String, Double>> last = new ArrayList<>();
    private final List<String> setup = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private String opMode = "", kind = "";
    private SimDriverStation.Phase lastPhase = SimDriverStation.Phase.IDLE;
    private double lastStopCommand = -10;
    private boolean fromNow = false;
    // what the field showed at Record, to tell what changed
    private int stored0, launched0, scored0, clean0;
    private final Map<String, Double> points0 = new HashMap<>();
    // the person's own state before Record, put back at the end (tuned numbers, the settings file)
    private List<Object[]> userTunables = null;
    private com.crossbotics.ftchelper.core.SettingsFile userSettings = null;

    SimRecorder(SimEngine sim) {
        this.sim = sim;
        this.fileBattery = sim.robot.batteryVolts;
        last.add(new HashMap<String, Double>());
        last.add(new HashMap<String, Double>());
    }

    synchronized boolean recording() {
        return on;
    }

    /** Seconds since Record (0 when not recording). */
    synchronized double seconds() {
        return on ? now() : 0;
    }

    /** How many things were recorded so far (the page shows it). */
    synchronized int count() {
        return events.size();
    }

    private double now() {
        return (System.nanoTime() - t0) / 1e9;
    }

    /**
     * Starts recording. fresh = true: like the scenario command, from a robot just turned on: the
     * OpMode running is stopped, the robot goes to its start spot, every game piece back, and the
     * Tunables numbers are the code's (a test runs with the code, not with numbers tuned on the page).
     * fresh = false ("from now"): the robot stays where it is; the test starts with a "set pose" there.
     */
    synchronized void start(boolean fresh) {
        events.clear();
        setup.clear();
        notes.clear();
        last.get(0).clear();
        last.get(1).clear();
        opMode = "";
        kind = "";
        fromNow = !fresh;
        lastStopCommand = -10;
        SimRobot r = sim.robot;
        if (fresh) {
            sim.ds.stop();
            userTunables = SimFresh.current();
            userSettings = com.crossbotics.ftchelper.core.SettingsFile.shared();
            SimFresh.robotApp();
            r.resetPose();
            r.paths.clear();
            sim.jumps.incrementAndGet();
        }
        // The page's settings that differ from a fresh simulator: written first, so the test runs the same.
        if (Math.abs(r.batteryVolts - fileBattery) > 1e-6) setup.add("set battery " + num(r.batteryVolts));
        if (r.pieces != null && !r.pieces.preloaded()) setup.add("set preload off");
        if (r.pieces != null && r.pieces.scatterSetting() != null) setup.add("set scatter " + r.pieces.scatterSetting());
        if (!sim.ds.autoTimer) setup.add("set autotimer off");
        if (!sim.ds.teleTimer) setup.add("set teletimer off");
        if (!fresh) {
            SimDriverStation.Entry e = sim.ds.current();
            SimDriverStation.Phase p = sim.ds.phase();
            if (e != null && (p == SimDriverStation.Phase.INIT || p == SimDriverStation.Phase.RUNNING)) {
                opMode = e.name;
                kind = e.kind;
                setup.add("init " + quote(e.name));
                if (p == SimDriverStation.Phase.RUNNING) setup.add("start");
            }
            synchronized (r) {
                setup.add("set pose " + num(r.x) + " " + num(r.y) + " " + num(r.headingDeg));
            }
            notes.add("Recorded from the middle of a run: the test puts the robot where it was, but the game pieces "
                    + "start where the match starts them and the parts start at rest. A test from INIT is more reliable.");
        }
        if (r.pieces != null) {
            stored0 = r.pieces.storedCount();
            launched0 = r.pieces.launched();
            scored0 = sum(r.pieces.scored());
            Integer c = r.pieces.verdictCounts().get("clean");
            clean0 = c == null ? 0 : c;
            points0.clear();
            synchronized (r) {
                for (Map.Entry<String, SimScore.Side> e : r.pieces.score.sides().entrySet()) points0.put(e.getKey(), e.getValue().total());
            }
        }
        lastPhase = sim.ds.phase();
        t0 = System.nanoTime();
        on = true;
        sim.ds.pad1.onPage = m -> pad(1, m);
        sim.ds.pad2.onPage = m -> pad(2, m);
        sim.onStep = this::step;
    }

    /** Stops without a test (Cancel). */
    synchronized void cancel() {
        detach();
        putBack();
    }

    private void detach() {
        on = false;
        sim.ds.pad1.onPage = null;
        sim.ds.pad2.onPage = null;
        sim.onStep = null;
    }

    // The person's tuned numbers and settings file, as before Record.
    private void putBack() {
        if (userSettings != null) com.crossbotics.ftchelper.core.SettingsFile.useForTests(userSettings);
        if (userTunables != null) SimFresh.restore(userTunables);
        userSettings = null;
        userTunables = null;
    }

    /** A gamepad update from the page (only the inputs that changed are written). */
    synchronized void pad(int n, Map<String, String> form) {
        if (!on) return;
        SimDriverStation.Phase phase = sim.ds.phase();
        Map<String, Double> was = last.get(n - 1);
        double t = now();
        for (int i = 0; i < INPUTS.length; i++) {
            String key = INPUTS[i][0];
            double v = i < BUTTONS ? (on(form.get(key)) ? 1 : 0) : stick(form.get(key));
            Double before = was.get(key);
            if (before != null && before == v) continue;
            if (before == null && v == 0) {
                was.put(key, v);
                continue;
            }
            was.put(key, v);
            // Before INIT and after STOP the gamepad does nothing: not written.
            if (phase != SimDriverStation.Phase.INIT && phase != SimDriverStation.Phase.RUNNING) continue;
            Ev e = new Ev(t, Kind.PAD, "", phase);
            e.pad = n;
            e.key = key;
            e.value = v;
            events.add(e);
        }
    }

    /**
     * A command from the page that worked (INIT, START, STOP, a push, the robot or a piece moved by hand,
     * a setting). The auto builder's Run is not a scenario command: it is noted instead.
     */
    synchronized void command(Map<String, String> f) {
        if (!on) return;
        String action = f.getOrDefault("action", "");
        SimDriverStation.Phase phase = sim.ds.phase();
        double t = now();
        String line;
        Kind k = Kind.COMMAND;
        switch (action) {
            case "init": {
                String name = f.getOrDefault("opmode", "");
                SimDriverStation.Entry e = sim.ds.find(name);
                opMode = name;
                kind = e == null ? "" : e.kind;
                line = "init " + quote(name);
                break;
            }
            case "start": line = "start"; break;
            case "stop":
                lastStopCommand = t;
                line = "stop";
                break;
            case "push": line = "push " + num(d(f, "forward")) + " " + num(d(f, "right")) + " " + num(d(f, "seconds")); break;
            case "resetPose":
                line = "set pose " + num(sim.robot.startX) + " " + num(sim.robot.startY) + " " + num(sim.robot.startHeading) + "\nset field start";
                break;
            case "resetField": line = "set field start"; break;
            case "piece": {
                double z = d(f, "z");
                line = "set piece " + f.getOrDefault("type", "POLLEN") + " " + num(d(f, "x")) + " " + num(d(f, "y")) + (z != 0 ? " " + num(z) : "");
                break;
            }
            case "preload": line = "set preload " + ("1".equals(f.get("on")) ? "on" : "off"); break;
            case "scatter": line = "set scatter " + f.getOrDefault("mode", "file"); break;
            case "pose":
                k = Kind.POSE;
                line = "set pose " + num(d(f, "x")) + " " + num(d(f, "y")) + " " + num(d(f, "heading"));
                break;
            case "touch": {
                SimRobot.SimTouch touch = sim.robot.touches.get(f.getOrDefault("name", ""));
                if (touch == null) return;
                line = "set touch " + f.get("name") + " " + (touch.manual ? "on" : "off");
                break;
            }
            case "autoTimer": line = "set autotimer " + ("1".equals(f.get("on")) ? "on" : "off"); break;
            case "teleTimer": line = "set teletimer " + ("1".equals(f.get("on")) ? "on" : "off"); break;
            case "battery": line = "set battery " + num(sim.robot.batteryVolts); break;
            default: return;   // not part of a test (clear the path lines ...)
        }
        events.add(new Ev(t, k, line, phase));
    }

    /** The auto builder ran a plan while recording: a test cannot do that (it runs your own OpModes). */
    synchronized void builderRun() {
        if (!on) return;
        String n = "The auto builder's Run is not in the test: a test runs your own OpModes. Copy the plan into your "
                + "code (the builder's Code tab), then record that autonomous.";
        if (!notes.contains(n)) notes.add(n);
    }

    // Each physics step: an OpMode that stopped by itself (an autonomous that finished, a timer).
    private synchronized void step() {
        if (!on) return;
        SimDriverStation.Phase p = sim.ds.phase();
        if (lastPhase == SimDriverStation.Phase.RUNNING && p == SimDriverStation.Phase.STOPPED) {
            double t = now();
            if (t - lastStopCommand > 0.5) events.add(new Ev(t, Kind.SELF_STOP, "", p));
        }
        lastPhase = p;
    }

    // ------------------------------------------------------------------ stop: the test

    /** One suggested check: a line "expect ..." the page shows with a tick box and its numbers to edit. */
    static final class Check {
        final String id, label;
        /** "goal" (what the test is for: ticked) or "detail" (only if it matters: not ticked). */
        final String group;
        boolean on;
        /** Wait up to this many seconds for it to become true (0: check at once). */
        double within;
        /** A number check: what, compared how, with what (and = VALUE TOLERANCE). */
        String subject = "", op = "", value = "", tol = "";
        /** A word check (phase, step, error, bumped, screen): the words after "expect [within T]". */
        String words = null;
        /** For a screen check: the text it looks for (the page lets you edit it). */
        String text = null;

        Check(String id, String label, String group, boolean on) {
            this.id = id;
            this.label = label;
            this.group = group;
            this.on = on;
        }

        static Check number(String id, String label, String group, boolean on, String subject, String op, String value, String tol) {
            Check c = new Check(id, label, group, on);
            c.subject = subject;
            c.op = op;
            c.value = value;
            c.tol = tol;
            return c;
        }

        static Check words(String id, String label, String group, boolean on, String words) {
            Check c = new Check(id, label, group, on);
            c.words = words;
            return c;
        }

        /** The scenario line. */
        String line() {
            String w = text != null ? "screen has " + quote(text) : words != null ? words
                    : subject + " " + op + " " + value + ("=".equals(op) && !tol.isEmpty() ? " " + tol : "");
            return "expect " + (within > 0 ? "within " + num(within) + " " : "") + w;
        }
    }

    /** A finished recording: the steps, the checks to choose from, and notes for the person. */
    static final class Recording {
        String opMode = "", kind = "", date = "";
        final List<String> steps = new ArrayList<>();
        final List<Check> checks = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        /** A wait for the end, used when no ticked check waits ("within"): what the person waited. */
        double tailWait;

        /**
         * The whole file: the header (made by the recorder, date, OpMode, how to run it), the robot line
         * if any, the steps, the ticked checks. name: the file name without .sim; robotLine: "" or "robot ...";
         * runLine: how to run it.
         */
        String text(String name, String robotLine, String runLine) {
            StringBuilder b = new StringBuilder();
            b.append("# ").append(name).append(".sim: made by the simulator's test recorder on ").append(date)
                    .append(", from ").append(quote(opMode)).append(".\n");
            b.append("# The steps replay what was done on the page; the \"expect\" lines at the end are the checks.\n");
            b.append("# Change a check's number or tolerance to make it looser or stricter (start lesson 10).\n");
            if (!runLine.isEmpty()) b.append("# Run it: ").append(runLine).append('\n');
            if (!robotLine.isEmpty()) b.append(robotLine).append('\n');
            for (String s : steps) b.append(s).append('\n');
            boolean waits = false;
            for (Check c : checks) if (c.on && c.within > 0) waits = true;
            b.append("# The checks\n");
            if (!waits && tailWait >= 0.05) b.append("wait ").append(num(tailWait)).append('\n');
            for (Check c : ordered()) if (c.on) b.append(c.line()).append('\n');
            return b.toString();
        }

        /**
         * The checks that wait ("within") first, then the others: what happens at the end is waited for,
         * then "error none" and the like are checked once it has happened.
         */
        List<Check> ordered() {
            List<Check> out = new ArrayList<>();
            for (Check c : checks) if (c.within > 0) out.add(c);
            for (Check c : checks) if (c.within <= 0) out.add(c);
            return out;
        }
    }

    /** Stops recording and makes the test: the steps, and checks suggested from what the simulator sees now. */
    synchronized Recording stop() {
        double end = now();
        detach();
        Recording rec = new Recording();
        rec.opMode = opMode;
        rec.kind = kind;
        rec.date = java.time.LocalDate.now().toString();
        rec.notes.addAll(notes);
        List<Ev> evs = compact(new ArrayList<>(events));
        // An autonomous that ended by itself as the last thing: that is the test's goal (a check, not a step).
        Ev finalStop = null;
        if (!evs.isEmpty() && evs.get(evs.size() - 1).kind == Kind.SELF_STOP) finalStop = evs.remove(evs.size() - 1);
        rec.steps.addAll(setup);
        double prev = 0;
        for (Ev e : evs) {
            if (e.kind == Kind.SELF_STOP) {
                rec.steps.add("expect within " + num(waitFor(e.t - prev)) + " phase STOPPED   # it stops by itself (autonomous ends)");
                prev = e.t;
                continue;
            }
            double gap = e.t - prev;
            double w = e.phase == SimDriverStation.Phase.RUNNING ? gap : Math.min(gap, IDLE_WAIT_MAX);
            // (nothing to wait for before the first thing done)
            if (w >= 0.05 && e != evs.get(0)) rec.steps.add("wait " + num(Math.round(w * 100) / 100.0));
            for (String l : e.line.split("\n")) rec.steps.add(l);
            prev = e.t;
        }
        if (rec.steps.isEmpty() || opMode.isEmpty()) {
            rec.notes.add("Nothing to test yet: choose an OpMode and press INIT while recording.");
        }
        double tail = (finalStop != null ? finalStop.t : end) - prev;
        rec.tailWait = Math.round(Math.min(tail, 30) * 10) / 10.0;
        suggest(rec, finalStop, finalStop != null ? waitFor(finalStop.t - prev) : Math.min(30, waitFor(tail)));
        putBack();
        return rec;
    }

    // A time limit for something that took this long: half as much again, rounded up, at least 1 s.
    static double waitFor(double took) {
        return Math.max(1, Math.ceil(took * 1.5 * 10) / 10.0);
    }

    // Taps, held buttons, stick sweeps and drags made into scenario lines.
    private static List<Ev> compact(List<Ev> evs) {
        for (int i = 0; i < evs.size(); i++) {
            Ev e = evs.get(i);
            if (e.kind != Kind.PAD || e.removed) continue;
            Ev next = null;
            for (int j = i + 1; j < evs.size(); j++) {
                Ev o = evs.get(j);
                if (o.kind == Kind.PAD && o.pad == e.pad && o.key.equals(e.key)) {
                    next = o;
                    break;
                }
            }
            String name = "g" + e.pad + "." + longName(e.key);
            if (isButton(e.key)) {
                if (e.value == 0) {
                    e.line = "release " + name;
                } else if (next != null && next.t - e.t < TAP_SEC) {
                    e.line = "press " + name;     // a tap: one line
                    next.removed = true;
                } else {
                    e.line = "hold " + name;
                }
            } else {
                if (next != null && next.t - e.t < STICK_MERGE_SEC) {
                    e.removed = true;               // a sweep: only where the stick ended
                    continue;
                }
                e.line = e.value == 0 ? "release " + name : "hold " + name + " " + num(e.value);
            }
        }
        // A drag sends many "pose" commands: only where the robot was dropped counts.
        for (int i = 0; i < evs.size(); i++) {
            Ev e = evs.get(i);
            if (e.kind != Kind.POSE || e.removed) continue;
            for (int j = i + 1; j < evs.size(); j++) {
                Ev o = evs.get(j);
                if (o.removed) continue;
                if (o.kind == Kind.POSE) e.removed = true;
                break;
            }
        }
        List<Ev> out = new ArrayList<>();
        for (Ev e : evs) if (!e.removed) out.add(e);
        // Letting go of three or more inputs of a gamepad at once: "release g1.all".
        List<Ev> grouped = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            Ev e = out.get(i);
            int j = i;
            while (j + 1 < out.size() && isRelease(out.get(j + 1), e)) j++;
            if (isRelease(e, e) && j - i + 1 >= 3) {
                e.line = "release g" + e.pad + ".all";
                grouped.add(e);
                i = j;
            } else {
                grouped.add(e);
            }
        }
        return grouped;
    }

    private static boolean isRelease(Ev e, Ev like) {
        return e.kind == Kind.PAD && e.line.startsWith("release ") && e.pad == like.pad && Math.abs(e.t - like.t) < 1e-9;
    }

    private static boolean isButton(String key) {
        for (int i = 0; i < BUTTONS; i++) if (INPUTS[i][0].equals(key)) return true;
        return false;
    }

    static String longName(String key) {
        for (String[] in : INPUTS) if (in[0].equals(key)) return in[1];
        return key;
    }

    // The checks, from what the simulator sees now. Goal checks are ticked; details are offered unticked.
    private void suggest(Recording rec, Ev finalStop, double within) {
        SimRobot r = sim.robot;
        SimDriverStation ds = sim.ds;
        boolean auto = "Autonomous".equals(kind);
        boolean changing = false;   // a goal check that waits for something to happen
        if (finalStop != null) {
            Check c = Check.words("phase", auto ? "The autonomous finishes by itself" : "The OpMode stops by itself", "goal", true, "phase STOPPED");
            c.within = within;
            rec.checks.add(c);
            changing = true;
        } else if (ds.phase() == SimDriverStation.Phase.RUNNING) {
            rec.checks.add(Check.words("running", "Still running at the end (it did not stop by itself)", "goal", true, "phase RUNNING"));
        }
        // the pieces may still be flying when an autonomous ends: a little time for them to land
        double pieceWait = finalStop != null ? 3 : within;
        // The autonomous report: each step that said "done".
        List<String> report = com.crossbotics.ftchelper.match.MatchMemory.lastReport();
        if (auto && finalStop != null && !report.isEmpty()) {
            int lead = 0;
            while (lead < report.size() && (report.get(lead).startsWith("Start:") || report.get(lead).startsWith("Start delay:"))) lead++;
            for (int i = lead; i < report.size(); i++) {
                String line = report.get(i);
                int colon = line.indexOf(": ");
                String word = colon < 0 ? line : line.substring(colon + 2);
                String name = colon < 0 ? line : line.substring(0, colon);
                int n = i - lead + 1;
                if (word.toLowerCase(Locale.US).startsWith("done")) {
                    rec.checks.add(Check.words("step" + n, "Step " + n + " (" + name + ") is done", "goal", true, "step " + n + " done"));
                } else {
                    rec.notes.add("Step " + n + " (" + name + ") said \"" + word + "\": not offered as a check. Is that what you wanted?");
                }
            }
        }
        if (r.pieces != null) {
            int launched = r.pieces.launched(), stored = r.pieces.storedCount(), scored = sum(r.pieces.scored());
            Integer clean = r.pieces.verdictCounts().get("clean");
            boolean pieceGoal = false;   // the robot did something with the pieces
            if (launched > launched0) pieceGoal |= addCount(rec, "launched", "Pieces shot: at least " + launched, "launched", launched, pieceWait, true);
            if (scored > scored0) pieceGoal |= addCount(rec, "scored", "Pieces scored in goals: at least " + scored, "scored \"all\"", scored, pieceWait, true);
            if (clean != null && clean > clean0) pieceGoal |= addCount(rec, "clean", "Clean shots into a CELL: at least " + clean, "clean", clean, pieceWait, true);
            boolean storedGoal = stored > stored0;   // it picked pieces up (fewer: it shot them, checked above)
            if (stored > 0) pieceGoal |= addCount(rec, "stored", "Pieces in the robot: at least " + stored, "stored", stored, pieceWait, storedGoal);
            changing |= pieceGoal;
            com.crossbotics.ftchelper.match.Alliance a = com.crossbotics.ftchelper.match.MatchMemory.chosen();
            if (a != null) {
                String side = a.name().toLowerCase(Locale.US);
                double pts;
                synchronized (r) {
                    SimScore.Side s = r.pieces.score.sides().get(side);
                    pts = s == null ? Double.NaN : s.total();
                }
                Double before = points0.get(side);
                if (!Double.isNaN(pts) && pts > 0) {
                    // a goal only when the robot did something with the pieces (the match clock alone changes points:
                    // pieces placed in a CELL score once the match runs)
                    boolean goal = pieceGoal && (before == null || pts > before);
                    changing |= addCount(rec, "points", a.name() + "'s points: at least " + num(pts), "points \"" + side + "\"", pts, pieceWait, goal);
                }
            }
        }
        if (ds.error().isEmpty()) rec.checks.add(Check.words("error", "No error on the Driver Station", "goal", true, "error none"));
        else rec.notes.add("The Driver Station shows an error now: " + ds.error().split("\n")[0]);
        String bumped = r.bumpedInto;
        if (bumped.isEmpty()) rec.checks.add(Check.words("bumped", "Not pushing against a wall or a field element at the end", "goal", true, "bumped none"));
        // Where the robot ended: the goal of a driving test (ticked when nothing else changed), else a detail.
        double x, y, h;
        synchronized (r) {
            x = r.x;
            y = r.y;
            h = r.headingDeg;
        }
        boolean poseGoal = !changing;
        String g = poseGoal ? "goal" : "detail";
        Check cx = Check.number("x", "Ends at x " + num1(x) + " in (field, seen from the red wall), give or take 3 in", g, poseGoal, "robot x", "=", num1(x), "3");
        Check cy = Check.number("y", "Ends at y " + num1(y) + " in, give or take 3 in", g, poseGoal, "robot y", "=", num1(y), "3");
        boolean wraps = Math.abs(h) > 170;
        Check ch = Check.number("heading", "Ends facing " + Math.round(h) + " degrees, give or take 5" + (wraps ? " (near 180: it can read -180 or 180, so not ticked)" : ""),
                g, poseGoal && !wraps, "robot heading", "=", String.valueOf(Math.round(h)), "5");
        for (Check c : new Check[] {cx, cy, ch}) {
            c.within = finalStop != null ? 0 : within;
            rec.checks.add(c);
        }
        if (ds.phase() == SimDriverStation.Phase.RUNNING) {
            double run = ds.runSeconds();
            rec.checks.add(Check.number("time", "Time since START under " + num(waitFor(run)) + " s", "detail", false, "time", "<", num(waitFor(run)), ""));
        }
        // Screen lines: only their steady part (numbers change from run to run, so they are cut off).
        int n = 0;
        java.util.Set<String> seenText = new java.util.HashSet<>();
        for (String line : ds.screen.screen()) {
            String steady = steady(line);
            if (steady.isEmpty() || !seenText.add(steady)) continue;
            Check c = Check.words("screen" + n, "The screen shows: " + steady, "detail", false, null);
            c.text = steady;
            rec.checks.add(c);
            if (++n >= 8) break;
        }
    }

    private static boolean addCount(Recording rec, String id, String label, String subject, double v, double within, boolean goal) {
        Check c = Check.number(id, label, goal ? "goal" : "detail", goal, subject, ">=", num(v), "");
        c.within = within;
        rec.checks.add(c);
        return goal;
    }

    /**
     * The part of a screen line that stays the same from run to run: up to the first number ("Lift : 1203
     * ticks" -> nothing useful; "Intake : stopped" -> all of it). Empty when only a label would be left.
     */
    static String steady(String line) {
        String s = line.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[-+]?[0-9]").matcher(s);
        if (m.find()) s = s.substring(0, m.start()).trim();
        // only a label left ("Time :"), or very short: says nothing
        if (s.endsWith(":") || s.endsWith("(") || s.endsWith("=") || s.length() < 6) return "";
        if (s.startsWith("---") || s.contains("\"")) return "";
        return s;
    }

    // ------------------------------------------------------------------ small helpers

    private static int sum(Map<String, Integer> m) {
        int n = 0;
        for (int v : m.values()) n += v;
        return n;
    }

    private static boolean on(String v) {
        return v != null && (v.equals("1") || v.equals("true"));
    }

    // A stick or trigger, in steps of 0.25 (a hand never holds a stick at exactly 0.53 twice); near 0 is 0.
    static double stick(String v) {
        if (v == null) return 0;
        double d;
        try {
            d = Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return 0;
        }
        if (Double.isNaN(d) || Math.abs(d) < 0.12) return 0;
        return Math.max(-1, Math.min(1, Math.round(d * 4) / 4.0));
    }

    private static double d(Map<String, String> f, String key) {
        try {
            double v = Double.parseDouble(f.getOrDefault(key, "0"));
            return Double.isNaN(v) || Double.isInfinite(v) ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A number for a scenario line: at most 2 decimals, no trailing zeros ("12", "0.5", "-56.25"). */
    static String num(double v) {
        double r = Math.round(v * 100) / 100.0;
        if (r == 0) return "0";
        if (r == Math.rint(r)) return String.valueOf((long) r);
        String s = String.format(Locale.US, "%.2f", r);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String num1(double v) {
        double r = Math.round(v * 10) / 10.0;
        if (r == 0) return "0";
        return r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.US, "%.1f", r);
    }

    /** Text in quotes for a scenario line (a quote inside becomes \"). */
    static String quote(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    // ------------------------------------------------------------------ JSON for the page

    /** The recording for the page: its steps, the checks (with their parts to edit) and the notes. */
    static String json(Recording rec) {
        StringBuilder b = new StringBuilder("{\"opMode\":");
        SimJson.string(b, rec.opMode).append(",\"kind\":");
        SimJson.string(b, rec.kind).append(",\"date\":");
        SimJson.string(b, rec.date).append(",\"tailWait\":").append(num(rec.tailWait)).append(",\"steps\":[");
        for (int i = 0; i < rec.steps.size(); i++) SimJson.string(b.append(i > 0 ? "," : ""), rec.steps.get(i));
        b.append("],\"notes\":[");
        for (int i = 0; i < rec.notes.size(); i++) SimJson.string(b.append(i > 0 ? "," : ""), rec.notes.get(i));
        b.append("],\"checks\":[");
        for (int i = 0; i < rec.checks.size(); i++) {
            Check c = rec.checks.get(i);
            b.append(i > 0 ? "," : "").append("{\"id\":");
            SimJson.string(b, c.id).append(",\"label\":");
            SimJson.string(b, c.label).append(",\"group\":");
            SimJson.string(b, c.group).append(",\"on\":").append(c.on).append(",\"within\":").append(num(c.within));
            if (c.text != null) SimJson.string(b.append(",\"text\":"), c.text);
            else if (c.words != null) SimJson.string(b.append(",\"words\":"), c.words);
            else {
                SimJson.string(b.append(",\"subject\":"), c.subject);
                SimJson.string(b.append(",\"op\":"), c.op);
                SimJson.string(b.append(",\"value\":"), c.value);
                SimJson.string(b.append(",\"tol\":"), c.tol);
            }
            SimJson.string(b.append(",\"line\":"), c.line()).append('}');
        }
        return b.append("]}").toString();
    }
}
