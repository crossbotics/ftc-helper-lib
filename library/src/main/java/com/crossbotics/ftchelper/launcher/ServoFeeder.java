package com.crossbotics.ftchelper.launcher;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.hardware.ServoSetup;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
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
 * <p>A servo cannot tell us where it is, so each move is timed: {@link #times} says how long to allow.
 * No waiting: {@link #update()} checks the clock every loop. Docs: docs/modules/launcher.md
 */
public class ServoFeeder implements Feeder {
    // "implements Feeder": this class keeps the Feeder promise, so a Launcher can use it.
    private final String name;
    private final Servo servo;
    // Where we get the time (nanoseconds). Tests swap in a fake clock.
    private LongSupplier clockNanos = System::nanoTime;
    private double rest = 0, push = 1;
    private double pushSec = 0.15, returnSec = 0.15;

    private int phase = 0;      // 0 idle, 1 pushing, 2 returning
    private double phaseEnd = 0;   // when the current phase is over (seconds)

    /** Gets the servo. Does not move it. */
    public static ServoFeeder create(HardwareMap hardwareMap, String name) {
        return new ServoFeeder(name, ServoSetup.get(hardwareMap, name).done());
    }

    /** Wraps a servo you already have. */
    public ServoFeeder(String name, Servo servo) {
        this.name = name;
        this.servo = servo;
    }

    /** For tests: use a pretend clock so tests do not wait for real time. */
    ServoFeeder withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** Raw servo positions (0 to 1) for "rest" and "push". Defaults 0 and 1: set your own. */
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

    /** Starts one push. Ignored while a push or return is still going on. */
    @Override
    public void feedOne() {
        if (phase != 0) return;
        servo.setPosition(push);
        phase = 1;
        // We do not wait: we write down when the push should be done, and update() checks.
        phaseEnd = now() + pushSec;
    }

    /** Once per loop: after the push time, sends the arm back; after the return time, it is free again. */
    @Override
    public void update() {
        // Idle, or the current phase is not over yet: nothing to do this loop.
        if (phase == 0 || now() < phaseEnd) return;
        if (phase == 1) {
            // Push done: come back.
            servo.setPosition(rest);
            phase = 2;
            phaseEnd = now() + returnSec;
        } else {
            // Back at rest: ready for the next piece. (We wait for the return so the next piece
            // can drop into place and the next push starts from rest, with a full stroke.)
            phase = 0;
        }
    }

    @Override public boolean isBusy() { return phase != 0; }

    /** Sends the arm back to rest if it was out, and ends any feed. */
    @Override
    public void stop() {
        if (phase != 0) servo.setPosition(rest);
        phase = 0;
    }

    @Override public String name() { return name; }

    /** "idle", "pushing" or "returning", in debug mode. */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        t.debug(name, phase == 0 ? "idle" : phase == 1 ? "pushing" : "returning");
    }

    // The time now, in seconds.
    private double now() {
        return clockNanos.getAsLong() / 1e9;
    }
}
