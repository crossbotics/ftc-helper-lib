package io.github.jasoncross.ftchelper.robot;

import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.hardware.Hw;

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
        };
    }
}
