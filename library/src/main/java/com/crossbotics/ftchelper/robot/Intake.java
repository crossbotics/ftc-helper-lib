package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import java.util.ArrayList;
import java.util.List;

/**
 * A roller that pulls game pieces in (and pushes them out): one motor, or one or more continuous
 * rotation (CR) servos.
 *
 * <pre>{@code
 * intake = add(Intake.motor("intake"));                              // Robot.java
 * intake = add(Intake.crServos("left_intake", "right_intake").reversed("right_intake"));
 * intake = add(Intake.motor("intake").onAtStart());                  // runs from START until stopped
 * intake = add(Intake.motor("intake").stopsWhen(() -> pieces.isFull()));   // no 5th piece (see Pieces)
 * }</pre>
 *
 * <p>{@code Tunables.Intake}: {@code inPower} (default 1.0) and {@code outPower} (default 0.7).
 * Suggested buttons: hold G2 RT to run it in, hold G2 LT to push out. With {@link #onAtStart()}:
 * G2 RT switches it on and off, and holding LT pushes out, then goes back to what it was doing.
 * With {@link #stopsWhen}: holding RT for 1 s runs it in anyway (the count was wrong), and the Driver
 * Station warns when it has been held back for more than 2 s.
 *
 * Docs: docs/modules/robot.md (section "Intake")
 */
public final class Intake extends Module<Intake> implements Toggleable {
    private final boolean servos;
    private final String[] names;
    private final List<String> reversed = new ArrayList<>();
    private final List<String> extraCrServos = new ArrayList<>();
    private boolean onAtStart = false;
    private int holds = 0;                // 0 = not given

    private final List<DcMotorEx> motors = new ArrayList<>();
    private final List<CRServo> crServos = new ArrayList<>();
    private Tunable inPower, outPower;
    private boolean on = false;           // running in
    private boolean out = false;          // running out (until stop or in)
    private boolean reversing = false;    // pushing out while a button is held
    private double lastPower = Double.NaN;
    // While this is true it does not run IN (out still works): e.g. pieces.whenFull(). null = never.
    private java.util.function.BooleanSupplier stopIn = null;
    // inAnyway(): runs in although stopsWhen says no, until stop() or out().
    private boolean ignoreStop = false;
    // The suggested in-button: since when it is held (nanoseconds; -1 = not held).
    private long inHeldSince = -1;
    // Since when stopsWhen has held it back (nanoseconds; -1 = not held back).
    private long heldBackSince = -1;
    /** Held this long, the in-button runs it in anyway; held back this long, the Driver Station warns. */
    static final double OVERRIDE_SEC = 1.0, WARN_SEC = 2.0;
    java.util.function.LongSupplier clock = System::nanoTime;

    private Intake(boolean servos, String... names) {
        super(names[0]);
        this.servos = servos;
        this.names = names.clone();
    }

    /** An intake on one motor. */
    public static Intake motor(String name) {
        return new Intake(false, name);
    }

    /** An intake on one or more CR servos (they run together). The Tunables section is the first name's. */
    public static Intake crServos(String... names) {
        if (names.length == 0) throw new IllegalArgumentException("Intake.crServos needs at least one name");
        return new Intake(true, names);
    }

    /**
     * CR servos that run together with the intake motor (for example two side wheels next to a
     * roller). They read the same Tunables section.
     *
     * @param crServoNames their configuration names
     */
    public Intake withCrServos(String... crServoNames) {
        for (String n : crServoNames) extraCrServos.add(n);
        return this;
    }

    /** These run the other way (for example the right one of two facing servos). */
    public Intake reversed(String... deviceNames) {
        for (String n : deviceNames) reversed.add(n);
        return this;
    }

    /** Reverses the only device (same as {@code reversed(itsName)}). */
    public Intake reversed() {
        reversed.add(device);
        return this;
    }

    /**
     * How many game pieces it can hold at once (for the simulator; default: a guess of 3).
     *
     * @param pieces e.g. 4
     */
    public Intake holds(int pieces) {
        if (pieces < 1) throw new IllegalArgumentException("Intake.holds: at least 1");
        holds = pieces;
        return this;
    }

