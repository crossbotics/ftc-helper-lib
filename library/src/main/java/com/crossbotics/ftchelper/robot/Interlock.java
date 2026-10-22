package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An interlock rule: two parts that must never be in the same space at the same time. "While the lift
 * is up, the turret stays out of the back" is written once in {@code Robot.java}, and the library keeps
 * to it every loop, in TeleOp and autonomous, whoever moves the part (a button, a stick, an action, a
 * following turret, a step).
 *
 * <pre>{@code
 * // Robot.java, in define(), after the parts:
 * keepApart("TurretLift", turret.zone(), lift.zoneAbove());
 *
 * // Tunables.java: the rule's numbers, in each part's own units
 * public static class TurretLift {
 *     public static double turretFrom = 154, turretTo = 218;   // degrees: the turret's zone
 *     public static double liftAbove = 620;                    // counts: the lift's zone
 *     public static double turretMargin = 4, liftMargin = 60;  // optional: extra safety distances
 *     public static double turretStopSec = 0.2;                // optional: seconds to stop (default 0.15)
 * }
 * }</pre>
 *
 * <p><b>The rule:</b> the two parts are never both in their zones. Whoever is in its zone first keeps it;
 * the other part waits outside its own zone until the first one leaves.
 * <ul>
 *   <li>A part that wants to go into its zone (a target inside it, or a stick pushing toward it) goes, if
 *       the other part is out of its zone. The other part is then held out of its zone.</li>
 *   <li>A held part stops at a safe distance from its zone (two margins) and waits there. Its target is
 *       kept: it goes on by itself when the other part leaves. The Driver Station says so: "Turret held
 *       at 150 deg: Lift is above 620 counts".</li>
 *   <li>A turret whose target is on the other side of its zone goes the other way round if its range
 *       allows (e.g. a range of -190 to 190, or a slip ring); else it waits at the edge.</li>
 *   <li>Both want in at the same moment: the first part named in the rule goes first.</li>
 *   <li>Both already in their zones (at START, or after one was pushed by hand): neither may go
 *       deeper. Each may only move OUT. As soon as one is out, the other keeps its zone. The Driver
 *       Station says "both in their zones: move one out".</li>
 *   <li>A part whose position is not known (a turret not homed yet; a servo before its first command, or
 *       while it is still moving) counts as "maybe anywhere it could be": in its zone, if the zone is in
 *       reach. So the other part waits.</li>
 *   <li>And the other way round: a part whose position is not known does not move at all (no homing,
 *       no stick, no first servo move) while the other part is in its zone, since it could sweep through
 *       it. The Driver Station says "Turret waits (not homed yet): Lift is above 620 counts; move Lift out
 *       of its zone first". It goes as soon as the other part is out.</li>
 * </ul>
 *
 * <p><b>Stop distance</b> (motor parts: a turret, lift or arm): a moving motor does not stop at once. It
 * goes on until the next loop (which may be slow: a camera, a busy hub; and its position is from the last
 * loop, so count that one too), then it coasts while it brakes. So every loop the rule measures the part's
 * speed (from its encoder) and works out how far it would still go if told to stop now: speed x (2 x the
 * slowest of the last 25 loops + half its {@code <part>StopSec}, default 0.15 s). A part that is kept out
 * may only use the power that lets it stop in time: it slows down near its waiting point and brakes as
 * soon as that distance would take it past it. A part that could not stop before its zone counts as
 * already in it (the other part waits). A servo needs none: it is never sent past the fence.
 *
 * <p><b>Margins</b> ({@code <part>Margin}, in its units; defaults: 3 degrees or twice the turret's
 * tolerance, twice a lift's or arm's tolerance (at least 1/50 of its range), 0.02 for a servo, 1/50 of a
 * hood's range): a part counts as in its zone within one margin of it, and a held part waits two margins
 * away. The stop distance already covers coasting: the margin is extra safety (an encoder that slips, a
 * part that bends).
 *
 * <p><b>Measured, not guessed:</b> motors use their encoders every loop. A servo cannot say where it is,
 * so it counts as anywhere between where it was and where it was sent, until its {@code travelSec}
 * (Tunables) says it has arrived.
 *
 * <p><b>Checked at INIT</b> (the OpMode stops with the reason): the rule's Tunables section and every
 * number it needs; a zone that is empty (from is not below to), outside the part's range, or covering
 * its whole range (the part could never be out of it); a negative margin; a part that is not on the
 * robot, or one with no position (an intake roller: use {@link Zone#when}); a stop time outside 0 to 2 s.
 *
 * <p><b>Not changeable live.</b> The numbers are read once at INIT and are not in the tuning menu: a zone
 * that moves while the parts move could suddenly put both in their zones. Change them in
 * {@code Tunables.java}, then INIT again.
 *
 * <p>Software only: it does not replace hard stops, and a part pushed by hand (or by another robot)
 * can still end up in its zone. The rule then only lets it move out.
 *
 * Docs: docs/modules/robot.md (section "Interlock"), docs/guides/interlocks.md
 */
public final class Interlock {
    /** The default {@code <part>StopSec}: a motor part in BRAKE stops in about this long (seconds). */
    public static final double DEFAULT_STOP_SEC = 0.15;

    private final String name;
    final Zone a, b;
    // Who may be in its zone now (a, b or null = nobody yet).
    private Zone holder = null;
    // The Driver Station line this loop ("" = nothing to say).
    private String message = "";
    // Set at INIT.
    private boolean built = false;
    // Set at INIT: the part of this rule that is not on the robot (Module.optional), or null. The rule is then off.
    private Module<?> offBecause = null;

    Interlock(String name, Zone a, Zone b) {
        this.name = name;
        this.a = a;
        this.b = b;
    }

    /** The rule's name: its section in {@code Tunables.java}. */
    public String name() {
        return name;
    }

    /** True while the rule is holding a part back (it wants to go further but must wait). */
    public boolean isHolding() {
        return !message.isEmpty();
    }

    /** What it is doing, for the Driver Station ("" when nothing is held). */
    public String status() {
        return message;
    }

    /** The rule in words: "Turret between 154 and 218 deg / Lift above 620 counts". */
    public String words() {
        return label(a) + " " + a.words() + " / " + label(b) + " " + b.words();
    }

    private static String label(Zone z) {
        return z.part == null ? "when" : z.part.name();
    }

    // ----------------------------- INIT: read and check the numbers -----------------------------

    void build(TunableSection t, RobotDefinition robot) {
        for (Zone z : new Zone[] {a, b}) {
            if (z.part == null) continue;
            if (z.part.robot != robot) {
                throw new IllegalStateException("Robot.java: interlock \"" + name + "\": " + z.part.name()
                        + " is not on the robot (add(...) it before keepApart)");
            }
        }
        // A part that is not on the robot yet cannot hit anything: the rule is off (no numbers needed).
        for (Zone z : new Zone[] {a, b}) {
            if (z.part != null && z.part.missing) {
                offBecause = z.part;
                return;
            }
        }
        if (!t.exists()) {
            throw new IllegalStateException("Tunables." + name + " is missing: the interlock rule \"" + name + "\" needs its numbers. "
                    + "Add: public static class " + name + " { " + example() + " }");
        }
        for (Zone z : new Zone[] {a, b}) read(z, t);
        built = true;
    }

    // "public static double turretFrom = ..., turretTo = ...; public static double liftAbove = ...;"
    private String example() {
        StringBuilder sb = new StringBuilder();
        for (Zone z : new Zone[] {a, b}) {
            String[] f = z.fields();
            if (f.length == 0) continue;
            sb.append("public static double ");
            for (int i = 0; i < f.length; i++) sb.append(i == 0 ? "" : ", ").append(f[i]).append(" = ...");
            sb.append("; ");
        }
        return sb.toString().trim();
    }

    private void read(Zone z, TunableSection t) {
        if (z.kind == Zone.Kind.WHEN) return;
        Module<?> p = z.part;
        for (String f : z.fields()) {
            if (!t.has(f)) {
                throw new IllegalStateException("Tunables." + name + "." + f + " is missing (the zone of " + p.name() + ", in "
                        + unitWords(p) + "). The rule needs: " + example());
            }
        }
        String pre = z.prefix();
        double min = p.zoneMin(), max = p.zoneMax();
        switch (z.kind) {
            case ABOVE:
                z.lo = t.number(pre + "Above", 0);
                z.hi = Double.POSITIVE_INFINITY;
                break;
            case BELOW:
                z.lo = Double.NEGATIVE_INFINITY;
                z.hi = t.number(pre + "Below", 0);
                break;
            default:
                z.lo = t.number(pre + "From", 0);
                z.hi = t.number(pre + "To", 0);
                if (!(z.lo < z.hi)) {
                    throw new IllegalStateException("Tunables." + name + ": " + pre + "From (" + z.lo + ") must be less than "
                            + pre + "To (" + z.hi + "): the zone is empty"
                            + (p.zoneWraps() ? " (a turret zone across 180 is written like 154 to 218, or -206 to -142)" : ""));
                }
        }
        if (Double.isNaN(z.lo) || Double.isNaN(z.hi)) throw new IllegalStateException("Tunables." + name + ": a zone number is not a number");
        double margin = t.has(pre + "Margin") ? t.number(pre + "Margin", 0) : p.zoneDefaultMargin();
        if (!(margin >= 0)) throw new IllegalStateException("Tunables." + name + "." + pre + "Margin must be 0 or more (it is " + margin + ")");
        z.margin = margin;
        // How long it takes to stop once its power is cut (a motor part; servos never coast past the fence).
        double stopSec = t.has(pre + "StopSec") ? t.number(pre + "StopSec", DEFAULT_STOP_SEC) : DEFAULT_STOP_SEC;
        if (!(stopSec >= 0) || stopSec > 2) {
            throw new IllegalStateException("Tunables." + name + "." + pre + "StopSec must be 0 to 2 seconds (it is " + stopSec + ")");
        }
        z.stopSec = stopSec;
        // In reach at all? And not the whole range (then the part could never be out of it)?
        boolean reach = false, everywhere = false;
        for (double[] iv : intervals(z)) {
            if (iv[1] >= min && iv[0] <= max) reach = true;
            if (iv[0] <= min && iv[1] >= max) everywhere = true;
        }
        if (!reach) {
            throw new IllegalStateException("Tunables." + name + ": " + p.name() + "'s zone (" + z.words() + ") is outside its range ("
                    + bare(min, p.zoneUnit()) + " to " + num(max, p.zoneUnit()) + "): it can never be there. Check the numbers and the units ("
                    + unitWords(p) + ")");
        }
        if (everywhere) {
            throw new IllegalStateException("Tunables." + name + ": " + p.name() + "'s zone (" + z.words() + ") covers its whole range ("
                    + bare(min, p.zoneUnit()) + " to " + num(max, p.zoneUnit()) + "): it could never move out of it");
        }
    }

    private static String unitWords(Module<?> p) {
        String u = p.zoneUnit();
        return u.isEmpty() ? "servo position, 0 to 1" : u;
    }

    /** The INIT line: the rule in words, with its margins (or why it is off). */
    String initLine() {
        if (offBecause != null) return "Interlock " + name + ": off (" + offBecause.name() + " is not on the robot)";
        return "Interlock " + name + ": " + words();
    }

    /** True if the rule is off because one of its parts is not on the robot ({@link Module#optional()}). */
    public boolean isOff() {
        return offBecause != null;
    }

    // ----------------------------- every loop -----------------------------

    // A zone's intervals in the part's units: a turret's repeats every 360 degrees near where it can be.
    static List<double[]> intervals(Zone z) {
        List<double[]> out = new ArrayList<>();
        if (z.kind == Zone.Kind.WHEN) return out;
        if (z.kind == Zone.Kind.BETWEEN && z.part.zoneWraps()) {
            double min = z.part.zoneMin(), max = z.part.zoneMax();
            // A slip-ring turret: near where it is now.
            double centre = Double.isInfinite(min) || Double.isInfinite(max) ? mid(z.part.zoneSweep()) : (min + max) / 2;
            if (Double.isNaN(centre) || Double.isInfinite(centre)) centre = 0;
            double shift = 360.0 * Math.round((centre - (z.lo + z.hi) / 2) / 360.0);
            for (int k = -3; k <= 3; k++) out.add(new double[] {z.lo + shift + 360.0 * k, z.hi + shift + 360.0 * k});
            return out;
        }
        out.add(new double[] {z.lo, z.hi});
        return out;
    }

    private static double mid(double[] s) {
        return (s[0] + s[1]) / 2;
    }

    // In its zone (within one margin), or maybe in it (a sweep that overlaps it).
    static boolean occupies(Zone z) {
        if (z.kind == Zone.Kind.WHEN) return z.condition.getAsBoolean();
        double[] s = z.part.zoneSweep();
        for (double[] iv : intervals(z)) {
            if (s[1] >= iv[0] - z.margin && s[0] <= iv[1] + z.margin) return true;
        }
        return false;
    }

    // Coming in: where it is, plus how far it would still go if told to stop now (its stop distance),
    // reaches its zone (within one margin). A fast part that cannot stop before its zone counts as in it
    // for "who goes first", so the other part does not start into its own zone meanwhile.
    static boolean coming(Zone z) {
        if (z.kind == Zone.Kind.WHEN) return occupies(z);
        double[] s = z.part.zoneSweep();
        double lo = s[0] - z.stopDown, hi = s[1] + z.stopUp;
        for (double[] iv : intervals(z)) {
            if (hi >= iv[0] - z.margin && lo <= iv[1] + z.margin) return true;
        }
        return false;
    }

    // Wants to go in: a target inside it, a target on the far side of it (the way there crosses the zone:
    // a lift at 0 going to 1000 through a zone from 400 to 600), or a stick pushing toward it.
    static boolean wants(Zone z) {
        if (z.kind == Zone.Kind.WHEN) return false;
        Module<?> p = z.part;
        double target = p.zoneTarget(), manual = p.zoneManual();
        double[] s = p.zoneSweep();
        for (double[] iv : intervals(z)) {
            if (!Double.isNaN(target) && target >= iv[0] - z.margin && target <= iv[1] + z.margin) return true;
            if (!Double.isNaN(target) && target > iv[1] && s[1] < iv[0]) return true;   // crosses it going up
            if (!Double.isNaN(target) && target < iv[0] && s[0] > iv[1]) return true;   // crosses it going down
            if (manual > 0 && iv[0] > s[1] && iv[0] < p.zoneMax()) return true;
            if (manual < 0 && iv[1] < s[0] && iv[1] > p.zoneMin()) return true;
        }
        return false;
    }

    // Kept out of its zone: {lo, hi, upPower, downPower}. lo..hi: where it may go (it waits two margins
    // from the zone, never past where it is). upPower, downPower: the most power it may use toward the zone
    // now, so a part moving fast brakes in time (see powerCap).
    static double[] blockedFence(Zone z) {
        double[] s = z.part.zoneSweep();
        double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY, up = 1, down = 1;
        double m = z.margin;
        for (double[] iv : intervals(z)) {
            if (iv[0] > s[1]) {
                double wait = iv[0] - 2 * m;
                hi = Math.min(hi, Math.max(wait, s[1]));
                up = Math.min(up, powerCap(wait - s[1] - z.stopUp, z));
            } else if (iv[1] < s[0]) {
                double wait = iv[1] + 2 * m;
                lo = Math.max(lo, Math.min(wait, s[0]));
                down = Math.min(down, powerCap(s[0] - wait - z.stopDown, z));
            }
        }
        return new double[] {lo, hi, up, down};
    }

    // The most power (0 to 1) a part may use toward its waiting point. room: how far it is from it AFTER
    // its stop distance (how far it would go if told to stop now). No room: 0 (it brakes now). Else the
    // share of a stop from full speed that still fits, so it slows down as it comes near and never gets
    // too fast to stop in time (a part whose top speed is not known yet: full power while there is room).
    static double powerCap(double room, Zone z) {
        if (!(room > 0)) return 0;
        double full = z.topSpeed * z.stopAfterSec;
        if (!(full > 0) || Double.isInfinite(full)) return 1;
        return Math.min(1, room / full);
    }

    // In its zone while it must not be: it may only move out (by the nearest way out within its range).
    static double[] outOnlyFence(Zone z) {
        double[] s = z.part.zoneSweep();
        double m = z.margin, min = z.part.zoneMin(), max = z.part.zoneMax();
        for (double[] iv : intervals(z)) {
            if (!(s[1] >= iv[0] - m && s[0] <= iv[1] + m)) continue;
            boolean down = iv[0] - m > min, up = iv[1] + m < max;
            double dDown = s[1] - (iv[0] - m), dUp = (iv[1] + m) - s[0];
            // {lo, hi, upPower, downPower}: no power deeper in.
            if (down && (!up || dDown <= dUp)) return new double[] {Double.NEGATIVE_INFINITY, s[1], 0, 1};
            if (up) return new double[] {s[0], Double.POSITIVE_INFINITY, 1, 0};
        }
        return new double[] {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1, 1};
    }

    /**
     * One loop: who may be in its zone, and each part's fence for this loop (added into {@code fences}).
     * A part whose position is not known that must not move yet is added to {@code waiting}.
     */
    void evaluate(Map<Module<?>, double[]> fences, java.util.Set<Module<?>> waiting) {
        if (!built) return;
        boolean inA = occupies(a), inB = occupies(b);
        // Coming in: in, or moving so fast toward its zone that it could not stop before it.
        boolean comingA = coming(a), comingB = coming(b);
        boolean wantA = wants(a), wantB = wants(b);
        // The holder lets go once it is out (and not coming in) and no longer wants in; it loses its turn if
        // the other part is really in its zone and it is not (yet).
        if (holder != null) {
            boolean in = holder == a ? inA : inB, want = holder == a ? wantA : wantB;
            boolean come = holder == a ? comingA : comingB, otherIn = holder == a ? inB : inA;
            if ((!come && !want) || (!in && otherIn)) holder = null;
        }
        // Both in their zones (pushed there, or found so at START): nobody keeps a zone; each may only move out.
        if (inA && inB) holder = null;
        if (holder == null) {
            if (inA && !inB) holder = a;
            else if (inB && !inA) holder = b;
            else if (!inA && !inB) {
                // A part that cannot stop before its zone goes first; else the one that wants in.
                if (comingA && !comingB) holder = a;
                else if (comingB && !comingA) holder = b;
                else holder = wantA ? a : wantB ? b : null;
            }
        }
        boolean bothIn = inA && inB;
        String msg = "";
        for (Zone z : new Zone[] {a, b}) {
            Zone other = z == a ? b : a;
            if (z.kind == Zone.Kind.WHEN) continue;
            double[] fence;
            boolean in = z == a ? inA : inB;
            if (holder == z || (!z.part.zoneKnown())) continue;          // free (or its position unknown)
            if (in) fence = outOnlyFence(z);                              // in while it must not be: only out
            else fence = blockedFence(z);                                 // kept out
            double[] f = fences.get(z.part);
            if (f == null) fences.put(z.part, fence);
            else fences.put(z.part, new double[] {Math.max(f[0], fence[0]), Math.min(f[1], fence[1]),
                    Math.min(f[2], fence[2]), Math.min(f[3], fence[3])});
            if (msg.isEmpty() && !bothIn && z.part.zoneHeld(fence[0], fence[1], z.margin)) {
                msg = z.part.name() + " held at " + num(mid(z.part.zoneSweep()), z.part.zoneUnit()) + ": " + whyWords(other);
            }
        }
        // A part whose position is not known (a turret before homing, a servo before its first move) may
        // be anywhere, so homing or a first move could sweep through the other part. It waits (does not
        // move at all) while the other part is in its zone; it may go once the other part is out. Both
        // unknown: the first part named in the rule goes first, the other waits.
        Zone waits = null;
        if (!known(a) && !known(b)) waits = b;
        else if (!known(a) && really(b)) waits = a;
        else if (!known(b) && really(a)) waits = b;
        // A target that cannot take it into its zone from anywhere it may be (a wrist sent to "folded",
        // below its zone that reaches the top of its range) needs no wait.
        if (waits != null && !mayEnter(waits)) waits = null;
        if (waits != null) waiting.add(waits.part);
        if (waits != null && moving(waits)) {
            Zone other = waits == a ? b : a;
            msg = waits.part.name() + " waits (" + waits.part.zoneUnknownWords() + "): " + whyWords(other)
                    + (other.kind == Zone.Kind.WHEN ? "" : "; move " + label(other) + " out of its zone first");
        } else if (bothIn) {
            // One of them only counts as in because its position is not known: say that, not "move one out".
            Zone unknown = !known(a) ? a : !known(b) ? b : null;
            if (unknown != null) {
                Zone other = unknown == a ? b : a;
                msg = label(other) + " held: " + whyWords(unknown);
            } else {
                msg = "both in their zones (" + label(a) + " " + a.words() + ", " + label(b) + " " + b.words()
                        + "): move one out (each may only move out)";
            }
        }
        message = msg;
    }

    // A part whose position is not known: could its next move take it into its zone? Homing or a stick
    // (no target): yes, it could go anywhere. A target: inside the zone, or the zone between the target
    // and some place it may be now (its whole range).
    private static boolean mayEnter(Zone z) {
        if (z.kind == Zone.Kind.WHEN) return false;
        double target = z.part.zoneTarget();
        if (Double.isNaN(target)) return true;
        double min = z.part.zoneMin(), max = z.part.zoneMax();
        for (double[] iv : intervals(z)) {
            double lo = iv[0] - z.margin, hi = iv[1] + z.margin;
            if (target >= lo && target <= hi) return true;
            if (target < lo && max > hi) return true;   // it may be above the zone and come down through it
            if (target > hi && min < lo) return true;   // or below it and go up through it
        }
        return false;
    }

    // Trying to move now: homing, a stick, or a target.
    private static boolean moving(Zone z) {
        return z.part.zoneHoming() || z.part.zoneManual() != 0 || !Double.isNaN(z.part.zoneTarget());
    }

    // Really in its zone (or coming in): its position is known and it is there. A servo never sent
    // anywhere (it "may be anywhere": its whole range) does not count: else neither part could ever move.
    private static boolean really(Zone z) {
        if (z.kind == Zone.Kind.WHEN) return z.condition.getAsBoolean();
        double[] s = z.part.zoneSweep();
        boolean anywhere = s[0] <= z.part.zoneMin() + 1e-9 && s[1] >= z.part.zoneMax() - 1e-9;
        return z.part.zoneKnown() && !anywhere && (occupies(z) || coming(z));
    }

    private static boolean known(Zone z) {
        return z.kind == Zone.Kind.WHEN || z.part.zoneKnown();
    }

    private static String whyWords(Zone other) {
        if (other.kind == Zone.Kind.WHEN) return other.words;
        // Not known (a turret not homed, a servo not sent anywhere yet): it counts as anywhere in its range,
        // so the other part waits. Say why, so nobody thinks the robot is stuck.
        if (!other.part.zoneKnown()) {
            return other.part.name() + (other.part.zoneUnit().equals("deg") ? " angle" : " position") + " unknown ("
                    + other.part.zoneUnknownWords() + ")";
        }
        return other.part.name() + " is " + other.words();
    }

    /** The Driver Station line (only while something is held). */
    void addTelemetry(RobotTelemetry t) {
        if (!message.isEmpty()) t.line(name, message);
    }

    // "152 deg", "620 counts", "0.45" (a servo position).
    static String num(double v, String unit) {
        String n = bare(v, unit);
        return unit.isEmpty() || Double.isInfinite(v) ? n : n + " " + unit;
    }

    // The number alone, rounded for its unit: "152", "0.45".
    static String bare(double v, String unit) {
        if (Double.isInfinite(v)) return v > 0 ? "the top" : "the bottom";
        String n = unit.isEmpty() ? String.format(Locale.US, "%.2f", v) : String.format(Locale.US, "%.0f", v);
        return n.equals("-0") ? "0" : n;
    }

    // ----------------------------- the robot's rules, every loop -----------------------------

    /**
     * Every rule of a robot, run once per loop after the actions and before the parts: works out each
     * part's fence and hands it over. A part in no rule is never touched.
     */
    static final class Rules implements Subsystem {
        // How many loop times to remember (the slowest of them is "how long until we can act again").
        static final int LOOPS_KEPT = 25;
        // The longest loop time counted (seconds): one long pause (INIT to START) must not count for ever.
        static final double LONGEST_LOOP_SEC = 0.5;
        // A part's speed is measured over at least this long (seconds), so very fast loops (a count or
        // two of encoder noise over 3 ms) do not make up a big speed.
        static final double SPEED_OVER_SEC = 0.02;

        final List<Interlock> rules = new ArrayList<>();
        // Every part in a rule (its fence is set every loop: free if no rule holds it).
        private final List<Module<?>> parts = new ArrayList<>();
        // Where we get the time (nanoseconds). Tests swap in a pretend clock.
        java.util.function.LongSupplier clock = System::nanoTime;
        private long lastNanos = Long.MIN_VALUE;
        private final double[] loopSecs = new double[LOOPS_KEPT];
        private int loopCount = 0;
        // Each part's recent positions, for its speed.
        private final Map<Module<?>, Track> tracks = new IdentityHashMap<>();

        void add(Interlock r) {
            rules.add(r);
            for (Zone z : new Zone[] {r.a, r.b}) if (z.part != null && !parts.contains(z.part)) parts.add(z.part);
        }

        @Override
        public void update() {
            if (rules.isEmpty()) return;
            long now = clock.getAsLong();
            if (lastNanos != Long.MIN_VALUE && now > lastNanos) {
                loopSecs[loopCount % LOOPS_KEPT] = Math.min(LONGEST_LOOP_SEC, (now - lastNanos) / 1e9);
                loopCount++;
            }
            lastNanos = now;
            double loop = slowestLoopSec();
            // Each part's speed (its units per second, + = toward zoneMax), then each zone's stop distance:
            // how far it would still go if told to stop now. It is only told at the next loop (wait up to
            // the slowest recent loop at this speed), then it brakes (about half its stopSec at this speed,
            // slowing evenly).
            Map<Module<?>, Double> speeds = new IdentityHashMap<>();
            for (Module<?> p : parts) speeds.put(p, speedOf(p, now));
            for (Interlock r : rules) {
                for (Zone z : new Zone[] {r.a, r.b}) {
                    if (z.part == null || Double.isNaN(z.stopSec)) continue;
                    double v = speeds.get(z.part);
                    // Its position is from the last loop's reading: it has gone on since (one more loop).
                    z.stopAfterSec = 2 * loop + z.stopSec / 2;
                    z.stopUp = Math.max(0, v) * z.stopAfterSec;
                    z.stopDown = Math.max(0, -v) * z.stopAfterSec;
                    // Its top speed: the motor's (a full battery gives about 15% more), or the fastest seen.
                    double top = z.part.zoneTopSpeed();
                    Track t = tracks.get(z.part);
                    double seen = t == null ? 0 : t.fastest;
                    z.topSpeed = Double.isNaN(top) ? seen : Math.max(top * 1.15, seen);
                }
            }
            Map<Module<?>, double[]> fences = new IdentityHashMap<>();
            java.util.Set<Module<?>> waiting = java.util.Collections.newSetFromMap(new IdentityHashMap<Module<?>, Boolean>());
            for (Interlock r : rules) r.evaluate(fences, waiting);
            for (Module<?> p : parts) {
                double[] f = fences.get(p);
                if (f == null) p.zoneFence(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1, 1);
                else p.zoneFence(f[0], f[1], f[2], f[3]);
                p.zoneWait(waiting.contains(p));
            }
        }

        /** The slowest of the recent loops, seconds (0 before two loops). */
        double slowestLoopSec() {
            double max = 0;
            for (int i = 0; i < Math.min(loopCount, LOOPS_KEPT); i++) max = Math.max(max, loopSecs[i]);
            return max;
        }

        // A part's speed now, from its encoder: (where it is - where it was) / the time between, over at
        // least SPEED_OVER_SEC. 0 for a part that does not coast (a servo) or whose position is not known.
        private double speedOf(Module<?> p, long now) {
            Track t = tracks.get(p);
            if (t == null) tracks.put(p, t = new Track());
            if (!p.zoneCoasts() || !p.zoneKnown()) {
                t.count = 0;
                return 0;
            }
            double[] s = p.zoneSweep();
            double pos = (s[0] + s[1]) / 2;
            if (Double.isNaN(pos) || Double.isInfinite(pos)) {
                t.count = 0;
                return 0;
            }
            return t.add(now, pos);
        }

        @Override
        public void stop() { }

        @Override
        public void addTelemetry(RobotTelemetry t) {
            for (Interlock r : rules) r.addTelemetry(t);
        }

        @Override
        public String name() {
            return "Interlocks";
        }
    }

    // One part's recent positions and their times, for its speed.
    static final class Track {
        private static final int KEPT = 16;
        private final long[] nanos = new long[KEPT];
        private final double[] pos = new double[KEPT];
        int count = 0;
        // The fastest it has been measured (units per second, either way).
        double fastest = 0;

        // Adds this loop's position; gives the speed (units per second) since the newest earlier position
        // that is at least SPEED_OVER_SEC old (or the oldest one kept), 0 if there is none yet.
        double add(long now, double p) {
            nanos[count % KEPT] = now;
            pos[count % KEPT] = p;
            count++;
            int kept = Math.min(count, KEPT);
            for (int back = 1; back < kept; back++) {
                int i = (count - 1 - back) % KEPT;
                double secs = (now - nanos[i]) / 1e9;
                if (secs >= Rules.SPEED_OVER_SEC || back == kept - 1) {
                    double v = secs > 0 ? (p - pos[i]) / secs : 0;
                    fastest = Math.max(fastest, Math.abs(v));
                    return v;
                }
            }
            return 0;
        }
    }
}
