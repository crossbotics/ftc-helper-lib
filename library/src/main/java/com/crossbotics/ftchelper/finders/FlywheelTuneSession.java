package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.finders.FlywheelTuneResults.Battery;
import com.crossbotics.ftchelper.finders.FlywheelTuneResults.Test;
import com.crossbotics.ftchelper.finders.FlywheelTuneResults.Verdict;
import com.crossbotics.ftchelper.launcher.Flywheel.SpeedControl;
import com.crossbotics.ftchelper.safety.EncoderGuard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Flywheel Tuner's brain, with no hardware: each loop it gets one reading (the time, the speed, the
 * battery...) and answers what the flywheel must do (off, a power, or hold a speed with a setting). The
 * OpMode ({@link FlywheelTunerOpMode}) reads the devices and does what it says. WHY apart: the whole tuner can
 * then be tested on a pretend flywheel, with slow and jumpy readings, in a fraction of a second.
 *
 * <p>The tests, in order, on a FULL battery: today's settings; full power (how fast the wheel can speed up);
 * steady powers (the power each speed needs); PID with a rising push strength (kP); bang-bang from wide limits
 * to narrow; today's settings again. On a LOW battery: today's settings, the best of each control again, then
 * a driving check of each. Every test is saved when it ends. Docs: docs/tuning/06-flywheel-tuner.md.
 */
final class FlywheelTuneSession {

    // ---- The rules (docs/tuning/06-flywheel-tuner.md lists them for students) ----

    /** At rest: under this speed for {@link #REST_SEC}, RPM. */
    static final double REST_RPM = 60;
    static final double REST_SEC = 1;
    /** Steady READY: inside the ready band this long in a row, seconds. */
    static final double STEADY_SEC = 0.75;
    static final double COUNTDOWN_SEC = 3;
    /** A setting fails when the speed goes past the target by more than this (two readings in a row), RPM. */
    static final double OVERSHOOT_RPM = 100;
    /** ...when it holds an average speed further than this from the target, RPM. */
    static final double AVERAGE_RPM = 25;
    /** ...when a reading jumps further than this while it holds, RPM. */
    static final double SETTLED_RPM = 100;
    /** ...when "at speed" is lost within this time, seconds. */
    static final double FALSE_READY_SEC = 0.75;
    /** ...when it reaches steady READY later than today's settings by more than this, seconds. */
    static final double LATER_SEC = 0.1;
    /** ...when the flywheel motor takes more than this many amps for {@link #AMPS_SEC}. */
    static final double AMPS_LIMIT = 9;
    static final double AMPS_SEC = 1;
    /** ...when the battery stays under this many volts for {@link #VOLTS_SEC} while it runs. */
    static final double VOLTS_LIMIT = 10.5;
    static final double VOLTS_SEC = 0.5;
    /**
     * Under this many volts for {@link #ABORT_READINGS} readings in a row, the battery run stops (the finished
     * tests are kept). One low reading, a dip at a full-power start, does not stop it.
     */
    static final double ABORT_VOLTS = 10;
    static final int ABORT_READINGS = 2;
    /** The batteries, with the motor off: FULL is this or more... */
    static final double FULL_VOLTS = 12.6;
    /** ...and LOW is from this... */
    static final double LOW_MIN_VOLTS = 12.0;
    /** ...to this. */
    static final double LOW_MAX_VOLTS = 12.4;
    /** The driving check: this long, the sticks at this share of full power. */
    static final double DRIVE_SEC = 10;
    static final double DRIVE_POWER = 0.5;
    /** The driving check passes when the speed stays within the ready band plus this, RPM. */
    static final double DRIVE_EXTRA_RPM = 50;
    /** How late the motor's speed reading is, seconds (a guess: the robot cannot measure it). */
    static final double SENSOR_LAG_SEC = 0.05;
    /** PID: the push strengths (kP) after today's, in order. */
    static final double[] PID_KP = {25, 50, 100, 150};
    /**
     * PID: the kI values tried after the kP list (small first), on the best kP. Only when today's kI is 0. kI
     * removes a steady error that kF alone leaves (a weaker battery); too much winds up and goes past the target.
     */
    static final double[] PID_KI = {0.02, 0.05, 0.15};
    /**
     * While a setting holds the speed, its steady power moves in steps of this share (PID: of the written kF;
     * bang-bang: of the fitted kv), at most {@link #KF_RANGE} either way, until the average is within 25 RPM.
     */
    static final double KF_STEP = 0.01;
    static final double KF_RANGE = 0.06;
    /** Bang-bang: full-power widths as multiples of the rise the control cannot see in time, wide first. */
    static final double[] BANG_WIDTHS = {2, 1.5, 1, 0.75, 0.5};
    /** Bang-bang: the most power steps down to these when a current or voltage limit stops it. */
    static final double[] BANG_POWERS = {1, 0.9, 0.8};

    // The time limit of each part of a test, seconds. A part that takes longer fails the setting.
    static final double REST_LIMIT = 45, SPINUP_LIMIT = 8, HOLD_WINDOW = 2, KF_SETTLE = 1, COAST_LIMIT = 3;
    static final double RECOVER_LIMIT = 3, SETTLE_LIMIT = 4, STEP_LIMIT = 8, FULL_LIMIT = 6, PLATEAU_LIMIT = 10;
    // Pretend shots per setting.
    static final int SHOTS = 3;
    // A tiny margin: 0.75 s of small time steps must not add up to 0.7499999.
    private static final double EPS = 1e-9;

    /** Why a setting failed. Its words finish "Why: ...". {@code unsafe}: a family stops getting stronger. */
    enum Reason {
        SLOWER("slower to steady speed than today's settings", false),
        OVERSHOOT("went more than 100 RPM past the target", true),
        AVERAGE("held a speed more than 25 RPM off the target", false),
        SETTLED("the speed jumped more than 100 RPM while holding", true),
        FALSE_READY("said \"at speed\", then lost it right away", true),
        NO_SPEED("never got to steady speed in time", false),
        RECOVERY("not back at speed after a pretend shot in time", false),
        STEP("did not reach a new speed in time", false),
        REVERSE("would brake the motor hard", true),
        CURRENT("the motor took too much current", true),
        VOLTAGE("pulled the battery too low", true),
        TIMEOUT("took too long", false),
        DRIVE("lost the speed while driving", false),
        SKIPPED("driving check skipped", false);

        final String words;
        final boolean unsafe;

        Reason(String words, boolean unsafe) {
            this.words = words;
            this.unsafe = unsafe;
        }

        static Reason of(String words) {
            for (Reason r : values()) if (r.words.equals(words)) return r;
            return null;
        }
    }

    /** Where the session is. */
    enum Stage { READY, COUNTDOWN, TESTING, ASK_DRIVE, STOPPED, BETWEEN, FINISHED }

    /** What stopped it. */
    enum Stop { NONE, BUTTON, HELP, ENCODER, LOW_BATTERY, NO_REST, CURRENT }

    /** The robot's numbers the tests use (from Tunables.java, read by the OpMode). */
    static final class Config {
        /** The installed control and today's numbers of both controls. */
        FlywheelSetting today;
        /** The middle shot speed, and the lowest and highest table speeds (steps), RPM. */
        double middleRpm, lowRpm, highRpm;
        /** "At speed" = this close to the target (readyBandRpm), for {@link #settleSec} (the flywheel's). */
        double readyBandRpm = 60, settleSec = 0.15;
        /**
         * The pretend shot's speed drop: ballDropRpm (one real ball), or shotDropRpm when that is not written; and
         * the time between balls (shotIntervalSec).
         */
        double dropRpm = 150, paceSec = 0.25;
        /** How long Continuous fire waits for steady speed, seconds. */
        double readyTimeoutSec = 3;
        /** Encoder counts in one turn of the wheel. */
        double countsPerRev = 28;
        /** False only in tests that run the plant at any battery: the start skips the FULL / LOW range. */
        boolean checkBattery = true;
        /** False when the robot has no drive: a driving check cannot start, and it is skipped (no winner). */
        boolean hasDrive = true;

