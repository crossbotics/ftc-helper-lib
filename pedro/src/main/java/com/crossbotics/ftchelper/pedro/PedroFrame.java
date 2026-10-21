package com.crossbotics.ftchelper.pedro;

import com.crossbotics.ftchelper.util.FieldPose;
import com.pedropathing.math.Pose;

/**
 * Turns poses between our frame and Pedro's.
 *
 * <p>Ours: x right and y away from the drivers (inches), heading in degrees, 0 = facing away,
 * counterclockwise +. Pedro: the same x and y, heading in radians, 0 = facing +x (right). So only the
 * heading changes: Pedro's heading = ours + 90 degrees.
 *
 * <p>Mirrored (RED with mirroring on): x becomes -x and the heading becomes -heading, in our frame.
 */
public final class PedroFrame {
    private PedroFrame() { }

    /** Our pose (inches, degrees) to Pedro's. */
    public static Pose toPedro(double x, double y, double headingDeg) {
        return new Pose(x, y, Math.toRadians(headingDeg + 90));
    }

    /** Our pose to Pedro's. */
    public static Pose toPedro(FieldPose p) {
        return toPedro(p.x, p.y, p.headingDeg);
    }

    /** Our heading (degrees) to Pedro's (radians). */
    public static double headingToPedro(double headingDeg) {
        return Math.toRadians(headingDeg + 90);
    }

    /** Pedro's pose to ours. */
    public static FieldPose fromPedro(Pose p) {
        return new FieldPose(p.x(), p.y(), wrap(Math.toDegrees(p.heading()) - 90));
    }

    // -180 .. 180
    static double wrap(double deg) {
        double d = deg % 360;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return d;
    }
}
