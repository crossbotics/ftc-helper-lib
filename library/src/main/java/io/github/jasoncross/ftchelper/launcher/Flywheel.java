package io.github.jasoncross.ftchelper.launcher;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.auto.AutoStep;
import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.util.MathUtil;
import java.util.function.LongSupplier;

/**
 * A flywheel (shooter wheel) that holds a speed in RPM, says when it is ready, and notices shots.
 * (Last season's Launcher, made general.)
 *
 * <pre>{@code
 * Flywheel flywheel = Flywheel.create(hardwareMap, "flywheel")
 *         .countsPerRev(28)            // encoder counts per FLYWHEEL turn (see below)
 *         .pidf(10, 3, 0, 12)          // the hub's speed control (start here, then tune)
 *         .tolerance(75)               // within 75 RPM...
 *         .settleTime(0.15);           // ...for 0.15 s = ready
 * flywheel.setRpm(3200);
 * // every loop:
 * flywheel.update();
 * if (flywheel.isReady()) { ...feed a ball... }
 * }</pre>
 *
 * <p><b>countsPerRev</b> = encoder counts for ONE turn of the flywheel. Motor encoder on a 1:1
 * flywheel: 28 (goBILDA/REV bare motor). With gears between motor and flywheel, multiply by
 * (motor turns per flywheel turn). An encoder on the flywheel shaft plugged into this motor's
 * encoder port: that encoder's counts per turn (REV Through Bore: 8192). Check that the hub reads
 * your top speed correctly (the speed on telemetry must match a tachometer or a slow-motion video).
 *
 * <p>Speed control runs on the hub (RUN_USING_ENCODER), so it is fast and smooth. A dropping
 * battery still changes the result a little: tune the F gain with a charged battery.
 *
 * <p>RPM = revolutions (turns) per minute. The hub works in encoder counts per second, so this class
 * converts between the two. Tutorial 17 builds a shooter step by step.
 *
 * Docs: docs/modules/launcher.md
 */
public class Flywheel implements Subsystem {
    // "implements Subsystem": it has update/stop/addTelemetry/name like every mechanism helper.
    private final String name;
    private final DcMotorEx motor;
    private DcMotorEx follower = null;   // an optional second motor on the same wheel; null = none
    // Where we get the time (nanoseconds). Tests swap in a fake clock.
    private LongSupplier clockNanos = System::nanoTime;

    private double countsPerRev = 28;
    private double toleranceRpm = 75;
    private double settleSec = 0.15;
    private double maxRpm = 6000;
    private double shotDropRpm = 150;

    private double targetRpm = 0;
    private double rpm = 0;                     // the speed measured in the last update()
    private double inBandSince = Double.NaN;    // when the speed came within tolerance; NaN = it is not
    private boolean ready = false;
    private boolean armedForShot = false;       // true once ready: the next big speed drop is a shot
    private int shots = 0;
    private double lastSent = Double.NaN;       // the speed sent to the hub last (counts/s); NaN = none
    private boolean followerDisabled = false;
    private double followerBadSince = Double.NaN;
    private String fault = "";

    /** Gets the motor: coast (a flywheel must spin down freely), speed control on. */
    public static Flywheel create(HardwareMap hardwareMap, String name) {
        return new Flywheel(name, MotorSetup.get(hardwareMap, name).coast().runUsingEncoder().done());
    }

    /** Wraps a motor you already set up (RUN_USING_ENCODER). */
    public Flywheel(String name, DcMotorEx motor) {
        this.name = name;
        this.motor = motor;
    }

    /** For tests: use a pretend clock so tests do not wait for real time. */
    Flywheel withClock(LongSupplier clock) {
        clockNanos = clock;
        return this;
    }

    /** Spins the other way for positive speeds. */
    public Flywheel reversed() {
        MotorSetup.of(motor).reversed().coast().runUsingEncoder().done();
        return this;
    }

