package com.crossbotics.ftchelper.sim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * How a launcher really throws (shoot.NAME = ..., table=FILE): for each game piece and wheel speed, the likely
 * exit speed, angle and spin and how much they scatter, from the launcher program (tools/launcher) or from filmed
 * shots. Each shot picks its numbers at random from that spread, so shots scatter like real ones.
 *
 * <pre>
 * {"source": "...", "pieces": {"POLLEN": [
 *    {"shaftRpm": 2400, "shot": 0.97, "speedIn": [310, 9], "angleDeg": [142, 2.5], "backspin": [180, 30],
 *     "exit": [-1.2, 2.9]}, ...]}}
 * </pre>
 * shaftRpm: the flywheel motor's shaft speed. shot: the share of feeds that leave as a shot (the rest drop out).
 * speedIn (in/s), angleDeg (up from the shape's forward, in its forward-up plane, like angle=) and backspin
 * (rad/s; negative = topspin): [mean, spread (one standard deviation)]. exit: where the piece leaves, forward and
 * up from the shape's centre (inches). Between rows the numbers are blended; past the ends the speed grows with
 * the wheel speed and the rest stays.
 */
final class SimShotTable {
    static final class Row {
        final double rpm, shot;
        final double[] speed, angle, spin, exit;

        Row(Map<String, Object> m, String what) {
            rpm = SimJsonReader.number(m, "shaftRpm", Double.NaN);
            if (!(rpm > 0)) throw new IllegalArgumentException("Robot file: " + what + ": every row needs a shaftRpm above 0");
            shot = SimJsonReader.number(m, "shot", 1);
            speed = pair(m, "speedIn", what, null);
            angle = pair(m, "angleDeg", what, null);
            spin = pair(m, "backspin", what, new double[] {0, 0});
            exit = pair(m, "exit", what, new double[] {0, 0});
        }

        private static double[] pair(Map<String, Object> m, String key, String what, double[] fallback) {
            if (!m.containsKey(key)) {
                if (fallback == null) throw new IllegalArgumentException(what + ": a row with no " + key);
                return fallback;
            }
            List<Object> a = SimJsonReader.array(m.get(key), what + " " + key);
            return new double[] {((Number) a.get(0)).doubleValue(), a.size() > 1 ? ((Number) a.get(1)).doubleValue() : 0};
        }
    }

    /** One shot's numbers. */
    static final class Shot {
        boolean shot;
        double speedIn, angleDeg, backspin, exitForward, exitUp;
        double angleMeanDeg;   // the angle before the scatter (a hood moves this)
    }

    final String file;
    final Map<String, List<Row>> pieces = new LinkedHashMap<>();
    private final Random random;

    SimShotTable(Path path, String what, long seed) {
        file = path.toString();
        String text;
        try {
            text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Robot file: " + what + ": can't read the shot table " + path + " (" + e.getMessage() + ")");
        }
        Map<String, Object> root = SimJsonReader.object(SimJsonReader.parse(text), what + " table");
        Map<String, Object> ps = SimJsonReader.object(root.get("pieces"), what + " table pieces");
        for (Map.Entry<String, Object> e : ps.entrySet()) {
            List<Row> rows = new ArrayList<>();
            for (Object o : SimJsonReader.array(e.getValue(), what + " " + e.getKey())) rows.add(new Row(SimJsonReader.object(o, "row"), what + " " + e.getKey()));
            rows.sort((a, b) -> Double.compare(a.rpm, b.rpm));
            if (rows.isEmpty()) throw new IllegalArgumentException("Robot file: " + what + ": the table has no rows for " + e.getKey());
            pieces.put(e.getKey(), rows);
        }
        random = new Random(seed);
    }

    /** A shot of this piece at this flywheel shaft speed, or null if the table does not have the piece. */
    Shot pick(String piece, double shaftRpm) {
        List<Row> rows = pieces.get(piece);
        if (rows == null) return null;
        double rpm = Math.abs(shaftRpm);
        Row a = rows.get(0), b = rows.get(rows.size() - 1);
        double t = 0, scale = 1;
        if (rpm <= a.rpm) {
            b = a;
            scale = a.rpm > 0 ? rpm / a.rpm : 1;
        } else if (rpm >= b.rpm) {
            a = b;
            scale = rpm / b.rpm;
        } else {
            for (int i = 0; i + 1 < rows.size(); i++) {
                if (rpm <= rows.get(i + 1).rpm) {
                    a = rows.get(i);
                    b = rows.get(i + 1);
                    t = (rpm - a.rpm) / (b.rpm - a.rpm);
                    break;
                }
            }
        }
        Shot s = new Shot();
        s.shot = random.nextDouble() < mix(a.shot, b.shot, t);
        s.speedIn = scale * draw(a.speed, b.speed, t);
        // angles blend the short way round (179 to -179 passes 180, not 0)
        double turn = ((b.angle[0] - a.angle[0]) % 360 + 540) % 360 - 180;
        s.angleMeanDeg = a.angle[0] + turn * t;
        s.angleDeg = s.angleMeanDeg + random.nextGaussian() * mix(a.angle[1], b.angle[1], t);
        s.backspin = scale * draw(a.spin, b.spin, t);
        s.exitForward = mix(a.exit[0], b.exit[0], t);
        s.exitUp = mix(a.exit[1], b.exit[1], t);
        return s;
    }

    /**
     * The average shot of this piece at this shaft speed (no scatter, and it does not use up a random pick, so
     * the real shots stay the same), or null if the table does not have the piece. For the page's shot arc.
     */
    Shot mean(String piece, double shaftRpm) {
        List<Row> rows = pieces.get(piece);
        if (rows == null) return null;
        double rpm = Math.abs(shaftRpm);
        Row a = rows.get(0), b = rows.get(rows.size() - 1);
        double t = 0, scale = 1;
        if (rpm <= a.rpm) {
            b = a;
            scale = a.rpm > 0 ? rpm / a.rpm : 1;
        } else if (rpm >= b.rpm) {
            a = b;
            scale = rpm / b.rpm;
        } else {
            for (int i = 0; i + 1 < rows.size(); i++) {
                if (rpm <= rows.get(i + 1).rpm) {
                    a = rows.get(i);
                    b = rows.get(i + 1);
                    t = (rpm - a.rpm) / (b.rpm - a.rpm);
                    break;
                }
            }
        }
        Shot s = new Shot();
        s.shot = true;
        s.speedIn = scale * mix(a.speed[0], b.speed[0], t);
        double turn = ((b.angle[0] - a.angle[0]) % 360 + 540) % 360 - 180;
        s.angleMeanDeg = a.angle[0] + turn * t;
        s.angleDeg = s.angleMeanDeg;
        s.backspin = scale * mix(a.spin[0], b.spin[0], t);
        s.exitForward = mix(a.exit[0], b.exit[0], t);
        s.exitUp = mix(a.exit[1], b.exit[1], t);
        return s;
    }

    private double draw(double[] a, double[] b, double t) {
        return mix(a[0], b[0], t) + random.nextGaussian() * mix(a[1], b[1], t);
    }

    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
