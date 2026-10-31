package com.crossbotics.ftchelper.robot;

import com.qualcomm.hardware.dfrobot.HuskyLens;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.vision.HuskyLensCamera;
import com.crossbotics.ftchelper.vision.LimelightTags;
import com.crossbotics.ftchelper.vision.TagGroup;
import com.crossbotics.ftchelper.vision.TargetObservation;
import com.crossbotics.ftchelper.vision.TargetSource;
import com.crossbotics.ftchelper.vision.WebcamTags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;

/**
 * A camera that finds AprilTags: a Limelight 3A, a webcam or a HuskyLens. Other modules use it: a {@link Turret}
 * that follows the goal, a {@link Launcher} whose speed comes from the distance.
 *
 * <pre>{@code
 * camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));   // Robot.java
 * }</pre>
 *
 * <p>{@link #goal(String, String)} names this season's goal for each alliance (the tag group's
 * name); {@link #goal()} then gives the target for the alliance chosen in autonomous. BIOBUZZ: the goal
 * is always that alliance's HIVE's CELL that reads right side up (the up CELL, the one you can score
 * in), whichever of its two CELLs that is now; a CELL whose tags read upside down is never the goal
 * ({@link TagGroup#goalTarget}). Tunables
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
 * <p>A HuskyLens ({@link #huskyLens}) says the number it was TAUGHT, not the tag's real number: list
 * them with {@link #tag}. It can also look for taught colours or an object ({@link #colors},
 * {@link #objects}, {@link #label}). Tunables: {@code hfovDeg} (its view angle: measure it) and
 * {@code sizeIn} (for a rough distance). No lighting test.
 *
 * Docs: docs/modules/robot.md (section "Camera"), docs/modules/vision.md
 */
public final class Camera extends Module<Camera> {
    private enum Kind { LIMELIGHT, WEBCAM, HUSKYLENS }

    private final Kind kind;
    private final boolean limelight;
    // HuskyLens: what it looks for, and what its taught numbers mean.
    private HuskyLensCamera.Mode huskyMode = HuskyLensCamera.Mode.TAGS;
    private final Map<Integer, Integer> huskyTags = new LinkedHashMap<>();
    private final Map<Integer, String> huskyLabels = new LinkedHashMap<>();
    private boolean hfovMeasured = true, sizeKnown = true;
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

    private Camera(Kind kind, String device) {
        super(device);
        this.kind = kind;
        this.limelight = kind == Kind.LIMELIGHT;
    }

    /** A Limelight 3A. */
    public static Camera limelight(String name) {
        return new Camera(Kind.LIMELIGHT, name);
    }

    /** A webcam (USB). */
    public static Camera webcam(String name) {
        return new Camera(Kind.WEBCAM, name);
    }

    /**
     * A DFRobot HuskyLens on an I2C port (configure it as "HuskyLens"). It looks for AprilTags unless
     * you say {@link #colors()} or {@link #objects()}.
     *
     * <pre>{@code
     * camera = add(Camera.huskyLens("huskylens").tag(1, 30).tag(2, 31).goal("BLUE SCORING", "RED SCORING"));
     * }</pre>
     */
    public static Camera huskyLens(String name) {
        return new Camera(Kind.HUSKYLENS, name);
    }

    // The HuskyLens lines need a HuskyLens.
    private void needsHuskyLens(String method) {
        if (kind != Kind.HUSKYLENS) throw new IllegalStateException("Robot.java: " + device + ": " + method + " is for a HuskyLens");
    }

    /**
     * HuskyLens: which real AprilTag a taught number is. Teach the tags with "Learn Multiple" on: the
     * first is ID1, the next ID2. Needed for {@code target(tagNumber)} and for {@link #goal(String, String)}.
     *
     * @param taughtNumber the number on the camera's screen (ID1 = 1)
     * @param tagNumber    the AprilTag's real number
     */
    public Camera tag(int taughtNumber, int tagNumber) {
        needsHuskyLens("tag(...)");
        huskyTags.put(taughtNumber, tagNumber);
        return this;
    }

    /**
     * HuskyLens: a name for a taught number, for {@code target(name)}.
     *
     * @param taughtNumber the number on the camera's screen
     * @param name         the name, e.g. "POLLEN"
     */
    public Camera label(int taughtNumber, String name) {
        needsHuskyLens("label(...)");
        huskyLabels.put(taughtNumber, name);
        return this;
    }

