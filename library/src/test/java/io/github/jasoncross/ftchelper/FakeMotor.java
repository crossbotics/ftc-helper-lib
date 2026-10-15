package io.github.jasoncross.ftchelper;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import java.lang.reflect.Proxy;

/** A pretend motor for tests: remembers power, counts writes, and has a settable encoder. */
public final class FakeMotor {
    public double power = 0;
    public double position = 0;
    public int powerWrites = 0;
    public double maxAbsPower = 0;
    public DcMotor.ZeroPowerBehavior zeroPower = DcMotor.ZeroPowerBehavior.BRAKE;
    public final DcMotorEx motor;

    public FakeMotor() {
        motor = (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(),
                new Class<?>[]{DcMotorEx.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setPower":
                            power = (Double) args[0];
                            powerWrites++;
                            maxAbsPower = Math.max(maxAbsPower, Math.abs(power));
                            return null;
                        case "getPower": return power;
                        case "getCurrentPosition": return (int) Math.round(position);
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
