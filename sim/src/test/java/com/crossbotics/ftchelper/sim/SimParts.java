package com.crossbotics.ftchelper.sim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The catalog of common FTC motors and servos (sim/parts/parts.json): real part numbers with the
 * numbers from the sellers' pages. A robot file names a part ("part=gobilda-5203-312") and the
 * simulator takes its speed, encoder counts and current from here.
 */
final class SimParts {
    /** A motor from the catalog. */
    static final class Motor {
        final String id, name;
        final double freeRpm, countsPerRev, stallCurrentA, freeCurrentA, stallTorqueKgCm;

        Motor(Map<String, Object> m) {
            id = SimJsonReader.string(m, "id", "");
            name = SimJsonReader.string(m, "name", id);
            freeRpm = SimJsonReader.number(m, "freeRpm", Double.NaN);
            countsPerRev = SimJsonReader.number(m, "countsPerRev", Double.NaN);
            stallCurrentA = SimJsonReader.number(m, "stallCurrentA", 9.2);
            freeCurrentA = SimJsonReader.number(m, "freeCurrentA", 0.25);
            stallTorqueKgCm = SimJsonReader.number(m, "stallTorqueKgCm", Double.NaN);
        }

        /** Encoder counts per second at full power (free speed). */
        double maxTicksPerSec() {
            return freeRpm * countsPerRev / 60.0;
        }
    }

    /** The FTC SDK's normal pulse for servo positions 0 and 1, microseconds (PwmControl.PwmRange.defaultRange). */
    static final double SDK_LOW_US = 600, SDK_HIGH_US = 2400;

    /**
     * A servo from the catalog. rangeDeg is the seller's full turn over pulseMinUs..pulseMaxUs; the hub
     * normally sends only 600..2400 us, so positions 0..1 turn it less (a goBILDA 2000 servo: 270 of its
     * 300 degrees) unless the code widens the pulse (.fullRange()). A team part without pulse numbers:
     * its rangeDeg is what 0..1 turns it on the normal 600..2400 us.
     */
    static final class Servo {
        final String id, name;
        final double rangeDeg, pulseMinUs, pulseMaxUs, secPer60Deg, torqueKgCm;

        Servo(Map<String, Object> m) {
            id = SimJsonReader.string(m, "id", "");
            name = SimJsonReader.string(m, "name", id);
            rangeDeg = SimJsonReader.number(m, "rangeDeg", Double.NaN);
            pulseMinUs = SimJsonReader.number(m, "pulseMinUs", Double.NaN);
            pulseMaxUs = SimJsonReader.number(m, "pulseMaxUs", Double.NaN);
            if (!Double.isNaN(pulseMinUs) || !Double.isNaN(pulseMaxUs)) {
                if (!(pulseMinUs >= 100 && pulseMaxUs <= 3000 && pulseMinUs < pulseMaxUs)) {
                    throw new IllegalArgumentException("servo " + id + ": pulseMinUs and pulseMaxUs: give both, 100 to 3000 us, min first");
                }
            }
            // A Control Hub servo port gives about 5 V, so use the seller's row closest to 5 V when there
            // is a table by voltage (the headline number is usually at 6 V).
            double sec = SimJsonReader.number(m, "secPer60Deg", Double.NaN);
            Object rows = m.get("byVoltage");
            if (rows instanceof java.util.List) {
                double best = Double.MAX_VALUE;
                for (Object r : (java.util.List<?>) rows) {
                    if (!(r instanceof Map)) continue;
                    Map<String, Object> row = SimJsonReader.object(r, "byVoltage");
                    double v = SimJsonReader.number(row, "voltage", Double.NaN), s60 = SimJsonReader.number(row, "secPer60Deg", Double.NaN);
                    if (!Double.isNaN(v) && !Double.isNaN(s60) && Math.abs(v - 5.0) < best) {
                        best = Math.abs(v - 5.0);
                        sec = s60;
                    }
                }
            }
            secPer60Deg = sec;
            torqueKgCm = SimJsonReader.number(m, "torqueKgCm", Double.NaN);
        }

