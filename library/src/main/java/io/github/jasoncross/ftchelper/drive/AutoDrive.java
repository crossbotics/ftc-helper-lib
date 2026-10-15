package io.github.jasoncross.ftchelper.drive;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.util.Angles;
import io.github.jasoncross.ftchelper.util.MathUtil;

/**
 * Autonomous moves for a mecanum robot, using the wheel encoders and the IMU.
 * (This was the blocking part of last season's Drivebase.)
 *
 * <p>Each method WAITS until the move ends ("blocking"), so use these only in a
 * {@link LinearOpMode} autonomous. Each one stops when it is done, when it is stuck,
 * when it times out, or when the OpMode stops. The motors are always stopped at the end.
 * No wheel ever gets more than {@link DriveConfig#powerLimit}.
 *
 * <pre>{@code
 * AutoDrive auto = new AutoDrive(this, driveConfig, autoConfig, imu);
 * waitForStart();
 * auto.move(24, 0, 0.6);      // 24 inches forward
 * auto.move(12, 90, 0.5);     // 12 inches right
 * auto.turn(90, 0.5);         // turn 90 degrees left
 * auto.turnTo(0, 0.5);        // face the starting direction again
 * }</pre>
 *
 * <p>Directions in {@link #move}: 0 = forward, 90 = right, 180 = back, -90 = left (clockwise).
 * Turns: positive = left (counterclockwise), like the IMU.
 *
 * Docs: docs/modules/drive.md
 */
public class AutoDrive {

    /** How a move ended. */
    public enum Result {
        /** Reached the target. */
        DONE,
        /** Stopped early because the robot was stuck. */
        STALLED,
        /** Stopped because it took longer than {@code actionTimeoutSec}. */
        TIMED_OUT,
        /** Stopped because the OpMode was stopped. */
        STOPPED
    }

    private final AutoDriveConfig cfg;
    private final double powerLimit;
    private final DcMotorEx fl, fr, bl, br;
    private final DcMotorEx[] motors;
    private final DoubleSupplier heading;
    private final Runnable headingReset;
    private final BooleanSupplier isActive;
    private final Runnable idle;
    private final Consumer<String> log;
    private final LongSupplier clockNanos;
    private Runnable loopHook = null;

    /**
     * Sets up the four drive motors (encoders reset, RUN_USING_ENCODER, brake, power 0).
     *
     * @param opMode       your autonomous OpMode, usually {@code this}
     * @param driveConfig  motor names, directions and power limit (the same one TeleOp uses)
     * @param autoConfig   tuning; {@code calibrationConfirmed} must be true
     * @param imu          a started IMU, e.g. from {@link ImuSetup#get}
     * @throws IllegalStateException if {@code autoConfig.calibrationConfirmed} is false
     */
    public AutoDrive(final LinearOpMode opMode, DriveConfig driveConfig, AutoDriveConfig autoConfig, final IMU imu) {
        this(checked(autoConfig), driveConfig.powerLimit,
                setUp(opMode, driveConfig.frontLeft, driveConfig.frontLeftReversed),
                setUp(opMode, driveConfig.frontRight, driveConfig.frontRightReversed),
                setUp(opMode, driveConfig.backLeft, driveConfig.backLeftReversed),
                setUp(opMode, driveConfig.backRight, driveConfig.backRightReversed),
                () -> ImuSetup.headingDeg(imu), imu::resetYaw,
                opMode::opModeIsActive, opMode::idle, line -> opMode.telemetry.addLine(line),
                System::nanoTime);
    }

    /**
     * For tests and simulations: everything AutoDrive needs, given directly.
     * The motors must already be set up (directions, encoder mode).
     */
    AutoDrive(AutoDriveConfig cfg, double powerLimit,
              DcMotorEx fl, DcMotorEx fr, DcMotorEx bl, DcMotorEx br,
              DoubleSupplier headingCcwDeg, Runnable headingReset,
              BooleanSupplier isActive, Runnable idle, Consumer<String> log, LongSupplier clockNanos) {
        this.cfg = checked(cfg);
        this.powerLimit = MathUtil.clamp(powerLimit, 0, 1);
        this.fl = fl;
        this.fr = fr;
        this.bl = bl;
        this.br = br;
        this.motors = new DcMotorEx[]{fl, fr, bl, br};
        this.heading = headingCcwDeg;
        this.headingReset = headingReset;
        this.isActive = isActive;
        this.idle = idle;
        this.log = log;
        this.clockNanos = clockNanos;
    }

