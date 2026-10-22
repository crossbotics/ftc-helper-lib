package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.mechanism.PositionMotor;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What {@link Lift} and {@link Arm} share: a motor that goes to named positions and holds them
 * (a {@link PositionMotor} inside), with its numbers from {@code Tunables.java}. You use Lift or Arm;
 * this class only holds their common commands.
 *
 * <p>Known fields: {@code min}, {@code max} (the safe range; not changeable from the tuning menu, so
 * tuning can never drive past a hard stop), {@code kP}, {@code kI}, {@code kD}, {@code kF} (the
 * steady push that holds it up), {@code maxPower}, {@code slowZone}, {@code slowZonePower},
 * {@code tolerance}. Every other number is a named position.
 */
public abstract class MotorPositions<M extends MotorPositions<M>> extends Module<M> {
    static final List<String> KNOWN = Arrays.asList("min", "max", "kP", "kI", "kD", "kF",
            "maxPower", "slowZone", "slowZonePower", "tolerance");

    private boolean reversed = false;
    String switchName = null;
    // A second motor on the same slide or arm (alsoMotor): its name (null = none) and whether it turns the
    // other way from the first for the same power.
    String secondMotor = null;
    boolean secondReversed = false;
    PositionMotor part;
    // The positions' names in Tunables.java, lowest first (for the buttons).
    final List<String> order = new ArrayList<>();
    double min, max;
    // The tolerance in use (written, from the parts catalog or the default), for the interlock's margin.
    double toleranceUsed;

    MotorPositions(String device) {
        super(device);
    }

    /**
     * Positive power turns the other way (if it moves down when it should go up). With a second motor
     * ({@link #alsoMotor}) both turn the other way.
     */
    public M reversed() {
        reversed = true;
        return self();
    }

    /**
     * A second motor on the same lift or arm (a slide with a motor on each side, an arm driven from both
     * ends). It gets exactly the same power as the first, at the same moment: in every command, stop,
     * pause, limit and interlock rule. Only the FIRST motor's encoder is read, so plug the encoder cable
     * into the first motor. Its Tunables section stays the first motor's name ({@code lift_left} reads
     * {@code Tunables.LiftLeft}).
     *
     * <pre>{@code
     * lift = add(Lift.slide("lift_left").alsoMotor("lift_right"));          // both turn the same way
     * lift = add(Lift.slide("lift_left").alsoMotorReversed("lift_right"));  // they face each other
     * }</pre>
     *
     * <p>Which one? Take the chain or belt off, then push the lift up with the stick (Motor Finder): both
     * motors must turn so the lift would go up. If one turns the wrong way, use the other method. Two
     * motors pushing against each other get hot and can break a gearbox.
     *
     * @param motor the second motor's configuration name, e.g. "lift_right"
     * @return this part
     */
    public M alsoMotor(String motor) {
        return second(motor, false);
    }

    /**
     * {@link #alsoMotor}, with the second motor turning the other way from the first for the same power
     * (two motors that face each other, mirror images: the usual way on a two-sided slide).
     *
     * @param motor the second motor's configuration name, e.g. "lift_right"
     * @return this part
     */
    public M alsoMotorReversed(String motor) {
        return second(motor, true);
    }

    private M second(String motor, boolean otherWay) {
        if (motor == null || motor.trim().isEmpty()) throw new IllegalArgumentException("Robot.java: " + name() + ".alsoMotor needs the motor's name");
        if (motor.equals(device)) throw new IllegalArgumentException("Robot.java: " + name() + ".alsoMotor(\"" + motor + "\") is its first motor");
        if (secondMotor != null) {
            throw new IllegalStateException("Robot.java: " + name() + " already has a second motor (\"" + secondMotor
                    + "\"); a lift or arm takes two motors at most");
        }
        secondMotor = motor;
        secondReversed = otherWay;
        return self();
    }

    /** The second motor's configuration name ({@link #alsoMotor}), or null if it has one motor. */
    public String secondMotorName() {
        return secondMotor;
    }

    @Override
    List<String> notes() {
        List<String> out = super.notes();
        // Without a switch, 0 is wherever the part is at INIT (the encoder is set to 0 then).
        if (!catalogNote.isEmpty()) out.add(catalogNote);
        if (switchName == null) out.add(name() + ": 0 is where it is at INIT: start with it at its bottom stop");
        if (secondMotor != null) {
            out.add(name() + ": two motors, \"" + device + "\" (its encoder is read) and \"" + secondMotor + "\" ("
                    + (secondReversed ? "turns the other way" : "turns the same way") + ", same power)");
        }
        return out;
    }

    @Override
    List<String> deviceNames() {
        List<String> out = new ArrayList<>();
        out.add(device);
        if (secondMotor != null) out.add(secondMotor);
        if (switchName != null) out.add(switchName);
        return out;
    }

    // The library's defaults, for a module whose Tunables section is missing some numbers.
    abstract double[] defaults();   // min, max, kP, kI, kD, kF, maxPower, slowZone, slowZonePower, tolerance

