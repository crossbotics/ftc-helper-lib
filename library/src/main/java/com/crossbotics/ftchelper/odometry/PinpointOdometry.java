package com.crossbotics.ftchelper.odometry;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

/**
 * Odometry from a goBILDA Pinpoint: a small computer on an I2C port that reads two odometry pods and its
 * own IMU and works out the pose itself, about 1500 times a second. The library only reads the answer
 * once per loop.
 *
 * <pre>{@code
 * // In Robot.java (the robot layer does this for you):
 * odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));   // 4-bar pods unless .podsSwingarm()
 * }</pre>
 *
 * <p>The offsets (from the SDK): x = how far to the LEFT of the robot's centre the forward-measuring
 * (X) pod is, y = how far FORWARD of the centre the sideways-measuring (Y) pod is, in mm. The
 * Odometry Tuner measures them.
 *
 * Docs: docs/modules/odometry.md
 */
public final class PinpointOdometry extends PoseTracker {
    private final GoBildaPinpointDriver pinpoint;

    /**
     * Sets the Pinpoint up and zeroes it (the robot must be still for about a quarter second; INIT is).
     *
     * @param name       the configuration name
     * @param pinpoint   the device ({@code hardwareMap.get(GoBildaPinpointDriver.class, name)})
     * @param xOffsetMm  the X pod's distance LEFT of the centre (right = negative)
     * @param yOffsetMm  the Y pod's distance FORWARD of the centre (back = negative)
     * @param pods       goBILDA 4-bar or swingarm pods, or null to use {@code ticksPerMm}
     * @param ticksPerMm encoder counts per mm, for other pods (ignored when {@code pods} is given)
     * @param xReversed  the X pod counts down when the robot drives forward
     * @param yReversed  the Y pod counts down when the robot moves left
     */
    public PinpointOdometry(String name, GoBildaPinpointDriver pinpoint, double xOffsetMm, double yOffsetMm,
                            GoBildaPinpointDriver.GoBildaOdometryPods pods, double ticksPerMm,
                            boolean xReversed, boolean yReversed) {
        super(name);
        this.pinpoint = pinpoint;
        pinpoint.setOffsets(xOffsetMm, yOffsetMm, DistanceUnit.MM);
        if (pods != null) pinpoint.setEncoderResolution(pods);
        else pinpoint.setEncoderResolution(ticksPerMm, DistanceUnit.MM);
        pinpoint.setEncoderDirections(
                xReversed ? GoBildaPinpointDriver.EncoderDirection.REVERSED : GoBildaPinpointDriver.EncoderDirection.FORWARD,
                yReversed ? GoBildaPinpointDriver.EncoderDirection.REVERSED : GoBildaPinpointDriver.EncoderDirection.FORWARD);
        pinpoint.resetPosAndIMU();
    }

    @Override
    protected double[] read() {
        pinpoint.update();
        return new double[]{
                pinpoint.getPosX(DistanceUnit.INCH), pinpoint.getPosY(DistanceUnit.INCH),
                pinpoint.getHeading(AngleUnit.DEGREES),
                pinpoint.getVelX(DistanceUnit.INCH), pinpoint.getVelY(DistanceUnit.INCH),
                pinpoint.getHeadingVelocity(UnnormalizedAngleUnit.DEGREES)};
    }

    @Override
    protected void write(double xIn, double yIn, double headingDeg) {
        pinpoint.setPosition(new Pose2D(DistanceUnit.INCH, xIn, yIn, AngleUnit.DEGREES, headingDeg));
    }

    @Override
    public boolean isReady() {
        return pinpoint.getDeviceStatus() == GoBildaPinpointDriver.DeviceStatus.READY;
    }

    @Override
    public String status() {
        GoBildaPinpointDriver.DeviceStatus s = pinpoint.getDeviceStatus();
        if (s == null) return "no answer";
        switch (s) {
            case READY: return "ready";
            case CALIBRATING: return "calibrating (keep the robot still)";
            case NOT_READY: return "starting up";
            case FAULT_X_POD_NOT_DETECTED: return "X pod not found: check its cable";
            case FAULT_Y_POD_NOT_DETECTED: return "Y pod not found: check its cable";
            case FAULT_NO_PODS_DETECTED: return "no pods found: check both cables";
            case FAULT_IMU_RUNAWAY: return "IMU fault: power the robot off and on";
            case FAULT_BAD_READ: return "bad reading (I2C cable?)";
            default: return s.name();
        }
    }

    @Override
    public String kind() {
        return "Pinpoint";
    }

    /** The device, for anything this class does not cover. */
    public GoBildaPinpointDriver device() {
        return pinpoint;
    }
}
