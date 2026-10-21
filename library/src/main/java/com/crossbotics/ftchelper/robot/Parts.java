package com.crossbotics.ftchelper.robot;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The wheels and rollers (and the servo ids) of the simulator's parts catalog ({@code sim/parts/parts.json},
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

    private static final Map<String, Wheel> WHEELS = new LinkedHashMap<>();
    private static final Set<String> SERVOS = new LinkedHashSet<>();

    private static void wheel(String id, double diameterMm, double widthMm) {
        WHEELS.put(id, new Wheel(id, diameterMm, widthMm));
    }

    // Copied from sim/parts/parts.json ("wheels": diameterMm, widthMm; "servos": id). PartsCatalogTest checks they match.
    static {
        wheel("gobilda-boot-72-rex", 72, Double.NaN);
        wheel("gobilda-gecko-48-rex", 48, Double.NaN);
        wheel("gobilda-gecko-72", 72, 24);
        wheel("gobilda-hogback-96", 96, 24);
        wheel("offset-tubing-60", 60, Double.NaN);
        wheel("offset-tubing-90", 90, Double.NaN);
        SERVOS.addAll(Arrays.asList("gobilda-2000-torque", "gobilda-2000-speed", "gobilda-2000-super-speed",
                "gobilda-2000-5turn-torque", "gobilda-2000-5turn-speed", "gobilda-2000-5turn-super-speed",
                "rev-smart-servo", "rev-smart-servo-v2-balanced", "rev-smart-servo-v2-ultraspeed",
                "axon-max-mk2", "axon-max-plus", "axon-mini-mk2", "axon-mini-plus"));
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
        if (!SERVOS.contains(id)) {
            throw new IllegalArgumentException("Robot.java: no servo \"" + id + "\" in the parts catalog. Known: " + SERVOS);
        }
        return id;
    }

    /** Every servo id, in catalog order (read only). */
    public static Set<String> servos() {
        return Collections.unmodifiableSet(SERVOS);
    }
}
