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
 * Checks and measures the odometry of the robot in five files (docs: Add odometry): which way each pod counts,
 * how far it measures, and where the pods sit (the offsets). Then it writes the lines to copy.
 *
 * <pre>
 * The sticks drive the robot slowly (30%), also between tests, once they were let go after START, help and
 * the end of a test. D-pad up/down = choose a test, A = start, B = done.
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
 * Docs: docs/modules/odometry.md. Guide: docs/tuning/09-odometry-tuner.md
 */
public abstract class OdometryTunerOpMode extends FinderOpMode {
    // Numbered: the chooser lists them in name order.
    private static final String[] TESTS = {"1 Forward 48 in", "2 Left 24 in", "3 Spin 3 turns or more", "4 Loop and back"};

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
    // The Loop test: how far the robot got from the mark (inches), so standing still is not "back on the mark".
    private double farthestIn;
    /** The Spin test needs this many turns: more turns, a smaller error from where it starts and stops. */
    static final double SPIN_TURNS = 3;
    /** The Loop test counts only when the robot went at least this far from the mark (inches). */
    static final double LOOP_MIN_IN = 24;
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
    protected void showInit(RobotTelemetry t) {
        t.warn("After START the STICKS drive the robot, slowly.");
        t.warn("Let go of the sticks = stop. Clear the floor.");
        t.text("Odometry tuner: checks the small measuring");
        t.text("wheels (pods) or sensor that track the robot.");
        t.text("You can push it by hand instead.");
        t.text("You need: a tape line 48 in, with a start mark.");
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
        farthestIn = 0;
        // A test run again: what it found before is not true any more until it passes again.
        if (test == 0) results.remove("odo:linear");
        if (test == 2) results.remove("odo:offsets");
        if (test == 3) notChecked("finish 4 Loop and back.");
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
        if (running >= 0) {
            FieldPose p = odo.pose();
            farthestIn = Math.max(farthestIn, Math.hypot(p.x, p.y));
        }
        // SAFETY: a stick already pushed at START, after help or at the end of a test does not drive.
        if (inputsReady()) driving.update(gamepad1, 0.3);
        else driving.hold();
    }

    private void finish() {
        FieldPose p = odo.pose();
        int test = running;
        running = -1;
        driving.hold();
        waitForRelease();   // the result is on the screen: the sticks drive again after they were let go
        switch (test) {
            case 0: forward(p); break;
            case 1: left(p); break;
            case 2: spin(); break;
            default: loop(p); break;
        }
    }

    // Not checked (any more): no "checked = true" to copy.
    private void notChecked(String why) {
        results.remove("odo:checked");
        results.notDone(why);
    }

    // A test that failed: its words, and the odometry is not checked.
    private void failed(String words) {
        notChecked("the last test failed. Run it again.");
        lastResult = words;
    }

    private void forward(FieldPose p) {
        if (Math.abs(p.y) < 6) {
            failed("It measured only " + FinderResults.num(Math.abs(p.y), 1) + " in forward."
                    + "\nNext: did it move? Yes: check the cables.");
            return;
        }
        if (p.y < 0) {
            failed("Forward counted BACKWARD.\n" + reverseHint(true));
            return;
        }
        double scale = 48 / p.y;
        double offPct = (1 / scale - 1) * 100;
        if (Math.abs(offPct) > 10) {
            failed("It measured " + FinderResults.num(p.y, 1) + " in for 48: over 10% off."
                    + "\nNext: was it 48 in on the tape? Test again."
                    + "\nStill off? Check the pod settings in Robot.java.");
            return;
        }
        // Under 0.05% shows as 0.0%: "exact", never "0.0% too low".
        lastResult = "Forward direction is right. It measured"
                + "\n" + FinderResults.num(p.y, 1) + " in for 48 (" + (Math.abs(offPct) < 0.05 ? "exact"
                : FinderResults.num(Math.abs(offPct), 1) + "% too " + (offPct < 0 ? "low" : "high")) + ")";
        if (spec.kindName().equals("otos")) {
            // The final setting, not a number to multiply: the one in Tunables.java times what was measured.
            double now = codeValue(robot.tunablesClass(), "Odometry", "linearScalar", 1.0);
            results.setTunable("odo:linear", "Odometry", "linearScalar", FinderResults.num(now * scale, 3));
            notChecked("install linearScalar, then run 4 Loop.");
        } else if (Math.abs(scale - 1) > 0.02) {
            notChecked("Forward is more than 2% off.");
            lastResult += "\nOver 2% off: check the tape, and in Robot.java"
                    + (spec.kindName().equals("pinpoint") ? "\nthe pod type (4-bar or swingarm)." : "\nticksPerMm(...) for your pods.");
            return;
        }
        lastResult += "\nNext: 2 Left 24 in.";
    }

