package io.github.jasoncross.ftchelper.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Turns a distance into a shot: flywheel RPM and hood angle. You measure a few good shots,
 * and it fills in between (straight lines between your points). (Last season's AutoRpmConfig.)
 *
 * <pre>{@code
 * ShotTable pollen = new ShotTable()
 *         .add(24, 2600, 8)     // at 24 in: 2600 rpm, hood 8 deg
 *         .add(48, 3100, 15)
 *         .add(72, 3700, 21);
 * ShotTable.Shot shot = pollen.lookup(36);   // 2850 rpm, 11.5 deg
 * }</pre>
 *
 * <p>Outside your points it uses the nearest end point (it does not guess beyond what you tested).
 * Make one table per game piece if they fly differently. Docs: docs/modules/launcher.md
 */
public final class ShotTable {

    /** One shot setting. */
    public static final class Shot {
        public final double distance, rpm, hoodDeg;
        public Shot(double distance, double rpm, double hoodDeg) {
            this.distance = distance;
            this.rpm = rpm;
            this.hoodDeg = hoodDeg;
        }
        @Override public String toString() {
            return String.format(Locale.US, "%.0f in: %.0f rpm, %.1f deg", distance, rpm, hoodDeg);
        }
    }

    private final List<Shot> points = new ArrayList<>();

    /** Adds a measured point. Order does not matter. Hood angle may be 0 if you have no hood. */
    public ShotTable add(double distance, double rpm, double hoodDeg) {
        points.add(new Shot(distance, rpm, hoodDeg));
        Collections.sort(points, (a, b) -> Double.compare(a.distance, b.distance));
        return this;
    }

    /** Adds a point with no hood. */
    public ShotTable add(double distance, double rpm) {
        return add(distance, rpm, 0);
    }

    /**
     * The shot for a distance.
     *
     * @throws IllegalStateException if the table has no points
     */
    public Shot lookup(double distance) {
        if (points.isEmpty()) throw new IllegalStateException("ShotTable has no points: add(...) some first");
        if (Double.isNaN(distance)) return points.get(0);
        if (distance <= points.get(0).distance) return points.get(0);
        Shot last = points.get(points.size() - 1);
        if (distance >= last.distance) return last;
        for (int i = 1; i < points.size(); i++) {
            Shot b = points.get(i);
            if (distance <= b.distance) {
                Shot a = points.get(i - 1);
                double t = (distance - a.distance) / (b.distance - a.distance);
                return new Shot(distance, a.rpm + (b.rpm - a.rpm) * t, a.hoodDeg + (b.hoodDeg - a.hoodDeg) * t);
            }
        }
        return last;
    }

    /** True if the distance is inside the tested range. */
    public boolean covers(double distance) {
        return !points.isEmpty() && distance >= points.get(0).distance
                && distance <= points.get(points.size() - 1).distance;
    }

    /** The points, sorted by distance. */
    public List<Shot> points() {
        return Collections.unmodifiableList(points);
    }
}
