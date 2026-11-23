package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.robot.Hood;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds a launcher hood's calibration for {@code Tunables.Hood}: the servo position (or encoder ticks) at
 * its two ends ({@code posA}, {@code posB}), and if you measure them, the hood's angle there
 * ({@code angleA}, {@code angleB}) and at points in between (a {@code calibration} table, for a linkage
 * that is not a straight line). It works before the hood has any numbers in Tunables.java.
 *
 * <pre>
 * A servo hood:
 *   Start:  D-pad up/down = choose a start point (the servo does NOT move yet), A = move it there.
 *   Nudge:  D-pad up/down = move by the step, D-pad left/right = smaller/bigger step
 *           (0.1, 0.05, 0.01, 0.005, 0.001; it starts at 0.05).
 * A motor hood: nothing is powered. Move the hood by hand to the end that is 0 in a match, then A = "this
 *   is 0". Nothing can be saved before that.
 * Both:
 *   X = this is end A (posA)           B = this is end B (posB)     (a little off each end)
 *   hold LB + D-pad = the angle you measured here (up/down +/- 1, right/left +/- 0.1)
 *   A = save a calibration point here (position, angle)    Y = undo the last point
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * <p>It shows the lines for {@code Tunables.java}, class Hood: {@code public static double posA = 0.205;} and posB,
 * then {@code angleA = 0, angleB = 25} once you gave the angles at both stops; OR, once you saved points
 * between the stops (and gave the angles at both stops), a {@code calibration} line INSTEAD (the Hood
 * reads one or the other). Each angle you give is used for one place (a stop or a point): measure the
 * next place before marking it. A table whose angles do not keep going one way is not shown (the Hood
 * would stop INIT): the screen says which point to measure again. Copy them into {@code Tunables.java} (the robot uses
 * the file, not this screen; a change made in the tuning menu is also lost at the next restart unless
 * you copy it back). The lines are saved on the Control Hub at STOP.
 *
 * <p>In your TeamCode, the robot in five files (it reads the hood's name, servo or motor, and
 * reversed() from Robot.java without needing its Tunables numbers):
 * <pre>{@code
 * @TeleOp(name = "Hood Finder", group = "Finders")
 * public class HoodFinder extends HoodFinderOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 * Without one, it uses the servo or motor called {@link #hoodName()} ("hood").
 *
 * Docs: docs/modules/finders.md. Guide: docs/tuning/README.md
 */
public abstract class HoodFinderOpMode extends FinderOpMode {

    /** Your robot in five files ({@code return new Robot();}): its hood is used. Default null: {@link #hoodName()}. */
    protected RobotDefinition robot() {
        return null;
    }

    /** The hood's configuration name when there is no {@link #robot()}. Default "hood". */
    protected String hoodName() {
        return "hood";
    }

    private String device, section;
    private boolean reversed = false;
    private double[] pulseUs = null;   // the hood's .fullRange() pulse, or null (the SDK's normal range)
    private Servo servo;        // a servo hood, or null
    private DcMotorEx motor;    // a motor hood, or null
    private double position = Double.NaN;   // servo: NaN = not moved yet
    private double startAt = 0.5;
    private final NudgeStep step = new NudgeStep();   // D-pad left/right changes it (servo hood)
    private double angle = 0;               // the angle the driver typed in (hold LB + D-pad)
    private boolean angleGiven = false;
    private double posA = Double.NaN, posB = Double.NaN, angleA = Double.NaN, angleB = Double.NaN;
    private final List<double[]> points = new ArrayList<>();   // {position, angle}
    private String note = "";
    private boolean zeroed = false;   // a motor hood: A said "this is 0" (the count means nothing before)

    @Override protected String finderName() { return "Hood finder"; }

    @Override protected String stoppedWords() {
        return motor != null ? "The hood's motor power is off." : "A servo may still move to its last position.";
    }

    @Override
    protected void setup() {
        RobotDefinition r = robot();
        Hood hood = null;
        if (r != null) {
            r.describe();
            for (Module<?> m : r.modules()) {
                if (m instanceof Hood) hood = (Hood) m;
                if (m instanceof Launcher && ((Launcher) m).hood() != null) hood = ((Launcher) m).hood();
            }
            if (hood == null) throw new IllegalStateException("Hood finder: Robot.java has no hood (Launcher...hood(Hood.servo(\"hood\")))");
            // A hood marked optional() that is not in the configuration yet: say so (not "no Servo named ...").
            if (hood.isOptional() && hardwareMap.tryGet(com.qualcomm.robotcore.hardware.HardwareDevice.class, hood.deviceName()) == null) {
                throw new IllegalStateException("Hood finder: \"" + hood.deviceName() + "\" is not in the robot configuration ("
                        + hood.name() + " is optional in Robot.java, so it is off). Add it to the configuration first.");
            }
        }
        boolean isMotor;
        if (hood != null) {
            device = hood.deviceName();
            section = hood.name();
            reversed = hood.isReversed();
            isMotor = hood.isMotor();
            pulseUs = isMotor ? null : hood.pulseRangeUs();
        } else {
            device = hoodName();
            section = RobotDefinition.sectionName(device);
            isMotor = !hardwareMap.getAllNames(Servo.class).contains(device);
        }
        if (isMotor) {
            motor = hardwareMap.get(DcMotorEx.class, device);
            motor.setDirection(reversed ? DcMotor.Direction.REVERSE : DcMotor.Direction.FORWARD);
            // Power 0, free to move by hand. The count becomes 0 when the driver says so (setZero), not now:
            // the hood may not be at its 0 yet.
            motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
            motor.setPower(0);
        } else {
            servo = hardwareMap.get(Servo.class, device);
            if (reversed) servo.setDirection(Servo.Direction.REVERSE);
            // The same pulse range as in a match (Hood.servo(...).fullRange()), so the stops it finds fit.
            if (pulseUs != null) com.crossbotics.ftchelper.hardware.ServoSetup.setPulseRange(servo, pulseUs[0], pulseUs[1]);
        }
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        if (motor != null) {
            t.warn("You move the hood by hand. Hands off the gears.");
        } else {
            t.warn("Hands clear of the hood.");
            t.warn("The servo can JUMP when you press {A}.");
        }
        t.text("Hood finder: finds the hood's two ends.");
        t.text("(The hood sets how steep a shot is.)");
        t.text(motor != null ? "Its motor gets no power in this tool." : "Nothing moves until you press {A}.");
        t.text("Its numbers go in Tunables.java, class " + section + ".");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> dpad(1, 1))
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> dpad(-1, -1))
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> dpad(0, 0.1))
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> dpad(0, -0.1))
         .bindPress(Pad.G1, Btn.A, () -> {
                    if (motor != null && !zeroed) setZero();
                    else if (servo != null && Double.isNaN(position)) moveToStart();
                    else savePoint();
                })
         .bindPress(Pad.G1, Btn.X, () -> markStop(true))
         .bindPress(Pad.G1, Btn.B, () -> markStop(false))
         .bindPress(Pad.G1, Btn.Y, this::undo);
    }

    // The D-pad: with LB held, the angle typed in; else up/down moves the servo one step (or its start
    // point) and left/right (direction 0) makes the step smaller/bigger.
    private void dpad(int direction, double angleStep) {
        if (gamepad1.left_bumper) {
            angle = Math.round((angle + angleStep) * 10) / 10.0;
            angleGiven = true;
            return;
        }
        if (servo == null) return;
        if (direction == 0) {
            if (angleStep > 0) step.bigger(); else step.smaller();
            return;
        }
        if (Double.isNaN(position)) {
            startAt = step.move(startAt, direction);
            return;
        }
        position = step.move(position, direction);
        servo.setPosition(position);
    }

    private void moveToStart() {
        position = startAt;
        servo.setPosition(position);
    }

    // A, a motor hood's first press: where the hood is now is 0, as in a match.
    private void setZero() {
        motor.setPower(0);
        motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        zeroed = true;
        note = "";
    }

    // The hood's position now: the servo position sent, or the encoder.
    private double here() {
        return motor != null ? motor.getCurrentPosition() : position;
    }

    private boolean known() {
        if (motor != null && !zeroed) return false;
        return !Double.isNaN(here());
    }

    private void markStop(boolean a) {
        if (!known()) return;
        // Each angle belongs to the place it was measured: it is used once, then must be given again
        // (else stop B would get stop A's angle when nobody measured it there).
        if (a) {
            posA = here();
            angleA = angleGiven ? angle : Double.NaN;
        } else {
            posB = here();
            angleB = angleGiven ? angle : Double.NaN;
        }
        note = "End " + (a ? "A" : "B") + " saved: " + text(here()) + (angleGiven ? ", " + FinderResults.num(angle, 1) + " deg" : "");
        angleGiven = false;
        writeResults();
    }

    private void savePoint() {
        if (!known()) return;
        if (!angleGiven) {
            note = "Measure the hood's angle here first.\nThen hold {LB} and use the D-pad to enter it.";
            return;
        }
        points.add(new double[] {here(), angle});
        note = "Point " + points.size() + ": " + text(here()) + ", " + FinderResults.num(angle, 1) + " deg";
        angleGiven = false;   // the next place needs its own measured angle
        writeResults();
    }

    private void undo() {
        if (points.isEmpty()) {
            note = "No points to undo.\nA wrong end: mark it again with {X} or {B}.";
            return;
        }
        points.remove(points.size() - 1);
        note = "Point " + (points.size() + 1) + " removed: " + points.size() + " left.";
        writeResults();
    }

    // A position as text: a servo position "0.205", or ticks "350".
    private String text(double v) {
        return motor != null ? FinderResults.num(v, 0) : FinderResults.num(v, 3);
    }

    // What the lines say now, for the screen (why a line is missing).
    private String problem = "";

    // The lines for Tunables.java: posA/posB (+ angleA/angleB), OR a calibration table (the Hood reads one or
    // the other: both stop INIT). The table needs both stops with their angles (its first and last rows are
    // the stops) and its angles must keep going one way along the positions, as the Hood checks at INIT.
    private void writeResults() {
        results.removeStartingWith("hood:");
        problem = "";
        boolean stops = !Double.isNaN(posA) && !Double.isNaN(posB);
        boolean angles = !Double.isNaN(angleA) && !Double.isNaN(angleB);
        if (stops && Double.compare(posA, posB) == 0) {
            problem = "End A and end B are at the same place.\nGo to the other end and mark it again.";
            results.notDone("mark the other end.");
            return;
        }
        String table = null;
        if (!points.isEmpty()) {
            if (!stops || !angles) {
                problem = "A table needs the angle at BOTH ends.\nGo to each end, enter it, then {X} or {B}.";
            } else {
                List<double[]> rows = new ArrayList<>(points);
                rows.add(new double[] {posA, angleA});
                rows.add(new double[] {posB, angleB});
                java.util.Collections.sort(rows, (x, y) -> Double.compare(x[0], y[0]));
                problem = tableProblem(rows);
                if (problem.isEmpty()) {
                    List<String> r = new ArrayList<>();
                    for (double[] p : rows) r.add("{" + text(p[0]) + ", " + ShotPoints.hoodText(p[1]) + "}");
                    table = "{" + FinderResults.commas(r) + "}";
                }
            }
        } else if (stops && (Double.isNaN(angleA) != Double.isNaN(angleB))) {
            problem = "Angle at end " + (Double.isNaN(angleA) ? "A" : "B") + " missing. Go there, enter it,\nthen press "
                    + (Double.isNaN(angleA) ? "{X}" : "{B}") + " again. (Or use no angles.)";
        }
        if (!stops) results.notDone("mark both ends ({X} and {B}).");
        else if (!problem.isEmpty()) results.notDone("fix the problem shown at the top.");
        else results.done();
        if (table != null) {
            // The table's first and last rows are the ends: the Hood reads the table OR posA and posB.
            results.setTunable("hood:1", section, "calibration", table);
            results.set("hood:2", "Delete posA, posB, angleA, angleB: the table has them.");
        } else if (stops) {
            results.setTunable("hood:a", section, "posA", text(posA));
            results.setTunable("hood:b", section, "posB", text(posB));
            if (angles) {
                results.setTunable("hood:aa", section, "angleA", ShotPoints.hoodText(angleA));
                results.setTunable("hood:ab", section, "angleB", ShotPoints.hoodText(angleB));
            }
        }
    }

    // A calibration table the Hood would refuse at INIT (sorted by position): two rows at one place, or
    // angles that do not keep going the same way. "" = fine.
    static String tableProblem(List<double[]> sorted) {
        int dir = 0;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i)[0] == sorted.get(i - 1)[0]) return "Two points at the same place.\n{Y} takes back the last one.";
            double d = sorted.get(i)[1] - sorted.get(i - 1)[1];
            int now = d > 0 ? 1 : d < 0 ? -1 : 0;
            if (now == 0 || (dir != 0 && now != dir)) {
                return "The angles must keep going one way.\nNow: " + FinderResults.num(sorted.get(i - 1)[1], 1) + ", then "
                        + FinderResults.num(sorted.get(i)[1], 1) + " deg. {Y}, then measure again.";
            }
            dir = now;
        }
        return "";
    }

    @Override
    protected void step() { }

    @Override
    protected void show(RobotTelemetry t) {
        if (motor != null && !zeroed) {
            t.warn("The hood's motor has no power here.");
            t.section("Hood \"" + device + "\": set its 0");
            t.text("Next: move the hood by hand to the end");
            t.text("where it starts each match. That end is 0.");
            t.text("Then press {A}. Nothing is saved before that.");
            return;
        }
        if (servo != null && Double.isNaN(position)) {
            t.warn("{A} = the servo JUMPS to this number.");
            t.warn("Hands clear. Nothing moves until {A}.");
            t.section("Hood \"" + device + "\": first move");
            t.text("Next: set a number near where it sits now.");
            t.text("0 and 1 are its two ends. 0.5 is the middle.");
            t.line("Start point", FinderResults.num(startAt, 3));
            t.line("Step", step.label() + "  (D-pad left/right)");
            t.text("Not sure? Take the servo's arm off. Use 0.500.");
            return;
        }
        t.warn(motor != null ? "Move the hood by hand. It has no power." : "Every step moves the hood. Hands clear.");
        t.section("Hood \"" + device + "\": its two ends");
        if (Double.isNaN(posA)) {
            t.text("Next: go to one end, then back a little.");
            t.text("{X} = save it as end A.");
        } else if (Double.isNaN(posB)) {
            t.text("Next: go to the other end, then back a little.");
            t.text("{B} = save it as end B.");
        } else if (!problem.isEmpty()) {
            t.text("Next: fix the problem at the top.");
        } else {
            t.text("Both ends are saved. Angles are optional.");
        }
        t.line("Position", text(here()) + (motor != null ? " counts" : "  (0 to 1)"));
        if (servo != null) t.line("Step", step.label() + "  (D-pad left/right)");
        t.line("End A", Double.isNaN(posA) ? "not saved" : text(posA) + (Double.isNaN(angleA) ? "" : ", " + FinderResults.num(angleA, 1) + " deg"));
        t.line("End B", Double.isNaN(posB) ? "not saved" : text(posB) + (Double.isNaN(angleB) ? "" : ", " + FinderResults.num(angleB, 1) + " deg"));
        t.line("Angle here", angleGiven ? FinderResults.num(angle, 1) + " deg" : "not set (optional)");
        t.text("Enter an angle: hold {LB}, use the D-pad.");
        if (!points.isEmpty()) t.line("Points between", points.size() + "  ({Y} takes one back)");
        textLines(t, note);
        warnLines(t, problem);
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds the hood's two ends.",
            "Motor: move it by hand to its 0. {A} = set 0.",
            "Servo: set a number near where it is. {A} = go.",
            "  Then D-pad up/down = step, left/right = size.",
            "Back off a little at each end (a servo hums).",
            "{X} = end A. {B} = end B.",
            "Angles (optional): measure with a phone angle",
            "  app on the hood. Hold {LB}: D-pad up/down",
            "  = 1 deg, right/left = 0.1 deg. Then save.",
            "A hood on a linkage: save points between the",
            "  ends too: {A}. {Y} takes back a point.",
            "A wrong end: mark it again ({Y} cannot undo it).",
        };
    }

    // A servo hood stays where it is; a motor hood is never powered.
    @Override
    protected void stopAll() {
        if (motor != null) motor.setPower(0);
    }
}
