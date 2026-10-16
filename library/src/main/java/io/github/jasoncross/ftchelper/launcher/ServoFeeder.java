package io.github.jasoncross.ftchelper.launcher;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import io.github.jasoncross.ftchelper.hardware.ServoSetup;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.function.LongSupplier;

/**
 * A feeder driven by a servo (a kicker or pusher arm): push, wait, come back, wait.
 *
 * <pre>{@code
 * Feeder kicker = ServoFeeder.create(hardwareMap, "kicker")
 *         .positions(0.15, 0.60)      // rest, push (raw servo positions; measure them)
 *         .times(0.15, 0.15);         // seconds to push, seconds to come back
 * }</pre>
 *
 * Docs: docs/modules/launcher.md
 */
public class ServoFeeder implements Feeder {
    private final String name;
    private final Servo servo;
    private LongSupplier clockNanos = System::nanoTime;
    private double rest = 0, push = 1;
    private double pushSec = 0.15, returnSec = 0.15;

    private int phase = 0;      // 0 idle, 1 pushing, 2 returning
    private double phaseEnd = 0;

    public static ServoFeeder create(HardwareMap hardwareMap, String name) {
        return new ServoFeeder(name, ServoSetup.get(hardwareMap, name).done());
    }

    public ServoFeeder(String name, Servo servo) {
        this.name = name;
        this.servo = servo;
    }

    ServoFeeder withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** Raw servo positions for "rest" and "push". */
    public ServoFeeder positions(double restPosition, double pushPosition) {
        rest = restPosition;
        push = pushPosition;
        return this;
    }

    /** Seconds to push, and seconds to come back before the next piece. */
    public ServoFeeder times(double pushSeconds, double returnSeconds) {
        pushSec = Math.abs(pushSeconds);
        returnSec = Math.abs(returnSeconds);
        return this;
    }

    /** Moves to rest (call in INIT only if moving there is safe). */
    public void goToRest() {
        servo.setPosition(rest);
    }

    @Override
    public void feedOne() {
        if (phase != 0) return;
        servo.setPosition(push);
        phase = 1;
        phaseEnd = now() + pushSec;
    }

    @Override
    public void update() {
        if (phase == 0 || now() < phaseEnd) return;
        if (phase == 1) {
            servo.setPosition(rest);
            phase = 2;
            phaseEnd = now() + returnSec;
        } else {
            phase = 0;
        }
    }

    @Override public boolean isBusy() { return phase != 0; }

    @Override
    public void stop() {
        if (phase != 0) servo.setPosition(rest);
        phase = 0;
    }

    @Override public String name() { return name; }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        t.debug(name, phase == 0 ? "idle" : phase == 1 ? "pushing" : "returning");
    }

    private double now() {
        return clockNanos.getAsLong() / 1e9;
    }
}
