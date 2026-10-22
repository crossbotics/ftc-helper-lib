package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.PieceSensor;
import com.crossbotics.ftchelper.robot.Pieces;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Piece Tuner: shows what each {@link PieceSensor} of your robot reads, and measures the numbers
 * it needs. Hold each kind of piece in front of the sensor (where it really sits on the robot), press
 * A, and copy the lines it writes into Tunables.java.
 *
 * <pre>
 * Gamepad 1:
 *   D-pad up/down    = choose the sensor
 *   D-pad left/right = choose what is in front of it: a kind, or EMPTY (nothing)
 *   A                = measure it (about half a second: hold the piece still)
 *   B                = forget this sensor's measurements
 * Help: GUIDE (or hold START 1 s).
 * </pre>
 *
 * <p>Measure EMPTY too: it sets {@code nearMm} (or a camera's {@code minSaturation}), the line between
 * "a piece is there" and "nothing". Nothing moves in this tuner.
 *
 * <p>In your TeamCode, next to Robot.java:
 * <pre>{@code
 * @TeleOp(name = "Piece Tuner", group = "Finders")
 * public class PieceTuner extends PieceTunerOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 *
 * Docs: docs/modules/finders.md. Guide: docs/modules/robot.md (section "Pieces and PieceSensor")
 */
public abstract class PieceTunerOpMode extends FinderOpMode {
    /** How many readings one measurement averages. */
    static final int SAMPLES = 20;
    static final String EMPTY = "EMPTY";

    /** Your robot in five files: {@code return new Robot();} (it needs a Pieces or a PieceSensor line). */
    protected abstract RobotDefinition robot();

    private final List<PieceSensor> sensors = new ArrayList<>();
    private int sensorIndex = 0, kindIndex = 0;
    // The measurement running now: its readings (null = none running).
    private List<double[]> sampling = null;
    private String samplingWhat = "";
    // Per sensor: what was measured, by kind (or EMPTY): {value (hue or mm), saturation, distance mm}.
    private final Map<PieceSensor, Map<String, double[]>> measured = new LinkedHashMap<>();
    private String lastNote = "";

    @Override
    protected String finderName() {
        return "Piece Tuner";
    }

    @Override
    protected void setup() {
        RobotDefinition robot = robot();
        if (robot == null) throw new IllegalStateException("The Piece Tuner needs your robot: robot() { return new Robot(); }");
        robot.setup(hardwareMap);
        sensors.addAll(sensorsOf(robot));
        if (sensors.isEmpty()) throw new IllegalStateException("Robot.java has no Pieces or PieceSensor line (docs/modules/robot.md)");
        for (PieceSensor s : sensors) measured.put(s, new LinkedHashMap<>());
    }

    /** Every piece sensor of the robot: the Pieces' sensors and the ones added alone. */
    static List<PieceSensor> sensorsOf(RobotDefinition robot) {
        List<PieceSensor> out = new ArrayList<>();
        for (Module<?> m : robot.modules()) {
            if (m instanceof PieceSensor) out.add((PieceSensor) m);
            if (m instanceof Pieces) {
                Pieces p = (Pieces) m;
                if (p.intakeSensor() != null) out.add(p.intakeSensor());
                if (p.readySensor() != null) out.add(p.readySensor());
            }
        }
        return out;
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> { if (sampling == null) { sensorIndex = (sensorIndex + sensors.size() - 1) % sensors.size(); kindIndex = 0; } })
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> { if (sampling == null) { sensorIndex = (sensorIndex + 1) % sensors.size(); kindIndex = 0; } })
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> { if (sampling == null) kindIndex = (kindIndex + choices().size() - 1) % choices().size(); })
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> { if (sampling == null) kindIndex = (kindIndex + 1) % choices().size(); })
         .bindPress(Pad.G1, Btn.A, () -> { if (sampling == null) { sampling = new ArrayList<>(); samplingWhat = choices().get(kindIndex); } })
         .bindPress(Pad.G1, Btn.B, () -> {
             sampling = null;
             measured.get(sensor()).clear();
             results.removeStartingWith("piece:" + sensor().deviceName() + ":");
             lastNote = "Forgot " + sensor().deviceName() + "'s measurements.";
         });
    }

    private PieceSensor sensor() {
        return sensors.get(sensorIndex);
    }

    // The kinds, then EMPTY.
    private List<String> choices() {
        List<String> c = new ArrayList<>(sensor().kindNames());
        c.add(EMPTY);
        return c;
    }

    @Override
    protected void step() {
        for (PieceSensor s : sensors) s.update();
        if (sampling == null) return;
        PieceSensor s = sensor();
        double v = s.measured();
        if (Double.isNaN(v) && !samplingWhat.equals(EMPTY)) return;   // no colour yet (black, or the camera is starting)
        sampling.add(new double[] {v, s.saturation(), s.distanceMm()});
        if (sampling.size() < SAMPLES) return;
        measured.get(s).put(samplingWhat, average(sampling, !s.measuresSize()));
        sampling = null;
        lastNote = "Measured " + samplingWhat + " at " + s.deviceName() + ".";
        writeLines(s);
    }

    // The mean of each column; the first column is a hue (averaged round the colour wheel) when hue is true.
    static double[] average(List<double[]> rows, boolean hue) {
        double[] out = new double[3];
        for (int col = 0; col < 3; col++) {
            double sum = 0, sx = 0, sy = 0;
            int n = 0;
            for (double[] r : rows) {
                if (Double.isNaN(r[col])) continue;
                n++;
                if (col == 0 && hue) {
                    sx += Math.cos(Math.toRadians(r[col]));
                    sy += Math.sin(Math.toRadians(r[col]));
                } else {
                    sum += r[col];
                }
            }
            if (n == 0) out[col] = Double.NaN;
            else if (col == 0 && hue) out[col] = (Math.toDegrees(Math.atan2(sy, sx)) + 360) % 360;
            else out[col] = sum / n;
        }
        return out;
    }

    // The Tunables lines from what this sensor measured so far.
    private void writeLines(PieceSensor s) {
        String section = "Tunables." + s.name();
        String key = "piece:" + s.deviceName() + ":";
        Map<String, double[]> m = measured.get(s);
        List<Double> values = new ArrayList<>();
        double farthestPiece = Double.NaN, leastSat = Double.NaN;
        for (Map.Entry<String, double[]> e : m.entrySet()) {
            if (e.getKey().equals(EMPTY)) continue;
            double[] v = e.getValue();
            values.add(v[0]);
            results.set(key + e.getKey(), section + ": " + e.getKey() + " = " + FinderResults.num(v[0], 0)
                    + (s.measuresSize() ? "   // mm with it in front" : "   // its colour (hue, degrees)"));
            if (!Double.isNaN(v[2])) farthestPiece = Double.isNaN(farthestPiece) ? v[2] : Math.max(farthestPiece, v[2]);
            if (!Double.isNaN(v[1])) leastSat = Double.isNaN(leastSat) ? v[1] : Math.min(leastSat, v[1]);
        }
        // Tolerance: half the smallest gap between two kinds, so each reading goes to the nearest kind.
        double gap = smallestGap(values, !s.measuresSize());
        if (!Double.isNaN(gap)) {
            if (s.measuresSize()) {
                results.set(key + "tol", section + ": sizeToleranceMm = " + FinderResults.num(Math.max(2, gap / 2), 0));
                if (gap < 10) lastNote = "Two kinds read only " + FinderResults.num(gap, 0) + " mm apart: they may be mixed up. Move the sensor?";
            } else {
                results.set(key + "tol", section + ": hueTolerance = " + FinderResults.num(Math.min(40, Math.max(5, gap / 2)), 0));
                if (gap < 15) lastNote = "Two kinds' colours are only " + FinderResults.num(gap, 0) + " deg apart: they may be mixed up.";
            }
        }
        double[] empty = m.get(EMPTY);
        if (empty != null && s.hasDistance() && !Double.isNaN(farthestPiece)) {
            double emptyMm = Math.min(empty[2], s.measuresSize() ? 2000 : 150);
            if (emptyMm <= farthestPiece + 2) {
                lastNote = "EMPTY reads no farther than a piece: the sensor cannot see when the spot is empty. Aim it at a far wall.";
            } else {
                results.set(key + "near", section + ": nearMm = " + FinderResults.num((emptyMm + farthestPiece) / 2, 0)
                        + "   // a piece is there below this");
            }
        }
        if (empty != null && !s.hasDistance() && !Double.isNaN(leastSat) && !Double.isNaN(empty[1])) {
            if (empty[1] >= leastSat - 0.05) lastNote = "EMPTY looks as colourful as a piece: put a grey or black background behind the spot.";
            else results.set(key + "sat", section + ": minSaturation = " + FinderResults.num((empty[1] + leastSat) / 2, 2));
        }
    }

    // The smallest difference between any two values (hues go round the wheel). NaN with fewer than two.
    static double smallestGap(List<Double> v, boolean hue) {
        double best = Double.NaN;
        for (int i = 0; i < v.size(); i++) {
            for (int j = i + 1; j < v.size(); j++) {
                double d = Math.abs(v.get(i) - v.get(j));
                if (hue) d = Math.min(d, 360 - d);
                if (Double.isNaN(best) || d < best) best = d;
            }
        }
        return best;
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.text("Piece Tuner: " + sensors.size() + " sensor(s). Nothing moves. Press START.");
    }

    @Override
    protected void show(RobotTelemetry t) {
        PieceSensor s = sensor();
        t.section("Sensor " + (sensorIndex + 1) + "/" + sensors.size() + ": " + s.deviceName() + " (" + s.sensorType() + ")");
        if (!s.problem().isEmpty()) t.warn(s.problem());
        if (s.hasDistance()) t.line("Distance", FinderResults.num(s.distanceMm(), 0) + " mm");
        if (!s.measuresSize()) t.line("Colour", "hue " + FinderResults.num(s.hueDeg(), 0) + " deg, saturation " + FinderResults.num(s.saturation(), 2));
        t.line("It sees", s.kind() + (s.rawKind().equals(s.kind()) ? "" : " (now " + s.rawKind() + ")"));
        t.section("In front of it: < " + choices().get(kindIndex) + " >   (D-pad left/right)");
        if (sampling != null) t.text("Measuring " + samplingWhat + ": " + sampling.size() + "/" + SAMPLES + " (hold still)");
        else t.text("A = measure it, B = forget this sensor's, D-pad up/down = another sensor");
        if (!lastNote.isEmpty()) t.text(lastNote);
        if (!results.isEmpty()) t.text("Copy the lines below into Tunables.java (then rebuild): changes made only in the tuning menu are lost.");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
                "Shows each piece sensor's readings and measures",
                "the numbers it needs. Nothing moves.",
                "D-pad up/down: choose the sensor.",
                "D-pad left/right: what is in front of it",
                "  (a kind of piece, or EMPTY).",
                "A: measure (hold the piece where it sits).",
                "B: forget this sensor's measurements.",
                "Measure every kind and EMPTY, then copy the",
                "lines into Tunables.java and rebuild.",
                "Tuning-menu changes: copy them into",
                "Tunables.java too, or they are lost."};
    }

    @Override
    protected void stopAll() {
        for (PieceSensor s : sensors) s.stop();
    }
}
