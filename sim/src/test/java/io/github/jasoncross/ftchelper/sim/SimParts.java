package io.github.jasoncross.ftchelper.sim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    /** A servo from the catalog. */
    static final class Servo {
        final String id, name;
        final double rangeDeg, secPer60Deg, torqueKgCm;

        Servo(Map<String, Object> m) {
            id = SimJsonReader.string(m, "id", "");
            name = SimJsonReader.string(m, "name", id);
            rangeDeg = SimJsonReader.number(m, "rangeDeg", Double.NaN);
            secPer60Deg = SimJsonReader.number(m, "secPer60Deg", Double.NaN);
            torqueKgCm = SimJsonReader.number(m, "torqueKgCm", Double.NaN);
        }

        /** How fast it moves in raw positions (0..1) per second: (60 deg / time) / range. */
        double rawPerSecond() {
            if (Double.isNaN(rangeDeg) || Double.isNaN(secPer60Deg) || secPer60Deg <= 0) return Double.NaN;
            return (60.0 / secPer60Deg) / rangeDeg;
        }
    }

    final Map<String, Motor> motors = new LinkedHashMap<>();
    final Map<String, Servo> servos = new LinkedHashMap<>();

    private static SimParts catalog;

    /** The catalog, read once from the classpath (sim/parts/parts.json). Empty if it is missing. */
    static synchronized SimParts catalog() {
        if (catalog != null) return catalog;
        catalog = new SimParts();
        try (InputStream in = SimParts.class.getResourceAsStream("/parts.json")) {
            if (in == null) return catalog;
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            Map<String, Object> root = SimJsonReader.object(SimJsonReader.parse(b.toString("UTF-8")), "parts.json");
            for (Object o : SimJsonReader.array(root.getOrDefault("motors", java.util.Collections.emptyList()), "motors")) {
                Motor m = new Motor(SimJsonReader.object(o, "motor"));
                catalog.motors.put(m.id, m);
            }
            for (Object o : SimJsonReader.array(root.getOrDefault("servos", java.util.Collections.emptyList()), "servos")) {
                Servo s = new Servo(SimJsonReader.object(o, "servo"));
                catalog.servos.put(s.id, s);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read parts.json", e);
        }
        return catalog;
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
