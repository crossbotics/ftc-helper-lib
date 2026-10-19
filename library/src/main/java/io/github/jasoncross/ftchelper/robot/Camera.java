package io.github.jasoncross.ftchelper.robot;

import com.qualcomm.hardware.limelightvision.Limelight3A;
import io.github.jasoncross.ftchelper.match.Alliance;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.vision.LimelightTags;
import io.github.jasoncross.ftchelper.vision.TargetObservation;
import io.github.jasoncross.ftchelper.vision.TargetSource;
import io.github.jasoncross.ftchelper.vision.WebcamTags;
import java.util.List;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;

/**
 * A camera that finds AprilTags: a Limelight 3A or a webcam. Other modules use it: a {@link Turret}
 * that follows the goal, a {@link Launcher} whose speed comes from the distance.
 *
 * <pre>{@code
 * camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));   // Robot.java
 * }</pre>
 *
 * <p>{@link #goal(String, String)} names this season's goal for each alliance (the tag group's
 * name); {@link #goal()} then gives the target for the alliance chosen in autonomous. Tunables
 * ({@code Tunables.Limelight} for device "limelight"): {@code pipeline} (Limelight only, default 0).
 * No suggested buttons. It starts in INIT (a webcam needs a second or two).
 *
 * Docs: docs/modules/robot.md (section "Camera"), docs/modules/vision.md
 */
public final class Camera extends Module<Camera> {
    private final boolean limelight;
    private String blueGoal = null, redGoal = null;
    TargetSource source;

    private Camera(boolean limelight, String device) {
        super(device);
        this.limelight = limelight;
    }

    /** A Limelight 3A. */
    public static Camera limelight(String name) {
        return new Camera(true, name);
    }

    /** A webcam (USB). */
    public static Camera webcam(String name) {
        return new Camera(false, name);
    }

    /**
     * The goal's tag group for each alliance, e.g. "BLUE SCORING" and "RED SCORING" in BIOBUZZ.
     *
     * @param blueGroup the group the BLUE alliance aims at
     * @param redGroup  the group the RED alliance aims at
     */
    public Camera goal(String blueGroup, String redGroup) {
        blueGoal = blueGroup;
        redGoal = redGroup;
        return this;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        if (limelight) {
            source = new LimelightTags(device, devices.get(Limelight3A.class, device))
                    .pipeline((int) Math.round(t.number("pipeline", 0)));
        } else {
            source = new WebcamTags(device, devices.get(WebcamName.class, device));
        }
        source.start();
    }

    // ----------------------------- reading -----------------------------

    /**
     * The goal for this match's alliance (see {@link #goal(String, String)}), or null if it is not
     * seen now (or no goal was named). Always check for null.
     */
    public TargetObservation goal() {
        if (source == null || blueGoal == null) return null;
        Alliance a = robot == null ? Alliance.BLUE : robot.alliance();
        return source.target(a.pick(blueGoal, redGoal));
    }

    /** The target with this name (a tag group or a tag's name), or null if not seen now. */
    public TargetObservation target(String name) {
        return source == null ? null : source.target(name);
    }

    /** The tag with this number, or null if not seen now. */
    public TargetObservation target(int id) {
        return source == null ? null : source.target(id);
    }

    /** The camera inside (a LimelightTags or WebcamTags), for anything this module does not offer. */
    public TargetSource source() {
        return source;
    }

    @Override
    public void update() {
        source.update();
    }

    @Override
    public void stop() {
        if (source != null) source.stop();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (source == null) return;
        source.addTelemetry(t);
        if (blueGoal != null) {
            TargetObservation g = goal();
            t.line("Goal", g == null ? "not seen" : fmt("%.0f in, %.1f deg", g.distanceIn, g.bearingDeg));
        }
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        String where = "forward=6, left=0, up=12, yaw=0, pitch=20";
        if (limelight) out.add("limelight." + device + " = name=" + device + ", " + where + "   # TODO measure the mount");
        else out.add("camera." + sectionFor(device) + " = name=" + device + ", " + where + ", hfov=70.4, width=640, height=480   # TODO measure the mount");
    }
}
