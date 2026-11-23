package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.launcher.ShotSight;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.vision.AimController;
import com.crossbotics.ftchelper.vision.MovingShot;
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
 *     public static double goalX = 13, goalY = -13;           // the goal on the field (odometry backup)
 *     public static boolean mirrorForRed = false;             // the robot's one setting (BIOBUZZ: false)
 *     public static double turnKp = 0.02, turnKd = 0.002, maxTurnPower = 0.5, minTurnPower = 0, toleranceDeg = 2;
 *     public static double aimDriveSpeed = 0.5;               // aim mode on: forward and strafe at this share
 *     public static double cameraOffset = 0, rumbleDeg = 4;
 *     public static double maxGoalDistance = 170, cameraOdometryAgree = 6;   // inches: odometry sanity checks
 *     public static boolean shootOnMove = false;              // shooting while driving: off until tuned
 *     public static double flightSec = 0.8, flightSecPerIn = 0, maxLeadIn = 24;
 *     public static double shooterForwardIn = 0, shooterLeftIn = 0;   // the shooter (turret pivot) from the center
 *     public static double cameraForwardIn = 0, cameraLeftIn = 0;     // a camera on the body, from the center
 *     public static boolean aimOnFire = false;                // quick turn to the goal on a fire press (off unless set)
 *     public static double aimOnFireSec = 0.5;                // the most it waits for the turn before it fires anyway
 * }
 * }</pre>
 *
 * <p><b>Quick turn on fire</b> ({@code aimOnFire = true}, off unless you set it: it turns the robot, so a
 * team must choose it). For a robot with no turret, with whole-robot aim OFF: a press of the fire button
 * with the goal in view turns the robot to the goal, fires as soon as it is within {@code toleranceDeg}
 * (or after {@code aimOnFireSec}, 0.5 s, whichever comes first: a press always fires), and gives the turn
 * back when the shots are done. The driver's turn stick still wins. No goal in view: it fires as before, with no
 * turn. Goal lost during the turn (a blurred picture): it waits up to {@code aimOnFireSec}, then fires.
 * Not while the launcher sprays (the last seconds, or tap-then-hold): SPRAY does not wait. It uses the same turn numbers as whole-robot aim ({@code turnKp} and the others).
 *
 * <p><b>Continuous fire</b> ({@code Tunables.Launcher.continuousOnHold}): from the press until the group
 * ends, the robot turns to the goal (the driver's turn stick wins). The group's start is strict
 * ({@link #shotSight()}): {@code aimOnFireSec} never lets it through, and neither does aim mode being on.
 * While it feeds it keeps the {@code cameraOffset} it started with.
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
 * seen from your drivers' wall, x inches to the right of the field's center line, y inches from the
 * center toward the far wall, written for BLUE. For RED with {@code mirrorForRed = true} (the default,
 * like autonomous) x is mirrored; a field turned half a turn between the alliances (BIOBUZZ) uses
 * {@code mirrorForRed = false}: the same numbers from the red wall. It is the robot's one setting
 * ({@link RobotDefinition#mirrorForRed()}): the autonomous and TeleOp's reset spot use it too. Put the point where the camera
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
 * <p><b>Shooting on the move</b> ({@code shootOnMove = true}, off by default: tune it on a real robot
 * first). The goal every part gets ({@link #goal()}, the turret's, the launcher's distance) is then
 * corrected for the robot's motion: a camera picture is brought forward to now (the robot turned and
 * moved since it was taken), and the aim leads the shot by the robot's speed x the flight time, because
 * the piece keeps the robot's speed (see {@link MovingShot}). Needs odometry (the speed comes from it).
 * It works for a turret that follows the goal (camera on the turret or on the body, or odometry only)
 * and for whole-robot aim with a fixed shooter; the shooter and the camera need not be at the robot's
 * center ({@code shooterForwardIn, shooterLeftIn, cameraForwardIn, cameraLeftIn}).
 * {@link #movingShotMode()} switches it with a button.
 *
 * Docs: docs/reference/robot-java.md (section "Aim"). Tutorial: docs/add-to-robot/05-launcher.md
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
    // The real clock (not core.Time): the camera stamps its pictures with System.nanoTime(), so a picture's
    // age must be measured on the same clock.
    static LongSupplier clock = System::nanoTime;

    private final RobotDefinition robot;
    // Whole-robot aim: off until a button (or code) switches it on.
    private final Mode mode = new Mode("Aim the robot", false);
    // Tunables.Aim: the goal point (only when both are written) and the mirroring rule.
    private final boolean hasGoalPoint;
    private final double goalX, goalY;
    private final double cameraMaxAgeMs;
    // Sanity checks for the odometry: the farthest the goal can be (inches), and how far apart the
    // camera's and the odometry's distances may be before the Driver Station warns.
    private final double maxGoalDistanceIn, agreeIn;
    // Live numbers (in the tuning menu when the section exists).
    private final DoubleSupplier turnKp, turnKd, maxTurnPower, minTurnPower, toleranceDeg, cameraOffset, rumbleDeg;
    // The angle aim: aim goalFrontIn inches in front of the tags (where the face points), on or off.
    private final Mode angleAim;
    private final DoubleSupplier goalFrontIn;
    // The angle aim's correction (degrees) from the last few pictures, and when each was taken: the middle one is
    // used, because a tag's facing is the noisiest part of its pose (one odd picture must not swing the aim).
    private final double[] openingTurn = new double[5];
    private final long[] openingTurnAt = new long[5];
    private int openingTurns = 0, openingNext = 0;
    private long lastOpeningPicture = Long.MIN_VALUE;
    private double lastOpeningDeg = Double.NaN;   // the correction used last (NaN: none), for the screen
    private long lastOpeningAt = 0;
    // While whole-robot aim is on: the share of the driver's forward and strafe (the aim keeps up).
    private final DoubleSupplier aimDriveSpeed;
    // The turn controller, made again if kP or kD is tuned.
    private AimController controller = null;
    private double controllerKp = Double.NaN, controllerKd = Double.NaN;
    // For the Driver Station: what the whole-robot aim used this loop.
    private Source lastSource = Source.NONE;
    private boolean driverTurning = false;
    // Shooting on the move: on from START when Tunables.Aim.shootOnMove = true; a button may switch it.
    private final Mode movingShot;
    // The flight time (seconds, and extra seconds per inch), the largest lead (inches), and the camera's
    // picture age when the team writes one (NaN: the camera's own timestamp, the normal case).
    private final DoubleSupplier flightSec, flightSecPerIn, maxLeadIn, cameraLatencyMs;
    // Where the shooter (a turret's pivot, or a fixed launcher's exit) and a camera on the body are,
    // from the robot's center: inches forward and left. Used while shooting on the move.
    private final double shooterForwardIn, shooterLeftIn, cameraForwardIn, cameraLeftIn;
    // The last lead worked out (for the Driver Station), and when (nanoseconds).
    private MovingShot.Result lastLead = null;
    // Locked stays locked until the error passes toleranceDeg + this (no flicker at the edge).
    private final double lockMarginDeg;
    private final double sweepStepDeg, sweepPower;
    private final double goalSteadySec, goalSteadyDeg, goalSteadyIn;
    private boolean lockedLast = false;
    private long lastLeadNanos = 0;
    // Quick turn on fire (Tunables.Aim aimOnFire, off unless set; aimOnFireSec 0.5): on, and the most it
    // waits for the turn. fireTurnStart: when the driver's fire press started a turn (nanoseconds), -1: none.
    private final boolean aimOnFire;
    private final double aimOnFireSec;
    private long fireTurnStart = -1;
    // The launcher whose fire press started the turn (the turn ends when ITS shots are done), or null.
    private Launcher fireTurnLauncher = null;

    Aim(RobotDefinition robot, TunableSection t) {
        this.robot = robot;
        hasGoalPoint = t.has("goalX") && t.has("goalY");
        goalX = hasGoalPoint ? t.number("goalX", 0) : 0;
        goalY = hasGoalPoint ? t.number("goalY", 0) : 0;
        cameraMaxAgeMs = t.has("cameraMaxAgeMs") ? t.number("cameraMaxAgeMs", 150) : 150;
        maxGoalDistanceIn = t.has("maxGoalDistance") ? t.number("maxGoalDistance", 170) : 170;
        agreeIn = t.has("cameraOdometryAgree") ? t.number("cameraOdometryAgree", 6) : 6;
        turnKp = live(t, "turnKp", 0.02, 0.002);
        turnKd = live(t, "turnKd", 0.002, 0.0005);
        maxTurnPower = live(t, "maxTurnPower", 0.5, 0.05);
        minTurnPower = live(t, "minTurnPower", 0, 0.01);
        toleranceDeg = live(t, "toleranceDeg", 2, 0.5);
        aimDriveSpeed = live(t, "aimDriveSpeed", 0.5, 0.05);
        cameraOffset = live(t, "cameraOffset", 0, 0.5);
        angleAim = new Mode("Aim into the opening", t.has("angleAim") && t.flag("angleAim", false));
        goalFrontIn = live(t, "goalFrontIn", 0, 0.5);
        rumbleDeg = live(t, "rumbleDeg", 4, 0.5);
        movingShot = new Mode("Shoot on the move", t.has("shootOnMove") && t.flag("shootOnMove", false));
        flightSec = live(t, "flightSec", 0, 0.05);
        flightSecPerIn = live(t, "flightSecPerIn", 0, 0.001);
        maxLeadIn = live(t, "maxLeadIn", 24, 2);
        cameraLatencyMs = t.has("cameraLatencyMs") ? live(t, "cameraLatencyMs", 0, 5) : () -> Double.NaN;
        shooterForwardIn = t.has("shooterForwardIn") ? t.number("shooterForwardIn", 0) : 0;
        shooterLeftIn = t.has("shooterLeftIn") ? t.number("shooterLeftIn", 0) : 0;
        cameraForwardIn = t.has("cameraForwardIn") ? t.number("cameraForwardIn", 0) : 0;
        cameraLeftIn = t.has("cameraLeftIn") ? t.number("cameraLeftIn", 0) : 0;
        lockMarginDeg = t.has("lockMarginDeg") ? Math.abs(t.number("lockMarginDeg", 1)) : 1;
        sweepStepDeg = t.has("sweepStepDeg") ? Math.max(1, Math.abs(t.number("sweepStepDeg", 15))) : 15;
        sweepPower = t.has("sweepPower") ? Math.min(1, Math.abs(t.number("sweepPower", 0.3))) : 0.3;
        goalSteadySec = t.has("goalSteadySec") ? Math.max(0, t.number("goalSteadySec", 1)) : 1;
        goalSteadyDeg = t.has("goalSteadyDeg") ? Math.abs(t.number("goalSteadyDeg", 2)) : 2;
        goalSteadyIn = t.has("goalSteadyIn") ? Math.abs(t.number("goalSteadyIn", 2)) : 2;
        aimOnFire = t.has("aimOnFire") && t.flag("aimOnFire", false);
        aimOnFireSec = t.has("aimOnFireSec") ? Math.max(0, t.number("aimOnFireSec", 0.5)) : 0.5;
        // Whole-robot aim turns on only with a goal in view (last season's rule): a press with no goal
        // leaves it off (one buzz with .rumble()), so the robot never waits to turn toward nothing.
        mode.onlyWhen(() -> goal() != null, "no goal in view");
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

    // This camera's goal, only if the picture is fresh and has a direction; else null. While shooting
    // on the move: brought forward to now and led for the flight (see moving()).
    TargetObservation cameraGoal(Camera camera) {
        if (camera == null) return null;
        TargetObservation g = camera.goal();
        if (g == null || !g.hasBearing() || !g.isFresh(clock.getAsLong(), cameraMaxAgeMs)) return null;
        TargetObservation opening = intoOpening(g);
        if (opening == g) return moving(g, camera);
        // The angle aim: the direction from the opening, the distance from the tags (the shot table's), each
        // brought forward and led on its own while shooting on the move. The opening's last, so lead() is the aim's.
        TargetObservation tags = moving(g, camera);
        TargetObservation aimed = moving(opening, camera);
        return new TargetObservation(aimed.id, aimed.name, aimed.bearingDeg, tags.distanceIn, aimed.timeNanos,
                aimed.rollDeg, aimed.facingDeg);
    }

    // The angle aim: the point goalFrontIn inches in front of the target, the way its face points, along
    // the floor. Straight on, that point is behind the same line: no change. From an angle it is off to
    // the side of the front, so the aim turns that way. The distance stays the target's (the shot table
    // was made with it). Off, no face direction, or no distance: the target as it is.
    TargetObservation intoOpening(TargetObservation g) {
        double front = goalFrontIn.getAsDouble();
        if (!angleAim.isOn() || front == 0 || !g.hasDistance() || Double.isNaN(g.facingDeg)) return g;
        double b = Math.toRadians(g.bearingDeg), f = Math.toRadians(g.facingDeg);
        double forward = g.distanceIn * Math.cos(b) + front * Math.cos(f);
        double left = g.distanceIn * Math.sin(b) + front * Math.sin(f);
        double turn = Angles.wrapDegrees(Math.toDegrees(Math.atan2(left, forward)) - g.bearingDeg);
        // Each picture once (goal() is asked many times a loop), then the middle of the fresh ones.
        long now = clock.getAsLong();
        if (g.timeNanos != lastOpeningPicture) {
            lastOpeningPicture = g.timeNanos;
            openingTurn[openingNext] = turn;
            openingTurnAt[openingNext] = now;
            openingNext = (openingNext + 1) % openingTurn.length;
            openingTurns = Math.min(openingTurns + 1, openingTurn.length);
        }
        java.util.List<Double> fresh = new java.util.ArrayList<>();
        for (int i = 0; i < openingTurns; i++) {
            if ((now - openingTurnAt[i]) / 1e9 <= 0.5) fresh.add(openingTurn[i]);
        }
        java.util.Collections.sort(fresh);
        double used = fresh.isEmpty() ? turn : fresh.get(fresh.size() / 2);
        lastOpeningDeg = used;
        lastOpeningAt = now;
        return g.withBearing(g.bearingDeg + used);
    }

    /**
     * The angle aim: while it is on, the aim (whole robot or turret) points {@code Tunables.Aim.goalFrontIn}
     * inches in front of the goal's tags, the way they face, not at the tags. Why: a goal's opening can sit
     * in front of its tags (BIOBUZZ: the CELL's opening is about 3 in in front of the tags under it). Straight
     * on, nothing changes. From an angle, the opening is off to one side of the tags, so the aim turns toward
     * the front by a few degrees (more up close). The distance for the speed stays the tags'. On from START
     * when {@code Tunables.Aim.angleAim = true}. A button can switch it:
     * {@code g1.a.toggle(robot.aim().angleAimMode())}. Needs a camera that knows which way the tags face
     * (a webcam; the simulator's too); with a Limelight it does nothing.
     */
    public Mode angleAimMode() {
        return angleAim;
    }

    /**
     * The goal worked out from the odometry: its direction from the robot's front (degrees,
     * counterclockwise + = to the LEFT, like a camera's bearing) and its distance from the robot's
     * center (inches). Null if the robot has no odometry, the odometry is not ready, {@code Tunables.Aim}
     * has no goal point, the pose was never set (no autonomous, no {@code setPose}), or the goal would
     * be farther than {@code maxGoalDistance} (a wrong pose). {@link #odometryProblem()} says which.
     */
    public TargetObservation fromOdometry() {
        TargetObservation o = odometryGoalUnchecked();
        // A goal farther than the field allows: the pose is wrong. Aiming there would be worse than not aiming.
        if (o == null || o.distanceIn > maxGoalDistanceIn) return null;
        return moving(o, null);
    }

    /**
     * Why the odometry backup is off now, in a few words for the Driver Station; "" when it works (or
     * the robot has no odometry or no goal point: then there is nothing to warn about).
     */
    public String odometryProblem() {
        PoseTracker tracker = robot.odometry();
        if (!hasGoalPoint || tracker == null) return "";
        if (!tracker.isReady()) return "the odometry is not ready (" + tracker.status() + ")";
        if (tracker.isPoseSet()) robot.poseUnsetBy = null;
        if (!tracker.isPoseSet() && robot.poseUnsetBy != null) {
            // It was set, then the alliance was switched: say that, not "never set".
            return "the pose is not set for " + robot.poseUnsetBy + " (the alliance was switched): camera only. Set it: "
                    + (robot.resetPoseHint != null ? robot.resetPoseHint : "robot.resetPose(...)");
        }
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
        return robot.alliance() == Alliance.RED && robot.mirrorForRed() ? -goalX : goalX;
    }

    /**
     * The camera that aims the whole robot: the first one with {@code goal(...)} in Robot.java, or null.
     * The Shot Tuner uses it, so it sees the goal through the same camera (and lighting test) as TeleOp.
     */
    public Camera camera() {
        return aimingCamera();
    }

    /**
     * The next {@link #isAimed()} must get within {@code toleranceDeg} again (not the wider band an aim that was
     * already locked keeps). TeleOp's quick turn on fire does this for each press; the Shot Tuner for each shot.
     */
    public void relock() {
        lockedLast = false;
    }

    /**
     * True when a turret follows the goal ({@code follows(camera)} or {@code followsOdometry()} in
     * Robot.java): the turret aims, and {@link #mode()} (whole-robot aim) cannot be used.
     */
    public boolean turretAims() {
        return followingTurretInRobot() != null;
    }

    // The camera that aims the whole robot: the first one with a goal(...) in Robot.java, or null.
    Camera aimingCamera() {
        for (Module<?> m : robot.modules()) {
            if (m instanceof Camera && m.isPresent() && ((Camera) m).hasGoal()) return (Camera) m;
        }
        return null;
    }

    // ----------------------------- shooting on the move -----------------------------

    /**
     * Shooting on the move: while it is on, the goal every part gets is corrected for the robot's
     * motion (the camera picture's age, and the lead for the flight time; see {@link MovingShot}). On
     * from START when {@code Tunables.Aim.shootOnMove = true}; off (and nothing changes) otherwise. A
     * button can switch it: {@code g1.y.toggle(robot.aim().movingShotMode())}. Needs odometry.
     */
    public Mode movingShotMode() {
        return movingShot;
    }

    /**
     * The lead worked out last (degrees and inches, see {@link MovingShot.Result}), or null if none in
     * the last {@code cameraMaxAgeMs} (shooting on the move off, no goal, no odometry).
     */
    public MovingShot.Result lead() {
        if (lastLead == null || (clock.getAsLong() - lastLeadNanos) / 1e6 > cameraMaxAgeMs) return null;
        return lastLead;
    }

    /**
     * True while shooting on the move is on and the robot moves (faster than 2 in/s, or turning faster
     * than 5 degrees per second): the aim is being led now.
     */
    public boolean isMovingShot() {
        PoseTracker tr = robot.odometry();
        return movingShot.isOn() && tr != null && tr.isReady()
                && (tr.speedInPerSec() > 2 || Math.abs(tr.turnDegPerSec()) > 5);
    }

    // The goal corrected for the robot's motion while shooting on the move (else unchanged), in the
    // form its users expect (see Turret.update, aimErrorDeg, and assistTurn here):
    // - a robot with a turret that follows the goal: the bearing is the turret angle to go to (the
    //   odometry's, a camera on the body's), or how far to turn the turret (a camera ON that turret);
    // - no following turret (whole-robot aim, a fixed shooter): how far to turn the whole robot.
    // The distance is from the shooter (Tunables.Aim.shooterForwardIn, shooterLeftIn) to the virtual goal.
    // camera: the camera that saw it (null: the odometry's goal, measured from the robot's center).
    private TargetObservation moving(TargetObservation g, Camera camera) {
        if (!movingShot.isOn()) return g;
        PoseTracker tr = robot.odometry();
        if (tr == null || !tr.isReady()) return g;
        Turret turret = followingTurretInRobot();
        boolean onTurret = turret != null && camera != null && turret.carries(camera);
        double turretDeg = onTurret ? turret.angle() : 0;
        // How old the goal is: the odometry's is worked out now; a camera's is as old as its picture
        // (its own timestamp), unless Tunables.Aim.cameraLatencyMs says otherwise.
        double latencyMs = g.fromOdometry() ? 0
                : !Double.isNaN(cameraLatencyMs.getAsDouble()) ? cameraLatencyMs.getAsDouble()
                : Math.max(0, g.ageMs(clock.getAsLong()));
        double turn = tr.turnDegPerSec();
        if (!g.hasDistance()) {
            // No distance: only the direction can be corrected (the robot's turn since the picture).
            return new TargetObservation(g.id, g.name, g.bearingDeg - turn * latencyMs / 1000.0, g.distanceIn, g.timeNanos);
        }
        // Where the goal was from the robot's center: the odometry's from the center; a camera on the
        // turret sits at the turret's pivot (the shooter) and faces where the turret points; a camera
        // on the body at Tunables.Aim.cameraForwardIn, cameraLeftIn, facing forward.
        double[] at = g.fromOdometry() ? MovingShot.seenFrom(g.bearingDeg, g.distanceIn, 0, 0, 0)
                : onTurret ? MovingShot.seenFrom(g.bearingDeg, g.distanceIn, shooterForwardIn, shooterLeftIn, turretDeg)
                : MovingShot.seenFrom(g.bearingDeg, g.distanceIn, cameraForwardIn, cameraLeftIn, 0);
        double[] v = MovingShot.toRobotFrame(tr.velocityX(), tr.velocityY(), tr.pose().headingDeg);
        MovingShot shot = new MovingShot().shooterAt(shooterForwardIn, shooterLeftIn)
                .flightSec(flightSec.getAsDouble(), flightSecPerIn.getAsDouble()).maxLeadIn(maxLeadIn.getAsDouble());
        if (turret != null) shot.onTurret();
        MovingShot.Result r = shot.solve(at[0], at[1], v[0], v[1], turn, latencyMs / 1000.0);
        lastLead = r;
        lastLeadNanos = clock.getAsLong();
        // A camera on the turret: how far to turn it from where it points now.
        double bearing = onTurret ? Angles.wrapDegrees(r.aimDeg - turretDeg) : r.aimDeg;
        // Same picture time: a turret uses each picture once, and the picture's age stays honest.
        return new TargetObservation(g.id, g.name, bearing, r.distanceIn, g.timeNanos);
    }

    // ----------------------------- the whole robot aims -----------------------------

    /**
     * Whole-robot aim, for a robot with no turret: while it is on, TeleOp turns the robot toward the
     * goal and the driver keeps forward and sideways, slowed to {@code aimDriveSpeed} (0.5) so the aim keeps
     * up. The driver's turn stick still wins: move it and the robot turns as the stick says; let go and the
     * aim takes over again. Off at START.
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

    // True while whole-robot aim is on (never throws, unlike mode() on a robot with a following turret).
    boolean modeOn() {
        return mode.isOn();
    }

    // For aimAtGoal: how far to turn the robot to face the goal now (positive = left), NaN if not seen.
    double bearingNow() {
        TargetObservation g = goal();
        return g == null ? Double.NaN : corrected(g).bearingDeg;
    }

    // aimAtGoal's sweep: degrees per step and the turn power (Tunables.Aim sweepStepDeg 15, sweepPower 0.3).
    double sweepStepNow() {
        return sweepStepDeg;
    }

    double sweepPowerNow() {
        return sweepPower;
    }

    // waitForGoal: how long the goal must stay in view and still, and how much it may move meanwhile
    // (Tunables.Aim goalSteadySec 1, goalSteadyDeg 2, goalSteadyIn 2).
    double goalSteadySec() {
        return goalSteadySec;
    }

    double goalSteadyDeg() {
        return goalSteadyDeg;
    }

    double goalSteadyIn() {
        return goalSteadyIn;
    }

    double toleranceNow() {
        return toleranceDeg.getAsDouble();
    }

    double maxTurnPowerNow() {
        return maxTurnPower.getAsDouble();
    }

    // ----------------------------- quick turn on fire -----------------------------

    /** True if a fire press turns the robot to the goal first ({@code Tunables.Aim.aimOnFire}; off unless set). */
    public boolean aimsOnFire() {
        return aimOnFire;
    }

    /**
     * True while a quick turn on fire is working: the driver pressed fire with the goal in view, and the
     * shots are not done yet (or the button is still held). False with {@code aimOnFire} off.
     */
    public boolean isTurningToFire() {
        if (fireTurnStart < 0) return false;
        // Aim mode took over, the shots are done, or the launcher sprays now (SPRAY does not wait, so a
        // turn would only throw pieces to the side): the turn is the driver's again.
        if (mode.isOn() || fireTurnLauncher == null || !fireTurnLauncher.firingForDriver() || fireTurnLauncher.spraysNow()) {
            fireTurnStart = -1;
            fireTurnLauncher = null;
            return false;
        }
        return true;
    }

    // The launcher's fire() (the driver's press) calls this: start a quick turn if it is on and can work.
    // Not with aim mode on (it is aiming already), not with a following turret (the turret aims), not
    // with no goal in view (fire as before), not while that launcher sprays (SPRAY does not wait). A press
    // during a turn keeps the same start time.
    void driverFired(Launcher launcher) {
        if (!aimOnFire || mode.isOn() || isTurningToFire() || goal() == null) return;
        if (launcher.spraysNow() || followingTurretInRobot() != null) return;
        fireTurnStart = clock.getAsLong();
        fireTurnLauncher = launcher;
        lockedLast = false;   // it must get inside the tolerance on this turn
    }

    // A launcher asks this before it feeds a piece: false only while a quick turn that this launcher
    // started is still turning (the goal in view, not within the tolerance, and aimOnFireSec not over yet).
    boolean readyToFire(Launcher launcher) {
        if (!isTurningToFire() || fireTurnLauncher != launcher) return true;
        if ((clock.getAsLong() - fireTurnStart) / 1e9 >= aimOnFireSec) return true;   // a press always fires
        // Lost the goal during the turn (the turn blurs the picture): keep waiting, up to aimOnFireSec,
        // so the piece is not fed while the robot still turns.
        TargetObservation g = goal();
        return g != null && withinLock(corrected(g).bearingDeg);
    }

    // ----------------------------- continuous fire -----------------------------

    /**
     * What continuous fire judges the aim by, now: a fresh goal (the camera, or the odometry backup), whether
     * the camera itself sees it ({@link ShotSight#fromCamera}), its distance, the error, and whether it is inside
     * {@code toleranceDeg}, strictly: no lock margin and no time limit that lets it pass, with aim mode on or
     * off. A group starts only when the camera sees the goal; during a group the odometry backup may still
     * steer, but it does not stop the "goal lost" clock. A turret that follows the goal answers with its own aim.
     *
     * <pre>{@code
     * launcher.continuous().start(() -> robot.aim().shotSight().withDistance(filteredInches));
     * }</pre>
     */
    public ShotSight shotSight() {
        double tol = toleranceDeg.getAsDouble();
        Turret turret = followingTurretInRobot();
        double offset = turret == null ? offsetNow() : Double.NaN;
        TargetObservation g = goal();
        if (g == null) return new ShotSight(false, Double.NaN, Double.NaN, false, tol, lockMarginDeg, offset);
        double error = turret == null ? corrected(g).bearingDeg : turret.aimErrorDeg();
        boolean aimed = turret == null ? Math.abs(error) <= tol : turret.isAimed();
        return new ShotSight(true, !g.fromOdometry(), g.distanceIn, error, aimed, tol, lockMarginDeg, offset);
    }

    // True while a launcher's press or group of continuous fire wants the whole robot turned to the goal.
    // Asked of the launchers each time, so it cannot stay on after the group is gone.
    private boolean groupTurning() {
        boolean wanted = false;
        for (Module<?> m : robot.modules()) {
            if (m instanceof Launcher && m.isPresent() && ((Launcher) m).wantsGroupAim()) wanted = true;
        }
        return wanted && followingTurretInRobot() == null;
    }

    // The camera offset in use: the live one, or the one a feeding group started with.
    private double offsetNow() {
        for (Module<?> m : robot.modules()) {
            if (!(m instanceof Launcher) || !m.isPresent()) continue;
            double kept = ((Launcher) m).groupAimOffset();
            if (!Double.isNaN(kept)) return kept;
        }
        return cameraOffset.getAsDouble();
    }

    // A turret that follows the goal (follows(camera) or followsOdometry() in Robot.java), or null.
    Turret followingTurretInRobot() {
        for (Module<?> m : robot.modules()) {
            // A turret that is not on the robot yet (optional) does not aim: whole-robot aim may be used.
            if (m instanceof Turret && m.isPresent() && ((Turret) m).followsGoal()) return (Turret) m;
        }
        return null;
    }

    /**
     * The turn TeleOp uses this loop (clockwise +, like the drive's turn), from the driver's turn stick
     * after shaping: unchanged while the mode is off (and no quick turn on fire is working) or the stick
     * is moved; the aim's turn otherwise (0 when there is no goal: the robot does not turn on its own).
     */
    /**
     * The same turn as TeleOp's, for an OpMode that drives with its own {@code DriverControls} (the Shot
     * Tuner): {@code driving.turnAssist(robot.aim()::turnFor)}. While {@link #mode()} is on it turns the
     * robot to the goal (with {@code cameraOffset} and the angle aim); the driver's stick still wins.
     *
     * @param driverTurn the driver's turn after shaping, -1 to 1 (clockwise +)
     * @return the turn to use this loop, -1 to 1
     */
    public double turnFor(double driverTurn) {
        return assistTurn(driverTurn);
    }

    double assistTurn(double driverTurn) {
        if (!mode.isOn() && !isTurningToFire() && !groupTurning()) {
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

    /**
     * The share of the driver's forward and strafe TeleOp uses this loop: {@code Tunables.Aim.aimDriveSpeed}
     * (0.5) while whole-robot aim is on, else 1. Why: at full speed the robot moves faster than the aim can
     * turn, so the goal drifts off and the robot only catches up when it stops. Slower, the aim keeps the
     * goal centered while the robot moves. The turn is not slowed.
     */
    double driveScale() {
        if (!mode.isOn()) return 1;
        return Math.max(0.1, Math.min(1, aimDriveSpeed.getAsDouble()));
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
        return new TargetObservation(g.id, g.name, g.bearingDeg - offsetNow(), g.distanceIn, g.timeNanos);
    }

    // ----------------------------- how far off -----------------------------

    /**
     * How many degrees the aim is off now (any sign), NaN if unknown. With whole-robot aim on (or a quick
     * turn on fire or continuous fire working): the robot's front to the goal. Otherwise, a turret that is following: its error. Neither: NaN.
     */
    public double errorDeg() {
        if (mode.isOn() || isTurningToFire() || groupTurning()) {
            TargetObservation g = goal();
            return g == null ? Double.NaN : corrected(g).bearingDeg;
        }
        Turret t = followingTurret();
        return t == null ? Double.NaN : t.aimErrorDeg();
    }

    /**
     * True when aimed: whole-robot aim on (or turning for a fire press) and within {@code toleranceDeg}, or a following turret that
     * is aimed AT THE GOAL NOW (see {@link Turret#isAimed()}: a goal seen this moment, homed, in range).
     * Losing the goal makes it false at once, so a launcher gated on it never fires blind. Use it as a
     * launcher check: {@code .requireReady("aimed", () -> aim().isAimed())}.
     */
    public boolean isAimed() {
        if (mode.isOn() || isTurningToFire() || groupTurning()) return withinLock(errorDeg());
        Turret t = followingTurret();
        return t != null && t.isAimed();
    }

    // Within the tolerance; once in, it stays in until the error passes toleranceDeg + lockMarginDeg
    // (a camera's jitter at the edge would make LOCKED and a launcher's "aimed" check flicker).
    private boolean withinLock(double errorDeg) {
        if (Double.isNaN(errorDeg)) {
            lockedLast = false;
            return false;
        }
        double limit = toleranceDeg.getAsDouble() + (lockedLast ? lockMarginDeg : 0);
        lockedLast = Math.abs(errorDeg) <= limit;
        return lockedLast;
    }

    /** The error that starts the buzz of {@code rumbleWhenAimed()} (Tunables.Aim.rumbleDeg, default 4). */
    public double rumbleDeg() {
        return rumbleDeg.getAsDouble();
    }

    /**
     * The error the aim buzz ({@code rumbleWhenAimed()}) uses, degrees (counterclockwise +), or NaN = no buzz.
     * While the library aims (whole-robot aim on, the quick turn on fire, a turret that follows the goal) it is
     * {@link #errorDeg()}. On a robot with no turret and the aim off, it is the camera's bearing to the goal:
     * the driver turns the robot by hand, and the gamepad buzzes when it faces the goal. Only the buzz: the
     * launcher's "aimed" check ({@link #isAimed()}) stays as it is.
     *
     * @return degrees to turn to face the goal; NaN when nothing aims and no goal is in view
     */
    public double buzzErrorDeg() {
        double e = errorDeg();
        if (!Double.isNaN(e) || mode.isOn() || isTurningToFire() || hasTurret()) return e;
        TargetObservation g = cameraGoal(aimingCamera());   // the camera only: an odometry guess can be off
        return g == null ? Double.NaN : corrected(g).bearingDeg;
    }

    // True if the robot has a turret on it now (a turret the driver aims by hand moves the camera's bearing,
    // so the whole-robot "by hand" buzz would be wrong for it).
    private boolean hasTurret() {
        for (Module<?> m : robot.modules()) {
            if (m instanceof Turret && m.isPresent()) return true;
        }
        return false;
    }

    // The first turret with its follow mode on, or null.
    private Turret followingTurret() {
        for (Module<?> m : robot.modules()) {
            if (m instanceof Turret && m.isPresent() && ((Turret) m).followMode().isOn()) return (Turret) m;
        }
        return null;
    }

    // ----------------------------- seen, in range, locked -----------------------------

    /** How far along the aim is, for the Driver Station line, lights or a buzz: each stage includes the ones before. */
    public enum Stage {
        /** The robot cannot aim (no camera with a goal, no odometry goal point), or no goal now. */
        NOT_SEEN,
        /** The goal is known (camera, or the odometry backup). */
        SEEN,
        /**
         * ... and its distance is inside the launcher's shot table, or an estimate its extended ends reach
         * ({@code Tunables.Launcher.extendTableEnds}), or the launcher has no table.
         */
        IN_RANGE,
        /** ... and the robot (or the following turret) points at it, within {@code toleranceDeg}. */
        LOCKED;

        /** "not seen", "seen", "in range", "LOCKED". */
        public String words() {
            return this == NOT_SEEN ? "not seen" : this == SEEN ? "seen" : this == IN_RANGE ? "in range" : "LOCKED";
        }
    }

    /** True if the robot can find the goal at all: a camera with goal(...) in Robot.java, or the odometry backup. */
    public boolean canAim() {
        return aimingCamera() != null || (hasGoalPoint && robot.odometry() != null);
    }

    /**
     * The aim's stage now: NOT_SEEN, SEEN, IN_RANGE or LOCKED. Locked: a following turret is aimed, or the
     * robot's front is within {@code toleranceDeg} of the goal (aim mode on or not). E.g. lights:
     * {@code robot.aim().stage() == Aim.Stage.LOCKED}.
     */
    public Stage stage() {
        TargetObservation g = goal();
        if (g == null) {
            lockedLast = false;   // lost: a goal that comes back must get inside the tolerance again
            return Stage.NOT_SEEN;
        }
        Launcher launcher = firstLauncher();
        Boolean range = launcher == null ? null : launcher.inRange(g.distanceIn);
        if (Boolean.FALSE.equals(range)) return Stage.SEEN;
        Turret t = followingTurret();
        boolean locked = t != null ? t.isAimed() : withinLock(corrected(g).bearingDeg);
        return locked ? Stage.LOCKED : Stage.IN_RANGE;
    }

    private Launcher firstLauncher() {
        for (Module<?> m : robot.modules()) if (m instanceof Launcher && m.isPresent()) return (Launcher) m;
        return null;
    }

    // The Driver Station line: "Goal: LOCKED (camera, 52 in, 0.8 deg off)".
    private String stageLine() {
        TargetObservation g = goal();
        Stage st = stage();
        if (g == null) {
            Camera c = aimingCamera();
            String why = c == null ? "" : c.whyNoGoal();
            return "not seen" + (c == null ? "" : why.isEmpty() ? " (the camera does not see it)" : " (" + why + ")");
        }
        Turret t = followingTurret();
        double off = t != null ? t.aimErrorDeg() : corrected(g).bearingDeg;
        Launcher launcher = firstLauncher();
        // Past the measured rows with extended ends: "estimated", or "estimated, speed limited".
        String estimate = launcher == null ? "" : launcher.estimateWords(g.distanceIn);
        String range = !estimate.isEmpty() ? ", " + estimate : st == Stage.SEEN ? ", out of the shot table's range" : "";
        return st.words() + " (" + sourceOf(g).words() + (Double.isNaN(g.distanceIn) ? "" : Module.fmt(", %.0f in", g.distanceIn))
                + range
                + (Double.isNaN(off) ? "" : Module.fmt(", %.1f deg off", off)) + ")";
    }

    // ----------------------------- the Driver Station -----------------------------

    /** The goal-point tolerance used by {@link Turret#isAimed()} for the goal error (Tunables.Aim.toleranceDeg, 2). */
    double toleranceDeg() {
        return toleranceDeg.getAsDouble();
    }

    // INIT: the alliance and how the goal point is used for it, so a wrong mirrorForRed is seen before
    // the match; and a loud warning when the odometry backup cannot work (no pose).
    void addInitTelemetry(RobotTelemetry t) {
        if (movingShot.isOn()) {
            // Shooting on the move: say it is on and with which numbers, so a wrong one is seen.
            if (robot.odometry() == null) t.warn("Aim: shootOnMove needs an odometry(...) line (the robot's speed): it does nothing");
            else if (flightSec.getAsDouble() <= 0 && flightSecPerIn.getAsDouble() <= 0) {
                t.warn("Aim: shootOnMove with flightSec = 0: only the camera picture's age is corrected, no lead");
            }
            t.line("Moving shot", Module.fmt("ON: flight %.2f s + %.4f s/in, lead at most %.0f in", flightSec.getAsDouble(),
                    flightSecPerIn.getAsDouble(), maxLeadIn.getAsDouble()));
        }
        if (!hasGoalPoint || robot.odometry() == null) return;
        String rule = (robot.mirrorForRed() ? "RED mirrors x: " : "the same numbers from each wall: ") + robot.mirrorSource();
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
        if (movingShot.isOn()) {
            // Works the lead out for this loop (goal() leads it), then says it.
            goal();
            MovingShot.Result r = lead();
            if (isMovingShot() && r != null) {
                t.line("Aim", Module.fmt("moving shot: lead %.1f deg, %.0f in", r.leadDeg, r.leadIn));
            }
            t.debug("Aim lead", r == null ? "no goal" : r.toString());
        }
        // Seen, in range, locked: always, on a robot that can aim (the drivers see it before they shoot).
        if (canAim()) t.line("Goal", stageLine());
        // The angle aim: how much it turns the aim now, or why it does nothing (so a camera that cannot tell
        // which way the tags face is seen, not guessed).
        if (angleAim.isOn()) {
            TargetObservation seen = aimingCamera() == null ? null : aimingCamera().goal();
            boolean fresh = (clock.getAsLong() - lastOpeningAt) / 1e9 <= 0.5;
            String words = seen == null ? "no goal in view"
                    : Double.isNaN(seen.facingDeg) ? "no face direction from this camera: aiming at the tags"
                    : goalFrontIn.getAsDouble() == 0 ? "goalFrontIn is 0: aiming at the tags"
                    : fresh && !Double.isNaN(lastOpeningDeg) ? Module.fmt("%+.1f deg toward the opening", lastOpeningDeg)
                    : "waiting for a picture";
            t.line("Angle aim", words);
        }
        if (mode.refusedLately()) t.warn("Aim mode not on: " + mode.whyRefused() + " (press again with the goal in view)");
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
