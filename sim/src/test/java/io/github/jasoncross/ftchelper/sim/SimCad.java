package io.github.jasoncross.ftchelper.sim;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * CAD models of robot parts (robot.model and model= / baseModel= on a shape). The team exports each part
 * from their whole-robot CAD assembly, in the robot's START pose and in the assembly's own coordinates,
 * as STL or OBJ. Then every part's mesh lines up with every other one, and the simulator moves it with
 * the shape it rides on: a part drawn where the CAD has it at the start goes where its shape goes.
 *
 * <p>The robot file says how the CAD's coordinates become the robot's:
 * <pre>
 * cad.units = mm            # mm (Onshape and Fusion's usual export) or in
 * cad.axes = y,-x,z         # the CAD axes that point the robot's forward, left and up
 * cad.origin = 0,0,0        # the robot's centre on the floor, in CAD units
 * </pre>
 * The 3D view draws the meshes; the size rules use them in place of the boxes they cover. Physics and
 * game pieces keep using the boxes (size=): a box is a far simpler thing to bump into.
 */
final class SimCad {
    // More than this (all models together) would take too much memory to read: about 0.4 GB.
    static final int MAX_TRIANGLES = 12_000_000;
    // The page draws at most this many of each model's triangles: a bigger one is simplified for the
    // picture (the size rules still use every triangle).
    static final int DRAWN_TRIANGLES = 300_000;

    /** One part's mesh: triangles in the robot frame at the start pose (forward, left, up; inches). */
    static final class Mesh {
        final String file;
        final float[] triangles;        // x y z per corner, 9 floats per triangle (as drawn: maybe simplified)
        final int readTriangles;        // how many the file had
        final double[][] hull;          // the corners of its outside (convex hull), for the size rules
        final double margin;            // how far the real mesh can be outside those corners (inches)
        final double[] lo, hi;          // its box in the robot frame at the start

        Mesh(String file, float[] triangles) {
            this.file = file;
            readTriangles = triangles.length / 9;
            this.triangles = simplify(triangles, DRAWN_TRIANGLES);
            lo = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            hi = new double[] {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int i = 0; i < triangles.length; i += 3) {
                for (int k = 0; k < 3; k++) {
                    lo[k] = Math.min(lo[k], triangles[i + k]);
                    hi[k] = Math.max(hi[k], triangles[i + k]);
                }
            }
            Hull h = outside(triangles);
            hull = h.points;
            margin = h.margin;
        }

        private byte[] bytes;

        // made once: a big mesh is tens of MB, and every page (and tablet) asks for it
        synchronized byte[] bytes() {
            if (bytes != null) return bytes;
            ByteBuffer b = ByteBuffer.allocate(triangles.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float f : triangles) b.putFloat(f);
            bytes = b.array();
            return bytes;
        }
    }

    /** How CAD coordinates become robot coordinates. */
    static final class Frame {
        final double scale;             // CAD units -> inches
        final int[] axis = new int[3];  // forward, left, up: which CAD axis (0 x, 1 y, 2 z)
        final double[] sign = new double[3];
        final double[] origin;          // the robot's centre on the floor, CAD units

        final String words;             // the settings in words, for "print cad"

