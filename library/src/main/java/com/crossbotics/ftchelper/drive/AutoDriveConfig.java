package com.crossbotics.ftchelper.drive;

/**
 * Tuning for {@link AutoDrive} (autonomous encoder and IMU moves).
 *
 * <p>The defaults are last season's (DECODE 2025-26) values. They are a starting point only.
 * Measure and tune them on the new robot: docs/modules/drive.md, "Tune AutoDrive".
 * Tutorial 09 walks through the tuning on a real robot.
 *
 * <p>These are plain public numbers (no getters): make one, change what is different on your
 * robot, and give it to {@link AutoDrive}.
 */
public class AutoDriveConfig {
    /**
     * Must be set to true before {@link AutoDrive} will run. It is a reminder:
     * set it only after you checked the wheel size, motor directions and IMU mounting.
     * (A robot with wrong numbers can drive the wrong way or much too far in autonomous.)
     */
    public boolean calibrationConfirmed = false;

    // --- Encoders and wheels ---
    // An encoder counts small steps ("ticks") as the motor turns. These numbers turn ticks into inches.
    /** Encoder counts per motor output turn. goBILDA Yellow Jacket 312 RPM (19.2:1) = 537.7. */
    public double ticksPerRev = 537.7;
    /**
     * Wheel diameter, inches. Tune it so a 48-inch move really moves 48 inches. 96 mm = 3.78 in.
     * The default 4.098 in is last season's value (about the size of a 104 mm wheel).
     */
    public double wheelDiameterIn = 4.098;
    /** Motor turns per wheel turn. 1.0 if the wheel is on the motor shaft. */
    public double gearRatio = 1.0;
    /**
     * Mecanum wheels slip sideways: the wheels turn more than the robot moves. Sideways wheel travel is
     * divided by this to get the real distance, and the sideways power is raised by it so diagonal moves
     * keep their direction. Tune on field tiles.
     */
    public double strafeCorrection = 1.15;
    /**
     * The same for strafing LEFT, if the robot slips differently that way (weight to one side, worn
     * rollers). NaN (the default) = {@link #strafeCorrection} both ways. The Drive Tuner's "Strafe left"
     * test finds it.
     */
    public double strafeCorrectionLeft = Double.NaN;

    /** The strafe correction for this side: + = right, - = left. */
    public double strafeCorrectionFor(double right) {
        return right < 0 && !Double.isNaN(strafeCorrectionLeft) ? strafeCorrectionLeft : strafeCorrection;
    }

    // --- Turning (IMU) ---
    // Turns use a PID controller: power grows with how far the heading is off.
    // Tutorial 07 explains PID step by step.
    /** Turn P gain: power per degree of error. */
    public double turnKp = 0.012;
    /** Turn D gain. Slows the turn as it gets close, to stop overshoot. */
    public double turnKd = 0.003;
    /** A turn is done when the error is this small (degrees)... */
    public double turnToleranceDeg = 1.0;
    /** ...for this long (seconds). */
    public double turnSettleSec = 0.15;
    /**
     * Smallest turn power while outside the tolerance, so the robot does not stall just short of
     * the target. 0 = off (last season's behavior). Try 0.05 to 0.1 if turns stop early.
     */
    public double turnMinPower = 0.0;

    // --- Straight moves ---
    /** Slowest translation power near the end of a move. */
    public double moveMinPower = 0.30;
    /** The last part of the move (as a fraction, 0.10 = last 10%) where speed tapers down. */
    public double taperStartFraction = 0.10;
    /**
     * Slow down over at least this many inches at the end of a move (0 = only {@code taperStartFraction}).
     * WHY: 10% of a short move is too little road to slow down on: an 8 in move would slow down over
     * 0.8 in only, still reach the end fast, and slide past it. With 6, every move slows down over its
     * last 6 in (or all of it, if it is shorter), so it reaches the end at about the same slow speed and
     * slides about the same amount, whatever its length. (A faster cruise still arrives a little faster:
     * test the Drive Tuner at your moves' power.) driveTo and routes also slow over their last 8 in.
     */
    public double slowDownIn = 0;
    /** moveWithTurn: heading correction is multiplied by this before mixing. */
    public double moveTurnScale = 0.60;
    /** moveWithTurn: largest heading-correction power, so moving stays the main job. */
    public double moveMaxTurnPower = 0.35;

    // --- Safety ---
    /**
     * Each move or turn gives up after this many seconds. This keeps one bad move (a wrong number,
     * a robot in the way) from eating the whole autonomous period.
     */
    public double actionTimeoutSec = 10.0;
    /** Stop a move early if the robot is stuck (pushing a wall or another robot). */
    public boolean stallExitEnabled = true;
    /**
     * The robot counts as stuck if it gets less than {@link #stallProgressIn} closer to the
     * target within {@link #stallTimeSec}. Defaults: 0.25 in within 0.4 s.
     */
    public double stallProgressIn = 0.25;
    /** See {@link #stallProgressIn}. Seconds. */
    public double stallTimeSec = 0.4;

    /**
     * The part of a move of this length (a fraction, 0 to 1) where it slows down: the larger of
     * {@code taperStartFraction} and {@code slowDownIn} over the length.
     *
     * @param distanceIn the move's length, inches
     * @return the fraction to slow down over, 0 to 1
     */
    public double taperFraction(double distanceIn) {
        double byInches = distanceIn > 1e-6 && slowDownIn > 0 ? slowDownIn / distanceIn : 0;
        return Math.min(1, Math.max(taperStartFraction, byInches));
    }

    /** Encoder counts per inch of wheel travel, worked out from the values above. */
    public double ticksPerInch() {
        // One wheel turn rolls the robot one wheel circumference (pi x diameter) inches,
        // and takes (ticks per motor turn x motor turns per wheel turn) ticks.
        return ticksPerRev * gearRatio / (Math.PI * wheelDiameterIn);
    }
}
