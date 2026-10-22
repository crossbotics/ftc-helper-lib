package com.crossbotics.ftchelper.testkit;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import java.lang.reflect.Proxy;

/**
 * A pretend motor for JUnit tests on a laptop (no robot): it remembers the power it was sent, and its
 * encoder is a number your test sets. Get one from {@link PretendDevices#motor(String)}.
 *
 * <pre>{@code
 * PretendMotor lift = devices.motor("lift");
 * lift.position = 900;                 // the encoder now reads 900
 * robot.all().update();
 * assertEquals(0, lift.power, 0.01);   // at its target: no power
 * }</pre>
 *
 * <p>The fields are public on purpose: a test reads and sets them directly.
 *
 * Docs: docs/modules/testkit.md, docs/guides/testing.md
 */
public class PretendMotor {
    /** The last power sent (-1 to 1). */
    public double power = 0;
    /** The encoder count your test sets; {@code getCurrentPosition()} gives it rounded. */
    public double position = 0;
    /** Ticks per second, what {@code getVelocity()} gives. */
    public double velocity = 0;
    /** The last {@code setVelocity(...)} (ticks per second). */
    public double velocityCommand = 0;
    /** What {@code getCurrent(...)} gives, amps. */
    public double currentAmps = 0;
    /** How many times a power was sent (each is a trip to the hub on a robot). */
    public int powerWrites = 0;
    /** How many times the encoder was read. */
    public int positionReads = 0;
    /** The largest power (either way) ever sent. */
    public double maxAbsPower = 0;
    /** The last {@code setVelocityPIDFCoefficients} gains {p, i, d, f}, or null. */
    public double[] pidf = null;
    /** True: {@code setPower} throws, like an unplugged motor. */
    public boolean failSetPower = false;
    /** The run mode set (STOP_AND_RESET_ENCODER also sets {@link #position} to 0). */
    public DcMotor.RunMode mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;
    /** BRAKE or FLOAT, as set. */
    public DcMotor.ZeroPowerBehavior zeroPower = DcMotor.ZeroPowerBehavior.BRAKE;
    /** FORWARD or REVERSE, as set ({@code .reversed()} sets REVERSE). */
    public DcMotorSimple.Direction direction = DcMotorSimple.Direction.FORWARD;
    /** The SDK motor to hand to code that wants one. */
    public final DcMotorEx motor;

    /** A new pretend motor at power 0 and position 0. */
    public PretendMotor() {
        // A "proxy": an object that answers every DcMotorEx method through the small switch below.
        motor = (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(),
                new Class<?>[]{DcMotorEx.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setPower":
                            if (failSetPower) throw new RuntimeException("motor unplugged");
                            power = (Double) args[0];
                            powerWrites++;
                            maxAbsPower = Math.max(maxAbsPower, Math.abs(power));
                            return null;
                        case "getPower": return power;
                        case "getCurrentPosition": positionReads++; return (int) Math.round(position);
                        case "getVelocity": return velocity;
                        case "setVelocity": velocityCommand = (Double) args[0]; return null;
                        case "getCurrent": return currentAmps;
                        case "setMode":
                            mode = (DcMotor.RunMode) args[0];
                            if (mode == DcMotor.RunMode.STOP_AND_RESET_ENCODER) position = 0;
                            return null;
                        case "getMode": return mode;
                        case "setDirection": direction = (DcMotorSimple.Direction) args[0]; return null;
                        case "getDirection": return direction;
                        case "setVelocityPIDFCoefficients":
                            pidf = new double[]{(Double) args[0], (Double) args[1], (Double) args[2], (Double) args[3]};
                            return null;
                        case "getZeroPowerBehavior": return zeroPower;
                        case "setZeroPowerBehavior": zeroPower = (DcMotor.ZeroPowerBehavior) args[0]; return null;
                        case "getDeviceName": return "Pretend motor";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "PretendMotor";
                        default: return PretendDevices.nothing(method.getReturnType());
                    }
                });
    }
}
