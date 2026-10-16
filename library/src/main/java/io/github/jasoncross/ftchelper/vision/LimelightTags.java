package io.github.jasoncross.ftchelper.vision;

import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.hardware.Hw;
import io.github.jasoncross.ftchelper.util.BackgroundWorker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.robotcore.external.navigation.Position;

/**
 * AprilTags with a Limelight 3A. The Limelight is a camera with its own small computer: it finds the
 * tags itself (set up a fiducial pipeline in its web page first; "fiducial" = AprilTag, and a
 * "pipeline" is one saved set of Limelight settings). This class reads its results through the FTC SDK.
 *
 * <pre>{@code
 * LimelightTags camera = LimelightTags.create(hardwareMap, "limelight")
 *         .pipeline(0)               // the pipeline number set up on the Limelight
 *         .pollRate(100);            // results per second
 * camera.start();                    // in INIT
 * // every loop:
 * camera.update();
 * TargetObservation tag = camera.target(20);
 * }</pre>
 *
 * <p>Bearing is the Limelight's tx with the sign flipped (tx is positive to the RIGHT; the
 * library uses positive = LEFT everywhere). Distance comes from the tag's 3D pose and needs the
 * correct tag size in the pipeline; it is NaN when the pipeline gives no pose.
 *
 * Docs: docs/modules/vision.md. Why slow requests go in the background: docs/guides/non-blocking.md
 */
public class LimelightTags implements TargetSource {
    private final String name;
    private final Limelight3A limelight;
    private int pipeline = 0;
    private int pollRateHz = 100;
    private boolean started = false;
    // The targets from the newest picture. Starts as an empty list (never null, so loops over it are safe).
    private List<TargetObservation> latest = Collections.emptyList();
    // The Limelight's time stamp of the last picture we read, to notice when it sends the same one again.
    private double lastTimestamp = Double.NaN;
    private double keepMs = 500;
    // pipelineSwitch() and updateRobotOrientation() are network requests that WAIT for an answer.
    // They run on this worker so the robot's loop never waits.
    private BackgroundWorker worker = null;

    /**
     * Finds the Limelight in the robot configuration and wraps it.
     * {@code static} means you call it on the class itself: {@code LimelightTags.create(...)}.
     *
     * @param hardwareMap the OpMode's hardwareMap
     * @param deviceName  the Limelight's name in the robot configuration, e.g. "limelight"
     */
    public static LimelightTags create(HardwareMap hardwareMap, String deviceName) {
        return new LimelightTags(deviceName, Hw.get(hardwareMap, Limelight3A.class, deviceName));
    }

    /** Wraps a Limelight you already have. Most teams use {@link #create} instead. */
    public LimelightTags(String name, Limelight3A limelight) {
        this.name = name;
        this.limelight = limelight;
    }

    /** The pipeline to use (set up on the Limelight's web page). Default 0. */
    public LimelightTags pipeline(int index) {
        pipeline = index;
        // Already running? Switch now. Switching is a network request that waits for an answer, so it
        // goes to the background worker ("() -> ..." is a small piece of code handed over to run later).
        if (started) worker().submitLatest("pipeline", () -> limelight.pipelineSwitch(index));   // never waits
        return this;
    }

    /** Targets older than this are dropped (milliseconds, default 500). */
    public LimelightTags keepTargetsMs(double ms) {
        keepMs = Math.abs(ms);
        return this;
    }

    /** How often to ask for results, per second. Default 100. Set it before {@link #start()}. */
    public LimelightTags pollRate(int hz) {
        pollRateHz = hz;
        return this;
    }

    /**
     * Tells the Limelight the robot's heading (for its MegaTag2 field position feature).
     * Not needed for aiming. Safe to call every loop: it is sent in the background, and only the
     * newest heading is sent if the network is slower than the loop.
     *
     * @param headingCcwDeg the robot's heading in degrees, positive = turned left (the IMU's sign)
     */
    public void setRobotHeading(double headingCcwDeg) {
        worker().submitLatest("heading", () -> limelight.updateRobotOrientation(headingCcwDeg));
    }

    /** Starts the Limelight. Call in INIT. Calling it again does nothing. */
    @Override
    public void start() {
        if (started) return;
        limelight.setPollRateHz(pollRateHz);
        // "final" copy: the code handed to the worker runs later, so it gets a value that cannot change.
        final int first = pipeline;
        worker().submitLatest("pipeline", () -> limelight.pipelineSwitch(first));   // a network request: in the background
        limelight.start();                                                             // starts the SDK's own polling thread
        started = true;
    }

