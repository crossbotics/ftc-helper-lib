package io.github.jasoncross.ftchelper.sim;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    // A mesh larger than this would make the page slow (and is more detail than the size check needs).
    static final int MAX_TRIANGLES = 2_000_000;

    /** One part's mesh: triangles in the robot frame at the start pose (forward, left, up; inches). */
    static final class Mesh {
        final String file;
        final float[] triangles;        // x y z per corner, 9 floats per triangle
        final double[][] hull;          // a few hundred outermost points, for the size rules
        final double[] lo, hi;          // its box in the robot frame at the start

        Mesh(String file, float[] triangles) {
            this.file = file;
            this.triangles = triangles;
            lo = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            hi = new double[] {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int i = 0; i < triangles.length; i += 3) {
                for (int k = 0; k < 3; k++) {
                    lo[k] = Math.min(lo[k], triangles[i + k]);
                    hi[k] = Math.max(hi[k], triangles[i + k]);
                }
            }
            hull = outermost(triangles);
        }

        byte[] bytes() {
            ByteBuffer b = ByteBuffer.allocate(triangles.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float f : triangles) b.putFloat(f);
            return b.array();
        }
    }

    /** How CAD coordinates become robot coordinates. */
    static final class Frame {
        final double scale;             // CAD units -> inches
        final int[] axis = new int[3];  // forward, left, up: which CAD axis (0 x, 1 y, 2 z)
        final double[] sign = new double[3];
        final double[] origin;          // the robot's centre on the floor, CAD units

        Frame(Properties p) {
            String units = p.getProperty("cad.units", "mm").trim().toLowerCase(Locale.US);
            if (units.equals("mm")) scale = 1 / 25.4;
            else if (units.equals("in")) scale = 1;
            else if (units.equals("cm")) scale = 1 / 2.54;
            else if (units.equals("m")) scale = 1 / 0.0254;
            else throw new IllegalArgumentException("Robot file: cad.units must be mm, cm, m or in");
            String[] a = p.getProperty("cad.axes", "x,y,z").replace(" ", "").toLowerCase(Locale.US).split(",");
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
        }

        void toRobot(double x, double y, double z, float[] out, int at) {
            double[] c = {x - origin[0], y - origin[1], z - origin[2]};
            for (int i = 0; i < 3; i++) out[at + i] = (float) (sign[i] * c[axis[i]] * scale);
        }
    }

    private final Frame frame;
    private final Path dir;
    private final Map<String, Mesh> cache = new HashMap<>();
    /** Every mesh loaded, in order: the page asks for them by number. */
    final List<Mesh> meshes = new ArrayList<>();
    private int triangles = 0;

    SimCad(Properties p, Path dir) {
        frame = new Frame(p);
        this.dir = dir;
    }

    /** The mesh in this file (relative to the robot file), read once. */
    Mesh load(String file, String what) {
        Mesh m = cache.get(file);
        if (m != null) return m;
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
            throw new IllegalArgumentException("Robot file: " + what + " \"" + file + "\": not a mesh this can read (" + e + ")");
        }
        if (t.length == 0) throw new IllegalArgumentException("Robot file: " + what + " \"" + file + "\" has no triangles");
        triangles += t.length / 9;
        if (triangles > MAX_TRIANGLES) {
            throw new IllegalArgumentException("Robot file: the CAD models have more than " + MAX_TRIANGLES
                    + " triangles together. Export them coarser (a lower STL resolution), or leave out small parts (bolts).");
        }
        m = new Mesh(file, t);
        cache.put(file, m);
        meshes.add(m);
        return m;
    }

    // Binary STL: 80 bytes, a count, then 50 bytes per triangle. ASCII STL starts with "solid" (but so do
    // some binary ones: the size decides).
    private float[] stl(byte[] b) {
        if (b.length >= 84) {
            ByteBuffer buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
            long n = buf.getInt(80) & 0xffffffffL;
            if (84 + n * 50 == b.length) {
                if (n > MAX_TRIANGLES) throw new IllegalArgumentException("Robot file: an STL with " + n + " triangles: export it coarser");
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
            String s = line.trim();
            if (s.startsWith("v ")) {
                String[] p = s.split("\\s+");
                v.add(new double[] {Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])});
            } else if (s.startsWith("f ")) {
                String[] p = s.split("\\s+");
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

    // The outermost points along many directions (about 1000): turned any way, the mesh's furthest point in
    // any direction is within about 1/300 of its size of one of these. Enough for the size rules,
    // and far fewer points to turn for every pose tried.
    private static double[][] outermost(float[] t) {
        int dirs = 1000;
        Map<Integer, double[]> best = new LinkedHashMap<>();
        double golden = Math.PI * (3 - Math.sqrt(5));
        double[][] d = new double[dirs + 6][];
        for (int i = 0; i < dirs; i++) {
            double z = 1 - 2 * (i + 0.5) / dirs, r = Math.sqrt(1 - z * z);
            d[i] = new double[] {r * Math.cos(golden * i), r * Math.sin(golden * i), z};
        }
        d[dirs] = new double[] {1, 0, 0}; d[dirs + 1] = new double[] {-1, 0, 0};
        d[dirs + 2] = new double[] {0, 1, 0}; d[dirs + 3] = new double[] {0, -1, 0};
        d[dirs + 4] = new double[] {0, 0, 1}; d[dirs + 5] = new double[] {0, 0, -1};
        // First one point per small cube (1/400 of the mesh's size): a big mesh has far fewer cubes than corners.
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < t.length; i += 3) {
            for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], t[i + k]); hi[k] = Math.max(hi[k], t[i + k]); }
        }
        double cell = Math.max(1e-6, Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2])) / 400);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < t.length; i += 3) {
            long key = ((long) ((t[i] - lo[0]) / cell) * 512 + (long) ((t[i + 1] - lo[1]) / cell)) * 512 + (long) ((t[i + 2] - lo[2]) / cell);
            if (seen.add(key)) keep.add(i);
        }
        double[] top = new double[d.length];
        int[] at = new int[d.length];
        java.util.Arrays.fill(top, -Double.MAX_VALUE);
        for (int i : keep) {
            for (int k = 0; k < d.length; k++) {
                double s = d[k][0] * t[i] + d[k][1] * t[i + 1] + d[k][2] * t[i + 2];
                if (s > top[k]) { top[k] = s; at[k] = i; }
            }
        }
        for (int k = 0; k < d.length; k++) best.put(at[k], new double[] {t[at[k]], t[at[k] + 1], t[at[k] + 2]});
        return best.values().toArray(new double[0][]);
    }
}
