package com.crossbotics.ftchelper.odometry;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import java.util.function.DoubleSupplier;

/**
 * Odometry from dead wheels: unpowered omni wheels ("pods") on encoder ports. Three wheels: two that
 * measure forward (left and right) and one that measures sideways. Two wheels: one forward, one
 * sideways, and the heading from the IMU. The library works out the pose from the wheels' changes
 * every loop.
 *
 * <p>The pods plug into a motor port's encoder (the motor on that port can still be used); the
 * library only reads the count.
 *
 * Docs: docs/modules/odometry.md
 */
public final class DeadWheelOdometry extends PoseTracker {
    private final DcMotorEx left, right, perp;
    private final DoubleSupplier imuHeadingDeg;   // two wheels: the heading source; null for three
    private final double ticksPerIn;
    private final double trackWidthIn;   // three wheels: distance between the left and right pods
    private final double parallelLeftIn; // two wheels: the forward pod's distance LEFT of the centre
    private final double perpForwardIn;  // the sideways pod's distance FORWARD of the centre
    private final int dirL, dirR, dirP;
    private int lastL, lastR, lastP;
    private double lastImu;
    private boolean started = false;
    // The pose in the pods' frame (X forward, Y left, heading CCW degrees).
    private double x, y, h;

    /** Three wheels. Distances in inches; {@code ticksPerIn} = encoder counts per inch of the pod wheel. */
    public static DeadWheelOdometry threeWheels(String name, DcMotorEx left, DcMotorEx right, DcMotorEx perp,
                                                double ticksPerIn, double trackWidthIn, double perpForwardIn,
                                                boolean leftReversed, boolean rightReversed, boolean perpReversed) {
        return new DeadWheelOdometry(name, left, right, perp, null, ticksPerIn, trackWidthIn, 0, perpForwardIn,
                leftReversed, rightReversed, perpReversed);
    }

    /** Two wheels and the IMU. {@code parallelLeftIn}: the forward pod's distance left of the centre. */
    public static DeadWheelOdometry twoWheels(String name, DcMotorEx parallel, DcMotorEx perp, DoubleSupplier imuHeadingDeg,
                                              double ticksPerIn, double parallelLeftIn, double perpForwardIn,
                                              boolean parallelReversed, boolean perpReversed) {
        return new DeadWheelOdometry(name, parallel, null, perp, imuHeadingDeg, ticksPerIn, 0, parallelLeftIn, perpForwardIn,
                parallelReversed, false, perpReversed);
    }

    private DeadWheelOdometry(String name, DcMotorEx left, DcMotorEx right, DcMotorEx perp, DoubleSupplier imu,
                              double ticksPerIn, double trackWidthIn, double parallelLeftIn, double perpForwardIn,
                              boolean lRev, boolean rRev, boolean pRev) {
        super(name);
        if (!(ticksPerIn > 0)) throw new IllegalArgumentException(name + ": ticksPerIn must be more than 0");
        if (right != null && !(trackWidthIn > 0)) throw new IllegalArgumentException(name + ": trackWidthIn must be more than 0");
        this.left = left;
        this.right = right;
        this.perp = perp;
        this.imuHeadingDeg = imu;
        this.ticksPerIn = ticksPerIn;
        this.trackWidthIn = trackWidthIn;
        this.parallelLeftIn = parallelLeftIn;
        this.perpForwardIn = perpForwardIn;
        dirL = lRev ? -1 : 1;
        dirR = rRev ? -1 : 1;
        dirP = pRev ? -1 : 1;
    }

    @Override
    protected double[] read() {
        int l = dirL * left.getCurrentPosition();
        int r = right == null ? 0 : dirR * right.getCurrentPosition();
        int p = dirP * perp.getCurrentPosition();
        double imu = imuHeadingDeg == null ? 0 : imuHeadingDeg.getAsDouble();
        if (!started) {
            started = true;
            lastL = l;
            lastR = r;
            lastP = p;
            lastImu = imu;
            return new double[]{x, y, h};
        }
        double dL = (l - lastL) / ticksPerIn, dR = (r - lastR) / ticksPerIn, dP = (p - lastP) / ticksPerIn;
        lastL = l;
        lastR = r;
        lastP = p;
        double dThetaRad, forward;
        if (right != null) {
            // Turning left (CCW) moves the right pod forward and the left pod back.
            dThetaRad = (dR - dL) / trackWidthIn;
            forward = (dL + dR) / 2;
        } else {
            dThetaRad = Math.toRadians(com.crossbotics.ftchelper.util.Angles.shortestDiff(imu, lastImu));
            // A pod left of the centre moves back by (its distance x turn) when the robot turns left.
            forward = dL + parallelLeftIn * dThetaRad;
        }
        lastImu = imu;
        // A pod forward of the centre moves left by (its distance x turn) when the robot turns left.
        double sideways = dP - perpForwardIn * dThetaRad;
        // Turn the robot's move into the field frame, using the heading halfway through the turn.
        double mid = Math.toRadians(h) + dThetaRad / 2;
        x += forward * Math.cos(mid) - sideways * Math.sin(mid);
        y += forward * Math.sin(mid) + sideways * Math.cos(mid);
        h += Math.toDegrees(dThetaRad);
        return new double[]{x, y, h};
    }

    @Override
    protected void write(double xIn, double yIn, double headingDeg) {
        x = xIn;
        y = yIn;
        h = headingDeg;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public String status() {
        return "ready";
    }

    @Override
    public String kind() {
        return right != null ? "three dead wheels" : "two dead wheels + IMU";
    }
}
