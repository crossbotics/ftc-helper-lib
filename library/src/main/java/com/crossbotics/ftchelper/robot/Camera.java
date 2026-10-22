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
 * No suggested buttons. It starts in INIT (a webcam needs a second or two). A camera missing from the
 * robot configuration never stops the OpMode: it warns on the Driver Station and sees nothing.
 *
 * <p>Venue lighting: {@link #autoPipelines} (Limelight) or {@link #autoExposure} (webcam) tries a few
 * camera settings in INIT and keeps the one that sees the most AprilTags; the Driver Station shows the
 * result at INIT. Its numbers are optional Tunables: {@code autoLighting} (true; false = skip the test
 * and use {@code pipeline}, or the webcam's {@code exposureMs} and {@code gain}), {@code lightingSettleMs}
 * (300), {@code lightingPictures} (10), {@code lightingMinPictures} (3), {@code lightingAfterStartMs}
 * (0 = stop at START with the best so far), and for a webcam {@code gain} (-1 = the camera's highest).
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
    // The Tunables lines that skip the test next time, e.g. "pipeline = 1;" (shown at INIT once found).
    private String skipHint = "";
    // For the simulator file: the turret this camera rides (its device name), or null (set by
    // RobotDefinition.simLines from the Turret's follows(camera)).
    String simOnTurret = null;
    // When build() ran, to warn about a camera that is still not working a few seconds later.
    private long builtNanos = 0;

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
        builtNanos = System.nanoTime();
        // tryGet: null when the camera is not in the robot configuration. A camera problem must never
        // stop the OpMode (the robot still drives): a stand-in camera warns and sees nothing.
        Object found = devices.tryGet(limelight ? Limelight3A.class : WebcamName.class, device);
        if (found == null) {
            source = new MissingCamera(device, limelight);
            return;
        }
        if (limelight) {
            LimelightTags ll = new LimelightTags(device, (Limelight3A) found)
                    .pipeline((int) Math.round(t.number("pipeline", 0)));
            if (auto && autoPipelines != null) {
                ll.autoPipelines(autoPipelines);
                sweep = ll.lighting();
            }
            source = ll;
        } else {
            WebcamTags cam = new WebcamTags(device, (WebcamName) found);
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
                    .giveUpAfterStartMs(optional(t, "lightingAfterStartMs", 0));
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
        // A camera that is still not working after 3 s (a webcam needs 1-2 s to start): say so now,
        // before START, so it can be fixed.
        if (!source.isWorking() && (source instanceof MissingCamera || System.nanoTime() - builtNanos > 3_000_000_000L)) {
            t.warn(device + ": " + source.status());
        }
        if (sweep == null) return;
        String line = sweep.status();
        if (sweep.isDone() && !sweep.gaveUp()) skipHint = skipLines(sweep.chosen());
        if (sweep.gaveUp() || sweep.noTagsYet()) t.warn(device + " lighting: " + line);
        else t.line(device + " lighting", line);
        for (String p : sweep.problems()) t.warn(device + " lighting: " + p);
        t.debug("  scores", sweep.scores());
        if (!skipHint.isEmpty()) {
            t.text("  To skip this test at this venue: in Tunables." + name() + ", set these (add any that are not there):");
            for (String l : skipHint.split("\n")) t.text("    " + l);
        }
    }

    // The Tunables lines that skip the test with setting i, one per line, as written in Tunables.java.
    private String skipLines(int i) {
        if (limelight) return "public static double pipeline = " + autoPipelines[i] + ";\npublic static boolean autoLighting = false;";
        long ms = autoExposures[i] <= 0 ? -1 : autoExposures[i];
        return "public static double exposureMs = " + ms + ";" + (ms < 0 ? "   // -1 = the camera's own exposure" : "")
                + "\npublic static boolean autoLighting = false;";
    }

    // START: an unfinished lighting test now stops and keeps the best setting so far.
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

    // True if goal(...) was given in Robot.java (robot.aim() aims with the first such camera).
    boolean hasGoal() {
        return blueGoal != null;
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
        // A guess until measured. Tilted 30 degrees up: goals' tags are often high (BIOBUZZ: up to 50 in
        // on a raised CELL), and a flatter camera sees them only from far away.
        String where = "forward=6, left=0, up=12, yaw=0, pitch=30";
        // On a turret (a turret follows this camera and cameraOnBody() is not used): it turns with the
        // turret in the simulator too (on=turret: measured with the turret pointing straight forward).
        if (simOnTurret != null) where += ", on=" + simOnTurret;
        if (limelight) out.add("limelight." + device + " = name=" + device + ", " + where + "   # TODO measure the mount");
        else out.add("camera." + sectionFor(device) + " = name=" + device + ", " + where + ", hfov=70.4, width=640, height=480   # TODO measure the mount");
    }

    /**
     * The stand-in for a camera that is not in the robot configuration: sees nothing, and says why on
     * the Driver Station (TargetSource's warning), so the OpMode still runs.
     */
    static final class MissingCamera implements TargetSource {
        private final String name;
        private final boolean limelight;

        MissingCamera(String name, boolean limelight) {
            this.name = name;
            this.limelight = limelight;
        }

        @Override public void start() { }
        @Override public void update() { }
        @Override public List<TargetObservation> targets() { return java.util.Collections.emptyList(); }
        @Override public boolean isWorking() { return false; }
        @Override public String status() {
            return "not found: no " + (limelight ? "Limelight" : "webcam") + " called \"" + name + "\" in the robot configuration";
        }
        @Override public void close() { }
        @Override public String name() { return name; }
    }

    @Override
    boolean makesDefaultShape() {
        return false;   // it draws nothing of its own: a shape cannot ride on it without looks(...)
    }
}