        Frame(Properties p) {
            // No defaults: a wrong guess would draw every part 25 times too small, or on its side.
            if (!p.containsKey("cad.units") || !p.containsKey("cad.axes")) {
                throw new IllegalArgumentException("Robot file: CAD models need cad.units (the unit in your CAD's export window: "
                        + "mm, cm, m or in) and cad.axes (the CAD axes that point the robot's forward, left and up). "
                        + "Onshape and SolidWorks: up is z, like cad.axes = y,-x,z. Fusion: up is usually y, like cad.axes = x,-z,y.");
            }
            String units = p.getProperty("cad.units").trim().toLowerCase(Locale.US);
            if (units.equals("mm")) scale = 1 / 25.4;
            else if (units.equals("in")) scale = 1;
            else if (units.equals("cm")) scale = 1 / 2.54;
            else if (units.equals("m")) scale = 1 / 0.0254;
            else throw new IllegalArgumentException("Robot file: cad.units must be mm, cm, m or in");
            String[] a = p.getProperty("cad.axes").replace(" ", "").toLowerCase(Locale.US).split(",");
            String help = "Robot file: cad.axes is the CAD axis that points the robot's forward, left and up, "
                    + "like y,-x,z (a minus sign for the other way)";
            if (a.length != 3) throw new IllegalArgumentException(help);
            boolean[] used = new boolean[3];
            for (int i = 0; i < 3; i++) {
                String s = a[i];
                sign[i] = s.startsWith("-") ? -1 : 1;
                s = s.replaceFirst("^[+-]", "");
                if (s.length() != 1 || "xyz".indexOf(s.charAt(0)) < 0) throw new IllegalArgumentException(help);
                axis[i] = "xyz".indexOf(s.charAt(0));
                if (used[axis[i]]) throw new IllegalArgumentException(help + ": each of x, y and z once");
                used[axis[i]] = true;
            }
            // forward x left must be up; otherwise every part would be drawn mirrored
            double[][] m = new double[3][3];
            for (int i = 0; i < 3; i++) m[i][axis[i]] = sign[i];
            double det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                    + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
            if (det < 0) {
                throw new IllegalArgumentException(help + ". \"" + p.getProperty("cad.axes").trim()
                        + "\" would draw the robot mirrored: change the sign of one of them");
            }
            String o = p.getProperty("cad.origin", "0,0,0").replace(" ", "");
            String[] q = o.split(",");
            try {
                if (q.length != 3) throw new NumberFormatException();
                origin = new double[] {Double.parseDouble(q[0]), Double.parseDouble(q[1]), Double.parseDouble(q[2])};
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Robot file: cad.origin is the robot's centre on the floor in CAD units: x,y,z");
            }
            String[] n = {"x", "y", "z"};
            words = String.format(Locale.US, "CAD in %s; the robot's forward is CAD %s%s, left %s%s, up %s%s; its centre on the floor is CAD %s,%s,%s",
                    units, sign[0] < 0 ? "-" : "", n[axis[0]], sign[1] < 0 ? "-" : "", n[axis[1]], sign[2] < 0 ? "-" : "", n[axis[2]],
                    fmt(origin[0]), fmt(origin[1]), fmt(origin[2]));
        }

        private static String fmt(double v) {
            return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        }

        void toRobot(double x, double y, double z, float[] out, int at) {
            double[] c = {x - origin[0], y - origin[1], z - origin[2]};
            for (int i = 0; i < 3; i++) out[at + i] = (float) (sign[i] * c[axis[i]] * scale);
        }
    }

    final Frame frame;
    private final Path dir;
    private final Map<String, Mesh> cache = new HashMap<>();
    /** Every mesh loaded, in order: the page asks for them by number. */
    final List<Mesh> meshes = new ArrayList<>();
    private int triangles = 0;

    SimCad(Properties p, Path dir) {
        frame = new Frame(p);
        this.dir = dir;
    }

    /**
     * The mesh in this file (relative to the robot file), read once. Several files with ; between them
     * make one mesh (parts that move together, exported one file each).
     */
    Mesh load(String files, String what) {
        Mesh m = cache.get(files);
        if (m != null) return m;
        List<float[]> parts = new ArrayList<>();
        int total = 0;
        for (String file : files.split(";")) {
            file = file.trim();
            if (file.isEmpty()) continue;
            float[] t = read(file, what);
            parts.add(t);
            total += t.length;
        }
        float[] t = new float[total];
        int at = 0;
        for (float[] part : parts) {
            System.arraycopy(part, 0, t, at, part.length);
            at += part.length;
        }
        if (t.length == 0) throw new IllegalArgumentException("Robot file: " + what + " \"" + files + "\" has no triangles");
        m = new Mesh(files, t);
        cache.put(files, m);
        meshes.add(m);
        return m;
    }

