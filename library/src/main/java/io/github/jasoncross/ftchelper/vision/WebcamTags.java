package io.github.jasoncross.ftchelper.vision;

import android.util.Size;
import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.hardware.Hw;
import io.github.jasoncross.ftchelper.util.BackgroundWorker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.hardware.camera.controls.ExposureControl;
import org.firstinspires.ftc.robotcore.external.hardware.camera.controls.GainControl;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.apriltag.AprilTagClusterDetection;
import org.firstinspires.ftc.vision.apriltag.AprilTagDetection;
import org.firstinspires.ftc.vision.apriltag.AprilTagGameDatabase;
import org.firstinspires.ftc.vision.apriltag.AprilTagLibrary;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.firstinspires.ftc.vision.apriltag.AprilTagSingleDetection;

/**
 * AprilTags with a USB webcam, using the FTC SDK's own AprilTag finder (SDK 12: single tags and
 * tag "clusters").
 *
 * <pre>{@code
 * WebcamTags camera = WebcamTags.create(hardwareMap, "Webcam 1")
 *         .resolution(640, 480)
 *         .exposure(6, 85);          // optional: short exposure = less blur while driving
 * camera.start();                    // in INIT
 * // every loop:
 * camera.update();
 * TargetObservation tag = camera.target(20);
 * }</pre>
 *
 * <p>The tag library is this season's from the SDK ({@code getCurrentGameTagLibrary()}); it gives
 * bearing and distance for every tag and cluster it knows. Unknown tags have an id but no bearing.
 * For best distances, calibrate your camera (see docs/modules/vision.md).
 *
 * Docs: docs/modules/vision.md
 */
public class WebcamTags implements TargetSource {
    private final String name;
    private final WebcamName webcam;
    private int width = 640, height = 480;
    private float decimation = 2;
    private boolean liveView = false;
    private AprilTagLibrary library = null;
    private long exposureMs = -1;
    private int gain = -1;

    private VisionPortal portal = null;
    private AprilTagProcessor processor = null;
    private List<TargetObservation> latest = Collections.emptyList();
    private volatile boolean controlsApplied = false;
    private BackgroundWorker worker = null;
    private double keepMs = 500;
    private volatile String controlsNote = "";

    public static WebcamTags create(HardwareMap hardwareMap, String webcamName) {
        return new WebcamTags(webcamName, Hw.get(hardwareMap, WebcamName.class, webcamName));
    }

    public WebcamTags(String name, WebcamName webcam) {
        this.name = name;
        this.webcam = webcam;
    }

    /**
     * Targets older than this are dropped from {@link #targets()} (milliseconds, default 500), so a
     * camera that stops sending pictures never leaves old targets behind.
     */
    public WebcamTags keepTargetsMs(double ms) {
        keepMs = Math.abs(ms);
        return this;
    }

    /** Camera resolution. 640x480 is a good start. Higher = farther tags, slower. */
    public WebcamTags resolution(int w, int h) {
        width = w;
        height = h;
        return this;
    }

    /** Skips pixels to go faster: 2 = default, 3 = faster but shorter range, 1 = slowest, longest range. */
    public WebcamTags decimation(float d) {
        decimation = d;
        return this;
    }

    /** Shows the camera picture on the Robot Controller screen (costs speed). Default off. */
    public WebcamTags liveView(boolean on) {
        liveView = on;
        return this;
    }

    /** A different tag library (default: this season's from the SDK). */
    public WebcamTags library(AprilTagLibrary tagLibrary) {
        library = tagLibrary;
        return this;
    }

    /**
     * Manual exposure (milliseconds) and gain. Short exposure (2-6 ms) makes tags sharp while driving,
     * but darker; raise gain to compensate. Applied once the camera is streaming. Not every camera
     * supports it; {@link #status()} says if it failed.
     */
    public WebcamTags exposure(long milliseconds, int cameraGain) {
        exposureMs = milliseconds;
        gain = cameraGain;
        return this;
    }

