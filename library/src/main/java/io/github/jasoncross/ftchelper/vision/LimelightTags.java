package io.github.jasoncross.ftchelper.vision;

import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.hardware.Hw;
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
        if (started) limelight.pipelineSwitch(index);
        return this;
    }

    /** How often to ask for results, per second. Default 100. */
    public LimelightTags pollRate(int hz) {
        pollRateHz = hz;
        return this;
    }

    /**
     * Tells the Limelight the robot's heading (for its MegaTag2 field position feature).
     * Not needed for aiming. Call it every loop if you use MegaTag2.
     */
    public void setRobotHeading(double headingCcwDeg) {
        limelight.updateRobotOrientation(headingCcwDeg);
    }

    @Override
    public void start() {
        if (started) return;
        limelight.setPollRateHz(pollRateHz);
        limelight.pipelineSwitch(pipeline);
        limelight.start();
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
        if (result.getTimestamp() == lastTimestamp) return;   // the same frame again: keep the list
        lastTimestamp = result.getTimestamp();
        long pictureTime = System.nanoTime() - (long) (result.getStaleness() * 1_000_000L);
        List<TargetObservation> out = new ArrayList<>();
        for (LLResultTypes.FiducialResult f : result.getFiducialResults()) {
            out.add(new TargetObservation(f.getFiducialId(), "Tag " + f.getFiducialId(),
                    -f.getTargetXDegrees(), distanceInches(f.getTargetPoseCameraSpace()), pictureTime));
        }
        latest = out;
    }

    /** Straight-line distance from a pose, in inches (NaN if there is no pose). Public for tests. */
    public static double distanceInches(Pose3D pose) {
        if (pose == null || pose.getPosition() == null) return Double.NaN;
        Position p = pose.getPosition().toUnit(DistanceUnit.INCH);
        double d = Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z);
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
    }

    /** The SDK's Limelight object, for anything this class does not cover (e.g. color pipelines). */
    public Limelight3A getLimelight() { return limelight; }

    @Override public String name() { return name; }
}
