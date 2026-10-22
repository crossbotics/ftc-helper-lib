package com.crossbotics.ftchelper.mechanism;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.auto.AutoStep;
import com.crossbotics.ftchelper.hardware.ServoSetup;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.util.MathUtil;
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
 * (docs/images/servo-range.svg shows the 0 to 1 range).
 */
public class PositionServo implements AngleDevice {
    // "implements AngleDevice" (a Subsystem that goes to an angle): this class has update/stop/addTelemetry/name like every mechanism,
    // so it can go in a Subsystems group. "final" below means set once and never replaced.
    private final String name;
    private final Servo servo;
    // Where we get the time from, in nanoseconds (billionths of a second). Tests swap in a fake clock.
    // "System::nanoTime" is a method reference: "ask System.nanoTime() whenever you need the time".
    private LongSupplier clockNanos = System::nanoTime;
    // Named positions in the order you added them (next() and previous() use that order).
    private final Map<String, Double> positions = new LinkedHashMap<>();
    private final Map<String, Tunable> tunables = new LinkedHashMap<>();
    // The allowed range of raw positions (see limits(...)).
    private double min = 0, max = 1;

    // Two measured points for degrees: raw posA is degA degrees, raw posB is degB degrees.
    private double posA = 0, degA = 0, posB = 1, degB = 180;
    private boolean hasDegrees = false;
    private double travelTimeSec = 0.5;
    private double maxSpeed = 0;    // positions per second; 0 = full speed

    private String warning = "";
    // NaN ("not a number") means "none yet": no target was ever given / nothing sent yet.
    // A slow move never steps more than this much time at once (see update()).
    private static final double MAX_STEP_SEC = 0.1;
    private double target = Double.NaN;      // where we want it to end up
    private String targetName = null;
    private double commanded = Double.NaN;   // the position sent to the servo last
    private double doneAtSec = 0;            // the time when we guess it will have arrived
    // Estimate of where the servo REALLY is (it moves at a limited speed).
    private double moveFrom = Double.NaN;
    private double moveStartSec = 0;
    private long lastNanos = -1;             // the time of the last update(), for slow moves; -1 = never

    /** Gets the servo. Does not move it. */
    public static PositionServo create(HardwareMap hardwareMap, String name) {
        // "static": called on the class (PositionServo.create(...)) to make a new one.
        return new PositionServo(name, ServoSetup.get(hardwareMap, name).done());
    }

    /** Wraps a servo you already have. */
    public PositionServo(String name, Servo servo) {
        this.name = name;
        this.servo = servo;
    }

    /** For tests: use a pretend clock so tests do not have to wait for real time. */
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
        // Math.min/max: works even if the two numbers are given the wrong way round.
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
        // Two equal points make no line (and would divide by zero later), so stop with a clear message.
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

    /**
     * Puts every named position in the tuning menu as "prefix/NAME" (steps of 0.01, kept 0 to 1).
     * Call it LAST. See docs/modules/tuning.md.
     */
    public PositionServo tunable(String prefix) {
        for (Map.Entry<String, Double> e : positions.entrySet()) {
            tunables.put(e.getKey(), Tunable.number(prefix + "/" + e.getKey(), e.getValue(), 0.01).range(0, 1));
        }
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Commands (quick: safe to call from buttons, never wait)
    // ---------------------------------------------------------------------------------------

    /** Goes to a named position. An unknown name (a typo) does nothing and shows a warning. */
    public void goTo(String positionName) {
        Double v = valueOf(positionName);
        // A typo must not stop the OpMode in a match: just warn and do nothing.
        if (v == null) {
            warning = "no position called \"" + positionName + "\" (known: " + positions.keySet() + ")";
            return;
        }
        moveTo(v, positionName);
    }

    /** "" or the last command problem (unknown position name). */
    public String getWarning() {
        return warning;
    }

    /** Goes to a raw position (0 to 1, kept inside the limits). */
    public void set(double position) {
        moveTo(position, null);
    }

    /** Goes to an angle in degrees (needs {@link #degrees}). */
    @Override
    public void setDegrees(double deg) {
        if (!hasDegrees) throw new IllegalStateException(name + ": call degrees(...) in the setup first");
        // Straight line through the two measured points: how far deg is from degA, scaled to raw positions.
        set(posA + (deg - degA) * (posB - posA) / (degB - degA));
    }

    /** Goes to {@code b} if at (or going to) {@code a}, else to {@code a}. */
    public void toggle(String a, String b) {
        // "x ? y : z" means: if x is true use y, otherwise use z.
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

    // ---------------------------------------------------------------------------------------
    // Every loop
    // ---------------------------------------------------------------------------------------

    /**
     * Once per loop. At full speed there is nothing to do (the servo was already told where to go).
     * For a slow move ({@link #maxSpeed}), it sends a position a little closer to the target each loop.
     */
    @Override
    public void update() {
        // How many seconds since the last update (0 the first time), so the slow move has the same
        // speed whether the loop runs fast or slow.
        long nowN = clockNanos.getAsLong();
        double dt = lastNanos < 0 ? 0 : (nowN - lastNanos) / 1e9;
        // After a pause in updates (a menu, a help page), do not jump: move at most one normal
        // loop's worth (0.1 s) this time, so a slow move stays slow.
        dt = Math.min(dt, MAX_STEP_SEC);
        lastNanos = nowN;
        // No target yet: leave the servo alone.
        if (Double.isNaN(target)) return;
        double next = target;
        if (maxSpeed > 0 && !Double.isNaN(commanded)) {
            // Slow move: step at most (speed x time) toward the target this loop.
            double step = maxSpeed * dt;
            next = commanded + MathUtil.clamp(target - commanded, -step, step);
        }
        write(next);
    }

    @Override
    public void stop() {
        // Servos hold their last position; nothing to stop.
    }

    /**
     * True when the servo should have arrived (an estimate: a servo cannot tell us where it is).
     * We guess from the time since the move started and {@link #travelTime}.
     */
    @Override
    public boolean isDone() {
        // All three must be true: there is a target, the slow move has sent the final position,
        // and enough time has gone by for the servo to get there.
        return !Double.isNaN(target) && commanded == target && clockNanos.getAsLong() / 1e9 >= doneAtSec;
    }

    /** A step (autonomous): go to a named position and wait until it should have arrived. */
    public AutoStep goToStep(String positionName) {
        // "ctx -> { ... }" is a small piece of code handed over to run later, when the routine reaches it.
        return ctx -> {
            goTo(positionName);
            // We call update() here too, so a slow move keeps going even if this servo is not in the
            // routine's list of mechanisms. The time limit (twice the full travel time + 1 s) stops a
            // wrong setting from freezing the autonomous.
            ctx.waitUntil(() -> { update(); return isDone(); }, travelTimeSec * 2 + 1);
        };
    }

    /** The raw position sent last (NaN before the first move). */
    public double getPosition() { return commanded; }

    /** The angle sent last, in degrees (needs {@link #degrees}; without it the number means nothing). */
    public double getDegrees() {
        // The same straight line as setDegrees, the other way round: raw position to degrees.
        return degA + (commanded - posA) * (degB - degA) / (posB - posA);
    }

    /**
     * The angle it is GOING to, in degrees (after the limits), or NaN before the first command.
     * Differs from {@link #getDegrees()} only during a slow move ({@link #maxSpeed}).
     */
    @Override
    public double getTargetDegrees() {
        if (!hasDegrees || Double.isNaN(target)) return Double.NaN;
        return degA + (target - posA) * (degB - degA) / (posB - posA);
    }

    /** The named position it is at or going to, or "-" . */
    public String getTargetName() { return targetName == null ? "-" : targetName; }

    /** The FTC servo inside, for anything this helper does not do. */
    public Servo getServo() { return servo; }
    @Override public String name() { return name; }

    /** Shows the target name; in debug mode also the raw position (and degrees, if set up). */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!warning.isEmpty()) t.warn(name + ": " + warning);
        t.line(name, getTargetName());
        // Nothing sent yet (before START): "-", not NaN.
        if (Double.isNaN(commanded)) t.debug(name + " pos", "-");
        else t.debug(name + " pos", "%.3f%s", commanded, hasDegrees ? String.format(java.util.Locale.US, " (%.1f deg)", getDegrees()) : "");
    }

