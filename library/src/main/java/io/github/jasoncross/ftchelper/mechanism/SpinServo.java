package io.github.jasoncross.ftchelper.mechanism;

import com.qualcomm.robotcore.hardware.AnalogInput;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.auto.AutoStep;
import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.hardware.Hw;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.util.MathUtil;
import java.util.function.LongSupplier;

/**
 * A continuous-rotation servo (it spins like a small motor): a roller, a spinner, a small intake.
 * Forward, backward, stop, or run for a set time. Optional angle sensor (e.g. an Axon servo's
 * analog wire) to read where it points.
 *
 * <pre>{@code
 * SpinServo roller = SpinServo.create(hardwareMap, "roller").power(0.8);
 * controls.bindHold(Pad.G2, Btn.X, roller::forward)
 *         .bindRelease(Pad.G2, Btn.X, roller::stop);
 * routine.step("Spit out", roller.runForStep(0.5, -1));    // backward for 0.5 s
 * }</pre>
 *
 * In the robot configuration the port must be set as "Continuous Rotation Servo".
 * Docs: docs/modules/mechanism.md
 */
public class SpinServo implements Subsystem {
    // "implements Subsystem": it has update/stop/addTelemetry/name like every mechanism helper.
    private final String name;
    private final CRServo servo;
    // Where we get the time (nanoseconds). Tests swap in a fake clock.
    private LongSupplier clockNanos = System::nanoTime;
    private AnalogInput angleSensor = null;   // null = no angle sensor
    private double sensorMaxVolts = 3.3;
    private double power = 1.0;

    private double commanded = 0;              // the power we want now, -1 to 1
    private double stopAtSec = Double.NaN;     // when a timed run ends, in seconds; NaN = not a timed run
    private double sent = Double.NaN;          // the power sent to the servo last; NaN = nothing sent yet

    /** Gets the servo (must be configured as a Continuous Rotation Servo). */
    public static SpinServo create(HardwareMap hardwareMap, String name) {
        return new SpinServo(name, Hw.get(hardwareMap, CRServo.class, name));
    }

    /** Wraps a CR servo you already have. */
    public SpinServo(String name, CRServo servo) {
        this.name = name;
        this.servo = servo;
    }

    /** For tests: use a pretend clock so tests do not wait for real time. */
    SpinServo withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** Positive power spins the other way. */
    public SpinServo reversed() {
        servo.setDirection(DcMotorSimple.Direction.REVERSE);
        return this;
    }

    /** The power used by {@link #forward} and {@link #backward}, 0 to 1. Default 1. */
    public SpinServo power(double p) {
        // Always stored as 0..1; backward() adds the minus sign.
        power = MathUtil.clamp(Math.abs(p), 0, 1);
        return this;
    }

    /**
     * Adds an angle sensor (an analog input, e.g. an Axon servo's 4th wire).
     * 0 volts = 0 degrees, {@code maxVolts} = 360 degrees.
     */
    public SpinServo angleSensor(HardwareMap hardwareMap, String sensorName, double maxVolts) {
        angleSensor = Hw.get(hardwareMap, AnalogInput.class, sensorName);
        sensorMaxVolts = maxVolts;
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Commands (quick; each one is sent to the servo at once). Each cancels a timed run.
    // ---------------------------------------------------------------------------------------

    /** Spins forward at the {@link #power} power. */
    public void forward() { run(power); }
    /** Spins backward at the {@link #power} power. */
    public void backward() { run(-power); }
    /** Stops spinning. */
    @Override public void stop() { run(0); }

    /** Any power, -1 to 1. Cancels a timed run. */
    public void setPower(double p) { run(MathUtil.clamp(p, -1, 1)); }

    /** Runs at {@code p} for {@code seconds}, then stops by itself (in {@link #update()}). */
    public void runFor(double seconds, double p) {
        run(MathUtil.clamp(p, -1, 1));
        // We do not wait here: we write down WHEN to stop, and update() checks the clock every loop.
        stopAtSec = clockNanos.getAsLong() / 1e9 + seconds;
    }

    /** Once per loop: ends a timed run when its time is up, and sends the power if it changed. */
    @Override
    public void update() {
        // A timed run whose time is up: stop.
        if (!Double.isNaN(stopAtSec) && clockNanos.getAsLong() / 1e9 >= stopAtSec) run(0);
        // Only talk to the servo when the power changes: each hardware command costs loop time.
        if (commanded != sent) {
            servo.setPower(commanded);
            sent = commanded;
        }
    }

    /** A step (autonomous): run for some seconds at a power, then stop. */
    public AutoStep runForStep(double seconds, double p) {
        // "ctx -> { ... }" is a small piece of code handed over to run later, when the routine reaches it.
        return ctx -> {
            runFor(seconds, p);
            // Wait until it stopped by itself; the extra 1 s time limit is a safety net.
            // "() -> !isRunning()" is a small question the wait asks every loop.
            ctx.waitUntil(() -> !isRunning(), seconds + 1);
            // Make sure it is stopped, even if the time limit ended the wait.
            stop();
        };
    }

    /** True while the power is not 0. */
    public boolean isRunning() { return commanded != 0; }
    /** The power it is running at now, -1 to 1. */
    public double getPower() { return commanded; }

    /** The angle from the sensor, 0 to 360 (NaN without a sensor). */
    public double getAngleDeg() {
        if (angleSensor == null) return Double.NaN;
        // Volts as a share of the full range (kept 0 to 1 in case of noise), times 360 degrees.
        return MathUtil.clamp(angleSensor.getVoltage() / sensorMaxVolts, 0, 1) * 360.0;
    }

    @Override public String name() { return name; }

    /** Shows the power (or "stopped"); the angle in debug mode if there is a sensor. */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        t.line(name, commanded == 0 ? "stopped" : String.format(java.util.Locale.US, "%.2f", commanded));
        if (angleSensor != null) t.debug(name + " angle", "%.1f deg", getAngleDeg());
    }

    // Every command ends here: set the power, cancel any timed run, and send it straight away
    // (by calling update()) so a button reacts in the same loop.
    private void run(double p) {
        commanded = p;
        stopAtSec = Double.NaN;
        update();
    }
}
