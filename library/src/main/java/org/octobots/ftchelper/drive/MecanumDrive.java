package org.octobots.ftchelper.drive;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import org.octobots.ftchelper.hardware.MotorSetup;
import org.octobots.ftchelper.util.MathUtil;

/**
 * Drives a four-wheel mecanum chassis. For TeleOp: call a drive method every loop.
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
 *
 * Docs: docs/modules/drive.md
 */
public class MecanumDrive {
    private final DcMotorEx frontLeft, frontRight, backLeft, backRight;
    private final DriveConfig config;

    /**
     * Gets the four motors, sets their directions and brake mode, and sets power to 0.
     * Encoders are not reset and the run mode is RUN_WITHOUT_ENCODER (plain power).
     *
     * @throws IllegalArgumentException if a motor name is not in the configuration (lists the real names)
     */
    public MecanumDrive(HardwareMap hardwareMap, DriveConfig config) {
        this.config = config;
        frontLeft = setUp(hardwareMap, config.frontLeft, config.frontLeftReversed);
        frontRight = setUp(hardwareMap, config.frontRight, config.frontRightReversed);
        backLeft = setUp(hardwareMap, config.backLeft, config.backLeftReversed);
        backRight = setUp(hardwareMap, config.backRight, config.backRightReversed);
    }

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
     * @param speedScale 0 to 1, multiplies the power limit. Example: 0.3 for slow, precise moves.
     */
    public void drive(double forward, double strafeRight, double turnClockwise, double speedScale) {
        double limit = config.powerLimit * MathUtil.clamp(speedScale, 0, 1);
        setPowers(MecanumMixer.mix(forward, strafeRight, turnClockwise, limit));
    }

    /**
     * Field-centric drive: "forward" is away from the driver, whichever way the robot faces.
     * Needs the IMU heading. Reset the IMU yaw while the robot faces away from the driver.
     *
     * @param fieldForward  -1 to 1, away from the driver
     * @param fieldRight    -1 to 1, to the driver's right
     * @param turnClockwise -1 to 1
     * @param headingCcwDeg IMU heading in degrees (counterclockwise positive), e.g. {@code ImuSetup.headingDeg(imu)}
     * @param speedScale    0 to 1
     */
    public void driveFieldCentric(double fieldForward, double fieldRight, double turnClockwise,
                                  double headingCcwDeg, double speedScale) {
        double[] robot = MecanumMixer.fieldToRobot(fieldForward, fieldRight, headingCcwDeg);
        drive(robot[0], robot[1], turnClockwise, speedScale);
    }

    /** Field-centric drive at full speed scale. */
    public void driveFieldCentric(double fieldForward, double fieldRight, double turnClockwise,
                                  double headingCcwDeg) {
        driveFieldCentric(fieldForward, fieldRight, turnClockwise, headingCcwDeg, 1.0);
    }

    /** Sets all four wheel powers to 0. */
    public void stop() {
        setPowers(new double[4]);
    }

    /**
     * Sets the four wheel powers directly (front-left, front-right, back-left, back-right).
     * Used for testing one wheel at a time. The power limit still applies.
     */
    public void setPowers(double[] powers) {
        double limit = config.powerLimit;
        frontLeft.setPower(MathUtil.clamp(powers[0], -limit, limit));
        frontRight.setPower(MathUtil.clamp(powers[1], -limit, limit));
        backLeft.setPower(MathUtil.clamp(powers[2], -limit, limit));
        backRight.setPower(MathUtil.clamp(powers[3], -limit, limit));
    }

    /** True = wheels hold still at power 0. False = wheels coast. */
    public void setBrake(boolean brake) {
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
