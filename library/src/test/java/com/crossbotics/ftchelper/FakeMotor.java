package com.crossbotics.ftchelper;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import java.lang.reflect.Proxy;

/** A pretend motor for tests: remembers power, counts writes, and has a settable encoder and speed. */
public final class FakeMotor {
    public double power = 0;
    public double position = 0;
    public double velocity = 0;          // ticks per second, what getVelocity() returns
    public double velocityCommand = 0;   // last setVelocity()
    public double currentAmps = 0;
    public int powerWrites = 0;
    public double maxAbsPower = 0;
    public double[] pidf = null;
    public boolean failSetPower = false;  // true = setPower throws, like a disconnected motor
    public DcMotor.RunMode mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;
    public DcMotor.ZeroPowerBehavior zeroPower = DcMotor.ZeroPowerBehavior.BRAKE;
    public final DcMotorEx motor;

    public FakeMotor() {
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
                        case "getCurrentPosition": return (int) Math.round(position);
                        case "getVelocity": return velocity;
                        case "setVelocity": velocityCommand = (Double) args[0]; return null;
                        case "getCurrent": return currentAmps;
                        case "setMode":
                            mode = (DcMotor.RunMode) args[0];
                            if (mode == DcMotor.RunMode.STOP_AND_RESET_ENCODER) position = 0;
                            return null;
                        case "getMode": return mode;
                        case "setVelocityPIDFCoefficients":
                            pidf = new double[]{(Double) args[0], (Double) args[1], (Double) args[2], (Double) args[3]};
                            return null;
                        case "getZeroPowerBehavior": return zeroPower;
                        case "setZeroPowerBehavior": zeroPower = (DcMotor.ZeroPowerBehavior) args[0]; return null;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "FakeMotor";
                        default:
                            Class<?> r = method.getReturnType();
                            if (r == boolean.class) return false;
                            if (r == int.class) return 0;
                            if (r == double.class) return 0.0;
                            return null;
                    }
                });
    }
}
