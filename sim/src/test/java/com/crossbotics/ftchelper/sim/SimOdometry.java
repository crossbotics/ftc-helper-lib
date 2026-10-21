package com.crossbotics.ftchelper.sim;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.hardware.sparkfun.SparkFunOTOS;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.I2cDeviceSynch;
import com.qualcomm.robotcore.hardware.I2cDeviceSynchSimple;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

/**
 * Pretend odometry for the simulator, from the robot's true motion: a goBILDA Pinpoint, a SparkFun
 * OTOS, or dead-wheel encoders. With the robot file's noise settings it makes the small errors a real
 * one makes, so a team sees why driveTo keeps correcting:
 *
 * <pre>
 * odometry = pinpoint pinpoint            (or: otos otos / threeWheels l, r, p / twoWheels par, perp)
 * odometry.scaleError = 0.01              # measures 1% too far (wheel slip, a wrong pod size)
 * odometry.driftDegPerMin = 0.5           # the heading creeps this much per minute
 * </pre>
 *
 * The other odometry lines (offsets, track width, ticks per inch) come from Robot.java through
 * {@code :sim:fromRobot}; the pretend pods sit where those lines say.
 */
final class SimOdometry {
    final String kind;
    final List<String> names;
    private final double ticksPerIn, trackWidthIn, perpForwardIn, parallelLeftIn;
    private final List<String> reversed;
    private final double scaleError, driftDegPerMin;
    // Pod counts (dead wheels): left/parallel, right, perpendicular.
    private final double[] pods = new double[3];
    // The pretend device's own pose (X forward, Y left, inches; heading CCW deg), as a Pinpoint/OTOS keeps it.
    private double devX, devY, devH;
    private double vX, vY, vH;
    // A Pinpoint's raw pod counts and its heading not wrapped to -180..180 (the tuner's spin test).
    private double encX, encY, turnTotalDeg;
    private final double xPodLeftMm, yPodForwardMm;
    private boolean started = false;
    private double lastX, lastY, lastH;

    SimOdometry(Properties p) {
        String[] head = p.getProperty("odometry", "").trim().split("\\s+", 2);
        kind = head[0];
        names = head.length > 1 ? Arrays.asList(head[1].split("\\s*,\\s*")) : new ArrayList<>();
        if (!Arrays.asList("pinpoint", "otos", "threeWheels", "twoWheels").contains(kind)) {
            throw new IllegalArgumentException("Robot file: odometry = pinpoint NAME, otos NAME, threeWheels L, R, P or twoWheels PAR, PERP");
        }
        if (names.isEmpty() || names.get(0).isEmpty()) throw new IllegalArgumentException("Robot file: odometry needs its device name(s)");
        ticksPerIn = num(p, "odometry.ticksPerIn", 19.89436789 * 25.4);
        trackWidthIn = num(p, "odometry.trackWidthIn", 12);
        perpForwardIn = num(p, "odometry.perpForwardIn", 0);
        parallelLeftIn = num(p, "odometry.parallelLeftIn", 0);
        String r = p.getProperty("odometry.reversed", "").trim();
        reversed = r.isEmpty() ? new ArrayList<>() : Arrays.asList(r.split("\\s*,\\s*"));
        scaleError = num(p, "odometry.scaleError", 0);
        String off = p.getProperty("odometry.offsetsMm", "0, 0").split("#")[0].trim();
        String[] o = off.split("\\s*,\\s*");
        xPodLeftMm = o.length > 0 && !o[0].isEmpty() ? Double.parseDouble(o[0]) : 0;
        yPodForwardMm = o.length > 1 ? Double.parseDouble(o[1]) : 0;
        driftDegPerMin = num(p, "odometry.driftDegPerMin", 0);
    }

