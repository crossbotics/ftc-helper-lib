package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.vision.AimController;
import com.crossbotics.ftchelper.vision.TargetObservation;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * Where the goal is, for everything that aims: the camera first, the odometry as a backup. One per
 * robot: {@code robot.aim()}. It also turns the WHOLE robot toward the goal while the driver drives
 * ({@link #mode()}, the same as {@code robot.aimMode()}), and tells how far off the aim is (for the
 * gamepad buzz, {@code g1.rumbleWhenAimed()}).
 *
 * <pre>{@code
 * // Controls.java, inside buttons(...):
 * g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off");   // whole-robot aim
 * g1.rumbleWhenAimed();                                                   // buzz: the closer, the stronger
 *
 * public static class Aim {                                   // Tunables.java (every line optional)
 *     public static double goalX = -58, goalY = 55;           // the goal on the field (odometry backup)
 *     public static boolean mirrorForRed = true;              // like your autonomous's mirrorForRed()
 *     public static double turnKp = 0.02, turnKd = 0.002, maxTurnPower = 0.5, minTurnPower = 0, toleranceDeg = 2;
 *     public static double cameraOffset = 0, rumbleDeg = 4;
 *     public static double maxGoalDistance = 170, cameraOdometryAgree = 6;   // inches: odometry sanity checks
 * }
 * }</pre>
 *
 * <p><b>Whole-robot aim and a following turret are separate modes.</b> Use one: a robot with a turret
 * that follows the goal ({@code follows(camera)} or {@code followsOdometry()}) aims with the turret;
 * {@code robot.aimMode()} is for a robot with no turret (or a turret that does not follow). Both at
 * once would chase the same error with two motors and swing back and forth, so
 * {@code robot.aimMode()} stops at INIT with a message on such a robot.
 *
 * <p><b>Camera first, odometry as a backup.</b> While the camera sees the goal (a picture newer than
 * {@code cameraMaxAgeMs}, 150 ms), that is used. When it does not (the tag is blocked, out of view, a
 * bad picture), and the robot has an {@code odometry(...)} line and {@code Tunables.Aim.goalX, goalY},
 * the goal's direction and distance are worked out from where the robot is ({@code robot.pose()}). The
 * Driver Station says which one is in use.
 *
 * <p><b>The goal point</b> is in the same numbers as {@code driveTo} and the autonomous start spots:
 * seen from your drivers' wall, x inches to the right of the field's centre line, y inches from the
 * centre toward the far wall, written for BLUE. For RED with {@code mirrorForRed = true} (the default,
 * like autonomous) x is mirrored; a field turned half a turn between the alliances (BIOBUZZ) uses
 * {@code mirrorForRed = false}: the same numbers from the red wall. Put the point where the camera
 * measures to (the goal's tags), so the two distances agree and one shot table works for both.
 *
 * <p><b>The odometry is only as good as its pose.</b> It must start right: autonomous's start spot,
 * carried into TeleOp. Without autonomous, TeleOp's pose would be (0, 0), the middle of the field, and
 * the robot would aim at the wrong place, so the odometry backup stays OFF until the pose was set
 * (autonomous ran in this match, or your code called {@code robot.odometry().setPose(...)}); the Driver
 * Station says so. It also stays off while the odometry says the goal is farther than
 * {@code maxGoalDistance} (170 in: more than the field allows, so the pose is wrong), and warns when the
 * camera and the odometry disagree by more than {@code cameraOdometryAgree} (6 in). INIT shows the
 * alliance and the {@code mirrorForRed} choice, so a wrong one is seen before the match.
 *
 * Docs: docs/modules/robot.md (section "Aim"). Tutorial: docs/tutorials/start/09-shooter-and-camera.md
 */
public final class Aim {
    /** Where the goal's direction and distance come from now. */
    public enum Source {
        /** the camera sees the goal */
        CAMERA,
        /** worked out from the odometry (where the robot is) and Tunables.Aim's goal point */
        ODOMETRY,
        /** neither: no fresh picture, and no odometry (or no goal point) */
        NONE;

        /** "camera", "odometry" or "no goal", for the Driver Station. */
        public String words() {
            return this == CAMERA ? "camera" : this == ODOMETRY ? "odometry" : "no goal";
        }
    }

    // The time, in nanoseconds. Tests use a pretend clock.
    static LongSupplier clock = System::nanoTime;

    private final RobotDefinition robot;
    // Whole-robot aim: off until a button (or code) switches it on.
    private final Mode mode = new Mode("Aim the robot", false);
    // Tunables.Aim: the goal point (only when both are written) and the mirroring rule.
    private final boolean hasGoalPoint;
    private final double goalX, goalY;
    private final boolean mirrorForRed;
    private final double cameraMaxAgeMs;
    // Sanity checks for the odometry: the farthest the goal can be (inches), and how far apart the
    // camera's and the odometry's distances may be before the Driver Station warns.
    private final double maxGoalDistanceIn, agreeIn;
    // Live numbers (in the tuning menu when the section exists).
    private final DoubleSupplier turnKp, turnKd, maxTurnPower, minTurnPower, toleranceDeg, cameraOffset, rumbleDeg;
    // The turn controller, made again if kP or kD is tuned.
    private AimController controller = null;
    private double controllerKp = Double.NaN, controllerKd = Double.NaN;
    // For the Driver Station: what the whole-robot aim used this loop.
    private Source lastSource = Source.NONE;
    private boolean driverTurning = false;

    Aim(RobotDefinition robot, TunableSection t) {
        this.robot = robot;
        hasGoalPoint = t.has("goalX") && t.has("goalY");
        goalX = hasGoalPoint ? t.number("goalX", 0) : 0;
        goalY = hasGoalPoint ? t.number("goalY", 0) : 0;
        mirrorForRed = !t.has("mirrorForRed") || t.flag("mirrorForRed", true);
        cameraMaxAgeMs = t.has("cameraMaxAgeMs") ? t.number("cameraMaxAgeMs", 150) : 150;
        maxGoalDistanceIn = t.has("maxGoalDistance") ? t.number("maxGoalDistance", 170) : 170;
        agreeIn = t.has("cameraOdometryAgree") ? t.number("cameraOdometryAgree", 6) : 6;
        turnKp = live(t, "turnKp", 0.02, 0.002);
        turnKd = live(t, "turnKd", 0.002, 0.0005);
        maxTurnPower = live(t, "maxTurnPower", 0.5, 0.05);
        minTurnPower = live(t, "minTurnPower", 0, 0.01);
        toleranceDeg = live(t, "toleranceDeg", 2, 0.5);
        cameraOffset = live(t, "cameraOffset", 0, 0.5);
        rumbleDeg = live(t, "rumbleDeg", 4, 0.5);
    }

    // A number from Tunables.Aim that the tuning menu can change. No section: the library's default
    // (and nothing in the tuning menu for a robot that does not aim).
    private static DoubleSupplier live(TunableSection t, String field, double fallback, double step) {
        if (!t.exists()) return () -> fallback;
        final com.crossbotics.ftchelper.tuning.Tunable n = t.tunable(field, fallback, step);
        return n::get;
    }

    // ----------------------------- where the goal is -----------------------------

    /** True if {@code Tunables.Aim} has {@code goalX} and {@code goalY} (the odometry backup can work). */
    public boolean hasGoalPoint() {
        return hasGoalPoint;
    }

    /**
     * The goal now: what the camera sees (the first camera with a {@code goal(...)} in Robot.java), or,
     * when it does not see it, the goal worked out from the odometry. Null if neither. Check for null.
     */
    public TargetObservation goal() {
        return goalFor(aimingCamera());
    }

    /** Where {@link #goal()} comes from now: CAMERA, ODOMETRY or NONE. */
    public Source source() {
        return sourceOf(goal());
    }

    /** CAMERA, ODOMETRY or NONE (null), for a target from {@link #goal()} or {@link #fromOdometry()}. */
    public static Source sourceOf(TargetObservation o) {
        if (o == null) return Source.NONE;
        return o.fromOdometry() ? Source.ODOMETRY : Source.CAMERA;
    }

    // This camera's goal if it sees it now, else the odometry's (null if neither). camera may be null.
    TargetObservation goalFor(Camera camera) {
        TargetObservation seen = cameraGoal(camera);
        return seen != null ? seen : fromOdometry();
    }

    // This camera's goal, only if the picture is fresh and has a direction; else null.
    TargetObservation cameraGoal(Camera camera) {
        if (camera == null) return null;
        TargetObservation g = camera.goal();
        if (g == null || !g.hasBearing() || !g.isFresh(clock.getAsLong(), cameraMaxAgeMs)) return null;
        return g;
    }

    /**
     * The goal worked out from the odometry: its direction from the robot's front (degrees,
     * counterclockwise + = to the LEFT, like a camera's bearing) and its distance from the robot's
     * centre (inches). Null if the robot has no odometry, the odometry is not ready, {@code Tunables.Aim}
     * has no goal point, the pose was never set (no autonomous, no {@code setPose}), or the goal would
     * be farther than {@code maxGoalDistance} (a wrong pose). {@link #odometryProblem()} says which.
     */
    public TargetObservation fromOdometry() {
        TargetObservation o = odometryGoalUnchecked();
        // A goal farther than the field allows: the pose is wrong. Aiming there would be worse than not aiming.
        if (o == null || o.distanceIn > maxGoalDistanceIn) return null;
        return o;
    }

    /**
     * Why the odometry backup is off now, in a few words for the Driver Station; "" when it works (or
     * the robot has no odometry or no goal point: then there is nothing to warn about).
     */
    public String odometryProblem() {
        PoseTracker tracker = robot.odometry();
        if (!hasGoalPoint || tracker == null) return "";
        if (!tracker.isReady()) return "the odometry is not ready (" + tracker.status() + ")";
        if (!tracker.isPoseSet()) {
            return "the pose was never set (no autonomous before this TeleOp): camera only. "
                    + "Run autonomous first, or set it: "
                    + (robot.resetPoseHint != null ? robot.resetPoseHint : "robot.resetPose(...)");
        }
        TargetObservation o = odometryGoalUnchecked();
        if (o != null && o.distanceIn > maxGoalDistanceIn) {
            return Module.fmt("the odometry says the goal is %.0f in away, more than the field allows (%.0f): "
                    + "the pose is wrong. Camera only", o.distanceIn, maxGoalDistanceIn);
        }
        return "";
    }

    /**
     * When the camera and the odometry both see the goal and their distances differ by more than
     * {@code Tunables.Aim.cameraOdometryAgree} (6 in): a warning (the pose drifted, or the goal point
     * is not where the camera measures to). "" otherwise.
     */
    public String disagreement() {
        TargetObservation seen = cameraGoal(aimingCamera());
        if (seen == null || !seen.hasDistance()) return "";
        TargetObservation odo = fromOdometry();
        if (odo == null || Math.abs(seen.distanceIn - odo.distanceIn) <= agreeIn) return "";
        return Module.fmt("camera says %.0f in, odometry %.0f in: check the pose (drifted?) or Tunables.Aim goalX, goalY",
                seen.distanceIn, odo.distanceIn);
    }

    // The goal from the pose, without the "too far" check. Null if the odometry cannot be used at all.
    private TargetObservation odometryGoalUnchecked() {
        PoseTracker tracker = robot.odometry();
        if (!hasGoalPoint || tracker == null || !tracker.isReady() || !tracker.isPoseSet()) return null;
        FieldPose p = tracker.pose();
        // Written for BLUE: on RED with mirroring, the field is a mirror image (x flips).
        double gx = goalXNow();
        double dx = gx - p.x, dy = goalY - p.y;
        // Which way the goal is on the field, as a heading: 0 = straight away from the drivers (+y),
        // counterclockwise +, so 90 = to the left (-x). atan2(-dx, dy) gives exactly that.
        double fieldDeg = Math.toDegrees(Math.atan2(-dx, dy));
        // From the robot's front: the field direction minus the way the robot faces (-180 to 180).
        double bearing = Angles.wrapDegrees(fieldDeg - p.headingDeg);
        return new TargetObservation(TargetObservation.ODOMETRY, "Goal (odometry)", bearing,
                Math.hypot(dx, dy), clock.getAsLong());
    }

    // The goal's x for this alliance: mirrored for RED when mirrorForRed.
    private double goalXNow() {
        return robot.alliance() == Alliance.RED && mirrorForRed ? -goalX : goalX;
    }

    // The camera that aims the whole robot: the first one with a goal(...) in Robot.java, or null.
    Camera aimingCamera() {
        for (Module<?> m : robot.modules()) {
            if (m instanceof Camera && ((Camera) m).hasGoal()) return (Camera) m;
        }
        return null;
    }

    // ----------------------------- the whole robot aims -----------------------------

    /**
     * Whole-robot aim, for a robot with no turret: while it is on, TeleOp turns the robot toward the
     * goal and the driver keeps forward and sideways. The driver's turn stick still wins: move it and
     * the robot turns as the stick says; let go and the aim takes over again. Off at START.
     * {@code g1.rb.toggle(robot.aimMode())}.
     *
     * @throws IllegalStateException if a turret follows the goal: whole-robot aim and turret-following
     *         are separate modes (both would chase the same error and swing back and forth)
     */
    public Mode mode() {
        Turret t = followingTurretInRobot();
        if (t != null) {
            throw new IllegalStateException("Controls.java: robot.aimMode() (whole-robot aim) and " + t.name()
                    + " following the goal are separate modes: use one. With a following turret, the turret aims "
                    + "(remove the aimMode() line); for whole-robot aim, remove follows(...)/followsOdometry() in Robot.java.");
        }
        return mode;
    }

    // A turret that follows the goal (follows(camera) or followsOdometry() in Robot.java), or null.
    private Turret followingTurretInRobot() {
        for (Module<?> m : robot.modules()) {
            if (m instanceof Turret && ((Turret) m).followsGoal()) return (Turret) m;
        }
        return null;
    }

    /**
     * The turn TeleOp uses this loop (clockwise +, like the drive's turn), from the driver's turn stick
     * after shaping: unchanged while the mode is off or the stick is moved; the aim's turn otherwise
     * (0 when there is no goal: the robot does not turn on its own).
     */
    double assistTurn(double driverTurn) {
        if (!mode.isOn()) {
            driverTurning = false;
            if (controller != null) controller.reset();
            return driverTurn;
        }
        if (driverTurn != 0) {
            // The driver wins. Forget the past so the aim starts fresh when the stick is let go.
            driverTurning = true;
            controller().reset();
            return driverTurn;
        }
        driverTurning = false;
        TargetObservation g = goal();
        lastSource = sourceOf(g);
        if (g == null) {
            controller().reset();
            return 0;
        }
        double power = controller().turnPower(corrected(g));
        // Too little power to turn the robot at all (friction): at least minTurnPower, outside the tolerance.
        double min = minTurnPower.getAsDouble();
        if (power != 0 && Math.abs(power) < min) power = Math.copySign(min, power);
        return power;
    }

    // The aim controller with this loop's numbers (a new one if kP or kD was tuned).
    private AimController controller() {
        double kp = turnKp.getAsDouble(), kd = turnKd.getAsDouble();
        if (controller == null || kp != controllerKp || kd != controllerKd) {
            controller = new AimController(kp, kd, clock);
            controllerKp = kp;
            controllerKd = kd;
        }
        return controller.deadband(toleranceDeg.getAsDouble()).maxPower(maxTurnPower.getAsDouble())
                .maxAgeMs(cameraMaxAgeMs);
    }

    // A camera's bearing minus the camera offset (where the goal sits in the picture when the launcher
    // points at it). The odometry's bearing is already from the robot's front: no offset.
    private TargetObservation corrected(TargetObservation g) {
        if (g.fromOdometry()) return g;
        return new TargetObservation(g.id, g.name, g.bearingDeg - cameraOffset.getAsDouble(), g.distanceIn, g.timeNanos);
    }

    // ----------------------------- how far off -----------------------------

    /**
     * How many degrees the aim is off now (any sign), NaN if unknown. With whole-robot aim on: the
     * robot's front to the goal. Otherwise, a turret that is following: its error. Neither: NaN.
     */
    public double errorDeg() {
        if (mode.isOn()) {
            TargetObservation g = goal();
            return g == null ? Double.NaN : corrected(g).bearingDeg;
        }
        Turret t = followingTurret();
        return t == null ? Double.NaN : t.aimErrorDeg();
    }

    /**
     * True when aimed: whole-robot aim on and within {@code toleranceDeg}, or a following turret that
     * is aimed AT THE GOAL NOW (see {@link Turret#isAimed()}: a goal seen this moment, homed, in range).
     * Losing the goal makes it false at once, so a launcher gated on it never fires blind. Use it as a
     * launcher check: {@code .requireReady("aimed", () -> aim().isAimed())}.
     */
    public boolean isAimed() {
        if (mode.isOn()) {
            double e = errorDeg();
            return !Double.isNaN(e) && Math.abs(e) <= toleranceDeg.getAsDouble();
        }
        Turret t = followingTurret();
        return t != null && t.isAimed();
    }

    /** The error that starts the buzz of {@code rumbleWhenAimed()} (Tunables.Aim.rumbleDeg, default 4). */
    public double rumbleDeg() {
        return rumbleDeg.getAsDouble();
    }

    // The first turret with its follow mode on, or null.
    private Turret followingTurret() {
        for (Module<?> m : robot.modules()) {
            if (m instanceof Turret && ((Turret) m).followMode().isOn()) return (Turret) m;
        }
        return null;
    }

    // ----------------------------- the Driver Station -----------------------------

    /** The goal-point tolerance used by {@link Turret#isAimed()} for the goal error (Tunables.Aim.toleranceDeg, 2). */
    double toleranceDeg() {
        return toleranceDeg.getAsDouble();
    }

    // INIT: the alliance and how the goal point is used for it, so a wrong mirrorForRed is seen before
    // the match; and a loud warning when the odometry backup cannot work (no pose).
    void addInitTelemetry(RobotTelemetry t) {
        if (!hasGoalPoint || robot.odometry() == null) return;
        String rule = mirrorForRed ? "mirrorForRed = true: RED mirrors x" : "mirrorForRed = false: the same numbers from each wall";
        t.line("Aim", Module.fmt("%s, goal x %.0f, y %.0f in (%s)", robot.alliance(), goalXNow(), goalY, rule));
        String problem = odometryProblem();
        if (!problem.isEmpty()) t.warn("Aim odometry: " + problem);
    }

    // Every loop in TeleOp (ControlMap shows it): the odometry warnings, and one line while whole-robot
    // aim is on.
    void addTelemetry(RobotTelemetry t) {
        String problem = odometryProblem();
        if (!problem.isEmpty()) t.warn("Aim odometry: " + problem);
        String apart = disagreement();
        if (!apart.isEmpty()) t.warn("Aim: " + apart);
        if (!mode.isOn()) return;
        if (driverTurning) {
            t.line("Aim", "the driver is turning");
            return;
        }
        if (lastSource == Source.NONE) {
            t.warn("Aim: no goal (the camera does not see it" + (hasGoalPoint && robot.odometry() != null ? "" : ", no odometry backup") + ")");
            return;
        }
        double e = errorDeg();
        t.line("Aim", lastSource.words() + (Double.isNaN(e) ? "" : Module.fmt(", %.1f deg off", e))
                + (isAimed() ? "  AIMED" : ""));
    }
}
