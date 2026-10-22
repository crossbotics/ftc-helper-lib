package com.crossbotics.ftchelper.vision;

import android.util.Size;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.util.BackgroundWorker;
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
 * tag "clusters", groups of tags with one aiming point).
 *
 * <pre>{@code
 * WebcamTags camera = WebcamTags.create(hardwareMap, "Webcam 1")
 *         .resolution(640, 480)
 *         .exposure(6, 85);          // optional: short exposure = less blur while driving
 * camera.start();                    // in INIT
 * // every loop:
 * camera.update();
 * TargetObservation goal = camera.target("RED SCORING");   // a BIOBUZZ CELL group (an SDK cluster)
 * }</pre>
 *
 * <p>The tag library (the list of tags the SDK knows: their numbers, names and sizes) is this
 * season's from the SDK ({@code getCurrentGameTagLibrary()}); it gives bearing and distance for every
 * tag and cluster it knows. Unknown tags have an id but no bearing.
 * For best distances, calibrate your camera (see docs/modules/vision.md).
 *
 * <p>Which way is left: the SDK's bearing is already positive = LEFT, the same as the library's,
 * so (unlike the Limelight) nothing is flipped here. See docs/conventions.md.
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
    // -1 means "not set": leave the camera's automatic exposure alone.
    private long exposureMs = -1;
    private int gain = -1;

    // The SDK's camera manager (the "portal") and its AprilTag finder (the "processor").
    // null until start() makes them.
    private VisionPortal portal = null;
    private AprilTagProcessor processor = null;
    private List<TargetObservation> latest = Collections.emptyList();
    // "volatile": this value is shared with the background worker. volatile makes sure the loop
    // and the worker always see each other's newest value.
    private volatile boolean controlsApplied = false;
    private BackgroundWorker worker = null;
    private double keepMs = 500;
    // A note for status(), written by the background worker ("manual exposure", "exposure failed"...).
    private volatile String controlsNote = "";
    // Venue lighting (autoExposure): the exposures to try (0 = the camera's own automatic exposure),
    // and the helper that tries them. null = off.
    private long[] autoList = null;
    private LightingSweep sweep = null;
    // Each switch gets a number; the worker writes the number it has finished. When they match, the
    // camera has the setting being tried, so its pictures count. (volatile: shared with the worker)
    private int requested = 0;
    private volatile int applied = -1;
    // Did the camera take the setting numbered "applied"? Written by the worker before "applied".
    private volatile boolean appliedOk = false;
    // The last switch the loop has told the sweep about (done or refused).
    private int reported = -1;
    // The camera's own gain, read before the first manual setting, so "auto exposure" gets it back.
    private volatile int savedGain = Integer.MIN_VALUE;

    /** The exposures {@link #autoExposure()} tries: the camera's own (0), then 3, 6 and 12 ms. */
    public static final long[] DEFAULT_EXPOSURES_MS = {0, 3, 6, 12};

    /**
     * Finds the webcam in the robot configuration and wraps it.
     * {@code static} means you call it on the class itself: {@code WebcamTags.create(...)}.
     *
     * @param hardwareMap the OpMode's hardwareMap
     * @param webcamName  the webcam's name in the robot configuration, e.g. "Webcam 1"
     */
    public static WebcamTags create(HardwareMap hardwareMap, String webcamName) {
        return new WebcamTags(webcamName, Hw.get(hardwareMap, WebcamName.class, webcamName));
    }

    /** Wraps a webcam you already have. Most teams use {@link #create} instead. */
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

    /** Camera resolution (pixels). 640x480 is a good start. Higher = farther tags, slower. Set before start(). */
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
     *
     * @param milliseconds how long each picture collects light (shorter = less blur, darker)
     * @param cameraGain   brightness boost (higher = brighter, more grainy); -1 = the camera's highest
     */
    public WebcamTags exposure(long milliseconds, int cameraGain) {
        exposureMs = milliseconds;
        gain = cameraGain;
        return this;
    }

    /**
     * The gain for {@link #autoExposure}'s manual exposures (default -1: the camera's highest).
     *
     * @param cameraGain brightness boost (higher = brighter, more grainy)
     */
    public WebcamTags gain(int cameraGain) {
        gain = cameraGain;
        return this;
    }

    /**
     * Venue lighting: tries {@link #DEFAULT_EXPOSURES_MS} (the camera's own automatic exposure, then
     * 3, 6 and 12 ms) in INIT and keeps the one whose pictures see the most AprilTags.
     * See {@link #autoExposure(long...)}.
     */
    public WebcamTags autoExposure() {
        return autoExposure(DEFAULT_EXPOSURES_MS);
    }

    /**
     * Venue lighting: tries these exposures one after the other in INIT, counts the pictures that see
     * AprilTags with each, and keeps the best one. Put your usual one FIRST (it wins ties, and it is
     * kept if no tag is seen at all). Have a tag in view at INIT: at START the test stops and keeps
     * the best exposure so far. A setting the camera refuses is skipped. Each manual exposure uses the
     * gain from {@link #exposure(long, int)} or {@link #gain(int)}, or the camera's highest gain if none
     * was given (a short exposure is dark; gain brightens it); "auto" gets the camera's own gain back. Replaces
     * {@link #exposure(long, int)}'s exposure. Never waits: camera settings are sent in the background.
     * {@link #lighting()} has the result. Not every webcam allows manual exposure ({@link #status()} says).
     *
     * <pre>{@code
     * WebcamTags camera = WebcamTags.create(hardwareMap, "Webcam 1").autoExposure(6, 3, 12, 0);
     * }</pre>
     *
     * @param exposuresMs exposures in milliseconds, usual one first; 0 = the camera's own automatic exposure
     */
    public WebcamTags autoExposure(long... exposuresMs) {
        if (exposuresMs == null || exposuresMs.length == 0) {
            autoList = null;
            sweep = null;
            return this;
        }
        autoList = exposuresMs.clone();
        List<String> labels = new ArrayList<>();
        for (long ms : autoList) labels.add(ms <= 0 ? "auto exposure" : ms + " ms exposure");
        // i -> ...: the code the sweep runs to switch to setting number i (camera settings, in the background)
        sweep = new LightingSweep(labels, this::switchTo, System::nanoTime);
        return this;
    }

    /** The venue-lighting helper (its numbers, status and result), or null without {@link #autoExposure}. */
    public LightingSweep lighting() {
        return sweep;
    }

    /**
     * Tell the camera the match has started, so an unfinished {@link #autoExposure} test stops and
     * keeps the best exposure so far (the five-files Camera module does this for you). Safe to call
     * without autoExposure.
     */
    public void matchStarted() {
        if (sweep != null) sweep.matchStarted();
    }

    // Sends setting number i to the camera, in the background (USB requests can take a moment).
    private void switchTo(int i) {
        final long ms = autoList[i];
        final int number = ++requested;
        if (worker == null) worker = new BackgroundWorker("webcam");
        // submitLatest: if the sweep moves on before this one ran, only the newest setting is sent
        worker.submitLatest("exposure", () -> {
            appliedOk = applyExposure(ms, gain);
            applied = number;        // done (taken or refused): the loop tells the sweep
        });
    }

    /**
     * Starts the camera and the AprilTag finder. Call in INIT: the webcam needs about 1-2 s before
     * pictures arrive. Calling it again does nothing.
     */
    @Override
    public void start() {
        if (portal != null) return;
        // The AprilTag finder: our tag library (or this season's), answers in inches and degrees.
        processor = new AprilTagProcessor.Builder()
                .setTagLibrary(library != null ? library : AprilTagGameDatabase.getCurrentGameTagLibrary())
                .setOutputUnits(DistanceUnit.INCH, AngleUnit.DEGREES)
                .build();
        processor.setDecimation(decimation);
        // The portal runs the camera and feeds every picture to the finder, on the SDK's own threads,
        // so our loop never waits for a picture.
        portal = new VisionPortal.Builder()
                .setCamera(webcam)
                .setCameraResolution(new Size(width, height))
                .enableLiveView(liveView)
                .addProcessor(processor)
                .build();
    }

    /** Every loop: picks up the newest picture's tags, if there is a new one. Never waits. */
    @Override
    public void update() {
        // Forgot to call start()? Do it now, so the camera still works.
        if (portal == null) start();
        // Manual exposure can only be set once the camera is streaming, and only needs setting once.
        boolean streaming = portal.getCameraState() == VisionPortal.CameraState.STREAMING;
        if (!controlsApplied && sweep == null && exposureMs >= 0 && streaming) {
            // Camera settings are USB requests that can take a moment: do them in the background, once.
            controlsApplied = true;
            if (worker == null) worker = new BackgroundWorker("webcam");
            // this::applyControls is a "method reference": hand the worker our method to run later
            worker.submit(this::applyControls);
        }
        // getFreshDetections() gives the tags from a picture we have not seen yet, or null if there is none.
        List<AprilTagDetection> fresh = processor.getFreshDetections();
        // Venue lighting: camera settings can only be sent while streaming. A picture counts only once
        // the camera has the setting being tried (applied == requested).
        if (sweep != null && !sweep.isDone()) {
            if (!streaming) {
                sweep.waitingForCamera();          // no switching yet, but START still ends the test
            } else {
                int done = applied;
                if (done == requested && done != reported) {
                    reported = done;
                    // appliedOk was written before applied, so it belongs to this switch
                    if (appliedOk) sweep.switched();
                    else sweep.switchFailed();     // refused: no score, the next setting is tried
                }
                boolean counts = fresh != null && done == requested && appliedOk;
                sweep.update(counts ? fresh.size() : LightingSweep.NO_PICTURE);
            }
        }
        readFresh(fresh);
        // A lighting test still running in the match: its trial pictures must not be aimed with.
        if (sweep != null && !sweep.isDone() && sweep.matchHasStarted()) latest = Collections.emptyList();
    }

    // Turns the newest picture's detections into targets (see update()).
    private void readFresh(List<AprilTagDetection> fresh) {
        if (fresh == null) {          // no new picture since last time: keep the last list, while it is recent
            // Forget targets that are too old, so a camera that stops sending never leaves an old
            // target behind for the robot to aim at.
            latest = dropOld(latest, System.nanoTime(), keepMs);
            return;
        }
        // A new picture: turn each SDK detection into our own TargetObservation.
        List<TargetObservation> out = new ArrayList<>();
        for (AprilTagDetection d : fresh) out.add(convert(d));
        latest = out;
    }

    /** Turns one SDK detection (single tag or cluster) into a TargetObservation. Public for tests. */
    public static TargetObservation convert(AprilTagDetection d) {
        // ftcPose is null for a tag that is not in the tag library (the SDK cannot measure it): use NaN.
        // The SDK's bearing is already positive = LEFT, and its range is measured flat in the camera's own
        // view (sideways and forward only), the same kind of distance as the Limelight's.
        double bearing = d.ftcPose != null ? d.ftcPose.bearing : Double.NaN;
        double range = d.ftcPose != null ? d.ftcPose.range : Double.NaN;
        // "instanceof" asks "is this a single tag?"; then "(AprilTagSingleDetection) d" lets us use it as one.
        if (d instanceof AprilTagSingleDetection) {
            AprilTagSingleDetection s = (AprilTagSingleDetection) d;
            String tagName = s.metadata != null ? s.metadata.name : "Tag " + s.id;
            // frameAcquisitionNanoTime: when the picture was taken, on the System.nanoTime() clock
            return new TargetObservation(s.id, tagName, bearing, range, d.frameAcquisitionNanoTime);
        }
        if (d instanceof AprilTagClusterDetection) {
            // A cluster has a name but no single tag number, so its id is CLUSTER (-1).
            AprilTagClusterDetection c = (AprilTagClusterDetection) d;
            String clusterName = c.metadata != null ? c.metadata.name : "Cluster";
            return new TargetObservation(TargetObservation.CLUSTER, clusterName, bearing, range, d.frameAcquisitionNanoTime);
        }
        // Some other kind of detection a future SDK might add: still report it, so it shows up.
        return new TargetObservation(TargetObservation.CLUSTER, "Unknown", bearing, range, d.frameAcquisitionNanoTime);
    }

    @Override
    public List<TargetObservation> targets() {
        return latest;
    }

    /** The observations not older than {@code keepMs}. Public for tests. Also used by {@link LimelightTags}. */
    public static List<TargetObservation> dropOld(List<TargetObservation> list, long nowNanos, double keepMs) {
        if (list.isEmpty()) return list;
        List<TargetObservation> out = new ArrayList<>();
        for (TargetObservation o : list) if (o.isFresh(nowNanos, keepMs)) out.add(o);
        // Nothing dropped? Give back the same list (no need for a copy).
        return out.size() == list.size() ? list : out;
    }

    /** True while the camera is streaming pictures. */
    @Override
    public boolean isWorking() {
        return portal != null && portal.getCameraState() == VisionPortal.CameraState.STREAMING;
    }

    /** e.g. "STREAMING 28 fps (manual exposure)", or "not started". */
    @Override
    public String status() {
        if (portal == null) return "not started";
        // the camera's state, plus pictures per second (fps) when it is streaming
        String s = portal.getCameraState() + (isWorking() ? String.format(Locale.US, " %.0f fps", portal.getFps()) : "");
        if (!controlsNote.isEmpty()) s += " (" + controlsNote + ")";
        return sweep == null ? s : s + ", lighting: " + sweep.status();
    }

    /** Stops the camera, frees it for the next OpMode, ends the background worker, forgets its targets. */
    @Override
    public void close() {
        if (worker != null) worker.shutdown();
        worker = null;
        if (portal != null) portal.close();
        portal = null;
        // a new start() must send the exposure and gain again to the new camera stream
        controlsApplied = false;
        // the next start() is a new camera session: the lighting test starts over, as in INIT
        if (sweep != null) sweep.newSession();
        applied = -1;
        reported = -1;
        // nothing seen any more: an old target must never be used after the camera stopped
        latest = Collections.emptyList();
    }

    @Override public String name() { return name; }

    // Runs on the background worker, NOT in the loop. One lighting setting: ms <= 0 = the camera's own
    // automatic exposure; otherwise manual, with this gain (-1 = the camera's highest).
    private boolean applyExposure(long ms, int wantGain) {
        try {
            ExposureControl exposure = portal.getCameraControl(ExposureControl.class);
            boolean ok;
            if (exposure == null) {
                ok = false;
            } else if (ms <= 0) {
                // Webcams like the Logitech C920 call their automatic mode "aperture priority".
                ExposureControl.Mode autoMode = exposure.isModeSupported(ExposureControl.Mode.AperturePriority)
                        ? ExposureControl.Mode.AperturePriority : ExposureControl.Mode.Auto;
                ok = exposure.setMode(autoMode);
                // Give back the camera's own gain (a manual setting before may have turned it up).
                GainControl gainControl = portal.getCameraControl(GainControl.class);
                if (ok && savedGain != Integer.MIN_VALUE && gainControl != null) ok = gainControl.setGain(savedGain);
            } else {
                // Keep it inside what this camera can do.
                long lo = exposure.getMinExposure(TimeUnit.MILLISECONDS), hi = exposure.getMaxExposure(TimeUnit.MILLISECONDS);
                long use = hi > lo ? Math.max(lo, Math.min(hi, ms)) : ms;
                GainControl gainControl = portal.getCameraControl(GainControl.class);
                // Before the first manual setting: remember the camera's own gain.
                if (savedGain == Integer.MIN_VALUE && gainControl != null) savedGain = gainControl.getGain();
                ok = exposure.setMode(ExposureControl.Mode.Manual) && exposure.setExposure(use, TimeUnit.MILLISECONDS);
                ok = ok && gainControl != null && gainControl.setGain(wantGain >= 0 ? wantGain : gainControl.getMaxGain());
            }
            controlsNote = ok ? "" : "exposure not supported";
            return ok;
        } catch (RuntimeException e) {
            controlsNote = "exposure failed";
            return false;
        }
    }

    // Runs on the background worker, NOT in the loop: these USB requests can take a moment.
    private void applyControls() {
        // Not every webcam allows these settings; status() shows the note so you know.
        if (applyExposure(exposureMs, gain)) controlsNote = "manual exposure";
    }
}
