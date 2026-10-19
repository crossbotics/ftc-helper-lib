package io.github.jasoncross.ftchelper.drive;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;

/**
 * Standard TeleOp driving from one gamepad, for mecanum AND standard (tank) robots, in one line per
 * loop. {@code EasyTeleOp} uses it for you; you can also use it in your own OpMode.
 *
 * <pre>
 *   left stick    drive (mecanum: any direction; tank: forward/back)
 *   right stick X turn
 *   left trigger  slow: squeeze for fine moves (down to {@link #slowSpeed} at full squeeze)
 * </pre>
 *
 * Mecanum robots can also drive field-centric ("stick up = away from the driver"), using a
 * {@link DriverHeading}. Robot-centric ("stick up = where the robot points") is the default.
 *
 * <pre>{@code
 * DriverControls driving = DriverControls.create(hardwareMap, new RobotSettings().drive(), heading);
 * // every loop:
 * driving.update(gamepad1);
 * // at the end (in finally):
 * driving.stop();
 * }</pre>
 *
 * Docs: docs/modules/drive.md (section "DriverControls")
 */
public final class DriverControls {

    /** Where the drive commands go. The real drive classes in normal use, a pretend one in tests. */
    interface Wheels {
        void robotCentric(double forward, double strafeRight, double turnClockwise, double speed);
        void fieldCentric(double forward, double right, double turnClockwise, double headingDeg, double speed);
        void tank(double left, double right, double speed);
        boolean canStrafe();
        void stop();
    }

    private final Wheels wheels;
    private final DriverHeading heading;   // null = no IMU: field-centric is not possible
    private double slowSpeed = 0.3;
    private double deadband = 0.05;
    private double exponent = 2;
    private boolean fieldCentric = false;
    // The last loop's values, for telemetry.
    private double lastSpeed = 1;

    /**
     * Makes the drive from your settings: a {@link MecanumDrive} or a {@link TankDrive}, picked by
     * {@code config.type}.
     *
     * @param hardwareMap the OpMode's hardwareMap
     * @param config      your drive settings (e.g. {@code robotConfig.drive()})
     * @param heading     for field-centric driving; null if the robot has no IMU (robot-centric only)
     */
    public static DriverControls create(HardwareMap hardwareMap, DriveConfig config, DriverHeading heading) {
        if (config.type == DriveConfig.DriveType.TANK) {
            final TankDrive tank = new TankDrive(hardwareMap, config);
            return new DriverControls(new Wheels() {
                // A standard drive cannot go sideways, so the strafe value is not used.
                @Override public void robotCentric(double f, double s, double t, double speed) { tank.arcade(f, t, speed); }
                @Override public void fieldCentric(double f, double r, double t, double h, double speed) { tank.arcade(f, t, speed); }
                @Override public void tank(double left, double right, double speed) { tank.tank(left, right, speed); }
                @Override public boolean canStrafe() { return false; }
                @Override public void stop() { tank.stop(); }
            }, heading);
        }
        final MecanumDrive mecanum = new MecanumDrive(hardwareMap, config);
        return new DriverControls(new Wheels() {
            @Override public void robotCentric(double f, double s, double t, double speed) { mecanum.drive(f, s, t, speed); }
            @Override public void fieldCentric(double f, double r, double t, double h, double speed) {
                mecanum.driveFieldCentric(f, r, t, h, speed);
            }
            // Two-stick tank style on mecanum wheels: the average is forward, the difference is the turn.
            @Override public void tank(double left, double right, double speed) {
                mecanum.drive((left + right) / 2, 0, (left - right) / 2, speed);
            }
            @Override public boolean canStrafe() { return true; }
            @Override public void stop() { mecanum.stop(); }
        }, heading);
    }

    /** For tests: pretend wheels. */
    DriverControls(Wheels wheels, DriverHeading heading) {
        this.wheels = wheels;
        this.heading = heading;
    }