        /** The settings the tests depend on, as one line (the saved file must match it). */
        String setup() {
            return today.text() + " mid=" + r(middleRpm) + " low=" + r(lowRpm) + " high=" + r(highRpm) + " band=" + r(readyBandRpm)
                    + " drop=" + r(dropRpm) + " pace=" + r(paceSec) + " cpr=" + r(countsPerRev);
        }

        private static String r(double v) {
            return String.format(Locale.US, "%.2f", v);
        }
    }

    /** One loop's readings. NaN = not known. */
    static final class Sample {
        final double sec, rpm, counts, volts, amps, power;
        final String fault;

        /**
         * @param sec    the time, seconds
         * @param rpm    the speed read, with its sign
         * @param counts the encoder position, counts (NaN: the average comes from the speed readings)
         * @param volts  the battery, volts
         * @param amps   the flywheel motor's current, amps
         * @param power  the power the motor had since the last loop (NaN: the hub's PID decides it)
         * @param fault  the flywheel's own fault ("" = none): bang-bang's encoder check
         */
        Sample(double sec, double rpm, double counts, double volts, double amps, double power, String fault) {
            this.sec = sec;
            this.rpm = rpm;
            this.counts = counts;
            this.volts = volts;
            this.amps = amps;
            this.power = power;
            this.fault = fault == null ? "" : fault;
        }
    }

    /** What the flywheel must do this loop. */
    static final class Output {
        enum Kind { OFF, POWER, CONTROL }

        final Kind kind;
        final double power, targetRpm;
        final FlywheelSetting setting;
        /** True while the sticks may drive (the driving check). */
        final boolean drive;

        Output(Kind kind, double power, double targetRpm, FlywheelSetting setting, boolean drive) {
            this.kind = kind;
            this.power = power;
            this.targetRpm = targetRpm;
            this.setting = setting;
            this.drive = drive;
        }

        static final Output OFF = new Output(Kind.OFF, 0, 0, null, false);
    }

    private final Config config;
    private final FlywheelTuneResults results;
    private Stage stage;
    private Stop stopWhy = Stop.NONE;
    private String stopDetail = "";
    private double countdownEnd = Double.NaN;
    private double now = Double.NaN;
    private Runner runner;
    // A driving check that A started (after its countdown).
    private Test driveFor;
    private String refusal = "";
    private String lastFailed = "";
    private int saves = 0;
    private final EncoderGuard guard = new EncoderGuard();
    private double lastSampleAt = Double.NaN;
    private double volts = Double.NaN;
    private int lowVoltReadings = 0;

    /**
     * A session that goes on from what was saved.
     *
     * @param config  the robot's numbers
     * @param results what was saved (empty for a new session)
     */
    FlywheelTuneSession(Config config, FlywheelTuneResults results) {
        this.config = config;
        this.results = results;
        // Saved tests: go on where they ended (the FULL run done: the battery change comes first).
        Plan p = plan();
        if (results.isFinished()) stage = Stage.FINISHED;
        else if (p == null || (planFull() == null && !anyFor(Battery.LOW))) stage = finishOrBetween();
        else stage = p.kind == Plan.Kind.ASK_DRIVE ? Stage.ASK_DRIVE : Stage.READY;
    }

    // ---------------------------------------------------------------- buttons

    /**
     * True while something may move: the countdown or a test. Any button then stops it.
     */
    boolean moving() {
        return stage == Stage.COUNTDOWN || stage == Stage.TESTING;
    }

    /** A: start, go on, or start the driving check. Never while moving (then every button stops). */
    void pressA() {
        if (moving()) return;
        refusal = "";
        if (stage == Stage.ASK_DRIVE) {
            if (!config.hasDrive) {
                // No drive: the sticks could not move the robot, so a "pass" would prove nothing. Skipped.
                skipDrive();
                refusal = "No drive found: the driving check is skipped.\nSo there is no winner. Fix the drive, then again.";
                return;
            }
            driveFor = askedDrive();
            startCountdown();
            return;
        }
        if (stage != Stage.READY && stage != Stage.STOPPED && stage != Stage.BETWEEN) return;
        Plan p = plan();
        if (p == null) {
            stage = finishOrBetween();
            return;
        }
        if (p.kind == Plan.Kind.ASK_DRIVE) {
            stage = Stage.ASK_DRIVE;
            return;
        }
        // A battery run starts on the right battery, with the motor off.
        Battery b = battery();
        if (config.checkBattery) {
            // A run that started goes on with some room: a FULL battery is a little lower after some tests.
            String wrong = startProblem(volts);
            if (!wrong.isEmpty()) {
                refusal = wrong;
                return;
            }
        }
        startCountdown();
    }

    /** B: skip the driving check that is asked now (then that control has no winner). */
    void pressB() {
        if (stage != Stage.ASK_DRIVE) return;
        refusal = "";
        skipDrive();
    }

    // The driving check asked now is saved as skipped (then that control has no winner).
    private void skipDrive() {
        Test t = askedDrive();
        if (t == null) return;
        Map<String, Double> none = new LinkedHashMap<>();
        record(new Test(Battery.LOW, "drive:" + t.id, Verdict.FAIL, Reason.SKIPPED.words, t.setting, none));
        advanceAfterAsk();
    }

    /**
     * Stops everything at once: a button, help, or the end. Finished tests stay saved; the unfinished one
     * is not counted and runs again from its start after a new A press.
     */
    void stop(Stop why, String detail) {
        if (!moving()) return;
        stage = Stage.STOPPED;
        stopWhy = why;
        stopDetail = detail == null ? "" : detail;
        runner = null;
        driveFor = null;
        lowVoltReadings = 0;
        guard.reset();
    }

    // ---------------------------------------------------------------- every loop

    /** One loop: the readings in, what the flywheel must do out. Never waits. */
    Output update(Sample s) {
        now = s.sec;
        if (!Double.isNaN(s.volts) && s.volts > 1) volts = s.volts;
        double gap = Double.isNaN(lastSampleAt) ? 0 : s.sec - lastSampleAt;
        lastSampleAt = s.sec;
        if (stage == Stage.COUNTDOWN) {
            if (s.sec >= countdownEnd - EPS) startTest(s);
            else return Output.OFF;
        }
        if (stage != Stage.TESTING || runner == null) return Output.OFF;
        // SAFETY first: the flywheel's own fault, a wrong speed reading, a battery near brownout.
        if (!s.fault.isEmpty()) {
            stop(Stop.ENCODER, s.fault);
            return Output.OFF;
        }
        double guardPower = runner.lastOut.kind == Output.Kind.OFF ? 0
                : runner.lastOut.kind == Output.Kind.POWER ? runner.lastOut.power
                : !Double.isNaN(s.power) ? s.power : runner.lastOut.targetRpm > 0 ? 1 : 0;
        String bad = guard.update(guardPower, s.rpm, s.sec);
        if (!bad.isEmpty()) {
            stop(Stop.ENCODER, bad);
            return Output.OFF;
        }
        // Near brownout: 2 readings in a row (one dip at a full-power start is not a battery problem). The
        // finished tests are kept; the test that was running runs again after the battery change.
        if (!Double.isNaN(s.volts) && s.volts > 1 && s.volts < ABORT_VOLTS) {
            if (++lowVoltReadings >= ABORT_READINGS) {
                stop(Stop.LOW_BATTERY, num(s.volts, 1));
                return Output.OFF;
            }
        } else {
            lowVoltReadings = 0;
        }
        Output out = runner.step(s, gap);
        if (runner != null) runner.lastOut = out;
        return stage == Stage.TESTING ? out : Output.OFF;
    }

