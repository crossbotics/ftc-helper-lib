package com.crossbotics.ftchelper.robot;

import android.util.Size;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.qualcomm.robotcore.hardware.DistanceSensor;
import com.qualcomm.robotcore.hardware.NormalizedColorSensor;
import com.qualcomm.robotcore.hardware.NormalizedRGBA;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.opencv.ImageRegion;
import org.firstinspires.ftc.vision.opencv.PredominantColorProcessor;

/**
 * A sensor that looks at ONE spot (the intake's mouth, or the "ready to fire" slot) and says which
 * game piece is there: one of your kinds ("POLLEN"), {@link Pieces#UNKNOWN} (a piece, but not one it
 * knows), or {@link Pieces#NONE}.
 *
 * <pre>{@code
 * readyColor = add(PieceSensor.color("ready_color").kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR"));   // Robot.java
 * if (robot.readyColor.sees("POLLEN")) ...
 * }</pre>
 *
 * <p>Three kinds of sensor:
 * <ul>
 * <li>{@link #color} a REV Color Sensor V3 (REV-31-1557): its colour tells the kinds apart, its
 *     distance says whether a piece is there (1-10 cm).</li>
 * <li>{@link #distance} a distance sensor (REV 2m Distance Sensor, REV-31-1505) looking ACROSS a
 *     channel: a bigger piece comes closer, so it tells SIZES apart (not colours).</li>
 * <li>{@link #camera} a second webcam looking at the spot, with the SDK's PredominantColorProcessor
 *     (the main colour inside a box of the picture): colours, no distance.</li>
 * </ul>
 *
 * <p>Tunables (the section is the device name in CamelCase: "ready_color" reads {@code Tunables.ReadyColor}):
 * one number per kind, named like the kind: its colour (hue, 0-360 degrees) for a colour sensor or a
 * camera, its distance reading (mm) for a distance sensor. Find them with the Piece Tuner
 * ({@link com.crossbotics.ftchelper.finders.PieceTunerOpMode}). Also {@code nearMm} (a piece is there
 * when the distance is below it), {@code hueTolerance} / {@code sizeToleranceMm} (how far from a
 * kind's number still counts), {@code minSaturation} (camera: how colourful the box must be),
 * {@code steadyMs} (a reading must stay this long before it counts).
 *
 * Docs: docs/modules/robot.md (section "Pieces and PieceSensor")
 */
public final class PieceSensor extends Module<PieceSensor> {
    enum Type { COLOR, DISTANCE, CAMERA }

    final Type type;
    private List<String> kinds = new ArrayList<>();
    // Where the simulator puts it: "intake" or "ready" (Pieces sets it; alone: "ready").
    String spot = null;
    // Camera: the box of the picture it looks at, -1..1 from the centre (left, top, right, bottom).
    private double[] box = {-0.25, 0.25, 0.25, -0.25};
    private Reader reader;
    private final Map<String, Tunable> kindNumbers = new LinkedHashMap<>();
    private final List<String> untuned = new ArrayList<>();
    private Tunable nearMm, tolerance, minSaturation, steadyMs;
    // The latest reading (NaN = none) and what it says, then the "steady" answer.
    private double hue = Double.NaN, saturation = Double.NaN, distance = Double.NaN;
    private String raw = Pieces.NONE, steady = Pieces.NONE, pending = Pieces.NONE;
    private long pendingSince = 0;
    LongSupplier clock = System::nanoTime;

    private PieceSensor(Type type, String device) {
        super(device);
        this.type = type;
    }

    /**
     * A colour sensor with a distance reading, like the REV Color Sensor V3 (configure it as "REV
     * Color Sensor V3"). Put it 1-2 cm from where a piece passes or rests.
     *
     * @param name its configuration name, e.g. "ready_color"
     */
    public static PieceSensor color(String name) {
        return new PieceSensor(Type.COLOR, name);
    }

    /**
     * A distance sensor looking across a channel at the far wall (REV 2m Distance Sensor: configure it
     * as "REV 2M Distance Sensor"). A bigger piece is closer: it tells pieces apart by SIZE only. The
     * 2m sensor reads from 5 cm: put it at least 5 cm from the nearest side of the biggest piece.
     *
     * @param name its configuration name, e.g. "intake_distance"
     */
    public static PieceSensor distance(String name) {
        return new PieceSensor(Type.DISTANCE, name);
    }

