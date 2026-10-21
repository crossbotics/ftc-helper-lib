package com.crossbotics.ftchelper.odometry;

import com.qualcomm.hardware.sparkfun.SparkFunOTOS;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

/**
 * Odometry from a SparkFun OTOS (Optical Tracking Odometry Sensor): a camera-like sensor under the robot
 * that watches the floor move, with its own IMU. No wheels. It must sit 10 to 12 mm above the tiles.
 *
 * <p>Settings: where it sits on the robot (see {@link #OtosOdometry}), and two scale numbers the
 * Odometry Tuner measures: linear (distance) and angular (turning).
 *
 * <p>The OTOS's own axes (the SDK's sample): x = to the robot's RIGHT, y = FORWARD, heading
 * counterclockwise +. This class turns them into the library's frame.
 *
 * Docs: docs/modules/odometry.md
 */
public final class OtosOdometry extends PoseTracker {
    private final SparkFunOTOS otos;
    private boolean connected;
    // When the connection was last checked (it is checked again once a second, not every loop).
    private long checkedAt;

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
        checkedAt = System.nanoTime();
        otos.setLinearUnit(DistanceUnit.INCH);
        otos.setAngularUnit(AngleUnit.DEGREES);
        // The OTOS's frame: x right, y forward. 5 in left and 10 in forward is (-5, 10).
        otos.setOffset(new SparkFunOTOS.Pose2D(-leftIn, forwardIn, headingDeg));
        otos.setLinearScalar(linearScalar);
        otos.setAngularScalar(angularScalar);
        otos.calibrateImu();
        otos.resetTracking();
    }

    @Override
    protected double[] read() {
        SparkFunOTOS.Pose2D p = otos.getPosition();
        if (p == null) return null;
        // To the shared device frame (X forward, Y left): X = the OTOS's y, Y = minus its x.
        SparkFunOTOS.Pose2D v = otos.getVelocity();
        if (v == null) return new double[]{p.y, -p.x, p.h};
        return new double[]{p.y, -p.x, p.h, v.y, -v.x, v.h};
    }

    @Override
    protected void write(double xIn, double yIn, double headingDeg) {
        // From the shared device frame (X forward, Y left) to the OTOS's (x right, y forward).
        otos.setPosition(new SparkFunOTOS.Pose2D(-yIn, xIn, headingDeg));
    }

    @Override
    public boolean isReady() {
        long now = System.nanoTime();
        if (now - checkedAt > 1_000_000_000L) {
            connected = otos.isConnected();
            checkedAt = now;
        }
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
