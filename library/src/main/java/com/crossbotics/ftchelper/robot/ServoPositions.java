package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What {@link Claw}, {@link Wrist} and {@link Positioner} share: a servo with named positions
 * (a {@link PositionServo} inside). You use one of those three; this class only holds their common
 * commands.
 *
 * <p>In {@code Tunables.java} every number is a named position (raw servo position, 0 to 1), except
 * {@code travelSec}: about how long the servo takes to get there (used by "has it arrived?").
 *
 * <p>A servo does not move until the program first tells it where to go, after START.
 */
public abstract class ServoPositions<M extends ServoPositions<M>> extends Module<M> {
    static final List<String> KNOWN = Collections.singletonList("travelSec");

    boolean reversed = false;
    PositionServo part;
    // The positions' names in the order the servo cycles through them.
    final List<String> order = new ArrayList<>();

    ServoPositions(String device) {
        super(device);
    }

    /** The servo turns the other way (0 and 1 swap). */
    public M reversed() {
        reversed = true;
        return self();
    }

    abstract Map<String, Double> defaultPositions();

    /** True to cycle through the positions lowest first (a wrist); false = as written. */
    boolean sortByValue() {
        return false;
    }

    // The positions from Tunables.java (or the defaults if it has none).
    Map<String, Double> positionsFrom(TunableSection t) {
        Map<String, Double> p = t.others(KNOWN);
        return p.isEmpty() ? defaultPositions() : p;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        Servo servo = devices.get(Servo.class, device);
        if (reversed) servo.setDirection(Servo.Direction.REVERSE);
        part = new PositionServo(name(), servo).travelTime(t.number("travelSec", 0.3));
        Map<String, Double> positions = positionsFrom(t);
        order.clear();
        order.addAll(positions.keySet());
        if (sortByValue()) {
            final Map<String, Double> v = positions;
            Collections.sort(order, (a, b) -> Double.compare(v.get(a), v.get(b)));
        }
        for (String n : order) part.position(n, positions.get(n));
        // Every position in the tuning menu ("Claw/open"), kept between 0 and 1.
        part.tunable(name());
    }

    // ----------------------------- commands -----------------------------

    /**
     * Goes to a named position from {@code Tunables.java}. Capitals do not matter. An unknown name
     * does nothing and shows a warning.
     *
     * @param position e.g. "open"
     */
    public void goTo(String position) {
        needBuilt(part);
        touch();
        String n = findName(order, position);
        part.goTo(n != null ? n : position);
    }

    /**
     * True if it should have arrived at its last position (a guess from {@code travelSec}). Always true
     * for a part that is not on the robot ({@link #optional()}): there is nothing to wait for.
     */
    public boolean isDone() {
        return part != null && (missing || part.isDone());
    }

    /** True if its last command was this position (whether or not it has arrived). */
    public boolean isAt(String position) {
        if (part == null) return false;
        String n = findName(order, position);
        return n != null && n.equals(part.getTargetName());
    }

    /** The named positions, in the order it cycles through them. */
    public List<String> positions() {
        return Collections.unmodifiableList(order);
    }

    /** The PositionServo inside, for anything this module does not offer. */
    public PositionServo part() {
        return part;
    }

    @Override
    public void update() {
        part.update();
    }

    @Override
    public void stop() {
        if (part != null) part.stop();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (part != null) part.addTelemetry(t);
    }

    // ----------------------------- interlock rules -----------------------------

    @Override boolean zoneCapable() { return true; }
    @Override String zoneUnit() { return ""; }
    @Override double zoneMin() { return 0; }
    @Override double zoneMax() { return 1; }

    // A servo cannot say where it is: anywhere between where it was and where it was sent, until its
    // travelSec has passed (its whole range before its first command).
    @Override
    double[] zoneSweep() {
        return part == null ? new double[] {0, 1} : part.getSweep();
    }

    @Override double zoneTarget() { return part == null ? Double.NaN : part.getTarget(); }

    @Override
    void zoneFence(double lo, double hi) {
        if (part != null) part.fence(lo, hi);
    }

    @Override double zoneDefaultMargin() { return 0.02; }

    static Map<String, Double> positions(Object... nameValue) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < nameValue.length; i += 2) m.put((String) nameValue[i], ((Number) nameValue[i + 1]).doubleValue());
        return m;
    }

    // A position as the simulated servo sees it: a reversed servo's 0.2 is really 0.8.
    double physical(double position) {
        return reversed ? 1 - position : position;
    }

    // "servo.claw = 0.2, part=gobilda-2000-torque": where the simulated servo starts (its first position).
    // The part given with part(...), or the default marked TODO.
    String simServoLine(TunableSection t, String part) {
        Map<String, Double> p = positionsFrom(t);
        double first = physical(p.isEmpty() ? 0.5 : p.values().iterator().next());
        if (partId != null) return fmt("servo.%s = %.2f, part=%s", device, first, partId);
        return fmt("servo.%s = %.2f, part=%s   # TODO the real servo", device, first, part);
    }

    @Override
    String checkPart(String id) {
        return Parts.servo(id);
    }

    // An arm on this servo: its angles at servo position 0 and 1, from the two named angles.
    @Override
    String driverFor(Shape s, TunableSection t) {
        if (!s.type.equals("arm")) {
            throw new IllegalStateException("Robot.java: " + name() + " is a servo: it swings an arm shape, not a " + s.type);
        }
        if (s.angles.size() < 2) {
            throw new IllegalStateException("Robot.java: " + name() + "'s arm needs its angle at two positions, e.g. "
                    + ".angle(\"stowed\", 95).angle(\"deployed\", -10)");
        }
        Map<String, Double> p = positionsFrom(t);
        java.util.Iterator<Map.Entry<String, Double>> it = s.angles.entrySet().iterator();
        Map.Entry<String, Double> a = it.next(), b = it.next();
        double va = physical(positionOf(p, a.getKey())), vb = physical(positionOf(p, b.getKey()));
        if (va == vb) {
            throw new IllegalStateException("Robot.java: " + name() + ": \"" + a.getKey() + "\" and \"" + b.getKey()
                    + "\" are the same servo position, so the arm's angles can't be worked out");
        }
        double perUnit = (b.getValue() - a.getValue()) / (vb - va);
        double pos0 = a.getValue() - perUnit * va;
        return "servo=" + device + ", pos0=" + Shape.n(pos0) + ", pos1=" + Shape.n(pos0 + perUnit);
    }

    // "intake_deploy>0.6": the servo has gone three quarters of the way from its start to this position.
    String dropWords(String position) {
        if (simSection == null) throw new IllegalStateException("Robot.java: " + name() + " is not added to the robot (add(...))");
        Map<String, Double> p = positionsFrom(simSection);
        double first = physical(p.values().iterator().next());
        double target = physical(positionOf(p, position));
        if (first == target) {
            throw new IllegalStateException("Robot.java: " + name() + "'s \"" + position + "\" is where it starts: it can't push anything over");
        }
        // 4 decimals: a threshold rounded to 2 could land past where the servo stops (0.50 -> 0.51).
        return device + (target > first ? ">" : "<") + fine(first + 0.75 * (target - first));
    }

    private static String fine(double v) {
        String s = String.format(java.util.Locale.US, "%.4f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private double positionOf(Map<String, Double> p, String position) {
        String n = findName(p.keySet(), position);
        if (n == null) {
            throw new IllegalStateException("Robot.java: " + name() + " has no position \"" + position + "\" (Tunables."
                    + name() + " has " + p.keySet() + ")");
        }
        return p.get(n);
    }
}
