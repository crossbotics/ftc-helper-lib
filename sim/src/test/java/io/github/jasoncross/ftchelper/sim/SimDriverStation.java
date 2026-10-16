package io.github.jasoncross.ftchelper.sim;

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
    private volatile boolean autoStopping = false;
    // STOP from the page, the timer or the OpMode itself runs here, one at a time (never on the
    // OpMode's own thread, which STOP waits for).
    private final java.util.concurrent.ExecutorService commands = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ds-commands");
        t.setDaemon(true);
        return t;
    });

    SimDriverStation(SimRobot robot) {
        this.robot = robot;
        findOpModes();
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
                // With the team's TeamCode built in (-PteamCode / inside a robot project), list only it.
                boolean team = rel.startsWith("org/firstinspires/ftc/teamcode/");
                boolean ours = Boolean.getBoolean("sim.teamOnly") ? team : (team || rel.startsWith("io/github/jasoncross/"));
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
        for (Entry e : opModes) if (e.name.equals(name)) return e;
        return null;
    }

    Phase phase() { return phase; }
    Entry current() { return current; }
    String error() { return error; }

    /** Seconds since START (0 before). */
    double runSeconds() {
        return phase == Phase.RUNNING ? (System.nanoTime() - startedAt) / 1e9 : 0;
    }

    /** INIT: makes the OpMode, gives it the hardware, gamepads and screen, and runs runOpMode(). */
    synchronized void init(String name) {
        if (phase == Phase.INIT || phase == Phase.RUNNING) stop();
        Thread old = thread;
        if (old != null && old.isAlive()) {
            error = "The last OpMode is still running: it ignores STOP (a loop without opModeIsActive()?). "
                    + "Its motor commands are blocked. Restart the simulator to run another OpMode.";
            return;
        }
        Entry e = find(name);
        if (e == null) throw new IllegalArgumentException("No OpMode called " + name);
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
            // Like the FTC app at INIT: motor directions, modes and servo ranges back to their defaults.
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
            phase = Phase.STOPPED;
        }
    }

    // An iterative OpMode, the way the FTC app runs it: init(), init_loop() until START, start(), loop()
    // until STOP, stop(); the telemetry is sent after each call.
    private void runIterative(OpMode o) {
        try {
            o.init();
            o.telemetry.update();
            while (!SimAccess.isStarted(o) && !SimAccess.isStopRequested(o)) {
                o.init_loop();
                o.telemetry.update();
                Thread.sleep(5);
            }
            if (SimAccess.isStarted(o) && !SimAccess.isStopRequested(o)) {
                o.start();
                o.telemetry.update();
                while (!SimAccess.isStopRequested(o)) {
                    o.loop();
                    o.telemetry.update();
                    Thread.sleep(5);
                }
            }
            o.stop();
        } catch (InterruptedException ignored) {
            try { o.stop(); } catch (RuntimeException e) { /* stopping anyway */ }
        } catch (Throwable t) {
            if (!t.getClass().getName().endsWith("ForceStopException")) error = firstLines(t);
        } finally {
            if (op == o) {
                robot.allOff();
                phase = Phase.STOPPED;
            }
        }
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
            error = b.toString().trim();
        } finally {
            // Only the CURRENT OpMode may switch things off (an old one that ended late must not
            // switch off the next one's motors).
            if (op == o) {
                robot.allOff();
                phase = Phase.STOPPED;
            }
        }
    }

    /** START. */
    synchronized void start() {
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
        phase = Phase.STOPPED;
    }

    /** A problem in the simulator itself (not the OpMode): shown like an OpMode error. */
    void physicsError(String text) {
        if (error.isEmpty()) error = text;
    }

    /** STOP without waiting here (for the page and the timer). */
    void stopAsync() {
        commands.submit(this::stop);
    }

    /** Called often by the simulator: the autonomous timer, and gamepads let go when the page goes quiet. */
    void tick() {
        OpMode o = op;
        if (o != null && (phase == Phase.INIT || phase == Phase.RUNNING)) SimAccess.eventLoop(o);
        // 2.5 s: a hidden browser tab only sends once a second.
        pad1.releaseIfSilent(2.5);
        pad2.releaseIfSilent(2.5);
        Entry e = current;
        if (autoTimer && !autoStopping && phase == Phase.RUNNING && e != null && e.kind.equals("Autonomous")
                && runSeconds() >= 30) {
            autoStopping = true;
            commands.submit(() -> {
                stop();
                autoStopping = false;
            });
        }
    }
}
