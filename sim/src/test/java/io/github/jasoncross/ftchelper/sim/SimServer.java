package io.github.jasoncross.ftchelper.sim;

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
 * GET /field        the season's field file
 * GET /field-model  the 3D field model named by field.model in the robot file (404 if none)
 * GET /three/...    the 3D library (three.js), so the page works without the internet
 * POST /pad/1, /pad/2   a gamepad's buttons and sticks ("a=1&lx=0.5...")
 * POST /cmd         a Driver Station or panel command ("action=init&opmode=...")
 */
final class SimServer {
    private final SimEngine sim;
    private final ServerSocket socket;
    // At most 8 requests at once and 64 waiting; more are turned away (a page sends about 50 a second).
    private final java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(
            8, 8, 30, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(64), r -> {
                Thread t = new Thread(r, "sim-web");
                t.setDaemon(true);
                return t;
            });
    private static final int MAX_BODY = 16 * 1024;
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

    private void handle(Socket c) {
        try (Socket client = c) {
            client.setSoTimeout(2000);
            InputStream in = client.getInputStream();
            String head = readHead(in);
            if (head == null) return;
            String[] first = head.split("\r\n")[0].split(" ");
            if (first.length < 2) return;
            String method = first[0], path = first[1];
            int length = 0;
            for (String line : head.split("\r\n")) {
                if (line.toLowerCase(Locale.US).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
            }
            if (length < 0 || length > MAX_BODY) return;   // far too big for a gamepad or a command
            byte[] body = new byte[length];
            int read = 0;
            while (read < length) {
                int n = in.read(body, read, length - read);
                if (n < 0) break;
                read += n;
            }
            // A request from another web site (its page open in the same browser) is refused.
            if (method.equals("POST") && !sameOrigin(head)) {
                send(client.getOutputStream(), 404, "text/plain", "refused".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String query = path.contains("?") ? path.substring(path.indexOf('?') + 1) : "";
            path = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
            OutputStream out = client.getOutputStream();
            if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
                send(out, 200, "text/html; charset=utf-8", page());
            } else if (method.equals("GET") && path.equals("/field")) {
                send(out, 200, "application/json", sim.robot.field.json.getBytes(StandardCharsets.UTF_8));
            } else if (method.equals("GET") && path.equals("/field-model")) {
                byte[] model = fieldModel();
                if (model == null) send(out, 404, "text/plain", "no field.model in the robot file".getBytes(StandardCharsets.UTF_8));
                else send(out, 200, "model/gltf-binary", model, "max-age=86400");   // the page asks with ?v=<file time>
            } else if (method.equals("GET") && THREE_FILES.contains(path)) {
                send(out, 200, "text/javascript; charset=utf-8", resource("/sim" + path));
            } else if (method.equals("GET") && path.equals("/state")) {
                send(out, 200, "application/json", SimJson.state(sim, this).getBytes(StandardCharsets.UTF_8));
            } else if (method.equals("POST") && path.startsWith("/pad/")) {
                Map<String, String> form = form(new String(body, StandardCharsets.UTF_8));
                form.put("_from", client.getInetAddress().getHostAddress());
                (path.endsWith("2") ? sim.ds.pad2 : sim.ds.pad1).apply(form);
                send(out, 204, "text/plain", new byte[0]);
            } else if (method.equals("POST") && path.equals("/cmd")) {
                String reply = command(form(new String(body, StandardCharsets.UTF_8)));
                send(out, 200, "text/plain; charset=utf-8", reply.getBytes(StandardCharsets.UTF_8));
            } else {
                send(out, 404, "text/plain", ("not found: " + path + " " + query).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException ignored) {
            // a closed tab or a bad request: nothing to do
        }
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
                case "resetPose": sim.robot.resetPose(); return "ok";
                case "pose": sim.robot.setPose(num(f, "x"), num(f, "y"), num(f, "heading")); return "ok";
                case "touch": sim.robot.toggleTouch(f.get("name")); return "ok";
                case "autoTimer": sim.ds.autoTimer = "1".equals(f.get("on")); return "ok";
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
        String status = code == 200 ? "OK" : code == 204 ? "No Content" : "Not Found";
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: " + cache + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