    private static AutoDriveConfig checked(AutoDriveConfig cfg) {
        if (!cfg.calibrationConfirmed) {
            throw new IllegalStateException("AutoDriveConfig.calibrationConfirmed is false. Check the wheel size, "
                    + "motor directions and IMU mounting, then set it to true. See docs/modules/drive.md.");
        }
        if (!(cfg.actionTimeoutSec > 0) || Double.isInfinite(cfg.actionTimeoutSec)) {
            throw new IllegalArgumentException("actionTimeoutSec must be a positive number");
        }
        return cfg;
    }

    private static DcMotorEx setUp(LinearOpMode opMode, String name, boolean reversed) {
        return MotorSetup.get(opMode.hardwareMap, name)
                .reversed(reversed).brake().resetEncoder().runUsingEncoder().done();
    }

    /**
     * Runs your code once per loop during every move and turn. Use it to keep other
     * mechanisms working while the robot drives (for example a lift PID or a flywheel).
     * The code must be quick. Pass null to remove it.
     */
    public void setLoopHook(Runnable hook) {
        this.loopHook = hook;
    }

    // =========================================================================================
    // Moves
    // =========================================================================================

    /**
     * Drives a distance in a straight line, robot-relative, without turning.
     * Full speed until the last part, then slows down (see {@code taperStartFraction}).
     *
     * @param distanceIn   inches. A negative distance goes the opposite way.
     * @param directionDeg robot-relative direction: 0 forward, 90 right, 180 back, -90 left
     * @param maxPower     0.1 to 1 (0.3 to 0.7 is usual). Never more than the DriveConfig power limit.
     */
    public Result move(double distanceIn, double directionDeg, double maxPower) {
        long deadline = deadlineFromNow();
        try {
            if (distanceIn < 0) {
                distanceIn = -distanceIn;
                directionDeg += 180;
            }
            if (distanceIn * cfg.ticksPerInch() < 5.0) return Result.DONE;
            double maxSpeed = Math.max(cfg.moveMinPower, MathUtil.clamp(maxPower, 0.1, 1.0));
            double rad = Math.toRadians(directionDeg);
            double[] command = commandFor(Math.cos(rad), Math.sin(rad));

            int[] start = getWheelTicks();
            StallDetector stall = new StallDetector(cfg.stallProgressIn, cfg.stallTimeSec);

            while (true) {
                Result early = checkStop(deadline);
                if (early != null) return early;

                double[] moved = displacementIn(start, getWheelTicks());
                double traveled = Math.hypot(moved[0], moved[1]);
                if (traveled >= distanceIn) return Result.DONE;

                double remaining = distanceIn - traveled;
                double speed = MotionMath.taperSpeed(remaining / distanceIn,
                        cfg.moveMinPower, maxSpeed, cfg.taperStartFraction);
                setPowers(MecanumMixer.mix(speed * command[0], speed * command[1], 0, powerLimit));

                if (cfg.stallExitEnabled && stall.update(remaining, seconds())) {
                    log.accept("AutoDrive: move stopped, robot stuck");
                    return Result.STALLED;
                }
                runLoopHook();
                idle.run();
            }
        } finally {
            stop();
        }
    }

