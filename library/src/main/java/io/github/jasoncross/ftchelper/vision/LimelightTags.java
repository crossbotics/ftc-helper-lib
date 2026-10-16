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
 * AprilTags with a Limelight 3A. The Limelight finds the tags itself (set up a fiducial pipeline
 * in its web page first); this class reads its results through the FTC SDK.
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
 * Docs: docs/modules/vision.md
 */
public class LimelightTags implements TargetSource {
    private final String name;
    private final Limelight3A limelight;
    private int pipeline = 0;
    private int pollRateHz = 100;
    private boolean started = false;
    private List<TargetObservation> latest = Collections.emptyList();
    private double lastTimestamp = Double.NaN;
    private double keepMs = 500;
    // pipelineSwitch() and updateRobotOrientation() are network requests that WAIT for an answer.
    // They run on this worker so the robot's loop never waits.
    private BackgroundWorker worker = null;

    public static LimelightTags create(HardwareMap hardwareMap, String deviceName) {
        return new LimelightTags(deviceName, Hw.get(hardwareMap, Limelight3A.class, deviceName));
    }

    public LimelightTags(String name, Limelight3A limelight) {
        this.name = name;
        this.limelight = limelight;
    }

    /** The pipeline to use (set up on the Limelight's web page). Default 0. */
    public LimelightTags pipeline(int index) {
        pipeline = index;
        if (started) worker().submitLatest("pipeline", () -> limelight.pipelineSwitch(index));   // never waits
        return this;
    }

    /** Targets older than this are dropped (milliseconds, default 500). */
    public LimelightTags keepTargetsMs(double ms) {
        keepMs = Math.abs(ms);
        return this;
    }

    /** How often to ask for results, per second. Default 100. */
    public LimelightTags pollRate(int hz) {
        pollRateHz = hz;
        return this;
    }

    /**
     * Tells the Limelight the robot's heading (for its MegaTag2 field position feature).
     * Not needed for aiming. Safe to call every loop: it is sent in the background, and only the
     * newest heading is sent if the network is slower than the loop.
     */
    public void setRobotHeading(double headingCcwDeg) {
        worker().submitLatest("heading", () -> limelight.updateRobotOrientation(headingCcwDeg));
    }

    @Override
    public void start() {
        if (started) return;
        limelight.setPollRateHz(pollRateHz);
        final int first = pipeline;
        worker().submitLatest("pipeline", () -> limelight.pipelineSwitch(first));   // a network request: in the background
        limelight.start();                                                             // starts the SDK's own polling thread
        started = true;
    }

    @Override
    public void update() {
        if (!started) start();
        LLResult result = limelight.getLatestResult();
        if (result == null || !result.isValid()) {
            latest = Collections.emptyList();
            return;
        }
        if (result.getTimestamp() == lastTimestamp) {          // the same frame again: keep the list while recent
            latest = WebcamTags.dropOld(latest, System.nanoTime(), keepMs);
            return;
        }
        lastTimestamp = result.getTimestamp();
        long pictureTime = pictureTimeNanos(System.nanoTime(), result.getStaleness(),
                result.getCaptureLatency(), result.getTargetingLatency(), result.getParseLatency());
        List<TargetObservation> out = new ArrayList<>();
        for (LLResultTypes.FiducialResult f : result.getFiducialResults()) {
            out.add(new TargetObservation(f.getFiducialId(), "Tag " + f.getFiducialId(),
                    -f.getTargetXDegrees(), distanceInches(f.getTargetPoseCameraSpace()), pictureTime));
        }
        latest = out;
    }

    /**
     * When the picture was really taken: now, minus the time since the hub got the result (staleness),
     * minus the Limelight's own capture, processing and parse time. All latencies in milliseconds.
     * Public for tests.
     */
    public static long pictureTimeNanos(long nowNanos, long stalenessMs, double captureMs, double targetingMs, double parseMs) {
        double totalMs = Math.max(0, stalenessMs) + Math.max(0, captureMs) + Math.max(0, targetingMs) + Math.max(0, parseMs);
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
        double d = Math.hypot(p.x, p.z);
        return d > 0 ? d : Double.NaN;
    }

    @Override public List<TargetObservation> targets() { return latest; }

    @Override
    public boolean isWorking() {
        return started && limelight.isConnected() && limelight.isRunning();
    }

    @Override
    public String status() {
        if (!started) return "not started";
        if (!limelight.isConnected()) return "not connected";
        return String.format(Locale.US, "pipeline %d, %d tags", pipeline, latest.size());
    }

    @Override
    public void close() {
        if (started) limelight.stop();
        started = false;
        latest = Collections.emptyList();
        if (worker != null) worker.shutdown();
        worker = null;
    }

    private BackgroundWorker worker() {
        if (worker == null) worker = new BackgroundWorker("limelight");
        return worker;
    }

    /** The SDK's Limelight object, for anything this class does not cover (e.g. color pipelines). */
    public Limelight3A getLimelight() { return limelight; }

    @Override public String name() { return name; }
}
