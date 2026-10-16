package io.github.jasoncross.ftchelper.finders;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.launcher.Flywheel;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;

/**
 * Finds a flywheel's speed-control numbers. Step 1 measures the top speed at full power and works
 * out starting gains from it. Step 2 holds a speed you choose, so you can fine-tune the main gain
 * (F), feed a piece by hand, and see how fast the wheel gets back to speed after a shot.
 * No code to write: it lists every motor in the robot configuration.
 *
 * <pre>
 * Choose:  D-pad up/down = motor, A = use it
 * Step 1:  hold A = full power (at most 5 s). Release when the speed stops rising.
 * Step 2:  B = hold a speed on/off. D-pad up/down = +/- 100 RPM, right/left = +/- 25 RPM.
 *          RB / LB = F gain +2% / -2%. Feed a piece by hand: it shows the recovery time.
 *          BACK = choose another motor
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
    private double[] gains = null;      // {P, I, D, F}
    private double targetRpm = 0;
    private int lastShots = 0;
    private double shotAt = Double.NaN;
    private double recoverySec = Double.NaN;

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
        topTicks = 0;
        gains = null;
        recoverySec = Double.NaN;
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
        holding = !holding;
        if (holding) {
            motor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            flywheel = new Flywheel(motorName, motor).countsPerRev(countsPerRev())
                    .maxRpm(topRpm()).pidf(gains[0], gains[1], gains[2], gains[3]);
            if (topTicks < 0) flywheel.reversed();
            if (targetRpm == 0) targetRpm = Math.round(topRpm() * 0.6 / 25) * 25.0;
            lastShots = 0;
        } else {
            flywheel.stop();
            plainPower();
        }
    }

    // Step 1's set-up: plain power, the motor's normal direction (step 2 may have reversed it).
    private void plainPower() {
        motor.setDirection(DcMotorSimple.Direction.FORWARD);
        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        motor.setPower(0);
    }

    private double topRpm() {
        return Math.abs(FinderMath.ticksPerSecToRpm(topTicks, countsPerRev()));
    }

    @Override
    protected void step() {
        if (motor == null) return;
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
        // Step 1: full power while A is held, at most MAX_TOP_SPEED_SEC.
        if (gamepad1.a) {
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
            gains = FinderMath.flywheelPidf(topTicks);
            writeResults();
        }
    }

    private void writeResults() {
        if (gains == null) return;
        String key = "flywheel:" + motorName;
        results.removeStartingWith(key);
        String line = "Flywheel.create(hardwareMap, \"" + motorName + "\")"
                + ".countsPerRev(" + FinderResults.num(countsPerRev(), 1) + ")"
                + (topTicks < 0 ? ".reversed()" : "")
                + ".pidf(" + FinderResults.num(gains[0], 2) + ", " + FinderResults.num(gains[1], 3) + ", 0, "
                + FinderResults.num(gains[3], 2) + ")"
                + ".maxRpm(" + FinderResults.num(Math.floor(topRpm() * 0.9 / 100) * 100, 0) + ")";
        results.set(key + ":0", line);
        results.set(key + ":1", "    // top speed " + FinderResults.num(topRpm(), 0) + " RPM; maxRpm = 90% of it");
        if (!Double.isNaN(recoverySec)) {
            results.set(key + ":2", "    // recovery after a shot at " + FinderResults.num(targetRpm, 0) + " RPM: "
                    + FinderResults.num(recoverySec, 2) + " s");
        }
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
        if (gains == null) {
            t.text("Step 1: hold A for full power until the speed stops rising.");
            return;
        }
        t.line("Top speed", FinderResults.num(topRpm(), 0) + " RPM" + (topTicks < 0 ? " (backward: reversed)" : ""));
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
    protected void stopAll() {
        holding = false;
        if (flywheel != null) flywheel.stop();
        if (motor != null) motor.setPower(0);
    }
}