    private void left(FieldPose p) {
        if (Math.abs(p.x) < 4) {
            failed("It measured only " + FinderResults.num(Math.abs(p.x), 1) + " in sideways."
                    + "\nNext: did it move? A tank: push it by hand.");
            return;
        }
        if (p.x > 0) {
            failed("Left counted RIGHT.\n" + reverseHint(false));
            return;
        }
        // Only the direction is checked here: not how exact the distance is.
        lastResult = "Left direction is right."
                + "\nMeasured " + FinderResults.num(-p.x, 1) + " in for 24 (not checked exactly)."
                + "\nNext: 3 Spin.";
    }

    private void spin() {
        double turnRad = Math.toRadians(imuTurnDeg);
        if (spec.kindName().equals("pinpoint")) turnRad = Math.toRadians(odoTurnDeg);
        if (podsAtStart == null) podsAtStart = podsNow;
        // (a hair less than 3 counts: nobody stops exactly on the mark)
        if (Math.abs(turnRad) < (SPIN_TURNS - 0.1) * 2 * Math.PI) {
            failed("Only " + FinderResults.num(Math.toDegrees(Math.abs(turnRad)) / 360, 1) + " turns."
                    + "\nNext: spin at least 3 (right stick), then {B}.");
            return;
        }
        notChecked("install the numbers, then run 4 Loop.");
        double perMm = spec.podTicksPerMm();
        switch (spec.kindName()) {
            case "pinpoint": {
                PinpointOdometry pp = (PinpointOdometry) odo;
                double dx = (pp.device().getEncoderX() - podsAtStart[0]) / perMm * (spec.isReversed("x") ? -1 : 1);
                double dy = (pp.device().getEncoderY() - podsAtStart[1]) / perMm * (spec.isReversed("y") ? -1 : 1);
                // A pod LEFT of the center rolls backward when the robot turns left; one FORWARD of it rolls left.
                double xLeft = -dx / turnRad, yForward = dy / turnRad;
                // Only the numbers to change: the rest of the line (pods, .reversed) stays as it is.
                // Written from the edges: the measured center numbers replace podsFromEdgesMm (they are exact).
                String line = "In odometry(...):";
                String code = "  .offsetsMm(" + FinderResults.num(xLeft, 1) + ", " + FinderResults.num(yForward, 1) + ")";
                if (spec.podsFromEdges()) {
                    results.setIn("odo:offsets", FinderResults.ROBOT_FILE, line, code, "in place of .podsFromEdgesMm(...)");
                } else {
                    results.setIn("odo:offsets", FinderResults.ROBOT_FILE, line, code);
                }
                lastResult = "Offsets measured (the line is below):"
                        + "\nX pod " + side(xLeft, "LEFT", "RIGHT", 1, "mm") + ", Y pod " + side(yForward, "FRONT", "BACK", 1, "mm")
                        + "\nNext: install the line. Then run 4 Loop.";
                break;
            }
            case "otos": {
                FieldPose p = odo.pose();
                double measured = Math.toRadians(Angles.wrapDegrees(p.headingDeg));
                notChecked("run 4 Loop and back.");
                lastResult = "IMU turned " + FinderResults.num(Math.toDegrees(turnRad), 0) + " deg. OTOS ends at "
                        + FinderResults.num(Math.toDegrees(measured), 1) + "."
                        + "\nTurn scale: push it 10 turns exactly on a mark."
                        + "\nIt should end at 0 (docs: odometry).";
                break;
            }
            case "threeWheels": {
                double l = signed(0), r = signed(1), pp = signed(2);
                double track = (r - l) / perMm / 25.4 / turnRad;
                double perp = pp / perMm / 25.4 / turnRad;
                results.setIn("odo:offsets", FinderResults.ROBOT_FILE, "In odometry(...):",
                        "  .trackWidthIn(" + FinderResults.num(track, 2) + ")",
                        "  .perpForwardIn(" + FinderResults.num(perp, 2) + ")");
                lastResult = "Pods apart " + FinderResults.num(track, 2) + " in. Sideways pod " + side(perp, "FRONT", "BACK", 2, "in")
                        + "\nNext: install the lines. Then run 4 Loop.";
                break;
            }
            default: {
                double par = signed(0), pp = signed(1);
                double left = -par / perMm / 25.4 / turnRad;
                double perp = pp / perMm / 25.4 / turnRad;
                results.setIn("odo:offsets", FinderResults.ROBOT_FILE, "In odometry(...):",
                        "  .parallelLeftIn(" + FinderResults.num(left, 2) + ")",
                        "  .perpForwardIn(" + FinderResults.num(perp, 2) + ")");
                lastResult = "Forward pod " + side(left, "LEFT", "RIGHT", 2, "in") + ", side pod " + side(perp, "FRONT", "BACK", 2, "in")
                        + "\nNext: install the lines. Then run 4 Loop.";
                break;
            }
        }
    }

