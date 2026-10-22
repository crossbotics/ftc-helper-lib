package com.crossbotics.ftchelper.sim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A very small web server for the simulator page. It only answers this computer, unless started
 * with --lan (then phones and tablets on the same WiFi can join as a gamepad).
 *
 * GET /             the page
 * GET /state        everything the page shows, as JSON
 * GET /stream       the same, kept coming (server-sent events): "f" where everything is, 50 a second
 *                   (SimView.frame); "s" the state without the OpMode list and the game, 10 a second;
 *                   "g" the game (score, clock, FLOWERs, shot verdicts, fouls) when it changes, at most 5 a second
 * GET /opmodes      the OpMode list (the stream sends only its key)
 * GET /seemap?cam=N&any=0|1&step=4  where camera N can see tags (SimView.seeMap)
 * GET /field        the season's field file
 * GET /field-model  the 3D field model named by field.model in the robot file (404 if none)
 * GET /robot-model?i=N  CAD mesh N of the robot (float32 triangles, robot frame inches)
 * GET /three/...    the 3D library (three.js), so the page works without the internet
 * GET /simview.js   the page's drawing helpers (gliding between updates, ball texture, labels)
 * POST /pad/1, /pad/2   a gamepad's buttons and sticks ("a=1&lx=0.5...")
 * POST /cmd         a Driver Station or panel command ("action=init&opmode=...")
 * GET /auto         the auto builder: the Robot classes it can run a plan with, and its best guess
 * POST /auto        the auto builder's Run: {"program": {...}, "alliance": "RED"} (BuiltAuto.Program); INITs the
 *                   Built Auto with that plan and presses START once the robot is on its start spot
 * GET /builder/...  the auto builder's page code (ES modules: builder.js and the files it imports)
 * GET /tests.js     the test recorder's page code
 * GET /tests        the tests: where they are saved (the robot project's simulator/scenarios), their names
 * GET /tests/read?name=N   one test's text (only from that folder, by a plain name)
 * POST /tests/save  name, text, replace=0|1: writes simulator/scenarios/NAME.sim
 * POST /tests/record  action=start&from=init|now, action=stop (answers the recording: steps, checks), action=cancel
 * POST /tests/run   name, text, measure=0|1: runs a test on the page; GET /tests/status its report; POST /tests/stop
 */
final class SimServer {
    private final SimEngine sim;
    private final ServerSocket socket;
    // At most 16 connections at once and 64 waiting; more are turned away. A page keeps a few connections open
    // (keep-alive: its gamepads send about 50 updates a second on them) and one stream (its own thread).
    private final java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(
            16, 16, 30, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(64), r -> {
                Thread t = new Thread(r, "sim-web");
                t.setDaemon(true);
                return t;
            });
    private static final int MAX_BODY = 16 * 1024;
    // An auto builder plan can be longer than a gamepad update (a long list of steps).
    private static final int MAX_PLAN = 128 * 1024;
    // Streams open now (each has its own thread); more are refused and that page reads /state instead.
    private final java.util.concurrent.atomic.AtomicInteger streams = new java.util.concurrent.atomic.AtomicInteger();
    static final int MAX_STREAMS = 8;
    // How often the stream sends where everything is, and the whole state (milliseconds).
    static final int FRAME_MS = 20, STATE_MS = 100, GAME_MS = 200;
    // A kept-alive connection with nothing to say for this long is closed (frees its thread).
    private static final int IDLE_MS = 5000;
    final boolean lan;
    /** The page's tests: the recorder, the team's scenario files, a test run on the page. */
    final SimTests tests;

    SimServer(SimEngine sim, int port, boolean lan) throws IOException {
        this(sim, port, lan, null);
    }

    /** robotFile: the robot file the simulator runs (tests saved in the robot project name it if it is not the usual one). */
    SimServer(SimEngine sim, int port, boolean lan, java.nio.file.Path robotFile) throws IOException {
        this.sim = sim;
        this.lan = lan;
        this.tests = new SimTests(sim, robotFile, SimTests.teamDirFromProperty());
        this.socket = lan ? new ServerSocket(port, 50) : new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
    }

    int port() {
        return socket.getLocalPort();
    }

    /** Answers requests until the program ends. */
    void serve() {
        while (!socket.isClosed()) {
            try {
                Socket c = socket.accept();
                try {
                    pool.execute(() -> handle(c));
                } catch (java.util.concurrent.RejectedExecutionException busy) {
                    c.close();
                }
            } catch (IOException e) {
                if (socket.isClosed()) return;
            }
        }
    }

    void close() throws IOException {
        socket.close();
        pool.shutdownNow();
    }

    // One connection: requests one after another (HTTP keep-alive, so the gamepads do not open a new
    // connection 50 times a second), until the browser closes it, it says "close", or it sits idle.
    private void handle(Socket c) {
        boolean handedOff = false;
        try {
            c.setSoTimeout(IDLE_MS);
            InputStream in = new java.io.BufferedInputStream(c.getInputStream());
            OutputStream out = new java.io.BufferedOutputStream(c.getOutputStream(), 32 * 1024);
            while (!c.isClosed()) {
                String head = readHead(in);
                if (head == null) return;
                String[] first = head.split("\r\n")[0].split(" ");
                if (first.length < 3) return;
                boolean keepAlive = first[2].equals("HTTP/1.1") && !head.toLowerCase(Locale.US).contains("\r\nconnection: close");
                KEEP.set(keepAlive);
                if (first[1].startsWith("/stream") && first[0].equals("GET")) {
                    handedOff = startStream(c, out);
                    return;
                }
                if (!request(c, in, out, head, first[0], first[1])) return;
                if (!KEEP.get()) return;
            }
        } catch (IOException | RuntimeException ignored) {
            // a closed tab, an idle connection or a bad request: nothing to do
        } finally {
            if (!handedOff) {
                try {
                    c.close();
                } catch (IOException ignored) {
                    // closing is best effort
                }
            }
        }
    }

    // Whether the request being answered on this thread keeps its connection open (each pool thread serves one
    // connection at a time).
    private static final ThreadLocal<Boolean> KEEP = ThreadLocal.withInitial(() -> false);
    private static boolean keepAliveNow() { return KEEP.get(); }

    /** Answers one request. False: stop reading this connection. */
    private boolean request(Socket client, InputStream in, OutputStream out, String head, String method, String path) throws IOException {
        int length = 0;
        for (String line : head.split("\r\n")) {
            if (line.toLowerCase(Locale.US).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
        }
        boolean plan = path.startsWith("/auto") || path.startsWith("/tests");
        if (length < 0 || length > (plan ? MAX_PLAN : MAX_BODY)) return false;   // far too big for a gamepad or a command
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) return false;
            read += n;
        }
        // A request from another web site (its page open in the same browser) is refused.
        if (method.equals("POST") && !sameOrigin(head)) {
            KEEP.set(false);
            send(out, 404, "text/plain", "refused".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        String query = path.contains("?") ? path.substring(path.indexOf('?') + 1) : "";
        path = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
            send(out, 200, "text/html; charset=utf-8", page());
        } else if (method.equals("GET") && path.equals("/field")) {
            send(out, 200, "application/json", sim.robot.field.json.getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("GET") && path.equals("/field-model")) {
            byte[] model = fieldModel();
            if (model == null) send(out, 404, "text/plain", "no field.model in the robot file".getBytes(StandardCharsets.UTF_8));
            else send(out, 200, "model/gltf-binary", model, "max-age=86400");   // the page asks with ?v=<file time>
        } else if (method.equals("GET") && path.equals("/robot-model")) {
            // one CAD mesh: float32 triangles (forward, left, up inches), little-endian
            SimCad cad = sim.robot.shapes.cad;
            int i = -1;
            for (String kv : query.split("&")) {
                if (kv.startsWith("i=")) {
                    try { i = Integer.parseInt(kv.substring(2)); } catch (NumberFormatException ignored) { /* 404 below */ }
                }
            }
            if (cad == null || i < 0 || i >= cad.meshes.size()) send(out, 404, "text/plain", "no such robot model".getBytes(StandardCharsets.UTF_8));
            else send(out, 200, "application/octet-stream", cad.meshes.get(i).bytes());
        } else if (method.equals("GET") && THREE_FILES.contains(path)) {
            send(out, 200, "text/javascript; charset=utf-8", resource("/sim" + path));
        } else if (method.equals("GET") && path.equals("/hit-rate")) {
            // N pretend shots from the robot as it is now: how many go cleanly into an up CELL (SimPieces.hitRate)
            Map<String, String> q = form(query);
            int n = (int) Math.max(1, Math.min(5000, num(q, "n") > 0 ? num(q, "n") : 400));
            SimPieces.HitRate h = sim.robot.pieces.hitRate(n, q.get("piece"), 12345);
            send(out, 200, "application/json", SimScore.json(h).getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("GET") && BUILDER_FILES.contains(path)) {
            send(out, 200, "text/javascript; charset=utf-8", resource("/sim" + path));
        } else if (method.equals("GET") && path.equals("/auto")) {
            send(out, 200, "application/json", builderInfo().getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("POST") && path.equals("/auto")) {
            send(out, 200, "application/json", runPlan(new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("GET") && path.equals("/simview.js")) {
            send(out, 200, "text/javascript; charset=utf-8", resource("/sim/simview.js"));
        } else if (method.equals("GET") && path.equals("/state")) {
            send(out, 200, "application/json", SimJson.state(sim, this).getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("GET") && path.equals("/opmodes")) {
            send(out, 200, "application/json", SimView.opModes(sim.ds).getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("GET") && path.equals("/seemap")) {
            Map<String, String> q = form(query);
            String map = SimView.seeMap(sim.robot, (int) num(q, "cam"), q.containsKey("step") ? num(q, "step") : 4, "1".equals(q.get("any")));
            if (map == null) send(out, 404, "text/plain", "no such camera".getBytes(StandardCharsets.UTF_8));
            else send(out, 200, "application/json", map.getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("POST") && path.startsWith("/pad/")) {
            Map<String, String> form = form(new String(body, StandardCharsets.UTF_8));
            form.put("_from", client.getInetAddress().getHostAddress());
            (path.endsWith("2") ? sim.ds.pad2 : sim.ds.pad1).apply(form);
            // 200 with a tiny body (not 204): browsers then keep the connection without a "canceled" note
            send(out, 200, "text/plain", OK);
        } else if (method.equals("POST") && path.equals("/cmd")) {
            String reply = command(form(new String(body, StandardCharsets.UTF_8)));
            send(out, 200, "text/plain; charset=utf-8", reply.getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("GET") && path.equals("/tests.js")) {
            send(out, 200, "text/javascript; charset=utf-8", resource("/sim/tests.js"));
        } else if (path.equals("/tests") || path.startsWith("/tests/")) {
            // Tests read and write files on this computer: only from this computer (not a phone on the WiFi).
            String reply = client.getInetAddress().isLoopbackAddress() ? tests(method, path, query, body)
                    : "{\"error\":\"tests only from the computer that runs the simulator\"}";
            send(out, 200, "application/json", reply.getBytes(StandardCharsets.UTF_8));
        } else {
            send(out, 404, "text/plain", ("not found: " + path + " " + query).getBytes(StandardCharsets.UTF_8));
        }
        return true;
    }

    private static final byte[] OK = "ok".getBytes(StandardCharsets.UTF_8);

    /**
     * GET /stream: server-sent events on this connection, from a thread of its own (so it does not hold one of
     * the pool's): "f" (SimView.frame) every FRAME_MS and "s" (the state without the OpMode list) every
     * STATE_MS, until the page goes away. True if the connection now belongs to the stream's thread.
     */
    private boolean startStream(Socket c, OutputStream out) throws IOException {
        if (streams.incrementAndGet() > MAX_STREAMS) {
            streams.decrementAndGet();
            KEEP.set(false);
            send(out, 503, "text/plain", "too many streams: read /state".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        String head = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-store\r\n"
                + "Connection: keep-alive\r\n\r\nretry: 1000\n\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        Thread t = new Thread(() -> {
            try {
                c.setSoTimeout(0);
                long nextState = 0, nextGame = 0, next = System.nanoTime();
                String lastGame = "";
                while (!c.isClosed() && !socket.isClosed()) {
                    long now = System.nanoTime();
                    // on a fixed beat (not "wait 20 ms after each send"), so it stays at 50 a second
                    next = Math.max(next + FRAME_MS * 1_000_000L, now - FRAME_MS * 1_000_000L);
                    StringBuilder b = new StringBuilder(8192);
                    b.append("event: f\ndata: ").append(SimView.frame(sim)).append("\n\n");
                    if (now >= nextState) {
                        nextState = now + STATE_MS * 1_000_000L;
                        b.append("event: s\ndata: ").append(SimJson.state(sim, this, false)).append("\n\n");
                    }
                    if (now >= nextGame) {
                        nextGame = now + GAME_MS * 1_000_000L;
                        String game;
                        synchronized (sim.robot) {
                            game = sim.robot.pieces.score.json();
                        }
                        if (!game.equals(lastGame)) {
                            lastGame = game;
                            b.append("event: g\ndata: ").append(game).append("\n\n");
                        }
                    }
                    out.write(b.toString().getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    long sleep = (next - System.nanoTime()) / 1_000_000L;
                    if (sleep > 0) Thread.sleep(sleep);
                }
            } catch (IOException | RuntimeException | InterruptedException ignored) {
                // the page went away (or the simulator stops)
            } finally {
                streams.decrementAndGet();
                try {
                    c.close();
                } catch (IOException ignored) {
                    // closing is best effort
                }
            }
        }, "sim-stream");
        t.setDaemon(true);
        t.start();
        return true;
    }

    // A Driver Station button or a panel control (recorded while the test recorder records).
    String command(Map<String, String> f) {
        String action = f.getOrDefault("action", "");
        // A test running on the page drives the robot: the page's buttons wait (STOP stops the test too).
        if (tests.runningNow() && !action.equals("clearPaths")) {
            if (action.equals("stop")) {
                tests.stopRun();
                return "ok";
            }
            return "a test is running: wait for it to finish, or stop it";
        }
        String reply = commandNow(f, action);
        if (reply.equals("ok")) tests.recorder.command(f);
        return reply;
    }

    private String commandNow(Map<String, String> f, String action) {
        try {
            switch (action) {
                case "init": sim.ds.init(f.get("opmode")); return "ok";
                case "start": sim.ds.start(); return "ok";
                case "stop": sim.ds.stopAsync(); return "ok";
                case "push":
                    sim.robot.push(num(f, "forward"), num(f, "right"), num(f, "seconds"));
                    return "ok";
                case "resetPose": sim.robot.resetPose(); sim.jumps.incrementAndGet(); return "ok";
                case "resetField": sim.robot.pieces.reset(); sim.jumps.incrementAndGet(); return "ok";
                case "clearPaths": sim.robot.paths.clear(); return "ok";
                // a game piece put on the field by hand (like the scenario's set piece)
                case "piece": sim.robot.pieces.place(f.getOrDefault("type", "POLLEN"), num(f, "x"), num(f, "y"), num(f, "z")); return "ok";
                case "preload": sim.robot.pieces.setPreloaded("1".equals(f.get("on"))); sim.jumps.incrementAndGet(); return "ok";
                // shot scatter for every launcher: off, realistic, rough, or file (each launcher's scatter=)
                case "scatter": sim.robot.pieces.setScatter(f.get("mode")); return "ok";
                case "pose": sim.robot.setPose(num(f, "x"), num(f, "y"), num(f, "heading")); sim.jumps.incrementAndGet(); return "ok";
                case "touch": sim.robot.toggleTouch(f.get("name")); return "ok";
                case "autoTimer": sim.ds.autoTimer = "1".equals(f.get("on")); return "ok";
                case "teleTimer": sim.ds.teleTimer = "1".equals(f.get("on")); return "ok";
                case "battery": sim.robot.setBattery(num(f, "volts")); return "ok";
                default: return "unknown action";
            }
        } catch (RuntimeException e) {
            return "error: " + e.getMessage();
        }
    }

    // ----------------------------- the page's tests (SimTests, SimRecorder) -----------------------------

    // /tests...: answers JSON ({"error": "..."} when something is not right).
    private String tests(String method, String path, String query, byte[] body) {
        try {
            Map<String, String> f = method.equals("POST") ? form(new String(body, StandardCharsets.UTF_8)) : form(query);
            if (method.equals("GET") && path.equals("/tests")) return tests.info();
            if (method.equals("GET") && path.equals("/tests/read")) {
                StringBuilder b = new StringBuilder("{\"text\":");
                return SimJson.string(b, tests.read(f.get("name"))).append('}').toString();
            }
            if (method.equals("GET") && path.equals("/tests/status")) return tests.status();
            if (!method.equals("POST")) return "{\"error\":\"not found\"}";
            switch (path) {
                case "/tests/save": {
                    boolean wrote = tests.write(f.get("name"), f.getOrDefault("text", ""), "1".equals(f.get("replace")));
                    StringBuilder b = new StringBuilder("{\"ok\":").append(wrote).append(",\"exists\":").append(!wrote).append(",\"path\":");
                    return SimJson.string(b, "simulator/scenarios/" + f.get("name") + ".sim").append('}').toString();
                }
                case "/tests/record": {
                    String a = f.getOrDefault("action", "");
                    if (a.equals("start")) {
                        if (tests.runningNow()) throw new IllegalArgumentException("a test is running: wait for it to finish");
                        tests.recorder.start(!"now".equals(f.get("from")));
                        return "{\"ok\":true}";
                    }
                    if (a.equals("stop")) return SimRecorder.json(tests.recorder.stop());
                    if (a.equals("cancel")) {
                        tests.recorder.cancel();
                        return "{\"ok\":true}";
                    }
                    throw new IllegalArgumentException("record: start, stop or cancel");
                }
                case "/tests/run":
                    tests.startRun(f.getOrDefault("name", ""), f.getOrDefault("text", ""), "1".equals(f.get("measure")));
                    return "{\"ok\":true}";
                case "/tests/stop":
                    tests.stopRun();
                    return "{\"ok\":true}";
                default:
                    return "{\"error\":\"not found\"}";
            }
        } catch (IllegalArgumentException | IOException e) {
            StringBuilder b = new StringBuilder("{\"error\":");
            return SimJson.string(b, String.valueOf(e.getMessage())).append('}').toString();
        }
    }

    private static double num(Map<String, String> f, String key) {
        try {
            double v = Double.parseDouble(f.getOrDefault(key, "0"));
            return Double.isNaN(v) || Double.isInfinite(v) ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // The field model, read once (it can be several MB; every page load and tablet would read it again).
    private byte[] modelBytes = null;

    private synchronized byte[] fieldModel() throws IOException {
        if (sim.robot.fieldModel == null) return null;
        if (modelBytes == null) modelBytes = java.nio.file.Files.readAllBytes(sim.robot.fieldModel);
        return modelBytes;
    }

    // ----------------------------- the auto builder -----------------------------

    // The Robot classes the Built Auto can use (the code the simulator lists), found once.
    private List<Class<? extends com.crossbotics.ftchelper.robot.RobotDefinition>> robots = null;

    private synchronized List<Class<? extends com.crossbotics.ftchelper.robot.RobotDefinition>> robots() {
        if (robots == null) robots = BuiltAuto.robotClasses(sim.ds.scope);
        return robots;
    }

    // GET /auto: {"robots": [{name, short, drive, odometry, follower, score, commands, actions, teleop}], "guess": name}
    private String builderInfo() {
        List<Class<? extends com.crossbotics.ftchelper.robot.RobotDefinition>> list = robots();
        StringBuilder b = new StringBuilder("{\"robots\":[");
        for (int i = 0; i < list.size(); i++) {
            // {name, short, drive, odometry, follower} plus how well it fits the robot file (the page picks among the best)
            String d = BuiltAuto.describe(list.get(i));
            b.append(i > 0 ? "," : "").append(d, 0, d.length() - 1).append(",\"score\":").append(BuiltAuto.score(list.get(i), sim.robot))
                    .append(',').append(BuiltAuto.codeInfo(list.get(i), sim.ds.opModes)).append('}');
        }
        Class<?> guess = BuiltAuto.guess(list, sim.robot);
        b.append("],\"guess\":");
        SimJson.string(b, guess == null ? "" : guess.getName());
        return b.append('}').toString();
    }

    // POST /auto: run the plan. Answers {"ok":true,"robotClass":...} or {"error":"..."} at once; START comes
    // from a thread of its own once INIT has put the robot on its spot.
    private String runPlan(String text) {
        try {
            Map<String, Object> m = SimJsonReader.object(SimJsonReader.parse(text), "the request");
            Object prog = m.get("program");
            if (prog == null) throw new IllegalArgumentException("no program");
            BuiltAuto.Program p = BuiltAuto.Program.from(SimJsonReader.object(prog, "the program"));
            // Only a Robot class from the list (never any class the page names).
            Class<? extends com.crossbotics.ftchelper.robot.RobotDefinition> chosen = null;
            for (Class<? extends com.crossbotics.ftchelper.robot.RobotDefinition> c : robots()) {
                if (c.getName().equals(p.robotClass)) chosen = c;
            }
            if (chosen == null && !p.robotClass.isEmpty()) throw new IllegalArgumentException("no Robot class " + p.robotClass + " in the code");
            if (chosen == null) chosen = BuiltAuto.guess(robots(), sim.robot);
            if (chosen == null) throw new IllegalArgumentException("no Robot class (a class that extends RobotDefinition) in the code");
            com.crossbotics.ftchelper.match.Alliance alliance = "BLUE".equals(m.get("alliance"))
                    ? com.crossbotics.ftchelper.match.Alliance.BLUE : com.crossbotics.ftchelper.match.Alliance.RED;
            if (tests.runningNow()) throw new IllegalArgumentException("a test is running: wait for it to finish, or stop it");
            tests.recorder.builderRun();
            BuiltAuto.program = p;
            BuiltAuto.robotClass = chosen;
            // Every Run starts a fresh match: the field's pieces back on their spots and the preload in the
            // robot (unless "Preloaded" is off), whatever an earlier TeleOp or run did with them.
            if (sim.robot.pieces != null) {
                sim.robot.pieces.reset();
                sim.jumps.incrementAndGet();
            }
            final int screens = sim.ds.screen.updates();
            sim.ds.init(BuiltAuto.NAME, alliance);
            final int run = sim.ds.initCount;
            Thread t = new Thread(() -> startWhenPlaced(run, screens), "built-auto-start");
            t.setDaemon(true);
            t.start();
            StringBuilder b = new StringBuilder("{\"ok\":true,\"robotClass\":");
            return SimJson.string(b, chosen.getName()).append('}').toString();
        } catch (RuntimeException e) {
            StringBuilder b = new StringBuilder("{\"error\":");
            return SimJson.string(b, String.valueOf(e.getMessage())).append('}').toString();
        }
    }

    // START once INIT has shown its screen and the robot stands on the start spot (at most 4 s), like a
    // drive team that waits for the robot to be placed. Not if INIT stopped with an error, or someone
    // pressed something else meanwhile.
    private void startWhenPlaced(int run, int screensBefore) {
        try {
            long end = System.nanoTime() + 4_000_000_000L;
            while (System.nanoTime() < end) {
                if (sim.ds.initCount != run || sim.ds.phase() != SimDriverStation.Phase.INIT || !sim.ds.error().isEmpty()) return;
                com.crossbotics.ftchelper.util.FieldPose at = com.crossbotics.ftchelper.match.MatchMemory.placement();
                boolean shown = sim.ds.screen.updates() > screensBefore;
                boolean placed = at == null || Math.hypot(at.x - sim.robot.x, at.y - sim.robot.y) < 0.5;
                if (shown && placed) {
                    Thread.sleep(300);   // a moment on the spot, so the page shows it there first
                    if (sim.ds.initCount == run && sim.ds.phase() == SimDriverStation.Phase.INIT && sim.ds.error().isEmpty()) sim.ds.start();
                    return;
                }
                Thread.sleep(20);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // The auto builder's page code: only these files (no other paths can be asked for).
    private static final java.util.Set<String> BUILDER_FILES = new java.util.HashSet<>(java.util.Arrays.asList(
            "/builder/builder.js", "/builder/simcore.js", "/builder/pathcore.js", "/builder/stepscore.js",
            "/builder/mapdraw.js", "/builder/setupcard.js"));

    // The only files served from the 3D library folder (no other paths can be asked for).
    private static final java.util.Set<String> THREE_FILES = new java.util.HashSet<>(java.util.Arrays.asList(
            "/three/three.min.js", "/three/OrbitControls.js", "/three/GLTFLoader.js"));

    private byte[] page() throws IOException {
        return resource("/sim/index.html");
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = SimServer.class.getResourceAsStream(name)) {
            if (in == null) return (name + " missing").getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0;
        while (true) {
            int ch = in.read();
            if (ch < 0) return null;
            b.write(ch);
            // the head ends at an empty line: \r\n\r\n
            state = (ch == '\r' && (state == 0 || state == 2)) || (ch == '\n' && (state == 1 || state == 3)) ? state + 1 : 0;
            if (state == 4) return b.toString("ISO-8859-1");
            if (b.size() > 16384) return null;
        }
    }

    // True when there is no Origin header, or it names the same host:port as the Host header.
    private static boolean sameOrigin(String head) {
        String origin = null, host = null;
        for (String line : head.split("\r\n")) {
            String low = line.toLowerCase(Locale.US);
            if (low.startsWith("origin:")) origin = line.substring(7).trim();
            if (low.startsWith("host:")) host = line.substring(5).trim();
        }
        if (origin == null || origin.equals("null")) return origin == null;
        String o = origin.replaceFirst("^https?://", "");
        return host != null && o.equalsIgnoreCase(host);
    }

    private static Map<String, String> form(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : text.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            try {
                String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), "UTF-8");
                String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                out.put(k, v);
            } catch (java.io.UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        return out;
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        send(out, code, type, body, "no-store");
    }

    private static void send(OutputStream out, int code, String type, byte[] body, String cache) throws IOException {
        String status = code == 200 ? "OK" : code == 204 ? "No Content" : code == 503 ? "Busy" : "Not Found";
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: " + cache + "\r\n"
                + (keepAliveNow() ? "Connection: keep-alive\r\nKeep-Alive: timeout=" + IDLE_MS / 1000 + "\r\n\r\n" : "Connection: close\r\n\r\n");
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
