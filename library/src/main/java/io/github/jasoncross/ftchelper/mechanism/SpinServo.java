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
    private final String name;
    private final CRServo servo;
    private LongSupplier clockNanos = System::nanoTime;
    private AnalogInput angleSensor = null;
    private double sensorMaxVolts = 3.3;
    private double power = 1.0;

    private double commanded = 0;
    private double stopAtSec = Double.NaN;
    private double sent = Double.NaN;

    /** Gets the servo (must be configured as a Continuous Rotation Servo). */
    public static SpinServo create(HardwareMap hardwareMap, String name) {
        return new SpinServo(name, Hw.get(hardwareMap, CRServo.class, name));
    }

    /** Wraps a CR servo you already have. */
    public SpinServo(String name, CRServo servo) {
        this.name = name;
        this.servo = servo;
    }

    /** For tests. */
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

    public void forward() { run(power); }
    public void backward() { run(-power); }
    @Override public void stop() { run(0); }

    /** Any power, -1 to 1. Cancels a timed run. */
    public void setPower(double p) { run(MathUtil.clamp(p, -1, 1)); }

    /** Runs at {@code p} for {@code seconds}, then stops by itself (in {@link #update()}). */
    public void runFor(double seconds, double p) {
        run(MathUtil.clamp(p, -1, 1));
        stopAtSec = clockNanos.getAsLong() / 1e9 + seconds;
    }

    @Override
    public void update() {
        if (!Double.isNaN(stopAtSec) && clockNanos.getAsLong() / 1e9 >= stopAtSec) run(0);
        if (commanded != sent) {
            servo.setPower(commanded);
            sent = commanded;
        }
    }

    /** A step: run for some seconds at a power, then stop. */
    public AutoStep runForStep(double seconds, double p) {
        return ctx -> {
            runFor(seconds, p);
            ctx.waitUntil(() -> !isRunning(), seconds + 1);
            stop();
        };
    }

    public boolean isRunning() { return commanded != 0; }
    public double getPower() { return commanded; }

    /** The angle from the sensor, 0 to 360 (NaN without a sensor). */
    public double getAngleDeg() {
        if (angleSensor == null) return Double.NaN;
        return MathUtil.clamp(angleSensor.getVoltage() / sensorMaxVolts, 0, 1) * 360.0;
    }

    @Override public String name() { return name; }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        t.line(name, commanded == 0 ? "stopped" : String.format(java.util.Locale.US, "%.2f", commanded));
        if (angleSensor != null) t.debug(name + " angle", "%.1f deg", getAngleDeg());
    }

    private void run(double p) {
        commanded = p;
        stopAtSec = Double.NaN;
        update();
    }
}
