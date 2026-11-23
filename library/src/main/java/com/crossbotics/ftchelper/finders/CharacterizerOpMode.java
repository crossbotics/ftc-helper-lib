package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.util.BackgroundWorker;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

/**
 * Measures how the real robot moves, so the simulator (and your code) can use real numbers: for the
 * drivetrain, each lift or arm, and each flywheel, it finds the feedforward numbers (kS = power to start
 * moving, kV = power per speed, kA = power per speeding-up, kG = power to hold a lift) and how fast a
 * flywheel coasts down. It gives each motor slow ramps of power and quick steps, logs the battery,
 * power and speed, and fits the numbers (see CharacterizeMath).
 *
 * <pre>
 * D-pad up/down = choose a test, A = run it, B = stop it now.
 *   Drive: ramps forward then back, then quick steps. About 5 ft clear in FRONT and BEHIND.
 *   Turn:  turns in place both ways (needs the IMU).
 *   Each lift/arm from mechanisms(): start it at the BOTTOM. It moves between your soft limits.
 *   Each flywheel: spins up slowly, coasts, spins up fast, coasts. Hands and pieces away.
 * </pre>
 *
 * <b>Advanced: a team can skip it.</b> A test run again forgets its old lines first; a poor match says NOT
 * DONE. The results are lines for the simulator's robot file and Feedforward lines for your code; they are
 * saved with the other finders' results. Each test's log is saved too (characterizer/*.csv in the same
 * folder) for the simulator's fit tool. In your TeamCode:
 * <pre>{@code
 * @TeleOp(name = "Characterizer", group = "Finders")
 * public class Characterizer extends CharacterizerOpMode {
 *     protected RobotConfig robotConfig() { return new RobotSettings(); }
 *     protected List<Mechanism> mechanisms() {
 *         return Arrays.asList(Mechanism.lift("lift", 0, 2700), Mechanism.flywheel("flywheel"));
 *     }
 * }
 * }</pre>
 *
 * Docs: docs/modules/finders.md ("Characterizer"). Guide: docs/simulator/03-make-it-match-your-robot.md (Real speeds)
 */
public abstract class CharacterizerOpMode extends FinderOpMode {

    // Its numbers are powers or speeds: they depend on the battery.
    @Override
    protected boolean batteryMatters() {
        return true;
    }

    /** A lift, arm or flywheel to measure. */
    public static final class Mechanism {
        final String motor;
        final boolean flywheel;
        final int low, high;
        final boolean reversed;

        private Mechanism(String motor, boolean flywheel, int low, int high, boolean reversed) {
            this.motor = motor;
            this.flywheel = flywheel;
            this.low = low;
            this.high = high;
            this.reversed = reversed;
        }

        /**
         * A lift (or an arm: treated like a lift, with the same pull of gravity everywhere). Start it at the
         * bottom: the soft limits are counts from where it is when its test starts, and it stays between them.
         *
         * @param motor     the motor's configuration name
         * @param lowTicks  the lowest it may go (usually 0: where it starts)
         * @param highTicks the highest it may go (below the hard stop)
         */
        public static Mechanism lift(String motor, int lowTicks, int highTicks) {
            if (highTicks <= lowTicks + 50) throw new IllegalArgumentException("Mechanism " + motor + ": the high limit must be above the low one");
            return new Mechanism(motor, false, lowTicks, highTicks, false);
        }

        /** A flywheel (or any motor that may spin freely: an intake, a spinner). */
        public static Mechanism flywheel(String motor) {
            return new Mechanism(motor, true, 0, 0, false);
        }

        /** Positive power makes it go DOWN (or backward): flip it, like your robot code does. */
        public Mechanism reversed() {
            return new Mechanism(motor, flywheel, low, high, true);
        }
    }

    /** Your robot's settings (drive motors, IMU). */
    protected abstract RobotConfig robotConfig();

    /** The lifts, arms and flywheels to measure. Default: none (drive and turn only). */
    protected List<Mechanism> mechanisms() {
        return Collections.emptyList();
    }

    /** The highest power the drive and turn ramps reach (default 0.4, at most 0.5). Lower it for a small space. */
    protected double driveRampMax() {
        return 0.4;
    }