    // ---------------------------------------------------------------- what the screen shows

    Stage stage() { return stage; }
    Stop stopWhy() { return stopWhy; }
    String stopDetail() { return stopDetail; }
    /** Why A did not start ("" = it did). */
    String refusal() { return refusal; }
    /** The last setting that failed, and why, in two short lines ("" = none yet in this run). */
    String lastFailed() { return lastFailed; }
    /** Goes up each time a test is saved: the OpMode then saves the file (off the loop). */
    int saves() { return saves; }
    FlywheelTuneResults results() { return results; }
    Config config() { return config; }

    /** The battery run now: FULL until it is done, then LOW. */
    Battery battery() {
        return planFull() == null ? Battery.LOW : Battery.FULL;
    }

    /** True while the countdown leads to a driving check (not a test). */
    boolean countdownForDrive() {
        return stage == Stage.COUNTDOWN && driveFor != null;
    }

    /** Seconds left in the countdown (whole seconds, at least 1). */
    int countdownLeft() {
        return (int) Math.max(1, Math.ceil(countdownEnd - now - EPS));
    }

    /** This battery run's test number (finished tests + 1). */
    int testNumber() {
        int n = 1;
        Battery b = battery();
        for (Test t : results.tests()) if (t.battery == b && !t.id.equals("done")) n++;
        return n;
    }

    /** The most tests this battery run can have (the families stop early when a setting is unsafe). */
    int testTotal() {
        if (battery() == Battery.FULL) return 3 + pidList().size() + BANG_WIDTHS.length + 2;
        return 1 + 6 + 2;
    }

    /** What the test now tests, in plain words: "today's settings", "PID, push strength (kP) 50"... */
    String testing() {
        return runner == null ? "" : runner.title();
    }

    /** What the test does now, in plain words: "spin up from a stop", "pretend shot 2 of 3"... */
    String doing() {
        return runner == null ? "" : runner.doing();
    }

    /** The target speed now, RPM (0 when the power is off). */
    double targetNow() {
        return runner == null || runner.lastOut.kind != Output.Kind.CONTROL ? 0 : runner.lastOut.targetRpm;
    }

    /** True while the setting under test is held by the hub's PID or bang-bang (not off, not a power). */
    boolean controlling() {
        return runner != null && runner.lastOut.kind == Output.Kind.CONTROL;
    }

    /** Seconds left in the driving check, or NaN when it is not driving. */
    double driveLeft() {
        return runner instanceof Trial && ((Trial) runner).phase == Phase.DRIVING
                ? Math.max(0, DRIVE_SEC - (now - ((Trial) runner).phaseAt)) : Double.NaN;
    }

    /** True while the driving check waits for steady speed before the sticks drive. */
    boolean driveWaiting() {
        return runner instanceof Trial && ((Trial) runner).phase == Phase.DRIVE_WAIT;
    }

    /** Settings that passed / failed in this battery run so far. */
    int[] goodAndBad() {
        int good = 0, bad = 0;
        Battery b = battery();
        for (Test t : results.tests()) {
            if (t.battery != b || !t.isCandidate()) continue;
            if (t.verdict == Verdict.PASS) good++;
            else bad++;
        }
        return new int[] {good, bad};
    }

    /** The driving check asked now (its FULL battery test), or null. */
    Test askedDrive() {
        Plan p = plan();
        return p != null && p.kind == Plan.Kind.ASK_DRIVE ? p.finalist : null;
    }

    /** Which driving check is asked: 1 or 2, of how many. */
    int[] driveNumber() {
        int done = 0, all = 0;
        for (SpeedControl c : SpeedControl.values()) {
            Test f = lowFinalist(c);
            if (f == null) continue;
            all++;
            if (results.find(Battery.LOW, "drive:" + f.id) != null) done++;
        }
        return new int[] {Math.min(all, done + 1), all};
    }

    /**
     * Full power at shot speed, from the full-power test: {accel RPM/s, step RPM, delay s, time from a stop
     * to the middle speed s}, or null before that test.
     */
    double[] fullPower() {
        Test t = results.find(Battery.FULL, "fullpower");
        if (t == null) return null;
        return new double[] {t.number("accel"), t.number("step"), t.number("delay"), t.number("reachSec")};
    }

    /**
     * True when full power cannot give back one ball's drop before the next ball: the acceleration at shot
     * speed x the time between balls is less than the drop. False when it can, or when not measured.
     */
    boolean recoveryImpossible() {
        double[] f = fullPower();
        return f != null && !Double.isNaN(f[0]) && f[0] * config.paceSec < config.dropRpm;
    }

    /** The battery needed for a run, as words: "12.6 V or more" / "12.0 to 12.4 V". */
    static String batteryRange(Battery b) {
        return b == Battery.FULL ? num(FULL_VOLTS, 1) + " V or more" : num(LOW_MIN_VOLTS, 1) + " to " + num(LOW_MAX_VOLTS, 1) + " V";
    }

    /** "" when this battery fits the run, else what is wrong and what to do, in short lines. */
    static String batteryProblem(Battery b, double v) {
        if (Double.isNaN(v)) return "No battery reading yet. Wait 1 s, then {A}.";
        if (b == Battery.FULL && v < FULL_VOLTS) {
            return "Battery " + num(v, 1) + " V: NOT full. Need " + batteryRange(b) + ".\nCharge it, or put in a full one.";
        }
        if (b == Battery.LOW && v > LOW_MAX_VOLTS) {
            return "Battery " + num(v, 1) + " V: too full for this run.\nNeed " + batteryRange(b) + ". Use a used battery.";
        }
        if (b == Battery.LOW && v < LOW_MIN_VOLTS) {
            return "Battery " + num(v, 1) + " V: too low for this run.\nNeed " + batteryRange(b) + ". Charge it a little.";
        }
        return "";
    }

    /** "" when A may start (or go on with) this battery run on this battery, else why not. */
    String startProblem(double v) {
        Battery b = battery();
        return anyFor(b) ? resumeProblem(b, v) : batteryProblem(b, v);
    }

    /** How far a battery may have run down when a stopped run goes on, volts. */
    static final double RESUME_ROOM_VOLTS = 0.3;

    /** "" when this battery may go on with a run that already started (a little lower is fine), else why not. */
    static String resumeProblem(Battery b, double v) {
        if (Double.isNaN(v)) return "No battery reading yet. Wait 1 s, then {A}.";
        double low = (b == Battery.FULL ? FULL_VOLTS : LOW_MIN_VOLTS) - RESUME_ROOM_VOLTS;
        if (v < low || (b == Battery.LOW && v > LOW_MAX_VOLTS)) {
            return "Battery " + num(v, 1) + " V: not this run's battery.\nNeed " + batteryRange(b) + ". Put it back in.";
        }
        return "";
    }

    static String num(double v, int decimals) {
        return FinderResults.num(v, decimals);
    }

    // ---------------------------------------------------------------- the plan

    // What comes next: a test, or a driving check to ask about.
    private static final class Plan {
        enum Kind { TRIAL, FULL_POWER, FIT, ASK_DRIVE }

        final Kind kind;
        final String id;
        final FlywheelSetting setting;
        final boolean adjust, compare;
        final Test finalist;

        Plan(Kind kind, String id, FlywheelSetting setting, boolean adjust, boolean compare, Test finalist) {
            this.kind = kind;
            this.id = id;
            this.setting = setting;
            this.adjust = adjust;
            this.compare = compare;
            this.finalist = finalist;
        }
    }

    private Plan plan() {
        Plan p = planFull();
        return p != null ? p : planLow();
    }

