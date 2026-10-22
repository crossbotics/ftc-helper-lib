package com.crossbotics.ftchelper;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/** Small pretend devices for tests. */
public final class Fakes {
    private Fakes() { }

    /** A pretend clock in nanoseconds that tests move by hand. */
    public static final class Clock {
        public long nanos = 0;
        public void addSec(double s) { nanos += (long) (s * 1e9); }
        public long get() { return nanos; }
    }

    /** A pretend servo. */
    public static final class FakeServo {
        public double position = Double.NaN;
        public int writes = 0;
        public Servo.Direction direction = Servo.Direction.FORWARD;
        // The pulse range set through PwmControl (null = never set: the SDK's normal 600-2400 us).
        public double[] pulseUs = null;
        public final Servo servo;

        /** A hub servo: a Servo that is also a PwmControl (like the SDK's ServoImplEx). */
        public FakeServo() {
            this(true);
        }

        /** {@code pwm} false: a plain Servo that cannot change its pulse range. */
        public FakeServo(boolean pwm) {
            Class<?>[] types = pwm ? new Class<?>[]{Servo.class, com.qualcomm.robotcore.hardware.PwmControl.class}
                    : new Class<?>[]{Servo.class};
            servo = (Servo) Proxy.newProxyInstance(Servo.class.getClassLoader(), types, (proxy, m, a) -> {
                    switch (m.getName()) {
                        case "setPosition": position = (Double) a[0]; writes++; return null;
                        case "getPosition": return position;
                        case "setDirection": direction = (Servo.Direction) a[0]; return null;
                        case "setPwmRange": {
                            com.qualcomm.robotcore.hardware.PwmControl.PwmRange r = (com.qualcomm.robotcore.hardware.PwmControl.PwmRange) a[0];
                            pulseUs = new double[] {r.usPulseLower, r.usPulseUpper};
                            return null;
                        }
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        default: return defaultFor(m.getReturnType());
                    }
                });
        }
    }

    /** A pretend continuous-rotation servo. */
    public static final class FakeCRServo {
        public double power = 0;
        public int writes = 0;
        public final CRServo servo = (CRServo) Proxy.newProxyInstance(CRServo.class.getClassLoader(),
                new Class<?>[]{CRServo.class}, (proxy, m, a) -> {
                    switch (m.getName()) {
                        case "setPower": power = (Double) a[0]; writes++; return null;
                        case "getPower": return power;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        default: return defaultFor(m.getReturnType());
                    }
                });
    }

    /** A pretend telemetry that keeps the last screen it was sent. */
    public static final class FakeTelemetry {
        public final List<String> building = new ArrayList<>();
        public List<String> lastScreen = new ArrayList<>();
        public int updates = 0;
        public final Telemetry telemetry = (Telemetry) Proxy.newProxyInstance(Telemetry.class.getClassLoader(),
                new Class<?>[]{Telemetry.class}, (proxy, m, a) -> {
                    switch (m.getName()) {
                        case "addLine":
                            if (a != null && a.length == 1) building.add(String.valueOf(a[0]));
                            return null;
                        case "addData":
                            if (a.length == 2) building.add(a[0] + " : " + a[1]);
                            else building.add(a[0] + " : " + String.format(String.valueOf(a[1]), (Object[]) a[2]));
                            return null;
                        case "update":
                            lastScreen = new ArrayList<>(building);
                            building.clear();
                            updates++;
                            return true;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        default: return defaultFor(m.getReturnType());
                    }
                });
    }

    static Object defaultFor(Class<?> r) {
        if (r == boolean.class) return false;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == double.class) return 0.0;
        if (r == float.class) return 0f;
        return null;
    }
}
