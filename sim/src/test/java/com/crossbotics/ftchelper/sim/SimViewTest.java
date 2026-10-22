package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * What the page's smooth picture and its Layers menu read (SimView): the fast update with the physics time, the
 * stream, the OpMode list on its own, keep-alive connections, the camera's reasons, the floor map and the shot arc.
 */
public class SimViewTest {
    private static Map<String, Object> json(String text) {
        return SimJsonReader.object(SimJsonReader.parse(text), "json");
    }

    // The fast update: the physics time goes up, and every piece has a key and a turn (quaternion).
    @Test public void theFastUpdateCarriesThePhysicsTimeAndPieceKeys() throws Exception {
        try (SimRun s = new SimRun()) {
            Map<String, Object> a = json(SimView.frame(s.sim));
            assertTrue(SimRun.waitFor(() -> s.sim.simSeconds() > SimJsonReader.number(a, "t", 0) + 0.05, 2));
            Map<String, Object> b = json(SimView.frame(s.sim));
            assertTrue("time goes on", SimJsonReader.number(b, "t", 0) > SimJsonReader.number(a, "t", 0));
            List<Object> r = SimJsonReader.array(b.get("r"), "r");
            assertEquals(s.robot().x, ((Number) r.get(0)).doubleValue(), 0.01);
            List<Object> pieces = SimJsonReader.array(b.get("p"), "p");
            assertFalse("the field's pieces", pieces.isEmpty());
            Set<Long> keys = new HashSet<>();
            for (Object o : pieces) {
                List<Object> p = SimJsonReader.array(o, "piece");
                assertEquals("key, type, x, y, z, qw, qx, qy, qz", 9, p.size());
                assertTrue("each key once", keys.add(((Number) p.get(0)).longValue()));
                double qn = 0;
                for (int k = 5; k < 9; k++) qn += Math.pow(((Number) p.get(k)).doubleValue(), 2);
                assertEquals("a unit quaternion", 1, qn, 0.01);
            }
            assertEquals(s.robot().shapes.pieces().size(), SimJsonReader.array(b.get("s"), "s").size());
            assertEquals(s.robot().cameras.size(), SimJsonReader.array(b.get("c"), "c").size());
        }
    }

    // A piece put on the field keeps its key from one update to the next while it falls (the page glides it).
    @Test public void aFallingPieceKeepsItsKey() throws Exception {
        try (SimRun s = new SimRun()) {
            s.robot().pieces.place("POLLEN", 30, 30, 60);
            double[] first = find(s, 30, 30);
            assertNotNull(first);
            assertTrue(SimRun.waitFor(() -> { double[] p = find(s, 30, 30); return p != null && p[4] < 40; }, 3));
            double[] later = find(s, 30, 30);
            assertEquals("same key", first[0], later[0], 0);
        }
    }

    private static double[] find(SimRun s, double x, double y) {
        for (double[] p : s.robot().pieces.drawnForView()) if (Math.hypot(p[2] - x, p[3] - y) < 1) return p;
        return null;
    }

    // The stream's state leaves out the OpMode list (only its key) and the fast update; /state has both.
    @Test public void theStreamStateLeavesOutTheOpModeList() throws Exception {
        try (SimRun s = new SimRun()) {
            SimServer server = new SimServer(s.sim, 0, false);
            Map<String, Object> full = json(SimJson.state(s.sim, server));
            Map<String, Object> lite = json(SimJson.state(s.sim, server, false));
            server.close();
            assertTrue(full.containsKey("opmodes"));
            assertTrue(full.containsKey("fast"));
            assertFalse(lite.containsKey("opmodes"));
            assertFalse(lite.containsKey("fast"));
            assertEquals(full.get("opmodesKey"), lite.get("opmodesKey"));
            assertTrue(lite.containsKey("t"));
            assertTrue(lite.containsKey("pieceTypes"));
            assertTrue(lite.containsKey("arcs"));
            Map<String, Object> cam = SimJsonReader.object(SimJsonReader.array(lite.get("cameras"), "cameras").get(0), "camera");
            assertEquals("every tag has a reason", s.robot().field.tags.size(), SimJsonReader.array(cam.get("sight"), "sight").size());
            assertTrue("the list is the smaller part", SimJson.state(s.sim, server, false).length() < SimJson.state(s.sim, server).length() - 4000);
        }
    }

