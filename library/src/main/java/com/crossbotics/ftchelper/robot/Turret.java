package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.vision.TargetObservation;
import java.util.List;

/**
 * A turret on one motor that turns inside its limits, by hand or following the goal with a
 * {@link Camera}. Angles in degrees, counterclockwise +, 0 = straight forward (where it points when
 * the program starts).
 *
 * <pre>{@code
 * turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));   // Robot.java
 *
 * public static class Turret {                                // Tunables.java
 *     public static double min = -90, max = 90;              // its range: it never turns past these
 *     public static double kP = 0.02, kI = 0, kD = 0.001;
 *     public static double maxPower = 0.8, tolerance = 1.0, cameraOffset = 0;
 *     public static double outOfRangeHold = 10;               // odometry goal this far past a limit: hold
 * }
 * }</pre>
 *
 * <p><b>Follow mode</b> ({@link #followMode()}, on from START when {@link #follows} or
 * {@link #followsOdometry} is used): every loop it turns toward the goal. The camera first; when the
 * camera does not see the goal and the robot has odometry and {@code Tunables.Aim.goalX, goalY}, toward
 * the goal worked out from where the robot is (see {@link Aim}). The Driver Station says which. A goal
 * just past the range: it waits at the limit and the Driver Station says "goal out of turret range".
 * A goal far past it (more than {@code outOfRangeHold}, 10 degrees, past a limit: behind the robot) from
 * the odometry: it holds where it points, so it does not swing from one limit to the other as the
 * heading wobbles, until the goal is back within 5 degrees of the range. The stick takes over while it
 * is moved, then following goes on.
 *
 * <p><b>Aimed</b> ({@link #isAimed()}) while following means aimed at the goal NOW: the goal is seen
 * this moment (camera or odometry), the turret is homed and in range, and it points within the
 * tolerance. A launcher gated on it never fires after the goal is lost.
 *
 * <p>A following turret and whole-robot aim ({@code robot.aimMode()}) are separate modes: use one (see
 * {@link Aim}).
 *
 * <p>Suggested buttons: operator right stick X turns it by hand; operator BACK switches following
 * on and off.
 *
 * Docs: docs/modules/robot.md (section "Turret")
 */
public final class Turret extends Module<Turret> {
    private final String motorId;
    private double gearRatio = 1;
    private boolean reversed = false;
    private Camera camera = null;
    private boolean cameraOnTurret = true;
    // followsOdometry(): follow the goal from the odometry alone (no camera).
    private boolean odometryOnly = false;
    // What the following used last loop (for the Driver Station).
    private Aim.Source source = Aim.Source.NONE;
    private double cameraOffsetDeg = 0;
    private final Mode follow = new Mode("Turret follow", false);
    com.crossbotics.ftchelper.launcher.Turret part;
    private Tunable kP, kI, kD;
    private boolean stickMoving = false;
    private double min, max;
    // The odometry's goal is far past the range (behind the robot): hold, do not swing limit to limit.
    private boolean goalBehind = false;
    private double holdPastDeg = 10;

    private Turret(String motor, String motorId) {
        super(motor);
        Motors.get(motorId);
        this.motorId = motorId;
    }

    /**
     * A turret on this motor.
     *
     * @param motor   the motor's configuration name
     * @param motorId the motor's parts-catalog id (see {@link Motors})
     */
    public static Turret motor(String motor, String motorId) {
        return new Turret(motor, motorId);
    }

    /** Motor turns per turret turn (the gears or belt). Default 1. */
    public Turret gearRatio(double motorTurnsPerTurretTurn) {
        gearRatio = motorTurnsPerTurretTurn;
        return this;
    }

    /** Positive power turns the other way (if + turns it clockwise). */
    public Turret reversed() {
        reversed = true;
        return this;
    }

    /**
     * Follows this camera's goal (follow mode on from START). The camera turns with the turret. With
     * an {@code odometry(...)} line and {@code Tunables.Aim.goalX, goalY}, the odometry takes over while
     * the camera does not see the goal.
     */
    public Turret follows(Camera c) {
        camera = c;
        return this;
    }

    /**
     * Follows the goal with NO camera: its direction comes from the odometry (where the robot is) and
     * {@code Tunables.Aim.goalX, goalY} (see {@link Aim}). Needs an {@code odometry(...)} line. Follow
     * mode on from START. Assumes the turret turns about the robot's centre.
     */
    public Turret followsOdometry() {
        odometryOnly = true;
        return this;
    }

    // True if it follows anything (a camera, or the odometry).
    private boolean follows() {
        return camera != null || odometryOnly;
    }

