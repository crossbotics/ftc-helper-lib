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
 * Jam detection needs the motor's encoder cable. Docs: docs/modules/mechanism.md
 */
public class SpinMotor implements Subsystem {

    /** What it is doing. */
    public enum State { STOPPED, IN, OUT, UNJAMMING, MANUAL }

    private final String name;
    private final DcMotorEx motor;
    private LongSupplier clockNanos = System::nanoTime;
    private double inPower = 1.0, outPower = -1.0;
    private JamDetector jam = null;
    private double unjamSec = 0.4;
    private boolean useCurrent = false;

    private State state = State.STOPPED;
    private double manualPower = 0;
    private double unjamUntil = 0;
    private int jams = 0;
    private double sentPower = Double.NaN;

    /** Gets the motor (coast, plain power) and wraps it. */
    public static SpinMotor create(HardwareMap hardwareMap, String name) {
        return new SpinMotor(name, MotorSetup.get(hardwareMap, name).coast().runWithoutEncoder().done());
    }

    /** Wraps a motor you already set up. */
    public SpinMotor(String name, DcMotorEx motor) {
        this.name = name;
        this.motor = motor;
    }

    /** For tests. */
    SpinMotor withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** Positive power spins the other way. */
    public SpinMotor reversed() {
        MotorSetup.of(motor).reversed().done();
        return this;
    }

    /** Powers for IN and OUT (OUT is usually negative and a bit gentler). */
    public SpinMotor powers(double in, double out) {
        inPower = in;
        outPower = out;
        return this;
    }

    /**
     * Turns on jam detection while IN.
     *
     * @param minSpeed  slowest normal speed while running, ticks per second
     * @param jamSec    how long too slow before it counts as a jam
     * @param unjamSec  how long to run OUT to clear it, then IN again
     */
    public SpinMotor jamDetection(double minSpeed, double jamSec, double unjamSec) {
        jam = new JamDetector(minSpeed, jamSec);
        this.unjamSec = unjamSec;
        return this;
    }

    /** Also counts a jam when the current is above this (amps). Reading current costs a little loop time. */
    public SpinMotor jamCurrent(double amps) {
        if (jam == null) jam = new JamDetector(0, 0.3);
        jam.setMaxCurrent(amps);
        useCurrent = true;
        return this;
    }

    /**
     * Spin IN. Safe to call every loop: if it is already in (or clearing a jam), nothing changes.
     * So you can write {@code if (reverse) out(); else if (on) in(); else stop();} each loop.
     */
    public void in() {
        if (state == State.IN || state == State.UNJAMMING) return;
        state = State.IN;
        if (jam != null) jam.reset();
    }

    /** Spin OUT. Safe to call every loop. */
    public void out() { state = State.OUT; }

    /** Stop. Safe to call every loop. */
    @Override public void stop() { state = State.STOPPED; write(0); }

    /** IN if it was not running IN, else STOPPED. */
    public void toggleIn() { if (state == State.IN || state == State.UNJAMMING) stop(); else in(); }

    /** OUT if it was not running OUT, else STOPPED. */
    public void toggleOut() { if (state == State.OUT) stop(); else out(); }

    /** Any power you like, -1 to 1 (e.g. from a trigger). 0 = stop. */
    public void setPower(double power) {
        manualPower = power;
        state = power == 0 ? State.STOPPED : State.MANUAL;
    }

    @Override
    public void update() {
        double now = clockNanos.getAsLong() / 1e9;
        switch (state) {
            case IN:
                write(inPower);
                if (jam != null) {
                    double amps = useCurrent ? motor.getCurrent(CurrentUnit.AMPS) : 0;
                    if (jam.update(inPower, motor.getVelocity(), amps, now)) {
                        jams++;
                        state = State.UNJAMMING;
                        unjamUntil = now + unjamSec;
                        write(outPower);
                    }
                }
                break;
            case UNJAMMING:
                write(outPower);
                if (now >= unjamUntil) {
                    state = State.IN;
                    jam.reset();
                }
                break;
            case OUT: write(outPower); break;
            case MANUAL: write(manualPower); break;
            default: write(0);
        }
    }

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
            ctx.waitFor(seconds);
            stop();
        };
    }

    public State getState() { return state; }
    /** How many jams were cleared since the OpMode started. */
    public int getJamCount() { return jams; }
    public boolean isRunning() { return state != State.STOPPED; }
    public DcMotorEx getMotor() { return motor; }
    @Override public String name() { return name; }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (state == State.UNJAMMING) t.warn(name + " jammed: backing out");
        t.line(name, state.name());
        t.debug(name + " speed", "%.0f ticks/s  jams %d", motor.getVelocity(), jams);
    }

    private void write(double power) {
        if (power != sentPower) {
            motor.setPower(power);
            sentPower = power;
        }
    }
}