    /**
     * Never runs IN while {@code condition} is true (pushing out still works), e.g.
     * {@code Intake.motor("intake").stopsWhen(() -> pieces.isFull())}: it will not pull in one piece
     * too many. It runs in again by itself once the condition is false (if it is still switched on).
     *
     * @param condition checked every loop; must be quick
     */
    public Intake stopsWhen(java.util.function.BooleanSupplier condition) {
        stopIn = condition;
        return this;
    }

    /** True while the {@link #stopsWhen} condition holds it back from running in. */
    public boolean isHeldBack() {
        return on && !reversing && !out && !ignoreStop && stopIn != null && stopIn.getAsBoolean();
    }

    /**
     * Runs in even though {@link #stopsWhen} says no, until {@link #stop()} or {@link #out()}: for when
     * the count is wrong (it says full, but the robot is not). The suggested in-button does this when
     * held for 1 s. The drivers are then responsible for the game's limit.
     */
    public void inAnyway() {
        in();
        ignoreStop = true;
    }

    /** Starts running in at START (in autonomous and TeleOp), until stopped. */
    public Intake onAtStart() {
        onAtStart = true;
        return this;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        inPower = t.tunable("inPower", 1.0, 0.05).range(0, 1);
        outPower = t.tunable("outPower", 0.7, 0.05).range(0, 1);
        for (String n : extraCrServos) {
            CRServo s = devices.get(CRServo.class, n);
            s.setDirection(reversed.contains(n) ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD);
            crServos.add(s);
        }
        for (String n : names) {
            DcMotorSimple.Direction dir = reversed.contains(n) ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD;
            if (servos) {
                CRServo s = devices.get(CRServo.class, n);
                s.setDirection(dir);
                crServos.add(s);
            } else {
                // Coast: a roller spins down freely, easier on the motor and gears.
                motors.add(MotorSetup.of(devices.get(DcMotorEx.class, n)).reversed(reversed.contains(n))
                        .coast().runWithoutEncoder().done());
            }
        }
    }

    @Override
    List<String> deviceNames() {
        List<String> all = new ArrayList<>(java.util.Arrays.asList(names));
        all.addAll(extraCrServos);
        return all;
    }

    @Override
    void start() {
        if (onAtStart) on = true;
    }

    // ----------------------------- commands -----------------------------

    /** Runs in until {@link #stop()}. */
    public void in() {
        touch();
        on = true;
        out = false;
    }

    /** Runs out until {@link #stop()} or {@link #in()}. */
    public void out() {
        touch();
        out = true;
        on = false;
        ignoreStop = false;
    }

    // The suggested in-button, every loop while held: in; held 1 s while held back, in anyway.
    private void holdIn() {
        in();
        long now = clock.getAsLong();
        if (inHeldSince < 0) inHeldSince = now;
        if (stopIn != null && (now - inHeldSince) / 1e9 >= OVERRIDE_SEC) ignoreStop = true;
    }

    /** Stops (and ends a reverse). */
    @Override
    void pause() {
        for (DcMotorEx m : motors) m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        for (DcMotorEx m : motors) m.setPower(0);
        for (CRServo s : crServos) s.setPower(0);
        lastPower = Double.NaN;   // resume sends its power again
    }

    @Override
    void resume() {
        // A roller spins down freely again (coast), and runs as it did before.
        for (DcMotorEx m : motors) m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        lastPower = Double.NaN;
    }

    @Override
    void halt() {
        on = false;
        out = false;
        ignoreStop = false;
    }

    @Override
    public void stop() {
        touch();
        on = false;
        out = false;
        reversing = false;
        ignoreStop = false;
        inHeldSince = -1;
        write(0);
    }

    /** On (in) if stopped, stopped if running. */
    @Override
    public void toggle() {
        if (on || out) stop();
        else in();
    }

    /**
     * Pushes out while a button is held: call it every loop while held, and {@link #endReverse()} on
     * release. Afterwards it goes back to what it was doing (running in, or stopped).
     */
    public void reverse() {
        touch();
        reversing = true;
    }

    /** Ends {@link #reverse()}. */
    public void endReverse() {
        reversing = false;
    }