    /** The next test's name ("today", "pid:kP=25"...), or "" when none is left (for tests and the screen). */
    String nextId() {
        Plan p = plan();
        return p == null ? "" : p.id;
    }

    /** The setting the next test tries, or null (a measurement, or none left). */
    FlywheelSetting nextSetting() {
        Plan p = plan();
        return p == null ? null : p.setting;
    }

    private boolean anyFor(Battery b) {
        for (Test t : results.tests()) if (t.battery == b) return true;
        return false;
    }

    private Plan planFull() {
        Battery b = Battery.FULL;
        if (results.find(b, "today") == null) return trial("today", config.today, false, false);
        if (results.find(b, "fullpower") == null) return new Plan(Plan.Kind.FULL_POWER, "fullpower", null, false, false, null);
        if (results.find(b, "fit") == null) return new Plan(Plan.Kind.FIT, "fit", null, false, false, null);
        Plan p = nextPid();
        if (p != null) return p;
        p = nextBang();
        if (p != null) return p;
        if (results.find(b, "drift") == null) return trial("drift", config.today, false, false);
        return null;
    }

    private Plan planLow() {
        Battery b = Battery.LOW;
        if (results.find(b, "today") == null) return trial("today", config.today, false, false);
        // The best of each control again, exactly as it was: the next best when it fails (3 at most).
        for (SpeedControl c : SpeedControl.values()) {
            for (Test t : forLow(c)) {
                Test low = results.find(b, t.id);
                if (low == null) return trial(t.id, t.setting, false, true);
                if (low.verdict == Verdict.PASS) break;
            }
        }
        for (SpeedControl c : SpeedControl.values()) {
            Test f = lowFinalist(c);
            if (f != null && results.find(b, "drive:" + f.id) == null) {
                return new Plan(Plan.Kind.ASK_DRIVE, "drive:" + f.id, f.setting, false, false, f);
            }
        }
        return null;
    }

    // The best FULL battery setting of this control that also passed on the LOW battery, or null.
    private Test lowFinalist(SpeedControl c) {
        for (Test t : forLow(c)) {
            Test low = results.find(Battery.LOW, t.id);
            if (low != null && low.verdict == Verdict.PASS) return t;
        }
        return null;
    }

    private Plan trial(String id, FlywheelSetting s, boolean adjust, boolean compare) {
        return new Plan(Plan.Kind.TRIAL, id, s, adjust, compare, null);
    }

    // PID: today's kP, then the stronger ones. kI and kD stay; kF goes on from the last one's.
    List<Double> pidList() {
        List<Double> out = new ArrayList<>();
        out.add(config.today.kP);
        for (double kp : PID_KP) if (kp > config.today.kP + EPS) out.add(kp);
        return out;
    }

    private Plan nextPid() {
        FlywheelSetting base = config.today;
        double kF = base.kF;
        for (double kp : pidList()) {
            String id = "pid:kP=" + trim(kp);
            Test r = results.find(Battery.FULL, id);
            if (r == null) return trial(id, base.withPid(kp, kF), true, true);
            if (r.setting != null) kF = r.setting.kF;
            Reason why = Reason.of(r.why);
            if (r.verdict == Verdict.FAIL && why != null && why.unsafe) break;
        }
        return nextKi(kF);
    }

    // PID with kI: on the best kP that passed (else today's kP with the last kF), smallest kI first. It stops
    // at the first unsafe failure (too far past the target: a bigger kI only winds up more).
    private Plan nextKi(double lastKf) {
        if (config.today.kI != 0) return null;
        FlywheelSetting base = config.today.withPid(config.today.kP, lastKf);
        for (Test t : results.ranked(SpeedControl.PID)) {
            if (t.setting.kI == 0) {
                base = t.setting;
                break;
            }
        }
        for (double ki : PID_KI) {
            String id = "pid:kP=" + trim(base.kP) + ":kI=" + trim(ki);
            Test r = results.find(Battery.FULL, id);
            if (r == null) return trial(id, base.withKi(ki), true, true);
            Reason why = Reason.of(r.why);
            if (r.verdict == Verdict.FAIL && why != null && why.unsafe) return null;
        }
        return null;
    }

    // The FULL battery settings the LOW battery checks, best first: the best 3, and for PID also the best one
    // with kI (kI is there for the weaker battery, so the LOW battery must judge it).
    List<Test> forLow(SpeedControl c) {
        List<Test> ranked = results.ranked(c);
        List<Test> out = new ArrayList<>(ranked.subList(0, Math.min(3, ranked.size())));
        if (c == SpeedControl.PID) {
            for (Test t : ranked) {
                if (t.setting.kI != 0) {
                    if (!out.contains(t)) out.add(t);
                    break;
                }
            }
        }
        return out;
    }

    // Bang-bang's numbers that do not change between its candidates: {ks, kv, coast, step}.
    double[] bangBase() {
        Test fit = results.find(Battery.FULL, "fit");
        double ks = config.today.ks, kv = config.today.kv;
        if (fit != null && !Double.isNaN(fit.number("kv")) && fit.number("kv") > 0) {
            ks = fit.number("ks");
            kv = fit.number("kv");
        }
        double[] f = fullPower();
        double step = f == null || Double.isNaN(f[1]) ? 43 : f[1];
        // Coast a little more than one reading step above the target (one jumpy reading does not coast), and
        // inside the ready band (a coasting wheel is still "at speed").
        double coast = Math.max(10, Math.round(Math.min(1.5 * step, 0.8 * config.readyBandRpm)));
        // The kv to start from: the last bang-bang candidate's (its hold fine-tuned it), else the fit's.
        double start = kv;
        for (Test t : results.tests()) {
            if (t.battery == Battery.FULL && t.id.startsWith("bang:") && t.setting != null) start = t.setting.kv;
        }
        return new double[] {ks, start, coast, step, kv};
    }

    /** Bang-bang's full-power widths to try, wide first, RPM. */
    List<Double> bangWidths() {
        double[] f = fullPower();
        double step = bangBase()[3];
        double h1 = Math.max(1, Math.round(step));
        List<Double> out = new ArrayList<>();
        // The rise the control cannot see in time: acceleration x (the reading's lag + the longest loop).
        double unseen = f == null || Double.isNaN(f[0]) || Double.isNaN(f[2]) ? 150 : f[0] * f[2];
        for (double m : BANG_WIDTHS) {
            double w = Math.max(h1 + 25, Math.round(unseen * m / 5) * 5.0);
            if (!out.contains(w)) out.add(w);
        }
        return out;
    }

    private Plan nextBang() {
        double[] base = bangBase();
        double h1 = Math.max(1, Math.round(base[3])), h2 = Math.max(2, Math.round(2 * base[3]));
        Test best = null;
        int powerAt = 0;
        widths:
        for (double w : bangWidths()) {
            Test r = null;
            for (; powerAt < BANG_POWERS.length; powerAt++) {
                double most = BANG_POWERS[powerAt];
                String id = bangId(w, h1, most);
                r = results.find(Battery.FULL, id);
                if (r == null) return trial(id, config.today.withBang(base[0], base[1], w, base[2], h1, most), true, true);
                Reason why = Reason.of(r.why);
                // Too much for the motor or the battery: the same limits with less power (the next one).
                if (!(r.verdict == Verdict.FAIL && (why == Reason.CURRENT || why == Reason.VOLTAGE))) break;
            }
            if (powerAt >= BANG_POWERS.length) break;
            Reason why = Reason.of(r.why);
            if (r.verdict == Verdict.PASS && (best == null || FlywheelTuneResults.BETTER_FIRST.compare(r, best) < 0)) best = r;
            if (r.verdict == Verdict.FAIL && why != null && why.unsafe) break widths;
        }
        // The best width again with a hysteresis of 2 speed steps.
        if (best != null && best.setting.fullBelowRpm > h2 + 10) {
            String id = bangId(best.setting.fullBelowRpm, h2, best.setting.maxPower);
            if (results.find(Battery.FULL, id) == null) {
                FlywheelSetting s = best.setting;
                return trial(id, s.withBang(s.ks, s.kv, s.fullBelowRpm, s.coastAboveRpm, h2, s.maxPower), true, true);
            }
        }
        return null;
    }