    /**
     * The farthest the drive test may roll in one go, in wheel counts (default 2500: about 4.5 ft with
     * 312 rpm motors and 104 mm wheels; fewer feet with smaller counts per inch). A ramp or step that
     * gets this far ends early. Lower it for a small space.
     */
    protected int maxDriveCounts() {
        return 2500;
    }

    // ---------------------------------------------------------------- the tests as phases

    // One part of a test: a power that changes with time, until a time limit or until it is done.
    private interface PowerAt { double at(double sec); }
    // sec = time in the phase; pos = counts from the test's start; moved = counts since the phase began;
    // vel = counts per second now.
    private interface DoneWhen { boolean done(double sec, double pos, double moved, double vel); }

    private static final class Phase {
        final String name;
        final double maxSec;
        final PowerAt power;
        final DoneWhen done;

        Phase(String name, double maxSec, PowerAt power, DoneWhen done) {
            this.name = name;
            this.maxSec = maxSec;
            this.power = power;
            this.done = done;
        }
    }

    private enum Kind { DRIVE, TURN, LIFT, FLYWHEEL }

    private static final class Test {
        final String name;
        final Kind kind;
        final Mechanism mech;

        Test(String name, Kind kind, Mechanism mech) {
            this.name = name;
            this.kind = kind;
            this.mech = mech;
        }
    }

    private final List<Test> tests = new ArrayList<>();
    private int chosen = 0;
    private DriveConfig drive;
    private IMU imu;
    private final List<DcMotorEx> left = new ArrayList<>(), right = new ArrayList<>();
    private List<VoltageSensor> batteries;

    // The test that is running (null = choosing).
    private Test running = null;
    private List<Phase> phases;
    private int phase;
    private double phaseStart, testStart, phaseStartPos, stalledSec;
    private double[] wheelStalledSec = new double[0];   // per drive wheel: one jammed wheel hides in an average
    private double lastLoop;
    private final List<String> wheelNames = new ArrayList<>();
    private DcMotorEx mechMotor;
    private double startPos;
    private String status = "Next: choose a test. {A} = run it.";
    // Each test that ran, and whether it ended with a good match.
    private final java.util.Map<String, Boolean> good = new java.util.LinkedHashMap<>();
    // How the last log was saved (written by the saving thread).
    private volatile String logNote = "";
    // The log of the running test: t, volts, power, pos, vel, turnVel, headingDeg.
    private final List<double[]> log = new ArrayList<>();
    private static BackgroundWorker saver;

    @Override protected String finderName() { return "Characterizer"; }

    @Override
    protected void setup() {
        RobotConfig config = robotConfig();
        drive = config.drive();
        imu = config.imu(hardwareMap);
        left.add(wheel(drive.frontLeft, drive.frontLeftReversed));
        right.add(wheel(drive.frontRight, drive.frontRightReversed));
        wheelNames.add(drive.frontLeft);
        wheelNames.add(drive.frontRight);
        if (drive.hasBackMotors()) {
            left.add(wheel(drive.backLeft, drive.backLeftReversed));
            right.add(wheel(drive.backRight, drive.backRightReversed));
            wheelNames.add(drive.backLeft);
            wheelNames.add(drive.backRight);
        }
        batteries = hardwareMap.getAll(VoltageSensor.class);
        tests.add(new Test("Drive (forward and back)", Kind.DRIVE, null));
        if (imu != null) tests.add(new Test("Turn in place", Kind.TURN, null));
        for (Mechanism m : mechanisms()) {
            hardwareMap.get(DcMotorEx.class, m.motor);   // a wrong name stops here, in INIT, with a clear message
            tests.add(new Test((m.flywheel ? "Flywheel: " : "Lift or arm: ") + m.motor, m.flywheel ? Kind.FLYWHEEL : Kind.LIFT, m));
        }
    }