    private void loop(FieldPose p) {
        double err = Math.hypot(p.x, p.y), turn = Math.abs(Angles.wrapDegrees(p.headingDeg));
        if (farthestIn < LOOP_MIN_IN) {
            failed("It went only " + FinderResults.num(farthestIn, 1) + " in from the mark."
                    + "\nNext: drive " + FinderResults.num(LOOP_MIN_IN, 0) + " in or more away, come back, {B}.");
            return;
        }
        String off = FinderResults.num(err, 2) + " in and " + FinderResults.num(turn, 1) + " deg";
        if (err > 1.0 || turn > 2.0) {
            failed("Off by " + off + " (limits 1 and 2)."
                    + "\nNext: run 1, 2 and 3 again. Install their"
                    + "\nlines. Then run 4 Loop again.");
        } else if (results.has("odo:offsets") || results.has("odo:linear")) {
            // This loop ran on the numbers that are installed, not on the new ones shown below.
            notChecked("install the new numbers, then run 4 Loop.");
            lastResult = "Back on the mark: " + off + " off."
                    + "\nBut that was with the OLD numbers."
                    + "\nNext: install the lines. Run 4 Loop again.";
        } else {
            results.setTunable("odo:checked", "Odometry", "checked", "true");
            results.done();
            lastResult = "Back on the mark: " + off + " off."
                    + "\nOK (limits 1 in and 2 deg). Copy the line.";
        }
    }

    private String reverseHint(boolean forwardPod) {
        String kind = spec.kindName();
        if (kind.equals("pinpoint")) return flip(forwardPod ? "x" : "y");
        if (kind.equals("otos")) return "Next: check the OTOS's direction (mountIn).";
        // Dead wheels: the pods that measured that way and counted backward (threeWheels: left, right, sideways).
        List<String> names = spec.names();
        int forwardPods = names.size() - 1;
        if (!forwardPod) return flip(names.get(forwardPods));
        for (int i = 0; i < forwardPods; i++) {
            if (podsAtStart != null && signed(i) < 0) return flip(names.get(i));
        }
        return "Next: run Encoder Check. Then test again.";
    }

    // The one fix for this pod: add its name to .reversed(...), or take it out, from what the code says now.
    private String flip(String pod) {
        String name = "\"" + pod + "\"";
        return (spec.isReversed(pod) ? "Next: in Robot.java, take " + name + " out of"
                : "Next: in Robot.java, add " + name + " to")
                + "\n  .reversed(...) on the odometry line.";
    }

    // "84.0 mm LEFT": a direction in words, never a sign to remember.
    private static String side(double v, String plus, String minus, int decimals, String unit) {
        String size = FinderResults.num(Math.abs(v), decimals);
        return size + " " + unit + (Double.parseDouble(size) == 0 ? "" : " " + (v < 0 ? minus : plus));
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
        t.warn("The sticks drive it. Let go = stop.");
        if (!odo.isReady()) {
            t.warn(odo.kind() + ": " + odo.status());
            t.warn("Next: wait. Still there? Check its cable.");
        }
        if (running < 0) {
            t.section("Odometry tuner: choose a test");
            if (lastResult.isEmpty()) t.text("Next: put it on the start mark. Do test 1.");
            else textLines(t, lastResult);
            chooser.addTelemetry(t, "");
            t.text("D-pad up/down = choose. {A} = start the test.");
            return;
        }
        t.section(TESTS[running]);
        textLines(t, new String[]{
                "Next: move it 48 in straight forward on the tape.",
                "Next: move it 24 in straight to its LEFT.",
                "Next: turn in place 3 times or more (right\nstick). Then stop.",
                "Next: drive 24 in or more away. Come back onto\nthe mark, facing the same way."}[running]);
        t.text("{B} = I am done (the test ends).");
        if (running == 2) {
            boolean pinpoint = spec.kindName().equals("pinpoint");
            t.line("Turns", FinderResults.num(Math.abs(pinpoint ? odoTurnDeg : imuTurnDeg) / 360, 1) + " of 3");
        } else {
            t.line("Moved", side(p.y, "forward", "back", 1, "in"));
            t.line("Sideways", side(p.x, "to the right", "to the left", 1, "in"));
            t.line("Turned", side(p.headingDeg, "left", "right", 1, "deg"));
        }
        if (podsNow != null && podsAtStart != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < podsNow.length; i++) sb.append(i == 0 ? "" : ", ").append((long) (podsNow[i] - podsAtStart[i]));
            t.debug("Pod counts", sb.toString());
        }
    }

    @Override
    protected String[] helpLines() {
        return new String[]{
                "Sticks = drive slowly. Let go = stop.",
                "Or push the robot by hand.",
                "D-pad = choose. {A} = start. {B} = I am done.",
                "1 Forward: 48 in along the tape.",
                "2 Left: 24 in to the robot's left.",
                "3 Spin: turn in place 3 times or more.",
                "   Install the line it shows.",
                "4 Loop: start on the mark. Drive 24 in or",
                "   more away. Come back, facing the same way.",
                "   Within 1 in and 2 deg = checked.",
                "Copy the lines into Robot.java, Tunables.java.",
        };
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
