package com.crossbotics.ftchelper.robot;

import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.vision.LimelightTags;
import com.crossbotics.ftchelper.vision.TargetObservation;
import com.crossbotics.ftchelper.vision.TargetSource;
import com.crossbotics.ftchelper.vision.WebcamTags;
import java.util.List;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;

/**
 * A camera that finds AprilTags: a Limelight 3A or a webcam. Other modules use it: a {@link Turret}
 * that follows the goal, a {@link Launcher} whose speed comes from the distance.
 *
 * <pre>{@code
 * camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));   // Robot.java
 * }</pre>
 *
 * <p>{@link #goal(String, String)} names this season's goal for each alliance (the tag group's
 * name); {@link #goal()} then gives the target for the alliance chosen in autonomous. Tunables
 * ({@code Tunables.Limelight} for device "limelight"): {@code pipeline} (Limelight only, default 0).
 * No suggested buttons. It starts in INIT (a webcam needs a second or two).
 *
 * <p>Venue lighting: {@link #autoPipelines} (Limelight) or {@link #autoExposure} (webcam) tries a few
 * camera settings in INIT and keeps the one that sees the most AprilTags; the Driver Station shows the
 * result at INIT. Its numbers are optional Tunables: {@code autoLighting} (true; false = skip the test
 * and use {@code pipeline}, or the webcam's {@code exposureMs} and {@code gain}), {@code lightingSettleMs}
 * (300), {@code lightingPictures} (10), {@code lightingMinPictures} (3), {@code lightingAfterStartMs}
 * (3000), and for a webcam {@code gain} (-1 = the camera's highest).
 *
 * Docs: docs/modules/robot.md (section "Camera"), docs/modules/vision.md
 */
public final class Camera extends Module<Camera> {
    private final boolean limelight;
    private String blueGoal = null, redGoal = null;
    // Venue lighting: the Limelight pipelines or webcam exposures to try (null = no test).
    private int[] autoPipelines = null;
    private long[] autoExposures = null;
    TargetSource source;
    // The lighting test as running (null when it is off).
    private com.crossbotics.ftchelper.vision.LightingSweep sweep = null;
    // The Tunables line that skips the test next time, e.g. "pipeline = 1" (shown at INIT once found).
    private String skipHint = "";

    private Camera(boolean limelight, String device) {
        super(device);
        this.limelight = limelight;
    }

    /** A Limelight 3A. */
    public static Camera limelight(String name) {
        return new Camera(true, name);
    }

    /** A webcam (USB). */
    public static Camera webcam(String name) {
        return new Camera(false, name);
    }

    /**
     * The goal's tag group for each alliance, e.g. "BLUE SCORING" and "RED SCORING" in BIOBUZZ.
     *
     * @param blueGroup the group the BLUE alliance aims at
     * @param redGroup  the group the RED alliance aims at
     */
    public Camera goal(String blueGroup, String redGroup) {
        blueGoal = blueGroup;
        redGoal = redGroup;
        return this;
    }

    /**
     * Venue lighting for a Limelight: tries these pipelines in INIT and keeps the one that sees the
     * most AprilTags (set each one up on the Limelight's web page for a kind of light). Your usual
     * pipeline FIRST: it wins ties and is kept if no tag is seen. See docs/modules/vision.md.
     *
     * <pre>{@code
     * camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
     * }</pre>
     *
     * @param pipelines the pipeline numbers to try, usual one first
     */
    public Camera autoPipelines(int... pipelines) {
        if (!limelight) throw new IllegalStateException("Robot.java: " + device + " is a webcam: use autoExposure() (autoPipelines is for a Limelight)");
        autoPipelines = pipelines == null || pipelines.length == 0 ? null : pipelines.clone();
        return this;
    }

    /**
     * Venue lighting for a webcam: tries the camera's own automatic exposure, then 3, 6 and 12 ms, in
     * INIT and keeps the one that sees the most AprilTags. See {@link #autoExposure(long...)}.
     */
    public Camera autoExposure() {
        return autoExposure(WebcamTags.DEFAULT_EXPOSURES_MS);
    }