        /** Degrees per microsecond of pulse (goBILDA 2000: 300 / 2000 = 0.15); NaN without a range. */
        double degPerUs() {
            if (Double.isNaN(pulseMinUs)) return rangeDeg / (SDK_HIGH_US - SDK_LOW_US);
            return rangeDeg / (pulseMaxUs - pulseMinUs);
        }

        /** Degrees that positions 0..1 turn it with this pulse range (goBILDA 2000 at 600..2400: 270). */
        double degreesOver(double lowUs, double highUs) {
            return degPerUs() * (highUs - lowUs);
        }

        /** How fast it moves in raw positions (0..1, over this pulse range) per second: (60 deg / time) / range. */
        double rawPerSecond(double lowUs, double highUs) {
            double range = degreesOver(lowUs, highUs);
            if (Double.isNaN(range) || Double.isNaN(secPer60Deg) || secPer60Deg <= 0) return Double.NaN;
            return (60.0 / secPer60Deg) / range;
        }
    }

    final Map<String, Motor> motors = new LinkedHashMap<>();
    final Map<String, Servo> servos = new LinkedHashMap<>();

    private static SimParts catalog;

    /** The catalog, read once from the classpath (sim/parts/parts.json). Empty if it is missing. */
    static synchronized SimParts catalog() {
        if (catalog != null) return catalog;
        SimParts c = new SimParts();
        try (InputStream in = SimParts.class.getResourceAsStream("/parts.json")) {
            if (in != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                c.add(b.toString("UTF-8"), "parts.json", false);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read parts.json", e);
        }
        catalog = c;
        return catalog;
    }

    /**
     * The library's catalog plus a team's own parts file (same format; "parts = my-parts.json" in the robot
     * file). An id that is already in the library is refused, so a typo can't quietly replace a real part.
     */
    static SimParts withTeamFile(Path file) {
        SimParts c = new SimParts();
        c.motors.putAll(catalog().motors);
        c.servos.putAll(catalog().servos);
        String name = file.getFileName().toString();
        try {
            c.add(new String(Files.readAllBytes(file), "UTF-8"), name, true);
        } catch (IOException e) {
            throw new IllegalArgumentException("Robot file: parts = " + name + ": could not read " + file);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Robot file: parts = " + name + ": " + e.getMessage());
        }
        return c;
    }

    private void add(String text, String what, boolean refuseKnown) {
        Map<String, Object> root = SimJsonReader.object(SimJsonReader.parse(text), what);
        for (Object o : SimJsonReader.array(root.getOrDefault("motors", java.util.Collections.emptyList()), "motors")) {
            Motor m = new Motor(SimJsonReader.object(o, "motor"));
            check(m.id, refuseKnown);
            motors.put(m.id, m);
        }
        for (Object o : SimJsonReader.array(root.getOrDefault("servos", java.util.Collections.emptyList()), "servos")) {
            Servo s = new Servo(SimJsonReader.object(o, "servo"));
            check(s.id, refuseKnown);
            servos.put(s.id, s);
        }
    }

    private void check(String id, boolean refuseKnown) {
        if (id.isEmpty()) throw new IllegalArgumentException("a part has no \"id\"");
        if (refuseKnown && (motors.containsKey(id) || servos.containsKey(id))) {
            throw new IllegalArgumentException("\"" + id + "\" is already a part (in the library catalog or twice in this file). Give yours its own id, like \"team-" + id + "\".");
        }
    }

    Motor motor(String id) {
        Motor m = motors.get(id);
        if (m == null) throw new IllegalArgumentException("Robot file: no motor part \"" + id + "\" in sim/parts/parts.json. Known: " + motors.keySet());
        if (Double.isNaN(m.freeRpm) || Double.isNaN(m.countsPerRev)) {
            throw new IllegalArgumentException("Motor part \"" + id + "\" has no free speed or encoder counts in parts.json");
        }
        return m;
    }

    Servo servo(String id) {
        Servo s = servos.get(id);
        if (s == null) throw new IllegalArgumentException("Robot file: no servo part \"" + id + "\" in sim/parts/parts.json. Known: " + servos.keySet());
        return s;
    }
}
