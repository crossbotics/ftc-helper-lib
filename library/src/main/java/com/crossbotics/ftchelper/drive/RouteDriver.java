package com.crossbotics.ftchelper.drive;

import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.util.MathUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Drives a route of field points, one loop at a time. It never waits: each loop, call
 * {@link #update()} and send what it returns to the wheels. It works out powers like
 * {@link AutoDrive#driveTo}. In autonomous, {@link AutoDrive#followRoute} runs it for you and blocks.
 *
 * <pre>{@code
 * RouteDriver route = new RouteDriver(autoConfig, 1.0, robot::pose, odometry::isReady, System::nanoTime)
 *         .checkLines(map::lineIsClear);
 * route.start(map.route(pose.x, pose.y, -56, -36), 0, 0.7, 2.0);
 * // every loop:
 * if (route.isRunning()) {
 *     double[] p = route.update();
 *     driving.drive(p[0], p[1], p[2], 1);
 * }
 * }</pre>
 *
 * <p>Controls.java's {@code route(...)} uses this for you. It ends by itself: at the last point, when
 * stuck, when the odometry stops working, or after {@code actionTimeoutSec} on one line of the route.
 *
 * Docs: docs/modules/drive.md, "RouteDriver"
 */
public final class RouteDriver {
    /** How a route ended. */
    public enum Result {
        /** It reached the last point and heading. */
        DONE,
        /** It made no progress: pushing a wall, a box you did not list, or another robot. */
        STALLED,
        /** One line of the route took longer than {@code actionTimeoutSec}. */
        TIMED_OUT,
        /** The odometry stopped working for more than 0.2 s: it cannot know where it is. */
        NO_ODOMETRY,
        /** {@link #cancel()} was called. */
        CANCELLED
    }

    /** A point on the way (not the last one) counts as reached within this many inches. */
    public static final double PASS_POINT_IN = 2.0;

    // A fast robot can slide past a point on the way, outside PASS_POINT_IN. Going back to it costs time
    // and can hit what is behind it: so past the point and this close also counts as reached.
    private static final double PASSED_BY_IN = 8.0;
    // Leaving a point early is only allowed when the line from here to the next point is clear (see
    // checkLines). Otherwise it drives on to the point itself, and this close counts as there. (FieldMap
    // keeps safe points this far outside every margin, so the line on from here is clear too.)
    static final double ON_POINT_IN = 0.5;

    /** Says if a straight line on the field is clear. {@link FieldMap#lineIsClear} is one. */
    public interface LineCheck {
        /** True if the robot's centre may drive straight from (x1, y1) to (x2, y2), inches. */
        boolean isClear(double x1, double y1, double x2, double y2);
    }

    private final AutoDriveConfig cfg;
    private final double limit;
    private final Supplier<FieldPose> pose;
    private final BooleanSupplier ready;
    private final LongSupplier clockNanos;

    private final List<double[]> points = new ArrayList<>();
    private int next = 0;
    private boolean running = false;
    private Result result = null;
    private double headingDeg, maxSpeed, toleranceIn;
    private PidController headingPid;
    private StallDetector stall, nearStall;
    private boolean wasNear;
    private double lineStartDist, lineStartX, lineStartY, lineStartSec, notReadySince, stallClock, lastLoopSec;
    private int closeLoops;
    private LineCheck lineCheck = null;

    /**
     * @param cfg        the autonomous numbers (turn gains, stuck rule, time limit)
     * @param powerLimit the drive's power limit, 0 to 1 (the slowest powers are raised to make up for it)
     * @param pose       where the robot is now
     * @param ready      false while the odometry cannot be trusted
     * @param clockNanos the clock, in nanoseconds (tests pass their own)
     */
    public RouteDriver(AutoDriveConfig cfg, double powerLimit, Supplier<FieldPose> pose, BooleanSupplier ready,
                       LongSupplier clockNanos) {
        if (cfg == null || pose == null || ready == null || clockNanos == null) {
            throw new IllegalArgumentException("RouteDriver: a setting is missing");
        }
        this.cfg = cfg;
        this.limit = Math.max(MathUtil.clamp(powerLimit, 0, 1), 1e-6);
        this.pose = pose;
        this.ready = ready;
        this.clockNanos = clockNanos;
    }

    /**
     * Gives it the map's line check (optional, but routes from {@link FieldMap#route} should have it). A
     * point on the way then counts as reached early (within 2 in, or slid past it) only if the line from
     * where the robot is to the next point is clear. Without it, a robot that left a safe point early, or
     * slid past a corner at an angle, could cut through a box's margin.
     *
     * <pre>{@code
     * route.checkLines(map::lineIsClear);
     * }</pre>
     *
     * @param check the check, or null for none
     * @return this, to chain
     */
    public RouteDriver checkLines(LineCheck check) {
        this.lineCheck = check;
        return this;
    }

    /**
     * Starts a route. A route with no points does not start (see {@link FieldMap#route}).
     *
     * @param route       the points, each {x, y}, inches; the last one is where it ends
     * @param headingDeg  the heading to turn to on the way (counterclockwise +; 0 = facing away)
     * @param maxPower    0.1 to 1
     * @param toleranceIn close enough at the last point, inches (1 is usual)
     * @return true if it started
     */
    public boolean start(List<double[]> route, double headingDeg, double maxPower, double toleranceIn) {
        running = false;
        result = null;
        points.clear();
        if (route == null || route.isEmpty()) return false;
        points.addAll(route);
        this.headingDeg = headingDeg;
        this.maxSpeed = Math.max(cfg.moveMinPower, MathUtil.clamp(maxPower, 0.1, 1.0));
        this.toleranceIn = Math.max(0.1, toleranceIn);   // 0 could never be reached
        headingPid = new PidController(cfg.turnKp, 0, cfg.turnKd, 0, clockNanos)
                .setAngleMode(true).setOutputLimits(-cfg.moveMaxTurnPower, cfg.moveMaxTurnPower);
        next = 0;
        notReadySince = Double.NaN;
        stallClock = 0;
        lastLoopSec = seconds();
        startLine();
        running = true;
        return true;
    }

    /** True while it drives. */
    public boolean isRunning() {
        return running;
    }

    /** How the last route ended, or null if it is still running or none has run. */
    public Result result() {
        return result;
    }

    /** Ends the route now. Send 0 to the wheels (or the driver's sticks) after this. */
    public void cancel() {
        if (running) finish(Result.CANCELLED);
    }

    /**
     * One loop of the route. Never waits.
     *
     * @return {forward, strafe right, turn clockwise}, each -1 to 1; all 0 once it has ended
     */
    public double[] update() {
        if (!running) return new double[3];
        double now = seconds();
        stallClock += Math.max(0, Math.min(AutoDrive.STALL_STEP_SEC, now - lastLoopSec));
        lastLoopSec = now;
        if (now - lineStartSec > cfg.actionTimeoutSec) return finish(Result.TIMED_OUT);
        if (!ready.getAsBoolean()) {
            // One bad reading is not a lost odometry: hold still for up to 0.2 s.
            if (Double.isNaN(notReadySince)) notReadySince = now;
            return now - notReadySince >= AutoDrive.ODOMETRY_LOST_SEC ? finish(Result.NO_ODOMETRY) : new double[3];
        }
        notReadySince = Double.NaN;

        FieldPose p = pose.get();
        double[] target = points.get(next);
        double dx = target[0] - p.x, dy = target[1] - p.y;
        double dist = Math.hypot(dx, dy);
        double headingErr = Angles.shortestDiff(headingDeg, p.headingDeg);
        double headingTol = Math.max(cfg.turnToleranceDeg, 2.0);
        boolean last = next == points.size() - 1;
        if (lineStartDist < 0) {
            lineStartDist = Math.max(dist, 1e-6);
            lineStartX = p.x;
            lineStartY = p.y;
        }
        // Past the point: the line from here to the point runs against the line it set off on.
        boolean past = dx * (target[0] - lineStartX) + dy * (target[1] - lineStartY) < 0;

        if (last) {
            // Close enough in place AND heading, for 3 loops in a row.
            if (dist <= toleranceIn && Math.abs(headingErr) <= headingTol) {
                if (++closeLoops >= 3) return finish(Result.DONE);
            } else {
                closeLoops = 0;
            }
        } else if (dist <= ON_POINT_IN
                || ((dist <= PASS_POINT_IN || (past && dist <= PASSED_BY_IN)) && nextLineIsClear(p))) {
            // A point on the way: on to the next line at once.
            next++;
            startLine();
            return update();
        }

        // Not last and not yet left: the next line is not clear from here, so it drives to the point itself.
        double tol = last ? toleranceIn : ON_POINT_IN;
        double speed;
        if (dist <= tol) speed = 0;
        else if (dist < AutoDrive.DRIVE_TO_NEAR_IN) {
            speed = Math.max(Math.min(1, AutoDrive.DRIVE_TO_MIN_POWER / limit), Math.min(AutoDrive.DRIVE_TO_KP * dist, maxSpeed));
        } else speed = MotionMath.taperSpeed(dist / lineStartDist, cfg.moveMinPower, maxSpeed, cfg.taperFraction(lineStartDist));

        // The field error seen from the robot: heading h (CCW from "away") faces (-sin h, cos h).
        double h = Math.toRadians(p.headingDeg);
        double forwardErr = -dx * Math.sin(h) + dy * Math.cos(h);
        double rightErr = dx * Math.cos(h) + dy * Math.sin(h);
        double forward = dist > 1e-6 ? speed * forwardErr / dist : 0;
        double right = dist > 1e-6 ? speed * rightErr / dist : 0;
        // PID output is counterclockwise; the drive's turn is clockwise.
        double turn = -headingPid.calculate(headingDeg, p.headingDeg);
        double minTurn = Math.min(1, (cfg.turnMinPower > 0 ? cfg.turnMinPower : AutoDrive.DRIVE_TO_MIN_TURN) / limit);
        if (Math.abs(headingErr) > headingTol && Math.abs(turn) < minTurn) turn = -Math.signum(headingErr) * minTurn;

        boolean near = dist <= AutoDrive.DRIVE_TO_NEAR_IN;
        if (near != wasNear) {
            stall.reset();
            nearStall.reset();
            wasNear = near;
        }
        boolean stuck = near ? dist > tol && nearStall.update(dist, stallClock) : stall.update(dist, stallClock);
        if (cfg.stallExitEnabled && stuck) return finish(Result.STALLED);
        return new double[] {forward, right, turn};
    }

    private boolean nextLineIsClear(FieldPose p) {
        if (lineCheck == null) return true;
        double[] after = points.get(next + 1);
        return lineCheck.isClear(p.x, p.y, after[0], after[1]);
    }

    // A new line of the route: fresh time limit and stuck checks.
    private void startLine() {
        stall = new StallDetector(cfg.stallProgressIn, cfg.stallTimeSec);
        // In the last inches it moves slowly on purpose: "stuck" there means no progress for 2 s.
        nearStall = new StallDetector(0.1, 2.0);
        wasNear = false;
        lineStartDist = -1;
        lineStartSec = seconds();
        closeLoops = 0;
    }

    private double[] finish(Result how) {
        running = false;
        result = how;
        return new double[3];
    }

    private double seconds() {
        return clockNanos.getAsLong() / 1e9;
    }
}