    /**
     * True while it pushes pieces out: after {@link #out()} (until {@link #in()} or {@link #stop()}), or
     * while {@link #reverse()} is held.
     */
    public boolean isPushingOut() {
        return (out || reversing) && !missing;
    }

    /** Seconds it has been held back by {@link #stopsWhen} without a break (0 when it is not). */
    public double heldBackSec() {
        return heldBackSince < 0 ? 0 : (clock.getAsLong() - heldBackSince) / 1e9;
    }

    /** True while it runs in or out. */
    public boolean isRunning() {
        return (on || out || reversing) && !missing;
    }

    @Override
    public void update() {
        if (!isHeldBack()) heldBackSince = -1;
        else if (heldBackSince < 0) heldBackSince = clock.getAsLong();
        double power = reversing || out ? -outPower.get() : on && !isHeldBack() ? inPower.get() : 0;
        write(power);
    }

    // Sends the power only when it changes (each write takes time on the hub).
    private void write(double power) {
        if (power == lastPower) return;
        lastPower = power;
        for (DcMotorEx m : motors) m.setPower(power);
        for (CRServo s : crServos) s.setPower(power);
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (heldBackSec() > WARN_SEC) {
            t.warn(name() + ": held back by stopsWhen for " + fmt("%.0f", heldBackSec()) + " s (the count says full). "
                    + "Not really full? Hold the in-button 1 s to pull in anyway");
        }
        t.line(name(), reversing || out ? "OUT" : isHeldBack() ? "IN (held back by stopsWhen)" : on ? "IN" : "stopped");
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        Pad op = plan.operator();
        if (onAtStart && stopIn != null) {
            // A tap switches it; held 1 s it runs in even when stopsWhen says no (the count was wrong).
            op.rt.tap(this::toggle).says("in on/off").heldFor(OVERRIDE_SEC, this::inAnyway).says("in anyway (count says full)");
            op.lt.hold(this::reverse, this::endReverse).says("out (hold)");
        } else if (onAtStart) {
            op.rt.toggle(this).says("in on/off");
            op.lt.hold(this::reverse, this::endReverse).says("out (hold)");
        } else {
            op.rt.hold(this::holdIn, this::stop).says(stopIn != null ? "in (hold; 1 s: in even when full)" : "in (hold)");
            op.lt.hold(this::out, this::stop).says("out (hold)");
        }
    }

    @Override
    String checkPart(String id) {
        return servos ? Parts.servo(id) : super.checkPart(id);
    }

    @Override
    boolean drawsLooks() {
        return true;
    }

    // Its rollers spin with the motor (or the first CR servo).
    @Override
    String driverFor(Shape s, TunableSection t) {
        if (s.spins()) return (servos ? "crservo=" : "motor=") + names[0];
        throw new IllegalStateException("Robot.java: an intake spins rollers; a " + s.type
                + " shape that swings belongs to the part that swings it (a Positioner), or is .passive()");
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        for (String n : names) {
            if (servos) out.add("crservo." + n + " =" + (partId != null ? "   # " + partId : ""));
            else if (partId != null) out.add("motor." + n + " = free, part=" + partId);
            else out.add("motor." + n + " = free, part=gobilda-5203-1150   # TODO the real motor");
        }
        if (looks.isEmpty()) {
            out.add("shape." + device + " = roller, at=8,0,2, size=2x14x2, "
                    + (servos ? "crservo=" : "motor=") + names[0] + "   # TODO measure: place and size");
        } else {
            boolean roller = false;
            for (Shape s : looks) if (s.spins()) roller = true;
            if (!roller) {
                throw new IllegalStateException("Robot.java: " + name() + "'s looks(...) needs a roller "
                        + "(Shape.roller(...)): pieces come in where it spins");
            }
            lookLines(out, t);
        }
        for (String n : extraCrServos) out.add("crservo." + n + " =");
        // The pieces come in at every roller and spinner while the motor (or first CR servo) turns inward.
        out.add("collect." + device + " = shape=" + spinningShapeNames() + ", " + (servos ? "crservo=" : "motor=")
                + names[0] + ", holds=" + (holds > 0 ? holds : 3 + "   # TODO how many it holds"));
    }
}
