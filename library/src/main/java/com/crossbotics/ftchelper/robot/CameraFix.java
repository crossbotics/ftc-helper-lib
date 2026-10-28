package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.odometry.CameraPoseFix;
import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.vision.LimelightTags;

/**
 * <b>ALPHA, NOT TESTED ON A ROBOT.</b> The robot in five files' camera fix ({@code cameraFix(camera)} in
 * Robot.java): every loop it sends the robot's heading to the Limelight (for MegaTag2), and for each new
 * picture asks {@link CameraPoseFix} whether to correct the odometry's x and y. Off unless
 * {@code Tunables.CameraFix.enabled = true}. Not for BIOBUZZ (the tags move with the HIVE).
 */
final class CameraFix implements Subsystem {
    private final RobotDefinition robot;
    private final Camera camera;
    private final CameraPoseFix fix;
    private final CameraPoseFix.Settings settings;
    private final double turnBlue, turnRed;
    private long lastNumber = -1;
    private String state = "";

    CameraFix(RobotDefinition robot, Camera camera, TunableSection t) {
        this.robot = robot;
        this.camera = camera;
        settings = new CameraPoseFix.Settings();
        settings.enabled = t.has("enabled") && t.flag("enabled", false);
        turnBlue = t.has("turnDegBlue") ? t.number("turnDegBlue", 0) : 0;
        turnRed = t.has("turnDegRed") ? t.number("turnDegRed", 0) : 0;
        if (t.has("maxAgeMs")) settings.maxAgeMs = t.number("maxAgeMs", 100);
        if (t.has("maxSpeedInPerSec")) settings.maxSpeedInPerSec = t.number("maxSpeedInPerSec", 20);
        if (t.has("maxTurnDegPerSec")) settings.maxTurnDegPerSec = t.number("maxTurnDegPerSec", 30);
        if (t.has("maxJumpIn")) settings.maxJumpIn = t.number("maxJumpIn", 12);
        if (t.has("blend")) settings.blend = t.number("blend", 0.2);
        if (t.has("minTags")) settings.minTags = (int) Math.round(t.number("minTags", 2));
        fix = new CameraPoseFix(settings);
    }

    // True when Tunables.CameraFix.enabled = true (robot.features() asks, with no hardware).
    static boolean enabledIn(TunableSection t) {
        return t.has("enabled") && t.flag("enabled", false);
    }

    /** The logic inside (tests, and the Camera Fix Check). */
    CameraPoseFix logic() {
        return fix;
    }

    boolean enabled() {
        return settings.enabled;
    }

    @Override
    public void update() {
        PoseTracker tracker = robot.odometry();
        LimelightTags ll = camera.source() instanceof LimelightTags ? (LimelightTags) camera.source() : null;
        if (!settings.enabled || tracker == null || ll == null) {
            state = !settings.enabled ? "off" : ll == null ? "needs a Limelight" : "needs an odometry(...) line";
            return;
        }
        // The turn for this alliance (it may change after setup). A mirrored red field is not a turn.
        boolean red = robot.alliance() == Alliance.RED;
        if (red && robot.mirrorForRed()) {
            state = "refused: a mirrored RED field is not in the alpha (mirrorForRed = true)";
            return;
        }
        settings.turnDeg = red ? turnRed : turnBlue;
        long now = System.nanoTime();
        FieldPose p = tracker.pose();
        fix.record(now, p.x, p.y);
        // MegaTag2 needs the heading, in the Limelight's frame (sent in the background, newest only).
        ll.setRobotHeading(fix.limelightYaw(p.headingDeg));
        LimelightTags.FieldSighting s = ll.fieldSighting();
        if (s == null || s.number == lastNumber) return;   // no new picture
        lastNumber = s.number;
        if (!tracker.isPoseSet()) {
            state = "refused: the pose was never set (start spot or setPose first)";
            return;
        }
        CameraPoseFix.Result r = fix.offer(s.mt2XIn, s.mt2YIn, s.timeNanos, now, s.tags,
                tracker.speedInPerSec(), tracker.turnDegPerSec());
        state = r.words;
        if (r.used && (r.dxIn != 0 || r.dyIn != 0)) {
            tracker.nudge(r.dxIn, r.dyIn);   // keeps the speed (setPose would zero it)
            fix.applied(r.dxIn, r.dyIn);     // later pictures do not count this error again
        }
    }

    @Override
    public String name() {
        return "Camera fix";
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        t.debug("Camera fix (ALPHA)", state.isEmpty() ? "no picture yet" : state);
    }
}