    /**
     * Starting values worked out from the motor in the parts catalog, used in place of {@link #defaults()}
     * where they are not NaN (same order). null = none (the plain defaults are used).
     */
    double[] catalogDefaults() {
        return null;
    }

    /** The INIT note when the motor is not known and kP is not written ("" = none). */
    String unknownMotorNote() {
        return "";
    }

    /** The parts catalog motor the starting values come from ("gobilda-5203-435"), for the INIT note. */
    String catalogMotor() {
        return partId;
    }

    // "Lift: defaults from the catalog (...)": "" when none were used.
    private String catalogNote = "";
    static final String[] FIELD_ORDER = {"min", "max", "kP", "kI", "kD", "kF", "maxPower", "slowZone", "slowZonePower", "tolerance"};
    abstract Map<String, Double> defaultPositions();
    /** Encoder counts per unit (1 for a lift in counts, counts per degree for an arm). */
    abstract double countsPerUnit();
    abstract String unit();

    void lowerSwitchName(String name) {
        switchName = name;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        double[] d = defaults().clone();
        // Starting values from the parts catalog, where the team wrote none (a written number always wins).
        double[] cat = catalogDefaults();
        List<String> fromCatalog = new ArrayList<>();
        List<String> catalogFields = new ArrayList<>();
        if (cat != null) {
            for (int i = 0; i < d.length && i < cat.length; i++) {
                if (Double.isNaN(cat[i]) || t.has(FIELD_ORDER[i])) continue;
                d[i] = cat[i];
                fromCatalog.add(FIELD_ORDER[i] + " " + exact(cat[i]));
                catalogFields.add(FIELD_ORDER[i]);
            }
        }
        // The safe range must be the team's own numbers (from the Motor Finder), never a default.
        t.require("min", "the bottom of the safe range, in " + unit() + ": Motor Finder, X");
        t.require("max", "the top of the safe range, in " + unit() + ": Motor Finder, B");
        min = t.number("min", d[0]);
        max = t.number("max", d[1]);
        if (!(max > min)) throw new IllegalStateException("Tunables." + name() + ": max (" + max + ") must be more than min (" + min + ")");
        DcMotorEx motor = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                .brake().resetEncoder().runWithoutEncoder().done();
        part = new PositionMotor(name(), motor);
        if (secondMotor != null) {
            // The second motor: the same power as the first, always (SafeMotor sends both together). Same
            // direction setting as the first, flipped for alsoMotorReversed. Brake too, so both hold the lift.
            DcMotorEx other = MotorSetup.of(devices.get(DcMotorEx.class, secondMotor)).reversed(reversed != secondReversed)
                    .brake().runWithoutEncoder().done();
            part.getSafeMotor().alsoPowers(other);
        }
        if (countsPerUnit() != 1) part.units(countsPerUnit(), unit());
        part.limits(min, max)
                .pid(t.number("kP", d[2]), t.number("kI", d[3]), t.number("kD", d[4]), t.number("kF", d[5]))
                .maxPower(t.number("maxPower", d[6]))
                .slowZone(t.number("slowZone", d[7]), t.number("slowZonePower", d[8]))
                .tolerance(toleranceUsed = t.number("tolerance", d[9]));
        if (switchName != null) {
            final TouchSensor sw = devices.get(TouchSensor.class, switchName);
            part.lowerSwitch(sw::isPressed);
        }
        Map<String, Double> positions = t.others(KNOWN);
        if (positions.isEmpty()) positions = defaultPositions();
        for (Map.Entry<String, Double> p : positions.entrySet()) part.position(p.getKey(), p.getValue());
        // Puts kP..kF and every position in the tuning menu ("Lift/kP", "Lift/high").
        part.tunable(name());
        for (String p : positions.keySet()) {
            // A tuned position stays inside the safe range.
            Tunable.number(name() + "/" + p, positions.get(p), 1).range(min, max);
        }
        t.fromCatalog(catalogFields.toArray(new String[0]));
        catalogNote = fromCatalog.isEmpty() ? "" : defaultsNote(catalogMotor(), fromCatalog,
                "Tune them live in the tuning menu (PRACTICE, hold GUIDE)");
        if (cat == null && !unknownMotorNote().isEmpty() && !t.has("kP")) {
            catalogNote = unknownMotorNote();
            // Their values are the general ones that note names: not listed again as library defaults.
            t.fromCatalog("kP", "kD", "slowZone", "tolerance");
        }
        order.clear();
        order.addAll(positions.keySet());
        final Map<String, Double> values = positions;
        Collections.sort(order, (a, b) -> Double.compare(values.get(a), values.get(b)));
    }

