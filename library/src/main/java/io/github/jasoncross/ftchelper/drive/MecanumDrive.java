package io.github.jasoncross.ftchelper.drive;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.util.MathUtil;

/**
 * Drives a four-wheel mecanum chassis. For TeleOp: call a drive method every loop.
 * For a standard (tank) drive, use {@link TankDrive}.
 *
 * <pre>{@code
 * MecanumDrive drive = new MecanumDrive(hardwareMap, new DriveConfig());
 * waitForStart();
 * while (opModeIsActive()) {
 *     drive.drive(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);
 * }
 * drive.stop();
 * }</pre>
 *
 * <p>Signs: forward +, strafe right +, turn clockwise +. Note that the SDK's stick Y is
 * negative when pushed up, so use {@code -gamepad1.left_stick_y} for forward.
 * All the sign rules: docs/conventions.md.
 *
 * <p>Each drive method only sets the wheel powers and returns at once (it never waits), so it is
 * safe to call every loop in TeleOp. The wheel math lives in {@link MecanumMixer}.
 *
 * Docs: docs/modules/drive.md. Tutorial 04 drives a mecanum robot with it, step by step.
 */
public class MecanumDrive {
    // "final": set once in the constructor, never replaced.
    private final DcMotorEx frontLeft, frontRight, backLeft, backRight;
    private final DriveConfig config;

    /**
     * Gets the four motors, sets their directions and brake mode, and sets power to 0.
     * Encoders are not reset and the run mode is RUN_WITHOUT_ENCODER (plain power).
     *
     * @throws IllegalArgumentException if the config is not MECANUM, or a motor name is not in the
     *                                  configuration (lists the real names)
     */
    public MecanumDrive(HardwareMap hardwareMap, DriveConfig config) {
        // Check the settings first, so a mistake shows up in INIT with a clear message.
        config.validate();
        if (config.type != DriveConfig.DriveType.MECANUM) {
            throw new IllegalArgumentException("MecanumDrive needs a MECANUM DriveConfig. For a standard drive use TankDrive.");
        }
        this.config = config;
        frontLeft = setUp(hardwareMap, config.frontLeft, config.frontLeftReversed);
        frontRight = setUp(hardwareMap, config.frontRight, config.frontRightReversed);
        backLeft = setUp(hardwareMap, config.backLeft, config.backLeftReversed);
        backRight = setUp(hardwareMap, config.backRight, config.backRightReversed);
    }

    // Gets one motor and prepares it for TeleOp driving. RUN_WITHOUT_ENCODER means the power
    // goes straight to the motor (the driver's stick is the "control", no speed correction).
    private DcMotorEx setUp(HardwareMap hardwareMap, String name, boolean reversed) {
        MotorSetup setup = MotorSetup.get(hardwareMap, name).reversed(reversed);
        if (config.brake) setup.brake(); else setup.coast();
        return setup.runWithoutEncoder().done();
    }

    /**
     * Robot-centric drive: "forward" is where the robot faces.
     *
     * @param forward       -1 to 1
     * @param strafeRight   -1 to 1
     * @param turnClockwise -1 to 1
     */
    public void drive(double forward, double strafeRight, double turnClockwise) {
        drive(forward, strafeRight, turnClockwise, 1.0);
    }

    /**
     * Robot-centric drive with a speed multiplier (for a slow mode).
     *
     * @param forward       -1 to 1
     * @param strafeRight   -1 to 1
     * @param turnClockwise -1 to 1
     * @param speedScale    0 to 1, multiplies the power limit. Example: 0.3 for slow, precise moves.
     */
    public void drive(double forward, double strafeRight, double turnClockwise, double speedScale) {
        // Slow mode lowers the limit, and the mixer scales all four wheels together to fit under it,
        // so the robot keeps the same direction, only slower.
        double limit = config.powerLimit * MathUtil.clamp(speedScale, 0, 1);
        setPowers(MecanumMixer.mix(forward, strafeRight, turnClockwise, limit));
    }

    /**
     * Field-centric drive: "forward" is away from the driver, whichever way the robot faces.
     * Needs the IMU heading. Reset the IMU yaw while the robot faces away from the driver.
     * See "Robot-centric or field-centric?" in docs/modules/drive.md, and Tutorial 04.
     *
     * @param fieldForward  -1 to 1, away from the driver
     * @param fieldRight    -1 to 1, to the driver's right
     * @param turnClockwise -1 to 1
     * @param headingCcwDeg IMU heading in degrees (counterclockwise positive), e.g. {@code ImuSetup.headingDeg(imu)}
     * @param speedScale    0 to 1
     */
    public void driveFieldCentric(double fieldForward, double fieldRight, double turnClockwise,
                                  double headingCcwDeg, double speedScale) {
        // Turn the stick direction (seen from the driver) into the direction as the robot sees it,
        // using which way the robot faces now. Turning is the same either way, so it is not changed.
        double[] robot = MecanumMixer.fieldToRobot(fieldForward, fieldRight, headingCcwDeg);
        drive(robot[0], robot[1], turnClockwise, speedScale);
    }

    /** Field-centric drive at full speed scale. */
    public void driveFieldCentric(double fieldForward, double fieldRight, double turnClockwise,
                                  double headingCcwDeg) {
        driveFieldCentric(fieldForward, fieldRight, turnClockwise, headingCcwDeg, 1.0);
    }

    /**
     * Sets all four wheel powers to 0. Call it at the end of your OpMode, best in a
     * {@code finally} block so it runs even after an error (Tutorial 04, "Always stop in finally").
     */
    public void stop() {
        // "new double[4]" is four zeros.
        setPowers(new double[4]);
    }

    /**
     * Sets the four wheel powers directly (front-left, front-right, back-left, back-right).
     * Used for testing one wheel at a time. The power limit still applies.
     */
    public void setPowers(double[] powers) {
        // Cut every power to the limit, so even a direct call can never go past it.
        double limit = config.powerLimit;
        // Each wheel on its own: if one motor throws an error, the others still get their power
        // (most important for stop()). The first error is thrown at the end, so it is not lost.
        DcMotorEx[] motors = {frontLeft, frontRight, backLeft, backRight};
        RuntimeException first = null;
        for (int i = 0; i < 4; i++) {
            try {
                motors[i].setPower(MathUtil.clamp(powers[i], -limit, limit));
            } catch (RuntimeException e) {
                if (first == null) first = e;
            }
        }
        if (first != null) throw first;
    }

    /** True = wheels hold still at power 0. False = wheels coast. */
    public void setBrake(boolean brake) {
        // "a ? b : c" = "if a then b, else c". FLOAT is the SDK's word for "coast".
        DcMotor.ZeroPowerBehavior behavior = brake ? DcMotor.ZeroPowerBehavior.BRAKE : DcMotor.ZeroPowerBehavior.FLOAT;
        for (DcMotorEx motor : getMotors()) motor.setZeroPowerBehavior(behavior);
    }

    /** The four motors, in the order front-left, front-right, back-left, back-right. */
    public DcMotorEx[] getMotors() {
        return new DcMotorEx[]{frontLeft, frontRight, backLeft, backRight};
    }

    /** The settings this drive was made with. */
    public DriveConfig getConfig() {
        return config;
    }
}
