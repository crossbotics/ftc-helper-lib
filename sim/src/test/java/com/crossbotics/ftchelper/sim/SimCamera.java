package com.crossbotics.ftchelper.sim;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;

/**
 * A pretend USB webcam (camera.NAME in the robot file). It has no pictures: it works out which of the
 * field file's AprilTags it would see from where it sits on the robot, and the pretend FTC vision code
 * (sim/src/test/java/org/firstinspires/ftc/vision/...) turns those into the SDK's own detections.
 *
 * Where it sits: forward/left/up (inches) from the robot's center on the floor, yaw (degrees, + = turned
 * left) and pitch (degrees, + = tilted up). What it sees: hfov degrees across, width x height pixels.
 * on=SHAPE: it rides a moving shape (a camera on a turret: on=turret); then forward/left/up/yaw are
 * measured with that part at 0 (the turret pointing straight forward), and it turns with the part.
 */
final class SimCamera implements InvocationHandler {
    final String name;
    final String kind;          // "webcam" or "limelight"
    final double forwardIn, leftIn, upIn, yawDeg, pitchDeg, hfovDeg;
    final int width, height;
    // on=SHAPE: the shape it rides (a turret), or null for a camera fixed to the chassis.
    final String on;
    final WebcamName device;     // null for a Limelight (SimLimelight is its device)
    final SimRobot robot;
    private final java.util.Set<Integer> goodPipelines;
    private final double[] goodExposureMs;
    // The Limelight's pipeline and the webcam's exposure now (0 = automatic); set by the pretend devices.
    volatile int pipeline = 0;
    volatile long exposureMs = 0;
    // The portals open on this camera (the FTC app closes them when an OpMode stops).
    private final List<AutoCloseable> portals = new ArrayList<>();

    SimCamera(String name, Map<String, String> s, SimRobot robot) {
        this(name, "webcam", s, robot);
    }

    SimCamera(String name, String kind, Map<String, String> s, SimRobot robot) {
        this.name = name;
        this.kind = kind;
        this.robot = robot;
        forwardIn = num(s, "forward", 0);
        leftIn = num(s, "left", 0);
        upIn = num(s, "up", 10);
        yawDeg = num(s, "yaw", 0);
        pitchDeg = num(s, "pitch", 0);
        on = s.containsKey("on") && !s.get("on").trim().isEmpty() && !s.get("on").trim().equals("chassis") ? s.get("on").trim() : null;
        // Logitech C920 at 640x480: about 70 degrees across; Limelight 3A: 54.5 (its spec sheet)
        hfovDeg = num(s, "hfov", kind.equals("limelight") ? 54.5 : 70.4);
        width = (int) num(s, "width", 640);
        height = (int) num(s, "height", 480);
        if (hfovDeg <= 1 || hfovDeg >= 179) throw new IllegalArgumentException("Robot file: camera." + name + ": hfov must be between 1 and 179 degrees");
        // Pretend venue lighting (for the library's lighting test): goodPipelines=1|2 = a Limelight sees
        // tags only on these pipelines; goodExposureMs=4-10 = a webcam sees tags only with a manual
        // exposure in this range (0 in the range = its own automatic exposure works too). Default: always.
        goodPipelines = s.containsKey("goodPipelines") ? parseList(s.get("goodPipelines")) : null;
        goodExposureMs = s.containsKey("goodExposureMs") ? parseRange(s.get("goodExposureMs")) : null;
        if (width < 16 || height < 16) throw new IllegalArgumentException("Robot file: camera." + name + ": width and height must be at least 16 pixels");
        device = kind.equals("webcam")
                ? (WebcamName) Proxy.newProxyInstance(WebcamName.class.getClassLoader(), new Class<?>[] {WebcamName.class}, this) : null;
    }

