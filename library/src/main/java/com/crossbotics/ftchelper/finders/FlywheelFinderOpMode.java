package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;
import java.util.List;

/**
 * Finds a flywheel's speed-control numbers. Step 1 measures the top speed at full power and works
 * out starting gains from it. Step 2 holds a speed you choose, so you can fine-tune the main gain
 * (F), feed a piece (with the feeder or a stick, never fingers), and see how fast the wheel gets back to speed after a shot.
 * No code to write: it lists every motor in the robot configuration.
 *
 * <p>For a robot in five files it shows the lines for {@code Tunables.java}, class Launcher: {@code kP},
 * {@code kI}, {@code kD}, {@code kF} and {@code nominalVolts}. Without one, the code for {@code Flywheel}.
 * They are starting numbers (NOT DONE) until step 2 saw the wheel at its speed with them.
 * {@code nominalVolts} is the battery voltage while the top speed was measured: the gains are right
 * for that voltage, and the launcher's battery compensation scales F from it. In step 2 the
 * compensation is on too (from that voltage), so F changes are for the same voltage.
 *
 * <pre>
 * Choose:  D-pad up/down = motor, A = use it (the list starts on Robot.java's flywheel motor, or a name like "flywheel")
 * Step 1:  hold A = full power (at most 5 s). Release when the speed stops rising. Still rising at
 *          5 s (a heavy wheel)? It says so: the top speed may be higher, so F is a little high.
 * Step 2:  B = hold a speed on/off. D-pad up/down = +/- 100 RPM, right/left = +/- 25 RPM.
 *          RB / LB = F gain +2% / -2%. Push ONE piece in with a rod, e.g. a ruler (never fingers; no feeder here): it shows the recovery time.
 *          From Tune and check with kP, kI and kF in Tunables.java: step 2 holds with THOSE gains (and their battery
 *          compensation), so step 1 is not needed and RB / LB fine-tune the kF the robot uses. Y = this finder's own
 *          gains from the top speed (after step 1), and back.
 *          X = the wheel spins the wrong way: reverse it (for both steps; adds .reversed())
 *          (From Tune and check, a motor starts the way Robot.java turns it: reversed() there = reversed here.)
 *          BACK = choose another motor
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * KEEP HANDS AND LOOSE CLOTHING AWAY from the wheel; wear eye protection.
 * In your TeamCode: {@code @TeleOp(name = "Flywheel Finder", group = "Finders")
 * public class FlywheelFinder extends FlywheelFinderOpMode { }}. If the encoder is not on the
 * motor's own shaft at 28 counts per turn, override {@link #countsPerRev()}.
 *
 * Docs: docs/modules/finders.md. Guide: docs/tuning/README.md
 */
public abstract class FlywheelFinderOpMode extends FinderOpMode {

    /** The longest full-power run in step 1, seconds. */
    public static final double MAX_TOP_SPEED_SEC = 5;

