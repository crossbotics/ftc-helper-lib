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
import java.util.Locale;
import java.util.Map;

/**
 * A very small web server for the simulator page. It only answers this computer, unless started
 * with --lan (then phones and tablets on the same WiFi can join as a gamepad).
 *
 * GET /             the page
 * GET /state        everything the page shows, as JSON
 * GET /stream       the same, kept coming (server-sent events): "f" where everything is, 50 a second
 *                   (SimView.frame); "s" the state without the OpMode list, 10 a second
 * GET /opmodes      the OpMode list (the stream sends only its key)
 * GET /seemap?cam=N&any=0|1&step=4  where camera N can see tags (SimView.seeMap)
 * GET /field        the season's field file
 * GET /field-model  the 3D field model named by field.model in the robot file (404 if none)
 * GET /robot-model?i=N  CAD mesh N of the robot (float32 triangles, robot frame inches)
 * GET /three/...    the 3D library (three.js), so the page works without the internet
 * GET /simview.js   the page's drawing helpers (gliding between updates, ball texture, labels)
 * POST /pad/1, /pad/2   a gamepad's buttons and sticks ("a=1&lx=0.5...")
 * POST /cmd         a Driver Station or panel command ("action=init&opmode=...")
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
    // Streams open now (each has its own thread); more are refused and that page reads /state instead.
    private final java.util.concurrent.atomic.AtomicInteger streams = new java.util.concurrent.atomic.AtomicInteger();
    static final int MAX_STREAMS = 8;
    // How often the stream sends where everything is, and the whole state (milliseconds).
    static final int FRAME_MS = 20, STATE_MS = 100;
    // A kept-alive connection with nothing to say for this long is closed (frees its thread).
    private static final int IDLE_MS = 5000;
    final boolean lan;

    SimServer(SimEngine sim, int port, boolean lan) throws IOException {
        this.sim = sim;
        this.lan = lan;
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
        if (length < 0 || length > MAX_BODY) return false;   // far too big for a gamepad or a command
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
                long nextState = 0, next = System.nanoTime();
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

    // A Driver Station button or a panel control.
    private String command(Map<String, String> f) {
        String action = f.getOrDefault("action", "");
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
