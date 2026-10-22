package com.crossbotics.ftchelper.testkit;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.I2cDeviceSynchSimple;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

/**
 * A pretend goBILDA Pinpoint (odometry computer) for JUnit tests: it says the robot is where your test
 * puts it. Get it from {@link PretendDevices#pinpoint(String)}; a robot with
 * {@code odometry(Odometry.pinpoint("pinpoint"))} gets it at INIT.
 *
 * <pre>{@code
 * devices.pinpoint("pinpoint").moveTo(10, 0, 90);   // the device's own frame: x forward, y left (inches, degrees)
 * }</pre>
 *
 * Docs: docs/modules/testkit.md
 */
public class PretendPinpoint extends GoBildaPinpointDriver {
    /** Where the device says it is, in its own frame: x inches forward, y inches left, heading degrees counterclockwise. */
    public double x = 0, y = 0, headingDeg = 0;

    /** A pretend Pinpoint at 0, 0, 0. */
    public PretendPinpoint() {
        // The SDK class wants an I2C connection: a pretend one that answers nothing.
        super((I2cDeviceSynchSimple) Proxy.newProxyInstance(I2cDeviceSynchSimple.class.getClassLoader(),
                new Class<?>[]{I2cDeviceSynchSimple.class},
                (p, m, a) -> m.getName().equals("hashCode") ? System.identityHashCode(p) : PretendDevices.nothing(m.getReturnType())), false);
    }

    /** Puts the device's pose here (its own frame: x forward, y left, inches; heading degrees). */
    public PretendPinpoint moveTo(double xIn, double yIn, double headingDegrees) {
        x = xIn;
        y = yIn;
        headingDeg = headingDegrees;
        return this;
    }

    @Override public void update() { }
    @Override public void update(ReadData data) { }
    @Override public void setOffsets(double xOffset, double yOffset, DistanceUnit unit) { }
    @Override public void setEncoderResolution(GoBildaOdometryPods pods) { }
    @Override public void setEncoderResolution(double ticksPerUnit, DistanceUnit unit) { }
    @Override public void setEncoderDirections(EncoderDirection xEncoder, EncoderDirection yEncoder) { }
    @Override public void resetPosAndIMU() { moveTo(0, 0, 0); }
    @Override public void recalibrateIMU() { }
    @Override public double getPosX(DistanceUnit unit) { return unit.fromInches(x); }
    @Override public double getPosY(DistanceUnit unit) { return unit.fromInches(y); }
    @Override public double getHeading(AngleUnit unit) { return unit.fromDegrees(headingDeg); }
    @Override public double getHeading(UnnormalizedAngleUnit unit) { return unit.fromDegrees(headingDeg); }
    @Override public double getVelX(DistanceUnit unit) { return 0; }
    @Override public double getVelY(DistanceUnit unit) { return 0; }
    @Override public double getHeadingVelocity(UnnormalizedAngleUnit unit) { return 0; }
    @Override public DeviceStatus getDeviceStatus() { return DeviceStatus.READY; }
    @Override public int getEncoderX() { return 0; }
    @Override public int getEncoderY() { return 0; }
    @Override public Pose2D getPosition() { return new Pose2D(DistanceUnit.INCH, x, y, AngleUnit.DEGREES, headingDeg); }

    @Override
    public void setPosition(Pose2D pos) {
        moveTo(pos.getX(DistanceUnit.INCH), pos.getY(DistanceUnit.INCH), pos.getHeading(AngleUnit.DEGREES));
    }

    @Override public String getDeviceName() { return "Pretend goBILDA Pinpoint"; }
    @Override public Manufacturer getManufacturer() { return Manufacturer.Other; }
}
