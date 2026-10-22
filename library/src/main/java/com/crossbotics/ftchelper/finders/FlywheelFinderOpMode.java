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

/**
 * Finds a flywheel's speed-control numbers. Step 1 measures the top speed at full power and works
 * out starting gains from it. Step 2 holds a speed you choose, so you can fine-tune the main gain
 * (F), feed a piece by hand, and see how fast the wheel gets back to speed after a shot.
 * No code to write: it lists every motor in the robot configuration.
 *
 * <p>It shows the code for {@code Flywheel} and, for the robot in five files, the line for
 * {@code Tunables.java}: {@code Tunables.Launcher: kP = 1.17, kI = 0.117, kD = 0, kF = 11.70, nominalVolts = 12.6}.
 * {@code nominalVolts} is the battery voltage while the top speed was measured: the gains are right
 * for that voltage, and the launcher's battery compensation scales F from it. In step 2 the
 * compensation is on too (from that voltage), so F changes are for the same voltage.
 *
 * <pre>
 * Choose:  D-pad up/down = motor, A = use it
 * Step 1:  hold A = full power (at most 5 s). Release when the speed stops rising.
 * Step 2:  B = hold a speed on/off. D-pad up/down = +/- 100 RPM, right/left = +/- 25 RPM.
 *          RB / LB = F gain +2% / -2%. Feed a piece by hand: it shows the recovery time.
 *          X = the wheel spins the wrong way: reverse it (for both steps; adds .reversed())
 *          BACK = choose another motor
 * Help: GUIDE (or hold START 1 s) = a help page with every button; everything pauses.
 * </pre>
 *
 * KEEP HANDS AND LOOSE CLOTHING AWAY from the wheel; wear eye protection.
 * In your TeamCode: {@code @TeleOp(name = "Flywheel Finder", group = "Finders")
 * public class FlywheelFinder extends FlywheelFinderOpMode { }}. If the encoder is not on the
 * motor's own shaft at 28 counts per turn, override {@link #countsPerRev()}.
 *
 * Docs: docs/modules/finders.md. Guide: docs/guides/finders.md
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
    // False right after choosing with A: A must be let go once before it means "full power".
    private boolean aReleased = false;
    // X: the wheel spins the wrong way, so the motor runs reversed (the code line gets .reversed()).
    private boolean reversed = false;
    private double[] gains = null;      // {P, I, D, F}
    private double targetRpm = 0;
    private int lastShots = 0;
    private double shotAt = Double.NaN;
    private double recoverySec = Double.NaN;
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

    @Override protected String finderName() { return "Flywheel finder"; }

    @Override
    protected void setup() {
        chooser = new Chooser(new ArrayList<>(hardwareMap.getAllNames(DcMotorEx.class)));
    }

    @Override
    protected void showInit(RobotTelemetry t) {
        t.text("Flywheel finder: top speed, speed-control gains, recovery after a shot.");
        t.warn("Hands away from the wheel. Eye protection on.");
        t.text("Press START.");
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
         .bindPress(Pad.G1, Btn.BACK, this::release);
    }

    private void choose() {
        if (chooser.isEmpty()) return;
        motorName = chooser.current();
        motor = hardwareMap.get(DcMotorEx.class, motorName);
        // A flywheel coasts: braking a fast wheel is hard on the gears.
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        plainPower();
        flywheel = null;
        holding = false;
        aReleased = false;
        reversed = false;
        topTicks = 0;
        gains = null;
        recoverySec = Double.NaN;
        tuneVolts = Double.NaN;
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
        if (flywheel != null) flywheel.pidf(gains[0], gains[1], gains[2], gains[3]);
        writeResults();
    }

    // B: hold the chosen speed with the hub's speed control (step 2), or stop.
    private void toggleHold() {
        if (motor == null || gains == null) return;
        if (topTicks < 0) return;   // the encoder counts backward: speed control would run away
        holding = !holding;
        if (holding) {
            motor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            flywheel = new Flywheel(motorName, motor).countsPerRev(countsPerRev())
                    .maxRpm(topRpm()).pidf(gains[0], gains[1], gains[2], gains[3]);
            // From the voltage the gains were found at: an F you fine-tune now is for that voltage too.
            if (tuneVolts >= 9 && tuneVolts <= 14) flywheel.batteryCompensation(batteryReader(), tuneVolts);
            if (targetRpm == 0) targetRpm = Math.round(topRpm() * 0.6 / 25) * 25.0;
            lastShots = 0;
        } else {
            flywheel.stop();
            plainPower();
        }
    }

    // X: reverse the wheel. Only while it is not spinning on purpose (step 1 or 2 running).
    private void toggleReverse() {
        if (motor == null || holding || !Double.isNaN(fullPowerSince)) return;
        reversed = !reversed;
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
            return;
        }
        // Step 1: full power while A is held, at most MAX_TOP_SPEED_SEC. The A press that chose the
        // motor does not count: A must be let go and pressed again.
        if (!gamepad1.a) aReleased = true;
        if (gamepad1.a && aReleased && inputsReady()) {
            if (Double.isNaN(fullPowerSince)) {
                fullPowerSince = now();
                recentCount = 0;
            }
            boolean tooLong = now() - fullPowerSince > MAX_TOP_SPEED_SEC;
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
        if (Math.abs(avg) > Math.abs(topTicks)) {
            topTicks = avg;
            tuneVolts = volts;   // the gains below are right for the battery as it is now
            gains = FinderMath.flywheelPidf(topTicks);
            writeResults();
        }
    }

    private void writeResults() {
        if (gains == null) return;
        String key = "flywheel:" + motorName;
        results.removeStartingWith(key + ":");
        String line = "Flywheel.create(hardwareMap, \"" + motorName + "\")"
                + ".countsPerRev(" + FinderResults.num(countsPerRev(), 1) + ")"
                + (reversed ? ".reversed()" : "")
                + ".pidf(" + FinderResults.num(gains[0], 2) + ", " + FinderResults.num(gains[1], 3) + ", 0, "
                + FinderResults.num(gains[3], 2) + ")"
                + ".maxRpm(" + FinderResults.num(Math.floor(topRpm() * 0.9 / 100) * 100, 0) + ")";
        results.set(key + ":0", line);
        results.set(key + ":1", "    // top speed " + FinderResults.num(topRpm(), 0) + " RPM; maxRpm = 90% of it");
        if (!Double.isNaN(recoverySec)) {
            results.set(key + ":2", "    // recovery after a shot at " + FinderResults.num(targetRpm, 0) + " RPM: "
                    + FinderResults.num(recoverySec, 2) + " s");
        }
        boolean knowVolts = tuneVolts >= 9 && tuneVolts <= 14;
        if (knowVolts) {
            results.set(key + ":3", "    // tuned at " + FinderResults.num(tuneVolts, 1) + " V: add .batteryCompensation(Hw.batteryReader(hardwareMap), "
                    + FinderResults.num(tuneVolts, 1) + ")");
        }
        // The same gains for the robot in five files (Tunables.java). A slow recovery also needs a
        // longer time between two shots (shotIntervalSec, default 0.25 s): a little above the recovery.
        List<String> fields = new ArrayList<>();
        fields.add("kP = " + FinderResults.num(gains[0], 2));
        fields.add("kI = " + FinderResults.num(gains[1], 3));
        fields.add("kD = 0");
        fields.add("kF = " + FinderResults.num(gains[3], 2));
        // The voltage the gains are for: the launcher's battery compensation scales F from it.
        if (knowVolts) fields.add("nominalVolts = " + FinderResults.num(tuneVolts, 1));
        if (!Double.isNaN(recoverySec) && recoverySec > 0.25) {
            fields.add("shotIntervalSec = " + FinderResults.num(Math.ceil(recoverySec * 20) / 20 + 0.05, 2));
        }
        results.set(key + ":t0", FinderResults.tunablesLine(motorName, fields));
        if (reversed) results.set(key + ":t1", "Robot.java: add .reversed() to the \"" + motorName + "\" launcher");
    }

    @Override
    protected void show(RobotTelemetry t) {
        if (motor == null) {
            t.section("Choose the flywheel motor (D-pad, then A)");
            chooser.addTelemetry(t, "No motors in the robot configuration.");
            return;
        }
        t.section("Flywheel \"" + motorName + "\"  (BACK = another)");
        double rpm = FinderMath.ticksPerSecToRpm(motor.getVelocity(), countsPerRev());
        t.line("Speed", FinderResults.num(rpm, 0) + " RPM");
        if (!Double.isNaN(volts)) {
            t.line("Battery", FinderResults.num(volts, 2) + " V" + (tuneVolts >= 9 ? " (gains for " + FinderResults.num(tuneVolts, 1) + " V)" : ""));
            if (volts < Flywheel.LOW_BATTERY_VOLTS) t.warn("Battery low: charge it before measuring (the gains are for this voltage).");
        }
        if (gains == null) {
            t.line("Direction", reversed ? "reversed (X)" : "normal (X = reverse)");
            t.text("Step 1: hold A for full power until the speed stops rising.");
            t.text("Wheel spins the wrong way? Let go, press X, try again.");
            return;
        }
        t.line("Top speed", FinderResults.num(topRpm(), 0) + " RPM");
        // Forward power but the count goes down: the encoder disagrees with the motor. Speed control
        // cannot work like that (it would run away), so step 2 is not allowed.
        if (topTicks < 0) t.warn("The encoder counts BACKWARD at forward power: check the encoder cable and motor port.");
        t.line("F gain", FinderResults.num(gains[3], 2) + " (RB/LB)");
        if (!holding) {
            t.text("Step 2: B = hold a speed. (Or hold A again to measure the top speed again.)");
            return;
        }
        t.line("Target", FinderResults.num(targetRpm, 0) + " RPM (D-pad)");
        t.line("Error", FinderResults.num(targetRpm - Math.abs(flywheel.getRpm()), 0) + " RPM");
        t.line("Ready", flywheel.isReady() ? "YES" : "no");
        t.line("Shots seen", flywheel.getShotCount());
        if (!Double.isNaN(recoverySec)) t.line("Recovery", FinderResults.num(recoverySec, 2) + " s");
        t.text("Too slow at every speed: F up (RB). Too fast: F down (LB).");
    }

    @Override
    protected String[] helpLines() {
        return new String[] {
            "Finds flywheel gains, top speed, recovery time.",
            "Hands away from the wheel. Eye protection on.",
            "1. D-pad up/down = choose the motor, A = use it.",
            "2. Let go of A. Hold A = full power (max 5 s) until",
            "   the speed stops rising: gains worked out.",
            "   Wrong way? Let go, X = reverse, try again.",
            "3. B = hold a speed (60% of top). D-pad up/down =",
            "   +/-100 RPM, right/left = +/-25 RPM.",
            "4. Too slow at every speed: RB (F +2%).",
            "   Too fast: LB (F -2%).",
            "5. Feed a piece by hand: the recovery time shows.",
            "   B again = stop. BACK = another motor.",
            "STOP saves the lines (gains + the battery volts).",
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