    private float[] read(String file, String what) {
        Path path = dir == null ? java.nio.file.Paths.get(file) : dir.resolve(file).normalize();
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Robot file: " + what + " \"" + file + "\": no such file (" + path + ")");
        String name = path.getFileName().toString().toLowerCase(Locale.US);
        float[] t;
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (name.endsWith(".stl")) t = stl(bytes);
            else if (name.endsWith(".obj")) t = obj(new String(bytes, StandardCharsets.UTF_8));
            else throw new IllegalArgumentException("Robot file: " + what + " \"" + file + "\" must be an .stl or .obj file (export it from your CAD)");
        } catch (IOException e) {
            throw new IllegalArgumentException("Robot file: " + what + " \"" + file + "\": can't read it (" + e.getMessage() + ")");
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException && e.getMessage() != null && e.getMessage().startsWith("Robot file")) throw e;
            throw new IllegalArgumentException("Robot file: " + what + " \"" + file + "\": not a mesh this can read ("
                    + (e instanceof NumberFormatException ? "a number it can't read: " : "") + e.getMessage() + ")");
        }
        triangles += t.length / 9;
        if (triangles > MAX_TRIANGLES) {
            throw new IllegalArgumentException("Robot file: the CAD models have more than " + MAX_TRIANGLES
                    + " triangles together, too many to read. Export them coarser (a lower STL resolution), or leave out small parts (bolts).");
        }
        return t;
    }

    // Binary STL: 80 bytes, a count, then 50 bytes per triangle. ASCII STL starts with "solid" (but so do
    // some binary ones: the size decides).
    private float[] stl(byte[] b) {
        if (b.length >= 84) {
            ByteBuffer buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
            long n = buf.getInt(80) & 0xffffffffL;
            if (84 + n * 50 <= b.length && (84 + n * 50 == b.length || !new String(b, 0, 5, StandardCharsets.US_ASCII).equals("solid"))) {
                if (n > MAX_TRIANGLES) throw new IllegalArgumentException("Robot file: an STL with " + n + " triangles, too many to read: export it coarser");
                float[] out = new float[(int) n * 9];
                for (int i = 0; i < n; i++) {
                    int at = 84 + i * 50 + 12;      // skip the normal
                    for (int c = 0; c < 3; c++) {
                        frame.toRobot(buf.getFloat(at + c * 12), buf.getFloat(at + c * 12 + 4), buf.getFloat(at + c * 12 + 8), out, i * 9 + c * 3);
                    }
                }
                return out;
            }
        }
        String text = new String(b, StandardCharsets.US_ASCII);
        if (!text.trim().startsWith("solid")) throw new IllegalArgumentException("not an STL file");
        List<double[]> v = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String s = line.trim();
            if (s.startsWith("vertex")) {
                String[] p = s.split("\\s+");
                v.add(new double[] {Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])});
            }
        }
        if (v.size() % 3 != 0) throw new IllegalArgumentException("an ASCII STL with a broken triangle");
        float[] out = new float[v.size() * 3];
        for (int i = 0; i < v.size(); i++) frame.toRobot(v.get(i)[0], v.get(i)[1], v.get(i)[2], out, i * 3);
        return out;
    }

    // OBJ: "v x y z" points and "f a b c ..." faces (1-based, or negative from the end; a face with more
    // corners is split into triangles).
    private float[] obj(String text) {
        List<double[]> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String[] p = line.trim().split("\\s+");
            if (p[0].equals("v")) {
                v.add(new double[] {Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])});
            } else if (p[0].equals("f")) {
                int[] f = new int[p.length - 1];
                for (int i = 1; i < p.length; i++) {
                    int k = Integer.parseInt(p[i].split("/")[0]);
                    f[i - 1] = k < 0 ? v.size() + k : k - 1;
                }
                for (int i = 1; i + 1 < f.length; i++) {
                    idx.add(f[0]);
                    idx.add(f[i]);
                    idx.add(f[i + 1]);
                }
            }
        }
        float[] out = new float[idx.size() * 3];
        for (int i = 0; i < idx.size(); i++) {
            double[] p = v.get(idx.get(i));
            frame.toRobot(p[0], p[1], p[2], out, i * 3);
        }
        return out;
    }

    // Fewer triangles for the picture: every corner moves to the middle of the corners in its small cube,
    // and triangles that shrink to a line or a point go. The cubes grow until it is under the limit. Small
    // details (bolt threads) blur first; the outline stays.
    static float[] simplify(float[] t, int max) {
        if (t.length / 9 <= max) return t;
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < t.length; i += 3) {
            for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], t[i + k]); hi[k] = Math.max(hi[k], t[i + k]); }
        }
        double size = Math.max(1e-6, Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2])));
        for (double cells = 2000; ; cells /= 1.4) {
            double cell = size / cells;
            Map<Long, Integer> id = new HashMap<>();
            List<double[]> sum = new ArrayList<>();
            int[] corner = new int[t.length / 3];
            for (int i = 0; i < t.length; i += 3) {
                long key = ((long) ((t[i] - lo[0]) / cell) * 4096 + (long) ((t[i + 1] - lo[1]) / cell)) * 4096 + (long) ((t[i + 2] - lo[2]) / cell);
                Integer k = id.get(key);
                if (k == null) {
                    k = sum.size();
                    id.put(key, k);
                    sum.add(new double[4]);
                }
                double[] a = sum.get(k);
                a[0] += t[i]; a[1] += t[i + 1]; a[2] += t[i + 2]; a[3]++;
                corner[i / 3] = k;
            }
            java.util.Set<String> seen = new java.util.HashSet<>();
            List<Integer> keep = new ArrayList<>();
            for (int j = 0; j < corner.length; j += 3) {
                int a = corner[j], b = corner[j + 1], c = corner[j + 2];
                if (a == b || b == c || a == c) continue;
                int[] o = {a, b, c};
                java.util.Arrays.sort(o);
                if (seen.add(o[0] + "," + o[1] + "," + o[2])) keep.add(j);
            }
            if (keep.size() > max && cells > 20) continue;
            float[] out = new float[keep.size() * 9];
            int n = 0;
            for (int j : keep) {
                for (int c = 0; c < 3; c++) {
                    double[] a = sum.get(corner[j + c]);
                    for (int k = 0; k < 3; k++) out[n++] = (float) (a[k] / a[3]);
                }
            }
            return out;
        }
    }

    // The corners of the mesh's convex hull (the shape a rubber sheet stretched round it would make): the
    // size rules only need the outside. First one corner per small cube (1/400 of the mesh's size, coarser
    // if the hull would still have too many corners to turn for every pose): every real corner is within a
    // cube's diagonal of one kept, so that diagonal is added as a margin (the check never says "fits" when
    // the real mesh does not).
    private static Hull outside(float[] t) {
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < t.length; i += 3) {
            for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], t[i + k]); hi[k] = Math.max(hi[k], t[i + k]); }
        }
        double size = Math.max(1e-6, Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2])));
        Hull h = null;
        for (int cells : new int[] {400, 200, 100, 50}) {
            double cell = size / cells;
            java.util.Set<Long> seen = new java.util.HashSet<>();
            List<Integer> keep = new ArrayList<>();
            for (int i = 0; i < t.length; i += 3) {
                long key = ((long) ((t[i] - lo[0]) / cell) * 1024 + (long) ((t[i + 1] - lo[1]) / cell)) * 1024 + (long) ((t[i + 2] - lo[2]) / cell);
                if (seen.add(key)) keep.add(i);
            }
            double[][] pts;
            try {
                double[] xyz = new double[keep.size() * 3];
                for (int j = 0; j < keep.size(); j++) for (int k = 0; k < 3; k++) xyz[j * 3 + k] = t[keep.get(j) + k];
                com.github.quickhull3d.QuickHull3D q = new com.github.quickhull3d.QuickHull3D(xyz);
                com.github.quickhull3d.Point3d[] v = q.getVertices();
                pts = new double[v.length][];
                for (int j = 0; j < v.length; j++) pts[j] = new double[] {v[j].x, v[j].y, v[j].z};
            } catch (IllegalArgumentException flat) {
                // a flat part (one plate, one line) has no 3D hull: use the kept points themselves
                pts = new double[keep.size()][];
                for (int j = 0; j < keep.size(); j++) pts[j] = new double[] {t[keep.get(j)], t[keep.get(j) + 1], t[keep.get(j) + 2]};
            }
            h = new Hull(pts, Math.sqrt(3) * cell);
            if (pts.length <= 4000) break;
        }
        return h;
    }

    static final class Hull {
        final double[][] points;
        final double margin;

        Hull(double[][] points, double margin) {
            this.points = points;
            this.margin = margin;
        }
    }
}
