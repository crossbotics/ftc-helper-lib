package io.github.jasoncross.ftchelper.sim;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

/**
 * A pretend motor with an encoder. It behaves like a REV hub motor port: power, direction (which
 * flips the encoder too), the run modes, the hub's speed control, brake/float, and a current reading.
 * Its shaft moves by simple physics in {@link #step}: it speeds up toward (power x top speed) with a
 * short delay, a lift stops at its hard stops and is pulled down a little, a flywheel coasts long.
 */
final class SimMotor {
    enum Kind { FREE, WHEEL, LIFT, FLYWHEEL }

    final String name;
    final int port;
    final Kind kind;
    final double maxTicksPerSec;      // shaft speed at full power
    double ticksPerRev = 537.7;       // encoder counts per output-shaft turn (for degrees/radians per second)
    final double lagSec;              // how long it takes to get to speed
    // LIFT: hard stops (shaft counts from the start position) and how hard gravity pulls (power units).
    double minTicks = -Double.MAX_VALUE, maxTicks = Double.MAX_VALUE, gravity = 0;
    // FLYWHEEL: the motor that feeds it, and how much a shot slows it (share of the speed).
    String feeder = null;
    double shotDrop = 0.15;

    // What the OpMode set.
    private DcMotorSimple.Direction direction = DcMotorSimple.Direction.FORWARD;
    private DcMotor.RunMode mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;
    private DcMotor.ZeroPowerBehavior zeroPower = DcMotor.ZeroPowerBehavior.BRAKE;
    private double power = 0;
    private double targetVelocity = Double.NaN;   // NaN = setPower was used last
    private int targetPosition = 0;
    private boolean targetSet = false;   // RUN_TO_POSITION needs a target first, like the SDK
    private boolean enabled = true;
    private double p = 10, i = 3, d = 0, f = 0;   // the hub's default velocity gains
    private double integral = 0;

    // Physics, in the shaft's own direction (before the direction setting).
    double shaftPos = 0, shaftVel = 0;
    private double encoderZero = 0;
    volatile double amps = 0;
    volatile boolean atStop = false;
    // The push of the drive model (wheel motors): when set, it decides the shaft speed.
    Double forcedVel = null;
    volatile double appliedPower = 0;   // the power the shaft really gets this step, in its own direction

    final DcMotorEx device;
    // From the catalog part (or typical numbers): current when free and when stalled at full power.
    String partName = "";
    double freeAmps = 0.25, stallAmps = 9.2;

    void setPart(SimParts.Motor part) {
        partName = part.name;
        if (!Double.isNaN(part.freeCurrentA)) freeAmps = part.freeCurrentA;
        if (!Double.isNaN(part.stallCurrentA)) stallAmps = part.stallCurrentA;
    }

    SimMotor(String name, int port, Kind kind, double maxTicksPerSec, double lagSec) {
        this.name = name;
        this.port = port;
        this.kind = kind;
        this.maxTicksPerSec = maxTicksPerSec;
        this.lagSec = lagSec;
        this.device = (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(),
                new Class<?>[] {DcMotorEx.class}, (proxy, m, a) -> handle(proxy, m.getName(), a, m.getReturnType()));
    }

    private int sign() {
        return direction == DcMotorSimple.Direction.REVERSE ? -1 : 1;
    }

    synchronized int encoder() {
        return (int) Math.round(sign() * (shaftPos - encoderZero));
    }

    /** Where the shaft is (its own counts from the start, before the direction setting and encoder resets). */
    synchronized double shaft() {
        return shaftPos;
    }

    /** The shaft's own speed (before the direction setting), counts per second. */
    synchronized double shaftSpeed() {
        return shaftVel;
    }

    /** True when the shaft is within this many counts of its bottom stop. */
    synchronized boolean nearBottom(double counts) {
        return shaftPos <= minTicks + counts;
    }

    synchronized double velocity() {
        return sign() * shaftVel;
    }

    synchronized double userPower() {
        return power;
    }

    synchronized String modeName() {
        return mode.name();
    }

    /** Like the FTC app at INIT: direction, mode, brake, gains and power back to their defaults. */
    synchronized void resetConfig() {
        direction = DcMotorSimple.Direction.FORWARD;
        mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;
        zeroPower = DcMotor.ZeroPowerBehavior.BRAKE;
        power = 0;
        targetVelocity = Double.NaN;
        p = 10; i = 3; d = 0; f = 0;
        integral = 0;
        targetSet = false;
        enabled = true;
    }