    private static String bangId(double w, double h, double most) {
        return "bang:full=" + trim(w) + ":hyst=" + trim(h) + ":max=" + trim(most);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ---------------------------------------------------------------- moving on

    private void startCountdown() {
        lowVoltReadings = 0;
        stage = Stage.COUNTDOWN;
        stopWhy = Stop.NONE;
        stopDetail = "";
        countdownEnd = (Double.isNaN(now) ? 0 : now) + COUNTDOWN_SEC;
        guard.reset();
    }

    private void startTest(Sample s) {
        stage = Stage.TESTING;
        guard.reset();
        if (driveFor != null) {
            Test f = driveFor;
            driveFor = null;
            runner = new Trial("drive:" + f.id, Battery.LOW, f.setting, false, false, true, s.sec);
            return;
        }
        nextRunner(s.sec, null);
    }

    // The next test starts at once (one A press runs the whole battery run), or the run ends. After the
    // FULL battery's last test it stops for the battery change.
    private void nextRunner(double t, Battery was) {
        Plan p = plan();
        if (p == null || (was == Battery.FULL && planFull() == null)) {
            runner = null;
            stage = finishOrBetween();
            return;
        }
        Battery b = battery();
        switch (p.kind) {
            case ASK_DRIVE:
                runner = null;
                stage = Stage.ASK_DRIVE;
                return;
            case FULL_POWER:
                runner = new FullPower(b, t);
                return;
            case FIT:
                runner = new Fit(b, t);
                return;
            default:
                runner = new Trial(p.id, b, p.setting, p.adjust, p.compare, false, t);
        }
    }

    private Stage finishOrBetween() {
        if (planFull() != null) return Stage.READY;
        if (!anyFor(Battery.LOW)) return Stage.BETWEEN;
        if (planLow() != null) return Stage.READY;
        if (!results.isFinished()) record(new Test(Battery.LOW, "done", Verdict.INFO, "", null, new LinkedHashMap<String, Double>()));
        return Stage.FINISHED;
    }

    private void advanceAfterAsk() {
        Plan p = plan();
        if (p == null) {
            stage = finishOrBetween();
        } else if (p.kind == Plan.Kind.ASK_DRIVE) {
            stage = Stage.ASK_DRIVE;
        } else {
            stage = Stage.READY;
        }
    }

    private void record(Test t) {
        results.add(t);
        saves++;
    }

    // A test ended: save it, and go on.
    private void finished(Test t) {
        record(t);
        if (t.verdict == Verdict.FAIL && t.setting != null) {
            lastFailed = t.setting.label() + ": not good.\n  Why: " + t.why + ".";
        }
        boolean wasDrive = t.id.startsWith("drive:");
        runner = null;
        if (wasDrive) {
            advanceAfterAsk();
            return;
        }
        nextRunner(now, t.battery);
    }

    // ---------------------------------------------------------------- the tests

    private abstract class Runner {
        final String id;
        final Battery battery;
        double phaseAt;
        Output lastOut = Output.OFF;
        double restSince = Double.NaN;

        Runner(String id, Battery battery, double t) {
            this.id = id;
            this.battery = battery;
            this.phaseAt = t;
        }

        abstract Output step(Sample s, double gap);

        abstract String title();

        abstract String doing();

        double ampsSince = Double.NaN;

        // SAFETY, in every loop with power on: the motor over AMPS_LIMIT for AMPS_SEC. A measurement (full
        // power, the steady powers) then stops the run: something drags or jams the wheel.
        boolean overCurrent(Sample s) {
            if (!Double.isNaN(s.amps) && s.amps > AMPS_LIMIT) {
                if (Double.isNaN(ampsSince)) ampsSince = s.sec;
                return s.sec - ampsSince >= AMPS_SEC - EPS;
            }
            ampsSince = Double.NaN;
            return false;
        }

        // True once the wheel was under REST_RPM for REST_SEC. A wheel that never stops: the run stops.
        boolean rested(Sample s) {
            if (Math.abs(s.rpm) < REST_RPM) {
                if (Double.isNaN(restSince)) restSince = s.sec;
                if (s.sec - restSince >= REST_SEC - EPS) return true;
            } else {
                restSince = Double.NaN;
            }
            if (s.sec - phaseAt > REST_LIMIT) stop(Stop.NO_REST, "");
            return false;
        }
    }

    enum Phase { REST, SPINUP, HOLD, COAST, RECOVER, SETTLE, STEP_UP, STEP_DOWN, DRIVE_WAIT, DRIVING }

    // One setting: from a stop to the middle speed, hold, 3 pretend shots, a step up and a step down.
    // Or the driving check of a finalist.
    private final class Trial extends Runner {
        final boolean adjust, compare, drive;
        FlywheelSetting setting;
        Phase phase;
        double target;
        int shot = 0;
        // "At speed" now: in the band since (NaN = not in it), READY since, readings in a row off.
        double bandSince = Double.NaN, readyAt = Double.NaN, outAt = Double.NaN;
        int outCount = 0, overCount = 0, settledCount = 0, driveOut = 0;
        boolean enteredBand = false;
        // What it measures.
        double spinStart = Double.NaN, readySec = Double.NaN, peakOver = 0, fullSec = 0;
        double windowAt = Double.NaN, counts0 = Double.NaN, t0 = Double.NaN, sum = 0, sumSq = 0;
        int n = 0;
        double avgErr = Double.NaN, wobble = Double.NaN, resumeAt = Double.NaN;
        // The battery while it holds the speed (the average of the last hold's readings), volts.
        double holdVolts = Double.NaN, voltsSum = 0;
        int voltsN = 0;
        final List<Double> recoveries = new ArrayList<>();
        double lowSince = Double.NaN, startVolts = Double.NaN, minVolts = Double.NaN, peakAmps = Double.NaN;
        // The number the hold may fine-tune (PID: the written kF; bang-bang: the fitted kv), the middle of its range.
        final double adjustBase;

        Trial(String id, Battery battery, FlywheelSetting setting, boolean adjust, boolean compare, boolean drive, double t) {
            super(id, battery, t);
            this.setting = setting;
            this.adjust = adjust;
            this.compare = compare;
            this.drive = drive;
            this.adjustBase = setting.control == SpeedControl.PID ? config.today.kF : bangBase()[4];
            phase = drive ? Phase.DRIVE_WAIT : Phase.REST;
            if (drive) begin(Phase.DRIVE_WAIT, config.middleRpm, t);
        }

        @Override String title() {
            if (drive) return setting.label();
            if (id.equals("today")) return "today's settings (" + (setting.control == SpeedControl.PID ? "PID" : "bang-bang") + ")";
            if (id.equals("drift")) return "today's settings again";
            return setting.label();
        }

        @Override String doing() {
            switch (phase) {
                case REST: return "wait: flywheel rolls to a stop (power off)";
                case SPINUP: return "spin up from a stop";
                case HOLD: return "hold the speed";
                case COAST: return "pretend shot " + (shot + 1) + " of " + SHOTS + ": power off";
                case RECOVER:
                case SETTLE: return "pretend shot " + (shot + 1) + " of " + SHOTS + ": back to speed";
                case STEP_UP: return "change speed: up";
                case STEP_DOWN: return "change speed: down";
                case DRIVE_WAIT: return "wait: flywheel gets to speed";
                default: return "driving check";
            }
        }

        private void begin(Phase p, double newTarget, double t) {
            phase = p;
            phaseAt = t;
            target = newTarget;
            bandSince = Double.NaN;
            readyAt = Double.NaN;
            outCount = overCount = settledCount = driveOut = 0;
            enteredBand = false;
        }

        @Override Output step(Sample s, double gap) {
            double t = s.sec;
            if (phase == Phase.REST) {
                if (!rested(s)) return Output.OFF;
                startVolts = volts;
                spinStart = t;
                begin(Phase.SPINUP, config.middleRpm, t);
            }
            if (phase == Phase.COAST) {
                if (s.rpm <= target - config.dropRpm) {
                    resumeAt = t;
                    begin(Phase.RECOVER, target, t);
                } else {
                    if (t - phaseAt > COAST_LIMIT) return fail(Reason.TIMEOUT);
                    return Output.OFF;
                }
            }
            // The speed is held now: watch it.
            if (!Double.isNaN(s.amps)) peakAmps = Double.isNaN(peakAmps) ? s.amps : Math.max(peakAmps, s.amps);
            if (!Double.isNaN(s.volts) && s.volts > 1) minVolts = Double.isNaN(minVolts) ? s.volts : Math.min(minVolts, s.volts);
            if (setting.control == SpeedControl.BANG_BANG && !Double.isNaN(s.power) && s.power >= setting.maxPower - 1e-6) fullSec += gap;
            Reason limit = limits(s);
            if (limit != null) return fail(limit);
            double err = s.rpm - target;
            boolean inBand = Math.abs(err) <= config.readyBandRpm;
            if (inBand) {
                if (Double.isNaN(bandSince)) bandSince = t;
            } else {
                bandSince = Double.NaN;
            }
            boolean ready = inBand && t - bandSince >= config.settleSec - EPS;
            boolean steady = inBand && t - bandSince >= STEADY_SEC - EPS;
            if (ready && Double.isNaN(readyAt)) readyAt = t;
            if (phase == Phase.DRIVING) return driving(s, err, t);
            // "At speed", then lost (two readings in a row, so one jumpy reading does not count). Lost = more than
            // one reading step out of the band: a reading is whole encoder counts, so a wheel just inside the
            // band's edge can read one step out of it (R1, 2026-11-24: every test failed on that alone).
            boolean lost = Math.abs(err) > config.readyBandRpm + readingStep();
            if (!Double.isNaN(readyAt) && lost) {
                if (outCount++ == 0) outAt = t;
            } else {
                outCount = 0;
            }
            if (outCount >= 2 && phase != Phase.DRIVE_WAIT) {
                if (outAt - readyAt < FALSE_READY_SEC - EPS) return fail(Reason.FALSE_READY);
                readyAt = Double.NaN;
            }
            if (phase == Phase.HOLD) {
                settledCount = Math.abs(err) > SETTLED_RPM ? settledCount + 1 : 0;
                if (settledCount >= 2) return fail(Reason.SETTLED);
            } else if (phase != Phase.DRIVE_WAIT) {
                // Past the target: from below (a start, a shot, a step up), or under it after a step down.
                double over;
                if (phase == Phase.STEP_DOWN) {
                    if (s.rpm <= target + config.readyBandRpm) enteredBand = true;
                    over = enteredBand ? -err : 0;
                } else {
                    over = err;
                }
                peakOver = Math.max(peakOver, over);
                overCount = over > overLimit() ? overCount + 1 : 0;
                if (overCount >= 2) return fail(Reason.OVERSHOOT);
            }
            switch (phase) {
                case SPINUP:
                    if (steady) {
                        readySec = t - spinStart;
                        startHold(t);
                        break;
                    }
                    double today = todayReadySec(battery);
                    if (compare && !Double.isNaN(today) && t - spinStart > today + LATER_SEC) return fail(Reason.SLOWER);
                    if (t - phaseAt > SPINUP_LIMIT) return fail(Reason.NO_SPEED);
                    break;
                case HOLD:
                    Output o = hold(s, t);
                    if (o != null) return o;
                    break;
                case RECOVER:
                    if (ready) {
                        recoveries.add(readyAt - resumeAt);
                        phase = Phase.SETTLE;
                        phaseAt = t;
                        break;
                    }
                    if (t - phaseAt > RECOVER_LIMIT) return fail(Reason.RECOVERY);
                    break;
                case SETTLE:
                    if (steady) {
                        shot++;
                        if (shot < SHOTS) {
                            begin(Phase.COAST, target, t);
                            return Output.OFF;
                        }
                        Reason no = nextStep(t, s.rpm);
                        if (no != null) return fail(no);
                        if (phase == null) return pass();
                        break;
                    }
                    if (t - phaseAt > SETTLE_LIMIT) return fail(Reason.TIMEOUT);
                    break;
                case STEP_UP:
                case STEP_DOWN:
                    if (steady) {
                        Reason no = nextStep(t, s.rpm);
                        if (no != null) return fail(no);
                        if (phase == null) return pass();
                        break;
                    }
                    if (t - phaseAt > STEP_LIMIT) return fail(Reason.STEP);
                    break;
                case DRIVE_WAIT:
                    if (steady) begin(Phase.DRIVING, target, t);
                    else if (t - phaseAt > SPINUP_LIMIT) return fail(Reason.NO_SPEED);
                    break;
                default:
                    break;
            }
            return control();
        }

        private Output control() {
            return new Output(Output.Kind.CONTROL, 0, target, setting, phase == Phase.DRIVING);
        }

        private double readingStep() {
            double[] f = fullPower();
            return f == null || Double.isNaN(f[1]) ? 43 : f[1];
        }

        // The speed limit past the target, less half a reading step: a reading is rounded to a step, so a
        // reading at the limit can be a real speed half a step over it.
        private double overLimit() {
            double[] f = fullPower();
            double step = f == null || Double.isNaN(f[1]) ? 0 : f[1];
            return OVERSHOOT_RPM - step / 2;
        }

        // After steady READY: hold the speed. The average comes from the encoder's count over 2 s (each speed
        // reading is rounded to a step; the count is not). PID: kF in 1% steps until it is within 25 RPM.
        private Output hold(Sample s, double t) {
            if (t < windowAt - EPS) return null;
            if (Double.isNaN(t0)) {
                t0 = t;
                counts0 = s.counts;
                sum = sumSq = 0;
                n = 0;
                voltsSum = 0;
                voltsN = 0;
            }
            if (!Double.isNaN(s.volts) && s.volts > 1) {
                voltsSum += s.volts;
                voltsN++;
            }
            double err = s.rpm - target;
            sum += err;
            sumSq += err * err;
            n++;
            if (t - t0 < HOLD_WINDOW - EPS) return null;
            double mean = sum / n;
            if (voltsN > 0) holdVolts = voltsSum / voltsN;
            avgErr = !Double.isNaN(counts0) && !Double.isNaN(s.counts) && t > t0
                    ? (s.counts - counts0) / (t - t0) / config.countsPerRev * 60 - target : mean;
            // The count and the readings tell very different stories (a count that did not move, a clock that
            // jumped): the count cannot be trusted, so the readings' average is used.
            if (Math.abs(avgErr - mean) > 3 * readingStep()) avgErr = mean;
            wobble = Math.sqrt(Math.max(0, sumSq / n - mean * mean));
            if (Math.abs(avgErr) <= AVERAGE_RPM) {
                begin(Phase.COAST, target, t);
                return Output.OFF;
            }
            if (adjust && adjustBase > 0) {
                // Too slow: more steady power. 1% of the base a step, at most KF_RANGE either way.
                boolean pid = setting.control == SpeedControl.PID;
                double cur = pid ? setting.kF : setting.kv;
                double next = cur + KF_STEP * adjustBase * (avgErr < 0 ? 1 : -1);
                if (Math.abs(next - adjustBase) <= KF_RANGE * adjustBase + 1e-12) {
                    setting = pid ? setting.withPid(setting.kP, round(next, 4))
                            : setting.withBang(setting.ks, round(next, 8), setting.fullBelowRpm, setting.coastAboveRpm,
                                    setting.hysteresisRpm, setting.maxPower);
                    startHold(t + KF_SETTLE);
                    return null;
                }
            }
            return fail(Reason.AVERAGE);
        }

        private void startHold(double at) {
            phase = Phase.HOLD;
            phaseAt = now;
            windowAt = at;
            t0 = Double.NaN;
        }

        // The next speed change, or the end (phase = null). SAFETY: a PID step down is checked BEFORE it is
        // commanded: a lower target that would brake the motor hard fails the setting and is never sent.
        private Reason nextStep(double t, double rpmNow) {
            boolean up = config.highRpm > config.middleRpm + config.readyBandRpm;
            boolean down = config.lowRpm < config.middleRpm - config.readyBandRpm;
            if (phase == Phase.SETTLE && up) {
                begin(Phase.STEP_UP, config.highRpm, t);
            } else if ((phase == Phase.SETTLE || phase == Phase.STEP_UP) && down) {
                if (setting.control == SpeedControl.PID && hardReverse(setting, rpmNow, config.lowRpm, config.countsPerRev)) {
                    return Reason.REVERSE;
                }
                begin(Phase.STEP_DOWN, config.lowRpm, t);
            } else {
                phase = null;
            }
            return null;
        }

        private Output driving(Sample s, double err, double t) {
            driveOut = Math.abs(err) > config.readyBandRpm + DRIVE_EXTRA_RPM ? driveOut + 1 : 0;
            if (driveOut >= 2) return fail(Reason.DRIVE);
            if (t - phaseAt >= DRIVE_SEC - EPS) return pass();
            return control();
        }

        // The motor's current, the battery, and PID's hard braking: checked in every loop that holds a speed.
        private Reason limits(Sample s) {
            if (overCurrent(s)) return Reason.CURRENT;
            if (!Double.isNaN(s.volts) && s.volts > 1 && s.volts < VOLTS_LIMIT) {
                if (Double.isNaN(lowSince)) lowSince = s.sec;
                if (s.sec - lowSince >= VOLTS_SEC - EPS) return Reason.VOLTAGE;
            } else {
                lowSince = Double.NaN;
            }
            if (setting.control == SpeedControl.PID && hardReverse(setting, s.rpm, target, config.countsPerRev)) return Reason.REVERSE;
            return null;
        }

        private Output pass() {
            finished(new Test(battery, id, Verdict.PASS, "", setting, numbers()));
            return Output.OFF;
        }

        private Output fail(Reason r) {
            finished(new Test(battery, id, Verdict.FAIL, r.words, setting, numbers()));
            return Output.OFF;
        }

        private Map<String, Double> numbers() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("ready", readySec);
            m.put("over", peakOver);
            m.put("avg", avgErr);
            m.put("wobble", wobble);
            double sumR = 0, worst = Double.NaN;
            for (double r : recoveries) {
                sumR += r;
                worst = Double.isNaN(worst) ? r : Math.max(worst, r);
            }
            m.put("recovery", recoveries.size() == SHOTS ? sumR / SHOTS : Double.NaN);
            m.put("worstRecovery", worst);
            m.put("fullSec", fullSec);
            m.put("volts", startVolts);
            m.put("minVolts", minVolts);
            m.put("holdVolts", holdVolts);
            m.put("amps", peakAmps);
            return m;
        }
    }

