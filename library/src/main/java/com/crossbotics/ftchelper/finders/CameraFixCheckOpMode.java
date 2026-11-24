package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.odometry.CameraPoseFix;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.vision.LimelightTags;
import java.util.Locale;

/**
 * <b>ALPHA.</b> The standing test for the camera fix ({@code cameraFix(camera)} in Robot.java): it finds
 * {@code turnDegBlue} / {@code turnDegRed}, the turn from the Limelight's field frame to ours, and shows
 * where the camera puts the robot, so you can check it against a tape measure. Nothing moves.
 *
 * <p>Put the robot on the field FACING AWAY from your drivers' wall (heading 0), with tags in view.
 * The screen shows the Limelight's own pose (MegaTag1: x, y and its yaw), the turn that matches the
 * robot's heading, and the robot's place in our frame with that turn. X saves the turn for BLUE, B for
 * RED (stand at the RED wall for that one).
 *
 * <p>It is not in "Tune and check" (the camera fix is untested, and not for BIOBUZZ). Run it from a file
 * of your own:
 * <pre>{@code
 * @TeleOp(name = "Camera Fix Check", group = "Finders")
 * public class CameraFixCheck extends CameraFixCheckOpMode {
 *     @Override protected RobotDefinition robot() { return new Robot(); }
 * }
 * }</pre>
 *
 * Docs: docs/modules/odometry.md, "Camera fix (alpha)"
 */
public abstract class CameraFixCheckOpMode extends FinderOpMode {

    /** The robot in five files: {@code return new Robot();}. */
    protected abstract RobotDefinition robot();

    private RobotDefinition robot;
    private LimelightTags ll;
    private String problem = "";
    private double suggested = Double.NaN;
    private double blue = Double.NaN, red = Double.NaN;

    @Override protected String finderName() { return "Camera fix check"; }

    @Override
    protected void setup() {
        robot = robot();
        Camera c = robot.cameraFixCamera();
        if (c == null) {
            problem = "Robot.java has no cameraFix(camera) line.\nPress STOP. You can skip this tool.";
            return;
        }
        robot.setup(hardwareMap);
        if (!(c.source() instanceof LimelightTags)) {
            problem = "The camera fix needs a Limelight.\nPress STOP. You can skip this tool.";
            return;
        }
        if (robot.odometry() == null) {
            problem = "The camera fix needs an odometry(...) line.\nPress STOP. Add it first.";
            return;
        }
        ll = (LimelightTags) c.source();
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        warnLines(t, problem);
        t.warn("Advanced, not tested: you can skip this.");
        t.warn("Not for BIOBUZZ: its tags move with the HIVE.");
        t.text("Camera fix check: is the camera's idea of where");
        t.text("the robot is right? No motor moves.");
        t.text("Next: face the robot away from your drivers'");
        t.text("wall, with field tags in view.");
    }

    @Override
    protected String stoppedWords() {
        return "Nothing moves in this tool.";
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.X, () -> { if (!Double.isNaN(suggested)) { blue = suggested; write(); } })
         .bindPress(Pad.G1, Btn.B, () -> { if (!Double.isNaN(suggested)) { red = suggested; write(); } });
    }

    private void write() {
        if (!Double.isNaN(blue)) results.setTunable("camerafix:blue", "CameraFix", "turnDegBlue", FinderResults.num(blue, 0));
        if (!Double.isNaN(red)) results.setTunable("camerafix:red", "CameraFix", "turnDegRed", FinderResults.num(red, 0));
        if (Double.isNaN(blue) || Double.isNaN(red)) results.notDone("save the turn at both walls.");
        else results.done();
    }

    @Override
    protected void step() {
        if (ll == null) return;
        robot.odometry().update();
        ll.update();
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (ll == null) {
            warnLines(t, problem);
            return;
        }
        t.warn("Advanced: you can skip this. No motor moves.");
        t.section("Camera fix check");
        FieldPose p = robot.odometry().pose();
        LimelightTags.FieldSighting s = ll.fieldSighting();
        if (s == null || Double.isNaN(s.mt1YawDeg)) {
            t.warn("The Limelight does not know where it is.");
            t.text("Next: put tags in view.");
            t.text("Still? Turn on Full 3D in its web page.");
            suggested = Double.NaN;
            return;
        }
        suggested = CameraPoseFix.suggestTurn(p.headingDeg, s.mt1YawDeg);
        CameraPoseFix.Settings set = new CameraPoseFix.Settings();
        set.turnDeg = suggested;
        double[] ours = new CameraPoseFix(set).toOurs(s.mt1XIn, s.mt1YIn);
        t.text("Next: compare with a tape (help says how).");
        t.text("Right place? {X} = save BLUE. {B} = save RED.");
        t.line("From the center line", fromEdge(ours[0], "RIGHT", "LEFT"));
        t.line("From the field's middle", fromEdge(ours[1], "away from you", "toward you"));
        t.line("Turn that fits", String.format(Locale.US, "%.0f deg", suggested));
        t.line("Tags seen", s.tags);
        t.debug("Robot heading", String.format(Locale.US, "%.1f deg", p.headingDeg));
        t.debug("Limelight's own", String.format(Locale.US, "x %.1f, y %.1f in, yaw %.1f deg", s.mt1XIn, s.mt1YIn, s.mt1YawDeg));
    }

    // "12.0 in RIGHT": a direction in words.
    private static String fromEdge(double v, String plus, String minus) {
        return String.format(Locale.US, "%.1f in %s", Math.abs(v), v < 0 ? minus : plus);
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Advanced, not tested: finds turnDegBlue / Red.",
            "Not for BIOBUZZ: its tags move with the HIVE.",
            "1. Robot facing away from your drivers' wall.",
            "2. Field's center line to the robot's left edge,",
            "   and to its right edge: measure both. The",
            "   average of the two should match the screen.",
            "   Edges on both sides of the line? Count left",
            "   as minus.",
            "3. The same with the front and back edges, from",
            "   the middle of the field.",
            "4. Within a few inches: {X} = save for BLUE.",
            "   At the RED wall: {B} = save for RED.",
        };
    }

    // At the end: the robot's cameras and helpers stop (setup started them).
    @Override protected void stopAll() {
        if (robot != null && ll != null) robot.all().stop();
    }

    // The help page: nothing moves here, so nothing to pause (the cameras keep running).
    @Override protected void pause() { }
}
