package io.github.jasoncross.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** CAD meshes on shapes: read, placed in the robot frame, moved with their shape, used for the size rules. */
public class SimCadTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // The robot file's devices (the starter's), with no shapes.
    private String devices() throws Exception {
        String base = new String(Files.readAllBytes(new File("robots/starter.properties").toPath()), "UTF-8");
        StringBuilder b = new StringBuilder();
        for (String line : base.split("\n")) {
            if (!line.startsWith("shape.") && !line.startsWith("collect.") && !line.startsWith("shoot.") && !line.startsWith("grab.")) {
                b.append(line).append('\n');
            }
        }
        return b.toString();
    }

    private SimRobot robot(String extra) throws Exception {
        File f = new File(tmp.getRoot(), "robot.properties");
        Files.write(f.toPath(), (devices() + extra).getBytes("UTF-8"));
        return SimRobot.load(f.toPath());
    }

    // The 12 triangles of a box from lo to hi (x, y, z), as a binary STL.
    private static byte[] boxStl(double[] lo, double[] hi) {
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) c[i] = new double[] {(i & 1) == 0 ? lo[0] : hi[0], (i & 2) == 0 ? lo[1] : hi[1], (i & 4) == 0 ? lo[2] : hi[2]};
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        ByteBuffer b = ByteBuffer.allocate(84 + 12 * 50).order(ByteOrder.LITTLE_ENDIAN);
        b.position(80);
        b.putInt(12);
        for (int[] f : faces) {
            for (int[] t : new int[][] {{f[0], f[1], f[2]}, {f[0], f[2], f[3]}}) {
                for (int k = 0; k < 3; k++) b.putFloat(0);
                for (int v : t) for (int k = 0; k < 3; k++) b.putFloat((float) c[v][k]);
                b.putShort((short) 0);
            }
        }
        return b.array();
    }

    private void write(String name, byte[] bytes) throws Exception {
        Files.write(new File(tmp.getRoot(), name).toPath(), bytes);
    }

    private static double top(SimRobot r, String shape) {
        double t = -1e9;
        for (SimShapes.Piece p : r.shapes.pieces()) if (p.shape.equals(shape)) for (double[] c : p.points()) t = Math.max(t, c[2]);
        return t;
    }

    // An arm on a hinge 10 in up, 0..120 degrees. Its CAD is a 14 in bar (the box says 10), drawn level and
    // forward, in mm, with CAD y forward and x to the right, and the robot's centre at (100, 200, 0) mm.
    private static final String CAD = "cad.units = mm\ncad.axes = y,-x,z\ncad.origin = 100,200,0\n";
    private static final String HDR = "cad.units = mm\ncad.axes = x,y,z\n";
    private static final String ARM = "shape.arm = arm, at=0,0,10, size=10x1x1, motor=arm, ticksPerDeg=10, start=0, model=arm.stl\n";

    private static byte[] bar() {
        return boxStl(new double[] {100 - 12.7, 200, 254 - 12.7}, new double[] {100 + 12.7, 200 + 14 * 25.4, 254 + 12.7});
    }

    @Test public void theMeshMovesWithItsShapeAndSetsTheSize() throws Exception {
        write("arm.stl", bar());
        SimRobot r = robot(CAD + ARM);
        SimShapes.Piece arm = null;
        for (SimShapes.Piece p : r.shapes.pieces()) if (p.shape.equals("arm")) arm = p;
        assertTrue("the arm has its mesh", arm.mesh != null);
        assertEquals("in the robot frame: 14 in forward", 14, arm.mesh.hi[0], 1e-3);
        assertEquals(-0.5, arm.mesh.lo[1], 1e-3);
        assertEquals(10.5, arm.mesh.hi[2], 1e-3);
        atLeast("level: the CAD's 14 in decides the front, not the 10 in box", 14, r.shapes.now().front);
        r.shapes.shapes.get("arm").value = 90;
        assertEquals("straight up: 10 + 14 in", 24, top(r, "arm"), 0.05);
        assertTrue("the boxes stay for physics: corners still 10 in long",
                Math.abs(arm.corners().stream().mapToDouble(c -> c[0]).max().getAsDouble() - 10) < 1e-6);
        atLeast("the full-reach check uses the mesh", 24, r.shapes.most.top);
        assertEquals("CAD in mm; the robot's forward is CAD y, left -x, up z; its centre on the floor is CAD 100,200,0", r.shapes.cadReport().get(0));
        String report = r.shapes.cadReport().get(1);
        assertTrue(report, report.contains("CAD 14.0 x 1.0 x 1.0 in, its middle 2.0 in from the box's; box 10.0 x 1.0 x 1.0 in") && !report.contains("FAR"));
    }

    @Test public void cadPoseIsWhereTheCadWasDrawn() throws Exception {
        // the same bar drawn straight up (arm at 90 degrees): at 0 it is level again
        write("arm.stl", boxStl(new double[] {100 - 12.7, 200 - 12.7, 254}, new double[] {100 + 12.7, 200 + 12.7, 254 + 14 * 25.4}));
        SimRobot r = robot(CAD + ARM.replace("model=arm.stl", "model=arm.stl, cadPose=90"));
        atLeast("level again", 14, r.shapes.now().front);
        assertEquals(10.5, top(r, "arm"), 0.05);
    }

    @Test public void objWorksTooAndMistakesAreExplained() throws Exception {
        String obj = "v 0 0 0\nv\t25.4 0 0\nv 0 25.4 0\nv 0 0 25.4\nf 1/1 3/3 2/2\nf\t1 2 4\nf 1 4 3\nf -3 -2 -1\n";
        write("tip.obj", obj.getBytes("UTF-8"));
        SimRobot r = robot(HDR + "robot.model = tip.obj\n");
        assertEquals(1, r.shapes.cad.meshes.size());
        assertEquals(1, r.shapes.cad.meshes.get(0).hi[0], 1e-6);
        expectProblem("cad.units = mm\ncad.axes = x,-y,z\nrobot.model = tip.obj\n", "mirrored");
        expectProblem(HDR + "robot.model = nothing.stl\n", "no such file");
        expectProblem("robot.model = tip.obj\n", "need cad.units");
        expectProblem(HDR + "robot.model = robot.properties\n", ".stl or .obj");
        write("bad.obj", "v 1,5 0 0\n".getBytes("UTF-8"));
        expectProblem(HDR + "robot.model = bad.obj\n", "a number it can't read: For input string");
        expectProblem("cad.units = feet\ncad.axes = x,y,z\nrobot.model = tip.obj\n", "cad.units");
        expectProblem(HDR + "shape.b = box, baseModel=tip.obj\n", "baseModel");
    }

    // The size rules never say a mesh is smaller than it is, and are within 0.1 in: a flat, tilted cone
    // (18 in across, 0.4 in high: a shape whose tip is easy to miss) on an arm, at many angles.
    @Test public void theSizeRulesAreSafeForAnyShape() throws Exception {
        java.util.List<double[]> tri = new java.util.ArrayList<>();
        double[] tip = {9 + 0.4 * Math.sin(0.7), 0, 10 + 0.4 * Math.cos(0.7)};
        for (int i = 0; i < 48; i++) {
            double a = 2 * Math.PI * i / 48, b = 2 * Math.PI * (i + 1) / 48;
            double[] p = rim(a), q = rim(b);
            tri.add(tip); tri.add(p); tri.add(q);
            tri.add(new double[] {9, 0, 10}); tri.add(q); tri.add(p);
        }
        write("cone.stl", stl(tri));
        SimRobot r = robot("cad.units = in\ncad.axes = x,y,z\n" + ARM.replace("arm.stl", "cone.stl"));
        SimShapes.Shape arm = r.shapes.shapes.get("arm");
        for (double deg = 0; deg <= 120; deg += 7.5) {
            arm.value = deg;
            double truth = -1e9;
            double c = Math.cos(Math.toRadians(deg)), sn = Math.sin(Math.toRadians(deg));
            for (double[] v : tri) truth = Math.max(truth, 10 + (v[0] * sn + (v[2] - 10) * c));   // turned about the hinge (0,0,10)
            double top = r.shapes.now().top;
            assertTrue("at " + deg + " deg: " + top + " vs the real " + truth, top >= truth - 1e-6 && top <= truth + 0.1);
        }
    }

    private static double[] rim(double a) {
        // a circle of radius 9 round (9, 0, 10), in the plane square to the tilted axis (sin 0.7, 0, cos 0.7)
        double[] u = {Math.cos(0.7), 0, -Math.sin(0.7)}, w = {0, 1, 0};
        return new double[] {9 + 9 * (u[0] * Math.cos(a) + w[0] * Math.sin(a)), 9 * Math.sin(a), 10 + 9 * u[2] * Math.cos(a)};
    }

    private static byte[] stl(java.util.List<double[]> corners) {
        ByteBuffer b = ByteBuffer.allocate(84 + corners.size() / 3 * 50).order(ByteOrder.LITTLE_ENDIAN);
        b.position(80);
        b.putInt(corners.size() / 3);
        for (int i = 0; i < corners.size(); i += 3) {
            for (int k = 0; k < 3; k++) b.putFloat(0);
            for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) b.putFloat((float) corners.get(i + j)[k]);
            b.putShort((short) 0);
        }
        return b.array();
    }

    // A roller's mesh with a paddle sticking 3 in out forward: as it spins the paddle points up too.
    @Test public void aSpinningRollerReachesAllRound() throws Exception {
        write("paddle.stl", boxStl(new double[] {8, -7, 1}, new double[] {11, 7, 2}));
        SimRobot r = robot("cad.units = in\ncad.axes = x,y,z\nshape.intake = roller, at=8,0,1.5, size=2x14x2, motor=intake, model=paddle.stl\n");
        // (a 16-sided cylinder round the real circle: up to 2% more, plus the margin)
        double top = r.shapes.now().top, reach = 1.5 + Math.hypot(3, 0.5);
        assertTrue("3 in above the roller's centre, whichever way it is turned: " + top, top >= reach && top < reach + 0.2);
        assertTrue("and 3 in below it: below the tiles", r.shapes.now().bottom < -1);
    }

    private static void atLeast(String what, double expected, double actual) {
        assertTrue(what + ": " + actual + " (expected " + expected + " to 0.1 more)", actual >= expected - 1e-6 && actual <= expected + 0.1);
    }

    // Parts that move together, one file each: one model. Inches exported but mm written: told so.
    @Test public void severalFilesMakeOneModelAndWrongUnitsAreNamed() throws Exception {
        write("a.stl", boxStl(new double[] {0, -1, 0}, new double[] {1, 1, 1}));
        write("b.stl", boxStl(new double[] {5, -1, 0}, new double[] {6, 1, 1}));
        SimRobot r = robot("cad.units = in\ncad.axes = x,y,z\nshape.part = box, at=0,0,0, size=6x2x1, model=a.stl; b.stl\n");
        assertEquals(1, r.shapes.cad.meshes.size());
        assertEquals(6, r.shapes.cad.meshes.get(0).hi[0], 1e-6);
        assertEquals(24, r.shapes.cad.meshes.get(0).triangles.length / 9);
        SimRobot mm = robot("cad.units = mm\ncad.axes = x,y,z\nshape.part = box, at=0,0,0, size=6x2x1, model=a.stl; b.stl\n");
        assertTrue(mm.shapes.cadReport().get(1), mm.shapes.cadReport().get(1).contains("25 times smaller than its box: check cad.units"));
    }

    // A big export is drawn with fewer triangles; its outline stays, and the size rules use every one.
    @Test public void bigModelsAreSimplifiedForThePicture() throws Exception {
        java.util.List<double[]> tri = new java.util.ArrayList<>();
        int n = 300;                                   // a 10 x 10 in bumpy plate of 2 x 300 x 300 triangles
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double[] a = bump(i, j, n), b = bump(i + 1, j, n), c = bump(i + 1, j + 1, n), d = bump(i, j + 1, n);
                tri.add(a); tri.add(b); tri.add(c);
                tri.add(a); tri.add(c); tri.add(d);
            }
        }
        float[] t = new float[tri.size() * 3];
        for (int i = 0; i < tri.size(); i++) for (int k = 0; k < 3; k++) t[i * 3 + k] = (float) tri.get(i)[k];
        float[] s = SimCad.simplify(t, 20_000);
        assertTrue("fewer: " + s.length / 9, s.length / 9 <= 20_000 && s.length / 9 > 2_000);
        double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
        for (int i = 0; i < s.length; i += 3) for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], s[i + k]); hi[k] = Math.max(hi[k], s[i + k]); }
        assertEquals("the outline stays", 0, lo[0], 0.1);
        assertEquals(10, hi[0], 0.1);
        assertEquals(10, hi[1], 0.1);
        write("plate.stl", stl(tri));
        SimRobot r = robot(HDR.replace("mm", "in") + "robot.model = plate.stl\n");
        atLeast("the size rules use every triangle", 0.5 + 0.3, r.shapes.now().top);
    }

    private static double[] bump(int i, int j, int n) {
        double x = 10.0 * i / n, y = 10.0 * j / n;
        return new double[] {x, y, 0.5 + 0.3 * Math.sin(x * 3) * Math.sin(y * 3)};
    }

    private void expectProblem(String extra, String words) throws Exception {
        try {
            robot(extra);
            fail("should not load: " + extra);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(words));
        }
    }
}