    /** A hand push turns this wheel at this shaft speed (counts per second). */
    synchronized void forceSpin(double ticksPerSec) {
        forcedVel = ticksPerSec;
    }

    /** The hand push ended: the wheel stops with the robot. */
    synchronized void stopForcedSpin() {
        if (forcedVel != null) shaftVel = 0;
        forcedVel = null;
    }

    // The motor type the configuration would name: counts per turn and top speed from the robot file.
    private Object motorType() {
        try {
            com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType t =
                    new com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType();
            t.setTicksPerRev(ticksPerRev);
            t.setMaxRPM(maxTicksPerSec / ticksPerRev * 60.0);
            return t;
        } catch (Throwable e) {
            return null;
        }
    }

    private double perSecondIn(AngleUnit unit, double ticksPerSec) {
        double turns = ticksPerSec / ticksPerRev;
        return unit == AngleUnit.RADIANS ? turns * 2 * Math.PI : turns * 360.0;   // not wrapped: speeds, not angles
    }

    private synchronized Object handle(Object proxy, String method, Object[] a, Class<?> returns) {
        // A stuck OpMode that ignored STOP may not move anything any more.
        if (SimRobot.isBlocked() && method.startsWith("set")) return null;
        switch (method) {
            case "setPower":
                power = Math.max(-1, Math.min(1, ((Number) a[0]).doubleValue()));
                targetVelocity = Double.NaN;
                return null;
            case "getPower": return power;
            case "setDirection": direction = (DcMotorSimple.Direction) a[0]; return null;
            case "getDirection": return direction;
            case "setMode":
                // Like the SDK: RUN_TO_POSITION before setTargetPosition() is an error.
                if (a[0] == DcMotor.RunMode.RUN_TO_POSITION && !targetSet) {
                    throw new com.qualcomm.robotcore.exception.TargetPositionNotSetException();
                }
                mode = (DcMotor.RunMode) a[0];
                if (mode == DcMotor.RunMode.STOP_AND_RESET_ENCODER) {
                    encoderZero = shaftPos;
                    power = 0;
                    targetVelocity = Double.NaN;
                }
                integral = 0;
                return null;
            case "getMode": return mode;
            case "setZeroPowerBehavior": zeroPower = (DcMotor.ZeroPowerBehavior) a[0]; return null;
            case "getZeroPowerBehavior": return zeroPower;
            case "getCurrentPosition": return encoder();
            case "getVelocity":
                if (a != null && a.length == 1 && a[0] instanceof AngleUnit) return perSecondIn((AngleUnit) a[0], velocity());
                return velocity();
            case "setVelocity": {
                double v = ((Number) a[0]).doubleValue();
                if (a.length == 2 && a[1] instanceof AngleUnit) {
                    // degrees or radians per second -> encoder counts per second
                    double turns = a[1] == AngleUnit.RADIANS ? v / (2 * Math.PI) : v / 360.0;
                    v = turns * ticksPerRev;
                }
                targetVelocity = v;
                // Like the REV hub: a speed command switches plain power to speed control.
                if (mode == DcMotor.RunMode.RUN_WITHOUT_ENCODER) mode = DcMotor.RunMode.RUN_USING_ENCODER;
                return null;
            }
            case "setVelocityPIDFCoefficients":
                p = ((Number) a[0]).doubleValue();
                i = ((Number) a[1]).doubleValue();
                d = ((Number) a[2]).doubleValue();
                f = ((Number) a[3]).doubleValue();
                return null;
            case "setPIDFCoefficients":
                if (a[1] instanceof PIDFCoefficients) {
                    PIDFCoefficients c = (PIDFCoefficients) a[1];
                    p = c.p; i = c.i; d = c.d; f = c.f;
                }
                return null;
            case "getPIDFCoefficients": return new PIDFCoefficients(p, i, d, f);
            case "setTargetPosition": targetPosition = (Integer) a[0]; targetSet = true; return null;
            case "getTargetPosition": return targetPosition;
            case "isBusy":
                return mode == DcMotor.RunMode.RUN_TO_POSITION && Math.abs(encoder() - targetPosition) > 5;
            case "getCurrent":
                // the unit asked for (amps or milliamps)
                return a != null && a.length == 1 && String.valueOf(a[0]).equals("MILLIAMPS") ? amps * 1000 : amps;
            case "setMotorEnable": enabled = true; return null;
            case "setMotorDisable": enabled = false; power = 0; return null;
            case "getMotorType": return motorType();
            case "getPortNumber": return port;
            case "isMotorEnabled": return enabled;
            case "getDeviceName": return "Simulated motor";
            case "getConnectionInfo": return "simulated port " + port;
            case "getManufacturer": return com.qualcomm.robotcore.hardware.HardwareDevice.Manufacturer.Other;
            case "getVersion": return 1;
            case "getPowerFloat": return zeroPower == DcMotor.ZeroPowerBehavior.FLOAT && power == 0;
            case "toString": return "SimMotor(" + name + ")";
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return proxy == a[0];
            default: return SimDefaults.of(returns);
        }
    }