    /**
     * Venue lighting for a webcam: tries these exposures (milliseconds; 0 = the camera's own automatic
     * exposure) in INIT and keeps the one that sees the most AprilTags. Usual one FIRST.
     *
     * @param exposuresMs the exposures to try, usual one first
     */
    public Camera autoExposure(long... exposuresMs) {
        if (limelight) throw new IllegalStateException("Robot.java: " + device + " is a Limelight: use autoPipelines(...) (autoExposure is for a webcam)");
        autoExposures = exposuresMs == null || exposuresMs.length == 0 ? null : exposuresMs.clone();
        return this;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        // Optional numbers: read only when written, so a team that does not use them gets no INIT note.
        boolean auto = !t.has("autoLighting") || t.flag("autoLighting", true);
        if (limelight) {
            LimelightTags ll = new LimelightTags(device, devices.get(Limelight3A.class, device))
                    .pipeline((int) Math.round(t.number("pipeline", 0)));
            if (auto && autoPipelines != null) {
                ll.autoPipelines(autoPipelines);
                sweep = ll.lighting();
            }
            source = ll;
        } else {
            WebcamTags cam = new WebcamTags(device, devices.get(WebcamName.class, device));
            int gain = (int) Math.round(optional(t, "gain", -1));
            long exposureMs = Math.round(optional(t, "exposureMs", -1));
            // A manual exposure from Tunables (e.g. copied from the lighting test's result)
            if (exposureMs > 0) cam.exposure(exposureMs, gain);
            else if (gain >= 0) cam.gain(gain);
            if (auto && autoExposures != null) {
                cam.autoExposure(autoExposures);
                sweep = cam.lighting();
            }
            source = cam;
        }
        if (sweep != null) {
            sweep.settleMs(optional(t, "lightingSettleMs", 300))
                    .pictures((int) Math.round(optional(t, "lightingPictures", 10)))
                    .minPictures((int) Math.round(optional(t, "lightingMinPictures", 3)))
                    .giveUpAfterStartMs(optional(t, "lightingAfterStartMs", 3000));
        }
        source.start();
    }

    // A number that is only read when Tunables has it (no "missing" note otherwise).
    private static double optional(TunableSection t, String field, double fallback) {
        return t.has(field) ? t.number(field, fallback) : fallback;
    }

    /** The venue-lighting test (status and result), or null when it is off. */
    public com.crossbotics.ftchelper.vision.LightingSweep lighting() {
        return sweep;
    }

    // The INIT loop: keep the camera reading (so the lighting test runs), and show the result.
    @Override
    void initLoop(RobotTelemetry t) {
        if (source == null) return;
        source.update();
        if (sweep == null) return;
        String line = sweep.status();
        if (sweep.isDone() && !sweep.gaveUp()) {
            int i = sweep.chosen();
            skipHint = limelight ? "pipeline = " + autoPipelines[i]
                    : (autoExposures[i] <= 0 ? "exposureMs = -1" : "exposureMs = " + autoExposures[i]);
        }
        if (sweep.gaveUp() || (sweep.current() >= 0 && line.contains("no tags yet"))) t.warn(device + " lighting: " + line);
        else t.line(device + " lighting", line);
        t.debug("  scores", sweep.scores());
        if (!skipHint.isEmpty()) t.text("  To skip this test at this venue: Tunables." + name() + "." + skipHint + ", autoLighting = false");
    }

    // START: an unfinished lighting test now gives up after lightingAfterStartMs (and keeps the first setting).
    @Override
    void start() {
        if (source instanceof LimelightTags) ((LimelightTags) source).matchStarted();
        if (source instanceof WebcamTags) ((WebcamTags) source).matchStarted();
    }

    // ----------------------------- reading -----------------------------

    /**
     * The goal for this match's alliance (see {@link #goal(String, String)}), or null if it is not
     * seen now (or no goal was named). Always check for null.
     */
    public TargetObservation goal() {
        if (source == null || blueGoal == null) return null;
        Alliance a = robot == null ? Alliance.BLUE : robot.alliance();
        return source.target(a.pick(blueGoal, redGoal));
    }

    /** The target with this name (a tag group or a tag's name), or null if not seen now. */
    public TargetObservation target(String name) {
        return source == null ? null : source.target(name);
    }

    /** The tag with this number, or null if not seen now. */
    public TargetObservation target(int id) {
        return source == null ? null : source.target(id);
    }

    /** The camera inside (a LimelightTags or WebcamTags), for anything this module does not offer. */
    public TargetSource source() {
        return source;
    }

    @Override
    public void update() {
        source.update();
    }

    @Override
    public void stop() {
        if (source != null) source.stop();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (source == null) return;
        source.addTelemetry(t);
        if (blueGoal != null) {
            TargetObservation g = goal();
            t.line("Goal", g == null ? "not seen" : fmt("%.0f in, %.1f deg", g.distanceIn, g.bearingDeg));
        }
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        String where = "forward=6, left=0, up=12, yaw=0, pitch=20";
        if (limelight) out.add("limelight." + device + " = name=" + device + ", " + where + "   # TODO measure the mount");
        else out.add("camera." + sectionFor(device) + " = name=" + device + ", " + where + ", hfov=70.4, width=640, height=480   # TODO measure the mount");
    }

    @Override
    boolean makesDefaultShape() {
        return false;   // it draws nothing of its own: a shape cannot ride on it without looks(...)
    }
}
