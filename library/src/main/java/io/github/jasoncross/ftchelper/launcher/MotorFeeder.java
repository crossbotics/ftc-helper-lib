package io.github.jasoncross.ftchelper.launcher;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.safety.JamDetector;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * A feeder driven by a motor (a roller, belt or indexer wheel): runs for a set time per piece,
 * or until a sensor sees the piece leave.
 *
 * <pre>{@code
 * Feeder feeder = MotorFeeder.create(hardwareMap, "feeder")
 *         .power(1.0)
 *         .feedTime(0.25)                         // seconds per piece
 *         .jamDetection(60, 0.2);                 // optional, needs the encoder cable
 * }</pre>
 *
 * Docs: docs/modules/launcher.md
 */
public class MotorFeeder implements Feeder {
    private final String name;
    private final DcMotorEx motor;
    private LongSupplier clockNanos = System::nanoTime;
    private double power = 1.0;
    private double feedSec = 0.25;
    private BooleanSupplier pieceGone = null;
    private JamDetector jam = null;

    private boolean busy = false;
    private boolean jammed = false;
    private double endAt = 0;
    private double startedAt = 0;

    /** Gets the motor (brake, plain power). */
    public static MotorFeeder create(HardwareMap hardwareMap, String name) {
        return new MotorFeeder(name, MotorSetup.get(hardwareMap, name).brake().runWithoutEncoder().done());
    }

    public MotorFeeder(String name, DcMotorEx motor) {
        this.name = name;
        this.motor = motor;
    }

    MotorFeeder withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    public MotorFeeder reversed() {
        MotorSetup.of(motor).reversed().brake().runWithoutEncoder().done();
        return this;
    }

    /** Power while feeding. Default 1. */
    public MotorFeeder power(double p) {
        power = p;
        return this;
    }

    /** Seconds to run per piece (also the longest it waits for the sensor). Default 0.25. */
    public MotorFeeder feedTime(double seconds) {
        feedSec = Math.abs(seconds);
        return this;
    }

    /** Stop as soon as this is true (e.g. a beam-break sensor sees the piece leave). */
    public MotorFeeder stopWhen(BooleanSupplier pieceHasLeft) {
        pieceGone = pieceHasLeft;
        return this;
    }

    /** Jam detection: slower than {@code minSpeed} ticks/s for {@code seconds} while feeding. */
    public MotorFeeder jamDetection(double minSpeed, double seconds) {
        jam = new JamDetector(minSpeed, seconds);
        return this;
    }

    @Override
    public void feedOne() {
        if (busy) return;
        double now = now();
        busy = true;
        jammed = false;
        startedAt = now;
        endAt = now + feedSec;
        if (jam != null) jam.reset();
        motor.setPower(power);
    }

    @Override
    public void update() {
        if (!busy) return;
        double now = now();
        if (jam != null && jam.update(power, motor.getVelocity(), now)) {
            jammed = true;
            finish();
            return;
        }
        // Give the sensor a moment: the piece may still be in view right after the start.
        boolean gone = pieceGone != null && now - startedAt > 0.03 && pieceGone.getAsBoolean();
        if (gone || now >= endAt) finish();
    }

    @Override public boolean isBusy() { return busy; }
    @Override public boolean isJammed() { return jammed; }

    @Override
    public void stop() {
        busy = false;
        motor.setPower(0);
    }

    /** Clears the jam flag (after you fixed it). */
    public void clearJam() {
        jammed = false;
    }

    @Override public String name() { return name; }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (jammed) t.warn(name + " JAMMED");
        t.debug(name, busy ? "feeding" : "idle");
    }

    private void finish() {
        busy = false;
        motor.setPower(0);
    }

    private double now() {
        return clockNanos.getAsLong() / 1e9;
    }
}
