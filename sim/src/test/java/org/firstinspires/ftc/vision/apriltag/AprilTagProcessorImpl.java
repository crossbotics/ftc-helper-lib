package org.firstinspires.ftc.vision.apriltag;

import android.graphics.Canvas;
import com.crossbotics.ftchelper.sim.SimVision;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.matrices.GeneralMatrixF;
import org.firstinspires.ftc.robotcore.external.matrices.MatrixF;
import org.firstinspires.ftc.robotcore.external.matrices.OpenGLMatrix;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.robotcore.internal.camera.calibration.CameraCalibration;
import org.opencv.core.Mat;
import org.opencv.core.Point;

/**
 * SIMULATOR ONLY: stands in for the FTC SDK's AprilTagProcessorImpl (same name and constructor, so the
 * SDK's AprilTagProcessor.Builder makes this one in the simulator). Instead of finding tags in a
 * picture, it takes the tags the pretend camera sees (from the field file) and builds the SDK's own
 * detections from them the way the SDK does:
 * - a tag in one of the tag library's clusters gives a cluster detection (BIOBUZZ: one per CELL group
 *   of 4 tags), with the cluster's pose; otherwise a single-tag detection (no pose if the library does
 *   not know the tag);
 * - ftcPose is worked out from rawPose with the SDK's own formulas (range and bearing measured flat).
 * The poses are exact (no noise, no blur). robotPose is not simulated (always null).
 *
 * Tag axes (as the SDK's: its corner points are (-s/2, +s/2) bottom-left ... so x is printed right,
 * y printed down, z into the tag). The printed up comes from the field file ("up"; else the world's up
 * turned into the tag's plane). A cluster's x runs from its first member to its last; SimCameraTest
 * checks that the field file's printed up agrees with that.
 */
public class AprilTagProcessorImpl extends AprilTagProcessor {
    public static final String TAG = "AprilTagProcessorImpl";

    private final AprilTagLibrary tagLibrary;
    private final DistanceUnit outputUnitsLength;
    private final AngleUnit outputUnitsAngle;
    private volatile float decimation = 3;    // the SDK's default
    private volatile PoseSolver poseSolver = PoseSolver.APRILTAG_BUILTIN;
    private ArrayList<AprilTagDetection> detections = new ArrayList<>();
    private ArrayList<AprilTagDetection> detectionsUpdate = new ArrayList<>();
    private final Object detectionsUpdateSync = new Object();

    public AprilTagProcessorImpl(OpenGLMatrix robotInCameraFrame, double fx, double fy, double cx, double cy,
                                 DistanceUnit outputUnitsLength, AngleUnit outputUnitsAngle, AprilTagLibrary tagLibrary,
                                 boolean drawAxes, boolean drawCube, boolean drawOutline, boolean drawTagID,
                                 TagFamily tagFamily, int threads, boolean suppressCalibrationWarnings) {
        this.tagLibrary = tagLibrary;
        this.outputUnitsLength = outputUnitsLength;
        this.outputUnitsAngle = outputUnitsAngle;
    }

