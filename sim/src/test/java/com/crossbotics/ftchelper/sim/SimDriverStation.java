package com.crossbotics.ftchelper.sim;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.SimAccess;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The pretend Driver Station: the OpMode list, INIT / START / STOP, the telemetry screen, and the
 * autonomous 30 s timer. It runs the OpMode's runOpMode() on its own thread, like the FTC app.
 */
final class SimDriverStation {
    /** One OpMode in the list. */
    static final class Entry {
        final String name, group, kind;   // kind: "TeleOp" or "Autonomous"
        final boolean disabled;            // @Disabled: hidden on a real robot, shown here (marked)
        final Class<? extends OpMode> type;

        Entry(String name, String group, String kind, boolean disabled, Class<? extends OpMode> type) {
            this.name = name;
            this.group = group;
            this.kind = kind;
            this.disabled = disabled;
            this.type = type;
        }
    }

    enum Phase { IDLE, INIT, RUNNING, STOPPED }

    final List<Entry> opModes = new ArrayList<>();
    // Like the real Driver Station: when an autonomous ends, its preselectTeleOp is chosen next. The
    // page selects "next" each time nextCount goes up.
    volatile String next = "";
    volatile int nextCount = 0;
    final SimRobot robot;
    final SimGamepad pad1 = new SimGamepad(), pad2 = new SimGamepad();
    final SimTelemetry screen = new SimTelemetry();

