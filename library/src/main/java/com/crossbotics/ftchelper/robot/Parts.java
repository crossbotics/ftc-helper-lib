package com.crossbotics.ftchelper.robot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The wheels and rollers (and the servos' turn and pulse range) of the simulator's parts catalog ({@code sim/parts/parts.json},
 * docs/guides/parts-catalog.md), by the same ids: "gobilda-hogback-96", "offset-tubing-90"... A
 * {@link Shape} uses it to draw a part at its real size: Robot.java names the part, and the size
 * comes from here. Motors are in {@link Motors}.
 *
 * <p>The numbers are copied from the parts catalog, which has each seller's page and the date it was
 * checked; a test checks that they still match it.
 *
 * Docs: docs/modules/robot.md (section "Shapes")
 */
public final class Parts {
    private Parts() { }

    /** One wheel or roller: its size in millimetres. */
    public static final class Wheel {
        /** The parts catalog id, e.g. "gobilda-hogback-96". */
        public final String id;
        /** Outside diameter, mm. */
        public final double diameterMm;
        /** Width along its axle, mm; NaN when the seller does not give it (then the shape needs its length). */
        public final double widthMm;

        Wheel(String id, double diameterMm, double widthMm) {
            this.id = id;
            this.diameterMm = diameterMm;
            this.widthMm = widthMm;
        }

        /** True if the catalog knows its width. */
        public boolean hasWidth() {
            return !Double.isNaN(widthMm);
        }
    }

    /**
     * One servo: how far it turns and over which pulse. The FTC SDK sends 600 to 2400 microseconds for
     * positions 0 to 1 unless the code asks for its full range ({@code .fullRange()}), so a servo that
     * turns 300 degrees over 500-2500 us turns 270 over 0 to 1 on a normal hub ({@link #sdkRangeDeg()}).
     */
    public static final class ServoPart {
        /** The parts catalog id, e.g. "gobilda-2000-speed". */
        public final String id;
        /** The seller's full turn, degrees, over {@link #pulseMinUs} to {@link #pulseMaxUs}; NaN = not given. */
        public final double rangeDeg;
        /** The seller's pulse range, microseconds; NaN = not given (then 500 to 2500 is assumed). */
        public final double pulseMinUs, pulseMaxUs;
        /**
         * Seconds to turn 60 degrees with no load at about the hub's servo voltage (a REV hub gives its
         * servos 5 V): the seller's 4.8 V number, or its 6 V one when it gives no 4.8 V number (then the
         * real servo is a little slower).
         */
        public final double secPer60Deg;

        ServoPart(String id, double rangeDeg, double pulseMinUs, double pulseMaxUs, double secPer60Deg) {
            this.id = id;
            this.rangeDeg = rangeDeg;
            this.pulseMinUs = pulseMinUs;
            this.pulseMaxUs = pulseMaxUs;
            this.secPer60Deg = secPer60Deg;
        }

        /**
         * About how long a move from position 0 to 1 takes on a hub, from the seller's speed: a starting
         * value for a part's {@code travelSec} (goBILDA 2000 torque on the normal range: 270 degrees at 0.25 s
         * per 60 = 1.13 s).
         *
         * @param fullRange true with {@code .fullRange()} (0 to 1 is the servo's whole turn)
         * @return seconds, or NaN when the catalog lacks the servo's turn or speed
         */
        public double fullMoveSec(boolean fullRange) {
            double deg = fullRange ? rangeDeg : sdkRangeDeg();
            return deg / 60.0 * secPer60Deg;
        }

        /** The pulse for position 0 with full range: the seller's, or 500 us when it gives none. */
        public double fullLowUs() {
            return Double.isNaN(pulseMinUs) ? com.crossbotics.ftchelper.hardware.ServoSetup.FULL_PULSE_LOW_US : pulseMinUs;
        }

        /** The pulse for position 1 with full range: the seller's, or 2500 us when it gives none. */
        public double fullHighUs() {
            return Double.isNaN(pulseMaxUs) ? com.crossbotics.ftchelper.hardware.ServoSetup.FULL_PULSE_HIGH_US : pulseMaxUs;
        }

        /** Degrees per microsecond of pulse (goBILDA 2000: 0.15); NaN when the range is not given. */
        public double degreesPerMicrosecond() {
            return rangeDeg / (fullHighUs() - fullLowUs());
        }

        /**
         * How far positions 0 to 1 turn it with this pulse range.
         *
         * @param lowUs  microseconds at position 0
         * @param highUs microseconds at position 1
         * @return degrees (NaN when the range is not given)
         */
        public double degreesOver(double lowUs, double highUs) {
            return degreesPerMicrosecond() * (highUs - lowUs);
        }

        /** Degrees over 0 to 1 with the SDK's normal 600-2400 us (goBILDA 2000: 270). */
        public double sdkRangeDeg() {
            return degreesOver(com.crossbotics.ftchelper.hardware.ServoSetup.SDK_PULSE_LOW_US,
                    com.crossbotics.ftchelper.hardware.ServoSetup.SDK_PULSE_HIGH_US);
        }
    }

    private static final Map<String, Wheel> WHEELS = new LinkedHashMap<>();
    private static final Map<String, ServoPart> SERVOS = new LinkedHashMap<>();

    private static void servo(String id, double rangeDeg, double pulseMinUs, double pulseMaxUs, double secPer60Deg) {
        SERVOS.put(id, new ServoPart(id, rangeDeg, pulseMinUs, pulseMaxUs, secPer60Deg));
    }

    private static void wheel(String id, double diameterMm, double widthMm) {
        WHEELS.put(id, new Wheel(id, diameterMm, widthMm));
    }

    // Copied from sim/parts/parts.json ("wheels": diameterMm, widthMm; "servos": id, rangeDeg, pulseMinUs, pulseMaxUs). PartsCatalogTest checks they match.
    static {
        wheel("gobilda-boot-72-rex", 72, Double.NaN);
        wheel("gobilda-gecko-48-rex", 48, Double.NaN);
        wheel("gobilda-gecko-72", 72, 24);
        wheel("gobilda-hogback-96", 96, 24);
        wheel("offset-tubing-60", 60, Double.NaN);
        wheel("offset-tubing-90", 90, Double.NaN);
        // "servos": id, rangeDeg, pulseMinUs, pulseMaxUs (NaN = null in the catalog), and seconds per 60 degrees:
        // the byVoltage row at 4.8 V, else the 6.0 V secPer60Deg (a REV hub's servo ports give 5 V).
        servo("gobilda-2000-torque", 300, 500, 2500, 0.25);
        servo("gobilda-2000-speed", 300, 500, 2500, 0.11);
        servo("gobilda-2000-super-speed", 300, 500, 2500, 0.055);
        servo("gobilda-2000-5turn-torque", 1800, 500, 2500, 0.25);
        servo("gobilda-2000-5turn-speed", 1800, 500, 2500, 0.11);
        servo("gobilda-2000-5turn-super-speed", 1800, 500, 2500, 0.055);
        servo("rev-smart-servo", 270, 500, 2500, 0.13);
        servo("rev-smart-servo-v2-balanced", 270, 500, 2500, 0.14);
        servo("rev-smart-servo-v2-ultraspeed", 270, 500, 2500, 0.043);
        servo("axon-max-mk2", 360, 500, 2500, 0.14);
        servo("axon-max-plus", Double.NaN, Double.NaN, Double.NaN, 0.14);
        servo("axon-mini-mk2", 360, 500, 2500, 0.11);
        servo("axon-mini-plus", Double.NaN, Double.NaN, Double.NaN, 0.11);
    }

    /**
     * A wheel or roller by its catalog id.
     *
     * @param id e.g. "offset-tubing-90"
     * @throws IllegalArgumentException for an unknown id (the message lists the known ones)
     */
    public static Wheel wheel(String id) {
        Wheel w = WHEELS.get(id);
        if (w == null) {
            throw new IllegalArgumentException("Robot.java: no wheel or roller \"" + id + "\" in the parts catalog. Known: "
                    + WHEELS.keySet() + ". A part that is not there: draw it with its size (Shape.box, or "
                    + "Shape.roller().diameterMm(...), or Shape.flywheel().diameterMm(...) for a launcher wheel), or add it to "
                    + "sim/parts/parts.json and Parts.java (docs/guides/parts-catalog.md)");
        }
        return w;
    }

    /** Every wheel and roller, in catalog order (read only). */
    public static Map<String, Wheel> wheels() {
        return Collections.unmodifiableMap(WHEELS);
    }

    /**
     * Checks a servo's catalog id.
     *
     * @param id e.g. "gobilda-2000-speed"
     * @return the same id
     * @throws IllegalArgumentException for an unknown id (the message lists the known ones)
     */
    public static String servo(String id) {
        servoPart(id);
        return id;
    }

    /**
     * A servo's numbers by its catalog id: its turn and pulse range.
     *
     * <pre>{@code
     * double deg = Parts.servoPart("gobilda-2000-speed").sdkRangeDeg();   // 270
     * }</pre>
     *
     * @param id e.g. "gobilda-2000-speed"
     * @throws IllegalArgumentException for an unknown id (the message lists the known ones)
     */
    public static ServoPart servoPart(String id) {
        ServoPart s = SERVOS.get(id);
        if (s == null) {
            throw new IllegalArgumentException("Robot.java: no servo \"" + id + "\" in the parts catalog. Known: " + SERVOS.keySet());
        }
        return s;
    }

    /** Every servo id, in catalog order (read only). */
    public static Set<String> servos() {
        return Collections.unmodifiableSet(SERVOS.keySet());
    }
}
