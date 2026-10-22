package com.crossbotics.ftchelper.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A season's game rules for the simulator (sim/fields/&lt;name&gt;.rules.json, next to the field file): match
 * times, point values, which pieces and zones are each alliance's, how a FLOWER stack scores, how long a HIVE
 * takes to tip, and what a clean CELL shot is. Typed by hand from the game manual (each part says which
 * section), while the field file is made from the field CAD by a script. No rules file: the simulator works as
 * before (a piece in a goal is counted and taken off, HIVEs tip at once, no points).
 */
final class SimRules {
    /** Match times (manual 10.1, 10.4). */
    final double autoSec, teleopSec;
    /** Points (manual table 10-2). */
    final double leave, parkAuto, parkTeleop, tipAuto, tipTeleop, inCell, bottomNectar, inOwnedFlower, inGarden, majorFoul;

    /** One alliance's pieces and zones (names in the field file). */
    static final class Alliance {
        final String name, nectar, loadingZone, garden;

        Alliance(String name, Map<String, Object> m) {
            this.name = name;
            nectar = SimJsonReader.string(m, "nectar", "");
            loadingZone = SimJsonReader.string(m, "loadingZone", "");
            garden = SimJsonReader.string(m, "garden", "");
        }
    }

    final Map<String, Alliance> alliances = new LinkedHashMap<>();

    /** FLOWERs (manual 9.7, 10.5.2, G410, G418). */
    final boolean flowerStack;
    final double scoringZMin, scoringZMax, lowerRingTop, lowerRingHole, retrievalHeight, gap, nectarLastSec;

    /** How long a HIVE takes to tip, seconds (0: at once). */
    final double tipSeconds;

    /** What counts as a clean shot into a CELL (the turret simulator's judge()). */
    static final class CleanEntry {
        final double minDescentDeg, frameAllowanceIn;
        final List<String> frameElements = new ArrayList<>();

        CleanEntry(Map<String, Object> m) {
            minDescentDeg = SimJsonReader.number(m, "minDescentDeg", 10);
            frameAllowanceIn = SimJsonReader.number(m, "frameAllowanceIn", 1);
            for (Object o : SimJsonReader.array(m.getOrDefault("frameElements", Collections.emptyList()), "frameElements")) frameElements.add(String.valueOf(o));
        }
    }

    final CleanEntry cleanEntry;
    /** A GARDEN piece's centre must be lower than this (inches). */
    final double gardenMaxZ;

    SimRules(String text, String what) {
        Map<String, Object> root = SimJsonReader.object(SimJsonReader.parse(text), what);
        Map<String, Object> match = obj(root, "match");
        autoSec = SimJsonReader.number(match, "autoSec", 30);
        teleopSec = SimJsonReader.number(match, "teleopSec", 120);
        Map<String, Object> pts = obj(root, "points");
        leave = SimJsonReader.number(pts, "leave", 0);
        parkAuto = SimJsonReader.number(pts, "parkAuto", 0);
        parkTeleop = SimJsonReader.number(pts, "parkTeleop", 0);
        tipAuto = SimJsonReader.number(pts, "tipAuto", 0);
        tipTeleop = SimJsonReader.number(pts, "tipTeleop", 0);
        inCell = SimJsonReader.number(pts, "inCell", 0);
        bottomNectar = SimJsonReader.number(pts, "bottomNectar", 0);
        inOwnedFlower = SimJsonReader.number(pts, "inOwnedFlower", 0);
        inGarden = SimJsonReader.number(pts, "inGarden", 0);
        majorFoul = SimJsonReader.number(pts, "majorFoul", 0);
        Map<String, Object> al = obj(root, "alliances");
        for (Map.Entry<String, Object> e : al.entrySet()) {
            if (e.getValue() instanceof Map) alliances.put(e.getKey(), new Alliance(e.getKey(), SimJsonReader.object(e.getValue(), e.getKey())));
        }
        Map<String, Object> fl = obj(root, "flowers");
        flowerStack = Boolean.TRUE.equals(fl.get("stack"));
        scoringZMin = SimJsonReader.number(fl, "scoringZMinIn", 0);
        scoringZMax = SimJsonReader.number(fl, "scoringZMaxIn", 0);
        lowerRingTop = SimJsonReader.number(fl, "lowerRingTopIn", 0);
        lowerRingHole = SimJsonReader.number(fl, "lowerRingHoleIn", 0);
        retrievalHeight = SimJsonReader.number(fl, "retrievalHeightIn", Double.POSITIVE_INFINITY);
        gap = SimJsonReader.number(fl, "gapIn", 0);
        nectarLastSec = SimJsonReader.number(fl, "nectarLastSec", Double.NaN);
        tipSeconds = Math.max(0, SimJsonReader.number(obj(root, "hives"), "tipSeconds", 0));
        cleanEntry = new CleanEntry(obj(root, "cleanEntry"));
        gardenMaxZ = SimJsonReader.number(obj(root, "garden"), "maxZIn", 6);
    }

    /** The alliance ("red", "blue") whose NECTAR this piece type is, or null (POLLEN). */
    String nectarOf(String pieceType) {
        for (Alliance a : alliances.values()) if (a.nectar.equals(pieceType)) return a.name;
        return null;
    }

    private static Map<String, Object> obj(Map<String, Object> root, String key) {
        return root.get(key) instanceof Map ? SimJsonReader.object(root.get(key), key) : Collections.<String, Object>emptyMap();
    }
}