    // For Aim: true if Robot.java made it follow the goal (whole-robot aim must not be used with it).
    boolean followsGoal() {
        return follows();
    }

    // For Aim (shooting on the move): true if this camera turns with this turret, so its pictures are
    // seen from where the turret points.
    boolean carries(Camera c) {
        return c != null && camera == c && cameraOnTurret;
    }

    /** The camera is on the robot's body, not on the turret. */
    public Turret cameraOnBody() {
        cameraOnTurret = false;
        return this;
    }

    double countsPerDegree() {
        return Motors.get(motorId).countsPerRev * gearRatio / 360.0;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        if (camera != null && camera.robot != robot) {
            throw new IllegalStateException("Robot.java: " + name() + " follows a camera that is not added: "
                    + "write camera = add(Camera...) and follows(camera)");
        }
        if (odometryOnly && robot.odometrySpec() == null) {
            throw new IllegalStateException("Robot.java: " + name() + ".followsOdometry() needs an odometry(...) line");
        }
        if (odometryOnly && !robot.aim().hasGoalPoint()) {
            throw new IllegalStateException(name() + ".followsOdometry() needs the goal on the field: "
                    + "Tunables.Aim { public static double goalX = ..., goalY = ...; } (inches, see docs/modules/robot.md#aim)");
        }
        cameraOffsetDeg = t.number("cameraOffset", 0);
        holdPastDeg = Math.max(0, t.number("outOfRangeHold", 10));
        min = t.number("min", -90);
        max = t.number("max", 90);
        kP = t.tunable("kP", 0.02, 0.002).range(0, 1);
        kI = t.tunable("kI", 0, 0.0005).range(0, 1);
        kD = t.tunable("kD", 0.001, 0.0002).range(0, 1);
        DcMotorEx motor = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                .brake().resetEncoder().runWithoutEncoder().done();
        part = new com.crossbotics.ftchelper.launcher.Turret(name(), motor)
                .countsPerDegree(countsPerDegree())
                .limits(min, max)
                .pid(kP.get(), kI.get(), kD.get(), 0)
                .maxPower(t.number("maxPower", 0.8))
                .tolerance(t.number("tolerance", 1.0))
                .slowZone(t.number("slowZone", 10), t.number("slowZonePower", 0.25))
                .cameraOffset(cameraOffsetDeg);
    }

    @Override
    void start() {
        if (follows()) follow.on();
    }

    // ----------------------------- commands -----------------------------

    /** Following the camera's goal: switch it with a button ({@code g2.back.toggle(robot.turret.followMode())}). */
    public Mode followMode() {
        return follow;
    }

    /**
     * Turns to an angle (inside the limits) and stops following.
     *
     * @param deg robot-relative, counterclockwise +, 0 = forward
     */
    public void aimAt(double deg) {
        needBuilt(part);
        touch();
        follow.off();
        goalBehind = false;
        part.aimAt(deg);
    }

    /**
     * By hand, -1 to 1 (a stick, every loop). While it is moved, following pauses.
     *
     * @param power + = counterclockwise
     */
    public void manual(double power) {
        needBuilt(part);
        stickMoving = power != 0;
        if (stickMoving) touch();
        part.manual(power);
    }

    /**
     * True when it points where it should, within the tolerance. While FOLLOWING, that means at the
     * goal now: the goal is seen this moment (a fresh camera picture, or the odometry), the turret is
     * homed and in range, it reached its target, and the goal is within the tolerance (the larger of
     * Tunables.Turret.tolerance and Tunables.Aim.toleranceDeg). False the moment the goal is lost, so a
     * launcher gated on it ({@code requireReady("aimed", turret::isAimed)}) never fires blind.
     */
    public boolean isAimed() {
        if (part == null || !part.isAimed()) return false;
        if (!follow.isOn() || !follows()) return true;   // by hand / aimAt(): where it was told
        if (goalBehind || !part.isHomed() || part.isOutOfRange()) return false;
        double e = aimErrorDeg();
        double tol = Math.max(part.getTolerance(), robot == null ? 0 : robot.aim().toleranceDeg());
        return !Double.isNaN(e) && Math.abs(e) <= tol;
    }

    /** True when the goal is past its range (it waits at the limit, or holds while the goal is behind). */
    public boolean isOutOfRange() {
        return part != null && (part.isOutOfRange() || goalBehind);
    }

    /** The angle now, degrees. */
    public double angle() {
        return part == null ? 0 : part.getAngle();
    }