    /**
     * A second webcam that looks at the spot from close by (the SDK's PredominantColorProcessor reads
     * the main colour in a box of the picture). It runs alongside an AprilTag webcam or a Limelight; it
     * uses a small picture (320x240) and no live view, to leave USB room for the other camera.
     *
     * @param webcamName its configuration name, e.g. "Webcam 2"
     */
    public static PieceSensor camera(String webcamName) {
        return new PieceSensor(Type.CAMERA, webcamName);
    }

    /**
     * The kinds of piece it can tell apart, as names that are also the Tunables fields (letters, digits
     * and _ only), e.g. "POLLEN", "RED_NECTAR". Inside {@link Pieces} it gets the Pieces' kinds.
     *
     * @param names one or more names
     */
    public PieceSensor kinds(String... names) {
        kinds = Pieces.checkKinds(names);
        return this;
    }

    /**
     * Camera only: the box of the picture to read, from the centre: -1 = left/bottom edge, 1 =
     * right/top edge. Default: the middle quarter (-0.25, 0.25, 0.25, -0.25).
     */
    public PieceSensor box(double left, double top, double right, double bottom) {
        if (type != Type.CAMERA) throw new IllegalStateException("Robot.java: " + device + ": box(...) is for a camera");
        if (left >= right || bottom >= top) throw new IllegalArgumentException("Robot.java: " + device + ": box needs left < right and bottom < top");
        box = new double[] {left, top, right, bottom};
        return this;
    }

    /** The kinds it tells apart. */
    public List<String> kindNames() {
        return Collections.unmodifiableList(kinds);
    }

    // ----------------------------- set up -----------------------------

    @Override
    void build(Devices devices, TunableSection t) {
        if (kinds.isEmpty()) {
            throw new IllegalStateException("Robot.java: " + device + " needs its kinds: .kinds(\"POLLEN\", \"NECTAR\") "
                    + "(or put it in a Pieces line)");
        }
        boolean size = type == Type.DISTANCE;
        for (String k : kinds) {
            if (t.has(k)) kindNumbers.put(k, t.tunable(k, 0, size ? 1 : 2).range(0, size ? 2000 : 360));
            else untuned.add(k);
        }
        nearMm = type == Type.CAMERA ? null : t.tunable("nearMm", size ? 100 : 40, 1).range(0, 2000);
        tolerance = size ? t.tunable("sizeToleranceMm", 8, 1).range(0, 500) : t.tunable("hueTolerance", 30, 1).range(1, 180);
        minSaturation = type == Type.DISTANCE ? null : t.tunable("minSaturation", type == Type.CAMERA ? 0.35 : 0, 0.05).range(0, 1);
        steadyMs = t.tunable("steadyMs", 30, 5).range(0, 1000);
        if (type == Type.COLOR) {
            NormalizedColorSensor c = devices.get(NormalizedColorSensor.class, device);
            DistanceSensor d = c instanceof DistanceSensor ? (DistanceSensor) c : null;
            if (t.has("gain")) c.setGain((float) t.number("gain", 2));
            reader = new ColorReader(c, d);
        } else if (type == Type.DISTANCE) {
            reader = new DistanceReader(devices.get(DistanceSensor.class, device));
        } else {
            // A camera problem never stops the OpMode: it says why and sees nothing.
            WebcamName cam = devices.tryGet(WebcamName.class, device);
            reader = cam == null ? new BrokenReader("not found: no webcam called \"" + device + "\" in the robot configuration")
                    : CameraReader.start(cam, box);
        }
    }

    @Override
    List<String> notes() {
        List<String> out = super.notes();
        if (!untuned.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (String k : untuned) names.append(names.length() == 0 ? "" : ", ").append(k);
            out.add("Tunables." + name() + ": " + names + " missing: it cannot tell "
                    + (untuned.size() == 1 ? "that kind" : "those kinds") + " yet (run the Piece Tuner)");
        }
        return out;
    }

    // ----------------------------- reading -----------------------------

    /** Every loop: reads the sensor (a few ms over I2C) and works out what is there. */
    @Override
    public void update() {
        if (reader == null) return;
        double[] r = reader.read();
        hue = r[0];
        saturation = r[1];
        distance = r[2];
        raw = classify(hue, saturation, distance);
        long now = clock.getAsLong();
        if (!raw.equals(pending)) {
            pending = raw;
            pendingSince = now;
        }
        if ((now - pendingSince) / 1e6 >= steadyMs.get()) steady = pending;
    }

