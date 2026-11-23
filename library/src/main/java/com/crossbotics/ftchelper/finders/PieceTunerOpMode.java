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
 *   A                = measure it (20 new readings: hold the piece still). The old measurement goes first,
 *                      so a failed one never leaves an old line to copy
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
 * Docs: docs/modules/finders.md. Guide: docs/reference/robot-java.md (section "Pieces and PieceSensor")
 */
public abstract class PieceTunerOpMode extends FinderOpMode {
    /** How many readings one measurement averages. */
    static final int SAMPLES = 20;
    static final String EMPTY = "EMPTY";
    /** A measurement with too few new readings stops after this long, seconds. */
    static final double TIMEOUT_SEC = 5;

    /** Your robot in five files: {@code return new Robot();} (it needs a Pieces or a PieceSensor line). */
    protected abstract RobotDefinition robot();

    final List<PieceSensor> sensors = new ArrayList<>();   // (not private: for the tests, like the next two)
    private int sensorIndex = 0, kindIndex = 0;
    // The measurement running now: its readings (null = none running).
    private List<double[]> sampling = null;
    private String samplingWhat = "";
    // Per sensor: what was measured, by kind (or EMPTY): {value (hue or mm), saturation, distance mm}.
    final Map<PieceSensor, Map<String, double[]>> measured = new LinkedHashMap<>();
    private String lastNote = "";
    private double samplingSince = 0;
    // Per sensor: why its measurements cannot give every line ("" = none).
    private final Map<PieceSensor, String> problems = new LinkedHashMap<>();
    // The sensor's reading count at the last sample: only NEW readings are measured (a distance sensor
    // is read on a helper thread and may have no new one this loop).
    private int lastReading = -1;

    @Override
    protected String finderName() {
        return "Piece Tuner";
    }

    @Override
    protected void setup() {
        RobotDefinition robot = robot();
        if (robot == null) throw new IllegalStateException("The Piece Tuner needs your robot: robot() { return new Robot(); }");
        robot.setup(hardwareMap);
        // Sensors of parts that are not on the robot yet (optional in Robot.java) are left out.
        for (PieceSensor s : sensorsOf(robot)) if (s.isPresent()) sensors.add(s);
        if (sensors.isEmpty()) {
            throw new IllegalStateException(sensorsOf(robot).isEmpty() ? "Robot.java has no Pieces or PieceSensor line (docs/reference/robot-java.md)"
                    : "Every piece sensor in Robot.java is optional and not in the robot configuration yet: add one to the configuration");
        }
        for (PieceSensor s : sensors) measured.put(s, new LinkedHashMap<>());
    }