    private static double num(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) return fallback;
        try {
            return Double.parseDouble(v.trim().split("[\\s#]")[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Robot file: " + key + " must be a number");
        }
    }

    /** How far the robot has turned in total since the start (degrees, counterclockwise +). */
    synchronized double turnTotalDeg() {
        return turnTotalDeg;
    }

    /** The robot was put somewhere by hand (a reset or "place" in the simulator): not a move the pods see. */
    synchronized void teleported(double x, double y, double headingDeg) {
        lastX = x;
        lastY = y;
        lastH = headingDeg;
        started = true;
    }

    /** After each physics step: how the robot really moved (field frame, inches; heading CCW deg). */
    synchronized void step(double x, double y, double headingDeg, double dt) {
        if (!started) {
            started = true;
            lastX = x;
            lastY = y;
            lastH = headingDeg;
            return;
        }
        double dx = x - lastX, dy = y - lastY, dh = SimRobot.wrap(headingDeg - lastH);
        // The move seen from the robot at the MIDDLE of the step (heading 0 faces +y): forward and left,
        // inches; turn radians. The middle heading matches how the library adds pod moves back up.
        double hm = Math.toRadians(lastH + dh / 2);
        double scale = 1 + scaleError;
        double forward = (-dx * Math.sin(hm) + dy * Math.cos(hm)) * scale;
        double left = -(dx * Math.cos(hm) + dy * Math.sin(hm)) * scale;
        double turn = Math.toRadians(dh + driftDegPerMin / 60 * dt);
        lastX = x;
        lastY = y;
        lastH = headingDeg;
        // Dead wheels: each pod rolls by the robot's move at its own place.
        if (kind.equals("threeWheels")) {
            pods[0] += (forward - trackWidthIn / 2 * turn) * ticksPerIn;
            pods[1] += (forward + trackWidthIn / 2 * turn) * ticksPerIn;
            pods[2] += (left + perpForwardIn * turn) * ticksPerIn;
        } else if (kind.equals("twoWheels")) {
            pods[0] += (forward - parallelLeftIn * turn) * ticksPerIn;
            pods[2] += (left + perpForwardIn * turn) * ticksPerIn;
        }
        // A Pinpoint's pods, where the robot file says they sit (4-bar pods, 19.894 counts per mm).
        encX += (forward - xPodLeftMm / 25.4 * turn) * 25.4 * 19.89436789;
        encY += (left + yPodForwardMm / 25.4 * turn) * 25.4 * 19.89436789;
        turnTotalDeg += Math.toDegrees(turn);
        // A Pinpoint or OTOS: its own pose, turned with the robot (the middle of the turn).
        double mid = Math.toRadians(devH) + turn / 2;
        devX += forward * Math.cos(mid) - left * Math.sin(mid);
        devY += forward * Math.sin(mid) + left * Math.cos(mid);
        devH = SimRobot.wrap(devH + Math.toDegrees(turn));
        if (dt > 0) {
            vX = forward / dt;
            vY = left / dt;
            vH = Math.toDegrees(turn) / dt;
        }
    }

    // A pod's count as the code reads it: a pod the code reverses is wired to count backward.
    private synchronized int pod(int i, String name) {
        double c = pods[i];
        return (int) Math.round(reversed.contains(name) ? -c : c);
    }

    private synchronized void setDevice(double x, double y, double h) {
        devX = x;
        devY = y;
        devH = h;
    }

    /** Adds the pretend devices: a Pinpoint or OTOS by name, dead wheels on (or as) motor encoders. */
    void addDevices(Map<String, HardwareDevice> devices, Map<String, SimMotor> motors) {
        switch (kind) {
            case "pinpoint":
                devices.put(names.get(0), new Pinpoint());
                break;
            case "otos":
                devices.put(names.get(0), new Otos());
                break;
            default:
                boolean three = kind.equals("threeWheels");
                int[] slot = three ? new int[]{0, 1, 2} : new int[]{0, 2};
                for (int k = 0; k < slot.length && k < names.size(); k++) {
                    final int i = slot[k];
                    final String n = names.get(k);
                    SimMotor m = motors.get(n);
                    if (m != null) {
                        // A pod on a motor's port: that port's encoder is the pod's.
                        m.encoderOverride = () -> pod(i, n);
                    } else {
                        devices.put(n, encoderOnly(() -> pod(i, n)));
                    }
                }
        }
    }

    // An encoder port with nothing driven: only the count is real.
    private static DcMotorEx encoderOnly(java.util.function.IntSupplier count) {
        return (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(), new Class<?>[]{DcMotorEx.class},
                (pr, m, a) -> {
                    switch (m.getName()) {
                        case "getCurrentPosition": return count.getAsInt();
                        case "getDeviceName": return "Simulated odometry pod";
                        case "toString": return "Simulated odometry pod";
                        case "hashCode": return System.identityHashCode(pr);
                        case "equals": return pr == a[0];
                        default: return defaultFor(m.getReturnType());
                    }
                });
    }

    private static Object defaultFor(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == double.class) return 0.0;
        if (t == float.class) return 0.0f;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        return null;
    }

