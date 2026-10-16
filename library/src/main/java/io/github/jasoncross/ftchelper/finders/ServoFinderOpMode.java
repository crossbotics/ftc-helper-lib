package io.github.jasoncross.ftchelper.finders;

import com.qualcomm.robotcore.hardware.Servo;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Finds a servo's numbers: its safe ends (limits) and the positions you want (open, closed, ...).
 * Nudge the servo with the D-pad, mark the ends and positions, and copy the code lines it shows.
 * No code to write: it lists every servo in the robot configuration.
 *
 * <pre>
 * Choose:  D-pad up/down = servo, A = use it
 * Nudge:   D-pad right/left = +/- 0.01   D-pad up/down = +/- 0.05   hold RB = 10 times finer
 *          X = mark this as one END, B = mark the other END (where it just stops touching the frame)
 *          A = save this as a position (P1, P2, ...; rename them in your code), Y = undo the last one
 *          BACK = choose another servo
 * </pre>
 *
 * The servo does not move until you first nudge it (its real position is unknown before that).
 * In your TeamCode: {@code @TeleOp(name = "Servo Finder", group = "Finders")
 * public class ServoFinder extends ServoFinderOpMode { }}
 *
 * Docs: docs/modules/finders.md. Guide: docs/guides/finders.md
 */
public abstract class ServoFinderOpMode extends FinderOpMode {

    private Chooser chooser;
    private Servo servo;          // null = still choosing
    private String servoName;
    private double position = Double.NaN;   // NaN = not moved yet
    private double endA = Double.NaN, endB = Double.NaN;
    private final Map<String, Double> positions = new LinkedHashMap<>();

    @Override protected String finderName() { return "Servo finder"; }

    @Override
    protected void setup() {
        chooser = new Chooser(new ArrayList<>(hardwareMap.getAllNames(Servo.class)));
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.text("Servo finder: finds a servo's safe ends and positions.");
        t.text("Nothing moves until you nudge it. Press START.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> { if (servo == null) chooser.previous(); else nudge(0.05); })
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> { if (servo == null) chooser.next(); else nudge(-0.05); })
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> { if (servo != null) nudge(0.01); })
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> { if (servo != null) nudge(-0.01); })
         .bindPress(Pad.G1, Btn.A, () -> { if (servo == null) choose(); else savePosition(); })
         .bindPress(Pad.G1, Btn.X, () -> { if (moved()) { endA = position; writeResults(); } })
         .bindPress(Pad.G1, Btn.B, () -> { if (moved()) { endB = position; writeResults(); } })
         .bindPress(Pad.G1, Btn.Y, this::undoPosition)
         .bindPress(Pad.G1, Btn.BACK, () -> servo = null);
    }

    // A = use the chosen servo. Its ends and positions start fresh (earlier lines for it are replaced).
    private void choose() {
        if (chooser.isEmpty()) return;
        servoName = chooser.current();
        servo = hardwareMap.get(Servo.class, servoName);
        position = Double.NaN;
        endA = endB = Double.NaN;
        positions.clear();
    }

    private boolean moved() {
        return servo != null && !Double.isNaN(position);
    }

    // Moves by "amount" (10 times finer while RB is held). The first nudge starts from the position
    // the SDK last sent, or 0.5 if none is known.
    private void nudge(double amount) {
        if (Double.isNaN(position)) position = FinderMath.servoPosition(servo.getPosition());
        double step = gamepad1.right_bumper ? amount / 10 : amount;
        position = FinderMath.servoPosition(position + step);
        servo.setPosition(position);
    }

    private void savePosition() {
        if (!moved()) return;
        positions.put("P" + (positions.size() + 1), position);
        writeResults();
    }

    private void undoPosition() {
        if (positions.isEmpty()) return;
        String last = null;
        for (String k : positions.keySet()) last = k;
        positions.remove(last);
        writeResults();
    }

    // The code lines for this servo. "servo:<name>" keys, so each servo has its own lines.
    private void writeResults() {
        String key = "servo:" + servoName;
        results.removeStartingWith(key);
        String line = "PositionServo.create(hardwareMap, \"" + servoName + "\")";
        if (!Double.isNaN(endA) && !Double.isNaN(endB)) {
            line += ".limits(" + FinderResults.num(Math.min(endA, endB), 3) + ", "
                    + FinderResults.num(Math.max(endA, endB), 3) + ")";
        }
        results.set(key + ":0", line);
        int n = 1;
        for (Map.Entry<String, Double> p : positions.entrySet()) {
            results.set(key + ":" + n++, "    .position(\"" + p.getKey() + "\", " + FinderResults.num(p.getValue(), 3) + ")");
        }
    }

    @Override protected void step() { }

    @Override
    protected void show(RobotTelemetry t) {
        if (servo == null) {
            t.section("Choose a servo (D-pad, then A)");
            chooser.addTelemetry(t, "No servos in the robot configuration.");
            return;
        }
        t.section("Servo \"" + servoName + "\"  (BACK = another)");
        t.line("Position", Double.isNaN(position) ? "not moved yet: nudge it" : FinderResults.num(position, 3));
        t.line("End X", Double.isNaN(endA) ? "-" : FinderResults.num(endA, 3));
        t.line("End B", Double.isNaN(endB) ? "-" : FinderResults.num(endB, 3));
        t.text("D-pad nudge (RB = finer), X/B = ends, A = save position, Y = undo");
        if (!Double.isNaN(endA) && !Double.isNaN(endB)
                && (position < Math.min(endA, endB) - 1e-9 || position > Math.max(endA, endB) + 1e-9)) {
            t.warn("Outside the marked ends");
        }
    }

    // A servo has no "off" that is safe for every mechanism, so it stays where it is.
    @Override protected void stopAll() { }
}
