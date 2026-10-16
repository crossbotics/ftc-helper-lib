package io.github.jasoncross.ftchelper.mechanism;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import io.github.jasoncross.ftchelper.auto.AutoStep;
import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.hardware.ServoSetup;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.tuning.Tunable;
import io.github.jasoncross.ftchelper.util.MathUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * A normal (positional) servo with named positions: a claw, a wrist, a hood, a gate.
 * Can also work in degrees, move slowly, and tell (by estimate) when it has arrived.
 *
 * <pre>{@code
 * PositionServo claw = PositionServo.create(hardwareMap, "claw")
 *         .position("OPEN", 0.20)
 *         .position("CLOSED", 0.75)
 *         .travelTime(0.3);                       // seconds for a full 0..1 move (measure it)
 *
 * controls.bindPress(Pad.G2, Btn.RB, () -> claw.toggle("OPEN", "CLOSED"));
 * routine.step("Grab", claw.goToStep("CLOSED"));  // waits until it should be closed
 *
 * PositionServo hood = PositionServo.create(hardwareMap, "hood")
 *         .degrees(0.10, 0, 0.90, 25);            // servo 0.10 = 0 deg, 0.90 = 25 deg (measured)
 * hood.setDegrees(18);
 * }</pre>
 *
 * <p>Positions are raw servo positions (0 to 1). A servo cannot report where it really is, so
 * "arrived" is an estimate from {@link #travelTime}. Docs: docs/modules/mechanism.md
 */
public class PositionServo implements Subsystem {
    private final String name;
    private final Servo servo;
    private LongSupplier clockNanos = System::nanoTime;
    private final Map<String, Double> positions = new LinkedHashMap<>();
    private final Map<String, Tunable> tunables = new LinkedHashMap<>();
    private double min = 0, max = 1;

    private double posA = 0, degA = 0, posB = 1, degB = 180;
    private boolean hasDegrees = false;
    private double travelTimeSec = 0.5;
    private double maxSpeed = 0;    // positions per second; 0 = full speed

    private double target = Double.NaN;
    private String targetName = null;
    private double commanded = Double.NaN;
    private double doneAtSec = 0;
    private long lastNanos = -1;

    /** Gets the servo. Does not move it. */
    public static PositionServo create(HardwareMap hardwareMap, String name) {
        return new PositionServo(name, ServoSetup.get(hardwareMap, name).done());
    }

    /** Wraps a servo you already have. */
    public PositionServo(String name, Servo servo) {
        this.name = name;
        this.servo = servo;
    }

    /** For tests. */
    PositionServo withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** Position 0 and 1 swap ends. */
    public PositionServo reversed() {
        servo.setDirection(Servo.Direction.REVERSE);
        return this;
    }

    /** Never goes below {@code min} or above {@code max} (raw positions). Keeps it off the frame. */
    public PositionServo limits(double min, double max) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        return this;
    }

    /** Adds a named raw position, 0 to 1. */
    public PositionServo position(String positionName, double value) {
        positions.put(positionName, value);
        return this;
    }

    /**
     * Lets you use degrees. Measure two points: raw position {@code posA} is {@code degA} degrees,
     * raw {@code posB} is {@code degB} degrees. In between is a straight line.
     */
    public PositionServo degrees(double posA, double degA, double posB, double degB) {
        if (posA == posB || degA == degB) throw new IllegalArgumentException("degrees(): the two points must differ");
        this.posA = posA;
        this.degA = degA;
        this.posB = posB;
        this.degB = degB;
        hasDegrees = true;
        return this;
    }

    /** Seconds the servo needs for a full 0..1 move. Used to guess when it has arrived. Default 0.5. */
    public PositionServo travelTime(double seconds) {
        travelTimeSec = Math.abs(seconds);
        return this;
    }

    /** Moves slowly: at most this many raw positions per second (e.g. 0.5). 0 = full speed. */
    public PositionServo maxSpeed(double positionsPerSecond) {
        maxSpeed = Math.abs(positionsPerSecond);
        return this;
    }

    /** Puts every named position in the tuning menu as "prefix/NAME". Call it LAST. */
    public PositionServo tunable(String prefix) {
        for (Map.Entry<String, Double> e : positions.entrySet()) {
            tunables.put(e.getKey(), Tunable.number(prefix + "/" + e.getKey(), e.getValue(), 0.01).range(0, 1));
        }
        return this;
    }

    // ---------------------------------------------------------------------------------------

    /** Goes to a named position. */
    public void goTo(String positionName) {
        Double v = valueOf(positionName);
        if (v == null) throw new IllegalArgumentException("No position called \"" + positionName + "\" on " + name);
        moveTo(v, positionName);
    }

    /** Goes to a raw position (0 to 1, kept inside the limits). */
    public void set(double position) {
        moveTo(position, null);
    }

    /** Goes to an angle (needs {@link #degrees}). */
    public void setDegrees(double deg) {
        if (!hasDegrees) throw new IllegalStateException(name + ": call degrees(...) in the setup first");
        set(posA + (deg - degA) * (posB - posA) / (degB - degA));
    }

    /** Goes to {@code b} if at (or going to) {@code a}, else to {@code a}. */
    public void toggle(String a, String b) {
        goTo(a.equals(targetName) ? b : a);
    }

    /** The next named position in the order you added them (wraps around). */
    public void next() {
        cycle(1);
    }

    /** The previous named position (wraps around). */
    public void previous() {
        cycle(-1);
    }

    @Override
    public void update() {
        long nowN = clockNanos.getAsLong();
        double dt = lastNanos < 0 ? 0 : (nowN - lastNanos) / 1e9;
        lastNanos = nowN;
        if (Double.isNaN(target)) return;
        double next = target;
        if (maxSpeed > 0 && !Double.isNaN(commanded)) {
            double step = maxSpeed * dt;
            next = commanded + MathUtil.clamp(target - commanded, -step, step);
        }
        write(next);
    }

    @Override
    public void stop() {
        // Servos hold their last position; nothing to stop.
    }

    /** True when the servo should have arrived (an estimate). */
    public boolean isDone() {
        return !Double.isNaN(target) && commanded == target && clockNanos.getAsLong() / 1e9 >= doneAtSec;
    }

    /** A step: go to a named position and wait until it should have arrived. */
    public AutoStep goToStep(String positionName) {
        return ctx -> {
            goTo(positionName);
            ctx.waitUntil(() -> { update(); return isDone(); }, travelTimeSec * 2 + 1);
        };
    }

    /** The raw position sent last (NaN before the first move). */
    public double getPosition() { return commanded; }

    /** The angle sent last (needs {@link #degrees}). */
    public double getDegrees() {
        return degA + (commanded - posA) * (degB - degA) / (posB - posA);
    }

    /** The named position it is at or going to, or "-" . */
    public String getTargetName() { return targetName == null ? "-" : targetName; }

    public Servo getServo() { return servo; }
    @Override public String name() { return name; }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        t.line(name, getTargetName());
        t.debug(name + " pos", "%.3f%s", commanded, hasDegrees ? String.format(java.util.Locale.US, " (%.1f deg)", getDegrees()) : "");
    }

    // ---------------------------------------------------------------------------------------

    private void moveTo(double position, String positionName) {
        double clamped = MathUtil.clamp(position, min, max);
        double from = Double.isNaN(commanded) ? clamped : commanded;
        target = clamped;
        targetName = positionName;
        double moveTime = maxSpeed > 0 ? Math.abs(clamped - from) / maxSpeed : 0;
        // Unknown start (first move): assume a full move.
        double distance = Double.isNaN(commanded) ? 1 : Math.abs(clamped - from);
        doneAtSec = clockNanos.getAsLong() / 1e9 + Math.max(moveTime, distance * travelTimeSec);
        if (maxSpeed == 0 || Double.isNaN(commanded)) write(clamped);
    }

    private void write(double position) {
        if (position != commanded) {
            servo.setPosition(position);
            commanded = position;
        }
    }

    private Double valueOf(String positionName) {
        Tunable t = tunables.get(positionName);
        return t != null ? t.get() : positions.get(positionName);
    }

    private void cycle(int direction) {
        List<String> names = new ArrayList<>(positions.keySet());
        if (names.isEmpty()) return;
        int i = names.indexOf(targetName);
        int next = i < 0 ? 0 : (i + direction + names.size()) % names.size();
        goTo(names.get(next));
    }
}
