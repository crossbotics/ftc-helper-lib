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
        b.append("],\"robot\":{");
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
        b.append("\"hasImu\":").append(!r.imuName.isEmpty()).append('}');
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