    /**
     * A second motor on the same flywheel. It gets the same speed command, so <b>it needs its own encoder
     * cable</b> (the hub's speed control reads it). If its encoder reads nothing while the wheel spins,
     * the follower is switched off and a warning shows (without an encoder, its speed control would
     * drive it to full power and fight the first motor).
     * Call it before {@link #pidf}, so the follower gets the same gains.
     */
    public Flywheel follower(HardwareMap hardwareMap, String followerName, boolean reversed) {
        follower = MotorSetup.get(hardwareMap, followerName).reversed(reversed).coast().runUsingEncoder().done();
        return this;
    }

    /** For tests: a pretend follower motor. */
    void attachFollowerForTests(DcMotorEx followerMotor) {
        follower = followerMotor;
    }

    /** Encoder counts per ONE flywheel turn. Default 28. */
    public Flywheel countsPerRev(double counts) {
        countsPerRev = Math.abs(counts);
        return this;
    }

    /**
     * The hub's speed-control gains (P, I, D, F). Applied to the follower too, if
     * {@link #follower} was called first. F is the main one: the steady power for a given speed.
     */
    public Flywheel pidf(double p, double i, double d, double f) {
        motor.setVelocityPIDFCoefficients(p, i, d, f);
        if (follower != null) follower.setVelocityPIDFCoefficients(p, i, d, f);
        return this;
    }

    /** Ready when within this many RPM of the target. Default 75. */
    public Flywheel tolerance(double rpmTolerance) {
        toleranceRpm = Math.abs(rpmTolerance);
        return this;
    }

    /** ...for at least this long, in seconds. Stops one lucky reading from counting. Default 0.15. */
    public Flywheel settleTime(double seconds) {
        settleSec = Math.abs(seconds);
        return this;
    }

    /** The highest speed it may be asked for, in RPM. Default 6000. */
    public Flywheel maxRpm(double max) {
        maxRpm = Math.abs(max);
        return this;
    }

