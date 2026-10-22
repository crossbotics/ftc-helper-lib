package com.crossbotics.ftchelper.vision;

import java.util.Locale;

/**
 * The math for shooting while the robot drives and turns. Two fixes to where the goal is, both worked
 * out from how the robot moves:
 *
 * <ol>
 *   <li><b>The picture is old.</b> A camera's picture is a few tens of milliseconds old by the time the
 *       code reads it. Since then the robot turned and moved, so the goal is no longer where the
 *       picture shows it. {@link #solve} turns the goal back by the robot's turn and moves it by the
 *       robot's motion over that time: the goal NOW.</li>
 *   <li><b>The piece keeps the shooter's speed.</b> A piece shot from a robot driving sideways at
 *       30 in/s also flies sideways at 30 in/s. If it is in the air for 0.8 s, it lands 24 in to the
 *       side. So aim at a point that far the OTHER way: the "virtual goal" = the goal minus the
 *       shooter's speed x the flight time. The launcher's speed comes from the virtual goal's distance
 *       too (the piece flies that far relative to the robot).</li>
 * </ol>
 *
 * <p><b>Where the shooter is.</b> The shooter need not be at the robot's centre: {@link #shooterAt}
 * says where it is (a turret's pivot, or a fixed launcher's exit). That changes three things: the
 * distance is from the shooter, a turning robot swings an off-centre shooter sideways (more speed for
 * the piece), and a fixed shooter to one side of the centre must turn the whole robot a little MORE
 * than "face the goal" so that its own line passes through the goal. {@link #onTurret()} says a turret
 * aims the shot (the answer is a turret angle); without it the whole robot turns (the answer is how far
 * to turn the robot).
 *
 * <p>One frame for everything, the robot's: x forward, y to the LEFT, from the robot's centre, angles
 * counterclockwise + (positive = to the left), the same as a {@link TargetObservation}. The five-files
 * robot does all this for you (docs/modules/robot.md, section "Aim", {@code Tunables.Aim.shootOnMove});
 * this class is the plain math, for your own code and tests.
 *
 * <pre>{@code
 * MovingShot shot = new MovingShot().shooterAt(-4, 0).onTurret().flightSec(0.7, 0).maxLeadIn(24);
 * // The goal 60 in ahead of the robot's centre; the robot strafes RIGHT at 30 in/s (left speed -30),
 * // no turn; the picture is 40 ms old.
 * MovingShot.Result r = shot.solve(60, 0, 0, -30, 0, 0.040);
 * // r.aimDeg: the turret angle (left of straight ahead: it leads); r.distanceIn: for the launcher's speed
 * }</pre>
 *
 * <p>What it leaves out (small for an FTC robot): the turret's own turn during the picture's age, air
 * drag on the piece, and a change of the robot's speed during the flight.
 *
 * Docs: docs/modules/vision.md (section "MovingShot"), docs/tutorials/start/09-shooter-and-camera.md
 */
public final class MovingShot {
    // Where the shooter is from the robot's centre (inches, forward and left), and whether a turret aims.
    private double shooterForward = 0, shooterLeft = 0;
    private boolean turret = false;
    private double flightSec = 0, flightSecPerIn = 0, maxLeadIn = 24;

    /**
     * Where the shooter is from the robot's centre: a turret's pivot, or where a fixed launcher's
     * piece leaves. Inches. Default 0, 0 (the centre).
     *
     * @param forwardIn inches in front of the centre (negative = behind)
     * @param leftIn    inches left of the centre (negative = right)
     */
    public MovingShot shooterAt(double forwardIn, double leftIn) {
        shooterForward = forwardIn;
        shooterLeft = leftIn;
        return this;
    }

    /** A turret aims the shot: {@link Result#aimDeg} is the turret angle. Without it the whole robot turns. */
    public MovingShot onTurret() {
        turret = true;
        return this;
    }

