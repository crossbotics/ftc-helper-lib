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
 * {@code Tunables.java} when Tune and check runs it: the file, the class and
 * {@code public static double P1 = 0.210;} (P1, P2 stand for your names, e.g. open, closed).
 *
 * <pre>
 * Choose:  D-pad up/down = servo, A = use it, Y = full range on/off (500-2500 us instead of the
 *          SDK's normal 600-2400: a goBILDA 2000 servo turns 300 degrees instead of 270; use it when
 *          the part has .fullRange() in Robot.java, so the numbers fit)
 * Start:   D-pad up/down = choose a start point (the servo does NOT move yet), A = move it there.
 *          Pick a value near where it sits now: a servo cannot tell where it is.
 * Nudge:   D-pad up/down = move by the step, D-pad left/right = smaller/bigger step
 *          (0.1, 0.05, 0.01, 0.005, 0.001; it starts at 0.05)
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
 * Docs: docs/modules/finders.md. Guide: docs/tuning/03-servo-finder.md
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
    private final NudgeStep step = new NudgeStep();   // D-pad left/right changes it

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
        return "A bigger number turns it " + way + " (seller)";
    }

    // Sends a position to the chosen servo and its second one.
    private void send(double p) {
        servo.setPosition(p);
        if (other != null) other.setPosition(p);
    }

    @Override protected String finderName() { return "Servo finder"; }

    @Override protected String stoppedWords() { return "A servo may still move to its last position."; }

    @Override
    protected void setup() {
        chooser = new Chooser(new ArrayList<>(hardwareMap.getAllNames(Servo.class)));
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.warn("Robot on a block. Hands clear of the part.");
        t.warn("The servo can JUMP when you move it with {A}.");
        t.text("Servo finder: finds a servo's two safe ends");
        t.text("and the positions you use.");
        t.text("Nothing moves until you press {A} two times.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> { if (servo == null) chooser.previous(); else nudge(1); })
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> { if (servo == null) chooser.next(); else nudge(-1); })
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> { if (servo != null) step.bigger(); })
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> { if (servo != null) step.smaller(); })
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
                chooseNote = name + " moves with another servo. Choose " + e.getKey() + ".";
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
            chooseNote = "Cannot move " + servoName + " alone. Add " + pair.getKey() + " to the robot configuration.";
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
        pulseNote = set ? "" : "This servo cannot use full range.";
        position = Double.NaN;
        startAt = 0.5;
        step.reset();
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

    // Moves one step up (+1) or down (-1). Before the first move it only changes the start point:
    // nothing moves until A.
    private void nudge(int direction) {
        if (Double.isNaN(position)) {
            startAt = step.move(startAt, direction);
            return;
        }
        double next = step.move(position, direction);
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
        boolean ends = !Double.isNaN(endA) && !Double.isNaN(endB);
        double low = Math.min(endA, endB), high = Math.max(endA, endB);
        List<String> outside = new ArrayList<>();
        for (Map.Entry<String, Double> p : positions.entrySet()) {
            if (ends && (p.getValue() < low - 1e-9 || p.getValue() > high + 1e-9)) outside.add(p.getKey());
        }
        if (!ends) results.notDone("mark both ends ({X} and {B}).");
        else if (positions.isEmpty()) results.notDone("save a position you use ({A}).");
        else if (!outside.isEmpty()) results.notDone("a saved position is outside the ends.");
        else results.done();
        if (hasRobotFiles()) {
            // A servo part has no ends of its own in Tunables: the ends are only a reminder for the positions.
            if (positions.isEmpty()) return;
            String section = sectionOf(servoName);
            for (Map.Entry<String, Double> p : positions.entrySet()) {
                results.setTunable(key + ":" + p.getKey(), section, p.getKey(), FinderResults.num(p.getValue(), 3));
            }
            results.set(key + ":names", "Write your names for P1, P2 (open, closed).");
            if (ends) results.set(key + ":ends", "Keep them " + FinderResults.num(low, 3) + " to " + FinderResults.num(high, 3) + " (the safe ends).");
            if (fullRange) results.setIn(key + ":range", FinderResults.ROBOT_FILE, "Add .fullRange() to " + servoName + "'s part.");
            return;
        }
        List<String> code = new ArrayList<>();
        code.add("PositionServo.create(hardwareMap, \"" + servoName + "\")" + (fullRange ? ".fullRange()" : "")
                + (ends ? ".limits(" + FinderResults.num(low, 3) + ", " + FinderResults.num(high, 3) + ")" : ""));
        for (Map.Entry<String, Double> p : positions.entrySet()) {
            code.add("    .position(\"" + p.getKey() + "\", " + FinderResults.num(p.getValue(), 3) + ")"
                    + (outside.contains(p.getKey()) ? "   // OUTSIDE the ends" : ""));
        }
        results.setIn(key + ":0", FinderResults.HELPER_CODE, code.toArray(new String[0]));
    }

    @Override protected void step() { }

    @Override
    protected void show(RobotTelemetry t) {
        if (servo == null) {
            t.warn("Hands clear. Nothing moves in this step.");
            t.section("Servo finder: choose a servo");
            t.text("Next: D-pad up/down = choose. {A} = use it.");
            chooser.addTelemetry(t, "No servos in the robot configuration.");
            if (!chooseNote.isEmpty()) t.warn(chooseNote);
            t.line("Full range", fullRange ? "ON" : "off (normal)");
            t.text("{Y} = change it. ON only if Robot.java has");
            t.text("  .fullRange() on that servo's part.");
            return;
        }
        if (Double.isNaN(position)) {
            t.warn("{A} = the servo JUMPS to this number.");
            t.warn("Hands clear. Nothing moves until {A}.");
            t.section("Servo \"" + servoName + "\": first move");
            t.text("Next: set a number near where it sits now.");
            t.text("0 and 1 are its two ends. 0.5 is the middle.");
            t.line("Start point", FinderResults.num(startAt, 3));
            t.line("Step", step.label() + "  (D-pad left/right)");
            t.text("D-pad up/down = change the start point.");
            t.text("Not sure? Take its arm off first. Use 0.500.");
        } else {
            t.warn("Every step moves it. Hands clear.");
            t.section("Servo \"" + servoName + "\": ends and positions");
            if (Double.isNaN(endA)) {
                t.text("Next: move to one end. Stop before it pushes.");
                t.text("It hums? Move back a little. {X} = save end.");
            } else if (Double.isNaN(endB)) {
                t.text("Next: move to the other end the same way.");
                t.text("{B} = save that end.");
            } else if (positions.isEmpty()) {
                t.text("Next: move to a position you use.");
                t.text("{A} = save that position.");
            } else {
                t.text("Next: save the other positions with {A}.");
            }
            t.line("Position", FinderResults.num(position, 3) + "  (0 to 1)");
            t.line("Step", step.label() + "  (D-pad left/right)");
            t.line("Ends ({X}, {B})", (Double.isNaN(endA) ? "-" : FinderResults.num(endA, 3)) + ", "
                    + (Double.isNaN(endB) ? "-" : FinderResults.num(endB, 3)));
            t.text("D-pad up/down = move one step.");
            if (!positions.isEmpty()) t.text("{Y} = take back the last position.");
            if (!Double.isNaN(endA) && !Double.isNaN(endB)
                    && (position < Math.min(endA, endB) - 1e-9 || position > Math.max(endA, endB) + 1e-9)) {
                t.warn("Outside the marked ends. Move back inside.");
            }
        }
        if (!pulseNote.isEmpty()) t.warn(pulseNote);
        // Which way it turns matters until the first end is found; then the line only fills the screen.
        if (Double.isNaN(endA)) {
            if (Boolean.TRUE.equals(servoDirections().get(servoName))) t.line("Direction", "reversed, as in Robot.java");
            String hint = directionHint();
            if (!hint.isEmpty()) t.text(hint);
        }
        if (other != null) t.line("Moves with it", otherName + (otherMirrored ? " (mirrored)" : " (same way)"));
        t.text("{BACK} = another servo.");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds a servo's safe ends and positions.",
            "1. Choose a servo. {A} = use it. {Y} = full range",
            "   (only if Robot.java has .fullRange()).",
            "2. Set a number near where it sits now.",
            "   Not sure? Take its arm off. Use 0.500.",
            "   {A} = the servo jumps there.",
            "3. D-pad up/down = one step. Left/right = size.",
            "4. One end, before it pushes: {X}. Other: {B}.",
            "   Then it stays between them.",
            "   Hold {LB} = go past a saved end (careful).",
            "5. Each position you use: {A}. {Y} = undo.",
            "{BACK} = another servo.",
            "Two servos: alsoTrim is not used in this tool.",
        };
    }

    // A servo has no "off" that is safe for every mechanism, so it stays where it is.
    @Override protected void stopAll() { }
}
