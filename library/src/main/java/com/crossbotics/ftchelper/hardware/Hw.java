package com.crossbotics.ftchelper.hardware;

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

    /**
     * The battery's voltage now, from the hubs' voltage sensors (the lowest reading, as the Characterizer
     * does: every hub is on the same battery). NaN if there is no voltage sensor (or none reads).
     *
     * <p>Each call asks every hub once and WAITS for the answers (a few milliseconds): never call it in
     * the loop. For a reading in the loop use {@link #batteryReader(HardwareMap)}, which asks on the
     * library's helper thread.
     *
     * <pre>{@code
     * telemetry.addData("Battery", Hw.batteryVolts(hardwareMap));   // in INIT, once
     * }</pre>
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @return volts (a charged 12 V FTC battery reads about 12.5 to 13.5 V), or NaN
     */
    public static double batteryVolts(HardwareMap hardwareMap) {
        double lowest = Double.NaN;
        // A reading of 1 V or less is a hub that does not measure (not a real battery).
        for (com.qualcomm.robotcore.hardware.VoltageSensor s : hardwareMap.getAll(com.qualcomm.robotcore.hardware.VoltageSensor.class)) {
            double v = s.getVoltage();
            if (v > 1 && (Double.isNaN(lowest) || v < lowest)) lowest = v;
        }
        return lowest;
    }

    /**
     * The battery's voltage for code that runs every loop: a reader that never waits. Each time you ask
     * it ({@code getAsDouble()}) it answers at once with the latest reading, and at most every
     * {@link #BATTERY_READ_SEC} s it asks the hubs again on the library's helper thread
     * ({@link com.crossbotics.ftchelper.util.BackgroundWorker}), so the slow hub question never holds up
     * the loop. NaN before the first answer, with no voltage sensor, or when the last answer is older
     * than {@link #BATTERY_STALE_SEC} s (the helper thread is stuck): then use nothing.
     *
     * <pre>{@code
     * flywheel.batteryCompensation(Hw.batteryReader(hardwareMap), 12.6);   // 12.6 V: from the Flywheel Finder
     * }</pre>
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @return a reader to ask every loop: volts, or NaN (no reading yet, or too old)
     */
    public static java.util.function.DoubleSupplier batteryReader(final HardwareMap hardwareMap) {
        return new BatteryReader(() -> batteryVolts(hardwareMap), System::nanoTime, batteryWorker()::submitLatest);
    }

    /** How often {@link #batteryReader} asks the hubs again, seconds. */
    public static final double BATTERY_READ_SEC = 0.25;
    /** A {@link #batteryReader} answer older than this (seconds) is not used (NaN). */
    public static final double BATTERY_STALE_SEC = 2.0;

    // One helper thread for every battery reader in the app (made the first time one is needed). It is a
    // daemon thread that sleeps while there is nothing to read, so it is never shut down.
    private static com.crossbotics.ftchelper.util.BackgroundWorker batteryWorker;

    private static synchronized com.crossbotics.ftchelper.util.BackgroundWorker batteryWorker() {
        if (batteryWorker == null || !batteryWorker.isRunning()) {
            batteryWorker = new com.crossbotics.ftchelper.util.BackgroundWorker("battery");
        }
        return batteryWorker;
    }

    /**
     * The reader behind {@link #batteryReader}: answers from its last reading and asks for a new one on
     * another thread. Package-private so tests can give it a pretend clock and run the job at once.
     */
    static final class BatteryReader implements java.util.function.DoubleSupplier {
        private final java.util.function.DoubleSupplier source;
        private final java.util.function.LongSupplier clock;
        private final java.util.function.BiConsumer<String, Runnable> submit;
        // Each reader its own job name, so two readers do not replace each other's jobs.
        private final String key = "battery#" + System.identityHashCode(this);
        // The latest answer: {volts, when it was asked for (ns)}. Written by the helper thread, read by
        // the loop: "volatile" makes the new one show up at once on the other thread, and one array
        // keeps the volts and their time together.
        private volatile double[] latest = null;
        // A question is on its way (do not ask again until it is answered), and when the last one was asked.
        private volatile boolean asking = false;
        private long askedAt = Long.MIN_VALUE;

        BatteryReader(java.util.function.DoubleSupplier source, java.util.function.LongSupplier clock,
                      java.util.function.BiConsumer<String, Runnable> submit) {
            this.source = source;
            this.clock = clock;
            this.submit = submit;
        }

        @Override
        public double getAsDouble() {
            final long now = clock.getAsLong();
            if (!asking && (askedAt == Long.MIN_VALUE || now - askedAt >= (long) (BATTERY_READ_SEC * 1e9))) {
                asking = true;
                askedAt = now;
                // On the helper thread: ask the hubs (the slow part), keep the answer with its time.
                submit.accept(key, () -> {
                    try {
                        latest = new double[] {source.getAsDouble(), now};
                    } finally {
                        asking = false;
                    }
                });
            }
            double[] l = latest;
            if (l == null || now - (long) l[1] > (long) (BATTERY_STALE_SEC * 1e9)) return Double.NaN;
            return l[0];
        }
    }
}