    private DcMotorEx wheel(String name, boolean reversed) {
        DcMotorEx m = hardwareMap.get(DcMotorEx.class, name);
        m.setDirection(reversed ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD);
        m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);   // plain power: the test is about the motor itself
        m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        return m;
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.warn("After {A} the robot DRIVES and TURNS by itself.");
        t.warn("Lifts and flywheels move too. Hands clear.");
        t.warn("Clear 5 ft in front and behind. Glasses on.");
        t.warn("{B}, {GUIDE} or {START} = stop.");
        t.text("Characterizer. Advanced: you can skip this.");
        t.text("It measures motors, mostly for the simulator.");
        t.text("You need: lifts and arms at the bottom.");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Advanced: you can skip this tool.",
            "D-pad up/down: choose a test. {A}: run it.",
            "{B}, {GUIDE} or {START}: stop the test now.",
            "Drive: forward and back, slow, then quick.",
            "  Clear 5 ft in front and behind.",
            "Turn: turns in place both ways. Clear all round.",
            "Lift or arm: start at the BOTTOM. It moves",
            "  between the limits in mechanisms().",
            "Flywheel: spins up and rolls down, twice.",
            "  Safety glasses. Hands and pieces away.",
            "Run each test 2-3 times on a charged battery.",
            "The lines are NOT for Tunables.java.",
            "Logs: characterizer/*.csv for the fit tool.",
        };
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> { if (running == null) chosen = (chosen + tests.size() - 1) % tests.size(); })
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> { if (running == null) chosen = (chosen + 1) % tests.size(); })
         .bindPress(Pad.G1, Btn.A, () -> { if (running == null && inputsReady()) begin(tests.get(chosen)); })
         .bindPress(Pad.G1, Btn.B, () -> { if (running != null) finish("Stopped with {B}: not measured."); });
    }

    private double now() {
        return System.nanoTime() / 1e9;
    }

    // ---------------------------------------------------------------- starting a test

    private void begin(Test test) {
        running = test;
        // This test again: its old lines are not true until it fits again.
        results.removeStartingWith(keyOf(test) + ":");
        good.put(keyOf(test), false);
        updateStatus();
        logNote = "";
        log.clear();
        phases = new ArrayList<>();
        phase = 0;
        testStart = phaseStart = lastLoop = now();
        phaseStartPos = Double.NaN;      // set from the first reading: the encoders need not start at 0
        stalledSec = 0;
        wheelStalledSec = new double[left.size() + right.size()];
        double ramp = Math.max(0.1, Math.min(0.5, driveRampMax()));
        switch (test.kind) {
            case DRIVE:
            case TURN: {
                // slow ramps (speed follows power: kS and kV), then quick steps (speeding up: kA). A drive
                // phase also ends when it has rolled maxDriveCounts().
                double rate = 0.1;   // power per second
                int far = maxDriveCounts();
                DoneWhen tooFar = test.kind == Kind.DRIVE ? (sec, pos, moved, vel) -> Math.abs(moved) >= far : null;
                phases.add(new Phase("forward, speeding up", ramp / rate, s -> Math.min(ramp, rate * s), tooFar));
                phases.add(new Phase("stop", 0.8, s -> 0, null));
                phases.add(new Phase("back, speeding up", ramp / rate, s -> -Math.min(ramp, rate * s), tooFar));
                phases.add(new Phase("stop", 0.8, s -> 0, null));
                // not too hard: spinning wheels would fake a quick speed-up
                double step = Math.min(0.5, ramp * 1.25);
                phases.add(new Phase("forward, quick push", 0.6, s -> step, tooFar));
                phases.add(new Phase("stop", 0.8, s -> 0, null));
                phases.add(new Phase("back, quick push", 0.6, s -> -step, tooFar));
                phases.add(new Phase("stop", 0.8, s -> 0, null));
                break;
            }
            case LIFT: {
                Mechanism m = test.mech;
                mechMotor = mechanism(m, DcMotor.ZeroPowerBehavior.BRAKE);
                startPos = mechMotor.getCurrentPosition();
                double span = m.high - m.low;
                double top = m.low + 0.6 * span, bottom = m.low + 0.1 * span;
                phases.add(new Phase("up slowly", 25, s -> Math.min(0.6, 0.04 * s), (s, p, d, v) -> p >= top));
                phases.add(new Phase("hold", 0.6, s -> 0, null));
                phases.add(new Phase("down slowly", 25, s -> -Math.min(0.6, 0.04 * s), (s, p, d, v) -> p <= bottom));
                phases.add(new Phase("hold", 0.6, s -> 0, null));
                // quick steps end early enough to stop in time: where it will be 0.15 s from now
                phases.add(new Phase("up fast", 3, s -> 0.45, (s, p, d, v) -> p + v * 0.15 >= m.low + 0.75 * span));
                phases.add(new Phase("hold", 0.6, s -> 0, null));
                phases.add(new Phase("down fast", 3, s -> -0.3, (s, p, d, v) -> p + v * 0.15 <= m.low + 0.2 * span));
                phases.add(new Phase("hold", 0.6, s -> 0, null));
                // back to (nearly) the bottom, so the next run's limits start from the same place
                phases.add(new Phase("back down", 10, s -> -Math.min(0.3, 0.04 * s), (s, p, d, v) -> p <= m.low + 0.02 * span));
                phases.add(new Phase("hold", 0.3, s -> 0, null));
                break;
            }
            default: {
                mechMotor = mechanism(test.mech, DcMotor.ZeroPowerBehavior.FLOAT);
                startPos = mechMotor.getCurrentPosition();
                phases.add(new Phase("speeding up", 5, s -> Math.min(1, 0.2 * s), null));
                phases.add(new Phase("rolling to a stop", 4, s -> 0, null));
                phases.add(new Phase("full power", 3, s -> 1, null));
                phases.add(new Phase("rolling to a stop", 3, s -> 0, null));
            }
        }
        status = test.name + ": running.";
    }

    private DcMotorEx mechanism(Mechanism m, DcMotor.ZeroPowerBehavior zero) {
        DcMotorEx motor = hardwareMap.get(DcMotorEx.class, m.motor);
        motor.setDirection(m.reversed ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD);
        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        motor.setZeroPowerBehavior(zero);
        return motor;
    }

    // ---------------------------------------------------------------- every loop

    @Override
    protected void step() {
        if (running == null) return;
        // GUIDE or START (the help keys) stops a moving test at once, before help opens.
        if (helpKeyDown()) {
            finish("Stopped: you pressed {GUIDE} or {START}.\nNot measured.");
            return;
        }
        double t = now(), s = t - phaseStart, loopSec = t - lastLoop;
        lastLoop = t;
        Phase ph = phases.get(phase);
        double volts = battery();
        // The IMU only when turning (it is a slow read, and a slow loop spoils the fit).
        double power, pos, vel, turnVel = 0, heading = imu != null && running.kind == Kind.TURN ? imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES) : 0;
        power = ph.power.at(s);
        if (running.kind == Kind.DRIVE || running.kind == Kind.TURN) {
            double l = 0, r = 0, lp = 0, rp = 0;
            List<DcMotorEx> wheels = new ArrayList<>();
            for (int i = 0; i < left.size(); i++) { wheels.add(left.get(i)); wheels.add(right.get(i)); }
            for (int i = 0; i < wheels.size(); i++) {
                // Each wheel must turn the way its own power pushes it (one reversed wheel is the classic mistake).
                boolean isLeft = i % 2 == 0;
                double wheelPower = isLeft || running.kind == Kind.DRIVE ? power : -power;
                double wv = wheels.get(i).getVelocity();
                // Each pushed wheel must move too: one jammed wheel would hide in the average below.
                wheelStalledSec[i] = Math.abs(wheelPower) > 0.25 && Math.abs(wv) < 20 ? wheelStalledSec[i] + loopSec : 0;
                if (wheelStalledSec[i] > 0.7) {
                    finish("Stopped: wheel \"" + wheelNames.get(i) + "\" is not turning."
                            + "\nNext: check for a jam. Run Encoder Check.");
                    return;
                }
                if (Math.abs(wheelPower) > 0.3 && Math.signum(wv) == -Math.signum(wheelPower) && Math.abs(wv) > 50) {
                    finish("Stopped: wheel \"" + wheelNames.get(i) + "\" turned the WRONG way."
                            + "\nNext: run Motor Direction Check.");
                    return;
                }
            }
            for (DcMotorEx m : left) { l += m.getVelocity() / left.size(); lp += m.getCurrentPosition() / (double) left.size(); }
            for (DcMotorEx m : right) { r += m.getVelocity() / right.size(); rp += m.getCurrentPosition() / (double) right.size(); }
            boolean turn = running.kind == Kind.TURN;
            vel = turn ? (l - r) / 2 : (l + r) / 2;
            turnVel = (l - r) / 2;
            pos = turn ? (lp - rp) / 2 : (lp + rp) / 2;
        } else {
            vel = mechMotor.getVelocity();
            pos = mechMotor.getCurrentPosition() - startPos;
        }
        if (Double.isNaN(phaseStartPos)) phaseStartPos = pos;
        // Safety: pushing but not moving (jammed, pushing into a hard stop, or an encoder in another port):
        // stop after 0.7 s. A lift going up needs more power before it moves (gravity holds it); going down,
        // gravity helps, so a little power must move it.
        boolean pushed = running.kind != Kind.LIFT ? Math.abs(power) > 0.25 : power >= 0.4 || power <= -0.2;
        stalledSec = pushed && Math.abs(vel) < 20 ? stalledSec + loopSec : 0;
        if (stalledSec > 0.7) {
            finish("Stopped: it pushed but did not move."
                    + "\nJammed, or at its end (wrong direction)?"
                    + "\nNext: check that, and run Encoder Check.");
            return;
        }
        // Safety: a lift never goes past its soft limits, whatever the power (an encoder that counts the
        // wrong way would otherwise let it run on), and it must move the way the power pushes it.
        if (running.kind == Kind.LIFT && (pos > running.mech.high || pos < running.mech.low - 100)) {
            finish("Stopped: lift past its limit (" + Math.round(pos) + " counts)."
                    + "\nNext: check its limits and .reversed()."
                    + "\nIts count must go UP when it goes up.");
            return;
        }
        if (running.kind == Kind.LIFT && phase == 0 && power > 0.3 && pos < running.mech.low - 60) {
            finish("Stopped: going up, the count went DOWN.\nNext: check .reversed() and its encoder cable.");
            return;
        }
        // Safety: pushing hard but moving the wrong way = the motor direction is wrong.
        if (Math.abs(power) > 0.3 && Math.signum(vel) == -Math.signum(power) && Math.abs(vel) > 50 && running.kind != Kind.FLYWHEEL) {
            finish("Stopped: it moved the WRONG way.\nNext: check the motor's direction (reversed).");
            return;
        }
        apply(power);
        // At most 200 moments a second (a robot loop is slower; the simulator's can be much faster).
        if (log.isEmpty() || t - testStart - log.get(log.size() - 1)[0] >= 0.005) {
            log.add(new double[] {t - testStart, volts, power, pos, vel, turnVel, heading});
        }
        boolean done = s >= ph.maxSec || (ph.done != null && ph.done.done(s, pos, pos - phaseStartPos, vel));
        if (running.kind == Kind.LIFT && phase == 0 && s >= ph.maxSec) {
            finish("Stopped: the lift was too slow (25 s).\nNext: is it free? Are its limits right?");
            return;
        }
        // A lift move that runs out of time without reaching its place: something holds it. Stop.
        if (running.kind == Kind.LIFT && ph.done != null && s >= ph.maxSec) {
            finish("Stopped: \"" + ph.name + "\" took over " + Math.round(ph.maxSec) + " s."
                    + "\nNext: is it caught on something?");
            return;
        }
        if (done) {
            phase++;
            phaseStart = t;
            phaseStartPos = pos;
            stalledSec = 0;
            if (phase >= phases.size()) finish(null);
        }
        if (running != null) status = running.name + "\nNow: " + phases.get(phase).name;
    }

    private void apply(double power) {
        if (running == null) return;
        if (running.kind == Kind.DRIVE) {
            for (DcMotorEx m : left) m.setPower(power);
            for (DcMotorEx m : right) m.setPower(power);
        } else if (running.kind == Kind.TURN) {
            for (DcMotorEx m : left) m.setPower(power);     // + = clockwise
            for (DcMotorEx m : right) m.setPower(-power);
        } else {
            mechMotor.setPower(power);
        }
    }

    private double battery() {
        double v = Double.MAX_VALUE;
        for (VoltageSensor s : batteries) {
            double x = s.getVoltage();
            if (x > 1) v = Math.min(v, x);
        }
        return v == Double.MAX_VALUE ? CharacterizeMath.NOMINAL_VOLTS : v;
    }

    // ---------------------------------------------------------------- the end of a test

    private void finish(String stoppedBecause) {
        Test test = running;
        apply(0);
        running = null;
        if (stoppedBecause != null) {
            status = stoppedBecause;
            saveLog(test, false);
            return;
        }
        saveLog(test, true);
        status = test.name + ": done.\n" + fitAndShow(test) + (test.kind == Kind.LIFT ? "\nIt is back near the bottom." : "");
    }

    // Fits the log and puts the lines in the results. Says how good the fit is.
    private String fitAndShow(Test test) {
        int n = log.size();
        double[] t = new double[n], volts = new double[n], power = new double[n], vel = new double[n], turnVel = new double[n], heading = new double[n];
        for (int i = 0; i < n; i++) {
            double[] r = log.get(i);
            t[i] = r[0]; volts[i] = r[1]; power[i] = r[2]; vel[i] = r[4]; turnVel[i] = r[5]; heading[i] = r[6];
        }
        CharacterizeMath.Fit f = CharacterizeMath.fit(t, volts, power, vel, test.kind == Kind.LIFT);
        if (!f.ok()) {
            return "Could not get a result: too little moving, or\nthe readings do not agree (" + FinderResults.num(f.r2, 2)
                    + ").\nNext: run it again.";
        }
        String key = keyOf(test);
        good.put(key, f.r2 >= GOOD_MATCH);
        updateStatus();
        String ff = "new Feedforward(" + sci(f.kS) + ", " + sci(f.kV) + ", " + sci(f.kA) + (test.kind == Kind.LIFT ? ", " + sci(f.kG) : "") + ")";
        switch (test.kind) {
            case DRIVE:
                results.setIn(key + ":sim", SIM_FILE,
                        "drive.kS = " + FinderResults.num(f.kS, 3),
                        "drive.maxTicksPerSec = " + FinderResults.num(f.ticksPerSecPerPower(), 0),
                        "drive.accelSec = " + FinderResults.num(f.accelSec(), 3));
                results.setIn(key + ":ff", JAVA, "Feedforward driveFf =", "  " + ff + ";");
                break;
            case TURN: {
                double degPerCount = CharacterizeMath.turnDegPerCount(turnVel, CharacterizeMath.yawRates(t, heading));
                results.setIn(key + ":sim", SIM_FILE, "drive.turnDegPerCount = " + FinderResults.num(degPerCount, 5));
                results.setIn(key + ":ff", JAVA, "Feedforward turnFf =", "  " + ff + ";");
                break;
            }
            case LIFT:
                results.setIn(key + ":sim", SIM_FILE, "add to the line motor." + test.mech.motor + ":",
                        "  kS=" + FinderResults.num(f.kS, 3) + ", maxTicksPerSec=" + FinderResults.num(f.ticksPerSecPerPower(), 0) + ",",
                        "  accelSec=" + FinderResults.num(f.accelSec(), 3) + ", gravity=" + FinderResults.num(f.kG, 3));
                results.setIn(key + ":ff", JAVA, "Feedforward " + test.mech.motor + "Ff =", "  " + ff + ";");
                break;
            default: {
                double coast = CharacterizeMath.coastSec(t, power, vel);
                results.setIn(key + ":sim", SIM_FILE, "add to the line motor." + test.mech.motor + ":",
                        "  kS=" + FinderResults.num(f.kS, 3) + ", maxTicksPerSec=" + FinderResults.num(f.ticksPerSecPerPower(), 0) + ",",
                        "  accelSec=" + FinderResults.num(f.accelSec(), 3)
                                + (Double.isNaN(coast) ? "" : ", coastSec=" + FinderResults.num(coast, 2)));
                results.setIn(key + ":ff", JAVA, "Feedforward " + test.mech.motor + "Ff =", "  " + ff + ";");
            }
        }
        return "Match " + FinderResults.num(f.r2, 3) + (f.r2 < GOOD_MATCH ? ": POOR (good is 0.9 or more)."
                + "\nNext: run it again on a charged battery." : ": good (0.9 or more).");
    }

    /** A match (r2) at least this good is trusted. */
    private static final double GOOD_MATCH = 0.9;
    private static final String SIM_FILE = "In your simulator robot file (not Java):";
    private static final String JAVA = "Advanced Java (never in Tunables.java):";

    private static String keyOf(Test test) {
        return test.kind.name().toLowerCase(Locale.US) + (test.mech != null ? ":" + test.mech.motor : "");
    }

    private void updateStatus() {
        for (java.util.Map.Entry<String, Boolean> e : good.entrySet()) {
            if (!e.getValue()) {
                results.notDone(e.getKey() + " has no good result. Do not copy it.");
                return;
            }
        }
        if (!good.isEmpty()) results.done();
    }

    private static String sci(double v) {
        return String.format(Locale.US, "%.4g", v);
    }

    // The log as a CSV file, saved in the background (the robot loop never waits for the storage).
    private void saveLog(Test test, boolean complete) {
        final StringBuilder csv = new StringBuilder("# " + test.name + (complete ? "" : " (stopped early)") + "\n");
        csv.append("# kind=").append(test.kind.name().toLowerCase(Locale.US)).append(test.mech != null ? " motor=" + test.mech.motor : "").append('\n');
        csv.append("t,volts,power,pos,vel,turnVel,headingDeg\n");
        for (double[] r : log) {
            csv.append(String.format(Locale.US, "%.4f,%.3f,%.4f,%.1f,%.2f,%.2f,%.3f%n", r[0], r[1], r[2], r[3], r[4], r[5], r[6]));
        }
        final String name = test.kind.name().toLowerCase(Locale.US) + (test.mech != null ? "-" + test.mech.motor : "")
                + "-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".csv";
        worker().submit(() -> {
            File folder = new File(FinderResults.folder(), "characterizer");
            try {
                if (!folder.exists() && !folder.mkdirs()) {
                    logNote = "Test readings NOT saved.";
                    return;
                }
                try (Writer out = new FileWriter(new File(folder, name))) {
                    out.write(csv.toString());
                }
                logNote = "Test readings saved.";
            } catch (IOException | SecurityException e) {
                // the results on the screen still have the numbers
                logNote = "Test readings NOT saved.";
            }
        });
    }

    private static synchronized BackgroundWorker worker() {
        if (saver == null) saver = new BackgroundWorker("characterizer-log");
        return saver;
    }

    // ---------------------------------------------------------------- screen and stop

    @Override
    protected void show(RobotTelemetry t) {
        if (running == null) {
            t.warn("{A} starts a test that moves by itself.");
            t.warn("Hands clear. {B}, {GUIDE} or {START} = stop.");
            t.section("Characterizer tests: advanced, can skip");
            textLines(t, status);
            for (int i = 0; i < tests.size(); i++) t.text((i == chosen ? "> " : "  ") + tests.get(i).name);
            if (!logNote.isEmpty()) t.text(logNote);
        } else {
            t.warn("It moves by itself. {B} = stop.");
            t.section("Characterizer: test running");
            textLines(t, status);
            t.line("Time", "%.1f s", now() - testStart);
        }
    }

    @Override
    protected void stopAll() {
        // Every motor gets its stop, even if one of them fails (the first error is reported after).
        RuntimeException first = null;
        List<DcMotorEx> all = new ArrayList<>(left);
        all.addAll(right);
        if (mechMotor != null) all.add(mechMotor);
        for (DcMotorEx m : all) {
            try {
                m.setPower(0);
            } catch (RuntimeException e) {
                if (first == null) first = e;
            }
        }
        if (running != null) status = "Stopped for help or STOP: not measured.";
        running = null;
        if (first != null) throw first;
    }
}
