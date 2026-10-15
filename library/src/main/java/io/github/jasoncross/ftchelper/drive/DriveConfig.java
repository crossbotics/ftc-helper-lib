package io.github.jasoncross.ftchelper.drive;

/**
 * The drivetrain type, the motor names and directions, and a power limit.
 *
 * <p>Start from the factory method for your drivetrain, change what is different on your robot,
 * and give it to {@link MecanumDrive} or {@link TankDrive} (and to {@link AutoDrive}).
 *
 * <pre>{@code
 * DriveConfig cfg = DriveConfig.mecanum();     // or DriveConfig.tank() / DriveConfig.tankTwoMotor()
 * cfg.frontLeft = "fl";                        // your configuration names
 * cfg.powerLimit = 0.8;                        // never more than 80% power
 * }</pre>
 *
 * <p>Default directions: the left motors are reversed. This is right for most goBILDA
 * and REV chassis, but CHECK IT: see "Check motor directions" in docs/modules/drive.md.
 */
public class DriveConfig {

    /** The kind of drivetrain. */
    public enum DriveType {
        /** Four mecanum wheels: can drive forward, sideways and turn. */
        MECANUM,
        /**
         * "Standard" drive (also called tank or differential drive): normal wheels, a left side and
         * a right side. Can drive forward and turn, not sideways. 2 or 4 motors.
         */
        TANK
    }

    /** The kind of drivetrain. Default MECANUM. */
    public DriveType type = DriveType.MECANUM;

    /** Configuration name of the front-left motor (tank: the left motor, or the front one of two). */
    public String frontLeft = "front_left";
    /** Configuration name of the front-right motor (tank: the right motor, or the front one of two). */
    public String frontRight = "front_right";
    /** Configuration name of the back-left motor. Tank with 2 motors: {@code null}. */
    public String backLeft = "back_left";
    /** Configuration name of the back-right motor. Tank with 2 motors: {@code null}. */
    public String backRight = "back_right";

    /** True = this motor is reversed. */
    public boolean frontLeftReversed = true;
    /** True = this motor is reversed. */
    public boolean frontRightReversed = false;
    /** True = this motor is reversed. */
    public boolean backLeftReversed = true;
    /** True = this motor is reversed. */
    public boolean backRightReversed = false;

    /** The largest power any wheel gets, 0 to 1. Start low (0.4) on a new robot. */
    public double powerLimit = 1.0;

    /** True = wheels hold still at power 0 (recommended). False = wheels coast. */
    public boolean brake = true;

    /** Settings for a four-motor mecanum drive (the same as {@code new DriveConfig()}). */
    public static DriveConfig mecanum() {
        return new DriveConfig();
    }

    /** Settings for a four-motor standard (tank) drive: front_left, front_right, back_left, back_right. */
    public static DriveConfig tank() {
        DriveConfig c = new DriveConfig();
        c.type = DriveType.TANK;
        return c;
    }

    /**
     * Settings for a two-motor standard (tank) drive. The names are "left_drive" and "right_drive",
     * the same as the FTC SDK samples.
     */
    public static DriveConfig tankTwoMotor() {
        DriveConfig c = tank();
        c.frontLeft = "left_drive";
        c.frontRight = "right_drive";
        c.backLeft = null;
        c.backRight = null;
        return c;
    }

    /** True if there are back motors (always for mecanum; for tank, when both back names are set). */
    public boolean hasBackMotors() {
        return backLeft != null && backRight != null;
    }

    /**
     * Checks the settings make sense. Called by the drive classes.
     *
     * @throws IllegalArgumentException with a plain message if something is missing
     */
    public void validate() {
        if (frontLeft == null || frontRight == null) {
            throw new IllegalArgumentException("DriveConfig: frontLeft and frontRight motor names must be set");
        }
        if (type == DriveType.MECANUM && !hasBackMotors()) {
            throw new IllegalArgumentException("DriveConfig: a mecanum drive needs all four motor names");
        }
        if ((backLeft == null) != (backRight == null)) {
            throw new IllegalArgumentException("DriveConfig: set both backLeft and backRight, or neither (2-motor tank)");
        }
    }
}
