package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;

/**
 * Motor Direction Check: spins ONE drive motor at a time, set up exactly as Robot.java says (its
 * direction, from {@code reversed(...)}), so you see if each wheel rolls FORWARD. A wrong direction makes the
 * robot drive in circles, strafe the wrong way, or fight itself. Run it on every new robot and after any
 * motor or wire is changed. <b>Put the robot on a block first</b>: the wheels spin.
 *
 * <p>Hold X = front-left (tank: left), Y = front-right (tank: right), A = back-left, B = back-right: that
 * motor runs at 30% while held. Each wheel must roll the way that drives the robot forward. A wheel that rolls
 * backward: add its name to {@code reversed(...)} on the drive line in Robot.java (or take it out).
 *
 * <p>It is in "Tune and check" for every robot in five files with a drive. On its own, in TeamCode:
 * <pre>{@code
 * @TeleOp(name = "Motor Direction Check", group = "Finders")
 * public class MotorDirectionCheck extends MotorDirectionCheckOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 *
 * Docs: docs/modules/finders.md. Guide: docs/guides/finders.md
 */
public abstract class MotorDirectionCheckOpMode extends FinderOpMode {

    /** Your robot in five files ({@code return new Robot();}): its drive is checked. */
    protected abstract RobotDefinition robot();

    // Power while a button is held: enough to see the wheel turn, slow enough to be safe on a block.
    private static final double POWER = 0.3;

    private final DcMotorEx[] motors = new DcMotorEx[4];   // FL, FR, BL, BR (a 2-motor tank: no back motors)
    private final String[] names = new String[4];
    private final boolean[] reversedNow = new boolean[4];
    private final boolean[] held = new boolean[4];
    private boolean tank = false;

    @Override protected String finderName() { return "Motor direction check"; }

    @Override
    protected void setup() {
        RobotDefinition r = robot();
        if (r == null) throw new IllegalStateException("Motor direction check: it needs your Robot.java (robot() returns null)");
        DriveConfig cfg = r.robotConfig().drive();
        cfg.validate();   // stops here with a plain message if a drive setting is wrong
        tank = cfg.type != DriveConfig.DriveType.MECANUM;
        String[] n = {cfg.frontLeft, cfg.frontRight, cfg.backLeft, cfg.backRight};
        boolean[] rev = {cfg.frontLeftReversed, cfg.frontRightReversed, cfg.backLeftReversed, cfg.backRightReversed};
        for (int i = 0; i < 4; i++) {
            if (i >= 2 && !cfg.hasBackMotors()) continue;
            names[i] = n[i];
            reversedNow[i] = rev[i];
            // Set up as the drive does: Robot.java's direction, brake, plain power, power 0 (nothing moves in INIT).
            motors[i] = MotorSetup.get(hardwareMap, n[i]).reversed(rev[i]).brake().runWithoutEncoder().done();
        }
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.warn("Wheels off the ground! Put the robot on a block.");
        t.text("Then press START. Hold a button to spin one motor.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        Btn[] btn = {Btn.X, Btn.Y, Btn.A, Btn.B};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            c.bindPress(Pad.G1, btn[i], () -> held[k] = true).bindRelease(Pad.G1, btn[i], () -> held[k] = false);
        }
    }

    @Override
    protected void step() {
        // One motor at a time: two buttons held = none runs (so each wheel is seen alone).
        int count = 0;
        for (int i = 0; i < 4; i++) if (held[i] && motors[i] != null) count++;
        for (int i = 0; i < 4; i++) if (motors[i] != null) motors[i].setPower(count == 1 && held[i] ? POWER : 0);
    }

    @Override
    protected void show(RobotTelemetry t) {
        t.section("Hold a button: that wheel must roll FORWARD");
        String[] key = tank ? new String[] {"X left", "Y right", "A", "B"} : new String[] {"X front-left", "Y front-right", "A back-left", "B back-right"};
        for (int i = 0; i < 4; i++) {
            if (motors[i] == null) continue;
            t.line(key[i], "\"" + names[i] + "\"" + (reversedNow[i] ? " (reversed)" : "") + (held[i] && heldCount() == 1 ? "  RUNNING" : ""));
        }
        if (heldCount() > 1) t.warn("One button at a time");
        t.text("Backward? Add its name to reversed(...) on the drive line in Robot.java (or take it out).");
    }

    private int heldCount() {
        int n = 0;
        for (int i = 0; i < 4; i++) if (held[i] && motors[i] != null) n++;
        return n;
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Checks that each drive wheel rolls forward.",
            "1. Robot on a block: the wheels spin.",
            "2. Hold X, Y, A or B: one motor runs at 30%.",
            "   X = front-left, Y = front-right,",
            "   A = back-left, B = back-right (tank: X, Y).",
            "3. Each wheel must roll the way that drives",
            "   the robot FORWARD.",
            "4. One backward: add its name to reversed(...)",
            "   in Robot.java's drive line (or take it out).",
            "Run it again after any motor or wire change.",
        };
    }

    @Override
    protected void stopAll() {
        // Every motor, even if stopping one fails: then the first error is reported.
        RuntimeException first = null;
        for (DcMotorEx m : motors) {
            if (m == null) continue;
            try { m.setPower(0); } catch (RuntimeException e) { if (first == null) first = e; }
        }
        if (first != null) throw first;
    }
}