    private volatile Phase phase = Phase.IDLE;
    private volatile Entry current;
    private volatile OpMode op;
    private volatile Thread thread;
    private volatile String error = "";
    private volatile long startedAt = 0;
    /** Autonomous stops by itself after 30 s, like the Driver Station's timer. */
    volatile boolean autoTimer = true;
    /**
     * TeleOp stops by itself after 2:00, like the real Driver Station with its TeleOp timer turned on
     * (the clock icon next to the OpMode): on here, as in a match. Off: it runs until STOP, the clock
     * counting up.
     */
    volatile boolean teleTimer = true;
    /** TeleOp's length with the timer on, seconds (FTC: 2:00; tests make it shorter). */
    volatile double teleOpSec = 120;
    private volatile boolean autoStopping = false;
    // STOP from the page, the timer or the OpMode itself runs here, one at a time (never on the
    // OpMode's own thread, which STOP waits for).
    private final java.util.concurrent.ExecutorService commands = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ds-commands");
        t.setDaemon(true);
        return t;
    });

    // The robot file's stand-ins for MEASURE numbers (tunable.Section.field = VALUE): put in at INIT, taken
    // out when the OpMode ends (SimTunables.StandIns). Its own lock: the OpMode's thread takes them out.
    private final SimTunables.StandIns standIns = new SimTunables.StandIns();
    private final Object standInLock = new Object();
    /** Fields a scenario file's "set tunable" set: they get no stand-in (set tunable wins). */
    final java.util.Set<java.lang.reflect.Field> noStandIn = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile String standInNote = "";

    /**
     * The note about the stand-ins this INIT used, e.g. "Tunables.IntakeDeploy.deployed = MEASURE: the
     * simulator uses the stand-in 0.5 from the robot file (the real robot stops here)"; "" = none.
     */
    String standInNote() {
        return standInNote;
    }

    // At INIT: the stand-ins for the fields that are MEASURE now (each INIT reads the robot file's lines again).
    private void putStandIns() {
        synchronized (standInLock) {
            standIns.restore();
            standIns.apply(scope, robot.standIns, noStandIn);
            standInNote = String.join("\n", standIns.notes);
        }
    }

    /**
     * Takes the robot file's stand-ins out again (the fields are MEASURE again): when the OpMode ends, and
     * before a test on the page reads the Tunables to put them back afterwards. The note stays until the next INIT.
     */
    void takeOutStandIns() {
        synchronized (standInLock) {
            standIns.restore();
        }
    }

    /** Which OpModes to list: the library's (examples, starter kit) or the team's (TeamCode). */
    enum Scope { LIBRARY, TEAM }
    final Scope scope;

    SimDriverStation(SimRobot robot) {
        this(robot, defaultScope());
    }

    /** The team's OpModes when the simulator runs with TeamCode (-Dsim.teamOnly=true), else the library's. */
    static Scope defaultScope() {
        return Boolean.getBoolean("sim.teamOnly") ? Scope.TEAM : Scope.LIBRARY;
    }

    SimDriverStation(SimRobot robot, Scope scope) {
        this.robot = robot;
        this.scope = scope;
        // An INIT error stops the OpMode with the error, as before: the simulator's own panel shows it
        // (on a robot, the library shows it on the Driver Station's lines and waits for STOP).
        com.crossbotics.ftchelper.core.InitStop.throwInsteadOfShowing(true);
        findOpModes();
        // The auto builder's own autonomous (it runs the page's plan with the team's code): always listed.
        opModes.add(new Entry(BuiltAuto.NAME, "Builder", "Autonomous", false, BuiltAuto.class));
        // "Tune and check" is the library's own OpMode (not in TeamCode): on the robot the SDK lists it too.
        if (scope == Scope.TEAM) {
            opModes.add(new Entry(com.crossbotics.ftchelper.finders.TuneAndCheckOpMode.NAME, "Finders", "TeleOp", false,
                    com.crossbotics.ftchelper.finders.TuneAndCheckOpMode.class));
        }
    }

    // Loops run by an iterative OpMode (a LinearOpMode's loops are counted by its telemetry updates).
    private volatile int iterations = 0;

    /** A number that grows as the OpMode runs: its screen updates plus its iterative loops. */
    int activity() {
        return screen.updates() + iterations;
    }

    // Every class with @TeleOp or @Autonomous in the examples, the starter kit and the simulator's own.
    @SuppressWarnings("unchecked")
    private void findOpModes() {
        List<String> classFiles = new ArrayList<>();
        for (String part : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File where = new File(part);
            if (where.isDirectory()) {
                List<File> files = new ArrayList<>();
                collect(where, files);
                for (File f : files) classFiles.add(where.toURI().relativize(f.toURI()).getPath());
            } else if (part.endsWith(".jar") && where.isFile()) {
                try (java.util.jar.JarFile jar = new java.util.jar.JarFile(where)) {
                    java.util.Enumeration<java.util.jar.JarEntry> en = jar.entries();
                    while (en.hasMoreElements()) {
                        String n = en.nextElement().getName();
                        if (n.endsWith(".class")) classFiles.add(n);
                    }
                } catch (java.io.IOException ignored) {
                    // not readable: skip it
                }
            }
        }
        for (String rel : classFiles) {
            {
                // The library's examples and starter kit, the simulator's own, and team code copied in
                // with its TeamCode package.
                // The team's TeamCode, or the library's examples and starter kit: never both, so two
                // OpModes with the same name ("Main TeleOp") cannot be mixed up.
                boolean ours = scope == Scope.TEAM ? rel.startsWith("org/firstinspires/ftc/teamcode/")
                        : rel.startsWith("com/crossbotics/");
                if (!ours || rel.contains("$")) continue;
                String className = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
                try {
                    Class<?> c = Class.forName(className, false, getClass().getClassLoader());
                    if (!OpMode.class.isAssignableFrom(c) || Modifier.isAbstract(c.getModifiers())) continue;
                    TeleOp tele = c.getAnnotation(TeleOp.class);
                    Autonomous auto = c.getAnnotation(Autonomous.class);
                    if (tele == null && auto == null) continue;
                    String name = tele != null ? tele.name() : auto.name();
                    String group = tele != null ? tele.group() : auto.group();
                    if (name.isEmpty()) name = c.getSimpleName();
                    opModes.add(new Entry(name, group, tele != null ? "TeleOp" : "Autonomous",
                            c.isAnnotationPresent(Disabled.class), (Class<? extends OpMode>) c));
                } catch (Throwable ignored) {
                    // a class that cannot load here (it needs Android): leave it out
                }
            }
        }
        Collections.sort(opModes, (a, b) -> (a.group + "/" + a.name).compareToIgnoreCase(b.group + "/" + b.name));
    }

    private static void collect(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".class")) out.add(f);
        }
    }

    /** Adds an OpMode that has no @TeleOp (for the simulator's own tests). */
    void register(String name, String kind, Class<? extends OpMode> type) {
        opModes.add(new Entry(name, "Tests", kind, false, type));
    }

    Entry find(String name) {
        Entry found = null;
        for (Entry e : opModes) {
            if (!e.name.equals(name)) continue;
            if (found != null) throw new IllegalArgumentException("Two OpModes are named \"" + name + "\" ("
                    + found.type.getName() + " and " + e.type.getName() + "): give one another name");
            found = e;
        }
        return found;
    }

    Phase phase() { return phase; }
    Entry current() { return current; }
    /** The OpMode object running now (null before the first INIT). */
    OpMode opMode() { return op; }
    String error() { return error; }

    /** Seconds since START (0 before). */
    double runSeconds() {
        return phase == Phase.RUNNING ? (System.nanoTime() - startedAt) / 1e9 : 0;
    }

    /** INIT: makes the OpMode, gives it the hardware, gamepads and screen, and runs runOpMode(). */
    // How many times INIT was pressed (the simulator places the robot once per INIT).
    volatile int initCount = 0;

    synchronized void init(String name) {
        init(name, null);
    }

    /**
     * INIT with an alliance already chosen in its menu (the auto builder's Run): the menu starts on it, as
     * if the drivers had pressed X or B. null: the menu's own start (BLUE, or this match's remembered one).
     */
    synchronized void init(String name, com.crossbotics.ftchelper.match.Alliance alliance) {
        initCount++;
        // A new program: its own alliance and start spot (not the last program's).
        com.crossbotics.ftchelper.match.MatchMemory.setChosen(null);
        com.crossbotics.ftchelper.match.MatchMemory.setPlacement(null, null);
        // The INIT menu's alliance line starts on the remembered alliance (InitMenu.alliance()).
        if (alliance != null) com.crossbotics.ftchelper.match.MatchMemory.remember(alliance, null);
        if (phase == Phase.INIT || phase == Phase.RUNNING) stop();
        Thread old = thread;
        if (old != null && old.isAlive()) {
            error = "The last OpMode is still running: it ignores STOP (a loop without opModeIsActive()?). "
                    + "Its motor commands are blocked. Restart the simulator to run another OpMode.";
            return;
        }
        Entry e = find(name);
        if (e == null) throw new IllegalArgumentException("No OpMode called " + name);
        // A MEASURE number would stop INIT: the robot file's stand-in goes in its place (simulator only).
        putStandIns();
        try {
            OpMode o = e.type.getDeclaredConstructor().newInstance();
            o.hardwareMap = robot.hardwareMap();
            o.telemetry = screen.telemetry;
            o.gamepad1 = pad1.gamepad;
            o.gamepad2 = pad2.gamepad;
            o.resetRuntime();
            // An OpMode may stop itself (requestOpModeStop): handle it like STOP, on another thread.
            SimAccess.installServices(o, () -> commands.submit(() -> {
                if (op == o) stop();   // a late request from an old OpMode must not stop the next one
            }));
            // Like the FTC app at INIT: motor directions, modes and servo ranges back to their defaults,
            // and no camera left open by the last OpMode.
            robot.closeCameras();
            robot.resetConfig();
            screen.clearScreen();
            error = "";
            current = e;
            op = o;
            phase = Phase.INIT;
            thread = o instanceof LinearOpMode
                    ? new Thread(() -> run((LinearOpMode) o), "OpMode " + e.name)
                    : new Thread(() -> runIterative(o), "OpMode " + e.name);
            thread.setDaemon(true);
            thread.start();
        } catch (ReflectiveOperationException ex) {
            error = "Could not make " + name + ": " + ex;
            takeOutStandIns();
            phase = Phase.STOPPED;
        }
    }

    // An iterative OpMode, the way the FTC app runs it: init(), init_loop() until START, start(), loop()
    // until STOP, stop(); the telemetry is sent after each call.
    private void runIterative(OpMode o) {
        try {
            o.init();
            screen.updateIfDirty();
            while (!SimAccess.isStarted(o) && !SimAccess.isStopRequested(o)) {
                o.init_loop();
                screen.updateIfDirty();
                iterations++;
                Thread.sleep(5);
            }
            if (SimAccess.isStarted(o) && !SimAccess.isStopRequested(o)) {
                o.start();
                screen.updateIfDirty();
                while (!SimAccess.isStopRequested(o)) {
                    o.loop();
                    screen.updateIfDirty();
                    iterations++;
                    Thread.sleep(5);
                }
            }
            o.stop();
        } catch (InterruptedException ignored) {
            try { o.stop(); } catch (RuntimeException e) { /* stopping anyway */ }
        } catch (Throwable t) {
            if (!t.getClass().getName().endsWith("ForceStopException")) error = withSimHint(firstLines(t));
        } finally {
            if (op == o) {
                robot.allOff();
                robot.closeCameras();
                takeOutStandIns();
                phase = Phase.STOPPED;
                preselectNext(current);
            }
        }
    }

    /** What to do in the simulator when the code asks for a device the robot file does not have. */
    static final String CONFIG_HINT = "In the simulator, the robot configuration is the robot file (simulator/robot.properties), "
            + "made from Robot.java at each start. A device your code asks for itself (hardwareMap.get, not in Robot.java) "
            + "needs its own line there: motor.NAME =, servo.NAME =, crservo.NAME =... Started with -Probot=FILE or "
            + "-PnoFromRobot? That file is used as it is: start with the Simulator button instead. "
            + "More: docs/guides/simulator.md, \"The robot file\".";

    // The error, with CONFIG_HINT after its first line when it is a device the robot file does not have
    // ("Check ... the active configuration on the Driver Station" does not apply in the simulator).
    static String withSimHint(String error) {
        if (error == null) return "";
        boolean missingDevice = error.contains("active configuration on the Driver Station")
                || error.contains("Unable to find a hardware device")
                || error.contains("in the active robot configuration");
        if (!missingDevice || error.contains(CONFIG_HINT)) return error;
        int nl = error.indexOf('\n');
        return nl < 0 ? error + "\n" + CONFIG_HINT : error.substring(0, nl) + "\n" + CONFIG_HINT + error.substring(nl);
    }

    private static String firstLines(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split("\n");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(8, lines.length); i++) b.append(lines[i].trim()).append('\n');
        return b.toString().trim();
    }

    private void run(LinearOpMode o) {
        try {
            o.runOpMode();
        } catch (InterruptedException ignored) {
            // stopped
        } catch (Throwable t) {
            // terminateOpModeNow(): a clean stop on a robot, not an error.
            if (t.getClass().getName().endsWith("ForceStopException")) return;
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String trace = sw.toString();
            // Keep the first lines: the error and where it happened.
            String[] lines = trace.split("\n");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < Math.min(8, lines.length); i++) b.append(lines[i].trim()).append('\n');
            error = withSimHint(b.toString().trim());
        } finally {
            // Only the CURRENT OpMode may switch things off (an old one that ended late must not
            // switch off the next one's motors).
            if (op == o) {
                robot.allOff();
                robot.closeCameras();
                takeOutStandIns();
                phase = Phase.STOPPED;
                preselectNext(current);
            }
        }
    }

    // An autonomous that ended: choose its preselectTeleOp next (if that TeleOp is in the list). Not after an
    // error: the team fixes it and runs the same autonomous again, so it stays chosen.
    private void preselectNext(Entry e) {
        if (e == null || !"Autonomous".equals(e.kind) || !error.isEmpty()) return;
        Autonomous a = e.type.getAnnotation(Autonomous.class);
        String teleOp = a == null ? "" : a.preselectTeleOp();
        if (teleOp.isEmpty()) return;
        for (Entry x : opModes) {
            if (x.name.equals(teleOp)) {
                next = teleOp;
                nextCount++;
                return;
            }
        }
    }

    // Run just before START (set by SimEngine): puts the robot on the INIT start spot, so a START pressed
    // right after INIT (a scenario, a quick click) never starts from the spot before. Run WITHOUT this
    // object's lock: it takes the robot's lock, and the physics takes the robot's lock before this one.
    volatile Runnable beforeStart = null;

    /** START. */
    void start() {
        Runnable r = beforeStart;
        if (r != null) r.run();
        startNow();
    }

    private synchronized void startNow() {
        if (phase != Phase.INIT || op == null) return;
        startedAt = System.nanoTime();
        op.resetRuntime();
        phase = Phase.RUNNING;
        SimAccess.start(op);
    }

    /** STOP: asks the OpMode to end, waits up to 2 s, then interrupts it. Motors off either way. */
    synchronized void stop() {
        OpMode o = op;
        Thread t = thread;
        if (o == null) return;
        SimAccess.stop(o);
        // Like the FTC app: a sleep() in the OpMode ends at once (its thread is interrupted right away).
        if (t != null) t.interrupt();
        if (t != null) {
            try {
                t.join(2000);
                if (t.isAlive()) {
                    t.interrupt();
                    t.join(1000);
                    if (t.isAlive()) {
                        // Stuck: from now on its hardware commands are ignored, so it cannot move anything.
                        SimRobot.block(t);
                        error = "The OpMode did not stop within 3 s (a loop that never checks opModeIsActive()?). "
                                + "Its motor and servo commands are now ignored.";
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        robot.allOff();
        robot.closeCameras();
        takeOutStandIns();
        phase = Phase.STOPPED;
    }

    /** Ends the command thread (when the simulator shuts down). */
    void close() {
        commands.shutdownNow();
    }

    /** A problem in the simulator itself (not the OpMode): shown like an OpMode error. */
    void physicsError(String text) {
        if (error.isEmpty()) error = text;
    }

    /** STOP without waiting here (for the page and the timer). */
    void stopAsync() {
        // Only the OpMode that was running when STOP was pressed: a quick INIT after it must not be stopped.
        OpMode o = op;
        commands.submit(() -> { if (op == o) stop(); });
    }

    /** Called often by the simulator: the autonomous and TeleOp timers, and gamepads let go when the page goes quiet. */
    void tick() {
        OpMode o = op;
        if (o != null && (phase == Phase.INIT || phase == Phase.RUNNING)) SimAccess.eventLoop(o);
        // 2.5 s: a hidden browser tab only sends once a second.
        pad1.releaseIfSilent(2.5);
        pad2.releaseIfSilent(2.5);
        Entry e = current;
        boolean autoEnd = autoTimer && e != null && e.kind.equals("Autonomous") && runSeconds() >= 30;
        boolean teleEnd = teleTimer && e != null && e.kind.equals("TeleOp") && runSeconds() >= teleOpSec;
        if (!autoStopping && phase == Phase.RUNNING && (autoEnd || teleEnd)) {
            autoStopping = true;
            OpMode timed = o;
            commands.submit(() -> {
                if (op == timed) stop();
                autoStopping = false;
            });
        }
    }
}
