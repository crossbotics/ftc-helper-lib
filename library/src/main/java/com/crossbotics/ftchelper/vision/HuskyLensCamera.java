package com.crossbotics.ftchelper.vision;

import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.util.BackgroundWorker;
import com.qualcomm.hardware.dfrobot.HuskyLens;
import com.qualcomm.robotcore.hardware.I2cDeviceSynch;
import com.qualcomm.robotcore.hardware.HardwareMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A DFRobot HuskyLens: a small camera with its own screen, plugged into an I2C sensor port of a hub. It
 * finds AprilTags, colours or objects by itself. You teach it with its buttons, on the camera.
 *
 * <pre>{@code
 * HuskyLensCamera camera = HuskyLensCamera.create(hardwareMap, "huskylens")
 *         .tag(1, 30).tag(2, 31);        // taught number 1 is AprilTag 30, taught number 2 is AprilTag 31
 * camera.start();                        // in INIT
 * // every loop:
 * camera.update();
 * TargetObservation tag = camera.target(30);
 * }</pre>
 *
 * <p>Three things are different from a webcam or a Limelight:
 * <ul>
 * <li><b>It does not say the AprilTag's real number.</b> It says the number it was TAUGHT: the first tag
 * you teach is 1, the next is 2. An untaught tag is 0. So tell this class what each taught number means
 * with {@link #tag}.</li>
 * <li><b>It gives only a box in the picture</b> (centre, width, height, in pixels of a 320 x 240 picture). The
 * bearing comes from the box's place and {@link #hfovDeg} (the camera's view angle, which you measure).
 * The distance is a rough estimate from the box's size and {@link #sizeIn} (NaN without it).</li>
 * <li><b>A single tag has no "right side up"</b> ({@link TargetObservation#rollDeg} is NaN). A tag group
 * ({@link TagGroup}) has one, from the order of its taught tags in the picture, as with the Limelight.
 * It needs 2 tags of the group in view.</li>
 * </ul>
 *
 * <p>The SDK's driver gives at most 6 boxes per reading. Reading a HuskyLens is a slow I2C request, so it
 * runs on a helper thread and {@link #update()} never waits (docs/guides/non-blocking.md).
 *
 * Docs: docs/modules/vision.md#huskylenscamera
 */
public class HuskyLensCamera implements TargetSource {
    /** What the camera looks for. */
    public enum Mode {
        /** AprilTags (TAG_RECOGNITION). Teach them with "Learn Multiple" on. The default. */
        TAGS(HuskyLens.Algorithm.TAG_RECOGNITION),
        /** Colours you teach (COLOR_RECOGNITION). Changes in the light can change what it finds. */
        COLORS(HuskyLens.Algorithm.COLOR_RECOGNITION),
        /** ONE object you teach (OBJECT_TRACKING): the HuskyLens can track only one. */
        OBJECTS(HuskyLens.Algorithm.OBJECT_TRACKING);

        final HuskyLens.Algorithm algorithm;

        Mode(HuskyLens.Algorithm algorithm) {
            this.algorithm = algorithm;
        }
    }

    /** The picture's size in pixels: the boxes are in the camera's 320 x 240 screen (FTC docs). */
    public static final int PICTURE_WIDTH = 320, PICTURE_HEIGHT = 240;
    /** The view angle used until you measure yours (a guess: we found no number from the seller). */
    public static final double GUESSED_HFOV_DEG = 60;
    // No finished reading for this long: it is not working.
    private static final long NO_ANSWER_NS = 1_000_000_000L;
    // While it sees nothing: how often to ask "are you there?".
    private static final long KNOCK_EVERY_NS = 1_000_000_000L;
    // The camera's "are you there?" request: the same 6 bytes the SDK's knock() sends.
    private static final byte[] KNOCK = {0x55, (byte) 0xAA, 0x11, 0x00, 0x2C, 0x3C};

    private final String name;
    private final HuskyLens device;
    private final LongSupplier clock;
    // Where the reads run: null = the helper thread. Tests run them at once (Runnable::run).
    private final Consumer<Runnable> background;
    private BackgroundWorker worker = null;
    private final Map<Integer, Integer> tagNumbers = new HashMap<>();
    private final Map<Integer, String> labels = new LinkedHashMap<>();
    private List<TagGroup> groups = TagGroup.currentSeason();
    private double hfovDeg = GUESSED_HFOV_DEG;
    private double sizeIn = Double.NaN;
    private double keepMs = 500;
    private boolean started = false;
    private List<TargetObservation> latest = Collections.emptyList();
    private Reading used = null;

    // Shared with the helper thread ("volatile": each side sees the other's change at once).
    private volatile Mode mode = Mode.TAGS;
    private volatile boolean asking = false;
    // The mode the camera has now, as far as we know (null: not sent yet).
    private volatile Mode sentMode = null;
    // Goes up at close(): a read from before that is thrown away when it finishes.
    private volatile int session = 0;
    private volatile Reading finished = null;
    private volatile String problem = "";
    private final Object deviceLock = new Object();
    // Only the helper thread uses these.
    private boolean sawNothing = false;
    private long lastKnockAt = 0;

    // One finished reading: its boxes, when it was asked for, and the mode it was read in.
    private static final class Reading {
        final HuskyLens.Block[] blocks;
        final long takenAt;
        final Mode mode;

        Reading(HuskyLens.Block[] blocks, long takenAt, Mode mode) {
            this.blocks = blocks;
            this.takenAt = takenAt;
            this.mode = mode;
        }
    }

    /**
     * Finds the HuskyLens in the robot configuration and wraps it.
     *
     * @param hardwareMap the OpMode's hardwareMap
     * @param deviceName  its name in the robot configuration, e.g. "huskylens"
     */
    public static HuskyLensCamera create(HardwareMap hardwareMap, String deviceName) {
        return new HuskyLensCamera(deviceName, Hw.get(hardwareMap, HuskyLens.class, deviceName));
    }

    /** Wraps a HuskyLens you already have. Most teams use {@link #create} instead. */
    public HuskyLensCamera(String name, HuskyLens device) {
        this(name, device, System::nanoTime, null);
    }

    // For tests: a pretend clock, and reads that run at once.
    HuskyLensCamera(String name, HuskyLens device, LongSupplier clock, Consumer<Runnable> background) {
        this.name = name;
        this.device = device;
        this.clock = clock;
        this.background = background;
    }

    /**
     * What to look for. Default {@link Mode#TAGS}. You can change it in a running OpMode: the old
     * mode's targets go away at once.
     */
    public HuskyLensCamera mode(Mode newMode) {
        if (newMode != mode) {
            mode = newMode;
            latest = Collections.emptyList();
        }
        return this;
    }

    /**
     * Says which real AprilTag a taught number is. Then {@code target(tagNumber)} and the tag groups work.
     *
     * @param taughtNumber the number on the camera's screen (ID1 = 1)
     * @param tagNumber    the AprilTag's real number
     */
    public HuskyLensCamera tag(int taughtNumber, int tagNumber) {
        tagNumbers.put(taughtNumber, tagNumber);
        return this;
    }

    /**
     * Gives a taught number a name, for {@code target(name)}. Useful for colours and objects.
     *
     * @param taughtNumber the number on the camera's screen
     * @param targetName   the name, e.g. "POLLEN"
     */
    public HuskyLensCamera label(int taughtNumber, String targetName) {
        labels.put(taughtNumber, targetName);
        return this;
    }

    /**
     * The camera's view angle from the left edge of the picture to the right edge. Measure yours
     * (docs/modules/vision.md#huskylenscamera). A wrong number makes bearings too big or too small,
     * but a bearing of 0 stays straight ahead.
     *
     * @param degrees the angle, more than 1 and less than 179
     */
    public HuskyLensCamera hfovDeg(double degrees) {
        if (!(degrees > 1 && degrees < 179)) throw new IllegalArgumentException("HuskyLens " + name + ": hfovDeg must be between 1 and 179");
        hfovDeg = degrees;
        return this;
    }

    /**
     * The real size of what it looks at, in inches: its longest side as the camera sees it (an AprilTag's
     * black square; a ball's width). With it, targets get a ROUGH distance from the box's longer side.
     * Without it, the distance is NaN.
     */
    public HuskyLensCamera sizeIn(double inches) {
        sizeIn = inches;
        return this;
    }

    /**
     * The tag groups reported as one target each (default: this season's, {@link TagGroup#currentSeason()}).
     * An empty list turns groups off.
     */
    public HuskyLensCamera groups(List<TagGroup> tagGroups) {
        groups = tagGroups == null ? Collections.<TagGroup>emptyList() : new ArrayList<>(tagGroups);
        return this;
    }

    /** Targets older than this are dropped (milliseconds, default 500). */
    public HuskyLensCamera keepTargetsMs(double ms) {
        keepMs = Math.abs(ms);
        return this;
    }

    /** Starts reading. Call in INIT. */
    @Override
    public void start() {
        started = true;
    }

    /** Every loop: asks for a new reading on the helper thread and picks up the newest one. Never waits. */
    @Override
    public void update() {
        if (!started) start();
        ask();
        Reading r = finished;
        if (r != null && r != used && r.mode == mode) {
            used = r;
            // A slow reading can be too old already when it arrives.
            latest = WebcamTags.dropOld(targetsFrom(r), clock.getAsLong(), keepMs);
        } else {
            // No new reading: forget old targets, so a camera that stops never leaves one to aim at.
            latest = WebcamTags.dropOld(latest, clock.getAsLong(), keepMs);
        }
    }

    // One reading at a time, on the helper thread.
    private void ask() {
        if (asking) return;
        asking = true;
        final int mySession = session;
        Runnable job = () -> readNow(mySession);
        if (background != null) {
            background.accept(job);
        } else {
            if (worker == null) worker = new BackgroundWorker("huskylens-" + name);
            worker.submitLatest("read", job);
        }
    }

    // On the helper thread: the slow I2C requests. deviceLock: after close() and a new start, a read still
    // running on the old helper finishes first, so two requests never mix (only helpers wait for it). A
    // job from before close() may still talk to the camera, but its answers are thrown away (session).
    private void readNow(int mySession) {
        synchronized (deviceLock) {
            try {
                Mode wanted = mode;
                boolean sending = sentMode != wanted;
                long now = clock.getAsLong();
                // Nothing seen looks the same as nothing plugged in: ask "are you there?" about once a second.
                if (sending || (sawNothing && now - lastKnockAt >= KNOCK_EVERY_NS)) {
                    lastKnockAt = now;
                    if (!answers(device)) {
                        sentMode = null;      // it may have restarted: send the mode again when it is back
                        if (mySession == session) problem = "not answering: check the cable, and set its Protocol Type to I2C or Auto Detect";
                        return;
                    }
                }
                if (sending) {
                    device.selectAlgorithm(wanted.algorithm);
                    sentMode = wanted;
                }
                long takenAt = clock.getAsLong();
                HuskyLens.Block[] blocks = device.blocks();
                sawNothing = blocks == null || blocks.length == 0;
                if (mySession == session) {
                    finished = new Reading(blocks == null ? new HuskyLens.Block[0] : blocks, takenAt, wanted);
                    problem = "";
                }
            } catch (RuntimeException e) {
                if (mySession == session) problem = "read failed: " + e.getMessage();
            } finally {
                if (mySession == session) asking = false;
            }
        }
    }

    /**
     * Asks the camera "are you there?" and checks its answer. A slow I2C request: never call it in the robot's
     * loop. The SDK's own {@code knock()} says true with nothing plugged in: on a REV hub, a read from
     * an empty port gives zeros, and the driver accepts them. A real answer starts with 0x55, 0xAA.
     *
     * @param device the camera
     * @return true if it answered
     */
    public static boolean answers(HuskyLens device) {
        I2cDeviceSynch client = device.getDeviceClient();
        client.write(KNOCK);
        byte[] reply = client.read(KNOCK.length);
        return reply != null && reply.length >= 2 && reply[0] == KNOCK[0] && reply[1] == KNOCK[1];
    }

    // Turns one reading's boxes into targets.
    private List<TargetObservation> targetsFrom(Reading r) {
        List<TargetObservation> out = new ArrayList<>();
        // The tags' places on an imaginary wall one lens-length away (x right, y down, z forward, in
        // pixels), for the tag groups, and their sizes in the picture.
        Map<Integer, double[]> places = new HashMap<>();
        Map<Integer, Double> pixels = new HashMap<>();
        double focal = focalPixels();
        for (HuskyLens.Block b : r.blocks) {
            Integer number = r.mode == Mode.TAGS ? tagNumbers.get(b.id) : null;
            int id = number != null ? number : b.id;
            double size = Math.max(b.width, b.height);
            double bearing = bearingDeg(b.x, focal);
            out.add(new TargetObservation(id, nameFor(r.mode, b.id, number), bearing, distanceIn(size, bearing, focal), r.takenAt));
            if (number != null) {
                places.put(number, new double[] {b.x - PICTURE_WIDTH / 2.0, b.y - PICTURE_HEIGHT / 2.0, focal});
                pixels.put(number, size);
            }
        }
        for (TagGroup g : groups) {
            TargetObservation o = LimelightTags.groupTarget(g, places, r.takenAt);
            if (o == null) continue;
            // The group's distance from the middle of what it found is in pixels: use the boxes' size instead.
            double total = 0;
            int count = 0;
            for (int tag : g.ids()) {
                Double p = pixels.get(tag);
                if (p != null) {
                    total += p;
                    count++;
                }
            }
            out.add(new TargetObservation(o.id, o.name, o.bearingDeg, distanceIn(total / count, o.bearingDeg, focal), o.timeNanos, o.rollDeg));
        }
        return out;
    }

    private String nameFor(Mode m, int taught, Integer tagNumber) {
        String label = labels.get(taught);
        if (label != null) return label;
        if (tagNumber != null) return "Tag " + tagNumber;
        if (taught == 0) return m == Mode.TAGS ? "Untaught tag" : "Untaught";
        return "Taught " + taught;
    }

    // Pixels per unit of sideways/forward: the lens's focal length, from the view angle.
    private double focalPixels() {
        return (PICTURE_WIDTH / 2.0) / Math.tan(Math.toRadians(hfovDeg) / 2);
    }

    // Degrees to turn to face a box centred at pixel x. Pixel x grows to the RIGHT and bearing is
    // positive to the LEFT, so left of the middle is positive.
    private static double bearingDeg(double x, double focal) {
        return Math.toDegrees(Math.atan2(PICTURE_WIDTH / 2.0 - x, focal));
    }

    // The rough distance from a box's size (a thing twice as far looks half as big), NaN without sizeIn.
    private double distanceIn(double boxPixels, double bearingDeg, double focal) {
        if (!(sizeIn > 0) || !(boxPixels > 0)) return Double.NaN;
        return sizeIn * focal / boxPixels / Math.cos(Math.toRadians(bearingDeg));
    }

    /** Everything seen in the newest reading (empty if nothing, or if it is too old). */
    @Override public List<TargetObservation> targets() { return latest; }

    /** The newest reading's boxes as the SDK gives them (empty before the first), for anything else you need. */
    public HuskyLens.Block[] latestBlocks() {
        Reading r = finished;
        return r == null ? new HuskyLens.Block[0] : r.blocks;
    }

    /** What it looks for now. */
    public Mode mode() {
        return mode;
    }

    /** True while the camera answers: a finished reading within the last second, and no problem. */
    @Override
    public boolean isWorking() {
        Reading r = finished;
        return started && problem.isEmpty() && r != null && clock.getAsLong() - r.takenAt <= NO_ANSWER_NS;
    }

    /** e.g. "TAGS, 2 seen", "not started", or why it is not working. */
    @Override
    public String status() {
        if (!started) return "not started";
        if (!problem.isEmpty()) return problem;
        Reading r = finished;
        if (r == null) return "waiting for the first reading";
        if (clock.getAsLong() - r.takenAt > NO_ANSWER_NS) return "no new reading for over 1 s";
        return String.format(Locale.US, "%s, %d seen", mode, latest.size());
    }

    /** Stops reading, forgets its targets and ends the helper thread. The camera itself stays on. */
    @Override
    public void close() {
        started = false;
        session++;
        latest = Collections.emptyList();
        finished = null;
        used = null;
        problem = "";
        sentMode = null;
        asking = false;
        if (worker != null) worker.shutdown();
        worker = null;
    }

    /** The SDK's HuskyLens object, for anything this class does not cover (e.g. arrows from line tracking). */
    public HuskyLens getHuskyLens() { return device; }

    @Override public String name() { return name; }
}
