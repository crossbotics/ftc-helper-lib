// The folder this file lives in. Change it to your TeamCode folder when you copy the file.
package com.crossbotics.ftchelper.examples;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.drive.MecanumDrive;
import com.crossbotics.ftchelper.drive.ReverseDrive;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.gamepad.ToggleRumble;
import com.crossbotics.ftchelper.hardware.BulkReads;
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.hardware.ImuSetup;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;
import com.crossbotics.ftchelper.util.LoopTimer;

/**
 * A full TeleOp: mecanum drive (tutorial 04) and a safe lift (tutorial 08).
 * Driver (gamepad1): left stick = move, right stick X = turn, LT = slow, Y = field-centric,
 * left stick click = reverse drive, BACK = reset heading (face away from you first).
 * Operator (gamepad2): left stick Y = lift by hand, A / X / Y = lift down / middle / up.
 * Needs front_left, front_right, back_left, back_right, lift, the hub's "imu"; optional "lift_bottom".
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/08-safe-lift/
 */
@TeleOp(name = "Example: Helper TeleOp", group = "Examples")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class ExampleTeleOp extends LinearOpMode {

    // Ticks: measure yours. LIFT_MAX = the software limit, a little below the real top.
    static final int LIFT_DOWN = 0, LIFT_MIDDLE = 1200, LIFT_UP = 2600, LIFT_MAX = 2900;

    private boolean fieldCentric = false;
    private Integer liftTarget = null;   // null = driver moves the lift by hand

    @Override
    public void runOpMode() {
        // ---------- INIT: set up hardware. Nothing moves. ----------
        BulkReads.auto(hardwareMap);

        // Names front_left ... back_right, left side reversed. Other names: driveConfig.frontLeft = "fl";
        DriveConfig driveConfig = DriveConfig.mecanum();
        driveConfig.powerLimit = 0.8;   // raise toward 1.0 once the drivers are comfortable
        MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);

        // Which way the hub's logo and USB ports face on YOUR robot.
        IMU imu = ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);

        // Turn the robot on with the lift DOWN: this spot is 0 ticks.
        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        // Software limits and a slow zone. The lift also needs a real, physical stop.
        SafeMotor lift = new SafeMotor(liftMotor)
                .setLimits(0, LIFT_MAX)
                .setSlowZone(200, 0.25);
        // Optional. Until it is pressed once, the lift only goes down, slowly.
        TouchSensor liftBottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
        if (liftBottom != null) lift.setLowerSwitch(liftBottom::isPressed);
        // kP, kI, kD: a starting point. Tune them on your robot (tutorial 07).
        PidController liftPid = new PidController(0.004, 0, 0.0002).setTolerance(20);

        // ---------- INIT: the buttons ----------
        ReverseDrive reverse = new ReverseDrive();
        ToggleRumble confirm = new ToggleRumble(gamepad1);   // two buzzes = ON, one = OFF
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

        // ---------- RUN: every loop. Nothing in here waits. ----------
        try {
            while (opModeIsActive()) {
                loopTimer.update();
                controls.update(gamepad1, gamepad2);
                lift.update();   // FIRST for the lift: before setPower or moveTo

                // Drive: deadband 0.05, then squared. Minus: stick up reads negative.
                double forward = reverse.apply(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2));
                double strafe = reverse.apply(StickShaping.shape(gamepad1.left_stick_x, 0.05, 2));
                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);   // not reversed
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);   // full pull = 30%
                if (fieldCentric) {
                    drive.driveFieldCentric(forward, strafe, turn, ImuSetup.headingDeg(imu), speed);
                } else {
                    drive.drive(forward, strafe, turn, speed);
                }

                // Lift: deadband 0.1, so a bumped stick does not cancel a button move.
                double liftStick = StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1);
                if (liftStick != 0) liftTarget = null;   // the stick takes over
                if (liftTarget == null) {
                    lift.setPower(liftStick);
                } else {
                    lift.moveTo(liftTarget, liftPid);
                }

                TelemetryHelper.section(telemetry, "Drive");
                telemetry.addData("Mode", "%s%s", fieldCentric ? "field-centric" : "robot-centric",
                        reverse.isReversed() ? ", REVERSED" : "");
                TelemetryHelper.value(telemetry, "Heading", ImuSetup.headingDeg(imu), "deg");
                TelemetryHelper.section(telemetry, "Lift");
                TelemetryHelper.motor(telemetry, "Lift", liftMotor);   // its position: measure LIFT_UP etc.
                telemetry.addData("Target", liftTarget == null ? "by hand" : liftTarget.toString());
                TelemetryHelper.flag(telemetry, "At bottom", lift.isAtLower());
                TelemetryHelper.value(telemetry, "Loop", loopTimer.getHz(), "Hz");
                telemetry.update();
            }
        } finally {
            // SAFETY: always stop every motor, even after an error.
            drive.stop();
            lift.stop();
        }
    }

    // Only sets the new target (the loop does the moving). Fresh PID memory for the new move.
    private void setLiftTarget(int ticks, PidController pid) {
        liftTarget = ticks;
        pid.reset();
    }
}
