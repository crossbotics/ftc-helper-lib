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
 * {@link #extendEnds} changes that, if you choose it: then each end continues the line of its two points,
 * up to a speed limit. Those speeds are estimates ({@link #isEstimated}): {@link #covers} stays false there.
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
    // extendEnds(): the most speed an estimate may ask for (NaN = off, the default: the ends stay flat).
    private double extendMaxRpm = Double.NaN;

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
        // Two speeds for one distance: with extended ends the line would have no slope. Say so now.
        if (extendsEnds()) {
            for (Shot p : points) if (p.distance == shot.distance) throw sameDistance(shot.distance);
        }
        points.add(shot);
        // Sort again after each add, so lookup() can walk the list from short to long.
        // "(a, b) -> ..." tells the sort how to compare two points: by distance.
        Collections.sort(points, (a, b) -> Double.compare(a.distance, b.distance));
        return this;
    }

    /**
     * The shot for a distance (inches). Between two measured points it fills in on a straight line;
     * outside them it gives the nearest end point (or, after {@link #extendEnds}, the end's line continued,
     * limited to 0..its speed limit). An unknown distance (NaN, e.g. no target seen)
     * gives the shortest point.
     *
     * @throws IllegalStateException if the table has no points
     */
    public Shot lookup(double distance) {
        if (points.isEmpty()) throw new IllegalStateException("ShotTable has no points: add(...) some first");
        // NaN = "not a number": we do not know the distance, so use the first (shortest) point.
        if (Double.isNaN(distance)) return points.get(0);
        // Closer than the closest point, or farther than the farthest: use that end point.
        // We do not guess beyond what was tested (a guessed shot could be far off), unless extendEnds() asks for it.
        Shot first = points.get(0), last = points.get(points.size() - 1);
        // Extended ends (opt-in): continue the end's line, never below 0 or above the limit. The hood keeps
        // its end angle: a line continued past a hood's range could jam it.
        if (isEstimated(distance)) {
            double rpm = Math.max(0, Math.min(extendMaxRpm, lineRpm(distance)));
            return new Shot(distance, rpm, distance < first.distance ? first.hoodDeg : last.hoodDeg);
        }
        if (distance <= first.distance) return first;
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

    /** True if the distance is inside the tested range. (An estimate past the ends is never "covered".) */
    public boolean covers(double distance) {
        return !points.isEmpty() && distance >= points.get(0).distance
                && distance <= points.get(points.size() - 1).distance;
    }

    // ---- Extended ends (opt-in) ----

    /**
     * Continues the table past both ends: each end follows the straight line of its two points, up to
     * {@code maxRpm}. WHY: a goal a little outside the measured rows then gets a sensible speed, with no
     * made-up rows typed into the table. An end stays flat, as without this, when there are fewer than two
     * points or its speed falls with distance ({@link #extensionNotes} says so). The hood keeps its end angle.
     *
     * <pre>{@code
     * table.extendEnds(4800);    // a 6000 RPM motor: keep room to recover
     * }</pre>
     *
     * @param maxRpm the most an estimate may ask for, RPM (above 0)
     * @return this table
     * @throws IllegalArgumentException if maxRpm is not a number above 0
     * @throws IllegalStateException    if two points have the same distance
     */
    public ShotTable extendEnds(double maxRpm) {
        if (!(maxRpm > 0) || Double.isInfinite(maxRpm)) {
            throw new IllegalArgumentException("Wrong table speed limit: " + maxRpm + " RPM.\nUse more than 0.");
        }
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).distance == points.get(i - 1).distance) throw sameDistance(points.get(i).distance);
        }
        extendMaxRpm = maxRpm;
        return this;
    }

    /** True after {@link #extendEnds}. */
    public boolean extendsEnds() {
        return !Double.isNaN(extendMaxRpm);
    }

    /** The speed limit of the estimates (RPM), NaN without {@link #extendEnds}. */
    public double getExtendMaxRpm() {
        return extendMaxRpm;
    }

    /**
     * True if the speed for this distance is an estimate: outside the points, at an end that could be
     * extended. False with extended ends off, inside the points, and at an end that stays flat.
     *
     * @param distance inches (a distance below 0 or not a number is never an estimate)
     */
    public boolean isEstimated(double distance) {
        if (!extendsEnds() || points.size() < 2 || !(distance >= 0) || Double.isInfinite(distance)) return false;
        if (distance < points.get(0).distance) return canExtend(true);
        if (distance > points.get(points.size() - 1).distance) return canExtend(false);
        return false;
    }

    /**
     * True if a shot at this distance has a usable speed: measured ({@link #covers}), or an estimate that
     * did not need more than the speed limit. False past the point where the limit is reached: there the
     * wheel gets the limit, and nothing says the piece still reaches the goal. Also false closer than the
     * first row where the estimate falls under {@link #LOWEST_ESTIMATE_SHARE} of the first row's speed: a steep
     * first pair of rows gives a guess far too slow to trust.
     *
     * @param distance inches
     */
    public boolean reaches(double distance) {
        if (covers(distance)) return true;
        if (!isEstimated(distance)) return false;
        double rpm = lineRpm(distance);
        double lowest = distance < points.get(0).distance ? LOWEST_ESTIMATE_SHARE * points.get(0).rpm : 0;
        // (A tiny margin: 99.55 in must not fail on the last digit of a sum.)
        return rpm > 0 && rpm >= lowest - 1e-9 && rpm <= extendMaxRpm + 1e-9;
    }

    /**
     * Closer than the first row, an estimate under this share of the first row's speed does not "reach"
     * ({@link #reaches}): the aim does not lock on it, and continuous fire does not start.
     */
    public static final double LOWEST_ESTIMATE_SHARE = 0.5;

    /**
     * True if the estimate for this distance is cut to the speed limit ("estimated, speed limited").
     *
     * @param distance inches
     */
    public boolean isSpeedLimited(double distance) {
        return isEstimated(distance) && lineRpm(distance) > extendMaxRpm + 1e-9;
    }

    /**
     * What {@link #extendEnds} could not do, for INIT: short plain lines, the problem and then what to do.
     * Empty when both ends extend, or when it is off.
     */
    public List<String> extensionNotes() {
        List<String> out = new ArrayList<>();
        if (!extendsEnds()) return out;
        if (points.size() < 2) {
            out.add("Only 1 table row: NO speed estimates.");
            out.add("Add a second tested row.");
            return out;
        }
        if (!canExtend(true)) {
            out.add("First 2 rows: speed drops with distance.");
            out.add("NO estimate closer than row 1.");
            out.add("Check those 2 rows.");
        }
        if (!canExtend(false)) {
            out.add("Last 2 rows: speed drops with distance.");
            out.add("NO estimate past the last row.");
            out.add("Check those 2 rows.");
        }
        double top = 0;
        for (Shot p : points) top = Math.max(top, p.rpm);
        if (top > extendMaxRpm) {
            out.add(String.format(Locale.US, "Speed limit %.0f RPM is below a tested row.", extendMaxRpm));
            out.add(String.format(Locale.US, "Set extendMaxRpm to %.0f or more.", top));
        }
        return out;
    }

    // The line through an end's two points can be used: the speed does not fall with distance.
    private boolean canExtend(boolean near) {
        Shot a = points.get(near ? 0 : points.size() - 2), b = points.get(near ? 1 : points.size() - 1);
        // ">=" also says no to NaN (a row that is not a number).
        return b.rpm - a.rpm >= 0;
    }

    // The end's line at this distance, with no limit. Only for an estimated distance.
    private double lineRpm(double distance) {
        boolean near = distance < points.get(0).distance;
        Shot a = points.get(near ? 0 : points.size() - 2), b = points.get(near ? 1 : points.size() - 1);
        Shot end = near ? a : b;
        return end.rpm + (distance - end.distance) * (b.rpm - a.rpm) / (b.distance - a.distance);
    }

    private static IllegalStateException sameDistance(double distance) {
        String inches = distance == Math.rint(distance) ? String.valueOf((long) distance) : String.valueOf(distance);
        return new IllegalStateException("Two table rows both use " + inches + " inches.\nDelete one of those rows.");
    }

    /** The points, sorted by distance. The list cannot be changed (use add(...) instead). */
    public List<Shot> points() {
        return Collections.unmodifiableList(points);
    }
}