    @Override public void init(int width, int height, CameraCalibration calibration) { }
    @Override public Object processFrame(Mat input, long captureTimeNanos) { return null; }
    @Override public void onDrawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx,
                                      float scaleCanvasDensity, Object userContext) { }
    @Override public void setDecimation(float decimation) { this.decimation = decimation; }
    @Override public void setPoseSolver(PoseSolver poseSolver) { this.poseSolver = poseSolver; }
    @Override public int getPerTagAvgPoseSolveTime() { return 0; }

    @Override public ArrayList<AprilTagDetection> getDetections() {
        synchronized (detectionsUpdateSync) { return detections; }
    }

    @Override public ArrayList<AprilTagDetection> getFreshDetections() {
        synchronized (detectionsUpdateSync) {
            ArrayList<AprilTagDetection> ret = detectionsUpdate;
            detectionsUpdate = null;
            return ret;
        }
    }

    /** SIMULATOR: one picture from the pretend camera (called by the pretend VisionPortalImpl). */
    public void simFrame(Object cam, long frameNanos) {
        ArrayList<AprilTagDetection> out = detect(cam, frameNanos);
        synchronized (detectionsUpdateSync) {
            detections = out;
            detectionsUpdate = out;
        }
    }

    private ArrayList<AprilTagDetection> detect(Object cam, long nanos) {
        double[][] c = SimVision.pose(cam);
        List<double[]> seen = SimVision.visible(cam, decimation);
        ArrayList<AprilTagDetection> out = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Map<AprilTagClusterMetadata, List<Integer>> clusters = new LinkedHashMap<>();
        for (double[] s : seen) {
            int id = (int) s[0];
            AprilTagClusterMetadata cluster = tagLibrary.lookupCluster(id);
            if (cluster != null) {
                clusters.computeIfAbsent(cluster, k -> new ArrayList<>()).add(id);
                continue;
            }
            AprilTagMetadata meta = tagLibrary.lookupTag(id);
            double[] t = SimVision.tag(cam, id);
            double[][] axes = singleTagAxes(t);
            AprilTagPoseRaw raw = meta == null ? null : raw(c, new double[] {t[0], t[1], t[2]}, axes);
            AprilTagPoseFtc ftc = raw == null ? null : ftc(raw);
            double half = (meta != null ? DistanceUnit.INCH.fromUnit(meta.distanceUnit, meta.tagsize) : 2) / 2;
            Point[] corners = new Point[4];
            double[][] cornerXY = {{-half, half}, {half, half}, {half, -half}, {-half, -half}};   // as the SDK: bottom-left first
            for (int k = 0; k < 4; k++) {
                double[] w = add(new double[] {t[0], t[1], t[2]}, add(scale(axes[0], cornerXY[k][0]), scale(axes[1], cornerXY[k][1])));
                corners[k] = project(cam, c, w);
            }
            out.add(new AprilTagSingleDetection(id, 0, 60f, new Point(s[2], s[3]), corners, meta, ftc, raw, null, nanos, outputUnitsLength));
            notes.add(ftc == null ? "tag " + id + " (not in the tag library: no distance)" : note("tag " + id, ftc));
        }
        for (Map.Entry<AprilTagClusterMetadata, List<Integer>> e : clusters.entrySet()) {
            AprilTagClusterMetadata cl = e.getKey();
            AprilTagPoseRaw raw = clusterRaw(cam, c, cl);
            AprilTagPoseFtc ftc = raw == null ? null : ftc(raw);
            int percent = Math.round(100f * e.getValue().size() / cl.getMemberIds().size());
            out.add(new AprilTagClusterDetection(percent, cl, outputUnitsLength, ftc, raw, null, nanos));
            notes.add(ftc == null ? cl.name + " (members missing from the field file)" : note(cl.name + " " + e.getValue(), ftc));
        }
        SimVision.detected(cam, notes);
        return out;
    }

    private String note(String what, AprilTagPoseFtc p) {
        String u = outputUnitsLength == DistanceUnit.INCH ? "in" : outputUnitsLength.toString().toLowerCase(Locale.US);
        String a = outputUnitsAngle == AngleUnit.DEGREES ? "deg" : "rad";
        return String.format(Locale.US, "%s: range %.1f %s, bearing %.1f %s, elevation %.1f %s", what, p.range, u, p.bearing, a, p.elevation, a);
    }

    // A single tag's axes on the field: z into the tag, y printed down (the field file's "up" reversed; or
    // the world's up turned into the tag's plane, reversed), x = y cross z (printed right).
    public static double[][] singleTagAxes(double[] t) {
        double[] n = {t[3], t[4], t[5]};
        double[] z = scale(n, -1);
        double[] upRef = !Double.isNaN(t[6]) ? new double[] {t[6], t[7], t[8]}
                : Math.abs(n[2]) > 0.99 ? new double[] {1, 0, 0} : new double[] {0, 0, 1};
        double[] up = sub(upRef, scale(n, dot(upRef, n)));
        double[] y = norm(scale(up, -1));
        double[] x = cross(y, z);
        return new double[][] {x, y, z};
    }

    /** SIMULATOR TEST: a cluster's aiming point on the field (inches), as clusterRaw works it out, or null. */
    public static double[] clusterOrigin(Object cam, AprilTagClusterMetadata cl) {
        AprilTagProcessorImpl p = new AprilTagProcessorImpl(null, 0, 0, 0, 0, DistanceUnit.INCH, AngleUnit.DEGREES, null,
                false, false, false, false, null, 1, true);
        double[][] c = {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}};   // a "camera" at the field origin, on the field axes
        AprilTagPoseRaw raw = p.clusterRaw(cam, c, cl);
        return raw == null ? null : new double[] {raw.x, raw.y, raw.z};
    }

    // A cluster's pose: its axes from its members on the field (x from the member with the smallest x in
    // the cluster plane to the one with the largest), its origin from where the members are.
    private AprilTagPoseRaw clusterRaw(Object cam, double[][] c, AprilTagClusterMetadata cl) {
        List<Integer> ids = cl.getMemberIds();
        double[] first = null, last = null, n = null;
        double xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE;
        List<double[]> found = new ArrayList<>();
        for (int id : ids) {
            double[] t = SimVision.tag(cam, id);
            if (t == null) continue;
            AprilTagClusterMemberMetadata m = cl.getMemberMetadata(id);
            double mx = m.positionInClusterPlane.get(0);
            if (mx < xMin) { xMin = mx; first = t; }
            if (mx > xMax) { xMax = mx; last = t; }
            if (n == null) n = new double[] {t[3], t[4], t[5]};
            found.add(new double[] {id, t[0], t[1], t[2]});
        }
        if (found.size() < 2 || first == last) return null;
        double[] z = scale(n, -1);
        double[] along = sub(new double[] {last[0], last[1], last[2]}, new double[] {first[0], first[1], first[2]});
        double[] x = norm(sub(along, scale(n, dot(along, n))));
        double[] y = cross(z, x);
        double[][] axes = {x, y, z};
        double[] origin = {0, 0, 0};
        for (double[] f : found) {
            AprilTagClusterMemberMetadata m = cl.getMemberMetadata((int) f[0]);
            double[] p = {DistanceUnit.INCH.fromUnit(cl.distanceUnit, m.positionInClusterPlane.get(0)),
                    DistanceUnit.INCH.fromUnit(cl.distanceUnit, m.positionInClusterPlane.get(1)),
                    DistanceUnit.INCH.fromUnit(cl.distanceUnit, m.positionInClusterPlane.get(2))};
            double[] o = sub(new double[] {f[1], f[2], f[3]}, add(scale(x, p[0]), add(scale(y, p[1]), scale(z, p[2]))));
            origin = add(origin, scale(o, 1.0 / found.size()));
        }
        return raw(c, origin, axes);
    }

    // The pose of a frame (origin, axes on the field, inches) as the camera sees it, in the output units.
    private AprilTagPoseRaw raw(double[][] c, double[] origin, double[][] axes) {
        double[] d = sub(origin, c[0]);
        MatrixF r = new GeneralMatrixF(3, 3);
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) r.put(i, j, (float) dot(c[i + 1], axes[j]));
        return new AprilTagPoseRaw(outputUnitsLength.fromUnit(DistanceUnit.INCH, dot(d, c[1])),
                outputUnitsLength.fromUnit(DistanceUnit.INCH, dot(d, c[2])),
                outputUnitsLength.fromUnit(DistanceUnit.INCH, dot(d, c[3])), r);
    }

    // The SDK's own rawPose -> ftcPose (AprilTagProcessorImpl.rawPoseToFtcPose in SDK 12.0).
    private AprilTagPoseFtc ftc(AprilTagPoseRaw raw) {
        Orientation rot = Orientation.getOrientation(raw.R, AxesReference.INTRINSIC, AxesOrder.YXZ, outputUnitsAngle);
        return new AprilTagPoseFtc(raw.x, raw.z, -raw.y, -rot.firstAngle, rot.secondAngle, rot.thirdAngle,
                Math.hypot(raw.x, raw.z),
                outputUnitsAngle.fromUnit(AngleUnit.RADIANS, Math.atan2(-raw.x, raw.z)),
                outputUnitsAngle.fromUnit(AngleUnit.RADIANS, Math.atan2(-raw.y, raw.z)));
    }

    private static Point project(Object cam, double[][] c, double[] w) {
        double[] d = sub(w, c[0]);
        double z = Math.max(1e-6, dot(d, c[3])), f = SimVision.focalPx(cam);
        return new Point(SimVision.width(cam) / 2.0 + f * dot(d, c[1]) / z, SimVision.height(cam) / 2.0 + f * dot(d, c[2]) / z);
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static double[] add(double[] a, double[] b) { return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    private static double[] sub(double[] a, double[] b) { return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static double[] scale(double[] a, double k) { return new double[] {a[0] * k, a[1] * k, a[2] * k}; }
    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
    private static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return scale(a, 1 / l); }
}
