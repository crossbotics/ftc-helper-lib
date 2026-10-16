package io.github.jasoncross.ftchelper.mechanism;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.auto.AutoStep;
import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.safety.JamDetector;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.function.LongSupplier;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;

/**
 * A motor that spins: an intake, a roller, a conveyor, a spinner. IN, OUT or STOPPED, with
 * optional jam detection that backs it out by itself.
 *
 * <pre>{@code
 * SpinMotor intake = SpinMotor.create(hardwareMap, "intake")
 *         .powers(1.0, -0.6)              // in, out
 *         .jamDetection(80, 0.3, 0.4);    // < 80 ticks/s for 0.3 s = jammed; back out for 0.4 s
 *
 * controls.bindToggle(Pad.G2, Btn.A, intake::in, intake::stop)
 *         .bindHold(Pad.G2, Btn.B, intake::out)
 *         .bindRelease(Pad.G2, Btn.B, intake::stop);
 * routine.step("Intake on", intake.inStep());
 * }</pre>
 *
 * <p>Buttons only change what it is doing (its "state"); {@link #update()}, once per loop, sends the
 * power and watches for jams. Jam detection needs the motor's encoder cable (the encoder is the sensor
 * that measures how fast the shaft turns). Docs: docs/modules/mechanism.md; the jam check itself is
 * {@link JamDetector} (docs/modules/safety.md).
 */
public class SpinMotor implements Subsystem {
    // "implements Subsystem": it has update/stop/addTelemetry/name like every mechanism helper.

    /**
     * What it is doing. An "enum" is a fixed list of named choices: the state is always exactly one
     * of these. UNJAMMING = running OUT for a moment by itself to clear a jam; MANUAL = a power you set.
     */
    public enum State { STOPPED, IN, OUT, UNJAMMING, MANUAL }

    private final String name;
    private final DcMotorEx motor;
    // Where we get the time (nanoseconds). Tests swap in a fake clock.
    private LongSupplier clockNanos = System::nanoTime;
    private double inPower = 1.0, outPower = -1.0;
    private JamDetector jam = null;   // null = no jam detection
    private double unjamSec = 0.4;
    private boolean useCurrent = false;

    private State state = State.STOPPED;
    private double manualPower = 0;
    private double unjamUntil = 0;    // the time (seconds) when backing out ends
    private int jams = 0;
    private double sentPower = Double.NaN;   // the power sent last; NaN = nothing sent yet

    /** Gets the motor (coast, plain power) and wraps it. */
    public static SpinMotor create(HardwareMap hardwareMap, String name) {
        // Coast (not brake): a roller can spin down freely, which is easier on the motor and the gears.
        return new SpinMotor(name, MotorSetup.get(hardwareMap, name).coast().runWithoutEncoder().done());
    }

    /** Wraps a motor you already set up. */
    public SpinMotor(String name, DcMotorEx motor) {
        this.name = name;
        this.motor = motor;
    }

    /** For tests: use a pretend clock so tests do not wait for real time. */
    SpinMotor withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** Positive power spins the other way. */
    public SpinMotor reversed() {
        MotorSetup.of(motor).reversed().done();
        return this;
    }

    /** Powers for IN and OUT, -1 to 1 (OUT is usually negative and a bit gentler). */
    public SpinMotor powers(double in, double out) {
        inPower = in;
        outPower = out;
        return this;
    }

    /**
     * Turns on jam detection while IN. A jam = it is given power but barely turns, for a while.
     * When that happens it runs OUT for a moment to spit out what is stuck, then goes back to IN.
     *
     * @param minSpeed  slowest normal speed while running, ticks per second
     * @param jamSec    how long too slow before it counts as a jam (long enough for the motor to spin up)
     * @param unjamSec  how long to run OUT to clear it, then IN again
     */
    public SpinMotor jamDetection(double minSpeed, double jamSec, double unjamSec) {
        jam = new JamDetector(minSpeed, jamSec);
        this.unjamSec = unjamSec;
        return this;
    }

