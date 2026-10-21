package com.crossbotics.ftchelper.drive;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.hardware.ImuSetup;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.util.MathUtil;

/**
 * Autonomous moves for a mecanum OR standard (tank) robot, using the wheel encoders and the IMU.
 * (This was the blocking part of last season's Drivebase.)
 *
 * <p>An encoder is a sensor on the motor that counts how far it has turned (in "ticks"). The IMU is
 * the sensor in the Control Hub that knows which way the robot is facing (its "heading").
 *
 * <p>Standard (tank) drive can only move forward and back (direction 0 or 180) and turn;
 * sideways moves and {@link #moveWithTurn} throw an error, because the robot cannot do them.
 *
 * <p><b>BLOCKING ON PURPOSE:</b> each move/turn method WAITS until the move ends, so use these only
 * in a {@link LinearOpMode} autonomous, never in TeleOp. Your mechanisms keep working during a move
 * through {@link #setLoopHook} (or {@code AutoRoutine}, which does that for you). Each one stops when it is done, when it is stuck,
 * when it times out, or when the OpMode stops. The motors are always stopped at the end.
 * No wheel ever gets more than {@link DriveConfig#powerLimit}.
 * Why waiting is fine here and nowhere else: docs/guides/non-blocking.md.
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
 * Turns: positive = left (counterclockwise), like the IMU. The full sign rules: docs/conventions.md.
 *
 * Docs: docs/modules/drive.md (section "AutoDrive"). Tutorial 09 builds a first autonomous with it.
 */
public class AutoDrive {

    /**
     * How a move ended. Every move and turn returns one of these, so your autonomous can react
     * (for example, skip the next step if the robot got stuck).
     * An {@code enum} is a fixed list of named choices; a variable of this type can only be one of them.
     */
    public enum Result {
        /** Reached the target. */
        DONE,
        /** Did not move, or stopped: the odometry is not working (a cable, a fault). */
        NO_ODOMETRY,
        /** Stopped early because the robot was stuck. */
        STALLED,
        /** Stopped because it took longer than {@code actionTimeoutSec}. */
        TIMED_OUT,
        /** Stopped because the OpMode was stopped. */
        STOPPED
    }

    // ---- Settings and parts, fixed when the AutoDrive is made ----
    // "final" means the value is set once (in the constructor) and never changes afterwards.
    private final AutoDriveConfig cfg;
    // True for a standard (tank) drive: it cannot move sideways.
    private final boolean tank;
    // The largest power any wheel may get, 0 to 1 (from DriveConfig).
    private final double powerLimit;
    private final DcMotorEx fl, fr, bl, br;
    // Only the motors that really exist (2 or 4), so stop() does not touch a motor twice.
    private final DcMotorEx[] motors;
    // The next few are small pieces of code handed to us that we call when we need them.
    // A "supplier" gives back a value each time we ask: here the IMU heading in degrees
    // (counterclockwise positive).
    private final DoubleSupplier heading;
    // Sets the IMU heading back to 0.
    private final Runnable headingReset;
    // Gives true while the OpMode is still running (false once STOP is pressed).
    private final BooleanSupplier isActive;
    // Gives the robot's system a moment to do its own work between our loops.
    private final Runnable idle;
    // Writes a line on the Driver Station (telemetry), for messages like "robot stuck".
    private final Consumer<String> log;
    // The time now, in nanoseconds (billionths of a second). Handed in so tests can fake the clock.
    private final LongSupplier clockNanos;
    // ---- Options that can change later (null = not used) ----
    private Runnable loopHook = null;
    private BooleanSupplier stopCondition = null;

