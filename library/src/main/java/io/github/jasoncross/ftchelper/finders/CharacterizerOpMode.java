package io.github.jasoncross.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.util.BackgroundWorker;
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
 * The results are lines for the simulator's robot file and Feedforward lines for your code; they are
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
 * Docs: docs/modules/finders.md ("Characterizer"). Guide: docs/guides/simulator.md ("Real numbers")
 */
public abstract class CharacterizerOpMode extends FinderOpMode {

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
    private String status = "Choose a test (D-pad), then A.";
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
            tests.add(new Test((m.flywheel ? "Flywheel " : "Lift/arm ") + m.motor, m.flywheel ? Kind.FLYWHEEL : Kind.LIFT, m));
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
        t.text("Characterizer: measures the drive, lifts and flywheels for the simulator and feedforward.");
        t.warn("Drive test: about 5 ft clear in front and behind. Lifts start at the bottom.");
        t.text("Press START.");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "D-pad up/down: choose a test. A: run it.",
            "B: stop the test now (everything stops).",
            "Drive: ramps forward, back, then quick steps.",
            "  Needs ~5 ft clear in front and behind.",
            "Turn: turns in place both ways (IMU).",
            "Lift/arm: start at the BOTTOM. It moves only",
            "  between the soft limits in mechanisms().",
            "Flywheel: spins up, coasts down, twice.",
            "Run each test 2-3 times on a charged battery.",
            "Results: lines for the robot file + code.",
            "Logs: characterizer/*.csv for the fit tool.",
        };
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> { if (running == null) chosen = (chosen + tests.size() - 1) % tests.size(); })
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> { if (running == null) chosen = (chosen + 1) % tests.size(); })
         .bindPress(Pad.G1, Btn.A, () -> { if (running == null && inputsReady()) begin(tests.get(chosen)); })
         .bindPress(Pad.G1, Btn.B, () -> { if (running != null) finish("Stopped with B: nothing was fitted."); });
    }

    private double now() {
        return System.nanoTime() / 1e9;
    }

    // ---------------------------------------------------------------- starting a test

    private void begin(Test test) {
        running = test;
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
                phases.add(new Phase("ramp +", ramp / rate, s -> Math.min(ramp, rate * s), tooFar));
                phases.add(new Phase("stop", 0.8, s -> 0, null));
                phases.add(new Phase("ramp -", ramp / rate, s -> -Math.min(ramp, rate * s), tooFar));
                phases.add(new Phase("stop", 0.8, s -> 0, null));
                // not too hard: spinning wheels would fake a quick speed-up
                double step = Math.min(0.5, ramp * 1.25);
                phases.add(new Phase("step +", 0.6, s -> step, tooFar));
                phases.add(new Phase("stop", 0.8, s -> 0, null));
                phases.add(new Phase("step -", 0.6, s -> -step, tooFar));
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
                phases.add(new Phase("ramp up", 5, s -> Math.min(1, 0.2 * s), null));
                phases.add(new Phase("coast", 4, s -> 0, null));
                phases.add(new Phase("full power", 3, s -> 1, null));
                phases.add(new Phase("coast", 3, s -> 0, null));
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
            finish("Stopped: GUIDE/START pressed. Nothing was fitted.");
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
                    finish("Stopped: wheel \"" + wheelNames.get(i) + "\" is pushed but not turning. Jammed, or its encoder "
                            + "cable in another port?");
                    return;
                }
                if (Math.abs(wheelPower) > 0.3 && Math.signum(wv) == -Math.signum(wheelPower) && Math.abs(wv) > 50) {
                    finish("Stopped: wheel \"" + wheelNames.get(i) + "\" turned the WRONG way. Check its direction in RobotSettings "
                            + "(run the Motor Direction Check).");
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
            finish("Stopped: it is pushing but not moving. Jammed, against a hard stop (wrong direction?), "
                    + "or the encoder cable in another port?");
            return;
        }
        // Safety: a lift never goes past its soft limits, whatever the power (an encoder that counts the
        // wrong way would otherwise let it run on), and it must move the way the power pushes it.
        if (running.kind == Kind.LIFT && (pos > running.mech.high || pos < running.mech.low - 100)) {
            finish("Stopped: the lift went past a soft limit (" + Math.round(pos) + " counts). Check its limits, "
                    + "its direction (.reversed()) and that its encoder counts UP when it goes up.");
            return;
        }
        if (running.kind == Kind.LIFT && phase == 0 && power > 0.3 && pos < running.mech.low - 60) {
            finish("Stopped: going up, the count went DOWN. Check the motor direction (.reversed()) and the encoder.");
            return;
        }
        // Safety: pushing hard but moving the wrong way = the motor direction is wrong.
        if (Math.abs(power) > 0.3 && Math.signum(vel) == -Math.signum(power) && Math.abs(vel) > 50 && running.kind != Kind.FLYWHEEL) {
            finish("Stopped: it moved the WRONG way for this power. Check the motor directions (reversed in RobotSettings, or .reversed()).");
            return;
        }
        apply(power);
        // At most 200 moments a second (a robot loop is slower; the simulator's can be much faster).
        if (log.isEmpty() || t - testStart - log.get(log.size() - 1)[0] >= 0.005) {
            log.add(new double[] {t - testStart, volts, power, pos, vel, turnVel, heading});
        }
        boolean done = s >= ph.maxSec || (ph.done != null && ph.done.done(s, pos, pos - phaseStartPos, vel));
        if (running.kind == Kind.LIFT && phase == 0 && s >= ph.maxSec) {
            finish("Stopped: the lift did not reach 60% of its range in 25 s. Is it free to move, and the limits right?");
            return;
        }
        // A lift move that runs out of time without reaching its place: something holds it. Stop.
        if (running.kind == Kind.LIFT && ph.done != null && s >= ph.maxSec) {
            finish("Stopped: the lift did not get where \"" + ph.name + "\" goes in " + Math.round(ph.maxSec)
                    + " s. Is it caught on something? Nothing was fitted.");
            return;
        }
        if (done) {
            phase++;
            phaseStart = t;
            phaseStartPos = pos;
            stalledSec = 0;
            if (phase >= phases.size()) finish(null);
        }
        if (running != null) status = running.name + ": " + phases.get(phase).name + " (B = stop)";
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
        status = test.name + ": done. " + fitAndShow(test) + (test.kind == Kind.LIFT ? " It is back near the bottom." : "");
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
        if (!f.ok()) return "Could not fit (too few moving moments, or a poor match: r2 " + FinderResults.num(f.r2, 2) + "). Run it again.";
        String key = test.kind.name().toLowerCase(Locale.US) + (test.mech != null ? ":" + test.mech.motor : "");
        String ff = "new Feedforward(" + sci(f.kS) + ", " + sci(f.kV) + ", " + sci(f.kA) + (test.kind == Kind.LIFT ? ", " + sci(f.kG) : "") + ")";
        switch (test.kind) {
            case DRIVE:
                results.set(key + ":sim0", "# simulator robot file:");
                results.set(key + ":sim1", "drive.kS = " + FinderResults.num(f.kS, 3));
                results.set(key + ":sim2", "drive.maxTicksPerSec = " + FinderResults.num(f.ticksPerSecPerPower(), 0));
                results.set(key + ":sim3", "drive.accelSec = " + FinderResults.num(f.accelSec(), 3));
                results.set(key + ":ff", "Feedforward driveFf = " + ff + ";   // wheels, counts per second");
                break;
            case TURN: {
                double degPerCount = CharacterizeMath.turnDegPerCount(turnVel, CharacterizeMath.yawRates(t, heading));
                results.set(key + ":sim0", "# simulator robot file:");
                results.set(key + ":sim1", "drive.turnDegPerCount = " + FinderResults.num(degPerCount, 5));
                results.set(key + ":ff", "Feedforward turnFf = " + ff + ";   // turning, wheel counts per second");
                break;
            }
            case LIFT:
                results.set(key + ":sim0", "# add to motor." + test.mech.motor + " in the robot file:");
                results.set(key + ":sim1", "kS=" + FinderResults.num(f.kS, 3) + ", maxTicksPerSec=" + FinderResults.num(f.ticksPerSecPerPower(), 0)
                        + ", accelSec=" + FinderResults.num(f.accelSec(), 3) + ", gravity=" + FinderResults.num(f.kG, 3));
                results.set(key + ":ff", "Feedforward " + test.mech.motor + "Ff = " + ff + ";");
                break;
            default: {
                double coast = CharacterizeMath.coastSec(t, power, vel);
                results.set(key + ":sim0", "# add to motor." + test.mech.motor + " in the robot file:");
                results.set(key + ":sim1", "kS=" + FinderResults.num(f.kS, 3) + ", maxTicksPerSec=" + FinderResults.num(f.ticksPerSecPerPower(), 0)
                        + ", accelSec=" + FinderResults.num(f.accelSec(), 3) + (Double.isNaN(coast) ? "" : ", coastSec=" + FinderResults.num(coast, 2)));
                results.set(key + ":ff", "Feedforward " + test.mech.motor + "Ff = " + ff + ";");
            }
        }
        return "Fit r2 " + FinderResults.num(f.r2, 3) + (f.r2 < 0.9 ? " (poor: run it again on a charged battery)" : " (good)");
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
                if (!folder.exists() && !folder.mkdirs()) return;
                try (Writer out = new FileWriter(new File(folder, name))) {
                    out.write(csv.toString());
                }
            } catch (IOException | SecurityException e) {
                // the results on the screen still have the numbers
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
            t.section("Characterizer tests");
            for (int i = 0; i < tests.size(); i++) t.text((i == chosen ? "> " : "  ") + tests.get(i).name);
        } else {
            t.line("Time", "%.1f s", now() - testStart);
        }
        t.text(status);
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
        if (running != null) status = "Stopped (STOP, help or an error). Nothing was fitted.";
        running = null;
        if (first != null) throw first;
    }
}