    /** Every piece sensor of the robot: the Pieces' sensors and the ones added alone. */
    static List<PieceSensor> sensorsOf(RobotDefinition robot) {
        List<PieceSensor> out = new ArrayList<>();
        for (Module<?> m : robot.modules()) {
            if (m instanceof PieceSensor) out.add((PieceSensor) m);
            if (m instanceof Pieces) {
                Pieces p = (Pieces) m;
                if (p.comingInSensor() != null) out.add(p.comingInSensor());
                if (p.readyToFireSensor() != null) out.add(p.readyToFireSensor());
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
         .bindPress(Pad.G1, Btn.A, this::beginMeasuring)
         .bindPress(Pad.G1, Btn.B, () -> {
             sampling = null;
             measured.get(sensor()).clear();
             writeLines(sensor());
             lastNote = "Erased " + sensor().deviceName() + "'s numbers.";
         });
    }

    void beginMeasuring() {
        if (sampling != null) return;
        PieceSensor s = sensor();
        sampling = new ArrayList<>();
        samplingWhat = choices().get(kindIndex);
        samplingSince = seconds();
        lastNote = "";
        // Measuring again: the old number, and every line worked out from it, goes first.
        measured.get(s).remove(samplingWhat);
        writeLines(s);
    }

    private static double seconds() {
        return System.nanoTime() / 1e9;
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
        if (seconds() - samplingSince > TIMEOUT_SEC) {
            lastNote = "No reading of " + samplingWhat + " in " + FinderResults.num(TIMEOUT_SEC, 0) + " s: not measured."
                    + "\nNext: check what it sees, and its cable. Then {A}.";
            sampling = null;
            return;
        }
        if (s.readings() == lastReading) return;     // no new reading since the last sample
        lastReading = s.readings();
        double v = s.measured();
        if (Double.isNaN(v) && !samplingWhat.equals(EMPTY)) return;   // no color yet (black, or the camera is starting)
        sampling.add(new double[] {v, s.saturation(), s.distanceMm()});
        if (sampling.size() < SAMPLES) return;
        measured.get(s).put(samplingWhat, average(sampling, !s.measuresSize()));
        sampling = null;
        lastNote = "Measured " + samplingWhat + " at " + s.deviceName() + ".";
        // On to the next thing to measure: the student only has to show it.
        kindIndex = nextToMeasure(s, kindIndex);
        writeLines(s);
    }

    // The mean of each column; the first column is a hue (averaged round the color wheel) when hue is true.
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
    void writeLines(PieceSensor s) {
        String key = "piece:" + s.deviceName() + ":";
        // All of this sensor's lines again: one that cannot be worked out now does not stay.
        results.removeStartingWith(key);
        String section = s.name(), problem = "";
        Map<String, double[]> m = measured.get(s);
        List<Double> values = new ArrayList<>();
        double farthestPiece = Double.NaN, leastSat = Double.NaN;
        for (Map.Entry<String, double[]> e : m.entrySet()) {
            if (e.getKey().equals(EMPTY)) continue;
            double[] v = e.getValue();
            values.add(v[0]);
            results.setTunable(key + "kind:" + e.getKey(), section, e.getKey(), FinderResults.num(v[0], 0));
            if (!Double.isNaN(v[2])) farthestPiece = Double.isNaN(farthestPiece) ? v[2] : Math.max(farthestPiece, v[2]);
            if (!Double.isNaN(v[1])) leastSat = Double.isNaN(leastSat) ? v[1] : Math.min(leastSat, v[1]);
        }
        // Tolerance: half the smallest gap between two kinds, so each reading goes to the nearest kind.
        double gap = smallestGap(values, !s.measuresSize());
        if (!Double.isNaN(gap)) {
            if (s.measuresSize()) {
                results.setTunable(key + "tol", section, "sizeToleranceMm", FinderResults.num(Math.max(2, gap / 2), 0));
                if (gap < 10) problem = "Two kinds read only " + FinderResults.num(gap, 0) + " mm apart.\nThey may be mixed up. Next: move the sensor.";
            } else {
                results.setTunable(key + "tol", section, "hueTolerance", FinderResults.num(Math.min(40, Math.max(5, gap / 2)), 0));
                if (gap < 15) problem = "Two colors are only " + FinderResults.num(gap, 0) + " deg apart.\nThey may be mixed up. Next: light it better.";
            }
        }
        double[] empty = m.get(EMPTY);
        if (empty != null && s.hasDistance() && !Double.isNaN(farthestPiece)) {
            double emptyMm = Math.min(empty[2], s.measuresSize() ? 2000 : 150);
            if (Double.isNaN(emptyMm)) {
                problem = "EMPTY gave no distance: no nearMm.\nNext: check the sensor, measure EMPTY again.";
            } else if (emptyMm <= farthestPiece + 2) {
                problem = "EMPTY reads no farther than a piece.\nNext: aim it at a far wall. Measure again.";
            } else {
                results.setTunable(key + "near", section, "nearMm", FinderResults.num((emptyMm + farthestPiece) / 2, 0));
            }
        }
        if (empty != null && !s.hasDistance() && !Double.isNaN(leastSat) && !Double.isNaN(empty[1])) {
            if (empty[1] >= leastSat - 0.05) {
                problem = "EMPTY looks as colorful as a piece.\nNext: gray or black behind it. Measure again.";
            } else {
                results.setTunable(key + "sat", section, "minSaturation", FinderResults.num((empty[1] + leastSat) / 2, 2));
            }
        }
        problems.put(s, problem);
        updateStatus();
    }

    // The next kind (or EMPTY) that is not measured for this sensor, after this one; this one if none is.
    private int nextToMeasure(PieceSensor s, int from) {
        List<String> all = choices();
        for (int i = 1; i <= all.size(); i++) {
            int at = (from + i) % all.size();
            if (!measured.get(s).containsKey(all.get(at))) return at;
        }
        return from;
    }

    // DONE when each sensor that was started has every kind and EMPTY, with no problem.
    private void updateStatus() {
        boolean any = false;
        for (PieceSensor s : sensors) {
            Map<String, double[]> m = measured.get(s);
            if (m.isEmpty()) continue;
            any = true;
            String problem = problems.get(s);
            if (problem != null && !problem.isEmpty()) {
                results.notDone(s.deviceName() + " has a problem.");
                return;
            }
            List<String> need = new ArrayList<>(s.kindNames());
            need.add(EMPTY);
            for (String kind : need) {
                if (!m.containsKey(kind)) {
                    results.notDone("measure " + kind + " at " + s.deviceName() + ".");
                    return;
                }
            }
        }
        if (any) results.done();
        else results.notDone("nothing is measured.");
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
        t.warn("Nothing moves in this tool.");
        t.text("Piece tuner: teaches each sensor what a game");
        t.text("piece looks like.");
        t.line("Piece sensors found", sensors.size());
        t.text("You need: one piece of each kind.");
    }

    @Override
    protected String stoppedWords() {
        return "Nothing moves in this tool.";
    }

    // Help: nothing moves, and the sensors must keep reading (stopAll() closes them). A measurement ends.
    @Override
    protected void pause() {
        if (sampling != null) lastNote = "Stopped for help: not measured. {A} = again.";
        sampling = null;
    }

    @Override
    protected void show(RobotTelemetry t) {
        PieceSensor s = sensor();
        String what = choices().get(kindIndex);
        t.section("Sensor " + (sensorIndex + 1) + " of " + sensors.size() + ": " + s.deviceName());
        if (!s.problem().isEmpty()) {
            t.warn(s.problem());
            t.warn("Next: press STOP and check that sensor.");
        }
        if (sampling != null) {
            t.text("Measuring " + samplingWhat + ": " + sampling.size() + " of " + SAMPLES + ". Hold still.");
        } else {
            if (what.equals(EMPTY)) t.text("Next: leave the spot empty. {A} = measure.");
            else t.text("Next: hold " + what + " where it sits. {A} = measure.");
            t.text("D-pad left/right = another kind, or EMPTY.");
        }
        t.line("Measuring for", "< " + what + " >");
        if (s.hasDistance()) t.line("Distance", FinderResults.num(s.distanceMm(), 0) + " mm");
        if (!s.measuresSize()) {
            t.line("Color number", Double.isNaN(s.hueDeg()) ? "no color seen" : FinderResults.num(s.hueDeg(), 0) + " of 360");
            t.line("Color strength", FinderResults.num(s.saturation(), 2) + " of 1");
        }
        t.line("It sees now", s.rawKind() + " (uses installed numbers)");
        warnLines(t, problems.containsKey(s) ? problems.get(s) : "");
        textLines(t, lastNote);
        t.text("{B} = erase this sensor's new numbers.");
        if (sensors.size() > 1) t.text("D-pad up/down = another sensor.");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
                "Teaches each sensor what a piece looks like.",
                "Nothing moves in this tool.",
                "D-pad up/down: choose the sensor.",
                "D-pad left/right: what is in front of it",
                "  (a kind of piece, or EMPTY).",
                "{A}: measure (hold the piece where it sits).",
                "{B}: erase this sensor's measurements.",
                "Measure every kind and EMPTY.",
                "At DONE: copy into Tunables.java, install.",
                "New numbers work only after you install.",
                "Changed some in the tuning menu? Copy those",
                "into Tunables.java too, or they are lost."};
    }

    @Override
    protected void stopAll() {
        for (PieceSensor s : sensors) s.stop();
    }
}