    /**
     * Sets up the four drive motors (encoders reset, RUN_USING_ENCODER, brake, power 0).
     * Brake means the wheels hold still at power 0 instead of rolling, so the robot stops where
     * the move ends. Encoders are reset so every autonomous starts counting from 0.
     *
     * @param opMode       your autonomous OpMode, usually {@code this}
     * @param driveConfig  motor names, directions and power limit (the same one TeleOp uses)
     * @param autoConfig   tuning; {@code calibrationConfirmed} must be true
     * @param imu          a started IMU, e.g. from {@link ImuSetup#get}
     * @throws IllegalStateException if {@code autoConfig.calibrationConfirmed} is false
     */
    public AutoDrive(final LinearOpMode opMode, DriveConfig driveConfig, AutoDriveConfig autoConfig, final IMU imu) {
        // "this(...)" hands everything to the second constructor below, so both share one setup.
        // "a ? b : c" means "if a then b, else c": a 2-motor tank drive has no back motors (null).
        // "() -> ..." is a small piece of code we hand over to run later (each time a heading is
        // needed), and "imu::resetYaw" is a short way to say "call imu.resetYaw() when asked".
        this(checked(autoConfig), validated(driveConfig).type == DriveConfig.DriveType.TANK, driveConfig.powerLimit,
                setUp(opMode, driveConfig.frontLeft, driveConfig.frontLeftReversed),
                setUp(opMode, driveConfig.frontRight, driveConfig.frontRightReversed),
                driveConfig.hasBackMotors() ? setUp(opMode, driveConfig.backLeft, driveConfig.backLeftReversed) : null,
                driveConfig.hasBackMotors() ? setUp(opMode, driveConfig.backRight, driveConfig.backRightReversed) : null,
                () -> ImuSetup.headingDeg(imu), imu::resetYaw,
                opMode::opModeIsActive, opMode::idle, line -> opMode.telemetry.addLine(line),
                System::nanoTime);
    }

    /**
     * For tests and simulations (no robot needed): everything AutoDrive needs, given directly.
     * The motors must already be set up (directions, encoder mode). For a 2-motor tank drive,
     * {@code bl} and {@code br} are null.
     *
     * @param headingCcwDeg gives the heading in degrees, counterclockwise positive
     * @param isActive      gives false once the OpMode should stop
     * @param clockNanos    gives the time now in nanoseconds (a fake clock in tests)
     */
    public AutoDrive(AutoDriveConfig cfg, boolean tank, double powerLimit,
              DcMotorEx fl, DcMotorEx fr, DcMotorEx bl, DcMotorEx br,
              DoubleSupplier headingCcwDeg, Runnable headingReset,
              BooleanSupplier isActive, Runnable idle, Consumer<String> log, LongSupplier clockNanos) {
        this.cfg = checked(cfg);
        this.tank = tank;
        // Mecanum math uses all four wheels, so refuse to start without them.
        if (!tank && (bl == null || br == null)) {
            throw new IllegalArgumentException("A mecanum AutoDrive needs four motors");
        }
        // Keep the limit inside 0..1: a motor only accepts powers from -1 to 1.
        this.powerLimit = MathUtil.clamp(powerLimit, 0, 1);
        this.fl = fl;
        this.fr = fr;
        // A 2-motor tank robot reads its "back" wheels from the front motors; the math stays the same.
        this.bl = bl != null ? bl : fl;
        this.br = br != null ? br : fr;
        this.motors = bl != null ? new DcMotorEx[]{fl, fr, bl, br} : new DcMotorEx[]{fl, fr};
        this.heading = headingCcwDeg;
        this.headingReset = headingReset;
        this.isActive = isActive;
        this.idle = idle;
        this.log = log;
        this.clockNanos = clockNanos;
    }

    // "static" means this helper belongs to the class itself, not to one AutoDrive. That lets the
    // constructor call it before the AutoDrive exists. It refuses settings that would be unsafe.
    private static AutoDriveConfig checked(AutoDriveConfig cfg) {
        // The team must say "yes, we measured the robot" first. Wrong wheel size or motor
        // directions would make the robot drive the wrong distance or the wrong way.
        if (!cfg.calibrationConfirmed) {
            throw new IllegalStateException("AutoDriveConfig.calibrationConfirmed is false. Check the wheel size, "
                    + "motor directions and IMU mounting, then set it to true. See docs/modules/drive.md.");
        }
        // Every move needs a real time limit. "!(x > 0)" also catches NaN (not a number), which
        // would otherwise slip through a plain "x <= 0" test.
        if (!(cfg.actionTimeoutSec > 0) || Double.isInfinite(cfg.actionTimeoutSec)) {
            throw new IllegalArgumentException("actionTimeoutSec must be a positive number");
        }
        return cfg;
    }

    // Checks the motor names make sense, then hands the same config back so it can be used in "this(...)".
    private static DriveConfig validated(DriveConfig driveConfig) {
        driveConfig.validate();
        return driveConfig;
    }

