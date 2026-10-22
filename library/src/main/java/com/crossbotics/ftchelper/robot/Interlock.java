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
 *     public static double turretMargin = 4, liftMargin = 60;  // optional safety distances
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
 * </ul>
 *
 * <p><b>Margins</b> ({@code <part>Margin}, in its units; defaults: 3 degrees or twice the turret's
 * tolerance, twice a lift's or arm's tolerance (at least 1/50 of its range), 0.02 for a servo, 1/50 of a
 * hood's range): a part counts as in its zone within one margin of it, and a held part waits two margins
 * away. Make it at least how far the part moves in one loop plus how far it coasts when stopped.
 *
 * <p><b>Measured, not guessed:</b> motors use their encoders every loop. A servo cannot say where it is,
 * so it counts as anywhere between where it was and where it was sent, until its {@code travelSec}
 * (Tunables) says it has arrived.
 *
 * <p><b>Checked at INIT</b> (the OpMode stops with the reason): the rule's Tunables section and every
 * number it needs; a zone that is empty (from is not below to), outside the part's range, or covering
 * its whole range (the part could never be out of it); a negative margin; a part that is not on the
 * robot, or one with no position (an intake roller: use {@link Zone#when}).
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

    // Wants to go in: a target inside it, or a stick pushing toward it.
    static boolean wants(Zone z) {
        if (z.kind == Zone.Kind.WHEN) return false;
        Module<?> p = z.part;
        double target = p.zoneTarget(), manual = p.zoneManual();
        double[] s = p.zoneSweep();
        for (double[] iv : intervals(z)) {
            if (!Double.isNaN(target) && target >= iv[0] - z.margin && target <= iv[1] + z.margin) return true;
            if (manual > 0 && iv[0] > s[1] && iv[0] < p.zoneMax()) return true;
            if (manual < 0 && iv[1] < s[0] && iv[1] > p.zoneMin()) return true;
        }
        return false;
    }

    // Kept out of its zone: the range it may move in now (two margins from the zone, never past where it is).
    static double[] blockedFence(Zone z) {
        double[] s = z.part.zoneSweep();
        double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY;
        double m = z.margin;
        for (double[] iv : intervals(z)) {
            if (iv[0] > s[1]) hi = Math.min(hi, Math.max(iv[0] - 2 * m, s[1]));
            else if (iv[1] < s[0]) lo = Math.max(lo, Math.min(iv[1] + 2 * m, s[0]));
        }
        return new double[] {lo, hi};
    }

    // In its zone while it must not be: it may only move out (by the nearest way out within its range).
    static double[] outOnlyFence(Zone z) {
        double[] s = z.part.zoneSweep();
        double m = z.margin, min = z.part.zoneMin(), max = z.part.zoneMax();
        for (double[] iv : intervals(z)) {
            if (!(s[1] >= iv[0] - m && s[0] <= iv[1] + m)) continue;
            boolean down = iv[0] - m > min, up = iv[1] + m < max;
            double dDown = s[1] - (iv[0] - m), dUp = (iv[1] + m) - s[0];
            if (down && (!up || dDown <= dUp)) return new double[] {Double.NEGATIVE_INFINITY, s[1]};
            if (up) return new double[] {s[0], Double.POSITIVE_INFINITY};
        }
        return new double[] {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
    }

    /**
     * One loop: who may be in its zone, and each part's fence for this loop (added into {@code fences}).
     */
    void evaluate(Map<Module<?>, double[]> fences) {
        if (!built) return;
        boolean inA = occupies(a), inB = occupies(b);
        boolean wantA = wants(a), wantB = wants(b);
        // The holder lets go once it is out and no longer wants in; it loses its turn if the other part is
        // really in its zone and it is not (yet).
        if (holder != null) {
            boolean in = holder == a ? inA : inB, want = holder == a ? wantA : wantB;
            boolean otherIn = holder == a ? inB : inA;
            if (!in && (!want || otherIn)) holder = null;
        }
        // Both in their zones (pushed there, or found so at START): nobody keeps a zone; each may only move out.
        if (inA && inB) holder = null;
        if (holder == null) {
            if (inA && !inB) holder = a;
            else if (inB && !inA) holder = b;
            else if (!inA && !inB) holder = wantA ? a : wantB ? b : null;
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
            else fences.put(z.part, new double[] {Math.max(f[0], fence[0]), Math.min(f[1], fence[1])});
            if (msg.isEmpty() && !bothIn && z.part.zoneHeld(fence[0], fence[1], z.margin)) {
                msg = z.part.name() + " held at " + num(mid(z.part.zoneSweep()), z.part.zoneUnit()) + ": " + whyWords(other);
            }
        }
        if (bothIn) {
            msg = "both in their zones (" + label(a) + " " + a.words() + ", " + label(b) + " " + b.words()
                    + "): move one out (each may only move out)";
        }
        message = msg;
    }

    private static String whyWords(Zone other) {
        if (other.kind == Zone.Kind.WHEN) return other.words;
        if (!other.part.zoneKnown()) return other.part.name() + "'s position is not known yet (" + other.part.zoneUnknownWords() + ")";
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
        final List<Interlock> rules = new ArrayList<>();
        // Every part in a rule (its fence is set every loop: free if no rule holds it).
        private final List<Module<?>> parts = new ArrayList<>();

        void add(Interlock r) {
            rules.add(r);
            for (Zone z : new Zone[] {r.a, r.b}) if (z.part != null && !parts.contains(z.part)) parts.add(z.part);
        }

        @Override
        public void update() {
            if (rules.isEmpty()) return;
            Map<Module<?>, double[]> fences = new IdentityHashMap<>();
            for (Interlock r : rules) r.evaluate(fences);
            for (Module<?> p : parts) {
                double[] f = fences.get(p);
                if (f == null) p.zoneFence(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
                else p.zoneFence(f[0], f[1]);
            }
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
}
