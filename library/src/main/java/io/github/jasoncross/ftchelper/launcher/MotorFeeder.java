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
 * <p>The {@link Launcher} calls {@link #feedOne()}; this class then times the feed in {@link #update()},
 * without ever waiting. Docs: docs/modules/launcher.md
 */
public class MotorFeeder implements Feeder {
    // "implements Feeder": this class keeps the Feeder promise (feedOne, isBusy, update, stop...),
    // so a Launcher can use it.
    private final String name;
    private final DcMotorEx motor;
    // Where we get the time (nanoseconds). Tests swap in a fake clock.
    private LongSupplier clockNanos = System::nanoTime;
    private double power = 1.0;
    private double feedSec = 0.25;
    private BooleanSupplier pieceGone = null;   // the optional sensor question; null = run for feedSec
    private JamDetector jam = null;             // null = no jam detection

    private boolean busy = false;
    private boolean jammed = false;
    private double endAt = 0;       // when this feed ends at the latest (seconds)
    private double startedAt = 0;   // when this feed started (seconds)

    /** Gets the motor (brake, plain power). */
    public static MotorFeeder create(HardwareMap hardwareMap, String name) {
        // Brake: the roller stops quickly when the power goes to 0, so it does not keep pushing.
        return new MotorFeeder(name, MotorSetup.get(hardwareMap, name).brake().runWithoutEncoder().done());
    }

    /** Wraps a motor you already set up. */
    public MotorFeeder(String name, DcMotorEx motor) {
        this.name = name;
        this.motor = motor;
    }

    /** For tests: use a pretend clock so tests do not wait for real time. */
    MotorFeeder withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** Positive power turns the other way. */
    public MotorFeeder reversed() {
        MotorSetup.of(motor).reversed().brake().runWithoutEncoder().done();
        return this;
    }

    /** Power while feeding, -1 to 1. Default 1. */
    public MotorFeeder power(double p) {
        power = p;
        return this;
    }

    /** Seconds to run per piece (also the longest it waits for the sensor). Default 0.25. */
    public MotorFeeder feedTime(double seconds) {
        feedSec = Math.abs(seconds);
        return this;
    }

    /**
     * Stop as soon as this is true (e.g. a beam-break sensor sees the piece leave).
     * Example: {@code stopWhen(() -> beam.isBroken())}: {@code () -> ...} is a small question
     * handed over to be asked every loop.
     */
    public MotorFeeder stopWhen(BooleanSupplier pieceHasLeft) {
        pieceGone = pieceHasLeft;
        return this;
    }

    /** Jam detection: slower than {@code minSpeed} ticks/s for {@code seconds} while feeding. */
    public MotorFeeder jamDetection(double minSpeed, double seconds) {
        jam = new JamDetector(minSpeed, seconds);
        return this;
    }

    // ---------------------------------------------------------------------------------------

    /** Starts feeding one piece: the motor turns on now, and {@link #update()} turns it off. Ignored while busy. */
    @Override
    public void feedOne() {
        // Already feeding: a second call must not restart the timer.
        if (busy) return;
        double now = now();
        busy = true;
        jammed = false;
        startedAt = now;
        // We do not wait for the feed to finish: we write down WHEN it should end, and update() checks.
        endAt = now + feedSec;
        if (jam != null) jam.reset();
        motor.setPower(power);
    }

    /** Once per loop: ends the feed when the time is up, the sensor sees the piece leave, or it jams. */
    @Override
    public void update() {
        if (!busy) return;
        double now = now();
        // Barely turning while powered, for a while = jammed. Stop the motor (pushing harder could
        // break something or burn out the motor) and tell the launcher.
        if (jam != null && jam.update(power, motor.getVelocity(), now)) {
            jammed = true;
            finish();
            return;
        }
        // Give the sensor a moment: the piece may still be in view right after the start.
        // (The sensor is only asked after the first 0.03 s.)
        boolean gone = pieceGone != null && now - startedAt > 0.03 && pieceGone.getAsBoolean();
        if (gone || now >= endAt) finish();
    }

    @Override public boolean isBusy() { return busy; }
    @Override public boolean isJammed() { return jammed; }

    /** Stops the motor at once and ends any feed. (A jam stays marked until {@link #clearJam()}.) */
    @Override
    public void stop() {
        busy = false;
        motor.setPower(0);
    }

    /** Clears the jam flag. The launcher calls this when the driver asks to shoot again. */
    @Override
    public void clearJam() {
        jammed = false;
    }

    @Override public String name() { return name; }

    /** A JAMMED warning; "feeding" or "idle" in debug mode. */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (jammed) t.warn(name + " JAMMED");
        t.debug(name, busy ? "feeding" : "idle");
    }

    // Ends the feed: no longer busy, motor off.
    private void finish() {
        busy = false;
        motor.setPower(0);
    }

    // The time now, in seconds.
    private double now() {
        return clockNanos.getAsLong() / 1e9;
    }
}