    /**
     * How many degrees the turret points away from the goal now (any sign), NaN if the goal is not
     * known (no fresh picture and no odometry). For the gamepad buzz ({@code rumbleWhenAimed()}).
     */
    public double aimErrorDeg() {
        if (part == null) return Double.NaN;
        TargetObservation seen = robot == null ? null : robot.aim().cameraGoal(camera);
        if (seen != null) {
            double error = seen.bearingDeg - cameraOffsetDeg;
            // On the turret: the bearing is from where the turret points. On the body: from the robot's front.
            return cameraOnTurret ? error : error - part.getAngle();
        }
        TargetObservation odo = robot == null || !follows() ? null : robot.aim().fromOdometry();
        return odo == null ? Double.NaN : com.crossbotics.ftchelper.util.Angles.wrapDegrees(odo.bearingDeg - part.getAngle());
    }

    // The odometry's goal direction. Far past a limit (behind the robot) the nearest limit would flip
    // from one side to the other as the heading wobbles around "straight behind", swinging the turret
    // all the way across. So: more than holdPastDeg past a limit, hold where it points; go on only when
    // the goal is back within half that (hysteresis: two thresholds, so it does not flicker at one).
    private void followOdometry(double bearingDeg) {
        double past = part.degreesOutOfRange(bearingDeg);
        boolean wasBehind = goalBehind;
        if (!goalBehind && past > holdPastDeg) goalBehind = true;
        else if (goalBehind && past <= holdPastDeg / 2) goalBehind = false;
        if (goalBehind) {
            // Hold where it points now (once, as it starts holding).
            if (!wasBehind && part.isHomed()) part.aimAt(part.getAngle());
            return;
        }
        part.aimAt(bearingDeg);
    }

    /** Where the following got the goal last loop: CAMERA, ODOMETRY or NONE. */
    public Aim.Source aimSource() {
        return source;
    }

    /** The launcher.Turret inside, for anything this module does not offer. */
    public com.crossbotics.ftchelper.launcher.Turret part() {
        return part;
    }

    @Override
    public void update() {
        part.pid(kP.get(), kI.get(), kD.get(), 0);
        if (!follow.isOn()) goalBehind = false;   // only following holds for a goal behind
        if (follow.isOn() && follows() && !stickMoving) {
            // The camera first (a fresh picture of the goal).
            TargetObservation seen = robot.aim().cameraGoal(camera);
            TargetObservation odo = seen == null ? robot.aim().fromOdometry() : null;
            if (seen != null) {
                goalBehind = false;
                part.aimAtTarget(seen, cameraOnTurret);
                source = Aim.Source.CAMERA;
            } else if (odo != null) {
                // The odometry's direction is from the robot's front: it IS the turret angle to go to.
                followOdometry(odo.bearingDeg);
                source = Aim.Source.ODOMETRY;
            } else {
                goalBehind = false;
                source = Aim.Source.NONE;   // nothing new: it holds where it points
            }
        }
        part.update();
    }

    @Override
    public void stop() {
        if (part == null) return;
        touch();
        follow.off();
        goalBehind = false;
        part.stop();
    }

    private double pausedTarget = Double.NaN;
    private boolean pausedFollowing = false;

    @Override
    void pause() {
        if (part == null) return;
        pausedTarget = part.getTargetAngle();
        pausedFollowing = follow.isOn();
        follow.off();
        part.stop();          // power 0 (it is on brake)
    }

    @Override
    void resume() {
        if (part == null) return;
        if (!Double.isNaN(pausedTarget) && part.isHomed()) part.aimAt(pausedTarget);
        if (pausedFollowing) follow.on();
    }

    @Override
    void halt() {
        if (part != null && part.isHomed()) part.aimAt(part.getAngle());   // hold where it points now
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (part == null) return;
        if (follow.isOn() && isOutOfRange()) t.warn("goal out of turret range" + (goalBehind ? " (behind the robot: holding)" : ""));
        t.line(name(), fmt("%.0f deg%s", part.getAngle(), follow.isOn() ? " following: " + source.words() : ""));
        part.addTelemetry(t);
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        Pad op = plan.operator();
        op.rightStickX.use(v -> manual(-v)).says("by hand (right = clockwise)");
        if (follows()) op.back.toggle(follow).says("follow the goal on/off");
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        double cpd = countsPerDegree();
        double lo = t.number("min", -90), hi = t.number("max", 90);
        out.add(fmt("motor.%s = lift, part=%s, min=%.0f, max=%.0f, gravity=0", device, motorId, lo * cpd, hi * cpd));
        // start = min: the simulator counts the shape's angle from the motor's min stop.
        out.add(fmt("shape.%s = turret, at=0,0,8, size=6x6x2, motor=%s, ticksPerDeg=%.3f, start=%.0f   # TODO measure",
                device, device, cpd, lo));
    }
}
