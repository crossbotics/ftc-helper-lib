package com.crossbotics.ftchelper.robot;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Every Tunables field each part can read, so INIT can stop on a misspelled one ({@code inPowr} for
 * {@code inPower}) instead of quietly using the library's default. A misspelled safety number, a
 * lift's max for example, would otherwise be ignored without a word.
 *
 * <p>One list per part, the same parts as the Tunables reference (docs/guides/tunables.md).
 * tools/check_tunables.py checks that each list has exactly the fields the code reads, so a new
 * field the code reads must be added here too (CI fails otherwise).
 *
 * <p>Names the team picks are allowed too: a Lift's or a Claw's named positions (any number), a
 * piece sensor's kinds ({@code POLLEN}), a launcher's {@code speedByDistance...} tables, and an
 * interlock rule's {@code <part>From}, {@code <part>To}, {@code <part>Above}, {@code <part>Below} and
 * {@code <part>Margin} for its own two parts.
 */
final class TunableFields {
    private TunableFields() { }

    private static final Map<String, List<String>> PARTS = new LinkedHashMap<>();

    private static void part(String key, String... fields) {
        PARTS.put(key, Collections.unmodifiableList(Arrays.asList(fields)));
    }

    // tools/check_tunables.py reads these lines: one part(...) per <!-- tunables: Key --> in tunables.md.
    static {
        part("Drive", "calibrated", "maxPower", "moveMinPower", "practiceMaxPower", "slowSpeed", "strafeCorrection",
                "turnKd", "turnKp");
        part("Odometry", "angularScalar", "checked", "linearScalar", "mirrorForRed", "resetHeading", "resetX", "resetY");
        part("Aim", "cameraForwardIn", "cameraLatencyMs", "cameraLeftIn", "cameraMaxAgeMs", "cameraOdometryAgree",
                "cameraOffset", "flightSec", "flightSecPerIn", "goalX", "goalY", "maxGoalDistance", "maxLeadIn",
                "maxTurnPower", "minTurnPower", "mirrorForRed", "rumbleDeg", "shootOnMove", "shooterForwardIn",
                "shooterLeftIn", "toleranceDeg", "turnKd", "turnKp");
        part("Buttons", "holdSec", "tapGapSec");
        part("Camera", "autoLighting", "exposureMs", "gain", "lightingAfterStartMs", "lightingMinPictures",
                "lightingPictures", "lightingSettleMs", "pipeline");
        part("Hood", "angleA", "angleB", "byHandPerSec", "calibration", "homeAngle", "kP", "maxPower", "posA", "posB",
                "stepAngle", "tolerance", "travelSec", "trimMax");
        part("Indexer", "feedSec", "power", "push", "pushSec", "rest", "returnSec");
        part("Intake", "inPower", "outPower");
        part("Interlock");   // only <part>From, <part>To, ... (see interlock below)
        part("Launcher", "batteryCompensation", "idleRpm", "kD", "kF", "kI", "kP", "nominalVolts", "readyBandRpm",
                "shotIntervalSec", "speedByDistance", "speedRpm", "stepRpm", "trimMaxPercent", "trimStepPercent");
        part("LiftArm", "kD", "kF", "kI", "kP", "max", "maxPower", "min", "slowZone", "slowZonePower", "tolerance");
        part("Lights", "allianceAtInit");
        part("PieceSensor", "gain", "gapMm", "hueTolerance", "minSaturation", "nearMm", "sizeToleranceMm", "steadyMs");
        part("ServoPositions", "travelSec");
        part("Turret", "angleA", "angleB", "cameraOffset", "encoderOffset", "homeAngle", "homeDirection", "homingPower",
                "homingTimeout", "kD", "kI", "kP", "keepAngle", "max", "maxPower", "min", "outOfRangeHold", "posA", "posB",
                "slowZone", "slowZonePower", "startAngle", "tolerance", "travelSec");
        part("Pieces");      // reads nothing: its sensors have their own sections
    }

    /** The fields of one part, by its reference key ("Intake", "LiftArm"...). */
    static List<String> of(String key) {
        List<String> f = PARTS.get(key);
        if (f == null) throw new IllegalStateException("TunableFields: no list for " + key);
        return f;
    }

    /** Every part's key, for tests. */
    static Set<String> keys() {
        return Collections.unmodifiableSet(PARTS.keySet());
    }

    /** What one section may hold: these names, names the test accepts, and (true) any number as a position. */
    static final class Allowed {
        final String part;
        final Set<String> names = new LinkedHashSet<>();
        Predicate<String> also = n -> false;
        boolean positions = false;

        Allowed(String part) {
            this.part = part;
        }

        // Two parts reading one section (.tunables("Same")): either one's fields are fine.
        void add(Allowed other) {
            names.addAll(other.names);
            final Predicate<String> mine = also, theirs = other.also;
            also = n -> mine.test(n) || theirs.test(n);
            positions |= other.positions;
        }
    }

    /** What a module's section may hold. */
    static Allowed forModule(Module<?> m) {
        Allowed a = new Allowed(m.name());
        if (m instanceof MotorPositions) {
            a.names.addAll(of("LiftArm"));
            a.positions = true;                    // every other number is a named position
        } else if (m instanceof ServoPositions) {
            a.names.addAll(of("ServoPositions"));
            a.positions = true;
        } else if (m instanceof PieceSensor) {
            a.names.addAll(of("PieceSensor"));
            a.names.addAll(((PieceSensor) m).kindNames());   // POLLEN, RED_NECTAR...
        } else if (m instanceof Launcher) {
            a.names.addAll(of("Launcher"));
            a.also = n -> n.startsWith("speedByDistance");   // speedByDistancePollen...: one table per piece
        } else {
            a.names.addAll(of(m.getClass().getSimpleName()));
        }
        return a;
    }

    /** What an interlock rule's section may hold: its two parts' zone fields and margins. */
    static Allowed forInterlock(Interlock r) {
        Allowed a = new Allowed("the interlock rule \"" + r.name() + "\"");
        for (Zone z : new Zone[] {r.a, r.b}) {
            if (z.kind == Zone.Kind.WHEN) continue;
            a.names.addAll(Arrays.asList(z.fields()));
            a.names.add(z.prefix() + "Margin");
            a.names.add(z.prefix() + "StopSec");
        }
        return a;
    }

    /** A named section that is not a module (Drive, Odometry, Aim, Buttons). */
    static Allowed forSection(String key) {
        Allowed a = new Allowed(key);
        a.names.addAll(of(key));
        return a;
    }

    /**
     * Stops (an exception, shown at INIT) on the first field in the section that the part never reads.
     *
     * @param t the section
     * @param a what it may hold
     */
    static void check(TunableSection t, Allowed a) {
        for (String n : t.fieldNames()) {
            if (a.names.contains(n) || a.also.test(n)) continue;
            if (a.positions && t.isNumber(n)) continue;   // a named position (others(...) checks look-alikes)
            String like = TunableSection.lookalike(n, a.names);
            StringBuilder known = new StringBuilder();
            for (String k : a.names) known.append(known.length() == 0 ? "" : ", ").append(k);
            throw new IllegalStateException("Tunables." + t.name + "." + n + " is not a setting of " + a.part
                    + (like != null ? " (did you mean " + like + "?)" : "")
                    + ": the robot would quietly ignore it. Fix the name (capitals count) or delete the line."
                    + (known.length() == 0 ? "" : " Known: " + known)
                    + (a.positions ? ". Named positions are numbers (double)." : "")
                    + " (docs/guides/tunables.md)");
        }
    }
}