    /** Speed at a full squeeze of the left trigger, 0 to 1. Default 0.3 (30%). */
    public DriverControls slowSpeed(double speed) {
        slowSpeed = Math.max(0, Math.min(1, speed));
        return this;
    }

    /**
     * How the sticks feel. Deadband: stick values smaller than this count as 0, so a resting stick
     * does not creep (default 0.05). Exponent: 2 (default) gives fine control near the middle,
     * 1 = straight line. See {@code StickShaping}.
     */
    public DriverControls sticks(double deadband, double exponent) {
        this.deadband = deadband;
        this.exponent = exponent;
        return this;
    }

    /** Drives from the gamepad's sticks. Call once every loop. Never waits. */
    public void update(Gamepad gamepad) {
        // The left trigger slows it down: the further it is pulled, the slower.
        driveSticks(gamepad, StickShaping.slowScale(gamepad.left_trigger, slowSpeed));
    }

    /**
     * Drives from the gamepad's sticks, with slow mode switched by your own code instead of the left
     * trigger (for example a one-driver layout where the trigger has another job). Call every loop.
     *
     * @param gamepad the driver's gamepad
     * @param slow    true = drive at the slow speed
     */
    public void update(Gamepad gamepad, boolean slow) {
        driveSticks(gamepad, slow ? slowSpeed : 1);
    }

    private void driveSticks(Gamepad gamepad, double speed) {
        // Minus: the SDK reads stick UP as negative (docs/conventions.md).
        double forward = StickShaping.shape(-gamepad.left_stick_y, deadband, exponent);
        double strafe = StickShaping.shape(gamepad.left_stick_x, deadband, exponent);
        double turn = StickShaping.shape(gamepad.right_stick_x, deadband, exponent);
        lastSpeed = speed;
        if (isFieldCentric()) wheels.fieldCentric(forward, strafe, turn, heading.degrees(), lastSpeed);
        else wheels.robotCentric(forward, strafe, turn, lastSpeed);
    }

    /**
     * Drives with numbers you worked out yourself (robot-centric), for your own controls.
     * Each value -1 to 1; a tank robot ignores strafeRight.
     */
    public void drive(double forward, double strafeRight, double turnClockwise, double speed) {
        lastSpeed = speed;
        wheels.robotCentric(forward, strafeRight, turnClockwise, speed);
    }

    /**
     * Two-stick "tank style": one value per side, -1 to 1. Works on tank AND mecanum robots.
     * Example: {@code driving.tank(-gamepad1.left_stick_y, -gamepad1.right_stick_y, 1)}.
     */
    public void tank(double left, double right, double speed) {
        lastSpeed = speed;
        wheels.tank(left, right, speed);
    }

    /** Holds the wheels still this loop (e.g. while a menu uses the gamepad). */
    public void hold() {
        wheels.robotCentric(0, 0, 0, 1);
    }

    /** All wheels to 0. Call at the end of the OpMode (in {@code finally}). */
    public void stop() {
        wheels.stop();
    }

    /** Field-centric on or off. Ignored (stays off) on a tank robot or without a heading. */
    public void setFieldCentric(boolean on) {
        fieldCentric = on && canFieldCentric();
    }

    /** Switches field-centric on/off; returns the new state. */
    public boolean toggleFieldCentric() {
        setFieldCentric(!fieldCentric);
        return fieldCentric;
    }

    /** True while driving field-centric. */
    public boolean isFieldCentric() {
        return fieldCentric;
    }

    /** True if this robot can drive field-centric: mecanum wheels and a heading. */
    public boolean canFieldCentric() {
        return wheels.canStrafe() && heading != null;
    }

    /** True while the slow trigger is pulled. */
    public boolean isSlow() {
        return lastSpeed < 0.99;
    }

    /** One short line for telemetry, e.g. "field  SLOW". */
    public String status() {
        return (fieldCentric ? "field" : "robot") + (isSlow() ? "  SLOW" : "");
    }
}
