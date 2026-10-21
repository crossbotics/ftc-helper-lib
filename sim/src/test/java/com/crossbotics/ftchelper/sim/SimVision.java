package com.crossbotics.ftchelper.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * The link between the pretend FTC vision classes (in the SDK's own packages, so the SDK's
 * VisionPortal.Builder and AprilTagProcessor.Builder make them) and the simulator's cameras.
 * Public only because those classes live in other packages; robot code never uses it.
 */
public final class SimVision {
    private SimVision() { }

    /** The pretend camera behind a CameraName from the hardware map, or null (not a simulator camera). */
    public static Object camera(Object cameraName) {
        return SimCamera.of(cameraName);
    }

    public static String name(Object camera) { return ((SimCamera) camera).name; }
    public static int width(Object camera) { return ((SimCamera) camera).width; }
    public static int height(Object camera) { return ((SimCamera) camera).height; }
    public static Object device(Object camera) { return ((SimCamera) camera).device; }

    public static void opened(Object camera, AutoCloseable portal) { ((SimCamera) camera).opened(portal); }
    public static void closed(Object camera, AutoCloseable portal) { ((SimCamera) camera).closed(portal); }

    /** The camera on the field: rows position, x (picture right), y (picture down), z (out of the lens). Inches. */
    public static double[][] pose(Object camera) {
        return ((SimCamera) camera).pose();
    }

    /** Pixels per unit of x/z. */
    public static double focalPx(Object camera) {
        return ((SimCamera) camera).focalPx();
    }

    /** The tags the camera sees now: {id, pixels across, picture x, picture y} each. Also kept for the page. */
    public static List<double[]> visible(Object camera, double decimation) {
        SimCamera c = (SimCamera) camera;
        List<double[]> out = new ArrayList<>();
        for (SimCamera.Seen s : c.visibleTags(c.robot.field, decimation)) {
            out.add(new double[] {s.tag.id, s.pixels, s.cx, s.cy});
        }
        c.lastSeen(out);
        return out;
    }

    /**
     * A field-file tag: {x, y, z, nx, ny, nz, upx, upy, upz}: its center (inches), which way its face
     * points, and its printed up (NaN when the field file does not give it). Null if there is no such tag.
     */
    public static double[] tag(Object camera, int id) {
        for (SimField.Tag t : ((SimCamera) camera).robot.field.tags) {
            if (t.id == id) {
                double[] p = t.pose(), n = SimCamera.tagNormal(p);
                return new double[] {p[0], p[1], p[2], n[0], n[1], n[2], p[5], p[6], p[7]};
            }
        }
        return null;
    }

    /** Notes a detection made from the tags (for the page): "RED SCORING 41.2 in, 3.1 deg". */
    public static void detected(Object camera, List<String> lines) {
        ((SimCamera) camera).lastDetections(lines);
    }
}
