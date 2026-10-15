package io.github.jasoncross.ftchelper.examples;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.TouchSensor;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.drive.ReverseDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.hardware.Hw;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.safety.SafeMotor;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;
import io.github.jasoncross.ftchelper.util.LoopTimer;

/**
 * A full TeleOp built with ftc-helper-lib. Copy it into TeamCode, change the names, and
 * remove the {@code @Disabled} line.
 *
 * Driver (gamepad1):  left stick = move, right stick X = turn, left trigger = slow,
 *                     Y = field-centric on/off, left stick click = reverse drive,
 *                     BACK = reset heading (face away from you first).
 * Operator (gamepad2): left stick Y = lift by hand, A = lift down, X = lift middle, Y = lift up.
 *                     With a "lift_bottom" switch, push the lift down once at the start to home it.
 *
 * Walk-through: docs/03-first-teleop.md
 */
@TeleOp(name = "Example: Helper TeleOp", group = "Examples")
@Disabled
public class ExampleTeleOp extends LinearOpMode {

    // Lift positions in encoder ticks. Measure these on your robot.
    static final int LIFT_DOWN = 0, LIFT_MIDDLE = 1200, LIFT_UP = 2600, LIFT_MAX = 2900;

    private boolean fieldCentric = false;
    private Integer liftTarget = null;   // null = driver moves the lift by hand

    @Override
    public void runOpMode() {
        // ---------- INIT: set up hardware ----------
        BulkReads.auto(hardwareMap);

        DriveConfig driveConfig = new DriveConfig();
        driveConfig.powerLimit = 0.8;
        MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);

        IMU imu = ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);

        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        SafeMotor lift = new SafeMotor(liftMotor)
                .setLimits(0, LIFT_MAX)
                .setSlowZone(200, 0.25);
        TouchSensor liftBottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
        if (liftBottom != null) lift.setLowerSwitch(liftBottom::isPressed);
        PidController liftPid = new PidController(0.004, 0, 0.0002).setTolerance(20);

        // ---------- INIT: say what each button does ----------
        ReverseDrive reverse = new ReverseDrive();
        ToggleRumble confirm = new ToggleRumble(gamepad1);
        ControllerBindings controls = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { fieldCentric = true; confirm.play(true); },
                        () -> { fieldCentric = false; confirm.play(false); })
                .bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()))
                .bindPress(Pad.G1, Btn.BACK, imu::resetYaw)
                .bindPress(Pad.G2, Btn.A, () -> setLiftTarget(LIFT_DOWN, liftPid))
                .bindPress(Pad.G2, Btn.X, () -> setLiftTarget(LIFT_MIDDLE, liftPid))
                .bindPress(Pad.G2, Btn.Y, () -> setLiftTarget(LIFT_UP, liftPid));

        LoopTimer loopTimer = new LoopTimer();
        telemetry.addLine("Ready. Press START.");
        telemetry.update();

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        // ---------- RUN: every loop ----------
        try {
            while (opModeIsActive()) {
                loopTimer.update();
                controls.update(gamepad1, gamepad2);
                lift.update();

                // Drive
                double forward = reverse.apply(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2));
                double strafe = reverse.apply(StickShaping.shape(gamepad1.left_stick_x, 0.05, 2));
                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
                if (fieldCentric) {
                    drive.driveFieldCentric(forward, strafe, turn, ImuSetup.headingDeg(imu), speed);
                } else {
                    drive.drive(forward, strafe, turn, speed);
                }

                // Lift: a stick push takes over from the buttons.
                double liftStick = StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1);
                if (liftStick != 0) liftTarget = null;
                if (liftTarget == null) {
                    lift.setPower(liftStick);
                } else {
                    lift.moveTo(liftTarget, liftPid);
                }

                // Telemetry
                TelemetryHelper.section(telemetry, "Drive");
                telemetry.addData("Mode", "%s%s", fieldCentric ? "field-centric" : "robot-centric",
                        reverse.isReversed() ? ", REVERSED" : "");
                TelemetryHelper.value(telemetry, "Heading", ImuSetup.headingDeg(imu), "deg");
                TelemetryHelper.section(telemetry, "Lift");
                TelemetryHelper.motor(telemetry, "Lift", liftMotor);
                telemetry.addData("Target", liftTarget == null ? "by hand" : liftTarget.toString());
                TelemetryHelper.flag(telemetry, "At bottom", lift.isAtLower());
                TelemetryHelper.value(telemetry, "Loop", loopTimer.getHz(), "Hz");
                telemetry.update();
            }
        } finally {
            drive.stop();
            lift.stop();
        }
    }

    private void setLiftTarget(int ticks, PidController pid) {
        liftTarget = ticks;
        pid.reset();
    }
}