    /** Also counts a jam when the current is above this (amps). Reading current costs a little loop time. */
    public SpinMotor jamCurrent(double amps) {
        // No jamDetection(...) before this: make a detector that never trips on speed (minimum 0 ticks/s),
        // so only the current counts, for 0.3 s.
        if (jam == null) jam = new JamDetector(0, 0.3);
        jam.setMaxCurrent(amps);
        useCurrent = true;
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Commands (quick: they only change the state; update() sends the power)
    // ---------------------------------------------------------------------------------------

    /**
     * Spin IN. Safe to call every loop: if it is already in (or clearing a jam), nothing changes.
     * So you can write {@code if (reverse) out(); else if (on) in(); else stop();} each loop.
     */
    public void in() {
        // Already IN or backing out of a jam: do not restart, or calling in() every loop would
        // cut the back-out short and keep resetting the jam timer.
        if (state == State.IN || state == State.UNJAMMING) return;
        state = State.IN;
        // Fresh start for the jam timer.
        if (jam != null) jam.reset();
    }

    /** Spin OUT. Safe to call every loop. */
    public void out() { state = State.OUT; }

    /** Stop. Safe to call every loop. The 0 power is sent right away, not in the next update(). */
    @Override public void stop() { state = State.STOPPED; write(0); }

    /** IN if it was not running IN, else STOPPED. */
    public void toggleIn() { if (state == State.IN || state == State.UNJAMMING) stop(); else in(); }

    /** OUT if it was not running OUT, else STOPPED. */
    public void toggleOut() { if (state == State.OUT) stop(); else out(); }

    /** Any power you like, -1 to 1 (e.g. from a trigger). 0 = stop. No jam detection in this mode. */
    public void setPower(double power) {
        manualPower = power;
        // "a ? b : c" means: if a is true use b, otherwise use c.
        state = power == 0 ? State.STOPPED : State.MANUAL;
    }

    // ---------------------------------------------------------------------------------------
    // Every loop
    // ---------------------------------------------------------------------------------------

    /** Once per loop: sends the power for the current state and, while IN, watches for jams. Never waits. */
    @Override
    public void update() {
        double now = clockNanos.getAsLong() / 1e9;   // seconds
        // "switch" picks the block for the current state; "break" ends that block.
        switch (state) {
            case IN:
                write(inPower);
                if (jam != null) {
                    // Read the current only if asked: each extra hardware read slows the loop a little.
                    double amps = useCurrent ? motor.getCurrent(CurrentUnit.AMPS) : 0;
                    // getVelocity() = how fast it turns now, ticks per second.
                    if (jam.update(inPower, motor.getVelocity(), amps, now)) {
                        // Jammed: count it, and back out for unjamSec. We do not wait here:
                        // we write down WHEN to stop backing out and check it in later loops.
                        jams++;
                        state = State.UNJAMMING;
                        unjamUntil = now + unjamSec;
                        write(outPower);
                    }
                }
                break;
            case UNJAMMING:
                write(outPower);
                // Back-out time is over: go back IN and start the jam timer from scratch.
                if (now >= unjamUntil) {
                    state = State.IN;
                    jam.reset();
                }
                break;
            case OUT: write(outPower); break;
            case MANUAL: write(manualPower); break;
            // STOPPED (and anything else): no power.
            default: write(0);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Autonomous steps
    // ---------------------------------------------------------------------------------------

    // "ctx -> in()" below is a small piece of code handed over to run later, when the routine reaches the step.

    /** A step: start spinning IN and go straight on. */
    public AutoStep inStep() { return ctx -> in(); }

    /** A step: start spinning OUT and go straight on. */
    public AutoStep outStep() { return ctx -> out(); }

    /** A step: stop. */
    public AutoStep stopStep() { return ctx -> stop(); }

    /** A step: run OUT for some seconds, then stop (e.g. to drop a game piece). */
    public AutoStep outForStep(double seconds) {
        return ctx -> {
            out();
            // ctx.waitFor keeps every mechanism updating while it waits (never sleep()).
            ctx.waitFor(seconds);
            stop();
        };
    }

    // ---------------------------------------------------------------------------------------
    // Questions
    // ---------------------------------------------------------------------------------------

    /** What it is doing now. */
    public State getState() { return state; }
    /** How many jams were cleared since the OpMode started. */
    public int getJamCount() { return jams; }
    /** True unless STOPPED. */
    public boolean isRunning() { return state != State.STOPPED; }
    /** The FTC motor inside, for anything this helper does not do. */
    public DcMotorEx getMotor() { return motor; }
    @Override public String name() { return name; }

    /** Shows the state; a warning while it backs out of a jam; speed and jam count in debug mode. */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (state == State.UNJAMMING) t.warn(name + " jammed: backing out");
        t.line(name, state.name());
        t.debug(name + " speed", "%.0f ticks/s  jams %d", motor.getVelocity(), jams);
    }

    // Sends a power only when it changes: each hardware command costs loop time.
    private void write(double power) {
        if (power != sentPower) {
            motor.setPower(power);
            sentPower = power;
        }
    }
}
