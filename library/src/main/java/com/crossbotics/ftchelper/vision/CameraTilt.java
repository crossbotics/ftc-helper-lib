package com.crossbotics.ftchelper.vision;

/**
 * Turns what a TILTED camera measures into what a LEVEL one would: the bearing along the floor and the
 * distance along the floor.
 *
 * <p>Why: a camera reports a target's direction in its OWN view. When the camera is tilted up (to see a
 * high goal), "to the side" in the picture is not "to the side" on the floor: a goal 27 degrees to the left
 * reads only about 21 degrees with a camera tilted 35 degrees up, and its distance is the slanted line from
 * the camera up to the goal. The aim and the shot tables want the floor's numbers.
 *
 * <p>How: the camera's numbers (right, forward along the lens, up in the picture) are turned down by the
 * tilt, so "forward" lies flat on the floor again. Then bearing = the angle sideways, distance = the
 * length sideways and forward (the height left out). With a tilt of 0 nothing changes.
 *
 * <p>The tilt: {@code pitchDeg} in the camera's Tunables section (degrees, + = tilted UP, as in the
 * simulator's robot file). Measure it on the robot: docs/modules/robot.md#camera.
 *
 * <pre>
 * double b = CameraTilt.bearingDeg(right, forward, up, 35);   // + = LEFT, along the floor
 * </pre>
 */
public final class CameraTilt {
    private CameraTilt() { }

    /**
     * The target's direction along the floor.
     *
     * @param right    the target's place to the right of the lens, in the camera's own view (any length unit)
     * @param forward  the target's place along the lens's line of sight (same unit)
     * @param up       the target's place up in the picture, square to the line of sight (same unit)
     * @param pitchDeg how far the camera is tilted up, degrees (0 = level; + = up, - = down)
     * @return degrees, + = to the LEFT (the library's sign, docs/conventions.md); NaN if a number is NaN
     */
    public static double bearingDeg(double right, double forward, double up, double pitchDeg) {
        return Math.toDegrees(Math.atan2(-right, flatForward(forward, up, pitchDeg)));
    }

    /**
     * The target's distance from the camera along the floor (the height difference left out).
     *
     * @param right    as in {@link #bearingDeg}
     * @param forward  as in {@link #bearingDeg}
     * @param up       as in {@link #bearingDeg}
     * @param pitchDeg as in {@link #bearingDeg}
     * @return in the same length unit as the inputs; NaN if a number is NaN
     */
    public static double floorDistance(double right, double forward, double up, double pitchDeg) {
        return Math.hypot(right, flatForward(forward, up, pitchDeg));
    }

    // "Forward" after turning the camera's view down by its tilt: level with the floor.
    private static double flatForward(double forward, double up, double pitchDeg) {
        double p = Math.toRadians(pitchDeg);
        return forward * Math.cos(p) - up * Math.sin(p);
    }
}
