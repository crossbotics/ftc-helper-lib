package org.octobots.ftchelper.drive;

/**
 * The names and directions of the four mecanum drive motors, and a power limit.
 *
 * <p>Make one, change what is different on your robot, and give it to {@link MecanumDrive}.
 *
 * <pre>{@code
 * DriveConfig cfg = new DriveConfig();
 * cfg.frontLeft = "fl";            // your configuration names
 * cfg.powerLimit = 0.8;            // never more than 80% power
 * }</pre>
 *
 * <p>Default directions: the left motors are reversed. This is right for most goBILDA
 * and REV mecanum chassis, but CHECK IT: see "Check motor directions" in docs/modules/drive.md.
 */
public class DriveConfig {
    /** Configuration name of the front-left motor. */
    public String frontLeft = "front_left";
    /** Configuration name of the front-right motor. */
    public String frontRight = "front_right";
    /** Configuration name of the back-left motor. */
    public String backLeft = "back_left";
    /** Configuration name of the back-right motor. */
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
}
