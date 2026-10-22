package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.launcher.ShotTable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The good shots saved in the shot tuner: distance, speed and (if there is a hood) hood angle, with
 * how many went in and the flywheel speed really measured at the shots. Saving a shot near a distance
 * already saved (within {@link #SAME_DISTANCE}) replaces it, so a better shot at the same spot wins.
 * Turns into {@code ShotTable} code and the line for {@code Tunables.java}.
 *
 * <p>Two helpers for the tuner live here too: {@link Tally} (hits and shots at each speed tried at one
 * spot, to keep the speed with the best hit ratio) and {@link DistanceAverage} (the distance averaged
 * over half a second, so one noisy camera frame does not go into the table).
 *
 * Docs: docs/modules/finders.md
 */
public final class ShotPoints {
    /** Two shots closer than this (in distance units, e.g. inches) count as the same spot. */
    public static final double SAME_DISTANCE = 1.0;

    /** One saved point: the shot, and what the tuner saw while testing it. */
    public static final class Saved {
        /** Distance, speed (the setpoint: what the robot will ask for) and hood angle. */
        public final ShotTable.Shot shot;
        /** The flywheel speed measured at the shots (average, RPM), NaN if not known. */
        public final double measuredRpm;
        /** Shots that went in, and shots marked, at this speed (0, 0 if not counted). */
        public final int hits, shots;

        Saved(ShotTable.Shot shot, double measuredRpm, int hits, int shots) {
            this.shot = shot;
            this.measuredRpm = measuredRpm;
            this.hits = hits;
            this.shots = shots;
        }
    }

    private final boolean hasHood;
    private final List<Saved> points = new ArrayList<>();

    /** @param hasHood true if the launcher has a hood (then each line has a hood angle too) */
    public ShotPoints(boolean hasHood) {
        this.hasHood = hasHood;
    }

    /** Saves a shot (replaces one within {@link #SAME_DISTANCE} of this distance). */
    public void save(double distance, double rpm, double hoodDeg) {
        save(distance, rpm, hoodDeg, Double.NaN, 0, 0);
    }

    /**
     * Saves a shot with what was seen while testing it (replaces one within {@link #SAME_DISTANCE}).
     *
     * @param distance    inches
     * @param rpm         the speed asked for (the setpoint), RPM
     * @param hoodDeg     the hood angle (ignored without a hood)
     * @param measuredRpm the flywheel speed measured at the shots (average), NaN if not known
     * @param hits        shots that went in at this speed
     * @param shots       shots marked at this speed
     */
    public void save(double distance, double rpm, double hoodDeg, double measuredRpm, int hits, int shots) {
        removeNear(distance);
        points.add(new Saved(new ShotTable.Shot(distance, rpm, hoodDeg), measuredRpm, hits, shots));
        Collections.sort(points, (a, b) -> Double.compare(a.shot.distance, b.shot.distance));
    }

    /** Removes the saved shot within {@link #SAME_DISTANCE} of this distance. True if one was removed. */
    public boolean removeNear(double distance) {
        return points.removeIf(p -> Math.abs(p.shot.distance - distance) < SAME_DISTANCE);
    }

    /** The saved shots, nearest first (a copy). */
    public List<ShotTable.Shot> points() {
        List<ShotTable.Shot> out = new ArrayList<>();
        for (Saved p : points) out.add(p.shot);
        return out;
    }

    /** The saved points with their hit counts and measured speeds, nearest first (a copy). */
    public List<Saved> saved() {
        return new ArrayList<>(points);
    }

    /** How many points are saved. */
    public int size() {
        return points.size();
    }

    /**
     * The code for these shots: "new ShotTable()" and one ".add(...)" line per shot, each with a
     * comment saying how many went in and the speed measured, e.g.
     * {@code .add(48.0, 2550)   // 4/5 in, measured 2530 RPM}.
     */
    public List<String> codeLines() {
        List<String> out = new ArrayList<>();
        if (points.isEmpty()) return out;
        out.add("new ShotTable()");
        for (Saved s : points) {
            ShotTable.Shot p = s.shot;
            String line = "    .add(" + FinderResults.num(p.distance, 1) + ", " + FinderResults.num(p.rpm, 0);
            if (hasHood) line += ", " + FinderResults.num(p.hoodDeg, 1);
            line += ")";
            String note = note(s);
            out.add(note.isEmpty() ? line : line + "   // " + note);
        }
        return out;
    }

    /** "4/5 in, measured 2530 RPM" for a saved point, or "" when nothing was counted or measured. */
    public static String note(Saved s) {
        List<String> parts = new ArrayList<>();
        if (s.shots > 0) parts.add(s.hits + "/" + s.shots + " in");
        if (!Double.isNaN(s.measuredRpm)) parts.add("measured " + FinderResults.num(s.measuredRpm, 0) + " RPM");
        return FinderResults.commas(parts);
    }

    /**
     * The table for the robot in five files, one line for {@code Tunables.java} (inches, rpm; no hood):
     * {@code public static double[][] speedByDistance = {{48, 2550}, {72, 2900}};   // in Tunables.Launcher}
     * (the line that replaces the one in that section). Empty if nothing is saved.
     *
     * @param section the launcher's Tunables section, e.g. "Launcher"
     */
    public String tunablesLine(String section) {
        return tunablesLine(section, "speedByDistance");
    }

    /**
     * {@link #tunablesLine(String)} for a named table, e.g. field "speedByDistanceNectar":
     * {@code public static double[][] speedByDistanceNectar = {{48, 2800}};   // in Tunables.Launcher}.
     *
     * @param section the launcher's Tunables section, e.g. "Launcher"
     * @param field   the table's field name, e.g. "speedByDistancePollen"
     */
    public String tunablesLine(String section, String field) {
        if (points.isEmpty()) return "";
        List<String> rows = new ArrayList<>();
        for (Saved s : points) rows.add("{" + shortNum(s.shot.distance) + ", " + FinderResults.num(s.shot.rpm, 0) + "}");
        return "public static double[][] " + field + " = {" + FinderResults.commas(rows) + "};   // in Tunables." + section;
    }

    // 48.0 -> "48", 48.5 -> "48.5"
    private static String shortNum(double v) {
        String s = FinderResults.num(v, 1);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    // ----------------------------- hits at each speed -----------------------------

    /**
     * Hits and shots at each speed tried at ONE spot, the way teams tune: shoot 3-5 at a speed, mark
     * each HIT or MISS, try the next speed, and keep the speed with the best hit ratio.
     *
     * <pre>{@code
     * ShotPoints.Tally tally = new ShotPoints.Tally();
     * tally.mark(2500, true, 2490);    // a hit at 2500 RPM (the flywheel measured 2490)
     * tally.mark(2500, false, 2480);   // a miss
     * tally.bestRpm();                 // 2500 (1 of 2)
     * }</pre>
     */
    public static final class Tally {
        // Per speed (RPM, rounded to a whole number): hits, shots, and the measured speeds.
        private static final class Row {
            int hits, shots, measuredCount;
            double measuredSum;
        }

        private final TreeMap<Long, Row> rows = new TreeMap<>();
        private double spotIn = Double.NaN;

        /**
         * One shot marked at this speed.
         *
         * @param rpm         the speed asked for (setpoint), RPM
         * @param hit         true if it went in
         * @param measuredRpm the flywheel speed measured at that shot, NaN if not known
         */
        public void mark(double rpm, boolean hit, double measuredRpm) {
            Row r = rows.get(Math.round(rpm));
            if (r == null) {
                r = new Row();
                rows.put(Math.round(rpm), r);
            }
            r.shots++;
            if (hit) r.hits++;
            if (!Double.isNaN(measuredRpm)) {
                r.measuredSum += measuredRpm;
                r.measuredCount++;
            }
        }

        /** Hits at this speed. */
        public int hits(double rpm) {
            Row r = rows.get(Math.round(rpm));
            return r == null ? 0 : r.hits;
        }

        /** Shots marked at this speed. */
        public int shots(double rpm) {
            Row r = rows.get(Math.round(rpm));
            return r == null ? 0 : r.shots;
        }

        /** Shots marked at every speed. */
        public int totalShots() {
            int n = 0;
            for (Row r : rows.values()) n += r.shots;
            return n;
        }

        /** The average measured flywheel speed at this speed's shots, NaN if none was measured. */
        public double measuredRpm(double rpm) {
            Row r = rows.get(Math.round(rpm));
            return r == null || r.measuredCount == 0 ? Double.NaN : r.measuredSum / r.measuredCount;
        }

        /**
         * The speed with the best hit ratio (hits / shots). A tie: the one with more shots (surer),
         * then the lower speed. NaN if nothing went in yet.
         */
        public double bestRpm() {
            long best = 0;
            Row bestRow = null;
            for (Map.Entry<Long, Row> e : rows.entrySet()) {
                Row r = e.getValue();
                if (r.hits == 0) continue;
                if (bestRow == null) {
                    best = e.getKey();
                    bestRow = r;
                    continue;
                }
                // Compare hits/shots without dividing: a/b > c/d  is the same as  a*d > c*b.
                long better = (long) r.hits * bestRow.shots - (long) bestRow.hits * r.shots;
                if (better > 0 || (better == 0 && r.shots > bestRow.shots)) {
                    best = e.getKey();
                    bestRow = r;
                }
            }
            return bestRow == null ? Double.NaN : best;
        }

        /** Every speed tried here with its hits, e.g. "2450: 1/3, 2550: 3/4 (best)"; "" if none. */
        public String summary() {
            double best = bestRpm();
            List<String> parts = new ArrayList<>();
            for (Map.Entry<Long, Row> e : rows.entrySet()) {
                parts.add(e.getKey() + ": " + e.getValue().hits + "/" + e.getValue().shots
                        + (!Double.isNaN(best) && e.getKey() == (long) best ? " (best)" : ""));
            }
            return FinderResults.commas(parts);
        }

        /** The distance where these shots were marked (inches), NaN before the first mark. */
        public double spotIn() {
            return spotIn;
        }

        /** Sets the spot (the distance when the first shot was marked). */
        public void setSpot(double distanceIn) {
            spotIn = distanceIn;
        }

        /** True if nothing is marked. */
        public boolean isEmpty() {
            return rows.isEmpty();
        }

        /** Forgets everything (a new spot, a new table, or after saving). */
        public void clear() {
            rows.clear();
            spotIn = Double.NaN;
        }
    }

    // ----------------------------- the distance, averaged -----------------------------

    /**
     * The distance averaged over the last {@link #WINDOW_SEC} seconds of fresh readings, so one noisy
     * camera frame does not go into the table. A reading from another source (camera, then odometry)
     * starts the average again: the two can differ by a few inches.
     *
     * <pre>{@code
     * ShotPoints.DistanceAverage avg = new ShotPoints.DistanceAverage();
     * avg.add(10.0, 47, "camera");
     * avg.add(10.1, 49, "camera");
     * avg.average(10.1);   // 48
     * }</pre>
     */
    public static final class DistanceAverage {
        /** How far back readings count, seconds. */
        public static final double WINDOW_SEC = 0.5;

        private final ArrayDeque<double[]> readings = new ArrayDeque<>();   // {time s, distance}
        private String source = "";

        /**
         * A fresh reading.
         *
         * @param nowSec   the time, seconds
         * @param distance inches
         * @param from     where it came from ("camera", "odometry")
         */
        public void add(double nowSec, double distance, String from) {
            if (!from.equals(source)) {
                readings.clear();
                source = from;
            }
            readings.addLast(new double[] {nowSec, distance});
            drop(nowSec);
        }

        /** The average of the readings in the last {@link #WINDOW_SEC} seconds; NaN if there are none. */
        public double average(double nowSec) {
            drop(nowSec);
            if (readings.isEmpty()) return Double.NaN;
            double sum = 0;
            for (double[] r : readings) sum += r[1];
            return sum / readings.size();
        }

        /** How many readings are in the average now. */
        public int count(double nowSec) {
            drop(nowSec);
            return readings.size();
        }

        /** Where the readings came from ("camera", "odometry"), "" if none yet. */
        public String source() {
            return source;
        }

        // Forget readings older than the window.
        private void drop(double nowSec) {
            for (Iterator<double[]> it = readings.iterator(); it.hasNext(); ) {
                if (nowSec - it.next()[0] > WINDOW_SEC) it.remove();
            }
        }
    }
}