    private static <T> T nothing(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (pr, m, a) -> m.getName().equals("hashCode") ? System.identityHashCode(pr) : defaultFor(m.getReturnType())));
    }

    /** The pretend Pinpoint: the SDK class, its reads from the true motion. */
    final class Pinpoint extends GoBildaPinpointDriver {
        Pinpoint() {
            super(nothing(I2cDeviceSynchSimple.class), false);
        }

        @Override public void update() { }
        @Override public void update(ReadData data) { }
        @Override public void setOffsets(double xOffset, double yOffset, DistanceUnit unit) { }
        @Override public void setEncoderResolution(GoBildaOdometryPods pods) { }
        @Override public void setEncoderResolution(double ticksPerUnit, DistanceUnit unit) { }
        @Override public void setEncoderDirections(EncoderDirection xEncoder, EncoderDirection yEncoder) { }
        @Override public void resetPosAndIMU() { setDevice(0, 0, 0); }
        @Override public void recalibrateIMU() { }
        @Override public double getPosX(DistanceUnit unit) { synchronized (SimOdometry.this) { return unit.fromInches(devX); } }
        @Override public double getPosY(DistanceUnit unit) { synchronized (SimOdometry.this) { return unit.fromInches(devY); } }
        @Override public double getHeading(AngleUnit unit) { synchronized (SimOdometry.this) { return unit.fromDegrees(devH); } }
        @Override public double getVelX(DistanceUnit unit) { synchronized (SimOdometry.this) { return unit.fromInches(vX); } }
        @Override public double getVelY(DistanceUnit unit) { synchronized (SimOdometry.this) { return unit.fromInches(vY); } }
        @Override public double getHeadingVelocity(UnnormalizedAngleUnit unit) { synchronized (SimOdometry.this) { return unit.fromDegrees(vH); } }
        @Override public DeviceStatus getDeviceStatus() { return DeviceStatus.READY; }
        // A pod the code reverses is wired to count backward (like the dead wheels).
        @Override public int getEncoderX() { synchronized (SimOdometry.this) { return (int) Math.round(reversed.contains("x") ? -encX : encX); } }
        @Override public int getEncoderY() { synchronized (SimOdometry.this) { return (int) Math.round(reversed.contains("y") ? -encY : encY); } }
        @Override public double getHeading(UnnormalizedAngleUnit unit) { synchronized (SimOdometry.this) { return unit.fromDegrees(turnTotalDeg); } }
        @Override public Pose2D getPosition() {
            synchronized (SimOdometry.this) {
                return new Pose2D(DistanceUnit.INCH, devX, devY, AngleUnit.DEGREES, devH);
            }
        }
        @Override public void setPosition(Pose2D pos) {
            setDevice(pos.getX(DistanceUnit.INCH), pos.getY(DistanceUnit.INCH), pos.getHeading(AngleUnit.DEGREES));
        }
        @Override public String getDeviceName() { return "Simulated goBILDA Pinpoint"; }
        @Override public Manufacturer getManufacturer() { return Manufacturer.Other; }
    }

    /** The pretend OTOS (inches and degrees, as the library sets it). */
    final class Otos extends SparkFunOTOS {
        Otos() {
            super(nothing(I2cDeviceSynch.class));
        }

        @Override public boolean begin() { return true; }
        @Override public boolean isConnected() { return true; }
        @Override public boolean calibrateImu() { return true; }
        @Override public boolean calibrateImu(int samples, boolean wait) { return true; }
        @Override public void setLinearUnit(DistanceUnit unit) { }
        @Override public void setAngularUnit(AngleUnit unit) { }
        @Override public boolean setLinearScalar(double s) { return true; }
        @Override public boolean setAngularScalar(double s) { return true; }
        @Override public void setOffset(Pose2D pose) { }
        @Override public void resetTracking() { setDevice(0, 0, 0); }
        // The OTOS's own axes: x = right, y = forward (the device pose here is X forward, Y left).
        @Override public Pose2D getPosition() { synchronized (SimOdometry.this) { return new Pose2D(-devY, devX, devH); } }
        @Override public Pose2D getVelocity() { synchronized (SimOdometry.this) { return new Pose2D(-vY, vX, vH); } }
        @Override public void setPosition(Pose2D pose) { setDevice(pose.y, -pose.x, pose.h); }
        @Override public String getDeviceName() { return "Simulated SparkFun OTOS"; }
        @Override public Manufacturer getManufacturer() { return Manufacturer.Other; }
    }
}
