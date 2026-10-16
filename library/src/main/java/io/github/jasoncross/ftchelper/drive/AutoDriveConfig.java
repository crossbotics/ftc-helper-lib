package io.github.jasoncross.ftchelper.drive;

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

    /** Encoder counts per inch of wheel travel, worked out from the values above. */
    public double ticksPerInch() {
        // One wheel turn rolls the robot one wheel circumference (pi x diameter) inches,
        // and takes (ticks per motor turn x motor turns per wheel turn) ticks.
        return ticksPerRev * gearRatio / (Math.PI * wheelDiameterIn);
    }
}
