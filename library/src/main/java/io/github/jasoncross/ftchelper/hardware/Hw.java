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
 * <p>The "robot configuration" is the list of names you typed on the Driver Station for each
 * motor, servo and sensor port. The code must use exactly the same names.
 *
 * <pre>{@code
 * DcMotorEx intake = Hw.get(hardwareMap, DcMotorEx.class, "intake");
 * DistanceSensor dist = Hw.tryGet(hardwareMap, DistanceSensor.class, "dist");  // null if not there
 * }</pre>
 *
 * Docs: docs/modules/hardware.md
 */
public final class Hw {
    // "final": no other class can extend this one. It is only a box of helper methods.
    // Private constructor: nobody makes an Hw object. Call the "static" methods on the class
    // name instead, like Hw.get(...).
    private Hw() { }

    /**
     * Gets a device by its configuration name, or stops the OpMode with a helpful message.
     *
     * <p>Stopping right away in INIT is on purpose: a robot with a missing motor should not
     * start a match and fail in a strange way later.
     *
     * <p>{@code <T>} is a "type placeholder" (Java calls it a generic): it stands for whatever
     * device type you ask for, so asking for {@code DcMotorEx.class} gives you back a
     * {@code DcMotorEx} with no extra conversion.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @param type        the device type, e.g. {@code DcMotorEx.class}
     * @param name        the name in the Driver Station configuration (exact, case matters)
     * @return the device
     * @throws IllegalArgumentException if there is no device of that type with that name
     */
    public static <T> T get(HardwareMap hardwareMap, Class<? extends T> type, String name) {
        // tryGet returns null (meaning "nothing") instead of crashing when the name is not found,
        // so we get the chance to build a better error message ourselves.
        T device = hardwareMap.tryGet(type, name);
        if (device != null) return device;
        // "throw" stops the program here and shows the message on the Driver Station.
        throw new IllegalArgumentException(missingMessage(hardwareMap, type, name));
    }

    /**
     * Gets a device if it is in the configuration, else returns null. Use it for parts that are
     * not on every robot (for example a test sensor). Always check for null before you use it.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @param type        the device type, e.g. {@code DistanceSensor.class}
     * @param name        the name in the Driver Station configuration (exact, case matters)
     * @return the device, or null ("nothing") if there is none with that type and name
     */
    public static <T> T tryGet(HardwareMap hardwareMap, Class<? extends T> type, String name) {
        return hardwareMap.tryGet(type, name);
    }

    /**
     * The error text for a missing device. Public so tests and tools can show it too.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}, used to list the names that DO exist
     * @param type        the device type that was asked for
     * @param name        the name that was not found
     * @return a message that names the missing device and lists the real names of that type
     */
    public static String missingMessage(HardwareMap hardwareMap, Class<?> type, String name) {
        // A StringBuilder glues many pieces of text together, one append(...) at a time.
        StringBuilder message = new StringBuilder()
                .append("No ").append(type.getSimpleName())
                .append(" named \"").append(name).append("\" in the active robot configuration. ");
        // The list of real names starts as null ("not found yet"); we fill it in if we can.
        Set<String> names = null;
        // "try { ... } catch" means: try this, and if it fails, run the catch part instead of
        // crashing. We are already building an error message, so a second crash would hide it.
        try {
            // The SDK can only list names for real hardware device types, so check that first.
            if (HardwareDevice.class.isAssignableFrom(type)) {
                // We just checked the type, so this conversion is safe; the line below tells
                // the compiler not to warn about it.
                @SuppressWarnings("unchecked")
                Class<? extends HardwareDevice> deviceType = (Class<? extends HardwareDevice>) type;
                names = hardwareMap.getAllNames(deviceType);
            }
        } catch (RuntimeException ignored) {
            // Some device types cannot be listed; the message still helps without the list.
        }
        // Only add the list when there is something in it.
        if (names != null && !names.isEmpty()) {
            message.append("Names of this type that do exist: ").append(names).append(". ");
        }
        message.append("Check the spelling and the active configuration on the Driver Station.");
        return message.toString();
    }
}