    /**
     * PID's calculated hard brake: the push against a wheel that is too fast, kP x (speed - target) in counts
     * per second, is more than the steady push kF x target plus a fifth of full power (32767 is full power on
     * the hub). Bang-bang never brakes.
     */
    static boolean hardReverse(FlywheelSetting s, double rpm, double target, double countsPerRev) {
        double over = (rpm - target) * countsPerRev / 60, hold = target * countsPerRev / 60;
        return target > 0 && s.kP * over > s.kF * hold + 0.2 * 32767;
    }

    // Today's time to steady speed on this battery (NaN when it never got there).
    private double todayReadySec(Battery b) {
        Test t = results.find(b, "today");
        return t == null || t.verdict == Verdict.INFO ? Double.NaN : t.number("ready");
    }

    // Full power from a stop: how fast the wheel speeds up at shot speed, the reading's step, the delay.
    private final class FullPower extends Runner {
        boolean on = false;
        double onAt = Double.NaN, reachSec = Double.NaN, maxGap = 0, stepRpm = Double.NaN, lastRead = Double.NaN;
        final List<double[]> points = new ArrayList<>();

        FullPower(Battery battery, double t) {
            super("fullpower", battery, t);
        }

        private void stepFrom(double rpm) {
            if (!Double.isNaN(lastRead)) {
                double d = Math.abs(rpm - lastRead);
                if (d >= 5 && (Double.isNaN(stepRpm) || d < stepRpm)) stepRpm = d;
            }
            lastRead = rpm;
        }

