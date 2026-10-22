package com.crossbotics.ftchelper.robot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encoder counts per turn and top speed of the common FTC motors, by the same ids as the simulator's
 * parts catalog ({@code sim/parts/parts.json}, docs/guides/parts-catalog.md): "gobilda-5203-312",
 * "rev-hd-hex-20"... Modules use it to turn a motor id into encoder counts per degree or per wheel turn.
 *
 * <p>The numbers are copied from the parts catalog, which has each seller's page and the date it was
 * checked; a test checks that they still match it.
 *
 * <p><b>A motor that is not in the catalog</b> (another seller, a custom gearbox): describe it in Robot.java
 * with {@link #custom} and use the id it gives wherever a motor id goes. Its numbers then work like a
 * catalog motor's (starting values, the simulator's robot file). Numbers you do not know: {@link #unknown},
 * or {@code Double.NaN} for one of them: the parts that need them use general starting values and say so.
 * <pre>{@code
 * launcher = add(Launcher.flywheel("launcher", Motors.custom("my-flywheel-motor", 28, 5800)));   // counts per turn, RPM
 * lift = add(Lift.slide("lift").part(Motors.unknown("old-lift-motor")));
 * }</pre>
 *
 * Docs: docs/modules/robot.md
 */
public final class Motors {
    private Motors() { }

    /** One motor: encoder counts per output-shaft turn, and its free (no-load) speed in RPM. */
    public static final class Kind {
        /** The parts catalog id, e.g. "gobilda-5203-312". */
        public final String id;
        /** Encoder counts per turn of the output shaft (what the FTC SDK reports). */
        public final double countsPerRev;
        /** Top speed with no load, RPM. NaN for a {@link #custom} motor whose speed is not known. */
        public final double freeRpm;
        /** True for a motor described in Robot.java ({@link #custom}, {@link #unknown}), not from the catalog. */
        public final boolean custom;

        Kind(String id, double countsPerRev, double freeRpm) {
            this(id, countsPerRev, freeRpm, false);
        }

        Kind(String id, double countsPerRev, double freeRpm, boolean custom) {
            this.id = id;
            this.countsPerRev = countsPerRev;
            this.freeRpm = freeRpm;
            this.custom = custom;
        }

        /** True if both numbers are known (a catalog motor always). */
        public boolean known() {
            return !Double.isNaN(countsPerRev) && !Double.isNaN(freeRpm);
        }

        /**
         * The words for the simulator's robot file: {@code part=ID} for a catalog motor, its own numbers
         * ({@code ticksPerRev=28, rpm=5800}) for a custom one ("" when none is known).
         */
        public String simWords() {
            if (!custom) return "part=" + id;
            StringBuilder sb = new StringBuilder();
            if (!Double.isNaN(countsPerRev)) sb.append("ticksPerRev=").append(Module.exact(countsPerRev));
            if (!Double.isNaN(freeRpm)) sb.append(sb.length() == 0 ? "" : ", ").append("rpm=").append(Module.exact(freeRpm));
            return sb.toString();
        }
    }

    private static final Map<String, Kind> ALL = new LinkedHashMap<>();
    // Motors described in Robot.java (custom, unknown), by id. Kept for the whole app run: the same
    // define() describes them again at every INIT.
    private static final Map<String, Kind> CUSTOM = new LinkedHashMap<>();

    private static void add(String id, double counts, double rpm) {
        ALL.put(id, new Kind(id, counts, rpm));
    }

    // Copied from sim/parts/parts.json (countsPerRev, freeRpm). MotorsCatalogTest checks they match.
    static {
        add("gobilda-5203-6000", 28.0, 6000);
        add("gobilda-5203-1620", 103.8, 1620);
        add("gobilda-5203-1150", 145.1, 1150);
        add("gobilda-5203-435", 384.5, 435);
        add("gobilda-5203-312", 537.7, 312);
        add("gobilda-5203-223", 751.8, 223);
        add("gobilda-5203-117", 1425.1, 117);
        add("gobilda-5203-84", 1993.6, 84);
        add("gobilda-5203-60", 2786.2, 60);
        add("gobilda-5203-43", 3895.9, 43);
        add("gobilda-5203-30", 5281.1, 30);
        add("gobilda-5204-1620", 103.8, 1620);
        add("gobilda-5204-1150", 145.1, 1150);
        add("gobilda-5204-435", 384.5, 435);
        add("gobilda-5204-312", 537.7, 312);
        add("gobilda-5204-223", 751.8, 223);
        add("gobilda-5204-117", 1425.1, 117);
        add("gobilda-5204-84", 1993.6, 84);
        add("gobilda-5204-60", 2786.2, 60);
        add("gobilda-5204-43", 3895.9, 43);
        add("gobilda-5204-30", 5281.1, 30);
        add("rev-hd-hex", 28, 6000);
        add("rev-hd-hex-up3", 81.1, 2071.4);
        add("rev-hd-hex-up4", 101.33, 1657.9);
        add("rev-hd-hex-up5", 146.46, 1147);
        add("rev-hd-hex-up12", 293.52, 572.4);
        add("rev-hd-hex-up15", 424.23, 396.0);
        add("rev-hd-hex-up20", 530.05, 317);
        add("rev-hd-hex-up60", 1535.32, 109.5);
        add("rev-hd-hex-40", 1120, 150);
        add("rev-hd-hex-20", 560, 300);
        add("rev-core-hex", 288, 125);
        add("am-neverest-orbital-1", 28, 6600);
        add("am-neverest-orbital-3-7", 103.6, 1784);
        add("am-neverest-orbital-20", 537.6, 344);
        add("am-neverest-orbital-50", 1425.2, 130);
        add("am-neverest-orbital-264", 7383.6, 25);
        add("am-neverest-classic-40", 1120, 160);
        add("am-neverest-classic-60", 1680, 105);
    }

    /**
     * The motor with this id.
     *
     * @param id e.g. "gobilda-5203-312"
     * @throws IllegalArgumentException for an unknown id, with the list of known ones
     */
    public static Kind get(String id) {
        Kind k = find(id);
        if (k == null) {
            throw new IllegalArgumentException("Unknown motor \"" + id + "\". Known: " + ALL.keySet()
                    + " (docs/guides/parts-catalog.md). A motor that is not in the catalog: Motors.custom(\"" + id
                    + "\", countsPerRev, freeRpm) in Robot.java (docs/modules/robot.md#parts-not-in-the-catalog)");
        }
        return k;
    }

    /**
     * The motor with this id, from the catalog or described with {@link #custom}; null if there is none.
     *
     * @param id e.g. "gobilda-5203-312"
     */
    public static Kind find(String id) {
        if (id == null) return null;
        Kind k = ALL.get(id);
        return k != null ? k : CUSTOM.get(id);
    }

    /**
     * Describes a motor that is not in the parts catalog, so its id works wherever a motor id goes
     * ({@code Launcher.flywheel}, {@code Arm.motor}, {@code Turret.motor}, {@code Hood.motor},
     * {@code Drive...motor}, {@code .part(...)}). Write it in Robot.java, in the line that uses it.
     *
     * <pre>{@code
     * launcher = add(Launcher.flywheel("launcher", Motors.custom("my-flywheel-motor", 28, 5800)));
     * }</pre>
     *
     * @param id           a name of your own, not a catalog id (e.g. "my-flywheel-motor")
     * @param countsPerRev encoder counts per output-shaft turn as the FTC SDK reports them (the seller's
     *                     page; a bare motor is usually 28); Double.NaN if not known
     * @param freeRpm      top speed with no load at 12 V, RPM; Double.NaN if not known
     * @return the id, to use in the part's line
     * @throws IllegalArgumentException for a catalog id, or a number that is 0 or less
     */
    public static String custom(String id, double countsPerRev, double freeRpm) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Robot.java: Motors.custom needs a name for the motor");
        if (ALL.containsKey(id)) {
            throw new IllegalArgumentException("Robot.java: Motors.custom(\"" + id + "\", ...): that motor is in the parts catalog: "
                    + "use its id alone, or give yours another name");
        }
        if (!(countsPerRev > 0) && !Double.isNaN(countsPerRev) || !(freeRpm > 0) && !Double.isNaN(freeRpm)) {
            throw new IllegalArgumentException("Robot.java: Motors.custom(\"" + id + "\", " + countsPerRev + ", " + freeRpm
                    + "): counts per turn and RPM must be more than 0 (Double.NaN for one you do not know)");
        }
        CUSTOM.put(id, new Kind(id, countsPerRev, freeRpm, true));
        return id;
    }

    /**
     * A motor whose numbers you do not know yet (the same as {@link #custom} with both NaN). A part that
     * needs its counts per turn (a launcher, an arm, a motor turret) stops at INIT and says so; a lift's
     * {@code .part(...)} uses general starting values and says so.
     *
     * @param id a name of your own
     * @return the id
     */
    public static String unknown(String id) {
        return custom(id, Double.NaN, Double.NaN);
    }

    /**
     * The motor with this id, which must know its counts per turn (a launcher, an arm, a turret turn
     * counts into RPM or degrees).
     *
     * @param id   the motor's id
     * @param user the part, for the message ("Launcher.flywheel(\"launcher\", ...)")
     */
    static Kind withCounts(String id, String user) {
        Kind k = get(id);
        if (Double.isNaN(k.countsPerRev)) {
            throw new IllegalArgumentException("Robot.java: " + user + ": the motor \"" + id + "\" has no counts per turn, and "
                    + "this part needs them: Motors.custom(\"" + id + "\", countsPerRev, freeRpm) (the seller's page; the "
                    + "Motor Finder shows the counts while you turn the shaft once by hand)");
        }
        return k;
    }

    /** Every catalog motor, in catalog order (read only; not the {@link #custom} ones). */
    public static Map<String, Kind> all() {
        return java.util.Collections.unmodifiableMap(ALL);
    }
}
