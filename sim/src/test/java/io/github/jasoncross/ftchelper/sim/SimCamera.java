package io.github.jasoncross.ftchelper.sim;

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
 */
final class SimCamera implements InvocationHandler {
    final String name;
    final String kind;          // "webcam" or "limelight"
    final double forwardIn, leftIn, upIn, yawDeg, pitchDeg, hfovDeg;
    final int width, height;
    final WebcamName device;     // null for a Limelight (SimLimelight is its device)
    final SimRobot robot;
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
        // Logitech C920 at 640x480: about 70 degrees across; Limelight 3A: 54.5 (its spec sheet)
        hfovDeg = num(s, "hfov", kind.equals("limelight") ? 54.5 : 70.4);
        width = (int) num(s, "width", 640);
        height = (int) num(s, "height", 480);
        if (hfovDeg <= 1 || hfovDeg >= 179) throw new IllegalArgumentException("Robot file: camera." + name + ": hfov must be between 1 and 179 degrees");
        if (width < 16 || height < 16) throw new IllegalArgumentException("Robot file: camera." + name + ": width and height must be at least 16 pixels");
        device = kind.equals("webcam")
                ? (WebcamName) Proxy.newProxyInstance(WebcamName.class.getClassLoader(), new Class<?>[] {WebcamName.class}, this) : null;
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
        double rx, ry, h;
        synchronized (robot) {
            rx = robot.x;
            ry = robot.y;
            h = Math.toRadians(robot.headingDeg);
        }
        double fx = -Math.sin(h), fy = Math.cos(h);            // robot forward
        double lx = -Math.cos(h), ly = -Math.sin(h);           // robot left
        double[] pos = {rx + fx * forwardIn + lx * leftIn, ry + fy * forwardIn + ly * leftIn, upIn};
        double look = h + Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
        double[] z = {-Math.sin(look) * Math.cos(p), Math.cos(look) * Math.cos(p), Math.sin(p)};
        double[] x = {Math.cos(look), Math.sin(look), 0};
        double[] y = cross(z, x);
        return new double[][] {pos, x, y, z};
    }

    /** Pixels per unit of x/z (the lens's focal length in pixels). */
    double focalPx() {
        return (width / 2.0) / Math.tan(Math.toRadians(hfovDeg) / 2);
    }

    /** One tag the camera can see now. */
    static final class Seen {
        final SimField.Tag tag;
        final double[] cam;          // the tag's center in camera axes (inches)
        final double pixels;         // about how many pixels across it is in the picture
        final double cx, cy;         // its center in the picture (pixels)

        Seen(SimField.Tag tag, double[] cam, double pixels, double cx, double cy) {
            this.tag = tag;
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
        double[][] c = pose();
        double f = focalPx(), tanH = Math.tan(Math.toRadians(hfovDeg) / 2), tanV = tanH * height / width;
        double minPx = 6.5 * Math.max(1, decimation);
        List<Seen> out = new ArrayList<>();
        for (SimField.Tag t : field.tags) {
            double[] d = {t.x - c[0][0], t.y - c[0][1], t.z - c[0][2]};
            double[] cam = {dot(d, c[1]), dot(d, c[2]), dot(d, c[3])};
            if (cam[2] < 1) continue;                                          // behind the lens
            if (Math.abs(cam[0] / cam[2]) > tanH || Math.abs(cam[1] / cam[2]) > tanV) continue;   // out of the picture
            double[] n = tagNormal(t);
            double dist = Math.sqrt(dot(d, d));
            double facing = -dot(n, d) / dist;                                  // cos of the angle to the tag's face
            if (facing < Math.cos(Math.toRadians(60))) continue;              // too slanted, edge-on or from behind
            double pixels = t.sizeIn * f / cam[2] * Math.sqrt(facing);
            if (pixels < minPx) continue;                                      // too small to find
            out.add(new Seen(t, cam, pixels, width / 2.0 + f * cam[0] / cam[2], height / 2.0 + f * cam[1] / cam[2]));
        }
        return out;
    }

    /** Which way a tag's face points (unit vector), from the field file's facingDeg and pitchDeg. */
    static double[] tagNormal(SimField.Tag t) {
        double a = Math.toRadians(t.facingDeg), p = Math.toRadians(t.pitchDeg);
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