    /** The power the shaft gets now, in its own direction (the hub's speed control included). */
    synchronized double shaftPower(double dt) {
        double user;
        if (mode == DcMotor.RunMode.STOP_AND_RESET_ENCODER || !enabled) {
            // reset, or switched off with setMotorDisable(): no drive at all (speed control included)
            user = 0;
        } else if (mode == DcMotor.RunMode.RUN_TO_POSITION) {
            double err = targetPosition - encoder();
            user = Math.abs(power) * Math.max(-1, Math.min(1, err / 100.0));
        } else if (mode == DcMotor.RunMode.RUN_USING_ENCODER) {
            // The hub's speed control: setPower(x) asks for x times the top speed; setVelocity asks directly.
            double target = Double.isNaN(targetVelocity) ? power * maxTicksPerSec : targetVelocity;
            double err = target - velocity();
            if (Math.abs(target) < 1) {
                integral = 0;
                user = 0;
            } else {
                integral = Math.max(-20000, Math.min(20000, integral + err * dt));
                user = (f * target + p * err + i * integral) / 32767.0;
            }
        } else {
            user = power;
        }
        user = Math.max(-1, Math.min(1, user));
        return sign() * user;
    }

    synchronized boolean braking() {
        return zeroPower == DcMotor.ZeroPowerBehavior.BRAKE;
    }

    /** Moves the shaft for dt seconds. battery: 1 = full, less = a weaker battery. */
    synchronized void step(double dt, double battery) {
        appliedPower = shaftPower(dt);
        if (forcedVel != null) {
            shaftVel = forcedVel;
        } else {
            double target = appliedPower * maxTicksPerSec * battery;
            if (kind == Kind.LIFT) target -= gravity * maxTicksPerSec;
            double lag = lagSec;
            if (Math.abs(appliedPower) < 1e-6) {
                // Not driven: brake stops it fast (and holds a lift against gravity); float coasts.
                if (braking()) {
                    // A heavy flywheel takes a while to stop even with brake; everything else stops fast.
                    target = 0;
                    lag = kind == Kind.FLYWHEEL ? 0.6 : 0.05;
                } else {
                    lag = kind == Kind.FLYWHEEL ? 2.5 : 0.4;
                }
            }
            shaftVel += (target - shaftVel) * Math.min(1, dt / Math.max(1e-3, lag));
        }
        shaftPos += shaftVel * dt;
        atStop = false;
        if (shaftPos <= minTicks) { shaftPos = minTicks; if (shaftVel < 0) { shaftVel = 0; atStop = true; } }
        if (shaftPos >= maxTicks) { shaftPos = maxTicks; if (shaftVel > 0) { shaftVel = 0; atStop = true; } }
        // Current: small when free, large when pushing and not turning (stalled).
        // A DC motor's current grows as it slows under load: free current at full speed, stall current
        // when it cannot turn (scaled by the power it gets).
        double p = Math.abs(appliedPower);
        double speedShare = Math.min(1, Math.abs(shaftVel) / Math.max(1, p * maxTicksPerSec));
        amps = p < 0.01 ? 0 : p * (freeAmps + (stallAmps - freeAmps) * (1 - speedShare));
    }

    /** A piece went through the flywheel: it loses some speed. */
    synchronized void shot() {
        shaftVel *= 1 - shotDrop;
    }
}