    private Chooser chooser;
    private DcMotorEx motor;
    private String motorName;
    private Flywheel flywheel;          // made for step 2
    private boolean holding = false;
    private double topTicks = 0;        // the fastest seen in step 1, counts per second (signed)
    private final double[] recent = new double[8];
    private int recentCount = 0;
    private double fullPowerSince = Double.NaN;
    // Still speeding up when step 1's time ran out (a heavy wheel): the top speed may be higher.
    // The top when it last rose by STILL_RISING_STEP, and when. NaN = not this run.
    private double riseTop = 0, riseAt = Double.NaN;
    private boolean stillRising = false;
    /** In step 1's last second, the speed rose by more than this share: still speeding up. */
    static final double STILL_RISING_STEP = 0.01;
    // False right after choosing with A: A must be let go once before it means "full power".
    private boolean aReleased = false;
    // X: the wheel spins the wrong way, so the motor runs reversed (the code line gets .reversed()).
    private boolean reversed = false;
    private double[] gains = null;      // {P, I, D, F}: the gains in use (step 2 holds with them)
    // The gains Tunables.java has for this motor ({P, I, D, F, nominal volts or NaN}), or null; and true
    // while step 2 uses them (Y switches to this finder's own gains from the top speed, and back).
    private double[] robotGainsNow = null;
    private boolean useRobot = false;
    private boolean fTouched = false;   // RB / LB used since the motor was chosen (Tunables' gains: results only then)
    // Step 2's guard: since when the wheel reads no forward speed while held (seconds; NaN = it does).
    private double noSpeedSince = Double.NaN;
    private String holdStopped = "";
    // Step 2 saw the wheel at speed with the numbers in use, and when they last changed (seconds).
    private boolean checked = false;
    private double changedAt = 0;
    /** The numbers must hold the speed this long after their last change before it says DONE (seconds). */
    static final double CHECK_AFTER_SEC = 1.0;
    /** Step 2 stops when the encoder reads no forward speed this long while the wheel is held (seconds). */
    static final double NO_SPEED_STOP_SEC = 1.0;
    // This finder's own gains, from the top speed (step 1), and the voltage then. Null / NaN = not measured.
    private double[] finderGains = null;
    private double finderVolts = Double.NaN;
    private double targetRpm = 0;
    private int lastShots = 0;
    private double shotAt = Double.NaN;
    private double recoverySec = Double.NaN;
    // Every drop out of "Ready", counted as a shot or not (a drop under the 150 RPM shot drop is not):
    // the deepest point of the drop now, the last drop's and the smallest of all (RPM), and how many.
    // For shotDropRpm. NaN = none. A new target (D-pad) is not a drop.
    private double dropNow = Double.NaN, lastDrop = Double.NaN, smallestDrop = Double.NaN;
    private int drops = 0;
    private boolean wasReady = false;
    private double dropTarget = Double.NaN;
    // The battery: the last reading, and the voltage when the top speed (so the gains) was measured.
    // NaN = none.
    private double volts = Double.NaN;
    // The battery, read on the library's helper thread (never waits in the loop); made at the first step.
    private java.util.function.DoubleSupplier battery = null;
    private double tuneVolts = Double.NaN;

    /**
     * Encoder counts per ONE turn of the flywheel. Default 28: a goBILDA or REV motor's own encoder,
     * with the wheel on the motor shaft (1:1). Geared? Multiply by the gear ratio.
     */
    protected double countsPerRev() {
        return 28;
    }

    /**
     * The direction Robot.java gives each flywheel motor it knows (true = {@code reversed()} on the
     * launcher). The finder starts a motor in that direction, so the wheel spins the way it does in
     * TeleOp, and the numbers fit Robot.java as it is. A motor not in the map starts forward. Default:
     * none (your own finder; set it in a subclass). Tune and check fills it from Robot.java.
     *
     * @return motor name (as in the robot configuration) to reversed; never null
     */
    protected java.util.Map<String, Boolean> motorDirections() {
        return java.util.Collections.emptyMap();
    }

    /**
     * The flywheel motors that Tunables.java runs with {@code speedControl = "BANG_BANG"}. This finder tunes
     * PID only, so it says to use the Flywheel Tuner for them. Default: none (your own finder). Tune and
     * check fills it from Tunables.java.
     *
     * @return motor names (as in the robot configuration); never null
     */
    protected java.util.Set<String> bangBangMotors() {
        return java.util.Collections.emptySet();
    }

    // This tool cannot tune bang-bang: said where the student starts, and while that motor is chosen.
    private static void sayPidOnly(RobotTelemetry t) {
        t.warn("This tool tunes PID only.");
        t.warn("Your robot uses bang-bang: use the Flywheel Tuner.");
    }

    // The direction Robot.java gives the chosen motor: TRUE reversed, FALSE forward, null not known.
    /**
     * The speed-control gains Tunables.java has for each flywheel motor: {kP, kI, kD, kF, nominalVolts}
     * (nominalVolts NaN = no battery compensation). The finder holds the wheel with them in step 2, so a
     * fine-tuned F (RB/LB) fits the kP and kI the robot really uses; {@code Y} switches to the finder's own
     * gains from the top speed. Default: none (your own finder). Tune and check fills it from Tunables.java.
     *
     * @return motor name to its gains; never null
     */
    protected java.util.Map<String, double[]> robotGains() {
        return java.util.Collections.emptyMap();
    }

