package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;

/**
 * Finds a motor mechanism's numbers (a lift, an arm, a turret): its limits in encoder counts, the
 * positions you want, and its top speed. Move it slowly with the stick, mark the ends and positions,
 * and copy the code lines it shows. No code to write: it lists every motor in the robot configuration.
 *
 * <p>In Tune and check, for a robot in five files, it shows the lines for {@code Tunables.java}:
 * {@code public static double max = 2580;} in class Lift (P1 stands for your name, e.g. low). An arm or a
 * turret uses degrees: it shows the counts and says not to copy them. On its own it shows the code for
 * {@code PositionMotor}.
 *
 * <pre>
 * Choose:  D-pad up/down = motor, A = use it (its count starts at 0 HERE: start at the bottom)
 * Move:    right stick Y = move (at most "max power"), D-pad up/down = max power +/- 0.05 (0.1 to 0.6)
 *          X = mark the LOW end, B = mark the HIGH end. Once both are marked, the power goes off at
 *          them (it coasts a little past, more at a higher max power; hold LB to move past anyway, e.g.
 *          to mark again). A position saved outside the ends gets a warning.
 *          A = save this as a position (P1, P2, ...), Y = undo the last one
 *          hold RT = speed test: full power toward the HIGH end, stops before it (needs both ends)
 *          BACK = choose another motor
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * In your TeamCode: {@code @TeleOp(name = "Motor Finder", group = "Finders")
 * public class MotorFinder extends MotorFinderOpMode { }}
 *
 * Docs: docs/modules/finders.md. Guide: docs/tuning/05-motor-finder.md
 */
public abstract class MotorFinderOpMode extends FinderOpMode {

    /** The speed test stops this share of the range before the HIGH end, so it never hits it. */
    public static final double SPEED_TEST_MARGIN = 0.15;

    private Chooser chooser;
    private DcMotorEx motor;      // null = still choosing
    private String motorName;
    private double maxPower = 0.3;
    private double lowEnd = Double.NaN, highEnd = Double.NaN;
    private final Map<String, Integer> positions = new LinkedHashMap<>();
    private double topSpeed = 0;   // counts per second, the fastest seen in a speed test
    private String note = "";
    // Saved positions outside the two ends (the robot never goes there: positions stay inside min..max).
    private String outside = "";
    private boolean liftFound = false;   // a part in counts has its min and max to copy

    @Override protected String finderName() { return "Motor finder"; }