    /**
     * Drives a distance in a FIELD direction while turning to a heading at the same time.
     * The direction stays fixed on the field even while the robot turns. If the move
     * finishes ({@code DONE}) but the heading is not there yet, it finishes the turn,
     * inside the same time limit. After a stall it does NOT turn.
     *
     * @param distanceIn        inches (negative = opposite way)
     * @param fieldDirectionDeg direction on the field, clockwise from where heading 0 points:
     *                          0 forward, 90 right, 180 back, -90 left
     * @param targetHeadingDeg  heading to end at, IMU degrees (counterclockwise positive)
     * @param maxPower          translation power, 0.1 to 1
     * @param maxTurnPower      turn power, 0.1 to 1
     */
    public Result moveWithTurn(double distanceIn, double fieldDirectionDeg, double targetHeadingDeg,
                               double maxPower, double maxTurnPower) {
        long deadline = deadlineFromNow();
        try {
            if (distanceIn < 0) {
                distanceIn = -distanceIn;
                fieldDirectionDeg += 180;
            }
            double maxSpeed = Math.max(cfg.moveMinPower, MathUtil.clamp(maxPower, 0.1, 1.0));
            maxTurnPower = MathUtil.clamp(maxTurnPower, 0.1, 1.0);
            Result result = Result.DONE;

            if (distanceIn * cfg.ticksPerInch() >= 5.0) {
                PidController headingPid = new PidController(cfg.turnKp, 0, cfg.turnKd, 0, clockNanos)
                        .setAngleMode(true).setOutputLimits(-maxTurnPower, maxTurnPower);
                StallDetector stall = new StallDetector(cfg.stallProgressIn, cfg.stallTimeSec);
                int[] last = getWheelTicks();
                double traveled = 0;

                while (true) {
                    Result early = checkStop(deadline);
                    if (early != null) return early;

                    double headingNow = heading.getAsDouble();
                    double[] dir = MecanumMixer.fieldDirection(fieldDirectionDeg, headingNow);
                    double[] command = commandFor(dir[0], dir[1]);
                    // PID output is counterclockwise; drive turn is clockwise, so flip the sign.
                    double turn = -headingPid.calculate(targetHeadingDeg, headingNow) * cfg.moveTurnScale;
                    turn = MathUtil.clamp(turn, -cfg.moveMaxTurnPower, cfg.moveMaxTurnPower);

                    int[] now = getWheelTicks();
                    double[] step = displacementIn(last, now);
                    last = now;
                    traveled += Math.hypot(step[0], step[1]);
                    if (traveled >= distanceIn) break;

                    double remaining = distanceIn - traveled;
                    double speed = MotionMath.taperSpeed(remaining / distanceIn,
                            cfg.moveMinPower, maxSpeed, cfg.taperStartFraction);
                    setPowers(MecanumMixer.mix(speed * command[0], speed * command[1], turn, powerLimit));

                    if (cfg.stallExitEnabled && stall.update(remaining, seconds())) {
                        log.accept("AutoDrive: move stopped, robot stuck");
                        return Result.STALLED;
                    }
                    runLoopHook();
                    idle.run();
                }
                stop();
            }

            // Finish the turn, only after a finished move, inside the same deadline.
            if (Math.abs(Angles.shortestDiff(targetHeadingDeg, heading.getAsDouble())) > cfg.turnToleranceDeg) {
                result = turnUntil(targetHeadingDeg, maxTurnPower, deadline);
            }
            return result;
        } finally {
            stop();
        }
    }

    /**
     * Turns in place by an amount.
     *
     * @param degrees  positive = left (counterclockwise), negative = right
     * @param maxPower 0.2 to 1 (0.3 to 0.6 is usual)
     */
    public Result turn(double degrees, double maxPower) {
        return turnTo(Angles.wrapDegrees(heading.getAsDouble() + degrees), maxPower);
    }

    /**
     * Turns in place to face a heading.
     *
     * @param headingDeg target IMU heading, degrees (counterclockwise positive, 0 = start direction)
     * @param maxPower   0.2 to 1
     */
    public Result turnTo(double headingDeg, double maxPower) {
        return turnUntil(headingDeg, maxPower, deadlineFromNow());
    }