    /** HuskyLens: look for the colours you taught it, not AprilTags. */
    public Camera colors() {
        needsHuskyLens("colors()");
        huskyMode = HuskyLensCamera.Mode.COLORS;
        return this;
    }

    /** HuskyLens: look for the ONE object you taught it, not AprilTags. */
    public Camera objects() {
        needsHuskyLens("objects()");
        huskyMode = HuskyLensCamera.Mode.OBJECTS;
        return this;
    }

    /**
     * The goal's tag group for each alliance, e.g. "BLUE SCORING" and "RED SCORING" in BIOBUZZ. For
     * BIOBUZZ each name stands for that alliance's HIVE: the aim goes to its CELL that reads right side
     * up now (the up CELL), "RED SCORING" or "RED AUDIENCE", and never to a CELL that reads upside down.
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
        if (kind == Kind.HUSKYLENS) throw new IllegalStateException("Robot.java: " + device + " is a HuskyLens: it has no lighting test");
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
        if (kind == Kind.HUSKYLENS) throw new IllegalStateException("Robot.java: " + device + " is a HuskyLens: it has no lighting test");
        if (limelight) throw new IllegalStateException("Robot.java: " + device + " is a Limelight: use autoPipelines(...) (autoExposure is for a webcam)");
        autoExposures = exposuresMs == null || exposuresMs.length == 0 ? null : exposuresMs.clone();
        return this;
    }

    // A camera missing from the configuration warns and sees nothing; INIT goes on.
    @Override
    boolean canDoWithout(String deviceName) {
        return true;
    }

    /** True for a Limelight. */
    public boolean isLimelight() {
        return limelight;
    }

    /** True for a webcam. */
    public boolean isWebcam() {
        return kind == Kind.WEBCAM;
    }

    /** True for a HuskyLens. */
    public boolean isHuskyLens() {
        return kind == Kind.HUSKYLENS;
    }

    // True when the webcam shows its picture (live view, PRACTICE profile only).
    private boolean liveView = false;

