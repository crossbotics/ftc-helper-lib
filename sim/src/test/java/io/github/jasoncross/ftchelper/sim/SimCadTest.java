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
        assertEquals("level: the CAD's 14 in decides the front, not the 10 in box", 14, r.shapes.now().front, 1e-3);
        r.shapes.shapes.get("arm").value = 90;
        assertEquals("straight up: 10 + 14 in", 24, top(r, "arm"), 0.05);
        assertTrue("the boxes stay for physics: corners still 10 in long",
                Math.abs(arm.corners().stream().mapToDouble(c -> c[0]).max().getAsDouble() - 10) < 1e-6);
        assertEquals("the full-reach check uses the mesh", 24, r.shapes.most.top, 0.05);
        String report = r.shapes.cadReport().get(0);
        assertTrue(report, report.contains("CAD 14.0 x 1.0 x 1.0 in, its middle 2.0 in from the box's; box 10.0 x 1.0 x 1.0 in") && !report.contains("FAR"));
    }

    @Test public void cadPoseIsWhereTheCadWasDrawn() throws Exception {
        // the same bar drawn straight up (arm at 90 degrees): at 0 it is level again
        write("arm.stl", boxStl(new double[] {100 - 12.7, 200 - 12.7, 254}, new double[] {100 + 12.7, 200 + 12.7, 254 + 14 * 25.4}));
        SimRobot r = robot(CAD + ARM.replace("model=arm.stl", "model=arm.stl, cadPose=90"));
        assertEquals(14, r.shapes.now().front, 0.05);
        assertEquals(10.5, top(r, "arm"), 0.05);
    }

    @Test public void objWorksTooAndMistakesAreExplained() throws Exception {
        String obj = "v 0 0 0\nv 25.4 0 0\nv 0 25.4 0\nv 0 0 25.4\nf 1 3 2\nf 1 2 4\nf 1 4 3\nf 2 3 4\n";
        write("tip.obj", obj.getBytes("UTF-8"));
        SimRobot r = robot("cad.units = mm\nrobot.model = tip.obj\n");
        assertEquals(1, r.shapes.cad.meshes.size());
        assertEquals(1, r.shapes.cad.meshes.get(0).hi[0], 1e-6);
        expectProblem("cad.axes = x,-y,z\nrobot.model = tip.obj\n", "mirrored");
        expectProblem("robot.model = nothing.stl\n", "no such file");
        expectProblem("robot.model = robot.properties\n", ".stl or .obj");
        expectProblem("cad.units = feet\nrobot.model = tip.obj\n", "cad.units");
        expectProblem("shape.b = box, baseModel=tip.obj\n", "baseModel");
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
