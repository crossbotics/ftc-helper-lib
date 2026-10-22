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
 * its two hard stops ({@code posA}, {@code posB}), and if you measure them, the hood's angle there
 * ({@code angleA}, {@code angleB}) and at points in between (a {@code calibration} table, for a linkage
 * that is not a straight line). It works before the hood has any numbers in Tunables.java.
 *
 * <pre>
 * A servo hood:
 *   Start:  D-pad = choose a start point (the servo does NOT move yet), A = move it there.
 *   Nudge:  D-pad right/left = +/- 0.01, up/down = +/- 0.05, hold RB = 10 times finer.
 * A motor hood: nothing is powered. Move the hood by hand; the screen shows the encoder (0 = where it
 *   was at INIT, so start with the hood on the stop you want as 0, the same as in a match).
 * Both:
 *   X = this is stop A (posA)          B = this is stop B (posB)    (a hair off each stop)
 *   hold LB + D-pad = the angle you measured here (up/down +/- 1, right/left +/- 0.1)
 *   A = save a calibration point here (position, angle)    Y = undo the last point
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * <p>It shows the lines for {@code Tunables.java}: {@code Tunables.Hood: posA = 0.205, posB = 0.648},
 * then {@code angleA = 0, angleB = 25} once you gave the angles at both stops, and a
 * {@code calibration} line with 3 or more points. Copy them into {@code Tunables.java} (the robot uses
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
 * Docs: docs/modules/finders.md. Guide: docs/guides/finders.md
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
    private double angle = 0;               // the angle the driver typed in (hold LB + D-pad)
    private boolean angleGiven = false;
    private double posA = Double.NaN, posB = Double.NaN, angleA = Double.NaN, angleB = Double.NaN;
    private final List<double[]> points = new ArrayList<>();   // {position, angle}
    private String note = "";

    @Override protected String finderName() { return "Hood finder"; }

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
            // 0 = where the hood is now (as in a match), power 0, free to move by hand.
            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
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
        t.text("Hood finder: the hood's two stops (and its angles) for Tunables." + section + ".");
        t.text(motor != null ? "Motor hood: nothing is powered; move the hood by hand. Start it on the stop you call 0."
                : "Servo hood: nothing moves until you choose a start point and press A.");
        if (pulseUs != null) t.text(String.format(java.util.Locale.US, "Full range (Robot.java .fullRange()): %.0f-%.0f us, as in a match.", pulseUs[0], pulseUs[1]));
        t.text("Press START.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> dpad(0.05, 1))
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> dpad(-0.05, -1))
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> dpad(0.01, 0.1))
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> dpad(-0.01, -0.1))
         .bindPress(Pad.G1, Btn.A, () -> {
                    if (servo != null && Double.isNaN(position)) moveToStart();
                    else savePoint();
                })
         .bindPress(Pad.G1, Btn.X, () -> markStop(true))
         .bindPress(Pad.G1, Btn.B, () -> markStop(false))
         .bindPress(Pad.G1, Btn.Y, this::undo);
    }

    // The D-pad: with LB held, the angle typed in; else the servo nudge (or its start point).
    private void dpad(double servoStep, double angleStep) {
        if (gamepad1.left_bumper) {
            angle = Math.round((angle + angleStep) * 10) / 10.0;
            angleGiven = true;
            return;
        }
        if (servo == null) return;
        double step = gamepad1.right_bumper ? servoStep / 10 : servoStep;
        if (Double.isNaN(position)) {
            startAt = FinderMath.servoPosition(startAt + step);
            return;
        }
        position = FinderMath.servoPosition(position + step);
        servo.setPosition(position);
    }

    private void moveToStart() {
        position = startAt;
        servo.setPosition(position);
    }

    // The hood's position now: the servo position sent, or the encoder.
    private double here() {
        return motor != null ? motor.getCurrentPosition() : position;
    }

    private boolean known() {
        return !Double.isNaN(here());
    }

    private void markStop(boolean a) {
        if (!known()) return;
        if (a) {
            posA = here();
            angleA = angleGiven ? angle : Double.NaN;
        } else {
            posB = here();
            angleB = angleGiven ? angle : Double.NaN;
        }
        note = "Stop " + (a ? "A" : "B") + " = " + text(here()) + (angleGiven ? " at " + FinderResults.num(angle, 1) : "");
        writeResults();
    }

    private void savePoint() {
        if (!known()) return;
        if (!angleGiven) {
            note = "Measure the hood's angle here first: hold LB and use the D-pad.";
            return;
        }
        points.add(new double[] {here(), angle});
        note = "Point " + points.size() + ": " + text(here()) + " = " + FinderResults.num(angle, 1);
        writeResults();
    }

    private void undo() {
        if (points.isEmpty()) return;
        points.remove(points.size() - 1);
        writeResults();
    }

    // A position as text: a servo position "0.205", or ticks "350".
    private String text(double v) {
        return motor != null ? FinderResults.num(v, 0) : FinderResults.num(v, 3);
    }

    // The lines for Tunables.java.
    private void writeResults() {
        results.removeStartingWith("hood:");
        boolean stops = !Double.isNaN(posA) && !Double.isNaN(posB);
        if (stops) {
            List<String> f = new ArrayList<>();
            f.add("posA = " + text(posA));
            f.add("posB = " + text(posB));
            if (!Double.isNaN(angleA) && !Double.isNaN(angleB)) {
                f.add("angleA = " + ShotPoints.hoodText(angleA));
                f.add("angleB = " + ShotPoints.hoodText(angleB));
            }
            results.set("hood:0", "Tunables." + section + ": " + FinderResults.commas(f));
        }
        // A calibration table: the stops (with their angles) and the points, by position.
        List<double[]> rows = new ArrayList<>(points);
        if (stops && !Double.isNaN(angleA) && !Double.isNaN(angleB)) {
            rows.add(new double[] {posA, angleA});
            rows.add(new double[] {posB, angleB});
        }
        if (rows.size() >= 3) {
            java.util.Collections.sort(rows, (x, y) -> Double.compare(x[0], y[0]));
            List<String> r = new ArrayList<>();
            for (double[] p : rows) r.add("{" + text(p[0]) + ", " + ShotPoints.hoodText(p[1]) + "}");
            results.set("hood:1", "public static double[][] calibration = {" + FinderResults.commas(r) + "};   // in Tunables."
                    + section + " (instead of posA/posB: for a linkage)");
        }
        if (stops || rows.size() >= 3) {
            results.set("hood:2", "// Copy into Tunables.java: the robot uses the file, not this screen.");
        }
    }

    @Override
    protected void step() { }

    @Override
    protected void show(RobotTelemetry t) {
        t.section("Hood \"" + device + "\" (" + (motor != null ? "motor" : "servo") + ")");
        if (servo != null && Double.isNaN(position)) {
            t.line("Start point", FinderResults.num(startAt, 3) + "  (D-pad; A = move the servo there)");
            t.text("Choose a start point near where the servo sits now. Nothing moves until A.");
            return;
        }
        t.line("Position", text(here()) + (motor != null ? " ticks (move the hood by hand)" : ""));
        t.line("Angle here", angleGiven ? FinderResults.num(angle, 1) + " (hold LB + D-pad)" : "- (hold LB + D-pad to give one)");
        t.line("Stop A", Double.isNaN(posA) ? "- (X)" : text(posA) + (Double.isNaN(angleA) ? "" : " at " + FinderResults.num(angleA, 1)));
        t.line("Stop B", Double.isNaN(posB) ? "- (B)" : text(posB) + (Double.isNaN(angleB) ? "" : " at " + FinderResults.num(angleB, 1)));
        t.line("Points", points.size() + " (A saves one, Y undoes)");
        if (servo != null) t.text("A straining servo hums: back off a little from each stop before X / B.");
        if (!note.isEmpty()) t.text(note);
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds the hood's two stops and its angles.",
            "Servo: D-pad = a start point, A = move there;",
            "  then D-pad nudges (RB = 10x finer).",
            "Motor: unpowered, move the hood by hand.",
            "Go to one stop, back off a hair: X = stop A.",
            "The other stop: B = stop B.",
            "Angles (optional): hold LB + D-pad = the angle",
            "  you measured here, before X / B / A.",
            "A = save a point (position, angle), Y = undo.",
            "3+ points: a calibration table (a linkage).",
            "Copy the lines into Tunables.java.",
        };
    }

    // A servo hood stays where it is; a motor hood is never powered.
    @Override
    protected void stopAll() {
        if (motor != null) motor.setPower(0);
    }
}
