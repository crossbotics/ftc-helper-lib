package com.crossbotics.ftchelper.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the page needs to draw a smooth picture and its layers, kept apart from SimJson (the whole state):
 *
 * - frame(): a small "where everything is" update, sent 50 times a second on GET /stream. It carries the
 *   physics time, so the page can glide between two updates instead of jumping to each one.
 * - sight(), arcs(): the Layers menu's camera sight lines (with the reason a tag is not seen) and the
 *   predicted shot arc. They ride on the whole state (10 times a second).
 * - seeMap(): GET /seemap, the floor map of where a camera can see tags.
 *
 * Everything here only reads the simulator; nothing changes the game.
 */
final class SimView {
    private SimView() { }

    /**
     * The fast update, as JSON:
     * {"t": physics seconds, "e": jump count (the page does not glide across a change),
     *  "r": [x, y, heading] (field inches, degrees),
     *  "p": [[key, type, x, y, z, qw, qx, qy, qz], ...] every piece (SimPieces.drawnForView),
     *  "s": [[center f,l,u, ef, el, eu, spin degrees (, 12 CAD frame numbers)], ...] each robot shape, robot frame,
     *  "c": [[x, y, z, picture-right x,y,z, picture-down x,y,z, lens x,y,z], ...] each camera on the field}
     */
    static String frame(SimEngine sim) {
        SimRobot r = sim.robot;
        StringBuilder b = new StringBuilder(2048);
        // One lock for everything: the time and the positions belong together.
        synchronized (r) {
            b.append("{\"t\":").append(f(sim.simSeconds(), 4)).append(",\"e\":").append(sim.jumps.get());
            b.append(",\"r\":[").append(f(r.x, 3)).append(',').append(f(r.y, 3)).append(',').append(f(r.headingDeg, 3)).append(']');
            b.append(",\"p\":[");
            List<double[]> ps = r.pieces.drawnForView();
            for (int i = 0; i < ps.size(); i++) {
                double[] p = ps.get(i);
                b.append(i > 0 ? ",[" : "[").append((long) p[0]).append(',').append((int) p[1]);
                for (int k = 2; k < 5; k++) b.append(',').append(f(p[k], 2));
                for (int k = 5; k < 9; k++) b.append(',').append(f(p[k], 3));
                b.append(']');
            }
            b.append("],\"s\":[");
            List<SimShapes.Piece> shapes = r.shapes.pieces();
            for (int i = 0; i < shapes.size(); i++) {
                SimShapes.Piece p = shapes.get(i);
                b.append(i > 0 ? ",[" : "[");
                vec(b, p.center, 3).append(',');
                vec(b, p.ef, 4).append(',');
                vec(b, p.el, 4).append(',');
                vec(b, p.eu, 4).append(',').append(f(p.spinDeg, 2));
                if (p.mesh != null && p.meshFrame != null) {
                    for (int k = 0; k < 12; k++) b.append(',').append(f(p.meshFrame[k / 4][k % 4], 4));
                }
                b.append(']');
            }
            b.append("],\"c\":[");
            int i = 0;
            for (SimCamera c : r.cameras.values()) {
                double[][] pose = c.pose();
                b.append(i++ > 0 ? ",[" : "[");
                vec(b, pose[0], 3).append(',');
                vec(b, pose[1], 4).append(',');
                vec(b, pose[2], 4).append(',');
                vec(b, pose[3], 4).append(']');
            }
            b.append("]}");
        }
        return b.toString();
    }

    /**
     * One camera's sight of every tag: [[id, "why", pixels, degrees turned away, distance in], ...]. "why" is seen,
     * behind, left, right, above, below (out of the picture that way), slanted (more than 60 degrees), small
     * (fewer pixels than the finder needs at its decimation) or light (the pretend lighting: wrong setting).
     */
    static void sight(StringBuilder b, SimCamera c, SimField field) {
        List<SimCamera.Sight> list = c.sight(field, c.decimationForPage(), c.pose());
        b.append('[');
        for (int i = 0; i < list.size(); i++) {
            SimCamera.Sight s = list.get(i);
            b.append(i > 0 ? ",[" : "[").append(s.id).append(",\"").append(s.why).append("\",").append(f(s.pixels, 1))
                    .append(',').append(f(s.angleDeg, 1)).append(',').append(f(s.distIn, 1)).append(']');
        }
        b.append(']');
    }

    // Gravity, inches per second squared.
    static final double G_IN = 9.81 / SimWorld.M;

