package com.crossbotics.ftchelper.launcher;

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
 * Make one table per game piece if they fly differently. Docs: docs/modules/launcher.md;
 * tutorial 17 measures a table step by step.
 */
public final class ShotTable {
    // "final" on a class means no other class can be built on top of it (extend it).

    /**
     * One shot setting: at {@code distance} (inches), spin to {@code rpm} with the hood at
     * {@code hoodDeg} degrees. "static" here means a Shot does not belong to one particular table.
     */
    public static final class Shot {
        // "final" fields: once a Shot is made, its numbers never change, so it is safe to share.
        /** The distance to the goal, the flywheel speed (RPM) and the hood angle (degrees) for it. */
        public final double distance, rpm, hoodDeg;

        /** A shot: distance in inches, flywheel RPM, hood angle in degrees. */
        public Shot(double distance, double rpm, double hoodDeg) {
            this.distance = distance;
            this.rpm = rpm;
            this.hoodDeg = hoodDeg;
        }
        // Text for telemetry, e.g. "36 in: 2850 rpm, 11.5 deg".
        @Override public String toString() {
            return String.format(Locale.US, "%.0f in: %.0f rpm, %.1f deg", distance, rpm, hoodDeg);
        }
    }

    // The measured points, always kept sorted from the shortest distance to the longest.
    private final List<Shot> points = new ArrayList<>();
    // True once a point was added WITH a hood angle: then a launcher with a hood moves it from this table.
    private boolean hasHood = false;

    /**
     * Adds a measured point (distance in inches, RPM, hood in degrees). Order does not matter. A table
     * with hood angles moves the launcher's hood ({@link #hasHood()}).
     */
    public ShotTable add(double distance, double rpm, double hoodDeg) {
        hasHood = true;
        return addPoint(new Shot(distance, rpm, hoodDeg));
    }

    /**
     * Adds a point with no hood angle (its hood number is 0 and means nothing). A table made only of
     * these never moves a hood: the hood stays where you put it.
     */
    public ShotTable add(double distance, double rpm) {
        return addPoint(new Shot(distance, rpm, 0));
    }

    /**
     * True if any point was added with a hood angle ({@code add(distance, rpm, hoodDeg)}). Only then does
     * {@link Launcher#setDistance} move the hood.
     */
    public boolean hasHood() {
        return hasHood;
    }

    private ShotTable addPoint(Shot shot) {
        points.add(shot);
        // Sort again after each add, so lookup() can walk the list from short to long.
        // "(a, b) -> ..." tells the sort how to compare two points: by distance.
        Collections.sort(points, (a, b) -> Double.compare(a.distance, b.distance));
        return this;
    }

    /**
     * The shot for a distance (inches). Between two measured points it fills in on a straight line;
     * outside them it gives the nearest end point. An unknown distance (NaN, e.g. no target seen)
     * gives the shortest point.
     *
     * @throws IllegalStateException if the table has no points
     */
    public Shot lookup(double distance) {
        if (points.isEmpty()) throw new IllegalStateException("ShotTable has no points: add(...) some first");
        // NaN = "not a number": we do not know the distance, so use the first (shortest) point.
        if (Double.isNaN(distance)) return points.get(0);
        // Closer than the closest point, or farther than the farthest: use that end point.
        // We never guess beyond what was tested, because a guessed shot could be far off.
        if (distance <= points.get(0).distance) return points.get(0);
        Shot last = points.get(points.size() - 1);
        if (distance >= last.distance) return last;
        // Find the two measured points the distance falls between (a below, b above).
        for (int i = 1; i < points.size(); i++) {
            Shot b = points.get(i);
            if (distance <= b.distance) {
                Shot a = points.get(i - 1);
                // t = how far from a toward b, 0 (at a) to 1 (at b). Example: 36 in between 24 and 48 = 0.5.
                double t = (distance - a.distance) / (b.distance - a.distance);
                // Go the same share of the way from a's RPM and hood to b's.
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

    /** The points, sorted by distance. The list cannot be changed (use add(...) instead). */
    public List<Shot> points() {
        return Collections.unmodifiableList(points);
    }
}
