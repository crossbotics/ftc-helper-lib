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
 * <p>It shows the code for {@code PositionMotor} and, for the robot in five files, the line for
 * {@code Tunables.java}: {@code Tunables.Lift: min = 0, max = 2580, P1 = 1200} (rename P1 to low...).
 *
 * <pre>
 * Choose:  D-pad up/down = motor, A = use it (its count starts at 0 HERE: start at the bottom)
 * Move:    right stick Y = move (at most "max power"), D-pad up/down = max power +/- 0.05 (0.1 to 0.6)
 *          X = mark the LOW end, B = mark the HIGH end. Once both are marked, it will not move past
 *          them (hold LB to move past anyway, e.g. to mark again)
 *          A = save this as a position (P1, P2, ...), Y = undo the last one
 *          hold RT = speed test: full power toward the HIGH end, stops before it (needs both ends)
 *          BACK = choose another motor
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * In your TeamCode: {@code @TeleOp(name = "Motor Finder", group = "Finders")
 * public class MotorFinder extends MotorFinderOpMode { }}
 *
 * Docs: docs/modules/finders.md. Guide: docs/guides/finders.md
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

    @Override protected String finderName() { return "Motor finder"; }

    @Override
    protected void setup() {
        chooser = new Chooser(new ArrayList<>(hardwareMap.getAllNames(DcMotorEx.class)));
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.text("Motor finder: finds a mechanism's limits, positions and top speed.");
        t.text("Put the mechanism at its BOTTOM (or start) before you choose it. Press START.");
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
         .bindPress(Pad.G1, Btn.X, () -> { if (motor != null) { lowEnd = ticks(); writeResults(); } })
         .bindPress(Pad.G1, Btn.B, () -> { if (motor != null) { highEnd = ticks(); writeResults(); } })
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
    }

    // BACK: stop this motor and go back to the list.
    private void release() {
        if (motor != null) motor.setPower(0);
        motor = null;
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
                note = "Mark both ends first (X and B).";
                power = 0;
            } else {
                double stopAt = highEnd - (highEnd - lowEnd) * SPEED_TEST_MARGIN;
                boolean before = sign > 0 ? ticks() < stopAt : ticks() > stopAt;
                power = before ? sign : 0;
                topSpeed = Math.max(topSpeed, Math.abs(motor.getVelocity()));
                note = before ? "Speed test..." : "Speed test done. Bring it back down with the stick.";
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
        String line = "PositionMotor.create(hardwareMap, \"" + motorName + "\")";
        boolean reversed = false;
        if (bothEnds()) {
            double[] lim = FinderMath.limits(lowEnd, highEnd);
            reversed = lim[2] == 1;
            if (reversed) line += ".reversed()";
            line += ".limits(" + FinderResults.num(lim[0], 0) + ", " + FinderResults.num(lim[1], 0) + ")";
        }
        results.set(key + ":0", line);
        int n = 1;
        for (Map.Entry<String, Integer> p : positions.entrySet()) {
            int value = reversed ? 0 - p.getValue() : p.getValue();
            results.set(key + ":" + n++, "    .position(\"" + p.getKey() + "\", " + value + ")");
        }
        if (topSpeed > 0) {
            results.set(key + ":speed", "    // top speed up: " + FinderResults.num(topSpeed, 0) + " counts/s");
        }
        // The same numbers for the robot in five files (Tunables.java): min, max and the positions.
        List<String> fields = new ArrayList<>();
        if (bothEnds()) {
            double[] lim = FinderMath.limits(lowEnd, highEnd);
            fields.add("min = " + FinderResults.num(lim[0], 0));
            fields.add("max = " + FinderResults.num(lim[1], 0));
        }
        for (Map.Entry<String, Integer> p : positions.entrySet()) {
            fields.add(p.getKey() + " = " + (reversed ? 0 - p.getValue() : p.getValue()));
        }
        if (fields.isEmpty()) return;
        results.set(key + ":t0", FinderResults.tunablesLine(motorName, fields));
        results.set(key + ":t1", "    // encoder counts: right for a Lift; an Arm's numbers are degrees");
        if (reversed) results.set(key + ":t2", "Robot.java: add .reversed() to the \"" + motorName + "\" part");
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (motor == null) {
            t.section("Choose a motor (D-pad, then A)");
            chooser.addTelemetry(t, "No motors in the robot configuration.");
            return;
        }
        t.section("Motor \"" + motorName + "\"  (BACK = another)");
        t.line("Count", ticks());
        t.line("Speed", FinderResults.num(motor.getVelocity(), 0) + " counts/s");
        t.line("Current", FinderResults.num(motor.getCurrent(CurrentUnit.AMPS), 1) + " A");
        t.line("Max power", FinderResults.num(maxPower, 2) + " (D-pad up/down)");
        t.line("LOW end (X)", Double.isNaN(lowEnd) ? "-" : FinderResults.num(lowEnd, 0));
        t.line("HIGH end (B)", Double.isNaN(highEnd) ? "-" : FinderResults.num(highEnd, 0));
        if (topSpeed > 0) t.line("Top speed", FinderResults.num(topSpeed, 0) + " counts/s");
        if (!note.isEmpty()) t.text(note);
        t.text("Stick = move, A = save position, Y = undo, hold RT = speed test");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds limits, positions, top speed (lift/arm...).",
            "Before: robot on a block, mechanism at its BOTTOM.",
            "1. D-pad up/down = choose a motor, A = use it",
            "   (its count starts at 0 here).",
            "2. Right stick = move slowly. D-pad up/down =",
            "   max power (0.1 to 0.6, starts at 0.3).",
            "3. Just above the bottom: X = LOW end. Just below",
            "   the top: B = HIGH end. Then it stops at them",
            "   (hold LB to go past).",
            "4. Each height you need: A (P1, P2...). Y = undo.",
            "5. After X and B: hold RT = speed test (full power",
            "   up, stops before the top).",
            "High amps, not moving = pushing on something: stop.",
        };
    }

    @Override
    protected void stopAll() {
        if (motor != null) motor.setPower(0);
    }
}