    /**
     * The motor the list starts on: the first robot flywheel motor in the list ({@code known}), else the
     * first name with "fly", "launch" or "shoot" in it, else null (the list's first). For tests.
     *
     * @param names the motor names in the configuration
     * @param known the robot's flywheel motor names (may be empty)
     */
    static String startMotor(java.util.List<String> names, java.util.Collection<String> known) {
        java.util.List<String> sorted = new ArrayList<>(names);
        java.util.Collections.sort(sorted);
        for (String n : sorted) if (known.contains(n)) return n;
        for (String n : sorted) {
            String low = n.toLowerCase(java.util.Locale.US);
            if (low.contains("fly") || low.contains("launch") || low.contains("shoot")) return n;
        }
        return null;
    }

    private Boolean robotDirection() {
        return motorName == null ? null : motorDirections().get(motorName);
    }

    @Override protected String finderName() { return "Flywheel finder"; }

    @Override
    protected void setup() {
        java.util.List<String> names = new ArrayList<>(hardwareMap.getAllNames(DcMotorEx.class));
        chooser = new Chooser(names);
        // Start on the flywheel (Robot.java's launcher motor, else a name like "flywheel"), not a drive wheel.
        java.util.Set<String> known = new java.util.HashSet<>(motorDirections().keySet());
        known.addAll(robotGains().keySet());
        chooser.select(startMotor(names, known));
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.warn("The flywheel runs at up to FULL power.");
        t.warn("Safety glasses. Hands and loose clothes away.");
        t.warn("Robot on a block. Nobody in front of it.");
        if (!bangBangMotors().isEmpty()) sayPidOnly(t);
        t.text("Flywheel finder: finds the numbers that");
        t.text("hold the flywheel's speed.");
        t.text("It starts with {A} (hold) or {B} (on/off).");
    }