        @Override String title() { return "full power: how fast it speeds up"; }

        @Override String doing() {
            return on ? "full power (it stops past " + num(config.middleRpm + 150, 0) + " RPM)" : "wait: flywheel rolls to a stop (power off)";
        }

        @Override Output step(Sample s, double gap) {
            double t = s.sec;
            // The reading's step: the smallest change from one reading to the next. Best seen while the wheel
            // coasts down slowly (here, before full power): under full power it can skip several steps a loop.
            stepFrom(s.rpm);
            if (!on) {
                if (!rested(s)) return Output.OFF;
                on = true;
                onAt = t;
                return new Output(Output.Kind.POWER, 1, 0, null, false);
            }
            if (overCurrent(s)) {
                stop(Stop.CURRENT, num(s.amps, 1));
                return Output.OFF;
            }
            maxGap = Math.max(maxGap, gap);
            if (Double.isNaN(reachSec) && s.rpm >= config.middleRpm) reachSec = t - onAt;
            if (s.rpm >= config.middleRpm - 600 && s.rpm <= config.middleRpm + 150) points.add(new double[] {t, s.rpm});
            if (s.rpm < config.middleRpm + 150 && t - onAt <= FULL_LIMIT) return new Output(Output.Kind.POWER, 1, 0, null, false);
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("accel", slope(points));
            m.put("step", stepRpm);
            m.put("gap", maxGap);
            m.put("delay", SENSOR_LAG_SEC + maxGap);
            m.put("reachSec", reachSec);
            m.put("volts", volts);
            String why = Double.isNaN(reachSec) ? "full power did not reach " + num(config.middleRpm, 0) + " RPM" : "";
            finished(new Test(battery, id, Verdict.INFO, why, null, m));
            return Output.OFF;
        }
    }

    // The best straight line through {time, speed} points: its slope, RPM per second (NaN with too few).
    static double slope(List<double[]> pts) {
        if (pts.size() < 2) return Double.NaN;
        double[] a = pts.get(0), b = pts.get(pts.size() - 1);
        // A light wheel passes the window in a few loops: then the first and last reading give the slope.
        if (pts.size() < 4 || b[0] - a[0] < 0.1) return b[0] - a[0] >= 0.03 ? (b[1] - a[1]) / (b[0] - a[0]) : Double.NaN;
        double st = 0, sr = 0, stt = 0, str = 0;
        for (double[] p : pts) {
            st += p[0];
            sr += p[1];
            stt += p[0] * p[0];
            str += p[0] * p[1];
        }
        int k = pts.size();
        double den = k * stt - st * st;
        if (Math.abs(den) < 1e-12) return Double.NaN;
        return (k * str - st * sr) / den;
    }

