package com.crossbotics.ftchelper.odometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.util.FieldPose;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.hardware.sparkfun.SparkFunOTOS;
import com.qualcomm.robotcore.hardware.I2cDeviceSynch;
import com.qualcomm.robotcore.hardware.I2cDeviceSynchSimple;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;
import org.junit.Test;

public class OdometryTest {
    private static final double E = 1e-6;
    // 505 counts per inch, like a goBILDA 4-bar pod (19.894 per mm).
    private static final double TPI = 19.89436789 * 25.4;

    @Test public void threeWheelsForwardTurnAndStrafe() {
        FakeMotor l = new FakeMotor(), r = new FakeMotor(), p = new FakeMotor();
        DeadWheelOdometry o = DeadWheelOdometry.threeWheels("odo", l.motor, r.motor, p.motor, TPI, 12, -6, false, false, false);
        o.update();
        // Forward 10 in: both forward pods +10 in. In our frame that is y +10.
        l.position += 10 * TPI;
        r.position += 10 * TPI;
        o.update();
        assertEquals(0, o.pose().x, 1e-3);
        assertEquals(10, o.pose().y, 1e-3);
        // Turn left 90 degrees in place: the right pod forward, the left pod back, by (12/2) * pi/2.
        double arc = 6 * Math.PI / 2;
        for (int i = 0; i < 90; i++) {   // in small steps, as the loop would see it
            l.position -= arc / 90 * TPI;
            r.position += arc / 90 * TPI;
            p.position += (-6) * (Math.PI / 2 / 90) * TPI;    // the pod 6 in behind the centre swings right while turning left
            o.update();
        }
        assertEquals(90, o.pose().headingDeg, 0.5);
        assertEquals(0, o.pose().x, 0.05);
        assertEquals(10, o.pose().y, 0.05);
        // Now facing left (-x). A strafe to the robot's left (perp pod +5 in) moves it toward the drivers: y -5.
        p.position += 5 * TPI;
        o.update();
        assertEquals(0, o.pose().x, 0.05);
        assertEquals(5, o.pose().y, 0.05);
    }

    @Test public void setPoseMovesTheStartPoint() {
        FakeMotor l = new FakeMotor(), r = new FakeMotor(), p = new FakeMotor();
        DeadWheelOdometry o = DeadWheelOdometry.threeWheels("odo", l.motor, r.motor, p.motor, TPI, 12, 0, false, false, false);
        o.update();
        o.setPose(new FieldPose(-36, -60, 0));
        l.position += 12 * TPI;
        r.position += 12 * TPI;
        o.update();
        assertEquals(-36, o.pose().x, 1e-3);
        assertEquals(-48, o.pose().y, 1e-3);
    }

    // A pretend Pinpoint: the SDK class with a do-nothing I2C client and its reads overridden.
    static final class FakePinpoint extends GoBildaPinpointDriver {
        double x, y, h;   // the device's frame: X forward, Y left (inches), heading CCW degrees
        DeviceStatus status = DeviceStatus.READY;

        FakePinpoint() {
            super((I2cDeviceSynchSimple) Proxy.newProxyInstance(I2cDeviceSynchSimple.class.getClassLoader(),
                    new Class<?>[]{I2cDeviceSynchSimple.class}, (pr, m, a) -> {
                        Class<?> t = m.getReturnType();
                        if (t == boolean.class) return false;
                        if (t == int.class || t == short.class || t == byte.class) return 0;
                        if (t == long.class) return 0L;
                        if (t == double.class || t == float.class) return 0.0f;
                        return null;
                    }), false);
        }

        @Override public void update() { }
        @Override public void setOffsets(double xOffset, double yOffset, DistanceUnit unit) { }
        @Override public void setEncoderResolution(GoBildaOdometryPods pods) { }
        @Override public void setEncoderResolution(double ticksPerUnit, DistanceUnit unit) { }
        @Override public void setEncoderDirections(EncoderDirection xEncoder, EncoderDirection yEncoder) { }
        @Override public void resetPosAndIMU() { x = y = h = 0; }
        @Override public double getPosX(DistanceUnit unit) { return unit.fromInches(x); }
        @Override public double getPosY(DistanceUnit unit) { return unit.fromInches(y); }
        @Override public double getHeading(AngleUnit unit) { return unit.fromDegrees(h); }
        @Override public double getVelX(DistanceUnit unit) { return 0; }
        @Override public double getVelY(DistanceUnit unit) { return 0; }
        @Override public double getHeadingVelocity(org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit unit) { return 0; }
        @Override public DeviceStatus getDeviceStatus() { return status; }
        @Override public void setPosition(Pose2D pos) {
            x = pos.getX(DistanceUnit.INCH);
            y = pos.getY(DistanceUnit.INCH);
            h = pos.getHeading(AngleUnit.DEGREES);
        }
    }

