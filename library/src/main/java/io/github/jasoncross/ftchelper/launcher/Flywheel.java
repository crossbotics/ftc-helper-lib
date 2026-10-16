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
 * Docs: docs/modules/launcher.md
 */
public class Flywheel implements Subsystem {
    private final String name;
    private final DcMotorEx motor;
    private DcMotorEx follower = null;
    private LongSupplier clockNanos = System::nanoTime;

    private double countsPerRev = 28;
    private double toleranceRpm = 75;
    private double settleSec = 0.15;
    private double maxRpm = 6000;
    private double shotDropRpm = 150;

    private double targetRpm = 0;
    private double rpm = 0;
    private double inBandSince = Double.NaN;
    private boolean ready = false;
    private boolean armedForShot = false;
    private int shots = 0;
    private double lastSent = Double.NaN;
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

    /** For tests. */
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

    /** The hub's speed-control gains (P, I, D, F). Applied to the follower too. */
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

    /** The highest speed it may be asked for. Default 6000. */
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

    /** Spins to this speed (0 = coast to a stop). Changing the target restarts the "ready" timer. */
    public void setRpm(double newRpm) {
        double clamped = MathUtil.clamp(newRpm, -maxRpm, maxRpm);
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
        send(0);
    }

    /** "" or a problem, e.g. the follower's encoder not reading. */
    public String getFault() { return fault; }

    @Override
    public void update() {
        double now = clockNanos.getAsLong() / 1e9;
        send(targetRpm / 60.0 * countsPerRev);
        rpm = motor.getVelocity() / countsPerRev * 60.0;
        checkFollower(now);

        if (targetRpm == 0) {
            ready = false;
            armedForShot = false;
            inBandSince = Double.NaN;
            return;
        }
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
        if (armedForShot && Math.abs(targetRpm) - Math.abs(rpm) > shotDropRpm) {
            shots++;
            armedForShot = false;
        }
    }

    /** True when at speed and steady. */
    public boolean isReady() { return ready; }

    /** The measured speed (RPM). */
    public double getRpm() { return rpm; }

    /** The target speed (RPM). */
    public double getTargetRpm() { return targetRpm; }

    /** How many shots were noticed (speed drops) since the OpMode started. */
    public int getShotCount() { return shots; }

    /** A step: spin up and wait until ready (or {@code timeoutSec}). */
    public AutoStep spinUpStep(double targetRpmValue, double timeoutSec) {
        return ctx -> {
            setRpm(targetRpmValue);
            ctx.waitUntil(this::isReady, timeoutSec);
        };
    }

    public DcMotorEx getMotor() { return motor; }
    @Override public String name() { return name; }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!fault.isEmpty()) t.warn(name + ": " + fault);
        t.line(name, "%.0f / %.0f rpm%s", rpm, targetRpm, ready ? "  READY" : "");
        t.debug(name + " shots seen", shots);
    }

    private void send(double ticksPerSec) {
        if (ticksPerSec == lastSent) return;
        lastSent = ticksPerSec;
        DcMotorEx f = followerDisabled ? null : follower;
        if (ticksPerSec == 0) {
            // Coast: plain power 0 (speed control at 0 would brake hard).
            motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motor.setPower(0);
            if (f != null) { f.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER); f.setPower(0); }
            return;
        }
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
        boolean bad = Math.abs(rpm) > 500 && Math.abs(followerRpm) < 0.2 * Math.abs(rpm);
        if (!bad) { followerBadSince = Double.NaN; return; }
        if (Double.isNaN(followerBadSince)) followerBadSince = now;
        if (now - followerBadSince > 0.5) {
            followerDisabled = true;
            follower.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            follower.setPower(0);
            fault = "follower encoder not reading: follower switched off";
        }
    }
}
