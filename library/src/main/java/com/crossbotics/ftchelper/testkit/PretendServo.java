package com.crossbotics.ftchelper.testkit;

import com.qualcomm.robotcore.hardware.PwmControl;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Proxy;

/**
 * A pretend servo for JUnit tests: it remembers the position it was sent (NaN = none yet, like a real
 * servo before its first command). Get one from {@link PretendDevices#servo(String)}.
 *
 * <pre>{@code
 * robot.claw.close();
 * assertEquals(0.70, devices.servo("claw").position, 1e-9);
 * }</pre>
 *
 * Docs: docs/modules/testkit.md
 */
public class PretendServo {
    /** The last position sent, 0 to 1 (NaN = never sent). */
    public double position = Double.NaN;
    /** How many positions were sent. */
    public int writes = 0;
    /** FORWARD or REVERSE, as set. */
    public Servo.Direction direction = Servo.Direction.FORWARD;
    /** The pulse range set with {@code .fullRange()} ({low, high} microseconds), or null (the SDK's 600-2400). */
    public double[] pulseUs = null;
    /** The SDK servo to hand to code that wants one (it is also a PwmControl, like a hub servo). */
    public final Servo servo;

    /** A new pretend servo on a hub (it can change its pulse range). */
    public PretendServo() {
        servo = (Servo) Proxy.newProxyInstance(Servo.class.getClassLoader(), new Class<?>[]{Servo.class, PwmControl.class},
                (proxy, m, a) -> {
                    switch (m.getName()) {
                        case "setPosition": position = (Double) a[0]; writes++; return null;
                        case "getPosition": return position;
                        case "setDirection": direction = (Servo.Direction) a[0]; return null;
                        case "getDirection": return direction;
                        case "setPwmRange": {
                            PwmControl.PwmRange r = (PwmControl.PwmRange) a[0];
                            pulseUs = new double[] {r.usPulseLower, r.usPulseUpper};
                            return null;
                        }
                        case "getDeviceName": return "Pretend servo";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        default: return PretendDevices.nothing(m.getReturnType());
                    }
                });
    }
}