    /** Every loop: reads the newest result and turns each tag into a {@link TargetObservation}. Never waits. */
    @Override
    public void update() {
        // Forgot to call start()? Do it now, so the camera still works.
        if (!started) start();
        // getLatestResult() does not wait: the SDK keeps the newest result ready for us.
        LLResult result = limelight.getLatestResult();
        // No result, or nothing usable in it: we see nothing right now.
        if (result == null || !result.isValid()) {
            latest = Collections.emptyList();
            return;
        }
        if (result.getTimestamp() == lastTimestamp) {          // the same frame again: keep the list while recent
            // Forget targets that are too old, so a Limelight that stops sending never leaves
            // an old target behind for the robot to aim at.
            latest = WebcamTags.dropOld(latest, System.nanoTime(), keepMs);
            return;
        }
        // A new picture.
        lastTimestamp = result.getTimestamp();
        // Work out when the picture was really taken (it is already a little old when we get it).
        long pictureTime = pictureTimeNanos(System.nanoTime(), result.getStaleness(),
                result.getCaptureLatency(), result.getTargetingLatency(), result.getParseLatency());
        List<TargetObservation> out = new ArrayList<>();
        for (LLResultTypes.FiducialResult f : result.getFiducialResults()) {
            // The minus sign flips the Limelight's tx (positive = RIGHT) into our bearing (positive = LEFT),
            // so the Limelight and the webcam agree. See docs/conventions.md.
            out.add(new TargetObservation(f.getFiducialId(), "Tag " + f.getFiducialId(),
                    -f.getTargetXDegrees(), distanceInches(f.getTargetPoseCameraSpace()), pictureTime));
        }
        latest = out;
    }

    /**
     * When the picture was really taken: now, minus the time since the hub got the result (staleness),
     * minus the Limelight's own capture, processing and parse time. All latencies in milliseconds.
     * Public for tests.
     *
     * @return the picture time on the {@code System.nanoTime()} clock (nanoseconds)
     */
    public static long pictureTimeNanos(long nowNanos, long stalenessMs, double captureMs, double targetingMs, double parseMs) {
        // Math.max(0, ...): a negative delay makes no sense (a glitch), so count it as 0.
        double totalMs = Math.max(0, stalenessMs) + Math.max(0, captureMs) + Math.max(0, targetingMs) + Math.max(0, parseMs);
        // milliseconds -> nanoseconds: 1,000,000 nanoseconds in a millisecond
        return nowNanos - (long) (totalMs * 1_000_000L);
    }

    /**
     * Distance to the target along the FLOOR (ignoring height), in inches, NaN if there is no pose.
     * Same meaning as the webcam's distance, so a shot table works with either camera.
     * Limelight camera space: x right, y down, z forward. Public for tests.
     */
    public static double distanceInches(Pose3D pose) {
        if (pose == null || pose.getPosition() == null) return Double.NaN;
        Position p = pose.getPosition().toUnit(DistanceUnit.INCH);
        // Only sideways (x) and forward (z): leaving out y (up/down) gives the distance along the floor.
        double d = Math.hypot(p.x, p.z);
        // 0 means the pipeline filled in no real pose (a tag cannot be 0 inches away), so: unknown.
        return d > 0 ? d : Double.NaN;
    }

    @Override public List<TargetObservation> targets() { return latest; }

    /** True when started, connected and running. */
    @Override
    public boolean isWorking() {
        return started && limelight.isConnected() && limelight.isRunning();
    }

    /** e.g. "pipeline 0, 2 tags", "not connected" or "not started". */
    @Override
    public String status() {
        if (!started) return "not started";
        if (!limelight.isConnected()) return "not connected";
        return String.format(Locale.US, "pipeline %d, %d tags", pipeline, latest.size());
    }

    /** Stops the Limelight, forgets its targets and ends the background worker. */
    @Override
    public void close() {
        if (started) limelight.stop();
        started = false;
        // nothing seen any more: an old target must never be used after the camera stopped
        latest = Collections.emptyList();
        // end the background helper, so it does not keep running after the OpMode
        if (worker != null) worker.shutdown();
        worker = null;
    }

    // Makes the background worker the first time it is needed (and again after close()).
    private BackgroundWorker worker() {
        if (worker == null) worker = new BackgroundWorker("limelight");
        return worker;
    }

    /** The SDK's Limelight object, for anything this class does not cover (e.g. color pipelines). */
    public Limelight3A getLimelight() { return limelight; }

    @Override public String name() { return name; }
}
