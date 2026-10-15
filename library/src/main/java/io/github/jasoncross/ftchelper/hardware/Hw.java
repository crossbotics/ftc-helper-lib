package io.github.jasoncross.ftchelper.hardware;

import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.HardwareMap;
import java.util.Set;

/**
 * Gets devices from the robot configuration, with clear error messages.
 *
 * <p>The SDK's {@code hardwareMap.get(...)} gives a short error when a name is wrong.
 * {@link #get} tells you which names DO exist, so you can spot the typo fast.
 *
 * <pre>{@code
 * DcMotorEx intake = Hw.get(hardwareMap, DcMotorEx.class, "intake");
 * DistanceSensor dist = Hw.tryGet(hardwareMap, DistanceSensor.class, "dist");  // null if not there
 * }</pre>
 *
 * Docs: docs/modules/hardware.md
 */
public final class Hw {
    private Hw() { }

    /**
     * Gets a device by its configuration name, or stops the OpMode with a helpful message.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @param type        the device type, e.g. {@code DcMotorEx.class}
     * @param name        the name in the Driver Station configuration (exact, case matters)
     * @return the device
     * @throws IllegalArgumentException if there is no device of that type with that name
     */
    public static <T> T get(HardwareMap hardwareMap, Class<? extends T> type, String name) {
        T device = hardwareMap.tryGet(type, name);
        if (device != null) return device;
        throw new IllegalArgumentException(missingMessage(hardwareMap, type, name));
    }

    /**
     * Gets a device if it is in the configuration, else returns null. Use it for parts that are
     * not on every robot (for example a test sensor). Always check for null before you use it.
     */
    public static <T> T tryGet(HardwareMap hardwareMap, Class<? extends T> type, String name) {
        return hardwareMap.tryGet(type, name);
    }

    /** The error text for a missing device. Public so tests and tools can show it too. */
    public static String missingMessage(HardwareMap hardwareMap, Class<?> type, String name) {
        StringBuilder message = new StringBuilder()
                .append("No ").append(type.getSimpleName())
                .append(" named \"").append(name).append("\" in the active robot configuration. ");
        Set<String> names = null;
        try {
            if (HardwareDevice.class.isAssignableFrom(type)) {
                @SuppressWarnings("unchecked")
                Class<? extends HardwareDevice> deviceType = (Class<? extends HardwareDevice>) type;
                names = hardwareMap.getAllNames(deviceType);
            }
        } catch (RuntimeException ignored) {
            // Some device types cannot be listed; the message still helps without the list.
        }
        if (names != null && !names.isEmpty()) {
            message.append("Names of this type that do exist: ").append(names).append(". ");
        }
        message.append("Check the spelling and the active configuration on the Driver Station.");
        return message.toString();
    }
}
