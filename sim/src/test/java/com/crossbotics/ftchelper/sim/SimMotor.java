package com.crossbotics.ftchelper.sim;

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
    // The motor's "feedforward" model (see the library's Feedforward and the Characterizer):
    //   power x battery = kS x sign(speed) + speed / maxTicksPerSec + accelSec / maxTicksPerSec x acceleration (+ gravity)
    double lagSec;                    // accelSec: seconds to reach about 63% of a new speed
    double kS = 0;                    // power just to start moving (friction)
    double coastSec = Double.NaN;     // FLOAT at zero power: seconds to lose about 63% of its speed (NaN = by kind)
    // LIFT: hard stops (shaft counts from the start position) and how hard gravity pulls (power units).
    double minTicks = -Double.MAX_VALUE, maxTicks = Double.MAX_VALUE, gravity = 0;
    // drivenBy=: this "motor" is really an encoder on a shaft that a CR servo turns (a CR servo turret's
    // Through Bore): its power comes from that CR servo (null = from its own setPower).
    volatile java.util.function.DoubleSupplier drivenBy = null;
    // A second motor on the same slide or arm (with= in the robot file): the slide's own motor (null = it
    // is one itself), and whether its shaft turns the other way (mirrored=). The slide's own motor keeps
    // the list of its partners: their pushes add to its own.
    SimMotor leader = null;
    boolean mirrored = false;
    final java.util.List<SimMotor> partners = new java.util.ArrayList<>();
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
    // A dead-wheel odometry pod plugged into this port: its count replaces the motor's own (null = none).
    volatile java.util.function.IntSupplier encoderOverride = null;
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
            case "setPower": {
                double was = Double.isNaN(targetVelocity) ? power : targetVelocity;
                power = Math.max(-1, Math.min(1, ((Number) a[0]).doubleValue()));
                targetVelocity = Double.NaN;
                commanded(was, power);
                return null;
            }
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
            case "getCurrentPosition": return encoderOverride != null ? sign() * encoderOverride.getAsInt() : encoder();
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
                double was = Double.isNaN(targetVelocity) ? power : targetVelocity;
                targetVelocity = v;
                commanded(was, v);
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

    // A stop that the next command undid before a physics step saw it. A real hub gets the stop at once and
    // holds it until the next command, however short that is: the robot brakes for that long, and the hub's
    // speed control starts again from nothing. Without this, setPower(0) then setPower(x) inside one physics
    // step was never a stop (the next move started with the old speed and the old controller state).
    private long zeroSinceNanos = 0;          // when power went to 0 (0 = it is not stopped now)
    private double zeroPendingSec = 0;        // how long it was stopped before the next command, not yet stepped

    // For tests: the hub speed control's built-up error.
    synchronized double integralForTests() {
        return integral;
    }

    private void commanded(double was, double now) {
        boolean stopNow = now == 0, stoppedBefore = was == 0;
        if (stopNow) {
            integral = 0;                     // the hub's speed control: a stop forgets what it had built up
            if (!stoppedBefore) zeroSinceNanos = System.nanoTime();
        } else if (stoppedBefore && zeroSinceNanos != 0) {
            zeroPendingSec += (System.nanoTime() - zeroSinceNanos) / 1e9;
            zeroSinceNanos = 0;
        }
    }

    /** The power the shaft gets now, in its own direction (the hub's speed control included). */
    synchronized double shaftPower(double dt) {
        double user;
        if (drivenBy != null) {
            // Turned by a CR servo: its power, whatever this port's mode (only its count is read).
            return Math.max(-1, Math.min(1, drivenBy.getAsDouble()));
        } else if (mode == DcMotor.RunMode.STOP_AND_RESET_ENCODER || !enabled) {
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

    /** A second motor on a slide (with=): only works out its power this step; the slide's own motor moves both. */
    synchronized void pushOnly(double dt) {
        appliedPower = shaftPower(dt);
    }

    /** A second motor on a slide: its shaft goes where the slide's own motor's shaft went (turned round if mirrored). */
    void follow() {
        double pos, vel;
        synchronized (leader) {
            pos = leader.shaftPos;
            vel = leader.shaftVel;
        }
        synchronized (this) {
            double sign = mirrored ? -1 : 1;
            shaftPos = sign * pos;
            shaftVel = sign * vel;
            atStop = leader.atStop;
            double p = Math.abs(appliedPower);
            double speedShare = Math.min(1, Math.abs(shaftVel) / Math.max(1, p * maxTicksPerSec));
            amps = p < 0.01 ? 0 : p * (freeAmps + (stallAmps - freeAmps) * (1 - speedShare));
        }
    }

    // This motor's power and its partners' (in this shaft's direction), shared: two motors on one slide
    // each carry half the load, so the slide moves as if one motor got the average power with half the gravity.
    private double sharedPower(double own) {
        if (partners.isEmpty()) return own;
        double sum = own;
        for (SimMotor m : partners) sum += (m.mirrored ? -1 : 1) * m.appliedPower;
        return sum / (partners.size() + 1);
    }

    /** Moves the shaft for dt seconds. battery: 1 = full, less = a weaker battery. */
    synchronized void step(double dt, double battery) {
        // A stop the OpMode undid before this step: first that stop (brake or coast), then the new command.
        double stopped = Math.min(dt, zeroPendingSec);
        zeroPendingSec = 0;
        // still stopped: count from now (the time before this step has been stepped already)
        boolean stoppedNow = Double.isNaN(targetVelocity) ? power == 0 : targetVelocity == 0;
        zeroSinceNanos = stoppedNow ? System.nanoTime() : 0;
        if (stopped > 1e-6 && forcedVel == null && drivenBy == null) {
            stepFor(stopped, battery, true);
            dt -= stopped;
            if (dt <= 1e-9) return;
        }
        stepFor(dt, battery, false);
    }

    private void stepFor(double dt, double battery, boolean stopping) {
        appliedPower = sharedPower(stopping ? 0 : shaftPower(dt));
        if (forcedVel != null) {
            shaftVel = forcedVel;
        } else {
            // The push left after gravity (a lift; shared by its motors), and the speed it would settle at
            // with friction (kS).
            double push = appliedPower * battery - (kind == Kind.LIFT ? gravity / (partners.size() + 1) : 0);
            double target;
            double lag = lagSec;
            if (Math.abs(appliedPower) < 1e-6 && braking()) {
                // Not driven, BRAKE: it stops fast (and holds a lift against gravity). A heavy flywheel
                // takes a while to stop even with brake.
                target = 0;
                lag = kind == Kind.FLYWHEEL ? 0.6 : 0.05;
            } else if (Math.abs(shaftVel) < 1 && Math.abs(push) <= kS) {
                target = 0;                                           // friction holds it still
            } else {
                double direction = Math.abs(shaftVel) >= 1 ? Math.signum(shaftVel) : Math.signum(push);
                // The full model while moving (friction slowing it included); the zero-crossing check
                // below stops friction from pushing it backward.
                target = (push - kS * direction) * maxTicksPerSec;
                if (Math.abs(appliedPower) < 1e-6) {
                    // Not driven, FLOAT: it coasts (a lift falls).
                    lag = !Double.isNaN(coastSec) ? coastSec : kind == Kind.FLYWHEEL ? 2.5 : 0.4;
                }
            }
            double before = shaftVel;
            shaftVel += (target - shaftVel) * Math.min(1, dt / Math.max(1e-3, lag));
            // Friction stops a motor; it never turns it around. Crossing zero without enough push: stop.
            if (Math.signum(shaftVel) == -Math.signum(before) && Math.abs(push) <= kS) shaftVel = 0;
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