    private Result turnUntil(double targetDeg, double maxPower, long deadline) {
        try {
            maxPower = Math.min(MathUtil.clamp(maxPower, 0.2, 1.0), powerLimit);
            PidController pid = new PidController(cfg.turnKp, 0, cfg.turnKd, 0, clockNanos)
                    .setAngleMode(true).setOutputLimits(-maxPower, maxPower);
            double minPower = Math.min(cfg.turnMinPower, maxPower);
            double settleStart = -1;
            while (true) {
                Result early = checkStop(deadline);
                if (early != null) return early;

                double ccw = pid.calculate(targetDeg, heading.getAsDouble());
                double error = pid.getLastError();
                boolean inside = Math.abs(error) <= cfg.turnToleranceDeg;
                if (!inside && Math.abs(ccw) < minPower) ccw = Math.signum(error) * minPower;
                // Counterclockwise: left wheels back, right wheels forward.
                setPowers(new double[]{-ccw, ccw, -ccw, ccw});

                if (inside) {
                    if (settleStart < 0) settleStart = seconds();
                    if (seconds() - settleStart >= cfg.turnSettleSec) return Result.DONE;
                } else {
                    settleStart = -1;
                }
                runLoopHook();
                idle.run();
            }
        } finally {
            stop();
        }
    }

    // =========================================================================================
    // Helpers
    // =========================================================================================

    /** The robot's heading, IMU degrees (counterclockwise positive). */
    public double headingDeg() {
        return heading.getAsDouble();
    }

    /** Sets the current heading to 0. */
    public void resetHeading() {
        headingReset.run();
    }

    /** Encoder counts, in the order front-left, front-right, back-left, back-right. */
    public int[] getWheelTicks() {
        return new int[]{fl.getCurrentPosition(), fr.getCurrentPosition(),
                bl.getCurrentPosition(), br.getCurrentPosition()};
    }

    /** Stops all four wheels (brake). */
    public void stop() {
        for (DcMotorEx motor : motors) {
            if (motor.getZeroPowerBehavior() != DcMotor.ZeroPowerBehavior.BRAKE) {
                motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            }
            motor.setPower(0);
        }
    }

    /**
     * How far the robot moved between two encoder readings, robot-relative:
     * {forward inches, right inches}. Sideways wheel travel is divided by {@code strafeCorrection},
     * because mecanum wheels turn more than the robot moves sideways.
     */
    double[] displacementIn(int[] from, int[] to) {
        double[] d = new double[4];
        for (int i = 0; i < 4; i++) d[i] = to[i] - from[i];
        double tpi = cfg.ticksPerInch();
        double forward = (d[0] + d[1] + d[2] + d[3]) / 4.0 / tpi;
        double right = (d[0] - d[1] - d[2] + d[3]) / 4.0 / tpi / cfg.strafeCorrection;
        return new double[]{forward, right};
    }

    /**
     * Turns a wanted direction into a drive command. The sideways part is boosted by
     * {@code strafeCorrection} so the robot really travels in the wanted direction.
     */
    private double[] commandFor(double forward, double right) {
        double lateral = right * cfg.strafeCorrection;
        double largest = Math.max(1.0, Math.max(Math.abs(forward), Math.abs(lateral)));
        return new double[]{forward / largest, lateral / largest};
    }

    private void setPowers(double[] powers) {
        fl.setPower(MathUtil.clamp(powers[0], -powerLimit, powerLimit));
        fr.setPower(MathUtil.clamp(powers[1], -powerLimit, powerLimit));
        bl.setPower(MathUtil.clamp(powers[2], -powerLimit, powerLimit));
        br.setPower(MathUtil.clamp(powers[3], -powerLimit, powerLimit));
    }

    private long deadlineFromNow() {
        return clockNanos.getAsLong() + (long) (cfg.actionTimeoutSec * 1e9);
    }

    private Result checkStop(long deadline) {
        if (!isActive.getAsBoolean()) return Result.STOPPED;
        if (clockNanos.getAsLong() - deadline >= 0) {
            log.accept("AutoDrive: timed out");
            return Result.TIMED_OUT;
        }
        return null;
    }

    private void runLoopHook() {
        if (loopHook != null) loopHook.run();
    }

    private double seconds() {
        return clockNanos.getAsLong() / 1e9;
    }
}