    // NONE, UNKNOWN or the nearest kind (see the class notes).
    String classify(double h, double s, double d) {
        boolean there;
        if (type == Type.CAMERA) there = !Double.isNaN(s) && s >= minSaturation.get();
        else if (!Double.isNaN(d)) there = d < nearMm.get();
        else there = type == Type.COLOR && !Double.isNaN(s) && s >= Math.max(0.2, minSaturation.get());   // no distance: colourful = there
        if (!there) return Pieces.NONE;
        if (type != Type.DISTANCE && minSaturation != null && !Double.isNaN(s) && s < minSaturation.get()) return Pieces.UNKNOWN;
        double value = type == Type.DISTANCE ? d : h;
        if (Double.isNaN(value)) return Pieces.UNKNOWN;
        String best = Pieces.UNKNOWN;
        double bestOff = tolerance.get();
        for (Map.Entry<String, Tunable> e : kindNumbers.entrySet()) {
            double off = type == Type.DISTANCE ? Math.abs(value - e.getValue().get()) : hueDiff(value, e.getValue().get());
            if (off <= bestOff) {
                bestOff = off;
                best = e.getKey();
            }
        }
        return best;
    }

    /** What is at the spot now: a kind, {@link Pieces#UNKNOWN}, or {@link Pieces#NONE}. Steady: a reading counts after steadyMs. */
    public String kind() {
        return steady;
    }

    /** True if a piece of this kind is at the spot now. */
    public boolean sees(String kind) {
        return steady.equals(kind);
    }

    /** True if any piece (known or not) is at the spot now. */
    public boolean seesPiece() {
        return !steady.equals(Pieces.NONE);
    }

    /** The latest reading's kind, before the steadyMs wait (the tuner shows it). */
    public String rawKind() {
        return raw;
    }

    /** The latest colour, 0-360 degrees (NaN for a distance sensor, or no reading yet). */
    public double hueDeg() {
        return hue;
    }

    /** The latest colourfulness, 0 (grey) to 1 (pure colour); NaN for a distance sensor. */
    public double saturation() {
        return saturation;
    }

    /** The latest distance in mm (NaN for a camera, or no reading yet). */
    public double distanceMm() {
        return distance;
    }

    /** The number this sensor compares: hue (colour, camera) or distance in mm (distance sensor). */
    public double measured() {
        return type == Type.DISTANCE ? distance : hue;
    }

    /** True for a distance sensor (it compares mm, not colours). */
    public boolean measuresSize() {
        return type == Type.DISTANCE;
    }

    /** True for a sensor that knows how far away the piece is (colour and distance sensors). */
    public boolean hasDistance() {
        return type != Type.CAMERA;
    }

    /** Why it sees nothing ("" when it works): a camera that is missing or did not start. */
    public String problem() {
        return reader == null ? "" : reader.problem();
    }

    /** "color", "distance" or "camera". */
    public String sensorType() {
        return type.name().toLowerCase(java.util.Locale.US);
    }