    @Override
    public void start() {
        if (portal != null) return;
        processor = new AprilTagProcessor.Builder()
                .setTagLibrary(library != null ? library : AprilTagGameDatabase.getCurrentGameTagLibrary())
                .setOutputUnits(DistanceUnit.INCH, AngleUnit.DEGREES)
                .build();
        processor.setDecimation(decimation);
        portal = new VisionPortal.Builder()
                .setCamera(webcam)
                .setCameraResolution(new Size(width, height))
                .enableLiveView(liveView)
                .addProcessor(processor)
                .build();
    }

    @Override
    public void update() {
        if (portal == null) start();
        if (!controlsApplied && exposureMs >= 0 && portal.getCameraState() == VisionPortal.CameraState.STREAMING) {
            // Camera settings are USB requests that can take a moment: do them in the background, once.
            controlsApplied = true;
            if (worker == null) worker = new BackgroundWorker("webcam");
            worker.submit(this::applyControls);
        }
        List<AprilTagDetection> fresh = processor.getFreshDetections();
        if (fresh == null) {          // no new picture since last time: keep the last list, while it is recent
            latest = dropOld(latest, System.nanoTime(), keepMs);
            return;
        }
        List<TargetObservation> out = new ArrayList<>();
        for (AprilTagDetection d : fresh) out.add(convert(d));
        latest = out;
    }

    /** Turns one SDK detection (single tag or cluster) into a TargetObservation. Public for tests. */
    public static TargetObservation convert(AprilTagDetection d) {
        double bearing = d.ftcPose != null ? d.ftcPose.bearing : Double.NaN;
        double range = d.ftcPose != null ? d.ftcPose.range : Double.NaN;
        if (d instanceof AprilTagSingleDetection) {
            AprilTagSingleDetection s = (AprilTagSingleDetection) d;
            String tagName = s.metadata != null ? s.metadata.name : "Tag " + s.id;
            return new TargetObservation(s.id, tagName, bearing, range, d.frameAcquisitionNanoTime);
        }
        if (d instanceof AprilTagClusterDetection) {
            AprilTagClusterDetection c = (AprilTagClusterDetection) d;
            String clusterName = c.metadata != null ? c.metadata.name : "Cluster";
            return new TargetObservation(TargetObservation.CLUSTER, clusterName, bearing, range, d.frameAcquisitionNanoTime);
        }
        return new TargetObservation(TargetObservation.CLUSTER, "Unknown", bearing, range, d.frameAcquisitionNanoTime);
    }

    @Override
    public List<TargetObservation> targets() {
        return latest;
    }

    /** The observations not older than {@code keepMs}. Public for tests. */
    public static List<TargetObservation> dropOld(List<TargetObservation> list, long nowNanos, double keepMs) {
        if (list.isEmpty()) return list;
        List<TargetObservation> out = new ArrayList<>();
        for (TargetObservation o : list) if (o.isFresh(nowNanos, keepMs)) out.add(o);
        return out.size() == list.size() ? list : out;
    }

    @Override
    public boolean isWorking() {
        return portal != null && portal.getCameraState() == VisionPortal.CameraState.STREAMING;
    }

    @Override
    public String status() {
        if (portal == null) return "not started";
        String s = portal.getCameraState() + (isWorking() ? String.format(Locale.US, " %.0f fps", portal.getFps()) : "");
        return controlsNote.isEmpty() ? s : s + " (" + controlsNote + ")";
    }

    @Override
    public void close() {
        if (worker != null) worker.shutdown();
        worker = null;
        if (portal != null) portal.close();
        portal = null;
        latest = Collections.emptyList();
    }

    @Override public String name() { return name; }

    private void applyControls() {
        try {
            ExposureControl exposure = portal.getCameraControl(ExposureControl.class);
            boolean ok = exposure != null && exposure.setMode(ExposureControl.Mode.Manual)
                    && exposure.setExposure(exposureMs, TimeUnit.MILLISECONDS);
            GainControl gainControl = portal.getCameraControl(GainControl.class);
            ok = ok && gainControl != null && gainControl.setGain(gain);
            controlsNote = ok ? "manual exposure" : "exposure not supported";
        } catch (RuntimeException e) {
            controlsNote = "exposure failed";
        }
    }
}
