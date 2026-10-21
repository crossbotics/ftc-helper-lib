package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.Fakes;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Pretend devices for the robot-module tests: every name asked for is made on the spot. */
final class RobotTestKit implements Devices {
    final Map<String, FakeMotor> motors = new HashMap<>();
    final Map<String, Fakes.FakeServo> servos = new HashMap<>();
    final Map<String, Fakes.FakeCRServo> crServos = new HashMap<>();
    final Map<String, boolean[]> switches = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<? extends T> type, String name) {
        if (type == DcMotorEx.class) {
            if (!motors.containsKey(name)) motors.put(name, new FakeMotor());
            return (T) motors.get(name).motor;
        }
        if (type == Servo.class) {
            if (!servos.containsKey(name)) servos.put(name, new Fakes.FakeServo());
            return (T) servos.get(name).servo;
        }
        if (type == CRServo.class) {
            if (!crServos.containsKey(name)) crServos.put(name, new Fakes.FakeCRServo());
            return (T) crServos.get(name).servo;
        }
        if (type == TouchSensor.class) {
            final boolean[] pressed = new boolean[1];
            switches.put(name, pressed);
            return (T) Proxy.newProxyInstance(TouchSensor.class.getClassLoader(), new Class<?>[]{TouchSensor.class},
                    (proxy, m, a) -> m.getName().equals("isPressed") ? (Object) pressed[0]
                            : m.getReturnType() == boolean.class ? (Object) false : null);
        }
        throw new IllegalArgumentException("no pretend " + type.getSimpleName() + " " + name);
    }

    @Override
    public <T> T tryGet(Class<? extends T> type, String name) {
        return get(type, name);
    }
}
