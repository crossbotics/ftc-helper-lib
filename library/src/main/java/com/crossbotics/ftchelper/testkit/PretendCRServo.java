package com.crossbotics.ftchelper.testkit;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import java.lang.reflect.Proxy;

/**
 * A pretend continuous-rotation (CR) servo for JUnit tests: it remembers the power it was sent. Get one
 * from {@link PretendDevices#crServo(String)}.
 *
 * Docs: docs/modules/testkit.md
 */
public class PretendCRServo {
    /** The last power sent, -1 to 1. */
    public double power = 0;
    /** How many powers were sent. */
    public int writes = 0;
    /** FORWARD or REVERSE, as set. */
    public DcMotorSimple.Direction direction = DcMotorSimple.Direction.FORWARD;
    /** The SDK CR servo to hand to code that wants one. */
    public final CRServo servo = (CRServo) Proxy.newProxyInstance(CRServo.class.getClassLoader(),
            new Class<?>[]{CRServo.class}, (proxy, m, a) -> {
                switch (m.getName()) {
                    case "setPower": power = (Double) a[0]; writes++; return null;
                    case "getPower": return power;
                    case "setDirection": direction = (DcMotorSimple.Direction) a[0]; return null;
                    case "getDirection": return direction;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == a[0];
                    default: return PretendDevices.nothing(m.getReturnType());
                }
            });
}
