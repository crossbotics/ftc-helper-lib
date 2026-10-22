package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.crossbotics.ftchelper.hardware.Hw;

/**
 * Where the modules find their motors, servos and sensors by configuration name. On the robot it is
 * the OpMode's {@code hardwareMap} ({@link #of(HardwareMap)}); tests give pretend devices instead.
 *
 * <p>You rarely need it yourself: {@link RobotDefinition} hands it to every module.
 *
 * Docs: docs/modules/robot.md
 */
public interface Devices {

    /**
     * The device with this name and type. Stops with a clear message (the names that DO exist) if
     * it is not in the configuration.
     *
     * @param type the device type, e.g. {@code DcMotorEx.class}
     * @param name the configuration name (exact, case matters)
     */
    <T> T get(Class<? extends T> type, String name);

    /** The device with this name and type, or null if there is none (for optional parts). */
    <T> T tryGet(Class<? extends T> type, String name);

    /**
     * True if the configuration has a device with this name, of any type. Used for parts marked
     * {@link Module#optional()}: one whose device is not there is left off instead of stopping INIT.
     * Default: asks the hardwareMap; with no hardwareMap (pretend devices in tests) every name is there.
     *
     * @param name the configuration name (exact, case matters)
     */
    default boolean has(String name) {
        HardwareMap hm = hardwareMap();
        return hm == null || hm.tryGet(com.qualcomm.robotcore.hardware.HardwareDevice.class, name) != null;
    }

    /** The OpMode's hardwareMap, for code that needs it whole (a path follower); null in tests. */
    default com.qualcomm.robotcore.hardware.HardwareMap hardwareMap() {
        return null;
    }

    /**
     * The battery's voltage now (volts), from the hubs ({@link Hw#batteryVolts}); NaN with no voltage
     * sensor (tests). Each call asks the hubs and waits (a few milliseconds): not for the loop. The
     * launcher uses {@link #batteryReader()} instead.
     */
    default double batteryVolts() {
        return hardwareMap() == null ? Double.NaN : Hw.batteryVolts(hardwareMap());
    }

    /**
     * The battery's voltage for code that runs every loop (the launcher's battery compensation): on the
     * robot {@link Hw#batteryReader}, which asks the hubs on the library's helper thread and never waits;
     * in tests (no hardwareMap) it asks {@link #batteryVolts()} directly.
     */
    default java.util.function.DoubleSupplier batteryReader() {
        return hardwareMap() == null ? this::batteryVolts : Hw.batteryReader(hardwareMap());
    }

    /**
     * The robot's devices, from the OpMode's hardwareMap.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     */
    static Devices of(final HardwareMap hardwareMap) {
        // A one-off object that answers each question by asking the hardwareMap (through Hw, which
        // writes the helpful "no device called ..." message).
        return new Devices() {
            @Override public <T> T get(Class<? extends T> type, String name) {
                return Hw.get(hardwareMap, type, name);
            }

            @Override public <T> T tryGet(Class<? extends T> type, String name) {
                return Hw.tryGet(hardwareMap, type, name);
            }

            @Override public HardwareMap hardwareMap() {
                return hardwareMap;
            }
        };
    }
}
