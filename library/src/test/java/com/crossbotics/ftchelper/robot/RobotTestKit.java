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
    // Magnetic switches (digital channels): true = closed (the channel then reads LOW, like the REV switch).
    final Map<String, boolean[]> digital = new HashMap<>();
    // Analog inputs (absolute encoders): the voltage now, and the top voltage.
    final Map<String, double[]> analog = new HashMap<>();
    // Names that are NOT in the configuration (tryGet gives null), e.g. a missing switch.
    final java.util.Set<String> missing = new java.util.HashSet<>();
    // Names configured as a digital device (so tryGet(TouchSensor) gives null for them).
    final java.util.Set<String> digitalNames = new java.util.HashSet<>();
    // Blinkin LED drivers: the last servo position each was sent (NaN = none yet).
    final Map<String, double[]> blinkins = new HashMap<>();
    // The battery the hubs read (volts); NaN = no voltage sensor, like the default.
    double volts = Double.NaN;
    int voltReads = 0;

    @Override
    public double batteryVolts() {
        voltReads++;
        return volts;
    }

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
        if (type == com.qualcomm.robotcore.hardware.DigitalChannel.class) {
            final boolean[] closed = digital.containsKey(name) ? digital.get(name) : new boolean[1];
            digital.put(name, closed);
            return (T) Proxy.newProxyInstance(TouchSensor.class.getClassLoader(), new Class<?>[]{com.qualcomm.robotcore.hardware.DigitalChannel.class},
                    (proxy, m, a) -> m.getName().equals("getState") ? (Object) !closed[0]
                            : m.getReturnType() == boolean.class ? (Object) false : null);
        }
        if (type == com.qualcomm.robotcore.hardware.AnalogInput.class) {
            final double[] v = analog.containsKey(name) ? analog.get(name) : new double[] {0, 3.3};
            analog.put(name, v);
            // AnalogInput is a class: a subclass with a pretend controller that answers our numbers.
            return (T) new com.qualcomm.robotcore.hardware.AnalogInput(null, 0) {
                @Override public double getVoltage() { return v[0]; }
                @Override public double getMaxVoltage() { return v[1]; }
            };
        }
        if (type == TouchSensor.class) {
            final boolean[] pressed = switches.containsKey(name) ? switches.get(name) : new boolean[1];
            switches.put(name, pressed);
            return (T) Proxy.newProxyInstance(TouchSensor.class.getClassLoader(), new Class<?>[]{TouchSensor.class},
                    (proxy, m, a) -> m.getName().equals("isPressed") ? (Object) pressed[0]
                            : m.getReturnType() == boolean.class ? (Object) false : null);
        }
        if (type == com.qualcomm.hardware.rev.RevBlinkinLedDriver.class) {
            // The SDK's own class, on a pretend servo controller that remembers the position sent.
            final double[] pos = {Double.NaN};
            blinkins.put(name, pos);
            com.qualcomm.robotcore.hardware.ServoControllerEx c = (com.qualcomm.robotcore.hardware.ServoControllerEx)
                    Proxy.newProxyInstance(TouchSensor.class.getClassLoader(),
                            new Class<?>[]{com.qualcomm.robotcore.hardware.ServoControllerEx.class}, (proxy, m, a) -> {
                                if (m.getName().equals("setServoPosition")) pos[0] = (Double) a[1];
                                return m.getReturnType() == boolean.class ? (Object) false
                                        : m.getReturnType() == double.class ? (Object) 0.0
                                        : m.getReturnType() == int.class ? (Object) 0 : null;
                            });
            return (T) new com.qualcomm.hardware.rev.RevBlinkinLedDriver(c, 0);
        }
        throw new IllegalArgumentException("no pretend " + type.getSimpleName() + " " + name);
    }

    @Override
    public <T> T tryGet(Class<? extends T> type, String name) {
        if (missing.contains(name)) return null;
        if (type == TouchSensor.class && digitalNames.contains(name)) return null;
        return get(type, name);
    }
}