    /**
     * How long a piece is in the air: {@code sec + perIn x distance}, seconds. Measure it with a
     * slow-motion video (frames from leaving the shooter to reaching the goal, divided by the frame rate).
     *
     * @param sec   the flight time, seconds (0 and perIn 0 = no lead: only the picture's age)
     * @param perIn extra seconds per inch of distance (0 = the same time at every distance)
     */
    public MovingShot flightSec(double sec, double perIn) {
        flightSec = sec;
        flightSecPerIn = perIn;
        return this;
    }

    /** The most the aim point may move for the lead, inches (a safety cap). 0 or less = no cap. Default 24. */
    public MovingShot maxLeadIn(double in) {
        maxLeadIn = in;
        return this;
    }

    /** What {@link #solve} gives: where to aim, how far to shoot, and how big the lead is. */
    public static final class Result {
        /**
         * Where to aim, degrees (counterclockwise + = left): with {@link #onTurret()} the turret angle
         * from the robot's front; otherwise how far to turn the whole robot (0 = it faces right).
         */
        public final double aimDeg;
        /** Inches from the shooter to the virtual goal: the distance for the launcher's speed. */
        public final double distanceIn;
        /** How many degrees the robot's motion moved the aim (the aim minus the aim of a still robot). */
        public final double leadDeg;
        /** How far the aim point moved because the piece keeps the shooter's speed (speed x flight time), inches. */
        public final double leadIn;
        /** The flight time used, seconds. */
        public final double flightSec;

