package com.crossbotics.ftchelper.robot;

/**
 * Two words you can write in {@code Tunables.java} in place of a number you do not know yet, so every
 * setting is in the file from the first day and the file says which ones are still to do.
 *
 * <pre>{@code
 * import static com.crossbotics.ftchelper.robot.Starting.*;   // at the top of Tunables.java
 *
 * public static class Launcher {
 *     public static double kP = DEFAULT;          // from the motor in Robot.java (parts catalog)
 *     public static double speedRpm = DEFAULT;    // 60% of the motor's top speed
 *     public static double stepRpm = DEFAULT;     // the library's plain default (100)
 * }
 * public static class Lift {
 *     public static double min = MEASURE, max = MEASURE;        // INIT stops until you write them
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@link #DEFAULT} (or its older name DEFAULT_FROM_CATALOG): "use the library's starting value". The same as leaving the
 *       line out: the value worked out from the parts catalog where there is one (a launcher's gains, a
 *       lift's kP with {@code .part(...)}, a servo's travelSec), else the library's default. INIT lists
 *       each one with the value it used. Never for a safety number (a lift's min and max, a hood's stops...):
 *       INIT stops, because a safety limit never comes from the catalog.</li>
 *   <li>{@link #MEASURE}: "a number you must find on the robot". INIT stops and names the tool that
 *       finds it (Motor Finder, Servo Finder...). Write the number in its place.</li>
 * </ul>
 *
 * <p>Only for {@code double} fields (the way Tunables.java writes numbers): an {@code int} cannot hold
 * them (Java says so when it builds), and an on/off setting ({@code boolean}) or a table ({@code double[][]})
 * has no such word: leave the line out instead. The tuning menu shows the real value
 * in use, and its "copy into Tunables.java" lines are real numbers to write in place of the word.
 *
 * <p>How they work: each is a "not a number" value (NaN) with its own bit pattern, which no calculation
 * makes. The library reads the bits, so no real number can be taken for one of them.
 *
 * Docs: docs/guides/tunables.md (section "DEFAULT and MEASURE")
 */
public final class Starting {
    private Starting() { }

    // Quiet NaNs with a payload of our own ("FC", "ME"). Arithmetic never makes these exact bits.
    private static final long DEFAULT_FROM_CATALOG_BITS = 0x7ff8000000465743L;
    private static final long MEASURE_BITS = 0x7ff80000004D4545L;

    /**
     * Use the library's starting value: worked out from the part Robot.java names (the parts catalog)
     * where the setting depends on the part, else the library's plain default. The same as leaving the
     * line out, but you can see it.
     */
    public static final double DEFAULT = Double.longBitsToDouble(DEFAULT_FROM_CATALOG_BITS);

    /** The older, longer name for {@link #DEFAULT}: exactly the same. */
    public static final double DEFAULT_FROM_CATALOG = Double.longBitsToDouble(DEFAULT_FROM_CATALOG_BITS);

    /** A number you must find on the robot: INIT stops and names the tool that finds it. */
    public static final double MEASURE = Double.longBitsToDouble(MEASURE_BITS);

    /**
     * True if the value is {@link #DEFAULT} (the same as DEFAULT_FROM_CATALOG; not any other NaN).
     *
     * @param v a number read from Tunables.java
     */
    public static boolean isDefaultFromCatalog(double v) {
        return Double.doubleToRawLongBits(v) == DEFAULT_FROM_CATALOG_BITS;
    }

    /**
     * True if the value is {@link #MEASURE} (not any other NaN).
     *
     * @param v a number read from Tunables.java
     */
    public static boolean isMeasure(double v) {
        return Double.doubleToRawLongBits(v) == MEASURE_BITS;
    }

}
