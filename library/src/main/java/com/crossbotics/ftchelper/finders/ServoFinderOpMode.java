package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.hardware.ServoSetup;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds a servo's numbers: its safe ends (limits) and the positions you want (open, closed, ...).
 * Nudge the servo with the D-pad, mark the ends and positions, and copy the code lines it shows.
 * No code to write: it lists every servo in the robot configuration.
 *
 * <p>It shows the code for {@code PositionServo} and, for the robot in five files, the line for
 * {@code Tunables.java}: {@code Tunables.Claw: P1 = 0.210, P2 = 0.770} (rename P1, P2 to open, closed).
 *
 * <pre>
 * Choose:  D-pad up/down = servo, A = use it, Y = full range on/off (500-2500 us instead of the
 *          SDK's normal 600-2400: a goBILDA 2000 servo turns 300 degrees instead of 270; use it when
 *          the part has .fullRange() in Robot.java, so the numbers fit)
 * Start:   D-pad = choose a start point (the servo does NOT move yet), A = move it there.
 *          Pick a value near where it sits now: a servo cannot tell where it is.
 * Nudge:   D-pad right/left = +/- 0.01   D-pad up/down = +/- 0.05   hold RB = 10 times finer
 *          X = mark this as one END, B = mark the other END (where it just stops touching the frame).
 *          Once both are marked, it stays between them (hold LB to go past, e.g. to mark again)
 *          A = save this as a position (P1, P2, ...; rename them in your code), Y = undo the last one
 *          BACK = choose another servo
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * The servo does not move until you press A at the start point (its real position is unknown).
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
    private double startAt = 0.5;           // the start point chosen before the first move
    private double endA = Double.NaN, endB = Double.NaN;
    private final Map<String, Double> positions = new LinkedHashMap<>();
    private boolean fullRange = false;      // Y while choosing: the servo's whole pulse range
    private String pulseNote = "";          // set when the servo cannot change its pulse range
    private final java.util.Set<String> widened = new java.util.HashSet<>();   // servos widened in this run
    private Servo other;          // a second servo that moves with the chosen one (a two-servo part), or null
    private String otherName = "";
    private boolean otherMirrored = false;
    private String chooseNote = "";        // "feeder_right moves with feeder_left: choose feeder_left"

    /**
     * The robot's two-servo parts ({@code alsoServo...} in Robot.java): each first servo's name, and its
     * second servo's name with "mirrored" or not. With one listed, the second servo moves with the first
     * here too (two servos on one part must never fight), and the second cannot be chosen alone. Tune and
     * check fills it from Robot.java; a Servo Finder of your own has none (take one side's arm off first).
     *
     * @return first servo name to {second servo name, mirrored}; empty = none
     */
    protected Map<String, Map.Entry<String, Boolean>> pairedServos() {
        return java.util.Collections.emptyMap();
    }

    /**
     * The direction Robot.java gives each servo it knows (true = {@code reversed()}): the finder turns them the
     * same way, so the positions it shows are the ones Tunables.java needs. A servo not listed keeps the
     * direction it has (your own finder can set it in {@code setup()}). Tune and check fills it from Robot.java.
     *
     * @return configuration name to reversed or not; empty = leave every servo as it is
     */
    protected Map<String, Boolean> servoDirections() {
        return java.util.Collections.emptyMap();
    }

    /**
     * Each servo's parts catalog id ({@code part(...)} in Robot.java), so the finder can say which way its
     * seller says a bigger number turns it (a hint: check it by test). Tune and check fills it from Robot.java.
     *
     * @return configuration name to catalog id; empty = none
     */
    protected Map<String, String> servoParts() {
        return java.util.Collections.emptyMap();
    }

    // "bigger = clockwise (seller); test: D-pad up" for a catalog servo whose seller says which way it turns.
    private String directionHint() {
        String id = servoParts().get(servoName);
        com.crossbotics.ftchelper.robot.Parts.ServoPart p = id == null ? null : com.crossbotics.ftchelper.robot.Parts.findServo(id);
        if (p == null || p.biggerTurns() == null) return "";
        boolean rev = servo.getDirection() == Servo.Direction.REVERSE;   // the direction really in use
        String way = rev ? (p.biggerTurns().equals("clockwise") ? "counterclockwise" : "clockwise") : p.biggerTurns();
        return "bigger = " + way + " (seller" + (rev ? ", reversed" : "") + "); test: D-pad up";
    }

    // Sends a position to the chosen servo and its second one.
    private void send(double p) {
        servo.setPosition(p);
        if (other != null) other.setPosition(p);
    }

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
         .bindPress(Pad.G1, Btn.A, () -> {
                    if (servo == null) choose();
                    else if (!moved()) moveToStart();
                    else savePosition();
                })
         .bindPress(Pad.G1, Btn.X, () -> { if (moved()) { endA = position; writeResults(); } })
         .bindPress(Pad.G1, Btn.B, () -> { if (moved()) { endB = position; writeResults(); } })
         .bindPress(Pad.G1, Btn.Y, () -> { if (servo != null) undoPosition(); else fullRange = !fullRange; })
         .bindPress(Pad.G1, Btn.BACK, () -> servo = null);
    }

    // A = use the chosen servo. Its ends and positions start fresh (earlier lines for it are replaced).
    private void choose() {
        if (chooser.isEmpty()) return;
        String name = chooser.current();
        Map<String, Map.Entry<String, Boolean>> pairs = pairedServos();
        chooseNote = "";
        for (Map.Entry<String, Map.Entry<String, Boolean>> e : pairs.entrySet()) {
            if (e.getValue().getKey().equals(name)) {
                // The second servo alone would push against the first: find the positions on the first.
                chooseNote = name + " moves with " + e.getKey() + " (one part, two servos): choose " + e.getKey();
                return;
            }
        }
        servoName = name;
        servo = hardwareMap.get(Servo.class, servoName);
        // The direction Robot.java gives it (reversed() swaps 0 and 1), so the numbers fit Tunables.java. A
        // servo Robot.java does not name keeps its own.
        Boolean rev = servoDirections().get(servoName);
        if (rev != null) servo.setDirection(rev ? Servo.Direction.REVERSE : Servo.Direction.FORWARD);
        Map.Entry<String, Boolean> pair = pairs.get(servoName);
        other = null;
        otherName = "";
        if (pair != null && !hardwareMap.getAllNames(Servo.class).contains(pair.getKey())) {
            // Only one side would move, and drag the other servo through its gears: do not use it.
            chooseNote = servoName + " moves with " + pair.getKey() + ", which is not in the configuration: add it first";
            servo = null;
            return;
        }
        if (pair != null) {
            otherName = pair.getKey();
            otherMirrored = pair.getValue();
            other = hardwareMap.get(Servo.class, otherName);
            Servo.Direction d = servo.getDirection();
            Servo.Direction opposite = d == Servo.Direction.FORWARD ? Servo.Direction.REVERSE : Servo.Direction.FORWARD;
            other.setDirection(otherMirrored ? opposite : d);
        }
        // The pulse range as chosen with Y. Off: back to the SDK's normal one only if this run widened it
        // (otherwise it is left as the robot configuration made it).
        boolean set = true;
        if (fullRange) {
            set = ServoSetup.setPulseRange(servo, ServoSetup.FULL_PULSE_LOW_US, ServoSetup.FULL_PULSE_HIGH_US);
            if (other != null) ServoSetup.setPulseRange(other, ServoSetup.FULL_PULSE_LOW_US, ServoSetup.FULL_PULSE_HIGH_US);
            if (set) widened.add(servoName);
        } else if (widened.remove(servoName)) {
            ServoSetup.setPulseRange(servo, ServoSetup.SDK_PULSE_LOW_US, ServoSetup.SDK_PULSE_HIGH_US);
            if (other != null) ServoSetup.setPulseRange(other, ServoSetup.SDK_PULSE_LOW_US, ServoSetup.SDK_PULSE_HIGH_US);
        }
        pulseNote = set ? "" : "This servo cannot change its pulse range: it uses the normal 600-2400 us";
        position = Double.NaN;
        startAt = 0.5;
        endA = endB = Double.NaN;
        positions.clear();
    }

    private boolean moved() {
        return servo != null && !Double.isNaN(position);
    }

    // A, before the first move: the servo goes to the chosen start point.
    private void moveToStart() {
        position = startAt;
        send(position);
    }

    // Moves by "amount" (10 times finer while RB is held). Before the first move it only changes the
    // start point: nothing moves until A.
    private void nudge(double amount) {
        double step = gamepad1.right_bumper ? amount / 10 : amount;
        if (Double.isNaN(position)) {
            startAt = FinderMath.servoPosition(startAt + step);
            return;
        }
        double next = FinderMath.servoPosition(position + step);
        // Once both ends are marked, it stays between them (hold LB to go past, e.g. to mark again).
        if (!Double.isNaN(endA) && !Double.isNaN(endB) && !gamepad1.left_bumper) {
            next = Math.max(Math.min(endA, endB), Math.min(Math.max(endA, endB), next));
        }
        position = next;
        send(position);
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
        results.removeStartingWith(key + ":");
        String line = "PositionServo.create(hardwareMap, \"" + servoName + "\")" + (fullRange ? ".fullRange()" : "");
        if (!Double.isNaN(endA) && !Double.isNaN(endB)) {
            line += ".limits(" + FinderResults.num(Math.min(endA, endB), 3) + ", "
                    + FinderResults.num(Math.max(endA, endB), 3) + ")";
        }
        results.set(key + ":0", line);
        int n = 1;
        boolean ends = !Double.isNaN(endA) && !Double.isNaN(endB);
        for (Map.Entry<String, Double> p : positions.entrySet()) {
            double v = p.getValue();
            boolean outside = ends && (v < Math.min(endA, endB) - 1e-9 || v > Math.max(endA, endB) + 1e-9);
            results.set(key + ":" + n++, "    .position(\"" + p.getKey() + "\", " + FinderResults.num(v, 3) + ")"
                    + (outside ? "   // OUTSIDE the ends: it will stop at the end" : ""));
        }
        // The same positions for the robot in five files (Tunables.java). A servo module has no ends
        // of its own, so the ends become a reminder: keep every position between them.
        if (!positions.isEmpty()) {
            List<String> fields = new ArrayList<>();
            for (Map.Entry<String, Double> p : positions.entrySet()) {
                fields.add(p.getKey() + " = " + FinderResults.num(p.getValue(), 3));
            }
            results.set(key + ":t0", FinderResults.tunablesLine(servoName, fields));
            results.set(key + ":t1", "    // rename P1, P2... (open, closed)"
                    + (ends ? "; keep them between " + FinderResults.num(Math.min(endA, endB), 3)
                            + " and " + FinderResults.num(Math.max(endA, endB), 3) : "")
                    + (fullRange ? "; FULL RANGE: add .fullRange() to it in Robot.java" : ""));
        }
    }

    @Override protected void step() { }

    @Override
    protected void show(RobotTelemetry t) {
        if (servo == null) {
            t.section("Choose a servo (D-pad, then A)");
            chooser.addTelemetry(t, "No servos in the robot configuration.");
            if (!chooseNote.isEmpty()) t.warn(chooseNote);
            t.line("Full range (Y)", fullRange ? "ON: 500-2500 us (goBILDA 2000: 300 deg)" : "off: 600-2400 us, the SDK's normal (goBILDA 2000: 270 deg)");
            return;
        }
        t.section("Servo \"" + servoName + "\"  (BACK = another)" + (fullRange ? "  FULL RANGE" : ""));
        if (!pulseNote.isEmpty()) t.warn(pulseNote);
        if (Boolean.TRUE.equals(servoDirections().get(servoName))) t.line("Direction", "reversed, as in Robot.java");
        String hint = directionHint();
        if (!hint.isEmpty()) t.line("Hint", hint);
        if (other != null) t.line("Moves with it", otherName + (otherMirrored ? " (mirrored)" : " (same way)") + ", alsoTrim not used here");
        if (Double.isNaN(position)) {
            t.line("Start point", FinderResults.num(startAt, 3) + "  (D-pad; A = move the servo there)");
            t.text("Choose a start point near where the servo sits now. Nothing moves until A.");
            return;
        }
        t.line("Position", FinderResults.num(position, 3));
        t.line("End X", Double.isNaN(endA) ? "-" : FinderResults.num(endA, 3));
        t.line("End B", Double.isNaN(endB) ? "-" : FinderResults.num(endB, 3));
        t.text("D-pad nudge (RB = finer, LB = past the ends), X/B = ends, A = save position, Y = undo");
        if (!Double.isNaN(endA) && !Double.isNaN(endB)
                && (position < Math.min(endA, endB) - 1e-9 || position > Math.max(endA, endB) + 1e-9)) {
            t.warn("Outside the marked ends");
        }
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds a servo's safe ends and positions.",
            "1. D-pad up/down = choose a servo, A = use it.",
            "   Y = full range (if Robot.java has .fullRange()).",
            "2. D-pad = a START POINT near where it sits now",
            "   (nothing moves), A = move it there.",
            "3. D-pad right/left = +/-0.01, up/down = +/-0.05,",
            "   hold RB = 10x finer.",
            "4. Move to one end until it JUST stops pushing on",
            "   the frame (a straining servo hums), back off a",
            "   little: X. The other end: B. Then it stays",
            "   between them (hold LB to go past).",
            "5. Each position you need: A (P1, P2...). Y = undo.",
            "BACK = another servo. STOP saves the code lines.",
        };
    }

    // A servo has no "off" that is safe for every mechanism, so it stays where it is.
    @Override protected void stopAll() { }
}
