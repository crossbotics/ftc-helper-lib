package com.crossbotics.ftchelper.odometry;

import com.qualcomm.hardware.sparkfun.SparkFunOTOS;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

/**
 * Odometry from a SparkFun OTOS (Optical Tracking Odometry Sensor): a camera-like sensor under the robot
 * that watches the floor move, with its own IMU. No wheels. It must sit 10 to 12 mm above the tiles.
 *
 * <p>Settings: where it sits on the robot (inches right... see {@link #OtosOdometry}), and two scale
 * numbers the Odometry Tuner measures: linear (distance) and angular (turning).
 *
 * Docs: docs/modules/odometry.md
 */
public final class OtosOdometry extends PoseTracker {
    private final SparkFunOTOS otos;
    private boolean connected;

    /**
     * Sets the OTOS up, calibrates its IMU and zeroes it (the robot must be still; INIT is).
     *
     * @param name          the configuration name
     * @param otos          the device ({@code hardwareMap.get(SparkFunOTOS.class, name)})
     * @param forwardIn     how far FORWARD of the robot's centre it sits (back = negative)
     * @param leftIn        how far LEFT of the centre it sits (right = negative)
     * @param headingDeg    which way its arrow points, counterclockwise from the robot's front
     * @param linearScalar  distance scale (1.0 until the tuner measures it; 0.872 to 1.127)
     * @param angularScalar turn scale (1.0 until the tuner measures it; 0.872 to 1.127)
     */
    public OtosOdometry(String name, SparkFunOTOS otos, double forwardIn, double leftIn, double headingDeg,
                        double linearScalar, double angularScalar) {
        super(name);
        this.otos = otos;
        connected = otos.isConnected();
        otos.setLinearUnit(DistanceUnit.INCH);
        otos.setAngularUnit(AngleUnit.DEGREES);
        otos.setOffset(new SparkFunOTOS.Pose2D(forwardIn, leftIn, headingDeg));
        otos.setLinearScalar(linearScalar);
        otos.setAngularScalar(angularScalar);
        otos.calibrateImu();
        otos.resetTracking();
    }

    @Override
    protected double[] read() {
        SparkFunOTOS.Pose2D p = otos.getPosition();
        if (p == null) return null;
        SparkFunOTOS.Pose2D v = otos.getVelocity();
        if (v == null) return new double[]{p.x, p.y, p.h};
        return new double[]{p.x, p.y, p.h, v.x, v.y, v.h};
    }

    @Override
    protected void write(double xIn, double yIn, double headingDeg) {
        otos.setPosition(new SparkFunOTOS.Pose2D(xIn, yIn, headingDeg));
    }

    @Override
    public boolean isReady() {
        return connected;
    }

    @Override
    public String status() {
        return connected ? "ready" : "not answering: check the I2C cable and the configuration";
    }

    @Override
    public String kind() {
        return "OTOS";
    }

    /** The device, for anything this class does not cover. */
    public SparkFunOTOS device() {
        return otos;
    }
}