    /** A drop of more than this many RPM after being ready counts as a shot. Default 150. */
    public Flywheel shotDrop(double rpmDrop) {
        shotDropRpm = Math.abs(rpmDrop);
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Commands (quick; update() does the work)
    // ---------------------------------------------------------------------------------------

    /**
     * Spins to this speed in RPM (0 = coast to a stop). Changing the target restarts the "ready" timer.
     * Safe to call every loop with the same (or almost the same) speed.
     */
    public void setRpm(double newRpm) {
        double clamped = MathUtil.clamp(newRpm, -maxRpm, maxRpm);
        // A real change (more than half the tolerance): it is no longer ready at the new speed.
        // Tiny changes (e.g. a distance reading that jitters every loop) keep "ready", or it would never fire.
        if (Math.abs(clamped - targetRpm) > toleranceRpm / 2) {
            inBandSince = Double.NaN;
            ready = false;
            armedForShot = false;
        }
        targetRpm = clamped;
    }

    /** Coasts to a stop (no braking: a fast flywheel must spin down freely). */
    @Override
    public void stop() {
        setRpm(0);
        // Send the 0 now, not in the next update(), so it stops at once.
        send(0);
    }

    /** "" or a problem, e.g. the follower's encoder not reading. */
    public String getFault() { return fault; }

    // ---------------------------------------------------------------------------------------
    // Every loop
    // ---------------------------------------------------------------------------------------

    /** Once per loop: sends the speed, reads the real speed, works out "ready", and counts shots. */
    @Override
    public void update() {
        double now = clockNanos.getAsLong() / 1e9;   // seconds
        // Turn RPM into what the hub wants, encoder counts per second:
        // turns per minute / 60 = turns per second; x counts per turn = counts per second.
        send(targetRpm / 60.0 * countsPerRev);
        // And back the other way for the measured speed.
        rpm = motor.getVelocity() / countsPerRev * 60.0;
        checkFollower(now);

        // Not spinning on purpose: never ready, nothing to count.
        if (targetRpm == 0) {
            ready = false;
            armedForShot = false;
            inBandSince = Double.NaN;
            return;
        }
        // "Ready" only after the speed has STAYED within tolerance for settleTime, not on the first
        // good reading: a wheel speeding up passes through the right speed for a moment on its way.
        boolean inBand = Math.abs(rpm - targetRpm) <= toleranceRpm;
        if (inBand) {
            if (Double.isNaN(inBandSince)) inBandSince = now;
            ready = now - inBandSince >= settleSec;
        } else {
            inBandSince = Double.NaN;
            ready = false;
        }
        if (ready) armedForShot = true;
        // A sudden drop after being ready = a ball went through.
        // (A ball takes energy from the wheel, so it slows down for a moment.) Count it once, then wait
        // until it is ready again before the next drop can count.
        if (armedForShot && Math.abs(targetRpm) - Math.abs(rpm) > shotDropRpm) {
            shots++;
            armedForShot = false;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Questions and steps
    // ---------------------------------------------------------------------------------------

    /** True when at speed and steady. */
    public boolean isReady() { return ready; }

    /** The measured speed (RPM). */
    public double getRpm() { return rpm; }

    /** The target speed (RPM). */
    public double getTargetRpm() { return targetRpm; }

    /** How many shots were noticed (speed drops) since the OpMode started. */
    public int getShotCount() { return shots; }

    /** A step (autonomous): spin up and wait until ready (or {@code timeoutSec}). */
    public AutoStep spinUpStep(double targetRpmValue, double timeoutSec) {
        // "ctx -> { ... }" is a small piece of code handed over to run later, when the routine reaches it.
        return ctx -> {
            setRpm(targetRpmValue);
            // The time limit stops a weak battery from freezing the autonomous here.
            ctx.waitUntil(this::isReady, timeoutSec);
        };
    }

    /** The FTC motor inside, for anything this helper does not do. */
    public DcMotorEx getMotor() { return motor; }
    @Override public String name() { return name; }

    /** Shows measured / target RPM and READY; shots seen in debug mode; a warning for a fault. */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!fault.isEmpty()) t.warn(name + ": " + fault);
        t.line(name, "%.0f / %.0f rpm%s", rpm, targetRpm, ready ? "  READY" : "");
        t.debug(name + " shots seen", shots);
    }

    // ---------------------------------------------------------------------------------------
    // Small helpers used only inside this class
    // ---------------------------------------------------------------------------------------

    // Sends a speed (encoder counts per second) to the motor, and to the follower if it is working.
    private void send(double ticksPerSec) {
        // Same as last time: skip it, each hardware command costs loop time.
        if (ticksPerSec == lastSent) return;
        lastSent = ticksPerSec;
        // A switched-off follower gets nothing. ("a ? b : c" means: if a use b, otherwise c.)
        DcMotorEx f = followerDisabled ? null : follower;
        if (ticksPerSec == 0) {
            // Coast: plain power 0 (speed control at 0 would brake hard).
            // A hard stop strains the motor, gears and belts of a fast, heavy wheel.
            motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motor.setPower(0);
            if (f != null) { f.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER); f.setPower(0); }
            return;
        }
        // Speeding up again after a coast: switch speed control back on (only when needed, as
        // changing the mode takes time).
        if (motor.getMode() != DcMotor.RunMode.RUN_USING_ENCODER) motor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        motor.setVelocity(ticksPerSec);
        if (f != null) {
            if (f.getMode() != DcMotor.RunMode.RUN_USING_ENCODER) f.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            f.setVelocity(ticksPerSec);
        }
    }

    /** A follower whose encoder reads (almost) nothing while the wheel clearly spins is switched off. */
    private void checkFollower(double now) {
        if (follower == null || followerDisabled) return;
        double followerRpm = follower.getVelocity() / countsPerRev * 60.0;
        // "Bad" = the main motor clearly spins (over 500 RPM) but the follower's encoder reads under
        // a fifth of that. Both motors turn the same wheel, so a working encoder would read about the same.
        boolean bad = Math.abs(rpm) > 500 && Math.abs(followerRpm) < 0.2 * Math.abs(rpm);
        if (!bad) { followerBadSince = Double.NaN; return; }
        if (Double.isNaN(followerBadSince)) followerBadSince = now;
        // Bad for over half a second (not one odd reading): switch it off for the rest of the OpMode.
        // Its speed control would otherwise push it to full power, fighting the main motor.
        if (now - followerBadSince > 0.5) {
            followerDisabled = true;
            follower.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            follower.setPower(0);
            fault = "follower encoder not reading: follower switched off";
        }
    }
}