    /**
     * Each launcher's predicted shot if it fired now, as JSON for the page's arc layer:
     * [{"name", "piece", "speed" in/s, "angle" degrees, "peak" in above the tiles, "why" (empty, or why there is no
     * shot), "pts": [[forward, left, up], ...] robot frame at this moment}]. The flight uses the same air as the
     * physics (drag and spin lift) but no contacts: it does not know about the goal, the walls or other pieces.
     */
    static void arcs(StringBuilder b, SimRobot r) {
        List<SimPieces.ShotNow> shots = r.pieces.shotsNow();
        double rx, ry, h;
        synchronized (r) {
            rx = r.x;
            ry = r.y;
            h = Math.toRadians(r.headingDeg);
        }
        double fx = -Math.sin(h), fy = Math.cos(h), lx = -Math.cos(h), ly = -Math.sin(h);
        b.append('[');
        for (int i = 0; i < shots.size(); i++) {
            SimPieces.ShotNow s = shots.get(i);
            if (i > 0) b.append(',');
            b.append("{\"name\":");
            str(b, s.launcher).append(",\"piece\":");
            str(b, s.piece).append(",\"speed\":").append(f(s.speedIn, 1)).append(",\"angle\":").append(f(s.angleDeg, 1))
                    .append(",\"why\":");
            str(b, s.why);
            List<double[]> pts = s.why.isEmpty() ? fly(s, r.field.wallHalf) : new ArrayList<double[]>();
            double peak = 0;
            for (double[] p : pts) peak = Math.max(peak, p[2]);
            b.append(",\"peak\":").append(f(peak, 1)).append(",\"pts\":[");
            for (int k = 0; k < pts.size(); k++) {
                double dx = pts.get(k)[0] - rx, dy = pts.get(k)[1] - ry;
                b.append(k > 0 ? ",[" : "[").append(f(dx * fx + dy * fy, 1)).append(',').append(f(dx * lx + dy * ly, 1))
                        .append(',').append(f(pts.get(k)[2], 1)).append(']');
            }
            b.append("]}");
        }
        b.append(']');
    }