    @Override
    public void stop() {
        if (reader != null) reader.close();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!problem().isEmpty()) t.warn(device + ": " + problem());
        t.line(name(), steady);
        if (type == Type.DISTANCE) t.debug("  " + name() + " mm", fmt("%.0f", distance));
        else t.debug("  " + name() + " hue/sat" + (type == Type.COLOR ? "/mm" : ""),
                type == Type.COLOR ? fmt("%.0f / %.2f / %.0f", hue, saturation, distance) : fmt("%.0f / %.2f", hue, saturation));
    }

    // ----------------------------- the library's -----------------------------

    @Override
    String checkPart(String id) {
        return id;   // not a motor or servo: the id is a note only
    }

    @Override
    boolean makesDefaultShape() {
        return false;
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        String at = spot == null ? "ready   # TODO intake or ready: which spot it looks at" : spot;
        if (type == Type.COLOR) out.add("color." + device + " = at=" + at);
        else if (type == Type.DISTANCE) out.add("distance." + device + " = at=" + (spot == null ? "ready" : spot)
                + ", acrossMm=150   # TODO measure: from the sensor to the far wall" + (spot == null ? "; at= intake or ready" : ""));
        // A camera's colours are not simulated: it warns "not found" and sees nothing.
    }

    // The difference between two hues (degrees), going the short way round the colour wheel (350 and 10 are 20 apart).
    static double hueDiff(double a, double b) {
        double d = Math.abs(((a - b) % 360 + 360) % 360);
        return Math.min(d, 360 - d);
    }

    /** Hue (0-360), saturation (0-1) from red, green, blue (any scale). Hue NaN for black or grey. */
    static double[] hueSat(double r, double g, double b) {
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double sat = max <= 0 ? 0 : (max - min) / max;
        if (max - min <= 1e-9) return new double[] {Double.NaN, sat};
        double h;
        if (max == r) h = 60 * (((g - b) / (max - min)) % 6);
        else if (max == g) h = 60 * ((b - r) / (max - min) + 2);
        else h = 60 * ((r - g) / (max - min) + 4);
        return new double[] {(h + 360) % 360, sat};
    }

    // ----------------------------- the readers -----------------------------

    // One sensor's reading: {hue, saturation, distance mm} (NaN for what it cannot measure).
    interface Reader {
        double[] read();

        default String problem() {
            return "";
        }

        default void close() { }
    }

    private static final class ColorReader implements Reader {
        private final NormalizedColorSensor color;
        private final DistanceSensor distance;

        ColorReader(NormalizedColorSensor color, DistanceSensor distance) {
            this.color = color;
            this.distance = distance;
        }

        @Override public double[] read() {
            NormalizedRGBA c = color.getNormalizedColors();
            double[] hs = hueSat(c.red, c.green, c.blue);
            double mm = distance == null ? Double.NaN : distance.getDistance(DistanceUnit.MM);
            return new double[] {hs[0], hs[1], mm};
        }
    }

    private static final class DistanceReader implements Reader {
        private final DistanceSensor sensor;

        DistanceReader(DistanceSensor sensor) {
            this.sensor = sensor;
        }

        @Override public double[] read() {
            double mm = sensor.getDistance(DistanceUnit.MM);
            // Out of range (nothing within 2 m): far away, so no piece.
            if (mm >= DistanceSensor.distanceOutOfRange || Double.isNaN(mm)) mm = 9999;
            return new double[] {Double.NaN, Double.NaN, mm};
        }
    }

    private static final class BrokenReader implements Reader {
        private final String why;

        BrokenReader(String why) {
            this.why = why;
        }

        @Override public double[] read() {
            return new double[] {Double.NaN, Double.NaN, Double.NaN};
        }

        @Override public String problem() {
            return why;
        }
    }

    // A webcam with the SDK's PredominantColorProcessor (SDK 12 Vision). The SDK reads the pictures on
    // its own threads, so read() only picks up the latest answer (never waits).
    private static final class CameraReader implements Reader {
        private final VisionPortal portal;
        private final PredominantColorProcessor processor;

        private CameraReader(VisionPortal portal, PredominantColorProcessor processor) {
            this.portal = portal;
            this.processor = processor;
        }

        static Reader start(WebcamName cam, double[] box) {
            try {
                PredominantColorProcessor p = new PredominantColorProcessor.Builder()
                        .setRoi(ImageRegion.asUnityCenterCoordinates(box[0], box[1], box[2], box[3]))
                        .setSwatches(PredominantColorProcessor.Swatch.values())
                        .build();
                // Small pictures and no live view: two webcams share the hub's USB, and the live view
                // screen can show only one portal without a split view.
                VisionPortal portal = new VisionPortal.Builder()
                        .setCamera(cam)
                        .setCameraResolution(new Size(320, 240))
                        .enableLiveView(false)
                        .addProcessor(p)
                        .build();
                return new CameraReader(portal, p);
            } catch (RuntimeException | LinkageError e) {
                return new BrokenReader("camera did not start: " + e);
            }
        }

        @Override public double[] read() {
            PredominantColorProcessor.Result r = processor.getAnalysis();
            if (r == null || r.RGB == null || r.RGB.length < 3) return new double[] {Double.NaN, Double.NaN, Double.NaN};
            double[] hs = hueSat(r.RGB[0], r.RGB[1], r.RGB[2]);
            return new double[] {hs[0], hs[1], Double.NaN};
        }

        @Override public String problem() {
            return portal.getCameraState() == VisionPortal.CameraState.ERROR ? "camera error" : "";
        }

        @Override public void close() {
            portal.close();
        }
    }

    /** For tests: a sensor that reads from {@code reader} instead of hardware. */
    PieceSensor useReader(Reader r) {
        reader = r;
        return this;
    }
}
