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
            problem = "Robot.java has no cameraFix(camera) line";
            return;
        }
        robot.setup(hardwareMap);
        if (!(c.source() instanceof LimelightTags)) {
            problem = "the camera fix needs a Limelight (Camera.limelight(...))";
            return;
        }
        if (robot.odometry() == null) {
            problem = "the camera fix needs an odometry(...) line";
            return;
        }
        ll = (LimelightTags) c.source();
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        if (!problem.isEmpty()) t.warn(problem);
        t.text("Camera fix check (ALPHA): robot FACING AWAY from your wall,");
        t.text("tags in view. Nothing moves. Press START.");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.X, () -> { if (!Double.isNaN(suggested)) { blue = suggested; write(); } })
         .bindPress(Pad.G1, Btn.B, () -> { if (!Double.isNaN(suggested)) { red = suggested; write(); } });
    }

    private void write() {
        StringBuilder sb = new StringBuilder("Tunables.CameraFix: ");
        if (!Double.isNaN(blue)) sb.append("turnDegBlue = ").append(FinderResults.num(blue, 0));
        if (!Double.isNaN(red)) sb.append(Double.isNaN(blue) ? "" : ", ").append("turnDegRed = ").append(FinderResults.num(red, 0));
        results.set("camerafix", sb.toString());
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
            t.warn(problem);
            return;
        }
        FieldPose p = robot.odometry().pose();
        LimelightTags.FieldSighting s = ll.fieldSighting();
        t.line("Robot heading", String.format(Locale.US, "%.1f deg (0 = facing away from your wall)", p.headingDeg));
        if (s == null || Double.isNaN(s.mt1YawDeg)) {
            t.warn("No field pose from the Limelight: tags in view? Full 3D on? (docs: Limelight lighting pipelines)");
            suggested = Double.NaN;
            return;
        }
        t.line("Limelight (its frame)", String.format(Locale.US, "x %.1f, y %.1f in, yaw %.1f deg, %d tags",
                s.mt1XIn, s.mt1YIn, s.mt1YawDeg, s.tags));
        suggested = CameraPoseFix.suggestTurn(p.headingDeg, s.mt1YawDeg);
        CameraPoseFix.Settings set = new CameraPoseFix.Settings();
        set.turnDeg = suggested;
        double[] ours = new CameraPoseFix(set).toOurs(s.mt1XIn, s.mt1YIn);
        t.line("Turn that fits", String.format(Locale.US, "%.0f deg", suggested));
        t.line("Robot's place (our frame)", String.format(Locale.US, "x %.1f, y %.1f in: check it with a tape", ours[0], ours[1]));
        t.text("X = save the turn for BLUE, B = for RED (at the red wall)");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "ALPHA camera fix: finds turnDegBlue / turnDegRed.",
            "1. Robot on the field, FACING AWAY from your",
            "   drivers' wall, tags in view. Nothing moves.",
            "2. Read 'Turn that fits' and 'Robot's place'.",
            "   Measure the robot's centre with a tape: x from",
            "   the field's centre line (+ = right), y from the",
            "   centre (+ = away from you). They should match",
            "   within a few inches.",
            "3. X = save the turn for BLUE. At the RED wall,",
            "   B = save it for RED. Copy the line.",
            "Not for BIOBUZZ: its tags move with the HIVE.",
        };
    }

    // At the end: the robot's cameras and helpers stop (setup started them).
    @Override protected void stopAll() {
        if (robot != null && ll != null) robot.all().stop();
    }

    // The help page: nothing moves here, so nothing to pause (the cameras keep running).
    @Override protected void pause() { }
}