    @Override
    String toolFor(String field) {
        if (field.equals("min")) return "the Motor Finder (X at the bottom stop), docs/guides/finders.md#motor-finder-limits-positions-top-speed";
        if (field.equals("max")) return "the Motor Finder (B at the top stop), docs/guides/finders.md#motor-finder-limits-positions-top-speed";
        if (KNOWN.contains(field)) return "test it, live in the tuning menu (PRACTICE, hold GUIDE)";
        return "the Motor Finder (a position: P1, P2...), docs/guides/finders.md#motor-finder-limits-positions-top-speed";
    }

    // ----------------------------- commands -----------------------------

    /**
     * Goes to a named position from {@code Tunables.java} and holds it. Capitals do not matter.
     * An unknown name does nothing and shows a warning (a typo must not stop a match).
     *
     * @param position e.g. "high"
     */
    public void goTo(String position) {
        needBuilt(part);
        touch();
        String n = findName(order, position);
        part.goTo(n != null ? n : position);
    }

    /** Goes to the next position up (by value). */
    public void stepUp() {
        needBuilt(part);
        touch();
        part.stepUp();
    }

    /** Goes to the next position down. */
    public void stepDown() {
        needBuilt(part);
        touch();
        part.stepDown();
    }

    /**
     * Moves by hand, -1 to 1 (a stick). Call every loop; 0 holds where it was let go.
     *
     * @param power + = up (toward max)
     */
    public void manual(double power) {
        needBuilt(part);
        if (power != 0) touch();
        part.manual(power);
    }

    /** Holds where it is now. */
    public void hold() {
        needBuilt(part);
        touch();
        part.hold();
    }

    /** True when it is at its target (always, for a part that is not on the robot: see {@link #optional()}). */
    public boolean isAtTarget() {
        return part != null && (missing || part.isAtTarget());
    }

    /** True when it is at this named position (not on the robot: when it was last sent there). */
    public boolean isAt(String position) {
        if (part == null) return false;
        String n = findName(order, position);
        if (missing) return n != null && n.equals(part.getTargetName());
        return n != null && part.isAt(n);
    }

    /** Where it is now, in its units. */
    public double position() {
        return part == null ? 0 : part.getPosition();
    }

    /** The named positions, lowest first. */
    public List<String> positions() {
        return Collections.unmodifiableList(order);
    }

    /** The PositionMotor inside, for anything this module does not offer. */
    public PositionMotor part() {
        return part;
    }

    @Override
    void pause() {
        // Power 0 now; it is on brake already. Its target stays, so resume goes on toward it.
        if (part != null) part.getSafeMotor().stop();
    }

    @Override
    void halt() {
        if (part != null) part.hold();   // stay at this height
    }

    @Override
    public void update() {
        part.update();
    }

    @Override
    public void stop() {
        if (part == null) return;
        touch();
        part.stop();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (part != null) part.addTelemetry(t);
    }

    // ----------------------------- interlock rules -----------------------------

    @Override boolean zoneCapable() { return true; }
    @Override String zoneUnit() { return unit(); }
    @Override double zoneMin() { return min; }
    @Override double zoneMax() { return max; }
    @Override boolean zoneKnown() { return part != null && !part.isWaitingForHoming(); }
    @Override String zoneUnknownWords() { return "not homed on its switch yet"; }

    @Override
    double[] zoneSweep() {
        if (!zoneKnown()) return new double[] {min, max};
        double p = part.getPosition();
        return new double[] {p, p};
    }

    @Override double zoneTarget() { return part == null ? Double.NaN : part.getTarget(); }
    @Override double zoneManual() { return part == null ? 0 : part.getManualPower(); }

    @Override
    void zoneFence(double lo, double hi) {
        if (part != null) part.fence(lo, hi);
    }

    @Override
    void zoneFence(double lo, double hi, double upPower, double downPower) {
        if (part != null) part.fence(lo, hi, upPower, downPower);
    }

    // Not homed yet (its position unknown) while the other part of a rule is in its zone: no power at all
    // (the stick could otherwise move it from "anywhere" into the other part).
    @Override
    void zoneWait(boolean wait) {
        if (part != null && wait) part.fence(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0);
    }

    // A motor coasts: the interlock measures its speed (its encoder) for the stop distance.
    @Override boolean zoneCoasts() { return true; }

    @Override
    double zoneDefaultMargin() {
        // Twice the tolerance, and at least 1/50 of the range.
        double tol = tunables == null ? 0 : toleranceUsed;
        return Math.max(2 * tol, (max - min) / 50);
    }

    // The simulator line for the second motor (alsoMotor): the same motor kind, tied to the first motor's
    // slide or arm ("with="); "mirrored" = its shaft turns the other way. Nothing without a second motor.
    final void secondMotorLine(List<String> out, String part) {
        if (secondMotor == null) return;
        out.add(fmt("motor.%s = lift, %s, with=%s%s", secondMotor, motorWords(part, "gobilda-5203-312"), device,
                secondReversed ? ", mirrored=true" : ""));
    }

    static Map<String, Double> positions(Object... nameValue) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < nameValue.length; i += 2) m.put((String) nameValue[i], ((Number) nameValue[i + 1]).doubleValue());
        return m;
    }
}
