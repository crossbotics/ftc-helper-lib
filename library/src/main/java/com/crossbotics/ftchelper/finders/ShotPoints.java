package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.launcher.ShotTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The good shots saved in the shot tuner: distance, speed and (if there is a hood) hood angle.
 * Saving a shot near a distance already saved (within {@link #SAME_DISTANCE}) replaces it, so a
 * better shot at the same spot wins. Turns into {@code ShotTable} code.
 *
 * Docs: docs/modules/finders.md
 */
public final class ShotPoints {
    /** Two shots closer than this (in distance units, e.g. inches) count as the same spot. */
    public static final double SAME_DISTANCE = 1.0;

    private final boolean hasHood;
    private final List<ShotTable.Shot> points = new ArrayList<>();

    /** @param hasHood true if the launcher has a hood (then each line has a hood angle too) */
    public ShotPoints(boolean hasHood) {
        this.hasHood = hasHood;
    }

    /** Saves a shot (replaces one within {@link #SAME_DISTANCE} of this distance). */
    public void save(double distance, double rpm, double hoodDeg) {
        removeNear(distance);
        points.add(new ShotTable.Shot(distance, rpm, hoodDeg));
        Collections.sort(points, (a, b) -> Double.compare(a.distance, b.distance));
    }

    /** Removes the saved shot within {@link #SAME_DISTANCE} of this distance. True if one was removed. */
    public boolean removeNear(double distance) {
        return points.removeIf(p -> Math.abs(p.distance - distance) < SAME_DISTANCE);
    }

    /** The saved shots, nearest first (a copy). */
    public List<ShotTable.Shot> points() {
        return new ArrayList<>(points);
    }

    /** The code for these shots: "new ShotTable()" and one ".add(...)" line per shot. */
    public List<String> codeLines() {
        List<String> out = new ArrayList<>();
        if (points.isEmpty()) return out;
        out.add("new ShotTable()");
        for (ShotTable.Shot p : points) {
            String line = "    .add(" + FinderResults.num(p.distance, 1) + ", " + FinderResults.num(p.rpm, 0);
            if (hasHood) line += ", " + FinderResults.num(p.hoodDeg, 1);
            out.add(line + ")");
        }
        return out;
    }

    /**
     * The table for the robot in five files, one line for {@code Tunables.java} (inches, rpm; no hood):
     * {@code Tunables.Launcher.speedByDistance = {{48, 2550}, {72, 2900}};}. Empty if nothing is saved.
     *
     * @param section the launcher's Tunables section, e.g. "Launcher"
     */
    public String tunablesLine(String section) {
        if (points.isEmpty()) return "";
        List<String> rows = new ArrayList<>();
        for (ShotTable.Shot p : points) rows.add("{" + shortNum(p.distance) + ", " + FinderResults.num(p.rpm, 0) + "}");
        return "Tunables." + section + ".speedByDistance = {" + FinderResults.commas(rows) + "};";
    }

    // 48.0 -> "48", 48.5 -> "48.5"
    private static String shortNum(double v) {
        String s = FinderResults.num(v, 1);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
