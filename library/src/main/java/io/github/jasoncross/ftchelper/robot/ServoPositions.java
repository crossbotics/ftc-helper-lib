package io.github.jasoncross.ftchelper.robot;

import com.qualcomm.robotcore.hardware.Servo;
import io.github.jasoncross.ftchelper.mechanism.PositionServo;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
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

    /** True if it should have arrived at its last position (a guess from {@code travelSec}). */
    public boolean isDone() {
        return part != null && part.isDone();
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
    String simServoLine(TunableSection t, String part) {
        Map<String, Double> p = positionsFrom(t);
        double first = physical(p.isEmpty() ? 0.5 : p.values().iterator().next());
        return fmt("servo.%s = %.2f, part=%s   # TODO the real servo", device, first, part);
    }
}