    /**
     * A free flight from a shot's start: gravity, air drag and spin lift as SimWorld.air has them, no contacts.
     * Points every 1/50 s (field inches) until the piece is back on the tiles, leaves the field on its way down,
     * or 3 s pass.
     */
    static List<double[]> fly(SimPieces.ShotNow s, double wallHalf) {
        List<double[]> out = new ArrayList<>();
        double m = SimWorld.M, mass = Math.max(1e-3, s.massG / 1000), r = s.diameterIn / 2 * m;
        double area = Math.PI * r * r;
        double[] p = {s.at[0] * m, s.at[1] * m, s.at[2] * m}, v = {s.vel[0] * m, s.vel[1] * m, s.vel[2] * m};
        double[] w = s.spin;
        double dt = 0.002, next = 0;
        for (double t = 0; t <= 3; t += dt) {
            if (t >= next - 1e-9) {
                out.add(new double[] {p[0] / m, p[1] / m, p[2] / m});
                next += 0.02;
            }
            if (p[2] < r && v[2] < 0 && t > 0) break;                           // back on the tiles
            // left the field, on its way down (one still rising goes on, so its peak is right)
            if ((Math.abs(p[0]) > (wallHalf + 6) * m || Math.abs(p[1]) > (wallHalf + 6) * m) && v[2] <= 0) break;
            double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            double[] a = {0, 0, -9.81};
            if (speed > 1e-6) {
                double drag = 0.5 * SimWorld.AIR_DENSITY * s.dragCd * area * speed / mass;
                double spin = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]);
                double cl = r * spin / speed, k = 0.5 * SimWorld.AIR_DENSITY * area * r * (cl > 0.5 ? 0.5 / cl : 1) / mass;
                double[] lift = {w[1] * v[2] - w[2] * v[1], w[2] * v[0] - w[0] * v[2], w[0] * v[1] - w[1] * v[0]};
                for (int k2 = 0; k2 < 3; k2++) a[k2] += -drag * v[k2] + k * lift[k2];
            }
            for (int k2 = 0; k2 < 3; k2++) {
                v[k2] += a[k2] * dt;
                p[k2] += v[k2] * dt;
            }
        }
        out.add(new double[] {p[0] / m, p[1] / m, Math.max(0, p[2] / m)});
        return out;
    }

    /**
     * Where on the field camera number cam can see tags: the robot put at the middle of each square (step inches),
     * its turret and other parts as they are now, facing the way it faces now (anyHeading false) or turned any of
     * 24 ways (true: the best one counts). Each square: how many tags it sees (the camera's own rules, the same as
     * the OpMode gets; field parts do not block). JSON: {"step", "half", "n", "heading", "any", "decimation",
     * "counts": [n*n numbers, row by row from the bottom-left]}. Null if there is no such camera.
     */
    static String seeMap(SimRobot r, int cam, double step, boolean anyHeading) {
        if (cam < 0 || cam >= r.cameras.size()) return null;
        SimCamera c = new ArrayList<>(r.cameras.values()).get(cam);
        step = Math.max(2, Math.min(12, step));
        double half = r.field.wallHalf;
        int n = (int) Math.ceil(2 * half / step);
        double heading;
        synchronized (r) {
            heading = r.headingDeg;
        }
        double dec = c.decimationForPage();
        StringBuilder b = new StringBuilder(8 * n * n + 200);
        b.append("{\"step\":").append(f(step, 2)).append(",\"half\":").append(f(half, 2)).append(",\"n\":").append(n)
                .append(",\"heading\":").append(f(heading, 1)).append(",\"any\":").append(anyHeading)
                .append(",\"decimation\":").append(f(dec, 1)).append(",\"counts\":[");
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double x = -half + (i + 0.5) * step, y = -half + (j + 0.5) * step;
                int best = 0;
                int turns = anyHeading ? 24 : 1;
                for (int k = 0; k < turns; k++) {
                    double hd = anyHeading ? k * 15 : heading;
                    best = Math.max(best, c.visibleTags(r.field, dec, c.poseAt(x, y, hd)).size());
                }
                b.append(i + j > 0 ? "," : "").append(best);
            }
        }
        return b.append("]}").toString();
    }

    /** The Driver Station's OpMode list, as JSON (GET /opmodes; the stream only sends its key). */
    static String opModes(SimDriverStation ds) {
        StringBuilder b = new StringBuilder(4096).append('[');
        boolean first = true;
        // How well each OpMode's Robot class fits the robot file (BuiltAuto.score; null = no Robot class):
        // the page starts on the best fit, not the first name in the list.
        java.util.Map<Class<?>, Integer> fits = new java.util.HashMap<>();
        for (SimDriverStation.Entry e : ds.opModes) {
            if (!first) b.append(',');
            first = false;
            b.append("{\"name\":");
            str(b, e.name).append(",\"group\":");
            str(b, e.group).append(",\"kind\":");
            str(b, e.kind).append(",\"disabled\":").append(e.disabled);
            Class<?> robot = BuiltAuto.robotOf(e.type);
            if (robot != null && ds.robot != null) {
                @SuppressWarnings("unchecked")
                Class<? extends com.crossbotics.ftchelper.robot.RobotDefinition> rc = (Class<? extends com.crossbotics.ftchelper.robot.RobotDefinition>) robot;
                Integer fit = fits.computeIfAbsent(robot, k -> BuiltAuto.score(rc, ds.robot));
                b.append(",\"fit\":").append(fit);
            }
            b.append('}');
        }
        return b.append(']').toString();
    }

    /** A short key that changes when the OpMode list changes (the page asks /opmodes again then). */
    static String opModesKey(SimDriverStation ds) {
        int h = 1;
        for (SimDriverStation.Entry e : ds.opModes) {
            h = 31 * h + (e.kind + "|" + e.group + "|" + e.name + "|" + e.disabled).hashCode();
        }
        return ds.opModes.size() + "-" + Integer.toHexString(h);
    }

    private static StringBuilder vec(StringBuilder b, double[] v, int places) {
        return b.append(f(v[0], places)).append(',').append(f(v[1], places)).append(',').append(f(v[2], places));
    }

    private static StringBuilder str(StringBuilder b, String s) {
        b.append('"');
        if (s != null) {
            for (char ch : s.toCharArray()) {
                if (ch == '"' || ch == '\\') b.append('\\').append(ch);
                else if (ch < 0x20) b.append(String.format(Locale.US, "\\u%04x", (int) ch));
                else b.append(ch);
            }
        }
        return b.append('"');
    }

    // A number with this many places, without trailing zeros (smaller updates); null for NaN.
    static String f(double v, int places) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "null";
        String s = String.format(Locale.US, "%." + places + "f", v);
        if (s.indexOf('.') >= 0) {
            int end = s.length();
            while (s.charAt(end - 1) == '0') end--;
            if (s.charAt(end - 1) == '.') end--;
            s = s.substring(0, end);
        }
        return s.equals("-0") ? "0" : s;
    }
}
