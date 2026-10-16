package io.github.jasoncross.ftchelper.drive;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.util.MathUtil;

/**
 * Drives a standard (tank / differential) chassis with 2 or 4 motors. For TeleOp: call a drive
 * method every loop.
 *
 * <pre>{@code
 * TankDrive drive = new TankDrive(hardwareMap, DriveConfig.tankTwoMotor());
 * waitForStart();
 * while (opModeIsActive()) {
 *     drive.arcade(-gamepad1.left_stick_y, gamepad1.right_stick_x);    // or:
 *     // drive.tank(-gamepad1.left_stick_y, -gamepad1.right_stick_y);
 * }
 * drive.stop();
 * }</pre>
 *
 * <p>Signs: forward +, turn clockwise +. A standard drive cannot move sideways.
 * All the sign rules: docs/conventions.md.
 *
 * <p>Each drive method only sets the wheel powers and returns at once (it never waits). The math
 * lives in {@link DifferentialMixer}. On a 4-motor drive, both motors on a side always get the same power.
 *
 * Docs: docs/modules/drive.md. Tutorial 05 drives a standard robot with it, step by step.
 */
public class TankDrive {
    private final DriveConfig config;
    private final DcMotorEx frontLeft, frontRight, backLeft, backRight;   // back ones may be null

    /**
     * Gets the motors, sets their directions and brake mode, and sets power to 0.
     * Plain power (RUN_WITHOUT_ENCODER). Encoders are not reset.
     *
     * @param config must have {@code type = TANK} (use {@link DriveConfig#tank()} or {@link DriveConfig#tankTwoMotor()})
     * @throws IllegalArgumentException if the config is not TANK, or a motor name is not in the configuration
     */
    public TankDrive(HardwareMap hardwareMap, DriveConfig config) {
        // "this(...)" hands everything to the constructor below. The config is checked first,
        // before any motor is touched. "a ? b : null" = "if a then b, else no motor" (2-motor drive).
        this(check(config),
                setUp(hardwareMap, config, config.frontLeft, config.frontLeftReversed),
                setUp(hardwareMap, config, config.frontRight, config.frontRightReversed),
                config.hasBackMotors() ? setUp(hardwareMap, config, config.backLeft, config.backLeftReversed) : null,
                config.hasBackMotors() ? setUp(hardwareMap, config, config.backRight, config.backRightReversed) : null);
    }

    /** For tests: motors already set up. Back motors may be null. */
    TankDrive(DriveConfig config, DcMotorEx frontLeft, DcMotorEx frontRight, DcMotorEx backLeft, DcMotorEx backRight) {
        this.config = check(config);
        this.frontLeft = frontLeft;
        this.frontRight = frontRight;
        this.backLeft = backLeft;
        this.backRight = backRight;
    }

    // "static": belongs to the class, so the constructor can call it before the TankDrive exists.
    // Refuses a config that is not for a tank drive, with a message that says what to use instead.
    private static DriveConfig check(DriveConfig config) {
        config.validate();
        if (config.type != DriveConfig.DriveType.TANK) {
            throw new IllegalArgumentException("TankDrive needs a TANK DriveConfig. For mecanum wheels use MecanumDrive.");
        }
        return config;
    }

    // Gets one motor and prepares it: direction, brake or coast, and plain power (no encoder control).
    private static DcMotorEx setUp(HardwareMap hardwareMap, DriveConfig config, String name, boolean reversed) {
        MotorSetup setup = MotorSetup.get(hardwareMap, name).reversed(reversed);
        if (config.brake) setup.brake(); else setup.coast();
        return setup.runWithoutEncoder().done();
    }

    /**
     * Arcade drive: one value for forward, one for turning.
     *
     * @param forward       -1 to 1, e.g. {@code -gamepad1.left_stick_y}
     * @param turnClockwise -1 to 1, e.g. {@code gamepad1.right_stick_x}
     */
    public void arcade(double forward, double turnClockwise) {
        arcade(forward, turnClockwise, 1.0);
    }

    /** Arcade drive with a speed multiplier 0 to 1 (for a slow mode). */
    public void arcade(double forward, double turnClockwise, double speedScale) {
        // Slow mode lowers the power limit; the mixer scales both sides together to fit under it.
        setPowers(DifferentialMixer.arcade(forward, turnClockwise, config.powerLimit * MathUtil.clamp(speedScale, 0, 1)));
    }

    /**
     * Tank drive: one value for each side.
     *
     * @param left  -1 to 1, e.g. {@code -gamepad1.left_stick_y}
     * @param right -1 to 1, e.g. {@code -gamepad1.right_stick_y}
     */
    public void tank(double left, double right) {
        tank(left, right, 1.0);
    }

    /** Tank drive with a speed multiplier 0 to 1. */
    public void tank(double left, double right, double speedScale) {
        setPowers(DifferentialMixer.tank(left, right, config.powerLimit * MathUtil.clamp(speedScale, 0, 1)));
    }

    /**
     * Sets both sides to 0. Call it at the end of your OpMode, best in a {@code finally} block
     * so it runs even after an error.
     */
    public void stop() {
        // "new double[2]" is two zeros.
        setPowers(new double[2]);
    }

    /** Sets the left and right powers directly ({left, right}). The power limit still applies. */
    public void setPowers(double[] leftRight) {
        // Cut both powers to the limit, so even a direct call can never go past it.
        double limit = config.powerLimit;
        double left = MathUtil.clamp(leftRight[0], -limit, limit);
        double right = MathUtil.clamp(leftRight[1], -limit, limit);
        frontLeft.setPower(left);
        frontRight.setPower(right);
        // A 2-motor drive has no back motors (null), so skip them.
        if (backLeft != null) backLeft.setPower(left);
        if (backRight != null) backRight.setPower(right);
    }

    /** True = hold at 0 power, false = coast. */
    public void setBrake(boolean brake) {
        // "a ? b : c" = "if a then b, else c". FLOAT is the SDK's word for "coast".
        DcMotor.ZeroPowerBehavior behavior = brake ? DcMotor.ZeroPowerBehavior.BRAKE : DcMotor.ZeroPowerBehavior.FLOAT;
        for (DcMotorEx motor : getMotors()) motor.setZeroPowerBehavior(behavior);
    }

    /** The motors: {left, right} for 2 motors, {front-left, front-right, back-left, back-right} for 4. */
    public DcMotorEx[] getMotors() {
        return backLeft == null
                ? new DcMotorEx[]{frontLeft, frontRight}
                : new DcMotorEx[]{frontLeft, frontRight, backLeft, backRight};
    }

    /** The settings this drive was made with. */
    public DriveConfig getConfig() {
        return config;
    }
}