    /** True when the webcam's live view is on (PRACTICE profile, {@code Tunables liveView} not false). */
    public boolean liveViewOn() {
        return liveView;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        // Optional numbers: read only when written, so a team that does not use them gets no INIT note.
        boolean auto = !t.has("autoLighting") || t.flag("autoLighting", true);
        builtNanos = System.nanoTime();
        // tryGet: null when the camera is not in the robot configuration. A camera problem must never
        // stop the OpMode (the robot still drives): a stand-in camera warns and sees nothing.
        Object found = devices.tryGet(limelight ? Limelight3A.class : kind == Kind.HUSKYLENS ? HuskyLens.class : WebcamName.class, device);
        if (found == null) {
            source = new MissingCamera(device, kindWords());
            return;
        }
        if (kind == Kind.HUSKYLENS) {
            HuskyLensCamera husky = new HuskyLensCamera(device, (HuskyLens) found).mode(huskyMode);
            for (Map.Entry<Integer, Integer> e : huskyTags.entrySet()) husky.tag(e.getKey(), e.getValue());
            for (Map.Entry<Integer, String> e : huskyLabels.entrySet()) husky.label(e.getKey(), e.getValue());
            hfovMeasured = t.has("hfovDeg");
            sizeKnown = t.has("sizeIn");
            if (hfovMeasured) husky.hfovDeg(t.number("hfovDeg", HuskyLensCamera.GUESSED_HFOV_DEG));
            if (sizeKnown) husky.sizeIn(t.number("sizeIn", 0));
            source = husky;
        } else if (limelight) {
            LimelightTags ll = new LimelightTags(device, (Limelight3A) found)
                    .pipeline((int) Math.round(t.number("pipeline", 0)));
            if (auto && autoPipelines != null) {
                ll.autoPipelines(autoPipelines);
                sweep = ll.lighting();
            }
            source = ll;
        } else {
            WebcamTags cam = new WebcamTags(device, (WebcamName) found);
            // Live view: the picture on the robot's screen and the Driver Station's Camera Stream, for
            // checking the brightness. On in the PRACTICE profile (Tunables liveView, true), never at an
            // event (COMPETITION): drawing the picture costs speed.
            liveView = com.crossbotics.ftchelper.core.Profile.current().isPractice()
                    && (!t.has("liveView") || t.flag("liveView", true));
            cam.liveView(liveView);
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

    @Override
    List<String> notes() {
        List<String> out = super.notes();
        if (kind != Kind.HUSKYLENS || source instanceof MissingCamera) return out;
        if (!hfovMeasured) out.add("Tunables." + name() + ": hfovDeg missing: its turn angles use a guess of "
                + fmt("%.0f", HuskyLensCamera.GUESSED_HFOV_DEG) + " degrees (measure it: docs/modules/vision.md#huskylenscamera)");
        if (blueGoal != null && !sizeKnown) out.add("Tunables." + name() + ": sizeIn missing: no distance to the goal, so a shot table cannot use this camera");
        if (blueGoal != null && huskyTags.isEmpty()) out.add(device + ": goal(...) needs tag(taughtNumber, tagNumber) lines: a HuskyLens says only taught numbers");
        return out;
    }

    // The words for this kind of camera, for messages.
    private String kindWords() {
        return limelight ? "Limelight" : kind == Kind.HUSKYLENS ? "HuskyLens" : "webcam";
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
        if (liveView) t.line(name() + " live view", "on (PRACTICE): Driver Station menu > Camera Stream, in INIT");
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
     * seen now (or no goal was named). BIOBUZZ: that alliance's HIVE's CELL that reads right side up;
     * null when only an upside-down CELL is in view. Always check for null.
     */
    public TargetObservation goal() {
        if (source == null || blueGoal == null) return null;
        return TagGroup.goalTarget(source, goalName());
    }

    // Why goal() is null while the camera sees part of the goal ("RED SCORING is upside down (a down
    // CELL)"); "" otherwise.
    String whyNoGoal() {
        return source == null || blueGoal == null ? "" : TagGroup.whyNoGoal(source, goalName());
    }

    // The goal's name for this match's alliance.
    private String goalName() {
        Alliance a = robot == null ? Alliance.BLUE : robot.alliance();
        return a.pick(blueGoal, redGoal);
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

    /** The camera inside (a LimelightTags, WebcamTags or HuskyLensCamera), for anything this module does not offer. */
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
            String why = g == null ? whyNoGoal() : "";
            if (g != null) t.line("Goal", fmt("%s, %.0f in, %.1f deg", g.name, g.distanceIn, g.bearingDeg));
            else t.line("Goal", why.isEmpty() ? "not seen" : "not seen (" + why + ")");
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
        if (kind == Kind.HUSKYLENS) {
            StringBuilder taught = new StringBuilder();
            // The file lists what was taught, in the order of the taught numbers (ID1 first).
            int last = 0;
            for (int n : huskyTags.keySet()) last = Math.max(last, n);
            for (int n : huskyLabels.keySet()) last = Math.max(last, n);
            for (int n = 1; n <= last; n++) {
                if (n > 1) taught.append('|');
                if (huskyMode == HuskyLensCamera.Mode.TAGS && huskyTags.containsKey(n)) taught.append(huskyTags.get(n));
                else if (huskyLabels.containsKey(n)) taught.append(huskyLabels.get(n));
                else taught.append('-');
            }
            out.add("huskylens." + device + " = name=" + device + ", " + where + (t != null && t.has("hfovDeg") ? ", hfov=" + fmt("%.1f", t.number("hfovDeg", HuskyLensCamera.GUESSED_HFOV_DEG)) : "")
                    + (last > 0 ? ", taught=" + taught : "") + "   # TODO measure the mount and hfov");
        } else if (limelight) out.add("limelight." + device + " = name=" + device + ", " + where + "   # TODO measure the mount");
        else out.add("camera." + sectionFor(device) + " = name=" + device + ", " + where + ", hfov=70.4, width=640, height=480   # TODO measure the mount");
    }

    /**
     * The stand-in for a camera that is not in the robot configuration: sees nothing, and says why on
     * the Driver Station (TargetSource's warning), so the OpMode still runs.
     */
    static final class MissingCamera implements TargetSource {
        private final String name;
        private final String kindWords;

        MissingCamera(String name, String kindWords) {
            this.name = name;
            this.kindWords = kindWords;
        }

        @Override public void start() { }
        @Override public void update() { }
        @Override public List<TargetObservation> targets() { return java.util.Collections.emptyList(); }
        @Override public boolean isWorking() { return false; }
        @Override public String status() {
            return "not found: no " + kindWords + " called \"" + name + "\" in the robot configuration";
        }
        @Override public void close() { }
        @Override public String name() { return name; }
    }

    @Override
    boolean makesDefaultShape() {
        return false;   // it draws nothing of its own: a shape cannot ride on it without looks(...)
    }
}