    // The camera's reasons say "seen" for exactly the tags the OpMode's camera finds.
    @Test public void theSightReasonsMatchWhatTheCameraSees() throws Exception {
        try (SimRun s = new SimRun()) {
            SimCamera c = s.robot().cameras.values().iterator().next();
            // In front of the red HIVE's +x CELL, facing it (BIOBUZZ field): some tags in view.
            s.robot().setPose(40, -14, 90);
            assertReasonsMatch(c, s.robot().field);
            boolean anySeen = false;
            for (SimCamera.Sight t : c.sight(s.robot().field, 3, c.pose())) anySeen |= t.why.equals("seen");
            assertTrue("it sees some tags from there", anySeen);
            // Facing the audience wall: none seen; the ones behind say so.
            s.robot().setPose(40, -14, -90);
            assertReasonsMatch(c, s.robot().field);
            for (SimCamera.Sight t : c.sight(s.robot().field, 3, c.pose())) assertFalse(t.why.equals("seen"));
        }
    }

    private static void assertReasonsMatch(SimCamera c, SimField field) {
        double[][] pose = c.pose();
        Set<Integer> seen = new HashSet<>(), model = new HashSet<>();
        for (SimCamera.Sight t : c.sight(field, 3, pose)) if (t.why.equals("seen")) seen.add(t.id);
        for (SimCamera.Seen t : c.visibleTags(field, 3, pose)) model.add(t.tag.id);
        assertEquals(model, seen);
    }

    // The floor map: one number per square, and more squares see tags when the robot may turn any way.
    @Test public void theFloorMapCountsTags() throws Exception {
        try (SimRun s = new SimRun()) {
            Map<String, Object> facing = json(SimView.seeMap(s.robot(), 0, 6, false));
            Map<String, Object> any = json(SimView.seeMap(s.robot(), 0, 6, true));
            int n = (int) SimJsonReader.number(facing, "n", 0);
            List<Object> a = SimJsonReader.array(facing.get("counts"), "counts"), b = SimJsonReader.array(any.get("counts"), "counts");
            assertEquals(n * n, a.size());
            int sumA = 0, sumB = 0;
            for (int i = 0; i < a.size(); i++) {
                int x = ((Number) a.get(i)).intValue(), y = ((Number) b.get(i)).intValue();
                assertTrue("turning can only help", y >= x);
                sumA += x;
                sumB += y;
            }
            assertTrue(sumB > 0);
            assertTrue(sumB >= sumA);
            assertNull("no such camera", SimView.seeMap(s.robot(), 9, 6, false));
        }
    }

    // The shot arc: stopped flywheel = no arc and why; a free flight without air peaks where the school formula says.
    @Test public void theShotArc() throws Exception {
        try (SimRun s = new SimRun()) {
            List<SimPieces.ShotNow> shots = s.robot().pieces.shotsNow();
            assertEquals(1, shots.size());
            assertEquals("the flywheel is stopped", shots.get(0).why);
            s.robot().motors.get("flywheel").device.setPower(1);
            assertTrue("up to speed", SimRun.waitFor(() -> s.robot().pieces.shotsNow().get(0).speedIn > 200, 4));
            SimPieces.ShotNow shot = s.robot().pieces.shotsNow().get(0);
            assertEquals(45, shot.angleDeg, 0.01);
            List<double[]> pts = SimView.fly(shot, s.robot().field.wallHalf);
            double peak = 0;
            for (double[] p : pts) peak = Math.max(peak, p[2]);
            assertTrue("it goes up: from " + shot.at[2] + " to " + peak + " at " + shot.speedIn + " in/s, " + pts.size() + " points",
                    peak > shot.at[2] + 5);
            StringBuilder b = new StringBuilder();
            SimView.arcs(b, s.robot());
            assertTrue(b.toString(), b.toString().contains("\"pts\":[["));
            s.robot().motors.get("flywheel").device.setPower(0);
        }
        SimPieces.ShotNow n = new SimPieces.ShotNow();
        n.at = new double[] {0, 0, 10};
        n.vel = new double[] {0, 300 * Math.cos(Math.PI / 4), 300 * Math.sin(Math.PI / 4)};
        n.spin = new double[3];
        n.diameterIn = 3;
        n.massG = 25;
        n.dragCd = 0;
        double peak = 0;
        for (double[] p : SimView.fly(n, 70.5)) peak = Math.max(peak, p[2]);
        double vz = 300 * Math.sin(Math.PI / 4);
        assertEquals(10 + vz * vz / (2 * SimView.G_IN), peak, 0.5);
    }