    @Override
    protected void setup() {
        chooser = new Chooser(new ArrayList<>(hardwareMap.getAllNames(DcMotorEx.class)));
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.warn("Robot on a block. Hands clear of the part.");
        t.warn("It has NO limits yet: it can hit its end.");
        t.warn("Stick = move, let go = stop. {RT} = FULL power.");
        t.text("Motor finder: for a lift or an arm.");
        t.text("Finds how far it may go, and its top speed.");
        t.text("Next: put the part at its BOTTOM now.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> {
                    if (motor == null) chooser.previous(); else maxPower = Math.min(0.6, maxPower + 0.05);
                })
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> {
                    if (motor == null) chooser.next(); else maxPower = Math.max(0.1, maxPower - 0.05);
                })
         .bindPress(Pad.G1, Btn.A, () -> { if (motor == null) choose(); else savePosition(); })
         .bindPress(Pad.G1, Btn.X, () -> { if (motor != null) { lowEnd = ticks(); endMarked(); } })
         .bindPress(Pad.G1, Btn.B, () -> { if (motor != null) { highEnd = ticks(); endMarked(); } })
         .bindPress(Pad.G1, Btn.Y, () -> { if (motor != null) undoPosition(); })
         .bindPress(Pad.G1, Btn.BACK, this::release);
    }

    // A = use the chosen motor: brake, count from 0 here, plain power.
    private void choose() {
        if (chooser.isEmpty()) return;
        motorName = chooser.current();
        motor = hardwareMap.get(DcMotorEx.class, motorName);
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        motor.setPower(0);
        lowEnd = highEnd = Double.NaN;
        positions.clear();
        topSpeed = 0;
        note = "";
        outside = "";
        waitForRelease();   // SAFETY: a stick still held must not move the motor that was just chosen
    }

    // X or B: an end is marked.
    private void endMarked() {
        // SAFETY: RT held while the second end is marked must not start full power by itself.
        if (gamepad1.right_trigger > 0.1) waitForRelease();
        note = "";
        writeResults();
    }

    // BACK: stop this motor and go back to the list.
    private void release() {
        if (motor != null) motor.setPower(0);
        motor = null;
        waitForRelease();
    }

    private int ticks() {
        return motor.getCurrentPosition();
    }

    private void savePosition() {
        positions.put("P" + (positions.size() + 1), ticks());
        writeResults();
    }

    private void undoPosition() {
        if (positions.isEmpty()) return;
        String last = null;
        for (String k : positions.keySet()) last = k;
        positions.remove(last);
        writeResults();
    }

    private boolean bothEnds() {
        return !Double.isNaN(lowEnd) && !Double.isNaN(highEnd) && lowEnd != highEnd;
    }

    @Override
    protected void step() {
        if (motor == null) return;
        // A stick or trigger already pushed after START or help does not count until let go.
        if (!inputsReady()) {
            motor.setPower(0);
            return;
        }
        // "up" = toward the HIGH end. Which way the count goes up there is not known until both are marked.
        double sign = bothEnds() && highEnd < lowEnd ? -1 : 1;
        double power;
        if (gamepad1.right_trigger > 0.5) {
            // Speed test: full power toward the HIGH end, stopping SPEED_TEST_MARGIN before it.
            if (!bothEnds()) {
                note = "Mark both ends first ({X} and {B}).\nThe speed test needs them.";
                power = 0;
            } else {
                double stopAt = highEnd - (highEnd - lowEnd) * SPEED_TEST_MARGIN;
                boolean before = sign > 0 ? ticks() < stopAt : ticks() > stopAt;
                power = before ? sign : 0;
                topSpeed = Math.max(topSpeed, Math.abs(motor.getVelocity()));
                note = before ? "Speed test: FULL power. Let go of {RT} = stop."
                        : "Speed test done. Let go of {RT}.\nThen bring it down with the right stick.";
                writeResults();
            }
        } else {
            power = -gamepad1.right_stick_y * maxPower;   // minus: stick up reads negative
            // Once both ends are marked, stop at them (unless LB is held).
            if (bothEnds() && !gamepad1.left_bumper) {
                double lo = Math.min(lowEnd, highEnd), hi = Math.max(lowEnd, highEnd);
                if (ticks() >= hi && power > 0) power = 0;
                if (ticks() <= lo && power < 0) power = 0;
            }
        }
        motor.setPower(power);
    }

    // The code lines for this motor.
    private void writeResults() {
        String key = "motor:" + motorName;
        results.removeStartingWith(key + ":");
        outside = "";
        if (bothEnds()) {
            double lo = Math.min(lowEnd, highEnd), hi = Math.max(lowEnd, highEnd);
            List<String> past = new ArrayList<>();
            for (Map.Entry<String, Integer> p : positions.entrySet()) {
                if (p.getValue() < lo || p.getValue() > hi) past.add(p.getKey() + " = " + p.getValue());
            }
            if (!past.isEmpty()) {
                outside = FinderResults.commas(past) + ": outside the two ends.\nIt can never get there. {Y} = take it back.";
            }
        }
        boolean degrees = usesDegrees(motorName);
        if (!bothEnds()) results.notDone("mark both ends ({X} and {B}).");
        else if (degrees && !liftFound) results.notDone("this part uses degrees, not counts.");
        else if (!outside.isEmpty()) results.notDone("a saved position is outside the ends.");
        else results.done();

        boolean reversed = false;
        double min = Double.NaN, max = Double.NaN;
        if (bothEnds()) {
            double[] lim = FinderMath.limits(lowEnd, highEnd);
            min = lim[0];
            max = lim[1];
            reversed = lim[2] == 1;
        }
        if (degrees) {
            // An arm's and a turret's Tunables are degrees: counts copied there would be a wrong limit.
            List<String> counts = new ArrayList<>();
            counts.add("LOW end " + count(lowEnd) + ", HIGH end " + count(highEnd));
            for (Map.Entry<String, Integer> p : positions.entrySet()) counts.add(p.getKey() + " " + p.getValue());
            counts.add("Next: ask your mentor for these in degrees.");
            results.setIn(key + ":counts", "Motor counts. Do NOT copy them:", counts.toArray(new String[0]));
            return;
        }
        if (!hasRobotFiles()) {
            List<String> code = new ArrayList<>();
            code.add("PositionMotor.create(hardwareMap, \"" + motorName + "\")" + (reversed ? ".reversed()" : "")
                    + (bothEnds() ? ".limits(" + FinderResults.num(min, 0) + ", " + FinderResults.num(max, 0) + ")" : ""));
            for (Map.Entry<String, Integer> p : positions.entrySet()) {
                code.add("    .position(\"" + p.getKey() + "\", " + (reversed ? 0 - p.getValue() : p.getValue()) + ")");
            }
            if (topSpeed > 0) code.add("    // top speed up: " + FinderResults.num(topSpeed, 0) + " counts/s");
            results.setIn(key + ":0", FinderResults.HELPER_CODE, code.toArray(new String[0]));
            return;
        }
        // Until both ends are known the counts' direction is not known: no line to copy yet.
        if (!bothEnds()) return;
        liftFound = true;
        String section = sectionOf(motorName);
        results.setTunable(key + ":min", section, "min", FinderResults.num(min, 0));
        results.setTunable(key + ":max", section, "max", FinderResults.num(max, 0));
        for (Map.Entry<String, Integer> p : positions.entrySet()) {
            results.setTunable(key + ":" + p.getKey(), section, p.getKey(),
                    String.valueOf(reversed ? 0 - p.getValue() : p.getValue()));
        }
        if (!positions.isEmpty()) results.set(key + ":names", "Write your names for P1, P2 (low, high).");
        if (!knowsPart(motorName)) results.set(key + ":units", "These are counts: for a Lift, never for an Arm.");
        if (reversed) results.setIn(key + ":rev", FinderResults.ROBOT_FILE, "Add .reversed() to the " + motorName + " part.");
    }

    private static String count(double end) {
        return Double.isNaN(end) ? "not saved" : FinderResults.num(end, 0);
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (motor == null) {
            t.warn("Hands clear. Once chosen, the stick moves it.");
            t.section("Motor finder: choose a motor");
            t.text("Next: choose the lift's or arm's motor.");
            t.text("D-pad up/down = choose. {A} = use it.");
            chooser.addTelemetry(t, "No motors in the robot configuration.");
            return;
        }
        t.warn(bothEnds() ? "Hands clear. Let go of the stick = power off." : "No limits yet. Short pushes. Let go = stop.");
        t.section("Motor \"" + motorName + "\"");
        if (Double.isNaN(lowEnd)) {
            t.text("Next: right stick = move just above the bottom.");
            t.text("{X} = save the LOW end.");
        } else if (!bothEnds()) {
            t.text("Next: move to just below the top.");
            t.text("{B} = save the HIGH end.");
        } else {
            t.text("Next: at each height you use: {A} = save it.");
            t.warn("Hold {RT} = FULL power up (speed test).");
        }
        t.line("Motor count", ticks());
        t.line("Current", FinderResults.num(motor.getCurrent(CurrentUnit.AMPS), 1) + " A");
        t.text("Current high and not moving? Stuck: let go.");
        if (results.isEmpty()) t.line("Max power", FinderResults.num(maxPower, 2) + "  (D-pad up/down)");
        t.line("LOW end ({X})", count(lowEnd));
        t.line("HIGH end ({B})", count(highEnd));
        if (topSpeed > 0) t.line("Top speed", FinderResults.num(topSpeed, 0) + " counts/s");
        textLines(t, note);
        warnLines(t, outside);
        t.text("{BACK} = another motor.");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds how far a lift or an arm may go.",
            "Robot on a block. The part at its BOTTOM.",
            "1. Choose its motor. {A} = use it (count 0 here).",
            "2. Right stick = move slowly. Let go = stop.",
            "   D-pad up/down = max power (0.1 to 0.6).",
            "3. Just above the bottom: {X} = LOW end.",
            "   Just below the top: {B} = HIGH end.",
            "   Then power goes off there. It rolls a bit.",
            "   Hold {LB} = go past them (no limit: careful).",
            "4. Each height you use: {A}. {Y} = take one back.",
            "5. Hold {RT} = speed test: FULL power up.",
            "   It stops before the top. Let go = stop.",
            "An Arm or turret uses degrees, not these counts.",
        };
    }

    @Override
    protected void stopAll() {
        if (motor != null) motor.setPower(0);
    }
}
