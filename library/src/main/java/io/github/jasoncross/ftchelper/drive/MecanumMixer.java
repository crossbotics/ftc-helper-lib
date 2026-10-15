package io.github.jasoncross.ftchelper.drive;

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
 *
 * Docs: docs/modules/drive.md
 */
public final class MecanumMixer {
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
        if (!Double.isFinite(forward) || !Double.isFinite(strafeRight)
                || !Double.isFinite(turnClockwise) || !Double.isFinite(limit)) {
            return new double[4];
        }
        double[] powers = {
                forward + strafeRight + turnClockwise,
                forward - strafeRight - turnClockwise,
                forward - strafeRight + turnClockwise,
                forward + strafeRight - turnClockwise
        };
        double largest = 1.0;
        for (double power : powers) largest = Math.max(largest, Math.abs(power));
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
     * @param fieldForward    -1 to 1, away from the driver (along the field direction where heading = 0)
     * @param fieldRight      -1 to 1, to the driver's right
     * @param headingCcwDeg   the robot's IMU heading, degrees, counterclockwise positive
     * @return {forward, strafeRight} for the robot
     */
    public static double[] fieldToRobot(double fieldForward, double fieldRight, double headingCcwDeg) {
        double h = Math.toRadians(headingCcwDeg);
        double cos = Math.cos(h), sin = Math.sin(h);
        // Rotate the field vector by -heading. Written with x = forward and y = LEFT, then y flipped.
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
        double radians = Math.toRadians(fieldClockwiseDeg + headingCcwDeg);
        return new double[]{Math.cos(radians), Math.sin(radians)};
    }
}
