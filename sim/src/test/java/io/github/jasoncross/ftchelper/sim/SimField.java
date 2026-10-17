package io.github.jasoncross.ftchelper.sim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A season's field, from a field file (sim/fields/<name>.json): the field elements (solid parts the
 * robot bumps into, floor zones and lines, things overhead) and the AprilTags. One file per season,
 * so a new season is a new file, not new code. The frame is the simulator's: inches from the field
 * center, +x to the right and +y away from the drivers (drawn at the bottom), z up from the tiles.
 */
final class SimField {
    /** One field element. */
    static final class Element {
        final String name, kind, color;
        final List<double[]> polygon;   // footprint, x/y in inches
        final List<double[]> hull;      // its convex hull (for bumping)
        final double zMin, zMax;

        Element(Map<String, Object> m) {
            name = SimJsonReader.string(m, "name", "?");
            kind = SimJsonReader.string(m, "kind", "solid");
            color = SimJsonReader.string(m, "color", "#888888");
            polygon = new ArrayList<>();
            for (Object p : SimJsonReader.array(m.getOrDefault("polygon", Collections.emptyList()), name + " polygon")) {
                List<Object> xy = SimJsonReader.array(p, name + " point");
                polygon.add(new double[] {((Number) xy.get(0)).doubleValue(), ((Number) xy.get(1)).doubleValue()});
            }
            hull = convexHull(polygon);
            zMin = SimJsonReader.number(m, "zMin", 0);
            zMax = SimJsonReader.number(m, "zMax", 0);
        }

        /** True if the robot bumps into it: a solid part that starts below the robot's top. */
        boolean blocks(double robotHeightIn) {
            return kind.equals("solid") && zMin < robotHeightIn && hull.size() >= 3;
        }
    }

    /** One AprilTag. */
    static final class Tag {
        final int id;
        final String name;
        final double sizeIn;
        // Where it is now. A tag on a HIVE's CELL has two places (the field file's "tipped" is the other one): flip()
        // swaps them when the HIVE tips.
        volatile double x, y, z, facingDeg, pitchDeg;
        volatile double[] up;   // the tag's printed up (unit vector), or null: then "up" is the world's up turned into the tag's plane
        private double[] other;           // the other place: x, y, z, facingDeg, pitchDeg (null: it does not move)
        private double[] otherUp;
        private final double[] start;     // the place at the start of a match, and its up
        private final double[] startUp;

