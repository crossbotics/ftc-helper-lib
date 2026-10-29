package com.crossbotics.ftchelper.vision;

import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.util.BackgroundWorker;
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
 * TargetObservation tag = camera.target(31);                // one tag, by its number
 * TargetObservation goal = camera.target("RED SCORING");    // a tag group (see below)
 * }</pre>
 *
 * <p>Tag groups: a Limelight reports each tag on its own. This class also reports each group of
 * tags in a row ({@link TagGroup}; BIOBUZZ: the 4 tags under each CELL) as ONE target, named like the
 * webcam's (the FTC SDK's cluster names), aimed at the middle of the row. It needs at least 2 of the
 * group's tags in view and their 3D poses. So the same code aims with either camera. The webcam's
 * cluster target is the SDK's own aiming point, which is NOT the tag row: the SDK puts it 7.2 in toward
 * the tags' printed top and 5.6 in behind them. For BIOBUZZ (the tags' printed bottom faces the field
 * centre, manual 9.9) that is about 9 in farther from a robot in front of the CELL and 1 in higher
 * than the row (not the CELL opening). So the two cameras give about the same bearing when you face the
 * group, but different distances: make the shot table with the camera the robot uses.
 *
 * <p>Right side up or not: a group's {@link TargetObservation#rollDeg} comes from the ORDER of its tags in
 * the picture (their numbers rise to the right when the tags are right side up, as printed). That needs
 * only where the tags are, not which slot of the Limelight's pose array holds the roll. A single tag's
 * rollDeg is the roll as the FTC SDK reads it from the pose.
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
    private List<TagGroup> groups = TagGroup.currentSeason();
    // pipelineSwitch() and updateRobotOrientation() are network requests that WAIT for an answer.
    // They run on this worker so the robot's loop never waits.
    private BackgroundWorker worker = null;
    // Venue lighting (autoPipelines): the pipelines to try, and the helper that tries them. null = off.
    private int[] autoList = null;
    private LightingSweep sweep = null;
    // The time stamp of the last picture given to the sweep (its own, so update() stays as it was).
    private double sweepTimestamp = Double.NaN;

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

    /**
     * Venue lighting: tries these pipelines one after the other in INIT, counts the pictures that see
     * AprilTags with each, and keeps the best one. Set up each pipeline on the Limelight's web page
     * for a kind of lighting (e.g. 0 = normal, 1 = bright, 2 = dim: exposure and black level), then
     * list them here with your usual one FIRST (it wins ties, and it is kept if no tag is seen at all).
     * Have a tag in view at INIT: at START the test stops and keeps the best pipeline so far.
     * A listed pipeline that sends no pictures (not set up) is skipped. Replaces {@link #pipeline}.
     * Never waits: each switch goes to the background helper. {@link #lighting()} has the result.
     *
     * <pre>{@code
     * LimelightTags camera = LimelightTags.create(hardwareMap, "limelight").autoPipelines(0, 1, 2);
     * }</pre>
     *
     * @param pipelines the pipeline numbers to try, usual one first (0 to 9)
     */
    public LimelightTags autoPipelines(int... pipelines) {
        if (pipelines == null || pipelines.length == 0) {
            autoList = null;
            sweep = null;
            return this;
        }
        autoList = pipelines.clone();
        List<String> labels = new ArrayList<>();
        for (int p : autoList) labels.add("pipeline " + p);
        // i -> ...: the code the sweep runs to switch to setting number i (a pipeline switch, in the background)
        sweep = new LightingSweep(labels, i -> switchTo(autoList[i]), System::nanoTime);
        pipeline = autoList[0];
        return this;
    }

    /** The venue-lighting helper (its numbers, status and result), or null without {@link #autoPipelines}. */
    public LightingSweep lighting() {
        return sweep;
    }

    /**
     * Tell the camera the match has started, so an unfinished {@link #autoPipelines} test stops and
     * keeps the best pipeline so far (the five-files Camera module does this for you). Safe to call
     * without autoPipelines.
     */
    public void matchStarted() {
        if (sweep != null) sweep.matchStarted();
    }

    // Switches the pipeline in the background (a network request that waits for an answer).
    private void switchTo(int index) {
        pipeline = index;
        worker().submitLatest("pipeline", () -> limelight.pipelineSwitch(index));   // never waits
    }

    /**
     * The tag groups reported as one target each (default: this season's, {@link TagGroup#currentSeason()}).
     * An empty list turns groups off (only single tags are reported).
     */
    public LimelightTags groups(List<TagGroup> tagGroups) {
        groups = tagGroups == null ? Collections.<TagGroup>emptyList() : new ArrayList<>(tagGroups);
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
        // With autoPipelines the lighting test switches pipelines itself (in its first update).
        if (sweep == null) worker().submitLatest("pipeline", () -> limelight.pipelineSwitch(first));   // a network request: in the background
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
        if (sweep != null && !sweep.isDone()) feedSweep(result);
        read(result);
        // A lighting test still running in the match: its trial pictures must not be aimed with.
        if (sweep != null && !sweep.isDone() && sweep.matchHasStarted()) latest = Collections.emptyList();
    }

    // Turns the result into targets (see update()).
    private void read(LLResult result) {
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
        // Each tag's place in camera space (inches), for the tag groups.
        java.util.Map<Integer, double[]> places = new java.util.HashMap<>();
        for (LLResultTypes.FiducialResult f : result.getFiducialResults()) {
            double[] p = cameraSpaceInches(f.getTargetPoseCameraSpace());
            if (p != null) places.put(f.getFiducialId(), p);
            // The minus sign flips the Limelight's tx (positive = RIGHT) into our bearing (positive = LEFT),
            // so the Limelight and the webcam agree. See docs/conventions.md.
            out.add(new TargetObservation(f.getFiducialId(), "Tag " + f.getFiducialId(),
                    -f.getTargetXDegrees(), distanceInches(f.getTargetPoseCameraSpace()), pictureTime,
                    rollDegrees(f.getTargetPoseCameraSpace())));
        }
        for (TagGroup g : groups) {
            TargetObservation o = groupTarget(g, places, pictureTime);
            if (o != null) out.add(o);
        }
        latest = out;
        // The field position (alpha camera fix, CameraPoseFix): MegaTag2 for the fix, MegaTag1 (with its
        // own yaw) for the Camera Fix Check.
        double[] mt2 = fieldInches(result.getBotpose_MT2()), mt1 = fieldInches(result.getBotpose());
        sighting = new FieldSighting(mt2[0], mt2[1], mt1[0], mt1[1], mt1[2], result.getBotposeTagCount(), pictureTime, ++sightings);
    }

    // Venue lighting: hands the sweep the number of tags in a NEW picture taken with the pipeline being
    // tried (a picture from the pipeline before does not count), or NO_PICTURE.
    private void feedSweep(LLResult result) {
        int tags = LightingSweep.NO_PICTURE;
        if (result != null && result.getTimestamp() != sweepTimestamp) {
            sweepTimestamp = result.getTimestamp();
            int want = sweep.current() < 0 ? -1 : autoList[sweep.current()];
            if (result.getPipelineIndex() == want) {
                sweep.switched();          // the first picture from this pipeline: the switch is done
                // isValid() is false for a picture with nothing in it: that picture counts, with 0 tags.
                tags = result.isValid() ? result.getFiducialResults().size() : 0;
            } else {
                tags = LightingSweep.OTHER_SETTING;   // still the pipeline before (or the wanted one is missing)
            }
        }
        sweep.update(tags);
    }

    // A tag's place in camera space in inches (x right, y down, z out of the lens), or null if no pose.
    private static double[] cameraSpaceInches(Pose3D pose) {
        if (pose == null || pose.getPosition() == null) return null;
        Position p = pose.getPosition().toUnit(DistanceUnit.INCH);
        if (p.x == 0 && p.y == 0 && p.z == 0) return null;     // the pipeline filled in no real pose
        return new double[] {p.x, p.y, p.z};
    }

    /**
     * One target for a tag group: the middle of its row, from the tags in view. The row's direction comes
     * from the two seen tags farthest apart; each seen tag then gives the middle by stepping back along
     * the row by its place in the group; the average is the aiming point. Null with fewer than 2 tags.
     * Public for tests.
     *
     * @param places each seen tag's place in camera space, inches (x right, y down, z out of the lens)
     */
    public static TargetObservation groupTarget(TagGroup group, java.util.Map<Integer, double[]> places, long timeNanos) {
        int lo = -1, hi = -1;
        int count = 0;
        for (int id : group.ids()) {
            if (!places.containsKey(id)) continue;
            count++;
            if (lo < 0 || group.alongRowIn(id) < group.alongRowIn(lo)) lo = id;
            if (hi < 0 || group.alongRowIn(id) > group.alongRowIn(hi)) hi = id;
        }
        if (count < 2) return null;
        double[] a = places.get(lo), b = places.get(hi);
        double span = group.alongRowIn(hi) - group.alongRowIn(lo);
        // The row's direction: one inch along the row, in camera space.
        double[] dir = {(b[0] - a[0]) / span, (b[1] - a[1]) / span, (b[2] - a[2]) / span};
        double[] mid = {0, 0, 0};
        for (int id : group.ids()) {
            double[] p = places.get(id);
            if (p == null) continue;
            double along = group.alongRowIn(id);
            for (int k = 0; k < 3; k++) mid[k] += (p[k] - dir[k] * along) / count;
        }
        // Limelight camera space: x right, z forward. The bearing is positive to the LEFT, so the minus sign.
        double bearing = -Math.toDegrees(Math.atan2(mid[0], mid[2]));
        // The row's turn in the picture: the tag numbers rise to the RIGHT when the tags are right side up
        // (as printed). Rising to the left (x negative) = upside down. y is down, so + = turned clockwise.
        double roll = Math.toDegrees(Math.atan2(dir[1], dir[0]));
        return new TargetObservation(TargetObservation.CLUSTER, group.name, bearing, Math.hypot(mid[0], mid[2]), timeNanos, roll);
    }

    // One tag's turn as the FTC SDK reads it from the Limelight's pose (NaN without a pose). Not used to
    // choose a goal: the tag groups use the order of the tags in the row instead (see groupTarget).
    private static double rollDegrees(Pose3D pose) {
        if (pose == null || pose.getOrientation() == null || cameraSpaceInches(pose) == null) return Double.NaN;
        return pose.getOrientation().getRoll(org.firstinspires.ftc.robotcore.external.navigation.AngleUnit.DEGREES);
    }

    /**
     * <b>ALPHA</b> (the camera fix, {@code CameraPoseFix}): the robot's field position the Limelight worked
     * out from the tags in one picture, in the Limelight's field frame (inches, degrees). NaN when it had
     * none. MegaTag2 needs the robot's heading ({@link #setRobotHeading}); MegaTag1 gives its own yaw.
     */
    public static final class FieldSighting {
        public final double mt2XIn, mt2YIn, mt1XIn, mt1YIn, mt1YawDeg;
        public final int tags;
        /** When the picture was taken (System.nanoTime clock). */
        public final long timeNanos;
        /** Counts up with each new picture: a new number = a new picture. */
        public final long number;

        FieldSighting(double mt2X, double mt2Y, double mt1X, double mt1Y, double mt1Yaw, int tags, long time, long number) {
            mt2XIn = mt2X;
            mt2YIn = mt2Y;
            mt1XIn = mt1X;
            mt1YIn = mt1Y;
            mt1YawDeg = mt1Yaw;
            this.tags = tags;
            timeNanos = time;
            this.number = number;
        }
    }

    private FieldSighting sighting = null;
    private long sightings = 0;

    /** ALPHA: the field position from the newest picture with tags, or null before the first. */
    public FieldSighting fieldSighting() {
        return sighting;
    }

    // {x, y, yaw} in inches and degrees, NaN for no pose (null, or all zero: the Limelight's "no solve").
    private static double[] fieldInches(Pose3D p) {
        double[] none = {Double.NaN, Double.NaN, Double.NaN};
        if (p == null || p.getPosition() == null) return none;
        org.firstinspires.ftc.robotcore.external.navigation.Position at =
                p.getPosition().toUnit(org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit.INCH);
        if (at.x == 0 && at.y == 0 && at.z == 0) return none;
        double yaw = p.getOrientation() == null ? Double.NaN
                : p.getOrientation().getYaw(org.firstinspires.ftc.robotcore.external.navigation.AngleUnit.DEGREES);
        return new double[] {at.x, at.y, yaw};
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
     * Distance to the target in inches, flat in the camera's own view (its up/down left out; the same as
     * the floor distance only for a level camera), NaN if there is no pose. Same meaning as the webcam's.
     * See {@link TargetObservation#distanceIn}.
     * Limelight camera space: x right, y down, z forward. Public for tests.
     */
    public static double distanceInches(Pose3D pose) {
        if (pose == null || pose.getPosition() == null) return Double.NaN;
        Position p = pose.getPosition().toUnit(DistanceUnit.INCH);
        // Only sideways (x) and forward (z): leaving out the camera's up/down (y). For a level camera that
        // is the distance along the floor; for a tilted one it is a little more.
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
        String s = String.format(Locale.US, "pipeline %d, %d tags", pipeline, latest.size());
        return sweep == null ? s : s + ", lighting: " + sweep.status();
    }

    /** Stops the Limelight, forgets its targets and ends the background worker. */
    @Override
    public void close() {
        if (started) limelight.stop();
        started = false;
        // nothing seen any more: an old target must never be used after the camera stopped
        latest = Collections.emptyList();
        // forget the last frame's time, so the first frame after a restart is not taken for an old one
        lastTimestamp = Double.NaN;
        sweepTimestamp = Double.NaN;
        // a new start() is a new camera session: the lighting test starts over, as in INIT
        if (sweep != null) sweep.newSession();
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