    @Override
    protected void bind(ControllerBindings c) {
        c.bindPress(Pad.G1, Btn.DPAD_UP, () -> { if (motor == null) chooser.previous(); else changeTarget(100); })
         .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> { if (motor == null) chooser.next(); else changeTarget(-100); })
         .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> changeTarget(25))
         .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> changeTarget(-25))
         .bindPress(Pad.G1, Btn.A, () -> { if (motor == null) choose(); })
         .bindPress(Pad.G1, Btn.B, this::toggleHold)
         .bindPress(Pad.G1, Btn.RB, () -> changeF(1.02))
         .bindPress(Pad.G1, Btn.LB, () -> changeF(1 / 1.02))
         .bindPress(Pad.G1, Btn.X, this::toggleReverse)
         .bindPress(Pad.G1, Btn.Y, this::switchGains)
         .bindPress(Pad.G1, Btn.BACK, this::release);
    }

    private void choose() {
        if (chooser.isEmpty()) return;
        motorName = chooser.current();
        motor = hardwareMap.get(DcMotorEx.class, motorName);
        // A flywheel coasts: braking a fast wheel is hard on the gears.
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        // The way Robot.java turns it (a wheel spinning backward throws the piece back or jams on it).
        // Before plainPower(): it sets the motor's direction from this.
        reversed = Boolean.TRUE.equals(robotDirection());
        plainPower();
        flywheel = null;
        holding = false;
        aReleased = false;
        topTicks = 0;
        stillRising = false;
        gains = null;
        finderGains = null;
        finderVolts = Double.NaN;
        recoverySec = Double.NaN;
        tuneVolts = Double.NaN;
        // Tunables.java has gains for this motor: step 2 holds with them (no step 1 needed first).
        double[] fromRobot = robotGains().get(motorName);
        robotGainsNow = fromRobot == null ? null : fromRobot.clone();   // RB / LB change this copy, never Tune and check's
        fTouched = false;
        checked = false;
        holdStopped = "";
        useRobot = robotGainsNow != null;
        if (useRobot) useGains();
    }

    // Y: switch step 2 between the gains in Tunables.java and this finder's own (after step 1). Not while holding.
    private void switchGains() {
        if (motor == null || holding || robotGainsNow == null) return;
        if (useRobot && finderGains == null) return;   // no own gains yet: measure the top speed first (step 1)
        useRobot = !useRobot;
        useGains();
    }

    // Puts the chosen gains in use: Tunables' (F fine-tuned from them) or this finder's own.
    private void useGains() {
        notChecked();
        if (useRobot) {
            gains = new double[] {robotGainsNow[0], robotGainsNow[1], robotGainsNow[2], robotGainsNow[3]};
            tuneVolts = robotGainsNow[4];   // NaN: Tunables has no battery compensation, so none here either
        } else {
            gains = finderGains == null ? null : finderGains.clone();
            tuneVolts = finderVolts;
        }
        writeResults();
    }

    // The numbers changed: step 2 must see them hold the speed again.
    private void notChecked() {
        checked = false;
        changedAt = now();
    }

    private void release() {
        stopAll();
        motor = null;
    }

    private double now() {
        return System.nanoTime() / 1e9;
    }

    private void changeTarget(double rpm) {
        if (motor == null || gains == null) return;
        targetRpm = Math.max(0, targetRpm + rpm);
    }

    private void changeF(double factor) {
        if (gains == null) return;
        gains[3] *= factor;
        fTouched = true;
        notChecked();
        // Kept with its source, so Y and back keeps the fine-tuned F.
        if (useRobot) robotGainsNow[3] = gains[3];
        else if (finderGains != null) finderGains[3] = gains[3];
        if (flywheel != null) flywheel.pidf(gains[0], gains[1], gains[2], gains[3]);
        writeResults();
    }

    // B: hold the chosen speed with the hub's speed control (step 2), or stop.
    private void toggleHold() {
        if (motor == null || gains == null) return;
        if (topTicks < 0) return;   // the encoder counts backward: speed control would run away
        holding = !holding;
        noSpeedSince = Double.NaN;
        holdStopped = "";
        changedAt = now();
        if (holding) {
            motor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            flywheel = new Flywheel(motorName, motor).countsPerRev(countsPerRev())
                    .pidf(gains[0], gains[1], gains[2], gains[3]);
            if (topRpm() > 0) flywheel.maxRpm(topRpm());   // no top speed yet (Tunables' gains): the default
            // From the voltage the gains were found at: an F you fine-tune now is for that voltage too.
            if (tuneVolts >= 9 && tuneVolts <= 14) flywheel.batteryCompensation(batteryReader(), tuneVolts);
            if (targetRpm == 0) targetRpm = topRpm() > 0 ? Math.round(topRpm() * 0.6 / 25) * 25.0 : 3000;
            lastShots = 0;
            dropNow = lastDrop = smallestDrop = Double.NaN;
            drops = 0;
            wasReady = false;
        } else {
            flywheel.stop();
            plainPower();
        }
    }

    // A drop: the wheel was "Ready", fell out of the ready band, and came back. Its depth is how far it
    // fell under the target. A target change starts over (the wheel is not "Ready" for the new speed).
    private void watchDrops() {
        boolean ready = flywheel.isReady();
        if (dropTarget != targetRpm) {
            dropTarget = targetRpm;
            dropNow = Double.NaN;
            wasReady = ready;
            return;
        }
        if (wasReady && !ready) dropNow = 0;
        if (!Double.isNaN(dropNow)) {
            dropNow = Math.max(dropNow, Math.abs(targetRpm) - Math.abs(flywheel.getRpm()));
            if (ready) {
                drops++;
                lastDrop = dropNow;
                smallestDrop = Double.isNaN(smallestDrop) ? dropNow : Math.min(smallestDrop, dropNow);
                dropNow = Double.NaN;
            }
        }
        wasReady = ready;
    }

    // X: reverse the wheel. Only while it is not spinning on purpose (step 1 or 2 running).
    private void toggleReverse() {
        if (motor == null || holding || !Double.isNaN(fullPowerSince)) return;
        reversed = !reversed;
        notChecked();
        plainPower();
        writeResults();
    }

    // Step 1's set-up: plain power, in the chosen direction.
    private void plainPower() {
        motor.setDirection(reversed ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD);
        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        motor.setPower(0);
    }

    private double topRpm() {
        return Math.abs(FinderMath.ticksPerSecToRpm(topTicks, countsPerRev()));
    }

    // One battery reader for the whole run (Hw.batteryReader: the hubs are asked off the loop).
    private java.util.function.DoubleSupplier batteryReader() {
        if (battery == null) battery = Hw.batteryReader(hardwareMap);
        return battery;
    }

    // The reader asks the hubs on a helper thread at most every 0.25 s: this never waits.
    private void readVolts() {
        double v = batteryReader().getAsDouble();
        if (v >= 7 && v <= 16) volts = v;
    }

    @Override
    protected void step() {
        if (motor == null) return;
        readVolts();
        if (holding) {
            flywheel.setRpm(targetRpm);
            flywheel.update();
            // The encoder must read the wheel going forward. Backward (a swapped cable) or nothing (unplugged):
            // speed control would push full power forever, so stop (step 1's backward check is skipped with
            // Tunables' gains, so it is checked here).
            double ticks = motor.getVelocity();
            boolean forward = targetRpm <= 0 || FinderMath.ticksPerSecToRpm(ticks, countsPerRev()) > Math.min(100, targetRpm / 2);
            if (forward) noSpeedSince = Double.NaN;
            else if (Double.isNaN(noSpeedSince)) noSpeedSince = now();
            else if (now() - noSpeedSince > NO_SPEED_STOP_SEC) {
                stopAll();
                plainPower();
                holdStopped = "Stopped: the speed reads " + (ticks < 0 ? "BACKWARD." : "0 or too low.")
                        + "\nNext: STOP, then run the Encoder Check.";
                return;
            }
            // A shot: the wheel's shot counter went up. Recovery = the time until it is ready again.
            if (flywheel.getShotCount() > lastShots) {
                lastShots = flywheel.getShotCount();
                shotAt = now();
            }
            if (!Double.isNaN(shotAt) && flywheel.isReady()) {
                recoverySec = now() - shotAt;
                shotAt = Double.NaN;
                writeResults();
            }
            watchDrops();
            if (!checked && targetRpm > 0 && flywheel.isReady() && now() - changedAt > CHECK_AFTER_SEC) {
                checked = true;
                writeResults();
            }
            return;
        }
        // Step 1: full power while A is held, at most MAX_TOP_SPEED_SEC. The A press that chose the
        // motor does not count: A must be let go and pressed again.
        if (!gamepad1.a) aReleased = true;
        if (gamepad1.a && aReleased && inputsReady()) {
            if (Double.isNaN(fullPowerSince)) {
                fullPowerSince = now();
                recentCount = 0;
                riseTop = 0;
                riseAt = Double.NaN;
                stillRising = false;
            }
            boolean tooLong = now() - fullPowerSince > MAX_TOP_SPEED_SEC;
            // The time ran out while the speed still rose (in the last second): the top may be higher.
            if (tooLong && !stillRising && !Double.isNaN(riseAt) && now() - riseAt < 1.0) {
                stillRising = true;
                writeResults();
            }
            motor.setPower(tooLong ? 0 : 1);
            if (!tooLong) measureTop();
        } else {
            fullPowerSince = Double.NaN;
            motor.setPower(0);
        }
    }

    // Keeps the fastest AVERAGE of the last few readings (one reading alone can jump).
    private void measureTop() {
        recent[recentCount % recent.length] = motor.getVelocity();
        recentCount++;
        if (recentCount < recent.length) return;
        double sum = 0;
        for (double v : recent) sum += v;
        double avg = sum / recent.length;
        // A rise of more than STILL_RISING_STEP since the last one: note when (for "still speeding up").
        if (Math.abs(avg) > Math.abs(riseTop) * (1 + STILL_RISING_STEP)) {
            riseTop = avg;
            riseAt = now();
        }
        if (Math.abs(avg) > Math.abs(topTicks)) {
            topTicks = avg;
            // This finder's own gains, right for the battery as it is now. With Tunables' gains in use,
            // they stay in use (Y switches); only the top speed is new.
            finderVolts = volts;
            finderGains = FinderMath.flywheelPidf(topTicks);
            if (!useRobot) {
                notChecked();
                tuneVolts = finderVolts;
                gains = finderGains.clone();
            }
            writeResults();
        }
    }

    private void writeResults() {
        if (gains == null) return;
        String key = "flywheel:" + motorName;
        results.removeStartingWith(key + ":");
        if (topTicks < 0) results.notDone("the speed counts backward.");
        else if (!checked) results.notDone("check them in step 2 ({B}).");
        else results.done();
        if (useRobot) {
            writeRobotResults(key);
            return;
        }
        if (hasRobotFiles() || robotGainsNow != null) {
            writeFinderTunables(key);
            return;
        }
        String line = "Flywheel.create(hardwareMap, \"" + motorName + "\")"
                + ".countsPerRev(" + FinderResults.num(countsPerRev(), 1) + ")"
                + (reversed ? ".reversed()" : "")
                + ".pidf(" + FinderResults.num(gains[0], 2) + ", " + FinderResults.num(gains[1], 3) + ", 0, "
                + FinderResults.num(gains[3], 2) + ")"
                + ".maxRpm(" + FinderResults.num(Math.floor(topRpm() * 0.9 / 100) * 100, 0) + ")";
        List<String> code = new ArrayList<>();
        code.add(line);
        code.add("    // top speed " + FinderResults.num(topRpm(), 0) + " RPM; maxRpm = 90% of it"
                + (stillRising ? " (still speeding up at " + FinderResults.num(MAX_TOP_SPEED_SEC, 0) + " s: may be higher)" : ""));
        if (!Double.isNaN(recoverySec)) {
            code.add("    // back at speed after a piece at " + FinderResults.num(targetRpm, 0) + " RPM: "
                    + FinderResults.num(recoverySec, 2) + " s");
        }
        if (knowTuneVolts()) {
            code.add("    // tuned at " + FinderResults.num(tuneVolts, 1) + " V: add .batteryCompensation(Hw.batteryReader(hardwareMap), "
                    + FinderResults.num(tuneVolts, 1) + ")");
        }
        results.setIn(key + ":0", FinderResults.HELPER_CODE, code.toArray(new String[0]));
        // Robot.java's direction is known (motorDirections): it must turn it the way it turned here.
        if (robotDirection() != null) writeDirection(key);
    }

    private boolean knowTuneVolts() {
        return tuneVolts >= 9 && tuneVolts <= 14;
    }

    // This finder's own numbers for Tunables.java. A slow recovery also needs a longer time between two
    // shots (shotIntervalSec, default 0.25 s): a little above the recovery.
    private void writeFinderTunables(String key) {
        String section = sectionOf(motorName);
        results.setTunable(key + ":kP", section, "kP", FinderResults.num(gains[0], 2));
        results.setTunable(key + ":kI", section, "kI", FinderResults.num(gains[1], 3));
        results.setTunable(key + ":kD", section, "kD", "0");
        results.setTunable(key + ":kF", section, "kF", FinderResults.num(gains[3], 2));
        // The voltage the gains are for: the launcher's battery compensation scales F from it.
        if (knowTuneVolts()) results.setTunable(key + ":volts", section, "nominalVolts", FinderResults.num(tuneVolts, 1));
        if (!Double.isNaN(recoverySec) && recoverySec > 0.25) {
            results.setTunable(key + ":gap", section, "shotIntervalSec",
                    FinderResults.num(Math.ceil(recoverySec * 20) / 20 + 0.05, 2));
        }
        writeDirection(key);
    }

    // Tunables' gains in use: their kP, kI, kD (and nominalVolts) as they are, with the fine-tuned kF.
    private void writeRobotResults(String key) {
        Boolean inRobot = robotDirection();
        boolean turned = inRobot == null ? reversed : inRobot != reversed;
        if (!fTouched && Double.isNaN(recoverySec) && !turned) return;   // nothing found yet: Tunables.java already has these
        String section = sectionOf(motorName);
        // exact(): these are copied back as they were, so 0.005 must not turn into 0.01.
        results.setTunable(key + ":kP", section, "kP", FinderResults.exact(gains[0], 2));
        results.setTunable(key + ":kI", section, "kI", FinderResults.exact(gains[1], 3));
        results.setTunable(key + ":kD", section, "kD", FinderResults.exact(gains[2], 3));
        results.setTunable(key + ":kF", section, "kF", fTouched ? FinderResults.num(gains[3], 2) : FinderResults.exact(gains[3], 2));
        if (!Double.isNaN(robotGainsNow[4])) {
            results.setTunable(key + ":volts", section, "nominalVolts", FinderResults.exact(robotGainsNow[4], 1));
        }
        writeDirection(key);
    }

    // Robot.java must turn it the way it turned here. Not known (your own finder): only a reversed one needs a line.
    private void writeDirection(String key) {
        Boolean inRobot = robotDirection();
        if (inRobot == null ? reversed : inRobot != reversed) {
            results.setIn(key + ":turn", FinderResults.ROBOT_FILE, (reversed ? "add .reversed() to" : "take .reversed() off")
                    + " the \"" + motorName + "\" launcher");
        }
    }

    // The gains line: whose gains step 2 holds with, and what Y does.
    private String gainsWords() {
        return useRobot ? "Tunables.java" : "this finder's";
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (motor == null) {
            t.warn("Safety glasses. Hands away from the flywheel.");
            if (!bangBangMotors().isEmpty()) sayPidOnly(t);
            t.section("Flywheel finder: choose the motor");
            t.text("Next: choose the flywheel's motor.");
            t.text("D-pad up/down = choose. {A} = use it.");
            chooser.addTelemetry(t, "No motors in the robot configuration.");
            return;
        }
        double rpm = FinderMath.ticksPerSecToRpm(motor.getVelocity(), countsPerRev());
        boolean measuring = !Double.isNaN(fullPowerSince);   // A is held
        boolean fullPower = measuring && now() - fullPowerSince <= MAX_TOP_SPEED_SEC;
        if (holding) t.warn("The flywheel spins. {B} = stop. Hands clear.");
        else if (fullPower) t.warn("FULL power. Let go of {A} = stop.");
        else t.warn("{A} or {B} spins the flywheel. Hands clear.");
        if (bangBangMotors().contains(motorName)) sayPidOnly(t);
        warnLines(t, holdStopped);
        if (stillRising) {
            t.warn("Still speeding up at " + FinderResults.num(MAX_TOP_SPEED_SEC, 0) + " s (a heavy wheel).");
            t.warn("So kF may be a little high: check step 2.");
        }
        if (topTicks < 0) {
            t.warn("The speed counts BACKWARD at full power.");
            t.warn("Next: STOP, then run the Encoder Check.");
        }
        if (!Double.isNaN(volts) && volts < com.crossbotics.ftchelper.tuning.TuningMenu.LOW_BATTERY_VOLTS) {
            t.warn("Battery low: change it before you measure.");
        }
        boolean step1 = !holding && (measuring || topTicks == 0);
        t.section("Flywheel \"" + motorName + "\": step " + (step1 ? 1 : 2) + " of 2");
        if (step1) {
            if (fullPower) t.text("Next: let go when Speed stops going up.");
            else if (measuring) t.text("Next: let go of {A}. The time is up.");
            else t.text("Next: hold {A} = full power, 5 s at most.");
            t.line("Speed", FinderResults.num(rpm, 0) + " RPM");
            Boolean inRobot = robotDirection();
            String asRobot = inRobot == null ? "" : inRobot == reversed ? ", as in Robot.java" : ", NOT as in Robot.java";
            t.line("Direction", (reversed ? "reversed" : "normal") + asRobot);
            t.text("It must throw pieces OUT. Wrong way? {X}.");
            if (gains != null && !measuring) {
                t.text("Or step 2 now: {B}. It uses the numbers in use:");
                showNumbers(t);
            }
        } else if (!holding) {
            if (topTicks < 0) {
                t.text("Step 2 cannot run until the count is fixed.");
            } else {
                t.text("Next: press {B}. The wheel spins at the target.");
                t.text("{B} again = stop.");
            }
            t.line("Top speed", FinderResults.num(topRpm(), 0) + " RPM");
            if (results.isEmpty()) t.text("Hold {A} = measure the top speed again.");
            showNumbers(t);
        } else {
            double error = targetRpm - Math.abs(flywheel.getRpm());
            boolean ready = flywheel.isReady();
            if (!ready) t.text("Next: wait. Still off? Too SLOW: {RB}.");
            else t.text("Next: push ONE piece in with a rod.");
            t.text(ready ? "NEVER fingers." : "Too FAST: {LB}.");
            t.line("Target", FinderResults.num(targetRpm, 0) + " RPM");
            t.line("Speed", FinderResults.num(rpm, 0) + " RPM");
            if (Math.abs(error) < 1) t.line("Speed check", "on the target");
            else t.line("Speed check", FinderResults.num(Math.abs(error), 0) + " RPM too " + (error > 0 ? "SLOW" : "FAST")
                    + (Math.abs(error) <= flywheel.getTolerance() ? ": OK" : ""));
            t.line("At speed", ready ? "YES" : "no");
            // Once the lines to copy show, they have the numbers: no need to show them two times.
            if (results.isEmpty()) showNumbersInUse(t);
            t.line("Speed drops counted", flywheel.getShotCount());
            if (!Double.isNaN(lastDrop)) {
                t.line("Below target at a drop", FinderResults.num(lastDrop, 0) + " RPM (least "
                        + FinderResults.num(smallestDrop, 0) + ")");
            }
            if (results.isEmpty()) t.text("D-pad = change the target.");
        }
        if (!Double.isNaN(recoverySec)) t.line("Back at speed after a piece", FinderResults.num(recoverySec, 2) + " s");
        if (checked) t.text("Next tool: the Flywheel Tuner (PID or bang-bang).");
        if (!holding) t.text("{BACK} = another motor.");
    }

    // Whose speed-control numbers step 2 uses, and what Y does.
    private void showNumbers(RobotTelemetry t) {
        if (gains == null) return;
        showNumbersInUse(t);
        if (useRobot && finderGains != null) t.text("{Y} = use this finder's numbers.");
        if (!useRobot && robotGainsNow != null) t.text("{Y} = use Tunables.java's numbers.");
    }

    private void showNumbersInUse(RobotTelemetry t) {
        t.line("Numbers in use", gainsWords());
        t.text("  kP " + FinderResults.num(gains[0], 2) + ", kI " + FinderResults.num(gains[1], 3)
                + ", kF " + FinderResults.num(gains[3], 2));
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds the numbers that hold the flywheel's speed.",
            "Safety glasses. Hands and loose clothes away.",
            "First choose its motor: D-pad, then {A}.",
            "1. Hold {A} = full power (5 s at most).",
            "   Wrong way? Let go, {X}, hold {A} again.",
            "2. {B} = hold the target speed, on or off.",
            "   D-pad up/down 100 RPM, right/left 25 RPM.",
            "3. Always too SLOW: {RB} (kF up 2%).",
            "   Always too FAST: {LB} (kF down 2%).",
            "4. Push ONE piece in with a rod. NEVER fingers.",
            "   A speed drop can happen with no piece, too.",
            "{Y} = Tunables' numbers or this finder's.",
            "{BACK} = another motor.",
        };
    }

    // Help open: stop, and go back to step 1's set-up (plain power, chosen direction), so a new
    // top-speed test after help is measured correctly.
    @Override
    protected void pause() {
        stopAll();
        if (motor != null) plainPower();
        flywheel = null;
        fullPowerSince = Double.NaN;
        aReleased = false;
    }

    @Override
    protected void stopAll() {
        holding = false;
        if (flywheel != null) flywheel.stop();
        if (motor != null) motor.setPower(0);
    }
}
