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
        /** Top speed with no load, RPM. */
        public final double freeRpm;

        Kind(String id, double countsPerRev, double freeRpm) {
            this.id = id;
            this.countsPerRev = countsPerRev;
            this.freeRpm = freeRpm;
        }
    }

    private static final Map<String, Kind> ALL = new LinkedHashMap<>();

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
        Kind k = ALL.get(id);
        if (k == null) {
            throw new IllegalArgumentException("Unknown motor \"" + id + "\". Known: " + ALL.keySet()
                    + " (docs/guides/parts-catalog.md)");
        }
        return k;
    }

    /** Every known motor, in catalog order (read only). */
    public static Map<String, Kind> all() {
        return java.util.Collections.unmodifiableMap(ALL);
    }
}
