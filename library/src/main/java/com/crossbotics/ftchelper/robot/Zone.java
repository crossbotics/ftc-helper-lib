package com.crossbotics.ftchelper.robot;

import java.util.function.BooleanSupplier;

/**
 * One side of an interlock rule ({@link Interlock}): the space one part must not be in while the other
 * part is in its own zone. Made in {@code Robot.java} from the part, with its numbers in the rule's
 * {@code Tunables.java} section:
 *
 * <pre>{@code
 * keepApart("TurretLift", turret.zone(), lift.zoneAbove());          // Robot.java
 *
 * public static class TurretLift {                                    // Tunables.java
 *     public static double turretFrom = 154, turretTo = 218;          // degrees: the turret's zone
 *     public static double liftAbove = 620;                           // counts: the lift's zone
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code part.zone()}: between two numbers, {@code <part>From} and {@code <part>To}.</li>
 *   <li>{@code part.zoneAbove()}: from {@code <part>Above} up (to the top of its range).</li>
 *   <li>{@code part.zoneBelow()}: from {@code <part>Below} down.</li>
 *   <li>{@link #when(String, BooleanSupplier)}: a condition, for something the library cannot move
 *       (your own sensor or device): the other part keeps out while it is true.</li>
 * </ul>
 *
 * <p>{@code <part>} is the part's Tunables section name with a small first letter ("Turret" gives
 * {@code turretFrom}, "IntakeDeploy" gives {@code intakeDeployFrom}). The numbers are in the part's own
 * units: degrees for a turret or an arm, counts for a lift, the servo position (0 to 1) for a claw,
 * wrist or positioner, the hood's scale for a hood. A turret's zone repeats every 360 degrees (a zone
 * from 154 to 218 is also -206 to -142).
 *
 * <p>Optional: {@code <part>Margin}, the safety distance (see {@link Interlock}).
 *
 * Docs: docs/modules/robot.md (section "Interlock")
 */
public final class Zone {
    enum Kind { BETWEEN, ABOVE, BELOW, WHEN }

    final Kind kind;
    final Module<?> part;          // null for a condition
    final BooleanSupplier condition;
    final String words;            // a condition's words, e.g. "the intake is out"
    // Read from Tunables at INIT (the part's units): the zone from lo to hi, and the safety margin.
    double lo = Double.NaN, hi = Double.NaN, margin = Double.NaN;

    private Zone(Kind kind, Module<?> part, BooleanSupplier condition, String words) {
        this.kind = kind;
        this.part = part;
        this.condition = condition;
        this.words = words;
    }

    static Zone of(Kind kind, Module<?> part) {
        return new Zone(kind, part, null, null);
    }

    /**
     * A condition instead of a part's position, for something the library cannot hold back (your own
     * sensor or device). While it is true, the other part of the rule keeps out of its zone (or, already
     * in it, may only move out). It never moves anything itself.
     *
     * <pre>{@code
     * keepApart("ArmBucket", arm.zoneBelow(), Zone.when("the bucket is up", () -> bucketUp.isPressed()));
     * }</pre>
     *
     * @param words     what it means, for the Driver Station ("the bucket is up")
     * @param condition true = "in its zone"; asked every loop, so it must be quick (no waiting)
     */
    public static Zone when(String words, BooleanSupplier condition) {
        if (condition == null) throw new IllegalArgumentException("Zone.when(\"" + words + "\", ...): the condition is missing");
        return new Zone(Kind.WHEN, null, condition, words == null || words.isEmpty() ? "its condition is true" : words);
    }

    /** The part, or null for a {@link #when} condition. */
    public Module<?> part() {
        return part;
    }

    /** The zone in words, e.g. "between 154 and 218 deg", "above 620 counts", "the bucket is up". */
    public String words() {
        if (kind == Kind.WHEN) return words;
        String u = part.zoneUnit();
        switch (kind) {
            case ABOVE: return "above " + Interlock.num(lo, u);
            case BELOW: return "below " + Interlock.num(hi, u);
            default: return "between " + Interlock.bare(lo, u) + " and " + Interlock.num(hi, u);
        }
    }

    // "Turret" -> "turret": the start of this zone's Tunables field names.
    String prefix() {
        String n = part.name();
        return n.isEmpty() ? n : Character.toLowerCase(n.charAt(0)) + n.substring(1);
    }

    // The Tunables fields this zone needs, for messages.
    String[] fields() {
        switch (kind) {
            case ABOVE: return new String[] {prefix() + "Above"};
            case BELOW: return new String[] {prefix() + "Below"};
            case BETWEEN: return new String[] {prefix() + "From", prefix() + "To"};
            default: return new String[0];
        }
    }
}
