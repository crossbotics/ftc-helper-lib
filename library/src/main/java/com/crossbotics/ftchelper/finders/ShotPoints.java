package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.launcher.ShotTable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The good shots saved in the shot tuner: distance, speed and (if there is a hood) hood angle, with
 * how many went in and the flywheel speed really measured at the shots. Saving a shot near a distance
 * already saved (within {@link #SAME_DISTANCE}) replaces it, so a better shot at the same spot wins.
 * Turns into {@code ShotTable} code and the line for {@code Tunables.java}.
 *
 * <p>Helpers for the tuner live here too: {@link Tally} (hits and shots at each speed tried at one
 * spot, to keep the speed with the best hit ratio), {@link Trials} (the shots waiting to be marked, with
 * undo) and {@link DistanceAverage} (the distance averaged over half a second, so one noisy camera frame
 * does not go into the table).
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
    private final int hoodDecimals;
    private final List<Saved> points = new ArrayList<>();

    /** @param hasHood true if the launcher has a hood (then each line has a hood angle too, 1 decimal) */
    public ShotPoints(boolean hasHood) {
        this(hasHood, 1);
    }

    /**
     * @param hasHood      true if the launcher has a hood (then each line has a hood angle too)
     * @param hoodDecimals decimals for the hood: 1 for degrees, 3 for servo positions, 0 for encoder ticks
     */
    public ShotPoints(boolean hasHood, int hoodDecimals) {
        this.hasHood = hasHood;
        this.hoodDecimals = Math.max(0, Math.min(4, hoodDecimals));
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
            if (hasHood) line += ", " + FinderResults.num(p.hoodDeg, hoodDecimals);
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
     * The table for the robot in five files, one line for {@code Tunables.java} ({inches, rpm}, or
     * {inches, rpm, hood} with a hood):
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
        for (Saved s : points) {
            rows.add("{" + shortNum(s.shot.distance) + ", " + FinderResults.num(s.shot.rpm, 0)
                    + (hasHood ? ", " + trim(FinderResults.num(s.shot.hoodDeg, hoodDecimals)) : "") + "}");
        }
        return "public static double[][] " + field + " = {" + FinderResults.commas(rows) + "};   // in Tunables." + section;
    }

    /** A hood number with no zeros that mean nothing: 12.5 -> "12.5", 0.42 -> "0.42", 350 -> "350". */
    static String hoodText(double v) {
        return trim(FinderResults.num(v, 3));
    }

    // "12.50" -> "12.5", "12.0" -> "12", "350" -> "350": no zeros that mean nothing.
    private static String trim(String n) {
        if (!n.contains(".")) return n;
        n = n.replaceAll("0+$", "");
        return n.endsWith(".") ? n.substring(0, n.length() - 1) : n;
    }

    // 48.0 -> "48", 48.5 -> "48.5"
    private static String shortNum(double v) {
        String s = FinderResults.num(v, 1);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    // ----------------------------- hits at each speed -----------------------------

    /**
     * Hits and shots at each speed (and hood angle) tried at ONE spot, the way teams tune: shoot 3-5 at
     * a speed, mark each HIT or MISS, try the next speed, and keep the speed with the best hit ratio
     * (hits / shots: 3 in out of 4 = 3/4). With a hood, each speed AND hood angle is its own try, so the
     * winner is saved with the hood it was really shot with.
     *
     * <pre>{@code
     * ShotPoints.Tally tally = new ShotPoints.Tally();
     * tally.mark(2500, true, 2490);    // a hit at 2500 RPM (the flywheel measured 2490)
     * tally.mark(2500, false, 2480);   // a miss
     * tally.bestRpm();                 // 2500 (1 of 2)
     * }</pre>
     */
    public static final class Tally {
        // One try: a speed (RPM, rounded to a whole number) and a hood angle (to 0.001; NaN = no hood),
        // with its hits, shots, and the measured speeds.
        private static final class Row {
            final long rpm;
            final double hood;
            int hits, shots, measuredCount;
            double measuredSum;

            Row(long rpm, double hood) {
                this.rpm = rpm;
                this.hood = hood;
            }
        }

        // Sorted by speed, then hood angle.
        private final List<Row> rows = new ArrayList<>();
        private double spotIn = Double.NaN;
        // Decimals for the hood on the screen: 1 for degrees (the default), 3 for a servo position, 0 ticks.
        int hoodDecimals = 1;

        /**
         * One shot marked at this speed (no hood).
         *
         * @param rpm         the speed asked for (setpoint), RPM
         * @param hit         true if it went in
         * @param measuredRpm the flywheel speed measured at that shot, NaN if not known
         */
        public void mark(double rpm, boolean hit, double measuredRpm) {
            mark(rpm, Double.NaN, hit, measuredRpm);
        }

        /**
         * One shot marked at this speed and hood angle.
         *
         * @param rpm         the speed asked for (setpoint), RPM
         * @param hoodDeg     the hood angle it was shot with, degrees (NaN = no hood)
         * @param hit         true if it went in
         * @param measuredRpm the flywheel speed measured at that shot, NaN if not known
         */
        public void mark(double rpm, double hoodDeg, boolean hit, double measuredRpm) {
            Row r = row(rpm, hoodDeg, true);
            r.shots++;
            if (hit) r.hits++;
            if (!Double.isNaN(measuredRpm)) {
                r.measuredSum += measuredRpm;
                r.measuredCount++;
            }
        }

        /**
         * Takes back one mark (an undo): the same numbers as the {@link #mark} it undoes. A try left
         * with no shots is forgotten; with nothing marked at all, the spot is too.
         */
        public void unmark(double rpm, double hoodDeg, boolean hit, double measuredRpm) {
            Row r = row(rpm, hoodDeg, false);
            if (r == null || r.shots == 0) return;
            r.shots--;
            if (hit && r.hits > 0) r.hits--;
            if (!Double.isNaN(measuredRpm) && r.measuredCount > 0) {
                r.measuredSum -= measuredRpm;
                r.measuredCount--;
            }
            if (r.shots == 0) rows.remove(r);
            if (rows.isEmpty()) spotIn = Double.NaN;
        }

        /** Hits at this speed (no hood). */
        public int hits(double rpm) {
            return hits(rpm, Double.NaN);
        }

        /** Hits at this speed and hood angle. */
        public int hits(double rpm, double hoodDeg) {
            Row r = row(rpm, hoodDeg, false);
            return r == null ? 0 : r.hits;
        }

        /** Shots marked at this speed (no hood). */
        public int shots(double rpm) {
            return shots(rpm, Double.NaN);
        }

        /** Shots marked at this speed and hood angle. */
        public int shots(double rpm, double hoodDeg) {
            Row r = row(rpm, hoodDeg, false);
            return r == null ? 0 : r.shots;
        }

        /** Shots marked at every speed. */
        public int totalShots() {
            int n = 0;
            for (Row r : rows) n += r.shots;
            return n;
        }

        /** The average measured flywheel speed at this speed's shots (no hood), NaN if none was measured. */
        public double measuredRpm(double rpm) {
            return measuredRpm(rpm, Double.NaN);
        }

        /** The average measured flywheel speed at this speed and hood angle, NaN if none was measured. */
        public double measuredRpm(double rpm, double hoodDeg) {
            Row r = row(rpm, hoodDeg, false);
            return r == null || r.measuredCount == 0 ? Double.NaN : r.measuredSum / r.measuredCount;
        }

        /**
         * The try with the best hit ratio: {speed RPM, hood degrees (NaN = no hood)}. A tie: the one
         * with more shots (surer), then the lower speed. null if nothing went in yet.
         */
        public double[] best() {
            Row bestRow = null;
            for (Row r : rows) {
                if (r.hits == 0) continue;
                if (bestRow == null) {
                    bestRow = r;
                    continue;
                }
                // Compare hits/shots without dividing: a/b > c/d  is the same as  a*d > c*b.
                long better = (long) r.hits * bestRow.shots - (long) bestRow.hits * r.shots;
                if (better > 0 || (better == 0 && r.shots > bestRow.shots)) bestRow = r;
            }
            return bestRow == null ? null : new double[] {bestRow.rpm, bestRow.hood};
        }

        /** The speed of {@link #best()}, NaN if nothing went in yet. */
        public double bestRpm() {
            double[] b = best();
            return b == null ? Double.NaN : b[0];
        }

        /**
         * Every try here with its hits, e.g. "2450: 1/3, 2550: 3/4 (best)", with a hood
         * "2550 @ 12.5 deg: 3/4 (best)"; "" if none.
         */
        public String summary() {
            double[] best = best();
            List<String> parts = new ArrayList<>();
            for (Row r : rows) {
                boolean isBest = best != null && r.rpm == (long) best[0] && sameHood(r.hood, best[1]);
                parts.add(r.rpm + (Double.isNaN(r.hood) ? "" : " @ hood " + trim(FinderResults.num(r.hood, hoodDecimals)))
                        + ": " + r.hits + "/" + r.shots + (isBest ? " (best)" : ""));
            }
            return FinderResults.commas(parts);
        }

        /** How many different tries (speed, hood) are marked here. */
        public int tries() {
            return rows.size();
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

        // The row for this speed and hood (made if asked and missing), or null.
        private Row row(double rpm, double hoodDeg, boolean make) {
            long key = Math.round(rpm);
            // Rounded to 0.001 (a hood in servo positions moves in steps of 0.005), so float dust does not split a try.
            double hood = Double.isNaN(hoodDeg) ? Double.NaN : Math.round(hoodDeg * 1000) / 1000.0;
            for (Row r : rows) if (r.rpm == key && sameHood(r.hood, hood)) return r;
            if (!make) return null;
            Row r = new Row(key, hood);
            rows.add(r);
            // Keep them in order: speed, then hood (NaN first).
            Collections.sort(rows, (a, b) -> a.rpm != b.rpm ? Long.compare(a.rpm, b.rpm) : Double.compare(
                    Double.isNaN(a.hood) ? Double.NEGATIVE_INFINITY : a.hood, Double.isNaN(b.hood) ? Double.NEGATIVE_INFINITY : b.hood));
            return r;
        }

        private static boolean sameHood(double a, double b) {
            return (Double.isNaN(a) && Double.isNaN(b)) || Math.abs(a - b) < 1e-9;
        }
    }

    // ----------------------------- the shots of one spot -----------------------------

    /**
     * The Shot Tuner's shots at one spot: each shot fired waits to be marked HIT or MISS, and keeps the
     * speed, hood angle and distance it was FIRED with (not what the screen says when you mark it).
     * Marks go into a {@link Tally}. It also undoes the last mark, drops a shot that never was (the
     * flywheel dipped without a piece), and forgets shots from a spot the robot has left.
     *
     * <pre>{@code
     * ShotPoints.Trials trials = new ShotPoints.Trials();
     * trials.fired(2550, Double.NaN, 2540, 48);   // a shot: 2550 asked, 2540 measured, 48 in away
     * trials.mark(true);                          // it went in
     * trials.undo();                              // oops, it was a miss: mark it again
     * trials.mark(false);
     * }</pre>
     */
    public static final class Trials {
        /** The marks start again when the distance moves more than this from where they began (inches). */
        public static final double SPOT_MOVE_IN = 4;
        /** At most this many shots wait to be marked (nobody marks more than 10 behind). */
        public static final int MAX_UNMARKED = 10;

        // Shots fired, not marked yet, oldest first: {speed asked, hood (NaN = none), speed measured, distance}.
        private final ArrayDeque<double[]> unmarked = new ArrayDeque<>();
        // The marks given, newest last, for undo: the shot's four numbers and 1 = HIT, 0 = MISS.
        private final ArrayDeque<double[]> marks = new ArrayDeque<>();
        private final Tally tally = new Tally();

        // How many decimals the hood gets on the screen (the Shot Tuner sets it from its hood).
        void hoodDecimals(int decimals) {
            tally.hoodDecimals = decimals;
        }

        /**
         * A piece was fed: it waits to be marked.
         *
         * @param rpm         the speed asked for, RPM
         * @param hoodDeg     the hood angle, degrees (NaN = no hood)
         * @param measuredRpm the flywheel speed measured as it was fed, RPM (NaN = not known)
         * @param distanceIn  the distance it was fired from, inches (NaN = not known)
         */
        public void fired(double rpm, double hoodDeg, double measuredRpm, double distanceIn) {
            unmarked.addLast(new double[] {rpm, hoodDeg, measuredRpm, distanceIn});
            if (unmarked.size() > MAX_UNMARKED) unmarked.pollFirst();   // one added: at most one too many
        }

        /**
         * Marks the OLDEST shot not marked yet (A = HIT, B = MISS), at the speed, hood and spot it was
         * fired with.
         *
         * @return a few words for the Driver Station
         */
        public String mark(boolean hit) {
            double[] shot = unmarked.pollFirst();
            if (shot == null) return "No shot to mark: shoot first (RB), then A = HIT or B = MISS.";
            if (tally.isEmpty()) tally.setSpot(shot[3]);
            else if (Double.isNaN(tally.spotIn())) tally.setSpot(shot[3]);
            tally.mark(shot[0], shot[1], hit, shot[2]);
            marks.addLast(new double[] {shot[0], shot[1], shot[2], shot[3], hit ? 1 : 0});
            if (marks.size() > 50) marks.pollFirst();   // undo goes back 50 marks at most
            return (hit ? "HIT" : "MISS") + " at " + what(shot) + ": " + tally.hits(shot[0], shot[1]) + "/"
                    + tally.shots(shot[0], shot[1]) + " in at this speed. (Wrong? left stick click = undo.)";
        }

        /**
         * Takes back the last HIT or MISS: that shot waits to be marked again (first in line).
         *
         * @return a few words for the Driver Station
         */
        public String undo() {
            double[] m = marks.pollLast();
            if (m == null) return "Nothing to undo.";
            boolean hit = m[4] == 1;
            tally.unmark(m[0], m[1], hit, m[2]);
            unmarked.addFirst(new double[] {m[0], m[1], m[2], m[3]});
            return "Took back the " + (hit ? "HIT" : "MISS") + " at " + what(m) + ": mark that shot again (A / B).";
        }

        /**
         * Forgets the NEWEST shot not marked yet: a "shot" that never was (the flywheel dipped without a
         * piece going through), so the marks stay in line with the real shots.
         *
         * @return a few words for the Driver Station
         */
        public String dropLast() {
            double[] s = unmarked.pollLast();
            if (s == null) return "No shot waiting to be marked.";
            return "Dropped the last shot (" + what(s) + "): it will not be marked.";
        }

        /**
         * Every loop, with the distance now: when the robot has moved more than {@link #SPOT_MOVE_IN}
         * from where the marks began, they start again; shots fired at another spot and not marked yet
         * are dropped (they belong to the old spot).
         *
         * @param distanceIn the distance now, inches (NaN = not known: nothing happens)
         * @return "" or a few words for the Driver Station
         */
        public String check(double distanceIn) {
            if (Double.isNaN(distanceIn)) return "";
            double spot = tally.spotIn();
            if (!tally.isEmpty() && !Double.isNaN(spot) && Math.abs(distanceIn - spot) > SPOT_MOVE_IN) {
                clear();
                // (it says the limit, not the distance now: the robot may still be moving)
                return "Moved more than " + FinderResults.num(SPOT_MOVE_IN, 0) + " in from where the shots were marked: "
                        + "the hit counts start again here.";
            }
            int dropped = 0;
            for (Iterator<double[]> it = unmarked.iterator(); it.hasNext(); ) {
                double d = it.next()[3];
                if (!Double.isNaN(d) && Math.abs(distanceIn - d) > SPOT_MOVE_IN) {
                    it.remove();
                    dropped++;
                }
            }
            if (dropped == 0) return "";
            return "Moved: " + dropped + (dropped == 1 ? " shot" : " shots") + " fired at the old spot dropped (not marked).";
        }

        /** Forgets every shot and mark (a new table, after a save). */
        public void clear() {
            unmarked.clear();
            marks.clear();
            tally.clear();
        }

        /** Shots waiting to be marked. */
        public int unmarkedCount() {
            return unmarked.size();
        }

        /** The hits and shots marked here. */
        public Tally tally() {
            return tally;
        }

        // "2550 RPM" or "2550 RPM, hood 12.5".
        private String what(double[] shot) {
            return FinderResults.num(shot[0], 0) + " RPM" + (Double.isNaN(shot[1]) ? "" : ", hood " + trim(FinderResults.num(shot[1], tally.hoodDecimals)));
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
