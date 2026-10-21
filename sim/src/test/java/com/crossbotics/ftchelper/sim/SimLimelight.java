package com.crossbotics.ftchelper.sim;

import com.qualcomm.hardware.limelightvision.LLFieldMap;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.LLStatus;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.hardware.limelightvision.SimLLResults;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A pretend Limelight 3A (limelight.KEY in the robot file). It is the SDK's own Limelight3A class with
 * the network parts replaced: instead of asking the camera over USB-Ethernet, it builds the same JSON
 * result a real Limelight sends from the tags the pretend camera sees, and the SDK's own LLResult reads
 * it. Every pipeline finds AprilTags (each tag on its own, as a Limelight does); pictures come 50 times a
 * second with about 25 ms of delay. Not simulated: botpose / MegaTag (no field map), robot-space poses,
 * Python, color and neural pipelines, snapshots.
 */
final class SimLimelight extends Limelight3A {
    private static final double FPS = 50;
    private static final double CAPTURE_MS = 12, TARGETING_MS = 13;   // like a real one's "cl" and "tl"
    final SimCamera cam;
    private volatile boolean running = false;
    private volatile int pipeline = 0;
    private volatile long pollMs = 10;
    private volatile long startedAt = 0;
    private long lastFrame = -1;
    private long lastPollNanos = 0;
    private LLResult latest = null;
    // What closes this camera when an OpMode stops (the camera's list of open "portals").
    private final AutoCloseable closer = this::stop;

    SimLimelight(SimCamera cam) {
        super(null, cam.name, InetAddress.getLoopbackAddress());
        this.cam = cam;
    }

    @Override public synchronized void start() {
        if (running) return;
        running = true;
        startedAt = System.nanoTime();
        cam.opened(closer);
    }

    @Override public synchronized void pause() { running = false; }

    @Override public synchronized void stop() {
        running = false;
        latest = null;
        lastFrame = -1;
        cam.closed(closer);
    }

    @Override public boolean isRunning() { return running; }

    @Override public synchronized void setPollRateHz(int hz) {
        pollMs = Math.round(1000.0 / Math.max(1, Math.min(250, hz)));
    }

    @Override public long getTimeSinceLastUpdate() { return running ? 0 : 100_000; }
    @Override public boolean isConnected() { return running; }

    /**
     * The newest result, like the SDK's: never null (before the first picture, about 0.5 s after start(),
     * an empty result whose isValid() is false). A new result arrives at the poll rate (setPollRateHz),
     * at most 50 a second (the camera's picture rate).
     */
    @Override public LLResult getLatestResult() {
        double[][] pose = cam.pose();          // the robot's lock first, never inside this object's lock
        synchronized (this) {
            long now = System.nanoTime();
            double t = (now - startedAt) / 1e9;
            if (running && t >= 0.5 && now - lastPollNanos >= pollMs * 1_000_000L) {
                long frame = (long) (t * FPS);
                if (frame != lastFrame) {
                    lastFrame = frame;
                    lastPollNanos = now;
                    try {
                        latest = SimLLResults.parse(result(t * 1000, pose));
                    } catch (org.json.JSONException e) {
                        throw new IllegalStateException("Simulator: could not build a Limelight result", e);
                    }
                }
            }
            if (latest == null) {
                try {
                    latest = SimLLResults.parse(new JSONObject());   // the SDK's empty result: isValid() false
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Simulator: could not build an empty Limelight result", e);
                }
            }
            return latest;
        }
    }