    // Gets one motor and prepares it for autonomous: direction, brake, encoder count at 0, and
    // RUN_USING_ENCODER (the motor controller uses the encoder to keep the speed steady).
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

    /**
     * An extra reason to stop a move early: when this is true, the move ends with {@code STOPPED}.
     * {@code AutoRoutine} uses it to end the main part of autonomous on time for parking.
     * Pass null to remove it.
     */
    public void setStopCondition(BooleanSupplier condition) {
        this.stopCondition = condition;
    }

    // =========================================================================================
    // Moves
    // =========================================================================================

    /**
     * Drives a distance in a straight line, robot-relative, without turning.
     * Full speed until the last part, then slows down (see {@code taperStartFraction}).
     *
     * <p><b>BLOCKS</b> (on purpose): returns when the move ends. Autonomous only; never in TeleOp.
     *
     * @param distanceIn   inches. A negative distance goes the opposite way.
     * @param directionDeg robot-relative direction: 0 forward, 90 right, 180 back, -90 left
     * @param maxPower     0.1 to 1 (0.3 to 0.7 is usual). Never more than the DriveConfig power limit.
     * @return how the move ended: DONE, STALLED, TIMED_OUT or STOPPED
     * @throws IllegalArgumentException on a standard (tank) drive, for any direction except 0 or 180
     */
    public Result move(double distanceIn, double directionDeg, double maxPower) {
        // Start the clock now: the whole move must finish within actionTimeoutSec.
        long deadline = deadlineFromNow();
        // "try { ... } finally { stop(); }": whatever happens inside (done, stuck, timed out,
        // STOP pressed, even an error), the code in "finally" still runs, so the wheels never
        // keep driving after the move.
        try {
            // "-24 inches at 0 degrees" is the same as "24 inches at 180 degrees". Using a positive
            // distance keeps the rest of the math simple.
            if (distanceIn < 0) {
                distanceIn = -distanceIn;
                directionDeg += 180;
            }
            // A tank drive can only go straight forward or back. Any direction with a sideways
            // part (its sine is not 0) is impossible for it, so say so clearly instead of guessing.
            if (tank && Math.abs(Math.sin(Math.toRadians(directionDeg))) > 1e-6) {
                throw new IllegalArgumentException("A standard (tank) drive can only move at 0 (forward) or 180 (back), not "
                        + directionDeg + ". Turn first, then move.");
            }
            // Less than 5 encoder ticks is too small to measure: count it as done right away.
            if (distanceIn * cfg.ticksPerInch() < 5.0) return Result.DONE;
            // Cruise speed: inside 0.1..1, and never below the slowest power the taper ends at.
            double maxSpeed = Math.max(cfg.moveMinPower, MathUtil.clamp(maxPower, 0.1, 1.0));
            double rad = Math.toRadians(directionDeg);
            // Split the direction into a forward part (cosine) and a right part (sine).
            // Tank: just +1 (forward) or -1 (back). Mecanum: see commandFor() below.
            double[] command = tank ? new double[]{Math.signum(Math.cos(rad)), 0} : commandFor(Math.cos(rad), Math.sin(rad));

            // Remember where the wheels started, so we can measure how far we have gone.
            int[] start = getWheelTicks();
            StallDetector stall = new StallDetector(cfg.stallProgressIn, cfg.stallTimeSec);

            // Loop until one of the "return" lines ends the move. Each pass is one short step.
            while (true) {
                // STOP pressed, the stop condition, or out of time? Then end now.
                Result early = checkStop(deadline);
                if (early != null) return early;

                // How far have we gone so far, in inches? hypot() gives the straight-line
                // distance from the forward and sideways parts (like the long side of a triangle).
                double[] moved = displacementIn(start, getWheelTicks());
                double traveled = Math.hypot(moved[0], moved[1]);
                if (traveled >= distanceIn) return Result.DONE;

                // Slow down near the end so the robot does not overshoot or skid (see MotionMath).
                double remaining = distanceIn - traveled;
                double speed = MotionMath.taperSpeed(remaining / distanceIn,
                        cfg.moveMinPower, maxSpeed, cfg.taperStartFraction);
                // Turn "forward, right" at this speed into four wheel powers (no turning).
                setPowers(MecanumMixer.mix(speed * command[0], speed * command[1], 0, powerLimit));

                // Stuck? (Pushing a wall or another robot: the distance left stops going down.)
                // Give up instead of burning the motors and the time.
                if (cfg.stallExitEnabled && stall.update(remaining, seconds())) {
                    log.accept("AutoDrive: move stopped, robot stuck");
                    return Result.STALLED;
                }
                // Let the other mechanisms (lift, flywheel...) do their work for this loop too.
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
     * <p><b>BLOCKS</b> (on purpose): returns when the move ends. Autonomous only; never in TeleOp.
     *
     * @param distanceIn        inches (negative = opposite way)
     * @param fieldDirectionDeg direction on the field, clockwise from where heading 0 points:
     *                          0 forward, 90 right, 180 back, -90 left
     * @param targetHeadingDeg  heading to end at, IMU degrees (counterclockwise positive)
     * @param maxPower          translation power, 0.1 to 1
     * @param maxTurnPower      turn power, 0.1 to 1
     * @return how the move ended: DONE, STALLED, TIMED_OUT or STOPPED
     * @throws UnsupportedOperationException on a standard (tank) drive
     */
    public Result moveWithTurn(double distanceIn, double fieldDirectionDeg, double targetHeadingDeg,
                               double maxPower, double maxTurnPower) {
        // Driving and turning at once needs sideways wheels; a tank drive cannot do it.
        if (tank) {
            throw new UnsupportedOperationException("moveWithTurn needs mecanum wheels. On a standard (tank) drive, "
                    + "use turnTo(...) and then move(...).");
        }
        // One time limit for the whole thing: the move AND the final turn.
        long deadline = deadlineFromNow();
        // finally: the wheels always stop at the end (see move()).
        try {
            // A negative distance is the same as the opposite direction.
            if (distanceIn < 0) {
                distanceIn = -distanceIn;
                fieldDirectionDeg += 180;
            }
            double maxSpeed = Math.max(cfg.moveMinPower, MathUtil.clamp(maxPower, 0.1, 1.0));
            maxTurnPower = MathUtil.clamp(maxTurnPower, 0.1, 1.0);
            Result result = Result.DONE;

            // Only drive if the distance is big enough to measure (5 encoder ticks or more).
            if (distanceIn * cfg.ticksPerInch() >= 5.0) {
                // A PID controller works out the turn power from how far the heading is off.
                // (PID: more power when far off, less when close; Tutorial 07 explains it step by
                // step.) Angle mode makes it take the short way around the circle.
                PidController headingPid = new PidController(cfg.turnKp, 0, cfg.turnKd, 0, clockNanos)
                        .setAngleMode(true).setOutputLimits(-maxTurnPower, maxTurnPower);
                StallDetector stall = new StallDetector(cfg.stallProgressIn, cfg.stallTimeSec);
                int[] last = getWheelTicks();
                double traveled = 0;

                while (true) {
                    Result early = checkStop(deadline);
                    if (early != null) return early;

                    // The robot is turning, so the same field direction means a different
                    // robot direction each loop. Work it out again from the heading right now.
                    double headingNow = heading.getAsDouble();
                    double[] dir = MecanumMixer.fieldDirection(fieldDirectionDeg, headingNow);
                    double[] command = commandFor(dir[0], dir[1]);
                    // PID output is counterclockwise; drive turn is clockwise, so flip the sign.
                    double turn = -headingPid.calculate(targetHeadingDeg, headingNow) * cfg.moveTurnScale;
                    // Cap the turn so driving stays the main job and the robot does not spin in place.
                    turn = MathUtil.clamp(turn, -cfg.moveMaxTurnPower, cfg.moveMaxTurnPower);

                    // Add up the distance a little piece at a time (since the last loop), because
                    // the robot's "forward" keeps changing while it turns. Turning in place does
                    // not add distance: the wheel counts cancel out in displacementIn().
                    int[] now = getWheelTicks();
                    double[] step = displacementIn(last, now);
                    last = now;
                    traveled += Math.hypot(step[0], step[1]);
                    // Far enough: leave the loop and go on to finish the turn below.
                    if (traveled >= distanceIn) break;

                    // Slow down near the end, like move().
                    double remaining = distanceIn - traveled;
                    double speed = MotionMath.taperSpeed(remaining / distanceIn,
                            cfg.moveMinPower, maxSpeed, cfg.taperStartFraction);
                    setPowers(MecanumMixer.mix(speed * command[0], speed * command[1], turn, powerLimit));

                    // Stuck: give up and do NOT try the turn (the robot is pinned against something).
                    if (cfg.stallExitEnabled && stall.update(remaining, seconds())) {
                        log.accept("AutoDrive: move stopped, robot stuck");
                        return Result.STALLED;
                    }
                    runLoopHook();
                    idle.run();
                }
                // Stop driving before the turn-in-place starts.
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
     * Drives to a point on the field and turns to a heading at the same time, using odometry (mecanum
     * only). It keeps correcting with the odometry's pose every loop, so a bump or wheel slip does not
     * matter: it ends where the field point is, not where the wheels think it is.
     *
     * <p>The frame is the odometry's: x inches right, y inches away from the drivers' wall, heading 0 =
     * facing away, counterclockwise +.
     *
     * <p><b>BLOCKS</b> (on purpose): returns when it arrives, gets stuck or runs out of time.
     *
     * @param pose        the odometry's pose, read each loop (the loop hook updates it)
     * @param xIn         where to go: inches right
     * @param yIn         inches away from the drivers' wall
     * @param headingDeg  the heading to end at (counterclockwise +; 0 = facing away)
     * @param maxPower    0.1 to 1
     * @param toleranceIn close enough, inches (1 is usual)
     * @return DONE, STALLED, TIMED_OUT or STOPPED
     */
    // driveTo's last few inches: proportional speed, so it stops on the point.
    static final double DRIVE_TO_NEAR_IN = 8, DRIVE_TO_KP = 0.04, DRIVE_TO_MIN_POWER = 0.12, DRIVE_TO_MIN_TURN = 0.08;

    public Result driveTo(java.util.function.Supplier<com.crossbotics.ftchelper.util.FieldPose> pose,
                          double xIn, double yIn, double headingDeg, double maxPower, double toleranceIn) {
        return driveTo(pose, () -> true, xIn, yIn, headingDeg, maxPower, toleranceIn);
    }

    /**
     * {@link #driveTo(java.util.function.Supplier, double, double, double, double, double)}, checking every
     * loop that the odometry works: if {@code ready} turns false, the robot stops at once (NO_ODOMETRY).
     * Without it the robot would drive on a frozen pose until the time runs out.
     */
    public Result driveTo(java.util.function.Supplier<com.crossbotics.ftchelper.util.FieldPose> pose,
                          BooleanSupplier ready,
                          double xIn, double yIn, double headingDeg, double maxPower, double toleranceIn) {
        if (tank) {
            throw new UnsupportedOperationException("driveTo needs mecanum wheels (it drives sideways). On a standard "
                    + "(tank) drive, use turnTo(...) and then move(...).");
        }
        long deadline = deadlineFromNow();
        try {
            double maxSpeed = Math.max(cfg.moveMinPower, MathUtil.clamp(maxPower, 0.1, 1.0));
            PidController headingPid = new PidController(cfg.turnKp, 0, cfg.turnKd, 0, clockNanos)
                    .setAngleMode(true).setOutputLimits(-cfg.moveMaxTurnPower, cfg.moveMaxTurnPower);
            StallDetector stall = new StallDetector(cfg.stallProgressIn, cfg.stallTimeSec);
            double startDist = -1;
            int closeLoops = 0;
            while (true) {
                Result early = checkStop(deadline);
                if (early != null) return early;
                if (!ready.getAsBoolean()) {
                    log.accept("AutoDrive: driveTo stopped, the odometry is not working");
                    return Result.NO_ODOMETRY;
                }
                com.crossbotics.ftchelper.util.FieldPose p = pose.get();
                double dx = xIn - p.x, dy = yIn - p.y;
                double dist = Math.hypot(dx, dy);
                if (startDist < 0) startDist = Math.max(dist, 1e-6);
                double headingErr = Angles.shortestDiff(headingDeg, p.headingDeg);
                // There: close enough in place AND heading, for 3 loops in a row (no stopping on a pass-by).
                if (dist <= toleranceIn && Math.abs(headingErr) <= cfg.turnToleranceDeg) {
                    if (++closeLoops >= 3) return Result.DONE;
                } else {
                    closeLoops = 0;
                }
                // The field error seen from the robot: heading h (CCW from "away") faces (-sin h, cos h).
                double h = Math.toRadians(p.headingDeg);
                double forwardErr = -dx * Math.sin(h) + dy * Math.cos(h);
                double rightErr = dx * Math.cos(h) + dy * Math.sin(h);
                // Speed: like move() far away; in the last few inches, proportional to the distance left
                // (DRIVE_TO_KP per inch, at least DRIVE_TO_MIN_POWER), so it settles on the point instead
                // of circling it.
                double speed;
                if (dist <= toleranceIn) speed = 0;
                else if (dist < DRIVE_TO_NEAR_IN) speed = MathUtil.clamp(DRIVE_TO_KP * dist, DRIVE_TO_MIN_POWER, maxSpeed);
                else speed = MotionMath.taperSpeed(dist / startDist, cfg.moveMinPower, maxSpeed, cfg.taperStartFraction);
                double fwd = dist > 1e-6 ? speed * forwardErr / dist : 0;
                double right = dist > 1e-6 ? speed * rightErr / dist : 0;
                // PID output is counterclockwise; the drive's turn is clockwise.
                double turn = -headingPid.calculate(headingDeg, p.headingDeg);
                // A small turn is too weak to move the robot: at least DRIVE_TO_MIN_TURN until it is there.
                if (Math.abs(headingErr) > cfg.turnToleranceDeg && Math.abs(turn) < DRIVE_TO_MIN_TURN) {
                    turn = -Math.signum(headingErr) * DRIVE_TO_MIN_TURN;
                }
                setPowers(MecanumMixer.mix(fwd, right, turn, powerLimit));
                // Stuck only counts away from the point: the last inches are slow on purpose.
                if (cfg.stallExitEnabled && dist > DRIVE_TO_NEAR_IN && stall.update(dist, seconds())) {
                    log.accept("AutoDrive: driveTo stopped, robot stuck");
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
     * Turns in place by an amount.
     *
     * <p><b>BLOCKS</b> (on purpose): returns when the move ends. Autonomous only; never in TeleOp.
     *
     * @param degrees  positive = left (counterclockwise), negative = right
     * @param maxPower 0.2 to 1 (0.3 to 0.6 is usual)
     * @return how the turn ended: DONE, TIMED_OUT or STOPPED
     */
    public Result turn(double degrees, double maxPower) {
        // "Turn 90 more" = "face (heading now + 90)". wrapDegrees keeps it between -180 and 180.
        return turnTo(Angles.wrapDegrees(heading.getAsDouble() + degrees), maxPower);
    }

    /**
     * Turns in place to face a heading.
     *
     * <p><b>BLOCKS</b> (on purpose): returns when the move ends. Autonomous only; never in TeleOp.
     *
     * @param headingDeg target IMU heading, degrees (counterclockwise positive, 0 = start direction)
     * @param maxPower   0.2 to 1
     * @return how the turn ended: DONE, TIMED_OUT or STOPPED
     */
    public Result turnTo(double headingDeg, double maxPower) {
        return turnUntil(headingDeg, maxPower, deadlineFromNow());
    }

    // The turn-in-place loop used by turn(), turnTo() and the end of moveWithTurn().
    // It is only "done" when the heading has stayed close enough for turnSettleSec, so a robot
    // that swings past the target and back is not counted as finished too early.
    private Result turnUntil(double targetDeg, double maxPower, long deadline) {
        try {
            // At least 0.2 (weaker turns may not move the robot), never more than the drive's limit.
            maxPower = Math.min(MathUtil.clamp(maxPower, 0.2, 1.0), powerLimit);
            PidController pid = new PidController(cfg.turnKp, 0, cfg.turnKd, 0, clockNanos)
                    .setAngleMode(true).setOutputLimits(-maxPower, maxPower);
            double minPower = Math.min(cfg.turnMinPower, maxPower);
            // When the heading first came inside the tolerance, in seconds. -1 = not inside now.
            double settleStart = -1;
            while (true) {
                Result early = checkStop(deadline);
                if (early != null) return early;

                // Turn power from the heading error (counterclockwise positive).
                double ccw = pid.calculate(targetDeg, heading.getAsDouble());
                double error = pid.getLastError();
                boolean inside = Math.abs(error) <= cfg.turnToleranceDeg;
                // Close to the target the PID power gets tiny and may be too weak to turn the
                // robot at all. If turnMinPower is set, push with at least that much, the right way.
                if (!inside && Math.abs(ccw) < minPower) ccw = Math.signum(error) * minPower;
                // Counterclockwise: left wheels back, right wheels forward.
                setPowers(new double[]{-ccw, ccw, -ccw, ccw});

                // Done only after staying inside the tolerance for turnSettleSec seconds.
                // Leaving the tolerance starts the count again.
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
            // Always stop the wheels, however the turn ended.
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

    /**
     * Encoder counts, in the order front-left, front-right, back-left, back-right.
     * On a 2-motor tank drive, the back values repeat the front ones.
     */
    public int[] getWheelTicks() {
        return new int[]{fl.getCurrentPosition(), fr.getCurrentPosition(),
                bl.getCurrentPosition(), br.getCurrentPosition()};
    }

    /** Stops all four wheels (brake). */
    public void stop() {
        for (DcMotorEx motor : motors) {
            // Make sure the wheels hold still (brake) instead of rolling on after the move.
            // Only change it if needed: each change is a message to the motor controller.
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
     * (No "public" or "private": only code in this package, like the tests, can use it.)
     */
    double[] displacementIn(int[] from, int[] to) {
        // How many ticks each wheel turned since "from".
        double[] d = new double[4];
        for (int i = 0; i < 4; i++) d[i] = to[i] - from[i];
        double tpi = cfg.ticksPerInch();
        // Forward: all four wheels turn forward, so the average of the four is the forward travel.
        // Dividing ticks by ticks-per-inch turns encoder counts into inches.
        // Turning in place adds nothing here: the left wheels go back as much as the right go forward.
        double forward = (d[0] + d[1] + d[2] + d[3]) / 4.0 / tpi;
        // Right: when strafing right, front-left and back-right turn forward and the other two
        // turn backward (the same pattern as MecanumMixer), so this mix picks out the sideways part.
        double right = (d[0] - d[1] - d[2] + d[3]) / 4.0 / tpi / cfg.strafeCorrection;
        return new double[]{forward, right};
    }

    /**
     * Turns a wanted direction into a drive command. The sideways part is boosted by
     * {@code strafeCorrection} so the robot really travels in the wanted direction.
     */
    private double[] commandFor(double forward, double right) {
        double lateral = right * cfg.strafeCorrection;
        // The boost can push a value past 1. If so, shrink both parts by the same amount,
        // so the direction (the ratio of forward to sideways) stays the same.
        double largest = Math.max(1.0, Math.max(Math.abs(forward), Math.abs(lateral)));
        return new double[]{forward / largest, lateral / largest};
    }

    // Sends the four powers to the motors, each cut to the power limit as a last safety check.
    private void setPowers(double[] powers) {
        fl.setPower(MathUtil.clamp(powers[0], -powerLimit, powerLimit));
        fr.setPower(MathUtil.clamp(powers[1], -powerLimit, powerLimit));
        // On a 2-motor tank drive "bl" and "br" are the front motors again: do not set them twice.
        if (bl != fl) bl.setPower(MathUtil.clamp(powers[2], -powerLimit, powerLimit));
        if (br != fr) br.setPower(MathUtil.clamp(powers[3], -powerLimit, powerLimit));
    }

    // The time (in nanoseconds) when a move that starts now must give up.
    private long deadlineFromNow() {
        return clockNanos.getAsLong() + (long) (cfg.actionTimeoutSec * 1e9);
    }

    // Checks every reason to end a move early. Returns the reason, or null to keep going.
    private Result checkStop(long deadline) {
        // STOP pressed on the Driver Station: end at once.
        if (!isActive.getAsBoolean()) return Result.STOPPED;
        // The extra stop condition (for example AutoRoutine saying "time to park").
        if (stopCondition != null && stopCondition.getAsBoolean()) return Result.STOPPED;
        // Out of time. Written as "now - deadline >= 0" (not "now >= deadline") because that
        // stays correct even if the nanosecond clock number wraps around.
        if (clockNanos.getAsLong() - deadline >= 0) {
            log.accept("AutoDrive: timed out");
            return Result.TIMED_OUT;
        }
        return null;
    }

    private void runLoopHook() {
        if (loopHook != null) loopHook.run();
    }

    // The clock in seconds (nanoseconds / 1,000,000,000).
    private double seconds() {
        return clockNanos.getAsLong() / 1e9;
    }
}