    // ---------------------------------------------------------------------------------------
    // Small helpers used only inside this class
    // ---------------------------------------------------------------------------------------

    // Starts a move: works out where it starts from and when it should be done.
    private void moveTo(double position, String positionName) {
        // Keep it inside the limits so it never pushes against the frame (a stalled servo gets hot).
        double clamped = MathUtil.clamp(position, min, max);
        targetName = positionName;
        // The same target again (e.g. called every loop): keep the move that is under way.
        if (clamped == target) return;
        double now = clockNanos.getAsLong() / 1e9;
        // First move ever: we have no idea where the servo is (it does not tell us).
        boolean unknown = Double.isNaN(commanded);
        // Start from our best guess of where it is right now, which may be part-way through the last move.
        double from = unknown ? clamped : estimatedPosition(now);
        target = clamped;
        moveFrom = from;
        moveStartSec = now;
        // Time for a slow move (distance / speed), 0 at full speed.
        double moveTime = maxSpeed > 0 ? Math.abs(clamped - from) / maxSpeed : 0;
        // Unknown start (first move): assume a full move.
        double distance = unknown ? 1 : Math.abs(clamped - from);
        // Done after the slow move or the servo's own travel time, whichever is longer.
        doneAtSec = now + Math.max(moveTime, distance * travelTimeSec);
        // At full speed (or the first time) send it straight away; a slow move is sent bit by bit in update().
        if (maxSpeed == 0 || unknown) write(clamped);
    }

    /** Where the servo probably is now, between where the move started and the target. */
    private double estimatedPosition(double now) {
        // No move under way, or it is over: it is at the target.
        if (Double.isNaN(moveFrom) || now >= doneAtSec || doneAtSec <= moveStartSec) return target;
        // t = how far through the move we are, 0 (just started) to 1 (done); assume steady speed.
        double t = (now - moveStartSec) / (doneAtSec - moveStartSec);
        return moveFrom + (target - moveFrom) * t;
    }

    // Sends a position only when it changes: each hardware command costs loop time.
    private void write(double position) {
        if (position != commanded) {
            servo.setPosition(position);
            commanded = position;
        }
    }

    // The value of a named position: the tuning menu's value if there is one, else the setup value.
    private Double valueOf(String positionName) {
        Tunable t = tunables.get(positionName);
        if (t != null) return t.get();
        return positions.get(positionName);   // null if unknown (no unboxing here)
    }

    // Moves to the named position 1 after (or before, with -1) the current one, wrapping round at the ends.
    private void cycle(int direction) {
        List<String> names = new ArrayList<>(positions.keySet());
        if (names.isEmpty()) return;
        // indexOf gives -1 when not at a named position: then start at the first one.
        int i = names.indexOf(targetName);
        // "% size" wraps round: one past the last goes back to the first. Adding size first keeps it
        // from going negative when stepping back from the first.
        int next = i < 0 ? 0 : (i + direction + names.size()) % names.size();
        goTo(names.get(next));
    }
}