        Tag(Map<String, Object> m) {
            id = (int) SimJsonReader.number(m, "id", -1);
            name = SimJsonReader.string(m, "name", "Tag " + id);
            x = SimJsonReader.number(m, "x", 0);
            y = SimJsonReader.number(m, "y", 0);
            z = SimJsonReader.number(m, "z", 0);
            facingDeg = SimJsonReader.number(m, "facingDeg", 0);
            pitchDeg = SimJsonReader.number(m, "pitchDeg", 0);   // 0 = the face points level; -90 = straight down
            sizeIn = SimJsonReader.number(m, "sizeIn", 6.5);
            double[] u = null;
            if (m.get("up") instanceof java.util.List) {
                List<Object> l = SimJsonReader.array(m.get("up"), name + " up");
                if (l.size() == 3) u = new double[] {((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(), ((Number) l.get(2)).doubleValue()};
            }
            up = u;
            if (m.get("tipped") instanceof Map) {
                Map<String, Object> t = SimJsonReader.object(m.get("tipped"), name + " tipped");
                other = new double[] {SimJsonReader.number(t, "x", x), SimJsonReader.number(t, "y", y), SimJsonReader.number(t, "z", z),
                        SimJsonReader.number(t, "facingDeg", facingDeg), SimJsonReader.number(t, "pitchDeg", pitchDeg)};
                if (t.get("up") instanceof java.util.List) {
                    List<Object> l = SimJsonReader.array(t.get("up"), name + " tipped up");
                    if (l.size() == 3) otherUp = new double[] {((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(), ((Number) l.get(2)).doubleValue()};
                }
            }
            start = new double[] {x, y, z, facingDeg, pitchDeg};
            startUp = up;
        }

        /**
         * Where it is now, read in one go (a HIVE may be tipping it on another thread): x, y, z, facingDeg,
         * pitchDeg, then its printed up (NaN when not given).
         */
        synchronized double[] pose() {
            double[] u = up != null ? up : new double[] {Double.NaN, Double.NaN, Double.NaN};
            return new double[] {x, y, z, facingDeg, pitchDeg, u[0], u[1], u[2]};
        }

        /** To its other place (its HIVE tipped). */
        synchronized void flip() {
            if (other == null) return;
            double[] now = {x, y, z, facingDeg, pitchDeg};
            double[] nowUp = up;
            x = other[0]; y = other[1]; z = other[2]; facingDeg = other[3]; pitchDeg = other[4];
            up = otherUp;
            other = now;
            otherUp = nowUp;
        }

        /** Back to where the match starts it. */
        synchronized void reset() {
            if (other != null && (x != start[0] || y != start[1] || z != start[2])) flip();
        }

        /** True when it is not where the match starts it (its HIVE is tipped). */
        boolean flipped() {
            return x != start[0] || y != start[1] || z != start[2];
        }
    }

    /**
     * A HIVE that tips (manual 9.6, 10.5.1): two CELLs on an arm that turns about a pivot along y. In each CELL's own
     * frame: a = along the arm from the pivot, w = along y, h = across the CELL's floor (0 = the floor). The CELL is
     * open at a = aOut and closed at aIn; its cross-section is the opening pentagon. upSide: the CELL toward +x (+1)
     * or -x (-1) is up. When the up CELL holds tipGrams or more, the HIVE turns tipDeg and the other CELL comes up.
     */
    static final class Hive {
        final String name, alliance, element;
        final double[] pivot;
        final int startUp;
        final double tipDeg, tipGrams, aIn, aOut, floor;
        final List<double[]> opening = new ArrayList<>();
        final List<Integer> tagsPlus = new ArrayList<>(), tagsMinus = new ArrayList<>();
        volatile int up;         // +1 or -1: which CELL is up now
        volatile int tips;       // how many times it has tipped since the start

        Hive(Map<String, Object> m) {
            name = SimJsonReader.string(m, "name", "HIVE");
            alliance = SimJsonReader.string(m, "alliance", "");
            element = SimJsonReader.string(m, "element", name);
            List<Object> pv = SimJsonReader.array(m.get("pivot"), name + " pivot");
            pivot = new double[] {((Number) pv.get(0)).doubleValue(), ((Number) pv.get(1)).doubleValue(), ((Number) pv.get(2)).doubleValue()};
            startUp = SimJsonReader.number(m, "upSide", 1) >= 0 ? 1 : -1;
            up = startUp;
            tipDeg = SimJsonReader.number(m, "tipDeg", 60);
            tipGrams = SimJsonReader.number(m, "tipGrams", 187);
            Map<String, Object> c = SimJsonReader.object(m.get("cell"), name + " cell");
            aIn = SimJsonReader.number(c, "aIn", 9.42);
            aOut = SimJsonReader.number(c, "aOut", 21.44);
            floor = SimJsonReader.number(c, "floor", 0);
            for (Object o : SimJsonReader.array(c.get("opening"), name + " opening")) {
                List<Object> xy = SimJsonReader.array(o, name + " opening point");
                opening.add(new double[] {((Number) xy.get(0)).doubleValue(), ((Number) xy.get(1)).doubleValue()});
            }
            if (m.get("tags") instanceof Map) {
                Map<String, Object> t = SimJsonReader.object(m.get("tags"), name + " tags");
                for (Object o : SimJsonReader.array(t.getOrDefault("+x", Collections.emptyList()), name)) tagsPlus.add(((Number) o).intValue());
                for (Object o : SimJsonReader.array(t.getOrDefault("-x", Collections.emptyList()), name)) tagsMinus.add(((Number) o).intValue());
            }
        }

        /**
         * The CELL on side s (+1: toward +x) now: its arm direction, y, and the direction across its floor, as
         * rows {a, w, h} (unit vectors, field frame). The up CELL's arm rises tipDeg / 2 above level; the other
         * CELL is where it would be up, turned back by the tip.
         */
        double[][] cellAxes(int s) {
            double half = Math.toRadians(tipDeg / 2), c = Math.cos(half), sn = Math.sin(half);
            double[] a = {s * c, 0, sn}, h = {-s * sn, 0, c};
            if (s != up) {
                double psi = Math.toRadians(-up * tipDeg);
                a = turnY(a, psi);
                h = turnY(h, psi);
            }
            return new double[][] {a, {0, 1, 0}, h};
        }

        /** A point (field inches) in the CELL on side s: {a, w, h}. */
        double[] inCell(int s, double[] p) {
            double[][] ax = cellAxes(s);
            double[] r = {p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]};
            return new double[] {dot(r, ax[0]), dot(r, ax[1]), dot(r, ax[2]) - floor};
        }

        /** True when a point (a piece's centre) is inside the CELL on side s. */
        boolean inside(int s, double[] p) {
            double[] q = inCell(s, p);
            if (q[0] <= aIn || q[0] >= aOut) return false;
            boolean in = false;     // the opening pentagon: an even-odd test
            for (int i = 0, j = opening.size() - 1; i < opening.size(); j = i++) {
                double[] pi = opening.get(i), pj = opening.get(j);
                if ((pi[1] > q[2]) != (pj[1] > q[2]) && q[1] < (pj[0] - pi[0]) * (q[2] - pi[1]) / (pj[1] - pi[1]) + pi[0]) in = !in;
            }
            return in;
        }

        /**
         * The CELL on side s as thin plates (for the physics and the picture): one along each edge of the opening
         * pentagon, the length of the CELL, and one closing its back. Each: centre x y z, then its own three
         * directions (length, width, thickness: 9 numbers), then its sizes (inches).
         */
        List<double[]> plates(int s) {
            final double t = 0.25;                                   // wall thickness (in): thin polycarbonate and metal
            double[][] ax = cellAxes(s);
            List<double[]> out = new ArrayList<>();
            double len = aOut - aIn, am = (aIn + aOut) / 2;
            for (int i = 0; i < opening.size(); i++) {
                double[] p = opening.get(i), q = opening.get((i + 1) % opening.size());
                double dw = q[0] - p[0], dh = q[1] - p[1], L = Math.hypot(dw, dh);
                if (L < 1e-6) continue;
                double nw = dh / L, nh = -dw / L;                    // outward (the pentagon is counterclockwise)
                double w = (p[0] + q[0]) / 2 + nw * t / 2, h = (p[1] + q[1]) / 2 + nh * t / 2;
                out.add(plate(ax, am, w, h, ax[0], comb(ax, dw / L, dh / L), comb(ax, nw, nh), len, L, t));
            }
            double wMax = 0, hMax = 0;
            for (double[] p : opening) { wMax = Math.max(wMax, Math.abs(p[0])); hMax = Math.max(hMax, p[1]); }
            out.add(plate(ax, aIn - t / 2, 0, hMax / 2, ax[1], ax[2], ax[0], 2 * wMax + 2 * t, hMax, t));
            return out;
        }

        // A plate at (a, w, h) in the CELL's frame with these three directions and sizes, in the field frame.
        private double[] plate(double[][] ax, double a, double w, double h, double[] e1, double[] e2, double[] e3,
                               double s1, double s2, double s3) {
            double[] c = new double[3];
            for (int k = 0; k < 3; k++) c[k] = pivot[k] + a * ax[0][k] + w * ax[1][k] + (h + floor) * ax[2][k];
            return new double[] {c[0], c[1], c[2], e1[0], e1[1], e1[2], e2[0], e2[1], e2[2], e3[0], e3[1], e3[2], s1, s2, s3};
        }

        // w and h directions combined into one field direction
        private static double[] comb(double[][] ax, double w, double h) {
            return new double[] {w * ax[1][0] + h * ax[2][0], w * ax[1][1] + h * ax[2][1], w * ax[1][2] + h * ax[2][2]};
        }

        /** Where a point goes when the HIVE tips now (it turns with the HIVE about the pivot). */
        double[] tipped(double[] p) {
            double[] r = {p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]};
            double[] t = turnY(r, Math.toRadians(up * tipDeg));
            return new double[] {pivot[0] + t[0], pivot[1] + t[1], pivot[2] + t[2]};
        }

        static double[] turnY(double[] v, double ang) {
            double c = Math.cos(ang), s = Math.sin(ang);
            return new double[] {v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c};
        }

        private static double dot(double[] a, double[] b) {
            return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        }
    }

    /** A kind of game piece: a ball this size, mass and colour. */
    static final class PieceType {
        final String name, color;
        final double diameterIn, massG, bounce;
        final double dragCd;            // air drag coefficient (a ball: about 0.5; not measured for these pieces)

        PieceType(Map<String, Object> m) {
            name = SimJsonReader.string(m, "name", "piece");
            color = SimJsonReader.string(m, "color", "#f2d22e");
            diameterIn = SimJsonReader.number(m, "diameterIn", 3);
            massG = SimJsonReader.number(m, "massG", 30);
            bounce = SimJsonReader.number(m, "bounce", 0.3);
            dragCd = SimJsonReader.number(m, "dragCd", 0.5);
        }
    }

    /** A piece where the match starts it. */
    static final class StagedPiece {
        final String type;
        final double x, y, z;

        StagedPiece(Map<String, Object> m) {
            type = SimJsonReader.string(m, "type", "");
            x = SimJsonReader.number(m, "x", 0);
            y = SimJsonReader.number(m, "y", 0);
            z = SimJsonReader.number(m, "z", 0);
        }
    }

    /**
     * Pieces staged INSIDE a field element (BIOBUZZ: 4 POLLEN in each FLOWER, 3 NECTAR in each up CELL). The
     * element is a solid block in the physics, so these are not loose pieces: they hold their places. take =
     * "bottom": an intake (or claw) at the element's opening takes the lowest one and the rest drop down one
     * place; "none": they stay (the simulator does not tip HIVEs).
     */
    static final class Stack {
        final String where, take;
        final List<StagedPiece> pieces = new ArrayList<>();

        Stack(Map<String, Object> m) {
            where = SimJsonReader.string(m, "where", "?");
            take = SimJsonReader.string(m, "take", "none");
            for (Object o : SimJsonReader.array(m.getOrDefault("pieces", Collections.emptyList()), where + " pieces")) {
                pieces.add(new StagedPiece(SimJsonReader.object(o, "piece")));
            }
        }
    }

    /**
     * A place to score: an opening of radiusIn around (x, y), z above the tiles. A piece over it (its edge
     * within the opening), down to it, not going up and not flying past fast, scores (see SimPieces).
     */
    static final class Goal {
        final String name;
        final double x, y, z, radiusIn;

        Goal(Map<String, Object> m) {
            name = SimJsonReader.string(m, "name", "goal");
            x = SimJsonReader.number(m, "x", 0);
            y = SimJsonReader.number(m, "y", 0);
            z = SimJsonReader.number(m, "z", 0);
            radiusIn = SimJsonReader.number(m, "radiusIn", 2);
        }
    }

    final Map<String, PieceType> pieceTypes = new java.util.LinkedHashMap<>();
    final List<StagedPiece> pieces = new ArrayList<>();
    final List<Goal> goals = new ArrayList<>();
    final List<Stack> stacks = new ArrayList<>();
    final List<Hive> hives = new ArrayList<>();
    final Map<String, Integer> preload = new java.util.LinkedHashMap<>();

    final String name, season, frameNote;
    final double size;
    // Half the measured inside of the walls (the robot stops there); the nominal size if not measured.
    final double wallHalf;
    final List<Element> elements = new ArrayList<>();
    final List<Tag> tags = new ArrayList<>();
    final String json;   // the file as read, for the page

    private SimField(String name, String text) {
        this.name = name;
        this.json = text;
        Map<String, Object> root = SimJsonReader.object(SimJsonReader.parse(text), name);
        season = SimJsonReader.string(root, "season", name);
        frameNote = SimJsonReader.string(root, "frameNote", "");
        Map<String, Object> field = root.get("field") instanceof Map
                ? SimJsonReader.object(root.get("field"), "field") : Collections.<String, Object>emptyMap();
        size = SimJsonReader.number(field, "size", 144);
        wallHalf = SimJsonReader.number(field, "insideWallSize", size) / 2;
        for (Object o : SimJsonReader.array(root.getOrDefault("elements", Collections.emptyList()), "elements")) {
            elements.add(new Element(SimJsonReader.object(o, "element")));
        }
        for (Object o : SimJsonReader.array(root.getOrDefault("tags", Collections.emptyList()), "tags")) {
            tags.add(new Tag(SimJsonReader.object(o, "tag")));
        }
        for (Object o : SimJsonReader.array(root.getOrDefault("pieceTypes", Collections.emptyList()), "pieceTypes")) {
            PieceType t = new PieceType(SimJsonReader.object(o, "pieceType"));
            pieceTypes.put(t.name, t);
        }
        for (Object o : SimJsonReader.array(root.getOrDefault("pieces", Collections.emptyList()), "pieces")) {
            StagedPiece sp = new StagedPiece(SimJsonReader.object(o, "piece"));
            if (!pieceTypes.containsKey(sp.type)) throw new IllegalArgumentException(name + ": a piece of unknown type \"" + sp.type + "\"");
            pieces.add(sp);
        }
        for (Object o : SimJsonReader.array(root.getOrDefault("stacks", Collections.emptyList()), "stacks")) {
            Stack st = new Stack(SimJsonReader.object(o, "stack"));
            for (StagedPiece sp : st.pieces) {
                if (!pieceTypes.containsKey(sp.type)) throw new IllegalArgumentException(name + ": a piece of unknown type \"" + sp.type + "\" in " + st.where);
            }
            stacks.add(st);
        }
        for (Object o : SimJsonReader.array(root.getOrDefault("hives", Collections.emptyList()), "hives")) {
            hives.add(new Hive(SimJsonReader.object(o, "hive")));
        }
        for (Object o : SimJsonReader.array(root.getOrDefault("goals", Collections.emptyList()), "goals")) {
            goals.add(new Goal(SimJsonReader.object(o, "goal")));
        }
        if (root.get("preload") instanceof Map) {
            for (Map.Entry<String, Object> e : SimJsonReader.object(root.get("preload"), "preload").entrySet()) {
                if (!pieceTypes.containsKey(e.getKey())) throw new IllegalArgumentException(name + ": preload of unknown type \"" + e.getKey() + "\"");
                preload.put(e.getKey(), ((Number) e.getValue()).intValue());
            }
        }
    }

    /** The tag with this id, or null. */
    Tag tag(int id) {
        for (Tag t : tags) if (t.id == id) return t;
        return null;
    }

    /** Tips a HIVE: the other CELL comes up, and the tags on both CELLs move with it. */
    void tip(Hive h) {
        h.up = -h.up;
        h.tips++;
        for (int id : h.tagsPlus) { Tag t = tag(id); if (t != null) t.flip(); }
        for (int id : h.tagsMinus) { Tag t = tag(id); if (t != null) t.flip(); }
    }

    /** Every HIVE and tag back to the start of a match. */
    void resetHives() {
        for (Hive h : hives) { h.up = h.startUp; h.tips = 0; }
        for (Tag t : tags) t.reset();
    }

    /** An empty 12 ft field (no elements). */
    static SimField empty() {
        return new SimField("empty", "{\"season\":\"Empty field\",\"field\":{\"size\":144},\"elements\":[],\"tags\":[]}");
    }

    /** A field by name (sim/fields/<name>.json on the classpath) or by file path. */
    static SimField load(String nameOrPath) throws IOException {
        Path p = Paths.get(nameOrPath);
        if (Files.isRegularFile(p)) return new SimField(p.getFileName().toString().replace(".json", ""), new String(Files.readAllBytes(p), "UTF-8"));
        try (InputStream in = SimField.class.getResourceAsStream("/" + nameOrPath + ".json")) {
            if (in == null) throw new IllegalArgumentException("Robot file: no field \"" + nameOrPath + "\" (sim/fields/" + nameOrPath + ".json)");
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new SimField(nameOrPath, b.toString("UTF-8"));
        }
    }

    /** The first solid element a robot with this footprint (corners) bumps into, or null. */
    Element bumps(List<double[]> robot, double robotHeightIn) {
        for (Element e : elements) {
            if (e.blocks(robotHeightIn) && overlap(robot, e.hull)) return e;
        }
        return null;
    }

    // Separating-axis test for two convex polygons: they overlap unless some edge direction separates them.
    static boolean overlap(List<double[]> a, List<double[]> b) {
        return !separated(a, b) && !separated(b, a);
    }

    private static boolean separated(List<double[]> a, List<double[]> b) {
        for (int i = 0; i < a.size(); i++) {
            double[] p = a.get(i), q = a.get((i + 1) % a.size());
            double nx = -(q[1] - p[1]), ny = q[0] - p[0];
            double aMin = Double.MAX_VALUE, aMax = -Double.MAX_VALUE, bMin = Double.MAX_VALUE, bMax = -Double.MAX_VALUE;
            for (double[] v : a) { double d = v[0] * nx + v[1] * ny; aMin = Math.min(aMin, d); aMax = Math.max(aMax, d); }
            for (double[] v : b) { double d = v[0] * nx + v[1] * ny; bMin = Math.min(bMin, d); bMax = Math.max(bMax, d); }
            if (aMax < bMin || bMax < aMin) return true;
        }
        return false;
    }

    // Andrew's monotone chain: the convex hull, counterclockwise.
    static List<double[]> convexHull(List<double[]> pts) {
        List<double[]> p = new ArrayList<>(pts);
        if (p.size() < 3) return p;
        Collections.sort(p, (u, v) -> u[0] != v[0] ? Double.compare(u[0], v[0]) : Double.compare(u[1], v[1]));
        double[][] h = new double[2 * p.size()][];
        int k = 0;
        for (double[] v : p) {
            while (k >= 2 && cross(h[k - 2], h[k - 1], v) <= 0) k--;
            h[k++] = v;
        }
        for (int i = p.size() - 2, t = k + 1; i >= 0; i--) {
            double[] v = p.get(i);
            while (k >= t && cross(h[k - 2], h[k - 1], v) <= 0) k--;
            h[k++] = v;
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < k - 1; i++) out.add(h[i]);
        return out;
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }
}