    // The power each speed needs: 3 steady speeds across the shot speeds, then volts = ks + kv x RPM. At each
    // speed a slow, gentle control (a PI: a push for the error now and one that grows with an old error) finds
    // the power that holds it; that power, over 2 s, is the point. WHY a control and not a fixed power: a heavy
    // wheel takes many seconds to settle at a fixed power, and a point measured before it settles is wrong.
    private final class Fit extends Runner {
        final double[] speeds;
        int at = -1;
        boolean boosting = false;
        double start = 0, integral = 0, power = 0, plateauAt = Double.NaN, windowAt = Double.NaN, counts0 = Double.NaN;
        double sumPV = 0, sumT = 0, sumR = 0;
        int nR = 0;
        final double kp, ki;
        final List<double[]> recent = new ArrayList<>();     // {time, error RPM} of the last second
        final List<double[]> recentVolts = new ArrayList<>(); // {time, volts} of the last second
        final List<double[]> found = new ArrayList<>();       // {RPM, volts at the motor}
        String why = "";

        Fit(Battery battery, double t) {
            super("fit", battery, t);
            double lo = config.lowRpm, mid = config.middleRpm, hi = config.highRpm;
            if (hi - lo < 300) {
                lo = mid * 0.85;
                hi = mid * 1.15;
            }
            speeds = new double[] {lo, mid, hi};
            // RPM for each unit of power (from today's kv), and how slow the wheel is (from full power's speed-up).
            double v = Double.isNaN(volts) ? 12.8 : volts;
            double rpmPerPower = v / Math.max(1e-6, config.today.kv);
            double[] f = fullPower();
            double slow = f == null || Double.isNaN(f[0]) || f[0] <= 0 ? 1.5
                    : Math.max(0.3, Math.min(5, (rpmPerPower - mid) / f[0]));
            // The push gives 4 x the wheel's own response; the old-error part cancels its slowness.
            kp = 4 / rpmPerPower;
            ki = kp / slow;
        }

        @Override String title() { return "the power each speed needs"; }

        @Override String doing() {
            if (at < 0) return "wait: flywheel rolls to a stop (power off)";
            return "steady speed " + (at + 1) + " of " + speeds.length + " (" + num(speeds[at], 0) + " RPM)";
        }

        private double guess(double rpm) {
            double v = Double.isNaN(volts) ? 12.8 : volts;
            return Math.max(0.05, Math.min(1, (config.today.ks + config.today.kv * rpm) / v));
        }

        private void next(double t) {
            at++;
            if (at >= speeds.length) return;
            boosting = true;
            plateauAt = t;
            windowAt = Double.NaN;
            integral = 0;
            start = guess(speeds[at]);
            recent.clear();
        }

        @Override Output step(Sample s, double gap) {
            double t = s.sec;
            if (at < 0) {
                if (!rested(s)) return Output.OFF;
                next(t);
            }
            if (at >= speeds.length) return done();
            if (overCurrent(s)) {
                stop(Stop.CURRENT, num(s.amps, 1));
                return Output.OFF;
            }
            double s0 = speeds[at];
            if (!Double.isNaN(s.volts) && s.volts > 1) recentVolts.add(new double[] {t, s.volts});
            final double oldest = t - 1;
            recentVolts.removeIf(r -> r[0] < oldest);
            if (s.rpm > speeds[speeds.length - 1] + 400) {
                // SAFETY: far too fast for this test. Power off; this speed gives no point.
                why = "a speed went too high";
                next(t);
                return at >= speeds.length ? done() : Output.OFF;
            }
            if (boosting) {
                // Full power to just under the speed, so each one settles fast.
                if (s.rpm < s0 - 150 && t - plateauAt < FULL_LIMIT) return power(1);
                boosting = false;
                plateauAt = t;
            }
            if (t - plateauAt > PLATEAU_LIMIT) {
                why = "a speed did not settle";
                next(t);
                // Off for this loop: the wheel may already be at or past the next speed. The next loop decides.
                return at >= speeds.length ? done() : power(0);
            }
            double err = s0 - s.rpm;
            // The old-error part, kept so the power stays between 0 and 1.
            integral = Math.max(-start, Math.min(1 - start, integral + ki * err * gap));
            double p = Math.max(0, Math.min(1, start + kp * err + integral));
            if (Double.isNaN(windowAt)) {
                // Steady: within 2 reading steps of the speed for the last 1 s, and at least 1.5 s in.
                recent.add(new double[] {t, Math.abs(err)});
                recent.removeIf(r -> r[0] < oldest);
                double worst = 0;
                for (double[] r : recent) worst = Math.max(worst, r[1]);
                double step = fullPower() == null || Double.isNaN(fullPower()[1]) ? 43 : fullPower()[1];
                if (t - plateauAt >= 1.5 && worst <= Math.max(2 * step, 30)) {
                    windowAt = t;
                    counts0 = s.counts;
                    sumPV = sumT = sumR = 0;
                    nR = 0;
                }
                return power(p);
            }
            // The power since the last loop x the battery (the highest of the last second, as the flywheel's own
            // bang-bang divides by), for the time it was on.
            double v = maxVolts();
            if (!Double.isNaN(s.power) && !Double.isNaN(v)) {
                sumPV += s.power * v * gap;
                sumT += gap;
            }
            sumR += s.rpm;
            nR++;
            if (t - windowAt < HOLD_WINDOW - EPS) return power(p);
            double rpm = !Double.isNaN(counts0) && !Double.isNaN(s.counts) ? (s.counts - counts0) / (t - windowAt) / config.countsPerRev * 60 : sumR / nR;
            if (sumT > 0 && rpm > 100) found.add(new double[] {rpm, sumPV / sumT});
            next(t);
            return at >= speeds.length ? done() : power(1);
        }

        private double maxVolts() {
            double v = Double.NaN;
            for (double[] r : recentVolts) v = Double.isNaN(v) ? r[1] : Math.max(v, r[1]);
            return Double.isNaN(v) ? volts : v;
        }

        private Output power(double p) {
            power = p;
            return new Output(Output.Kind.POWER, p, 0, null, false);
        }

        private Output done() {
            Map<String, Double> m = new LinkedHashMap<>();
            double[] ksKv = fitLine(found);
            m.put("ks", ksKv[0]);
            m.put("kv", ksKv[1]);
            m.put("points", (double) found.size());
            m.put("volts", volts);
            if (Double.isNaN(ksKv[1]) && why.isEmpty()) why = "not enough steady speeds";
            finished(new Test(battery, id, Verdict.INFO, Double.isNaN(ksKv[1]) ? why : "", null, m));
            return Output.OFF;
        }
    }

    /**
     * volts = ks + kv x RPM through the points {RPM, volts}: {ks, kv}. Needs 3 points at least 300 RPM apart;
     * a ks under 0 is made 0 (the line then goes through 0). {NaN, NaN} when it cannot.
     */
    static double[] fitLine(List<double[]> pts) {
        if (pts.size() < 3) return new double[] {Double.NaN, Double.NaN};
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE, sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (double[] p : pts) {
            lo = Math.min(lo, p[0]);
            hi = Math.max(hi, p[0]);
            sx += p[0];
            sy += p[1];
            sxx += p[0] * p[0];
            sxy += p[0] * p[1];
        }
        if (hi - lo < 300) return new double[] {Double.NaN, Double.NaN};
        int k = pts.size();
        double kv = (k * sxy - sx * sy) / (k * sxx - sx * sx);
        double ks = (sy - kv * sx) / k;
        if (ks < 0) {
            ks = 0;
            kv = sxy / sxx;
        }
        if (!(kv > 0)) return new double[] {Double.NaN, Double.NaN};
        return new double[] {round(ks, 4), round(kv, 7)};
    }

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }
}
