package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.drive.DriverControls;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.hardware.ImuSetup;
import com.crossbotics.ftchelper.odometry.PinpointOdometry;
import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.FieldPose;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks and measures the odometry of the robot in five files (lesson 8): which way each pod counts,
 * how far it measures, and where the pods sit (the offsets). Then it writes the lines to copy.
 *
 * <pre>
 * The sticks drive the robot slowly (30%) in every test. D-pad up/down = choose a test, A = start, B = done.
 *   Forward:  push or drive the robot 48 in straight forward along the tape. (Directions, distance.)
 *   Left:     push or drive it 24 in straight to its left. (The sideways pod's direction.)
 *   Spin:     turn it in place at least 3 times (right stick). (The offsets / track width.)
 *   Loop:     put it on a tape mark, drive anywhere, come back to the mark. (Checked when it ends within 1 in.)
 * Help: GUIDE (or hold START 1 s).
 * </pre>
 *
 * <p>In your TeamCode, next to Robot.java:
 * <pre>{@code
 * @TeleOp(name = "Odometry Tuner", group = "Finders")
 * public class OdometryTuner extends OdometryTunerOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 *
 * Docs: docs/modules/odometry.md. Guide: docs/guides/finders.md
 */
public abstract class OdometryTunerOpMode extends FinderOpMode {
    // Numbered: the chooser lists them in name order.
    private static final String[] TESTS = {"1 Forward 48 in", "2 Left 24 in", "3 Spin 3+ turns", "4 Loop and back"};

    /** Your robot in five files: {@code return new Robot();} (it must have an odometry(...) line). */
    protected abstract RobotDefinition robot();

    private RobotDefinition robot;
    private Odometry spec;
    private PoseTracker odo;
    private DriverControls driving;
    private IMU imu;
    private final Chooser chooser = new Chooser(java.util.Arrays.asList(TESTS));
    private int running = -1;
    private String lastResult = "";
    // At the start of a test: the pose, the IMU heading added up, and the raw pod counts.
    private double imuTurnDeg, lastImu;
    private double[] podsAtStart, podsNow;
    // The turn since the test began, from the odometry's own heading (added up loop by loop).
    private double odoTurnDeg, lastOdoH;
    private final List<DcMotorEx> pods = new ArrayList<>();

    @Override
    protected String finderName() {
        return "Odometry Tuner";
    }

    @Override
    protected void setup() {
        robot = robot();
        if (robot == null) throw new IllegalStateException("The Odometry Tuner needs your robot: robot() { return new Robot(); }");
        spec = robot.odometrySpec();
        if (spec == null) throw new IllegalStateException("Robot.java has no odometry(...) line (docs/modules/odometry.md)");
        robot.setup(hardwareMap);
        odo = robot.odometry();
        driving = DriverControls.create(hardwareMap, robot.robotConfig().drive(), null);
        imu = robot.robotConfig().imu(hardwareMap);
        if (spec.kindName().endsWith("Wheels")) {
            for (String n : spec.names()) pods.add(hardwareMap.get(DcMotorEx.class, n));
        }
        // Dead wheels and the OTOS: the Spin test compares with the IMU.
        if (imu == null && !spec.kindName().equals("pinpoint")) {
            throw new IllegalStateException("Robot.java: the Odometry Tuner's Spin test needs the IMU: add .imu(...) to drive(...)");
        }
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> { if (running < 0) chooser.previous(); })
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> { if (running < 0) chooser.next(); })
         .bindPress(Pad.G1, Btn.A, () -> { if (running < 0) begin(java.util.Arrays.asList(TESTS).indexOf(chooser.current())); })
         .bindPress(Pad.G1, Btn.B, () -> { if (running >= 0) finish(); });
    }

    private void begin(int test) {
        running = test;
        odo.setPose(new FieldPose(0, 0, 0));
        imuTurnDeg = 0;
        lastImu = imuDeg();
        odoTurnDeg = 0;
        lastOdoH = 0;
        podsAtStart = null;   // taken in the next loop, after the device has read fresh counts
        lastResult = "";
    }

    @Override
    protected void step() {
        odo.update();
        double h = imuDeg();
        imuTurnDeg += Angles.shortestDiff(h, lastImu);
        lastImu = h;
        podsNow = podCounts();
        if (running >= 0 && podsAtStart == null) podsAtStart = podsNow.clone();
        double oh = odo.pose().headingDeg;
        odoTurnDeg += Angles.shortestDiff(oh, lastOdoH);
        lastOdoH = oh;
        driving.update(gamepad1, 0.3);
    }

    private void finish() {
        FieldPose p = odo.pose();
        int test = running;
        running = -1;
        switch (test) {
            case 0: forward(p); break;
            case 1: left(p); break;
            case 2: spin(); break;
            default: loop(p); break;
        }
    }

    private void forward(FieldPose p) {
        if (Math.abs(p.y) < 6) {
            lastResult = "It measured only " + FinderResults.num(p.y, 1) + " in forward: did the robot move? Check the cables.";
            return;
        }
        if (p.y < 0) {
            lastResult = "Forward counted BACKWARD: the forward-measuring pod is reversed. " + reverseHint(true);
            return;
        }
        double scale = 48 / p.y;
        lastResult = "Forward OK: " + FinderResults.num(p.y, 1) + " in for 48 (" + FinderResults.num((1 / scale - 1) * 100, 1)
                + "% off). Sideways drift " + FinderResults.num(p.x, 1) + " in.";
        if (spec.kindName().equals("otos")) {
            results.set("odo:linear", "Tunables.Odometry: linearScalar = " + FinderResults.num(scale, 3) + "   // times the one in use now");
        } else if (Math.abs(scale - 1) > 0.02) {
            lastResult += spec.kindName().equals("pinpoint")
                    ? " More than 2% off: check the pod type (4-bar or swingarm) and the tape."
                    : " More than 2% off: check ticksPerMm(...) for your pods, and the tape.";
        }
    }

    private void left(FieldPose p) {
        if (Math.abs(p.x) < 4) {
            lastResult = "It measured only " + FinderResults.num(-p.x, 1) + " in to the left: did it move? A tank robot cannot: push it.";
            return;
        }
        lastResult = p.x < 0
                ? "Left OK: " + FinderResults.num(-p.x, 1) + " in for 24."
                : "Left counted RIGHT: the sideways-measuring pod is reversed. " + reverseHint(false);
    }

    private void spin() {
        double turnRad = Math.toRadians(imuTurnDeg);
        if (spec.kindName().equals("pinpoint")) turnRad = Math.toRadians(odoTurnDeg);
        if (podsAtStart == null) podsAtStart = podsNow;
        if (Math.abs(turnRad) < 2 * Math.PI) {
            lastResult = "Only " + FinderResults.num(Math.toDegrees(Math.abs(turnRad)) / 360, 1) + " turns: spin at least 3 (right stick).";
            return;
        }
        double perMm = spec.podTicksPerMm();
        switch (spec.kindName()) {
            case "pinpoint": {
                PinpointOdometry pp = (PinpointOdometry) odo;
                double dx = (pp.device().getEncoderX() - podsAtStart[0]) / perMm * (spec.isReversed("x") ? -1 : 1);
                double dy = (pp.device().getEncoderY() - podsAtStart[1]) / perMm * (spec.isReversed("y") ? -1 : 1);
                // A pod LEFT of the centre rolls backward when the robot turns left; one FORWARD of it rolls left.
                double xLeft = -dx / turnRad, yForward = dy / turnRad;
                // Only the numbers to change: the rest of the line (pods, .reversed) stays as it is.
                results.set("odo:offsets", "Robot.java: in the odometry(...) line, .offsetsMm("
                        + FinderResults.num(xLeft, 1) + ", " + FinderResults.num(yForward, 1) + ")");
                lastResult = "Offsets measured: X pod " + FinderResults.num(xLeft, 1) + " mm left, Y pod "
                        + FinderResults.num(yForward, 1) + " mm forward. Change those two numbers in Robot.java, rebuild, then the Loop test.";
                break;
            }
            case "otos": {
                FieldPose p = odo.pose();
                double measured = Math.toRadians(Angles.wrapDegrees(p.headingDeg));
                lastResult = "OTOS: for the turn scale, spin exactly 10 turns by hand on a mark and compare the heading "
                        + "with 0 (docs/modules/odometry.md). The IMU says " + FinderResults.num(Math.toDegrees(turnRad), 0)
                        + " deg; the OTOS ends at " + FinderResults.num(Math.toDegrees(measured), 1) + " deg.";
                break;
            }
            case "threeWheels": {
                double l = signed(0), r = signed(1), pp = signed(2);
                double track = (r - l) / perMm / 25.4 / turnRad;
                double perp = pp / perMm / 25.4 / turnRad;
                results.set("odo:offsets", "Robot.java: in the odometry(...) line, .trackWidthIn("
                        + FinderResults.num(track, 2) + ").perpForwardIn(" + FinderResults.num(perp, 2) + ")");
                lastResult = "Track width " + FinderResults.num(track, 2) + " in, sideways pod " + FinderResults.num(perp, 2) + " in forward.";
                break;
            }
            default: {
                double par = signed(0), pp = signed(1);
                double left = -par / perMm / 25.4 / turnRad;
                double perp = pp / perMm / 25.4 / turnRad;
                results.set("odo:offsets", "Robot.java: in the odometry(...) line, .parallelLeftIn("
                        + FinderResults.num(left, 2) + ").perpForwardIn(" + FinderResults.num(perp, 2) + ")");
                lastResult = "Forward pod " + FinderResults.num(left, 2) + " in left, sideways pod " + FinderResults.num(perp, 2) + " in forward.";
                break;
            }
        }
    }

    private void loop(FieldPose p) {
        double err = Math.hypot(p.x, p.y), turn = Math.abs(Angles.wrapDegrees(p.headingDeg));
        if (err <= 1.0 && turn <= 2.0) {
            results.set("odo:checked", "Tunables.Odometry: checked = true");
            lastResult = "Back on the mark within " + FinderResults.num(err, 2) + " in and " + FinderResults.num(turn, 1)
                    + " deg: the odometry is good. Set checked = true in Tunables.Odometry.";
        } else {
            lastResult = "Off by " + FinderResults.num(err, 1) + " in and " + FinderResults.num(turn, 1)
                    + " deg. Run Forward, Left and Spin again, copy their lines, rebuild, and try again.";
        }
    }

    private String reverseHint(boolean forwardPod) {
        switch (spec.kindName()) {
            case "pinpoint": return "Add \"" + (forwardPod ? "x" : "y") + "\" to .reversed(...) in Robot.java (or take it out).";
            case "otos": return "Check the OTOS's mounting direction (mountIn heading).";
            default: return "Flip that pod's name in .reversed(...): the counts are below.";
        }
    }

    // A dead-wheel pod's change since the test began, with its .reversed(...) setting.
    private double signed(int i) {
        return (podsNow[i] - podsAtStart[i]) * (spec.isReversed(spec.names().get(i)) ? -1 : 1);
    }

    private double[] podCounts() {
        if (odo instanceof PinpointOdometry) {
            PinpointOdometry pp = (PinpointOdometry) odo;
            return new double[]{pp.device().getEncoderX(), pp.device().getEncoderY()};
        }
        double[] out = new double[pods.size()];
        for (int i = 0; i < pods.size(); i++) out[i] = pods.get(i).getCurrentPosition();
        return out;
    }


    private double imuDeg() {
        return imu == null ? 0 : ImuSetup.headingDeg(imu);
    }

    @Override
    protected void show(RobotTelemetry t) {
        FieldPose p = odo.pose();
        if (!odo.isReady()) t.warn(odo.kind() + ": " + odo.status());
        if (running < 0) {
            t.section("Odometry Tuner (" + odo.kind() + ")");
            chooser.addTelemetry(t, "");
            t.text("A = start the test. The sticks drive slowly.");
        } else {
            t.section(TESTS[running] + ": B when done");
            t.text(new String[]{
                    "Push or drive it 48 in straight forward along the tape.",
                    "Push or drive it 24 in straight to its LEFT.",
                    "Turn in place at least 3 times (right stick), then stop.",
                    "Drive anywhere, then back onto the tape mark, facing the same way."}[running]);
        }
        t.line("Pose", "x " + FinderResults.num(p.x, 1) + ", y " + FinderResults.num(p.y, 1) + " in, heading "
                + FinderResults.num(p.headingDeg, 1));
        if (podsNow != null && podsAtStart != null && running >= 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < podsNow.length; i++) sb.append(i == 0 ? "" : ", ").append((long) (podsNow[i] - podsAtStart[i]));
            t.line("Pod counts", sb.toString());
        }
        if (!lastResult.isEmpty()) t.text(lastResult);
    }

    @Override
    protected String[] helpLines() {
        return new String[]{
                "Sticks: drive slowly (30%). D-pad: choose a test. A: start. B: done.",
                "Forward: 48 in along the tape. Left: 24 in to the left. Spin: 3+ turns in place.",
                "Loop: start on a tape mark, drive anywhere, come back to it. Within 1 in = checked.",
                "Copy the lines it shows into Robot.java and Tunables.java (also saved in finder-results.txt)."};
    }

    @Override
    protected void pause() {
        driving.stop();
    }

    @Override
    protected void stopAll() {
        if (driving != null) driving.stop();
        if (robot != null && robot.all() != null) robot.all().stop();
    }
}
