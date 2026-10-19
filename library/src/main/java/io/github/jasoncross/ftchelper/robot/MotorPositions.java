package io.github.jasoncross.ftchelper.robot;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.TouchSensor;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.mechanism.PositionMotor;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.tuning.Tunable;
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
    PositionMotor part;
    // The positions' names in Tunables.java, lowest first (for the buttons).
    final List<String> order = new ArrayList<>();
    double min, max;

    MotorPositions(String device) {
        super(device);
    }

    /** Positive power turns the other way (if it moves down when it should go up). */
    public M reversed() {
        reversed = true;
        return self();
    }

    @Override
    List<String> deviceNames() {
        return switchName == null ? Collections.singletonList(device) : Arrays.asList(device, switchName);
    }

    // The library's defaults, for a module whose Tunables section is missing some numbers.
    abstract double[] defaults();   // min, max, kP, kI, kD, kF, maxPower, slowZone, slowZonePower, tolerance
    abstract Map<String, Double> defaultPositions();
    /** Encoder counts per unit (1 for a lift in counts, counts per degree for an arm). */
    abstract double countsPerUnit();
    abstract String unit();

    void lowerSwitchName(String name) {
        switchName = name;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        double[] d = defaults();
        min = t.number("min", d[0]);
        max = t.number("max", d[1]);
        DcMotorEx motor = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                .brake().resetEncoder().runWithoutEncoder().done();
        part = new PositionMotor(name(), motor);
        if (countsPerUnit() != 1) part.units(countsPerUnit(), unit());
        part.limits(min, max)
                .pid(t.number("kP", d[2]), t.number("kI", d[3]), t.number("kD", d[4]), t.number("kF", d[5]))
                .maxPower(t.number("maxPower", d[6]))
                .slowZone(t.number("slowZone", d[7]), t.number("slowZonePower", d[8]))
                .tolerance(t.number("tolerance", d[9]));
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
        order.clear();
        order.addAll(positions.keySet());
        final Map<String, Double> values = positions;
        Collections.sort(order, (a, b) -> Double.compare(values.get(a), values.get(b)));
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

    /** True when it is at its target. */
    public boolean isAtTarget() {
        return part != null && part.isAtTarget();
    }

    /** True when it is at this named position. */
    public boolean isAt(String position) {
        if (part == null) return false;
        String n = findName(order, position);
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

    static Map<String, Double> positions(Object... nameValue) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < nameValue.length; i += 2) m.put((String) nameValue[i], ((Number) nameValue[i + 1]).doubleValue());
        return m;
    }
}