    @Test public void pinpointFrameIsTurnedIntoOurs() {
        FakePinpoint pp = new FakePinpoint();
        PinpointOdometry o = new PinpointOdometry("pinpoint", pp, -84, -168, GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD, 0, false, false);
        pp.x = 10;      // 10 in forward (at heading 0 = away from the drivers)
        pp.y = 3;       // 3 in left
        pp.h = 30;
        o.update();
        assertEquals(-3, o.pose().x, E);     // left = toward -x
        assertEquals(10, o.pose().y, E);     // forward = away = +y
        assertEquals(30, o.pose().headingDeg, E);
        o.setPose(new FieldPose(12, -60, 90));
        assertEquals(-60, pp.x, E);
        assertEquals(-12, pp.y, E);
        assertEquals(90, pp.h, E);
        assertTrue(o.isReady());
        pp.status = GoBildaPinpointDriver.DeviceStatus.FAULT_Y_POD_NOT_DETECTED;
        assertEquals("Y pod not found: check its cable", o.status());
    }

    // A pretend OTOS: its own axes are x = RIGHT, y = FORWARD (the SDK sample).
    static final class FakeOtos extends SparkFunOTOS {
        Pose2D pos = new Pose2D(0, 0, 0), offset = new Pose2D(0, 0, 0);
        boolean connected = true;

        FakeOtos() {
            super((I2cDeviceSynch) Proxy.newProxyInstance(I2cDeviceSynch.class.getClassLoader(),
                    new Class<?>[]{I2cDeviceSynch.class}, (pr, m, a) -> {
                        Class<?> t = m.getReturnType();
                        if (t == boolean.class) return false;
                        if (t == int.class || t == short.class || t == byte.class) return 0;
                        if (t == long.class) return 0L;
                        if (t == double.class || t == float.class) return 0.0f;
                        return null;
                    }));
        }

        @Override public boolean isConnected() { return connected; }
        @Override public boolean calibrateImu() { return true; }
        @Override public void setLinearUnit(DistanceUnit unit) { }
        @Override public void setAngularUnit(AngleUnit unit) { }
        @Override public boolean setLinearScalar(double s) { return true; }
        @Override public boolean setAngularScalar(double s) { return true; }
        @Override public void setOffset(Pose2D p) { offset = p; }
        @Override public void resetTracking() { pos = new Pose2D(0, 0, 0); }
        @Override public Pose2D getPosition() { return pos; }
        @Override public Pose2D getVelocity() { return new Pose2D(0, 0, 0); }
        @Override public void setPosition(Pose2D p) { pos = p; }
    }

    @Test public void otosAxesAreTurnedIntoOurs() {
        FakeOtos otos = new FakeOtos();
        // Mounted 10 in forward and 5 in left: the OTOS's own offset is (-5, 10).
        OtosOdometry o = new OtosOdometry("otos", otos, 10, 5, 0, 1, 1);
        assertEquals(-5, otos.offset.x, E);
        assertEquals(10, otos.offset.y, E);
        // 10 in forward (heading 0 = away): the OTOS reads y = 10; ours is y = 10 too.
        otos.pos = new SparkFunOTOS.Pose2D(0, 10, 0);
        o.update();
        assertEquals(0, o.pose().x, E);
        assertEquals(10, o.pose().y, E);
        o.setPose(new FieldPose(12, -60, 90));
        assertEquals(12, otos.pos.x, E);
        assertEquals(-60, otos.pos.y, E);
        assertEquals(90, otos.pos.h, E);
    }

    // Review (Fable): a pose set while the Pinpoint calibrates is written once it is ready.
    @Test public void aPoseSetWhileCalibratingIsWrittenWhenReady() {
        FakePinpoint pp = new FakePinpoint();
        PinpointOdometry o = new PinpointOdometry("pinpoint", pp, 0, 0, GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD, 0, false, false);
        pp.status = GoBildaPinpointDriver.DeviceStatus.CALIBRATING;
        o.setPose(new FieldPose(12, -60, 90));
        o.update();
        assertEquals(0, pp.x, E);                    // not written yet
        assertEquals(12, o.pose().x, E);             // but the pose is already the one we gave
        pp.status = GoBildaPinpointDriver.DeviceStatus.READY;
        o.update();
        assertEquals(-60, pp.x, E);
        assertEquals(-12, pp.y, E);
        assertEquals(12, o.pose().x, E);
    }
}
