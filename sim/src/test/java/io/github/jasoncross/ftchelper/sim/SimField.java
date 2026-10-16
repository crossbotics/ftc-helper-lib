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
        final double x, y, z, facingDeg, pitchDeg, sizeIn;

        Tag(Map<String, Object> m) {
            id = (int) SimJsonReader.number(m, "id", -1);
            name = SimJsonReader.string(m, "name", "Tag " + id);
            x = SimJsonReader.number(m, "x", 0);
            y = SimJsonReader.number(m, "y", 0);
            z = SimJsonReader.number(m, "z", 0);
            facingDeg = SimJsonReader.number(m, "facingDeg", 0);
            pitchDeg = SimJsonReader.number(m, "pitchDeg", 0);   // 0 = the face points level; -90 = straight down
            sizeIn = SimJsonReader.number(m, "sizeIn", 6.5);
        }
    }

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
