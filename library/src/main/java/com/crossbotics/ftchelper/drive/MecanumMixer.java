package com.crossbotics.ftchelper.drive;

/**
 * The math that turns "forward, right, turn" into four mecanum wheel powers.
 * No hardware here, so it is easy to test. {@link MecanumDrive} uses it.
 *
 * <p>Signs used everywhere in this library's drive code:
 * <ul>
 *   <li>forward: positive = robot moves forward</li>
 *   <li>strafeRight: positive = robot moves right</li>
 *   <li>turnClockwise: positive = robot turns right (clockwise, seen from above)</li>
 *   <li>IMU heading: positive = robot turned LEFT (counterclockwise). This is the SDK's rule.</li>
 * </ul>
 * Why the two turning signs differ: docs/conventions.md.
 *
 * <p>How mecanum wheels move: each wheel has small rollers set at an angle, so a spinning wheel
 * pushes the robot forward AND a bit sideways. Front-left and back-right push forward-and-right;
 * front-right and back-left push forward-and-left. Spinning the wheels in different combinations
 * adds those pushes up to forward, sideways or turning. Tutorial 04 shows it with pictures.
 *
 * Docs: docs/modules/drive.md
 */
public final class MecanumMixer {
    // Private constructor: nobody makes a MecanumMixer object. You call the "static" methods on
    // the class itself, like MecanumMixer.mix(...).
    private MecanumMixer() { }

    /** Index of each wheel in the arrays this class returns. */
    public static final int FRONT_LEFT = 0, FRONT_RIGHT = 1, BACK_LEFT = 2, BACK_RIGHT = 3;

    /**
     * Wheel powers for a robot-relative move.
     *
     * <p>If any wheel would get more than {@code limit}, all four are scaled down together,
     * so the robot still moves in the direction you asked.
     *
     * @param forward       -1 to 1
     * @param strafeRight   -1 to 1
     * @param turnClockwise -1 to 1
     * @param limit         the largest power any wheel may get, 0 to 1
     * @return powers in the order front-left, front-right, back-left, back-right.
     *         All zero if any input is NaN or infinite (a safe stop).
     */
    public static double[] mix(double forward, double strafeRight, double turnClockwise, double limit) {
        // A broken value (NaN = "not a number", or infinity) must never reach a motor: stop instead.
        if (!Double.isFinite(forward) || !Double.isFinite(strafeRight)
                || !Double.isFinite(turnClockwise) || !Double.isFinite(limit)) {
            return new double[4];
        }
        // Each wheel adds up its share of the three moves:
        // - forward: every wheel turns forward (+ on all four).
        // - strafe right: front-left and back-right forward, front-right and back-left backward.
        //   Their angled rollers then push sideways together while the forward pushes cancel out.
        // - turn clockwise: the left wheels forward, the right wheels backward (like a tank turning).
        double[] powers = {
                forward + strafeRight + turnClockwise,
                forward - strafeRight - turnClockwise,
                forward - strafeRight + turnClockwise,
                forward + strafeRight - turnClockwise
        };
        // The sums can reach 3.0, but a motor only takes -1 to 1. Find the biggest one (at least 1)...
        double largest = 1.0;
        for (double power : powers) largest = Math.max(largest, Math.abs(power));
        // ...and divide ALL four by it (and multiply by the limit). Shrinking every wheel by the same
        // amount keeps their ratios, so the robot still goes the way you asked, only slower.
        // Cutting only the too-big wheels would bend the path.
        double scale = Math.max(0, Math.min(1, limit)) / largest;
        for (int i = 0; i < powers.length; i++) powers[i] *= scale;
        return powers;
    }

    /**
     * Turns a field-relative command into a robot-relative one ("field-centric" driving).
     *
     * <p>With field-centric driving, pushing the stick away from you always moves the robot
     * away from you, whichever way the robot faces.
     *
     * <p>Example: the robot has turned 90 degrees left (heading 90) and you push the stick away from
     * you. Away from you is now on the robot's right side, so this returns "strafe right".
     *
     * @param fieldForward    -1 to 1, away from the driver (along the field direction where heading = 0)
     * @param fieldRight      -1 to 1, to the driver's right
     * @param headingCcwDeg   the robot's IMU heading, degrees, counterclockwise positive
     * @return {forward, strafeRight} for the robot
     */
    public static double[] fieldToRobot(double fieldForward, double fieldRight, double headingCcwDeg) {
        // Math's sine and cosine work in radians, not degrees (180 degrees = pi radians).
        double h = Math.toRadians(headingCcwDeg);
        double cos = Math.cos(h), sin = Math.sin(h);
        // Rotate the field vector by -heading. Written with x = forward and y = LEFT, then y flipped.
        // (Rotating "backwards" by the robot's turn undoes the turn, giving the direction as the
        // robot sees it. The standard rotation formula expects left-positive, hence the flips.)
        double xLeftFrame = fieldForward * cos - fieldRight * sin;
        double yLeftFrame = -fieldForward * sin - fieldRight * cos;
        return new double[]{xLeftFrame, -yLeftFrame};
    }

    /**
     * The robot-relative direction of a field direction. Used by autonomous moves.
     *
     * @param fieldClockwiseDeg direction to travel on the field, degrees clockwise from field-forward
     *                          (0 = forward, 90 = right, 180 = back, -90 = left)
     * @param headingCcwDeg     the robot's IMU heading, degrees, counterclockwise positive
     * @return {forward, strafeRight}, a unit vector
     */
    public static double[] fieldDirection(double fieldClockwiseDeg, double headingCcwDeg) {
        // The robot turned LEFT by the heading, so a field direction looks that much further to the
        // RIGHT (clockwise) from the robot. Adding the two gives the clockwise angle from the robot's front.
        double radians = Math.toRadians(fieldClockwiseDeg + headingCcwDeg);
        // Cosine = the forward part, sine = the right part (each between -1 and 1).
        return new double[]{Math.cos(radians), Math.sin(radians)};
    }
}