    // Over HTTP: two requests on one connection (keep-alive), the OpMode list, and the stream's first updates.
    @Test public void theServerKeepsConnectionsAndStreams() throws Exception {
        try (SimRun s = new SimRun()) {
            SimServer server = new SimServer(s.sim, 0, false);
            Thread t = new Thread(server::serve, "test-server");
            t.setDaemon(true);
            t.start();
            try {
                try (Socket c = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                    c.setSoTimeout(3000);
                    OutputStream out = c.getOutputStream();
                    InputStream in = c.getInputStream();
                    for (int i = 0; i < 2; i++) {
                        String body = "a=1&session=t&seq=" + (i + 1);
                        out.write(("POST /pad/1 HTTP/1.1\r\nHost: x\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: "
                                + body.length() + "\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        String head = readResponse(in);
                        assertTrue(head, head.startsWith("HTTP/1.1 200"));
                        assertTrue(head, head.toLowerCase(java.util.Locale.US).contains("connection: keep-alive"));
                    }
                    out.write("GET /opmodes HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    String head = readResponse(in);
                    assertTrue(head, head.contains("Connection: close"));
                    assertTrue(head, head.contains("\"kind\":"));
                }
                int jumps = s.sim.jumps.get();
                try (Socket c = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                    String body = "action=pose&x=-40&y=-40&heading=0";
                    c.getOutputStream().write(("POST /cmd HTTP/1.1\r\nHost: x\r\nConnection: close\r\nContent-Length: " + body.length()
                            + "\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8));
                    readResponse(c.getInputStream());
                }
                assertEquals("putting the robot somewhere is a jump", jumps + 1, s.sim.jumps.get());
                try (Socket c = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                    c.setSoTimeout(3000);
                    c.getOutputStream().write("GET /stream HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                    List<String> events = new ArrayList<>();
                    long end = System.currentTimeMillis() + 2000;
                    String line;
                    while (System.currentTimeMillis() < end && (line = r.readLine()) != null) {
                        if (line.startsWith("event: ")) events.add(line.substring(7));
                        if (events.contains("s") && events.stream().filter("f"::equals).count() >= 5) break;
                    }
                    assertTrue(events.toString(), events.contains("s"));
                    assertTrue(events.toString(), events.stream().filter("f"::equals).count() >= 5);
                }
            } finally {
                server.close();
            }
        }
    }

    // One response: its head and body (by Content-Length).
    private static String readResponse(InputStream in) throws Exception {
        StringBuilder head = new StringBuilder();
        int state = 0;
        while (state < 4) {
            int ch = in.read();
            if (ch < 0) break;
            head.append((char) ch);
            state = (ch == '\r' && (state == 0 || state == 2)) || (ch == '\n' && (state == 1 || state == 3)) ? state + 1 : 0;
        }
        int length = 0;
        for (String l : head.toString().split("\r\n")) {
            if (l.toLowerCase(java.util.Locale.US).startsWith("content-length:")) length = Integer.parseInt(l.substring(15).trim());
        }
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int k = in.read(body, read, length - read);
            if (k < 0) break;
            read += k;
        }
        return head + new String(body, StandardCharsets.UTF_8);
    }
}
