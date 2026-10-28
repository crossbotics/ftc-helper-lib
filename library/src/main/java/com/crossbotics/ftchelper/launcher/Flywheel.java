package com.crossbotics.ftchelper.launcher;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.crossbotics.ftchelper.auto.AutoStep;
import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.MathUtil;
import java.util.function.DoubleSupplier;
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
 * battery still changes the result: the hub's F gain gives the same POWER for a speed, and the same
 * power on a weaker battery spins the wheel slower (the I gain corrects it, but slowly). With
 * {@link #batteryCompensation} the F gain is scaled by (tuned volts / volts now), so the wheel gets
 * the same push at any battery level:
 * <pre>{@code
 * flywheel.batteryCompensation(Hw.batteryReader(hardwareMap), 12.6);   // 12.6 V: from the Flywheel Finder
 * }</pre>
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
    // The gains from pidf(), kept so a follower added later gets them too. null = pidf() not called.
    private double[] gains = null;
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

    // Battery compensation (batteryCompensation()): where the volts come from (null = off), the volts the
    // gains were tuned at, the battery after the filter (see BATTERY_FILTER_SEC), when to read next, the F
    // scale sent to the hub, and how many readings in a row were low (for the warning).
    private DoubleSupplier batteryVolts = null;
    private double nominalVolts = DEFAULT_NOMINAL_VOLTS;
    private double volts = Double.NaN;
    private double nextVoltsAt = Double.NaN;
    private double fScale = 1;
    private int lowReadings = 0;
    // The good readings of the last BATTERY_FILTER_SEC: {time s, volts}, oldest first.
    private final java.util.ArrayDeque<double[]> recentVolts = new java.util.ArrayDeque<>();

    /** The volts the gains are for when nothing else is said: a charged battery under load (as {@code Feedforward}). */
    public static final double DEFAULT_NOMINAL_VOLTS = 12.8;
    /** Below this many volts the Driver Station warns that the battery is low. */
    public static final double LOW_BATTERY_VOLTS = 11.5;
    /** How often the battery is read, seconds. */
    public static final double BATTERY_READ_SEC = 0.25;
    /**
     * The battery used is the HIGHEST reading of the last this-many seconds. Why: while the drive or the
     * flywheel itself pulls hard, the battery dips for a moment and comes back. A dip is not a weaker
     * battery: scaling F up for it would push the wheel past its speed just as the load ends. A battery
     * that is really lower reads low for the whole second, and then F is scaled.
     */
    public static final double BATTERY_FILTER_SEC = 1.0;
    /** The F gain is changed only when the new scale differs by more than this share (2%). */
    public static final double RESEND_SHARE = 0.02;
    /** The F scale is kept between these, so a wrong reading cannot make the wheel run away. */
    public static final double MIN_F_SCALE = 0.8, MAX_F_SCALE = 1.3;

    /**
     * Starting speed-control gains from a top speed, before anything is tuned: {P, I, D, F}.
     * The hub's full power is 32767, so F = 32767 / (top speed in encoder counts per second) gives the
     * right power for any speed by itself; P = F / 10 and I = P / 10 fix what is left; D = 0. This is
     * REV's formula (docs.revrobotics.com/first-global/fgc-pid) and the Flywheel Finder's. With the parts
     * catalog's top speed (a goBILDA 6000 RPM motor: 6000 RPM x 28 counts / 60 = 2800 counts/s) it gives
     * F 11.70, P 1.17, I 0.117: the wheel spins close to the asked speed on the first run. The Flywheel
     * Finder measures the real top speed of YOUR wheel, a little lower, and gives a slightly bigger F.
     *
     * <pre>{@code
     * double[] g = Flywheel.startingPidf(6000 * 28 / 60.0);   // {1.17, 0.117, 0, 11.70}
     * flywheel.pidf(g[0], g[1], g[2], g[3]);
     * }</pre>
     *
     * @param topTicksPerSec the speed at full power, encoder counts per second (sign ignored)
     * @return {P, I, D, F}, or all zeros if the top speed is under 1 count per second
     */
    public static double[] startingPidf(double topTicksPerSec) {
        double top = Math.abs(topTicksPerSec);
        if (top < 1) return new double[] {0, 0, 0, 0};
        double f = 32767 / top;
        double p = f / 10;
        double i = p / 10;
        return new double[] {p, i, 0, f};
    }

    /**
     * Uses {@link #startingPidf} with this flywheel's {@link #maxRpm} and {@link #countsPerRev} (set those
     * first): gains for a first run when you have no tuned ones yet. Your own {@link #pidf} replaces them.
     *
     * <pre>{@code
     * Flywheel.create(hardwareMap, "flywheel").countsPerRev(28).maxRpm(6000).startingGains();
     * }</pre>
     *
     * @return this flywheel
     */
    public Flywheel startingGains() {
        double[] g = startingPidf(maxRpm * countsPerRev / 60.0);
        return pidf(g[0], g[1], g[2], g[3]);
    }

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
     * It gets the same {@link #pidf} gains, whether you call pidf before or after this.
     */
    public Flywheel follower(HardwareMap hardwareMap, String followerName, boolean reversed) {
        follower = MotorSetup.get(hardwareMap, followerName).reversed(reversed).coast().runUsingEncoder().done();
        applyGainsToFollower();
        return this;
    }

    /** For tests: a pretend follower motor. */
    void attachFollowerForTests(DcMotorEx followerMotor) {
        follower = followerMotor;
        applyGainsToFollower();
    }

    // If pidf() was already called, give the new follower the same gains (F scaled for the battery).
    private void applyGainsToFollower() {
        if (gains != null) follower.setVelocityPIDFCoefficients(gains[0], gains[1], gains[2], gains[3] * fScale);
    }

    /** Encoder counts per ONE flywheel turn. Default 28. */
    public Flywheel countsPerRev(double counts) {
        countsPerRev = Math.abs(counts);
        return this;
    }

    /**
     * The hub's speed-control gains (P, I, D, F). Applied to the follower too (in any order).
     * F is the main one: the steady power for a given speed. With {@link #batteryCompensation}, the
     * hub gets F x (tuned volts / volts now); these numbers stay the ones you tuned.
     */
    public Flywheel pidf(double p, double i, double d, double f) {
        gains = new double[] {p, i, d, f};
        sendGains();
        return this;
    }

    /**
     * Battery compensation: every {@link #BATTERY_READ_SEC} s it reads the battery and gives the hub
     * F x (nominalVolts / volts), kept to {@link #MIN_F_SCALE}..{@link #MAX_F_SCALE}, sent again only
     * when that changes by more than 2%. "volts" is the highest reading of the last
     * {@link #BATTERY_FILTER_SEC} s, so a short dip while the robot pulls hard does not raise F. Why: F
     * is a POWER per speed, and the same power spins the wheel slower on a weaker battery; scaled, the
     * wheel gets the same push at 11.5 V as at 12.6 V, so it reaches its speed and gets back to it after
     * a shot as fast as when it was tuned. P and I stay as tuned. Needs {@link #pidf} (nothing to scale
     * without an F). A reading outside 7..16 V (no sensor, a bad or too old reading) is skipped and the
     * last scale kept. Below {@link #LOW_BATTERY_VOLTS} the Driver Station warns.
     *
     * <p>Give it {@link com.crossbotics.ftchelper.hardware.Hw#batteryReader Hw.batteryReader(hardwareMap)}:
     * it asks the hubs on the library's helper thread, so the loop never waits for them. (Asking the hubs
     * yourself, {@code () -> Hw.batteryVolts(hardwareMap)}, holds up the loop a few milliseconds
     * every 0.25 s.)
     *
     * <pre>{@code
     * Flywheel.create(hardwareMap, "flywheel").pidf(1.17, 0.117, 0, 11.70)
     *         .batteryCompensation(Hw.batteryReader(hardwareMap), 12.6);
     * }</pre>
     *
     * @param batteryVolts where the volts come from, e.g. {@code Hw.batteryReader(hardwareMap)}; asked every
     *                     {@link #BATTERY_READ_SEC} s in {@link #update()}, so it must answer at once
     * @param nominalVolts the battery voltage the gains were tuned at (the Flywheel Finder shows it),
     *                     9 to 14 V; {@link #DEFAULT_NOMINAL_VOLTS} if you do not know it
     */
    public Flywheel batteryCompensation(DoubleSupplier batteryVolts, double nominalVolts) {
        if (!(nominalVolts >= 9 && nominalVolts <= 14)) {
            throw new IllegalArgumentException("Flywheel " + name + ": batteryCompensation nominalVolts = " + nominalVolts
                    + ": a 12 V battery's tuning voltage is 9 to 14 V (the Flywheel Finder shows it)");
        }
        this.batteryVolts = batteryVolts;
        this.nominalVolts = nominalVolts;
        nextVoltsAt = Double.NaN;   // read at the next update()
        recentVolts.clear();
        return this;
    }

    /** Battery compensation off: the hub gets F exactly as tuned (the default). */
    public Flywheel noBatteryCompensation() {
        batteryVolts = null;
        if (fScale != 1) {
            fScale = 1;
            sendGains();
        }
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
        checkBattery(now);
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

    /** The highest speed it may be asked for (see {@link #maxRpm}). */
    public double getMaxRpm() { return maxRpm; }

    /** The ready band: within this many RPM of the target (for the settle time) = ready. */
    public double getTolerance() { return toleranceRpm; }

    /** The measured speed (RPM). */
    public double getRpm() { return rpm; }

    /** The target speed (RPM). */
    public double getTargetRpm() { return targetRpm; }

    /** How many shots were noticed (speed drops) since the OpMode started. */
    public int getShotCount() { return shots; }

    /**
     * The battery the F scale uses (volts): the highest reading of the last {@link #BATTERY_FILTER_SEC} s.
     * NaN before the first reading or without {@link #batteryCompensation}.
     */
    public double getBatteryVolts() { return volts; }

    /** What F is multiplied by for the battery now (1 = as tuned, or compensation off). */
    public double getFScale() { return fScale; }

    /** True while {@link #batteryCompensation} is on. */
    public boolean hasBatteryCompensation() { return batteryVolts != null; }

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

    /**
     * Shows measured / target RPM and READY; shots seen and the battery (volts, F scale) in debug mode;
     * a warning for a fault or a low battery.
     */
    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (!fault.isEmpty()) t.warn(name + ": " + fault);
        // Two low readings in a row (after the filter: the battery was low for over a second).
        if (lowReadings >= 2) {
            t.warn(name + String.format(java.util.Locale.US, ": battery %.1f V: low. The flywheel gets back to speed slower: charge or swap it", volts));
        }
        t.line(name, "%.0f / %.0f rpm%s", rpm, targetRpm, ready ? "  READY" : "");
        t.debug(name + " shots seen", shots);
        if (batteryVolts != null) {
            t.debug(name + " battery", Double.isNaN(volts) ? "no reading" : String.format(java.util.Locale.US,
                    "%.2f V (tuned at %.1f V), F x%.2f", volts, nominalVolts, fScale));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Small helpers used only inside this class
    // ---------------------------------------------------------------------------------------

    // Sends the gains to the hub (and the follower): F scaled for the battery.
    private void sendGains() {
        if (gains == null) return;
        motor.setVelocityPIDFCoefficients(gains[0], gains[1], gains[2], gains[3] * fScale);
        if (follower != null) applyGainsToFollower();
    }

    // Battery compensation: reads the volts at most every BATTERY_READ_SEC, keeps the highest of the last
    // BATTERY_FILTER_SEC (a dip under load is not a weaker battery), and sends a new F only when it really
    // changed (sending costs loop time).
    private void checkBattery(double now) {
        if (batteryVolts == null) return;
        if (!Double.isNaN(nextVoltsAt) && now < nextVoltsAt) return;
        nextVoltsAt = now + BATTERY_READ_SEC;
        double reading = batteryVolts.getAsDouble();
        // Forget readings older than the filter's time.
        recentVolts.removeIf(r -> now - r[0] > BATTERY_FILTER_SEC + 1e-9);
        // No sensor (NaN), a reading too old, or one no 12 V battery gives: keep what we have.
        if (!(reading >= 7 && reading <= 16)) return;
        recentVolts.addLast(new double[] {now, reading});
        // The highest reading of the last second: a short dip under load does not count.
        double v = 0;
        for (double[] r : recentVolts) v = Math.max(v, r[1]);
        volts = v;
        lowReadings = v < LOW_BATTERY_VOLTS ? lowReadings + 1 : 0;
        if (gains == null) return;   // no F to scale (pidf() was not called)
        // A weaker battery: more power for the same push. 12.6 V tuned, 11.5 V now: F x 1.10.
        double want = MathUtil.clamp(nominalVolts / v, MIN_F_SCALE, MAX_F_SCALE);
        if (Math.abs(want - fScale) > RESEND_SHARE * fScale) {
            fScale = want;
            sendGains();
        }
    }

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
