package io.github.jasoncross.ftchelper.sim;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Everything the page shows, as JSON (written by hand: no extra library needed). */
final class SimJson {
    private SimJson() { }

    static String state(SimEngine sim, SimServer server) {
        SimDriverStation ds = sim.ds;
        SimRobot r = sim.robot;
        StringBuilder b = new StringBuilder(4096);
        b.append('{');
        field(b, "phase", ds.phase().name()).append(',');
        SimDriverStation.Entry cur = ds.current();
        field(b, "opmode", cur == null ? "" : cur.name).append(',');
        field(b, "kind", cur == null ? "" : cur.kind).append(',');
        field(b, "error", ds.error()).append(',');
        b.append("\"runSeconds\":").append(fmt(ds.runSeconds())).append(',');
        b.append("\"autoTimer\":").append(ds.autoTimer).append(',');
        b.append("\"battery\":").append(fmt(r.batteryVolts)).append(',');
        b.append("\"lan\":").append(server.lan).append(',');
        b.append("\"port\":").append(server.port()).append(',');
        b.append("\"addresses\":");
        strings(b, server.lan ? addresses() : Collections.<String>emptyList());
        b.append(',');
        b.append("\"pads\":[");
        for (SimGamepad g : new SimGamepad[] {ds.pad1, ds.pad2}) {
            if (g != ds.pad1) b.append(',');
            b.append('{');
            field(b, "owner", g.owner()).append(',');
            field(b, "from", g.ownerFrom()).append('}');
        }
        b.append("],");
        b.append("\"opmodes\":[");
        boolean firstItem = true;
        for (SimDriverStation.Entry e : ds.opModes) {
            if (!firstItem) b.append(',');
            firstItem = false;
            b.append('{');
            field(b, "name", e.name).append(',');
            field(b, "group", e.group).append(',');
            field(b, "kind", e.kind).append(',');
            b.append("\"disabled\":").append(e.disabled).append('}');
        }
        b.append("],\"screen\":");
        strings(b, ds.screen.screen());
        b.append(",\"motors\":[");
        firstItem = true;
        for (SimMotor m : r.motors.values()) {
            if (!firstItem) b.append(',');
            firstItem = false;
            b.append('{');
            field(b, "name", m.name).append(',');
            field(b, "kind", m.kind.name().toLowerCase(Locale.US)).append(',');
            field(b, "part", m.partName).append(',');
            field(b, "mode", m.modeName()).append(',');
            b.append("\"power\":").append(fmt(m.userPower())).append(',');
            b.append("\"applied\":").append(fmt(m.appliedPower)).append(',');
            b.append("\"ticks\":").append(m.encoder()).append(',');
            b.append("\"velocity\":").append(fmt(m.velocity())).append(',');
            b.append("\"amps\":").append(fmt(m.amps)).append(',');
            b.append("\"atStop\":").append(m.atStop).append('}');
        }
        b.append("],\"servos\":[");
        firstItem = true;
        for (SimRobot.SimServo s : r.servos.values()) {
            if (!firstItem) b.append(',');
            firstItem = false;
            b.append('{');
            field(b, "name", s.name).append(',');
            field(b, "part", s.partName).append(',');
            b.append("\"actual\":").append(fmt(s.actual)).append(',');
            b.append("\"target\":").append(fmt(s.target())).append('}');
        }
        b.append("],\"crservos\":[");
        firstItem = true;
        for (Map.Entry<String, double[]> e : r.crServos.entrySet()) {
            if (!firstItem) b.append(',');
            firstItem = false;
            b.append('{');
            field(b, "name", e.getKey()).append(',');
            b.append("\"power\":").append(fmt(e.getValue()[0])).append('}');
        }
        b.append("],\"touches\":[");
        firstItem = true;
        for (Map.Entry<String, SimRobot.SimTouch> e : r.touches.entrySet()) {
            if (!firstItem) b.append(',');
            firstItem = false;
            b.append('{');
            field(b, "name", e.getKey()).append(',');
            b.append("\"pressed\":").append(e.getValue().pressed()).append(',');
            b.append("\"manual\":").append(e.getValue().manual).append('}');
        }
        b.append("],\"cameras\":[");
        boolean firstCam = true;
        for (SimCamera c : r.cameras.values()) {
            if (!firstCam) b.append(',');
            firstCam = false;
            b.append('{');
            field(b, "name", c.name).append(',');
            field(b, "kind", c.kind).append(',');
            b.append("\"forward\":").append(fmt(c.forwardIn)).append(',');
            b.append("\"left\":").append(fmt(c.leftIn)).append(',');
            b.append("\"up\":").append(fmt(c.upIn)).append(',');
            b.append("\"yaw\":").append(fmt(c.yawDeg)).append(',');
            b.append("\"pitch\":").append(fmt(c.pitchDeg)).append(',');
            b.append("\"hfov\":").append(fmt(c.hfovDeg)).append(',');
            b.append("\"width\":").append(c.width).append(',');
            b.append("\"height\":").append(c.height).append(',');
            b.append("\"open\":").append(c.openPortals() > 0).append(',');
            b.append("\"seen\":").append(c.seenIds().toString()).append(',');
            b.append("\"detections\":");
            strings(b, c.detections());
            b.append('}');
        }
        // Game pieces: each on the field (or in a claw), what the robot holds, what has scored.
        b.append("],\"pieces\":[");
        List<Object[]> drawn = r.pieces.drawn();
        for (int i = 0; i < drawn.size(); i++) {
            Object[] d = drawn.get(i);
            SimField.PieceType t = (SimField.PieceType) d[0];
            if (i > 0) b.append(',');
            b.append('{');
            field(b, "type", t.name).append(',');
            field(b, "color", t.color).append(',');
            b.append("\"d\":").append(fmt(t.diameterIn)).append(",\"x\":").append(fmt((Double) d[1]))
                    .append(",\"y\":").append(fmt((Double) d[2])).append(",\"z\":").append(fmt((Double) d[3])).append('}');
        }
        b.append("],\"stored\":").append(counts(r.pieces.stored()));
        b.append(",\"scored\":").append(counts(r.pieces.scored()));
        b.append(",\"launched\":").append(r.pieces.launched());
        b.append(",\"preloaded\":").append(r.pieces.preloaded());
        b.append(",\"holds\":").append(r.pieces.capacity());
        b.append(",\"leftField\":").append(r.pieces.leftField());
        // each HIVE: which CELL is up (+1: toward +x), tips, pieces in its up CELL, and its CELL plates now (for the picture)
        synchronized (r) {
            b.append(",\"hives\":[");
            java.util.Map<String, Integer> cells = r.pieces.inCells();
            for (int i = 0; i < r.field.hives.size(); i++) {
                SimField.Hive h = r.field.hives.get(i);
                if (i > 0) b.append(',');
                b.append('{');
                field(b, "name", h.name).append(',');
                field(b, "alliance", h.alliance).append(',');
                b.append("\"up\":").append(h.up).append(",\"startUp\":").append(h.startUp).append(",\"tips\":").append(h.tips)
                        .append(",\"inCell\":").append(cells.getOrDefault(h.name, 0)).append(",\"tipDeg\":").append(fmt(h.tipDeg))
                        .append(",\"pivot\":").append(vec(h.pivot)).append(",\"plates\":[");
                int k = 0;
                for (int s : new int[] {1, -1}) {
                    for (double[] p : h.plates(s)) {
                        if (k++ > 0) b.append(',');
                        b.append(vec(p));
                    }
                }
                b.append("]}");
            }
            b.append(']');
        }
        b.append(",\"robot\":{");
        synchronized (r) {
            field(b, "drive", r.driveType).append(',');
            b.append("\"x\":").append(fmt(r.x)).append(',');
            b.append("\"y\":").append(fmt(r.y)).append(',');
            b.append("\"heading\":").append(fmt(r.headingDeg)).append(',');
        }
        b.append("\"imu\":").append(fmt(r.imuYaw())).append(',');
        b.append("\"shots\":").append(r.shots()).append(',');
        field(b, "bumped", r.bumpedInto).append(',');
        field(b, "field", r.field.season).append(',');
        b.append("\"length\":").append(fmt(r.lengthIn)).append(',');
        b.append("\"width\":").append(fmt(r.widthIn)).append(',');
        b.append("\"height\":").append(fmt(r.heightIn)).append(',');
        b.append("\"fieldModel\":").append(r.fieldModel != null).append(',');
        b.append("\"fieldModelVersion\":").append(modelTime(r)).append(',');
        b.append("\"robotModels\":").append(r.shapes.cad == null ? 0 : r.shapes.cad.meshes.size()).append(',');
        b.append("\"hasImu\":").append(!r.imuName.isEmpty()).append(',');
        // The robot as shapes (robot frame: f forward, l left, u up, inches) and its size now and at most.
        b.append("\"shapes\":[");
        List<SimShapes.Piece> pieces = r.shapes.pieces();
        for (int i = 0; i < pieces.size(); i++) {
            SimShapes.Piece p = pieces.get(i);
            if (i > 0) b.append(',');
            b.append('{');
            field(b, "name", p.shape).append(',');
            field(b, "kind", p.kind).append(',');
            field(b, "color", p.color).append(',');
            b.append("\"c\":").append(vec(p.center)).append(",\"ef\":").append(vec(p.ef)).append(",\"el\":").append(vec(p.el))
                    .append(",\"eu\":").append(vec(p.eu)).append(",\"size\":").append(vec(new double[] {p.sf, p.sl, p.su}))
                    .append(",\"spin\":").append(fmt(p.spinDeg))
                    .append(",\"run\":").append(fmt(p.run)).append(",\"flow\":").append(p.flow)
                    .append(",\"moving\":").append(p.moving);
            if (p.mesh != null) {
                // the CAD mesh drawn in the box's place (GET /robot-model?i=N), and where it is now
                b.append(",\"model\":").append(r.shapes.cad.meshes.indexOf(p.mesh)).append(",\"m\":[");
                for (int k = 0; k < 12; k++) b.append(k > 0 ? "," : "").append(fmt(p.meshFrame[k / 4][k % 4]));
                b.append(']');
            }
            b.append('}');
        }
        b.append("],\"size\":{");
        SimShapes.Extent now = r.shapes.measure(pieces), most = r.shapes.most;
        b.append("\"now\":").append(vec(new double[] {now.lengthIn(), now.widthIn(), now.top})).append(',');
        b.append("\"most\":").append(vec(new double[] {most.lengthIn(), most.widthIn(), most.top})).append(',');
        b.append("\"startOk\":").append(now.fits(18, 18, 18)).append(',');
        b.append("\"matchOk\":").append(now.fits(18, 24, 29)).append(',');
        b.append("\"mostOk\":").append(r.shapes.mostOk).append(',');
        b.append("\"floorOk\":").append(now.aboveFloor()).append(',');
        b.append("\"below\":").append(fmt(Math.max(0, -now.bottom))).append(',');
        field(b, "mostPose", r.shapes.mostPose).append(',');
        b.append("\"poses\":").append(r.shapes.posesTried).append(',');
        b.append("\"hingeStep\":").append(fmt(r.shapes.hingeStepDeg)).append('}');
        b.append('}');
        b.append('}');
        return b.toString();
    }

