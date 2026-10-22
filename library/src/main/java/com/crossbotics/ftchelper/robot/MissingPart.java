package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.AnalogInput;
import com.qualcomm.robotcore.hardware.ServoControllerEx;
import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * What the library uses for a part marked {@link Module#optional()} whose device is not in the robot
 * configuration (a part not built yet). Not for team code.
 *
 * <p>The part is still set up, but on stand-in devices that do nothing: a motor that never turns, a
 * servo that goes nowhere. So every command and every question still works (nothing crashes), and
 * the part's own code needs no "is it there?" checks. The robot then leaves the part out of every
 * loop: it is never updated, and its telemetry line is "-".
 *
 * Docs: docs/modules/robot.md (section "Parts not on the robot yet")
 */
final class MissingPart {
    private MissingPart() { }

    /** What a missing part's telemetry line says. */
    static final String TELEMETRY = "- (not on the robot)";

    /**
     * Stand-in devices: {@code get} gives a device that does nothing, {@code tryGet} gives null (a
     * switch or camera that is "not there", which every part already copes with).
     */
    static Devices devices() {
        return new Devices() {
            @Override public <T> T get(Class<? extends T> type, String name) {
                return standIn(type, name);
            }

            @Override public <T> T tryGet(Class<? extends T> type, String name) {
                return null;
            }

            @Override public boolean has(String name) {
                return false;
            }
        };
    }

    /**
     * A device of this type that does nothing. An interface (motor, servo, CR servo, touch sensor...)
     * gets a stand-in that remembers what it was set to ({@code setPower(0.5)} then {@code getPower()}
     * gives 0.5) and answers 0, false or "" to everything else. Two SDK classes get their own; any
     * other class gives null (no part uses one).
     */
    @SuppressWarnings("unchecked")
    static <T> T standIn(Class<? extends T> type, final String name) {
        if (type.isInterface()) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, new Remembers(name));
        }
        if (type == AnalogInput.class) {
            // AnalogInput is a class: one that always reads 0 volts (its controller is never asked).
            return (T) new AnalogInput(null, 0) {
                @Override public double getVoltage() { return 0; }
                @Override public double getMaxVoltage() { return 3.3; }
                @Override public String getDeviceName() { return name + " (not in the configuration)"; }
            };
        }
        if (type == com.qualcomm.hardware.rev.RevBlinkinLedDriver.class) {
            // A Blinkin on a stand-in servo controller: the patterns it is sent go nowhere.
            ServoControllerEx c = (ServoControllerEx) Proxy.newProxyInstance(ServoControllerEx.class.getClassLoader(),
                    new Class<?>[] {ServoControllerEx.class}, new Remembers(name));
            return (T) new com.qualcomm.hardware.rev.RevBlinkinLedDriver(c, 0);
        }
        return null;
    }

    /** The stand-in's answers: "set" remembers, "get"/"is" gives back what was set (or 0, false, ""). */
    private static final class Remembers implements InvocationHandler {
        private final String name;
        private final Map<String, Object> values = new HashMap<>();

        Remembers(String name) {
            this.name = name;
        }

        @Override
        public synchronized Object invoke(Object proxy, Method m, Object[] args) {
            String n = m.getName();
            int argc = args == null ? 0 : args.length;
            // Java asks every object these three: answer like a plain object.
            if (n.equals("equals") && argc == 1) return proxy == args[0];
            if (n.equals("hashCode") && argc == 0) return System.identityHashCode(proxy);
            if (n.equals("toString") && argc == 0) return name + " (not in the configuration)";
            if (n.equals("getDeviceName")) return name + " (not in the configuration)";
            if (n.equals("getConnectionInfo")) return "not in the configuration";
            if (n.startsWith("set") && argc >= 1) {
                values.put(n.substring(3), args[0]);
                return blank(m.getReturnType());
            }
            String key = n.startsWith("get") ? n.substring(3) : n.startsWith("is") ? n.substring(2) : null;
            if (key != null && argc == 0) {
                Object v = values.get(key);
                if (v != null && wrap(m.getReturnType()).isInstance(v)) return v;
            }
            return blank(m.getReturnType());
        }
    }

    /** 0, false, "", the first value of an enum, or null: what a device that is not there answers. */
    static Object blank(Class<?> t) {
        if (t == void.class) return null;
        if (t == boolean.class) return false;
        if (t == double.class) return 0.0;
        if (t == float.class) return 0f;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == char.class) return (char) 0;
        if (t == String.class) return "";
        if (t.isEnum()) {
            Object[] all = t.getEnumConstants();
            return all.length == 0 ? null : all[0];
        }
        return null;
    }

    private static Class<?> wrap(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == boolean.class) return Boolean.class;
        if (t == double.class) return Double.class;
        if (t == float.class) return Float.class;
        if (t == int.class) return Integer.class;
        if (t == long.class) return Long.class;
        if (t == short.class) return Short.class;
        if (t == byte.class) return Byte.class;
        return Character.class;
    }

    /**
     * The missing part in the robot's list of parts: never updated (so no safety check watches a motor
     * that is not there), stopping does nothing, and its telemetry line is "-".
     */
    static final class Absent implements Subsystem {
        final Module<?> part;

        Absent(Module<?> part) {
            this.part = part;
        }

        @Override public void update() { }

        @Override public void addTelemetry(RobotTelemetry t) {
            t.line(part.name(), TELEMETRY);
        }

        @Override public String name() {
            return part.name();
        }
    }
}