    private static java.util.Set<Integer> parseList(String v) {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        try {
            for (String p : v.split("\\|")) out.add(Integer.parseInt(p.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: goodPipelines=" + v + " must be pipeline numbers like 1|2");
        }
        return out;
    }

    private static double[] parseRange(String v) {
        String[] p = v.split("-");
        try {
            if (p.length == 2) return new double[] {Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim())};
        } catch (NumberFormatException e) {
            // the message below
        }
        throw new IllegalArgumentException("Robot file: goodExposureMs=" + v + " must be a range like 4-10");
    }

    /** True if the pretend lighting lets this camera find tags with its setting now. */
    boolean lightingOk() {
        if (goodPipelines != null && !goodPipelines.contains(pipeline)) return false;
        return goodExposureMs == null || (exposureMs >= goodExposureMs[0] && exposureMs <= goodExposureMs[1]);
    }

    private static double num(Map<String, String> s, String key, double fallback) {
        if (!s.containsKey(key)) return fallback;
        try {
            return Double.parseDouble(s.get(key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: camera setting " + key + "=" + s.get(key) + " is not a number");
        }
    }

    @Override
    public Object invoke(Object proxy, Method m, Object[] a) {
        switch (m.getName()) {
            case "isWebcam": return true;
            case "isCameraDirection":
            case "isSwitchable":
            case "isUnknown": return false;
            case "getDeviceName": return "Pretend webcam " + name;
            case "getConnectionInfo": return "simulator";
            case "getVersion": return 1;
            case "getManufacturer": return com.qualcomm.robotcore.hardware.HardwareDevice.Manufacturer.Other;
            case "isAttached": return true;
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return proxy == a[0];
            case "toString": return "SimCamera " + name;
            default: return SimDefaults.of(m.getReturnType());
        }
    }

    /** The SimCamera behind a pretend webcam, or null for any other camera. */
    static SimCamera of(Object cameraName) {
        if (cameraName != null && Proxy.isProxyClass(cameraName.getClass())) {
            InvocationHandler h = Proxy.getInvocationHandler(cameraName);
            if (h instanceof SimCamera) return (SimCamera) h;
        }
        return null;
    }

    synchronized void opened(AutoCloseable portal) { portals.add(portal); }
    synchronized void closed(AutoCloseable portal) { portals.remove(portal); }

    /** Closes every portal on this camera (the FTC app does it when an OpMode stops). */
    void closeAll() {
        List<AutoCloseable> copy;
        synchronized (this) { copy = new ArrayList<>(portals); }
        for (AutoCloseable p : copy) {
            try {
                p.close();
            } catch (Exception ignored) {
                // closing is best effort
            }
        }
    }

    synchronized int openPortals() { return portals.size(); }

    // What the camera saw last (for the page and scenario checks): tag ids, and the detections the
    // pretend AprilTag finder made from them. Cleared when no portal is open.
    private List<Integer> seenIds = new ArrayList<>();
    private List<String> detections = new ArrayList<>();
    private long seenAt = 0;

    synchronized void lastSeen(List<double[]> tags) {
        seenIds = new ArrayList<>();
        for (double[] t : tags) seenIds.add((int) t[0]);
        seenAt = System.nanoTime();
    }

    synchronized void lastDetections(List<String> lines) {
        detections = new ArrayList<>(lines);
    }

    /** The tag ids seen in the last picture (empty when no portal is open or the last picture is old). */
    synchronized List<Integer> seenIds() {
        if (portals.isEmpty() || System.nanoTime() - seenAt > 500_000_000L) return new ArrayList<>();
        return new ArrayList<>(seenIds);
    }

    synchronized List<String> detections() {
        if (portals.isEmpty() || System.nanoTime() - seenAt > 500_000_000L) return new ArrayList<>();
        return new ArrayList<>(detections);
    }

    /**
     * The camera on the field now: its position (inches) and its axes, the same as OpenCV's: x to the
     * right of the picture, y down the picture, z straight out of the lens. Rows: position, x, y, z.
     */
    double[][] pose() {
        synchronized (robot) {
            return poseAt(robot.x, robot.y, robot.headingDeg);
        }
    }

    /**
     * The same as pose(), with the robot put at (rx, ry) facing headingDeg instead of where it is (its turret and
     * other moving parts as they are now). The page's "where the camera sees tags" map uses it.
     */
    double[][] poseAt(double rx, double ry, double headingDeg) {
        double h = Math.toRadians(headingDeg);
        // In the robot frame (forward, left, up): where the lens is, and its three axes. Yaw 0 looks
        // forward; the picture's x is to the right.
        double yaw = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
        double[] pos = {forwardIn, leftIn, upIn};
        double[] z = {Math.cos(yaw) * Math.cos(p), Math.sin(yaw) * Math.cos(p), Math.sin(p)};
        double[] x = {Math.sin(yaw), -Math.cos(yaw), 0};
        if (on != null) {
            synchronized (robot) {
                // On a turret: turned (and moved) with it, from where it was measured (the turret at 0).
                double[][] r = robot.shapes.ridingNow(on);
                pos = SimShapes.apply(r, pos);
                z = turn(r, z);
                x = turn(r, x);
            }
        }
        double[] y = cross(z, x);
        // Robot frame to field: forward and left on the field now.
        double fx = -Math.sin(h), fy = Math.cos(h);            // robot forward
        double lx = -Math.cos(h), ly = -Math.sin(h);           // robot left
        double[] fieldPos = {rx + fx * pos[0] + lx * pos[1], ry + fy * pos[0] + ly * pos[1], pos[2]};
        return new double[][] {fieldPos, toField(x, fx, fy, lx, ly), toField(y, fx, fy, lx, ly), toField(z, fx, fy, lx, ly)};
    }

    // A direction turned by a frame's rotation only (no move).
    private static double[] turn(double[][] f, double[] v) {
        return new double[] {f[0][0] * v[0] + f[0][1] * v[1] + f[0][2] * v[2],
                f[1][0] * v[0] + f[1][1] * v[1] + f[1][2] * v[2],
                f[2][0] * v[0] + f[2][1] * v[1] + f[2][2] * v[2]};
    }

    // A robot-frame direction (forward, left, up) on the field.
    private static double[] toField(double[] v, double fx, double fy, double lx, double ly) {
        return new double[] {fx * v[0] + lx * v[1], fy * v[0] + ly * v[1], v[2]};
    }

    /** Pixels per unit of x/z (the lens's focal length in pixels). */
    double focalPx() {
        return (width / 2.0) / Math.tan(Math.toRadians(hfovDeg) / 2);
    }

    /** One tag the camera can see now. */
    static final class Seen {
        final SimField.Tag tag;
        final double[] pose;         // where the tag was when it was seen (SimField.Tag.pose())
        final double[] cam;          // the tag's center in camera axes (inches)
        final double pixels;         // about how many pixels across it is in the picture
        final double cx, cy;         // its center in the picture (pixels)

        Seen(SimField.Tag tag, double[] pose, double[] cam, double pixels, double cx, double cy) {
            this.tag = tag;
            this.pose = pose;
            this.cam = cam;
            this.pixels = pixels;
            this.cx = cx;
            this.cy = cy;
        }
    }

    /**
     * The tags this camera can see: in the picture, facing the camera (within 60 degrees), and big
     * enough to find at this decimation (AprilTag needs about 6.5 pixels across per step of decimation;
     * that matches the FTC docs' ranges for a 2 in tag at 640x480). Field elements do not block the view.
     */
    List<Seen> visibleTags(SimField field, double decimation) {
        return visibleTags(field, decimation, pose());
    }

    /** The same, from a camera pose worked out already (see pose()). */
    List<Seen> visibleTags(SimField field, double decimation, double[][] c) {
        double f = focalPx(), tanH = Math.tan(Math.toRadians(hfovDeg) / 2), tanV = tanH * height / width;
        double minPx = 6.5 * Math.max(1, decimation);
        List<Seen> out = new ArrayList<>();
        if (!lightingOk()) return out;        // too bright or too dark for this setting: no tags found
        for (SimField.Tag t : field.tags) {
            double[] tp = t.pose();
            double[] d = {tp[0] - c[0][0], tp[1] - c[0][1], tp[2] - c[0][2]};
            double[] cam = {dot(d, c[1]), dot(d, c[2]), dot(d, c[3])};
            if (cam[2] < 1) continue;                                          // behind the lens
            if (Math.abs(cam[0] / cam[2]) > tanH || Math.abs(cam[1] / cam[2]) > tanV) continue;   // out of the picture
            double[] n = tagNormal(tp);
            double dist = Math.sqrt(dot(d, d));
            double facing = -dot(n, d) / dist;                                  // cos of the angle to the tag's face
            if (facing < Math.cos(Math.toRadians(60))) continue;              // too slanted, edge-on or from behind
            double pixels = t.sizeIn * f / cam[2] * Math.sqrt(facing);
            if (pixels < minPx) continue;                                      // too small to find
            out.add(new Seen(t, tp, cam, pixels, width / 2.0 + f * cam[0] / cam[2], height / 2.0 + f * cam[1] / cam[2]));
        }
        return out;
    }

    // The decimation the OpMode's AprilTag finder used last (NaN: none yet). The page's reasons use it.
    volatile double decimationNow = Double.NaN;

    /** The decimation for the page: the one used last, or the usual default (the SDK's 3; a Limelight 2). */
    double decimationForPage() {
        double d = decimationNow;
        return Double.isNaN(d) ? (kind.equals("limelight") ? 2 : 3) : d;
    }

    /** Why the camera does or does not see one tag (the page's sight lines). */
    static final class Sight {
        final int id;
        final String why;      // seen, behind, left, right, above, below, slanted, small, light
        final double pixels;   // about how many pixels across (0 when not in the picture)
        final double angleDeg; // how far the tag's face is turned from the camera (0 = straight on)
        final double distIn;

        Sight(int id, String why, double pixels, double angleDeg, double distIn) {
            this.id = id;
            this.why = why;
            this.pixels = pixels;
            this.angleDeg = angleDeg;
            this.distIn = distIn;
        }
    }

    /**
     * Every tag of the field with the reason the camera sees it or not, checked in the same order and with the
     * same numbers as visibleTags (so the page never says "seen" for a tag the OpMode does not get).
     */
    List<Sight> sight(SimField field, double decimation, double[][] c) {
        double f = focalPx(), tanH = Math.tan(Math.toRadians(hfovDeg) / 2), tanV = tanH * height / width;
        double minPx = 6.5 * Math.max(1, decimation);
        boolean light = lightingOk();
        List<Sight> out = new ArrayList<>();
        for (SimField.Tag t : field.tags) {
            double[] tp = t.pose();
            double[] d = {tp[0] - c[0][0], tp[1] - c[0][1], tp[2] - c[0][2]};
            double[] cam = {dot(d, c[1]), dot(d, c[2]), dot(d, c[3])};
            double dist = Math.sqrt(dot(d, d));
            double facing = dist < 1e-9 ? 0 : -dot(tagNormal(tp), d) / dist;
            double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, facing))));
            String why;
            double pixels = 0;
            if (cam[2] < 1) why = "behind";
            else if (Math.abs(cam[0] / cam[2]) > tanH) why = cam[0] > 0 ? "right" : "left";
            else if (Math.abs(cam[1] / cam[2]) > tanV) why = cam[1] > 0 ? "below" : "above";
            else if (facing < Math.cos(Math.toRadians(60))) why = "slanted";
            else {
                pixels = t.sizeIn * f / cam[2] * Math.sqrt(facing);
                why = pixels < minPx ? "small" : light ? "seen" : "light";
            }
            out.add(new Sight(t.id, why, pixels, ang, dist));
        }
        return out;
    }

    /** Which way a tag's face points (unit vector), from the field file's facingDeg and pitchDeg. */
    static double[] tagNormal(double[] pose) {
        double a = Math.toRadians(pose[3]), p = Math.toRadians(pose[4]);
        return new double[] {Math.cos(a) * Math.cos(p), Math.sin(a) * Math.cos(p), Math.sin(p)};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return new double[] {a[0] / l, a[1] / l, a[2] / l};
    }
}