    // The computer's WiFi addresses, so a phone knows where to connect.
    private static List<String> addresses() {
        List<String> out = new java.util.ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address) out.add(a.getHostAddress());
                }
            }
        } catch (java.net.SocketException ignored) {
            // no network: nothing to show
        }
        return out;
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "null";
        return String.format(Locale.US, "%.4f", v);
    }

    private static long modelTime(SimRobot r) {
        try {
            return r.fieldModel == null ? 0 : java.nio.file.Files.getLastModifiedTime(r.fieldModel).toMillis();
        } catch (java.io.IOException e) {
            return 0;
        }
    }

    private static String counts(Map<String, Integer> m) {
        StringBuilder b = new StringBuilder("{");
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (b.length() > 1) b.append(',');
            string(b, e.getKey()).append(':').append(e.getValue());
        }
        return b.append('}').toString();
    }

    private static String vec(double[] v) {
        return "[" + fmt(v[0]) + "," + fmt(v[1]) + "," + fmt(v[2]) + "]";
    }

    private static StringBuilder field(StringBuilder b, String name, String value) {
        b.append('"').append(name).append("\":");
        return string(b, value);
    }

    private static void strings(StringBuilder b, List<String> list) {
        b.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(',');
            string(b, list.get(i));
        }
        b.append(']');
    }

    private static StringBuilder string(StringBuilder b, String s) {
        b.append('"');
        if (s != null) {
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"': b.append("\\\""); break;
                    case '\\': b.append("\\\\"); break;
                    case '\n': b.append("\\n"); break;
                    case '\r': break;
                    case '\t': b.append("\\t"); break;
                    default:
                        if (c < 0x20) b.append(String.format(Locale.US, "\\u%04x", (int) c));
                        else b.append(c);
                }
            }
        }
        return b.append('"');
    }
}