        Result(double aimDeg, double distanceIn, double leadDeg, double leadIn, double flightSec) {
            this.aimDeg = aimDeg;
            this.distanceIn = distanceIn;
            this.leadDeg = leadDeg;
            this.leadIn = leadIn;
            this.flightSec = flightSec;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "aim %.1f deg, %.1f in (lead %.1f deg, %.1f in, flight %.2f s)",
                    aimDeg, distanceIn, leadDeg, leadIn, flightSec);
        }
    }

    /**
     * Brings a goal seen in an old picture forward to now, leads it for the flight time, and says
     * where to aim the shooter.
     *
     * @param goalForwardIn   the goal in front of the robot's centre when the picture was taken, inches
     * @param goalLeftIn      the goal left of the robot's centre then, inches (see {@link #seenFrom})
     * @param forwardInPerSec the robot's speed forward, inches per second (see {@link #toRobotFrame})
     * @param leftInPerSec    the robot's speed to the left, inches per second
     * @param turnDegPerSec   how fast the robot turns, degrees per second, counterclockwise +
     * @param latencySec      how old the picture is, seconds (0 for a goal worked out from the odometry now)
     * @return where to aim and how far to shoot, now
     */
    public Result solve(double goalForwardIn, double goalLeftIn, double forwardInPerSec, double leftInPerSec,
                        double turnDegPerSec, double latencySec) {
        Result still = aim(goalForwardIn, goalLeftIn, 0, 0, 0, 0);
        Result moving = aim(goalForwardIn, goalLeftIn, forwardInPerSec, leftInPerSec, turnDegPerSec, latencySec);
        double lead = wrap(moving.aimDeg - still.aimDeg);
        // Keep the answer near the still aim (no jump from +179 to -181).
        return new Result(still.aimDeg + lead, moving.distanceIn, lead, moving.leadIn, moving.flightSec);
    }

    // The aim for one set of speeds (all 0: a still robot).
    private Result aim(double gx0, double gy0, double vf, double vl, double turnDegPerSec, double latencySec) {
        double w = Math.toRadians(turnDegPerSec);
        // 1. The picture's age: the robot turned w x latency since. A goal that stays put looks turned
        //    back by that much (turn left, and the goal moves right), and moved back by the robot's move.
        double a = -w * latencySec;
        double gx = gx0 * Math.cos(a) - gy0 * Math.sin(a) - vf * latencySec;
        double gy = gx0 * Math.sin(a) + gy0 * Math.cos(a) - vl * latencySec;
        // 2. The shooter's speed: the robot's, plus the swing of an off-centre shooter while turning
        //    (a point at (x, y) on a robot turning at w moves at (-w y, w x)).
        double sf = vf - w * shooterLeft, sl = vl + w * shooterForward;
        double speed = Math.hypot(sf, sl);
        // 3. The virtual goal = the goal - the shooter's speed x the flight time. The flight time may
        //    grow with the distance, and the distance is the virtual goal's: work it out a few times.
        double qx = gx, qy = gy, t = 0, leadIn = 0, dist = distanceFrom(gx, gy);
        for (int i = 0; i < 5; i++) {
            t = Math.max(0, flightSec + flightSecPerIn * dist);
            leadIn = speed * t;
            double scale = maxLeadIn > 0 && leadIn > maxLeadIn ? maxLeadIn / leadIn : 1;
            leadIn *= scale;
            qx = gx - sf * t * scale;
            qy = gy - sl * t * scale;
            dist = distanceFrom(qx, qy);
        }
        double aimDeg;
        if (turret) {
            // A turret at the shooter's place points straight at the virtual goal.
            aimDeg = Math.toDegrees(Math.atan2(qy - shooterLeft, qx - shooterForward));
        } else {
            // A fixed shooter shoots along a line parallel to the robot's front, shooterLeft to the side.
            // Turn the robot until that line passes through the virtual goal: the goal's direction,
            // minus asin(side / distance) (more for a shooter far to the side or a goal close by).
            double r = Math.hypot(qx, qy);
            double side = r < 1e-9 ? 0 : Math.max(-1, Math.min(1, shooterLeft / r));
            aimDeg = Math.toDegrees(Math.atan2(qy, qx) - Math.asin(side));
        }
        return new Result(aimDeg, dist, 0, leadIn, t);
    }

    // From the shooter to a goal at (x, y) from the centre, inches: a turret's straight line; a fixed
    // shooter's distance along its own line once the robot has turned to it.
    private double distanceFrom(double x, double y) {
        if (turret) return Math.hypot(x - shooterForward, y - shooterLeft);
        double along = Math.sqrt(Math.max(0, x * x + y * y - shooterLeft * shooterLeft));
        return along - shooterForward;
    }

    /**
     * Where a goal is from the robot's centre, from what a camera sees: {forward, left} inches.
     *
     * @param bearingDeg  the camera's bearing to it, degrees, counterclockwise + (left)
     * @param distanceIn  the camera's distance to it, inches
     * @param camForwardIn where the camera is: inches in front of the centre
     * @param camLeftIn   inches left of the centre
     * @param camYawDeg   which way the camera faces from the robot's front (a turret's angle for a camera on it)
     */
    public static double[] seenFrom(double bearingDeg, double distanceIn, double camForwardIn, double camLeftIn,
                                    double camYawDeg) {
        double a = Math.toRadians(bearingDeg + camYawDeg);
        return new double[]{camForwardIn + distanceIn * Math.cos(a), camLeftIn + distanceIn * Math.sin(a)};
    }

    /**
     * The robot's speed over the field turned into the robot's own frame: {forward, left}, inches per
     * second. The field frame is the library's (x right, y away from your drivers' wall).
     *
     * @param fieldVx    speed toward +x (right), inches per second
     * @param fieldVy    speed toward +y (away), inches per second
     * @param headingDeg the robot's heading, degrees, counterclockwise +, 0 = away from your wall
     * @return {forward, left}
     */
    public static double[] toRobotFrame(double fieldVx, double fieldVy, double headingDeg) {
        double h = Math.toRadians(headingDeg);
        // Forward at heading h points to (-sin h, cos h) on the field; left points to (-cos h, -sin h).
        double forward = -fieldVx * Math.sin(h) + fieldVy * Math.cos(h);
        double left = -fieldVx * Math.cos(h) - fieldVy * Math.sin(h);
        return new double[]{forward, left};
    }

    // -180 to 180.
    private static double wrap(double deg) {
        double d = deg % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }
}