    // The JSON a Limelight sends, from the tags the pretend camera sees.
    private JSONObject result(double timestampMs, double[][] c) throws org.json.JSONException {
        List<SimCamera.Seen> seen = cam.visibleTags(cam.robot.field, 2, c);
        List<double[]> ids = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        JSONObject r = new JSONObject();
        JSONArray fiducials = new JSONArray();
        double bestArea = -1, bestTx = 0, bestTy = 0;
        for (SimCamera.Seen s : seen) {
            double tx = Math.toDegrees(Math.atan2(s.cam[0], s.cam[2]));          // + = right of the crosshair
            double ty = -Math.toDegrees(Math.atan2(s.cam[1], s.cam[2]));         // + = above it
            double area = 100.0 * s.pixels * s.pixels / (cam.width * cam.height);
            double[] n = SimCamera.tagNormal(s.pose);
            double[] nc = {SimCamera.dot(n, c[1]), SimCamera.dot(n, c[2]), SimCamera.dot(n, c[3])};
            // The tag's turn as the camera sees it: yaw about the camera's up/down axis, pitch about its
            // left/right axis (0, 0 when the tag faces the lens straight on). Roll is not worked out.
            double yaw = Math.toDegrees(Math.atan2(nc[0], -nc[2])), pitch = Math.toDegrees(Math.atan2(-nc[1], -nc[2]));
            JSONObject f = new JSONObject();
            f.put("fID", s.tag.id);
            f.put("fam", "36H11C");
            f.put("tx", tx);
            f.put("ty", ty);
            f.put("tx_nocross", tx);
            f.put("ty_nocross", ty);
            f.put("txp", s.cx);
            f.put("typ", s.cy);
            f.put("ta", area);
            f.put("skew", 0);
            // Target in camera space: metres, x right, y down, z out of the lens; then roll, pitch, yaw.
            f.put("t6t_cs", new JSONArray(new double[] {s.cam[0] * 0.0254, s.cam[1] * 0.0254, s.cam[2] * 0.0254, 0, pitch, yaw}));
            fiducials.put(f);
            ids.add(new double[] {s.tag.id});
            notes.add(String.format(Locale.US, "tag %d: tx %.1f deg, ty %.1f deg, %.1f in along the floor",
                    s.tag.id, tx, ty, Math.hypot(s.cam[0], s.cam[2])));
            if (area > bestArea) { bestArea = area; bestTx = tx; bestTy = ty; }
        }
        cam.lastSeen(ids);
        cam.lastDetections(notes);
        r.put("v", seen.isEmpty() ? 0 : 1);
        r.put("ts", timestampMs);
        r.put("cl", CAPTURE_MS);
        r.put("tl", TARGETING_MS);
        r.put("pID", pipeline);
        r.put("pipelineType", "pipe_fiducial");
        r.put("tx", seen.isEmpty() ? 0 : bestTx);
        r.put("ty", seen.isEmpty() ? 0 : bestTy);
        r.put("ta", Math.max(0, bestArea));
        r.put("Fiducial", fiducials);
        return r;
    }

    @Override public LLStatus getStatus() {
        try {
            return status();
        } catch (org.json.JSONException e) {
            throw new IllegalStateException("Simulator: could not build a Limelight status", e);
        }
    }

    private LLStatus status() throws org.json.JSONException {
        JSONObject s = new JSONObject();
        s.put("name", cam.name);
        s.put("fps", running ? FPS : 0);
        s.put("pipelineIndex", pipeline);
        s.put("pipelineType", "pipe_fiducial");
        s.put("temp", 40);
        s.put("cpu", 30);
        s.put("ram", 40);
        return SimLLResults.status(s);
    }

    @Override public boolean pipelineSwitch(int index) {
        if (index < 0 || index > 9) return false;
        pipeline = index;
        cam.pipeline = index;
        return true;
    }

    // Accepted and ignored (no real camera to send them to).
    @Override public boolean reloadPipeline() { return true; }
    @Override public boolean captureSnapshot(String name) { return true; }
    @Override public boolean deleteSnapshots() { return true; }
    @Override public boolean deleteSnapshot(String name) { return true; }
    @Override public boolean updatePythonInputs(double a, double b, double c, double d, double e, double f, double g, double h) { return true; }
    @Override public boolean updatePythonInputs(double[] inputs) { return inputs != null && inputs.length <= 32; }
    @Override public boolean updateRobotOrientation(double yaw) { return true; }
    @Override public boolean uploadPipeline(String json, Integer index) { return true; }
    @Override public boolean uploadFieldmap(LLFieldMap map, Integer index) { return true; }
    @Override public boolean uploadPython(String code, Integer index) { return true; }
    @Override public LLResultTypes.CalibrationResult getCalDefault() { return null; }
    @Override public LLResultTypes.CalibrationResult getCalFile() { return null; }
    @Override public LLResultTypes.CalibrationResult getCalEEPROM() { return null; }
    @Override public LLResultTypes.CalibrationResult getCalLatest() { return null; }
    @Override public void shutdown() { stop(); }
    @Override public Manufacturer getManufacturer() { return Manufacturer.Other; }
    @Override public String getDeviceName() { return "Pretend Limelight 3A"; }
    @Override public String getConnectionInfo() { return "simulator"; }
    @Override public int getVersion() { return 1; }
    @Override public void resetDeviceConfigurationForOpMode() { }
    @Override public void close() { stop(); }
}
