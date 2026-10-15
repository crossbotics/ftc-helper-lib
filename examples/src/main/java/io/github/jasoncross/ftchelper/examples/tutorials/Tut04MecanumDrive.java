package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.drive.ReverseDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 04: a complete mecanum drive.
 * Left stick = move, right stick X = turn, left trigger = slow,
 * Y = field-centric on/off, BACK = "away from me is forward" (reset heading),
 * left stick click = reverse drive.
 *
 * Tutorial: docs/tutorials/04-drive-mecanum.md
 */
@TeleOp(name = "Tutorial 04: Mecanum Drive", group = "Tutorials")
@Disabled
public class Tut04MecanumDrive extends LinearOpMode {

    private boolean fieldCentric = false;

    @Override
    public void runOpMode() {
        DriveConfig driveConfig = DriveConfig.mecanum();
        driveConfig.powerLimit = 0.6;               // raise this after the robot drives correctly
        MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);

        IMU imu = ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);

        ReverseDrive reverse = new ReverseDrive();
        ToggleRumble confirm = new ToggleRumble(gamepad1);
        ControllerBindings controls = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { fieldCentric = true; confirm.play(true); },
                        () -> { fieldCentric = false; confirm.play(false); })
                .bindPress(Pad.G1, Btn.BACK, imu::resetYaw)
                .bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()));

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);

                double forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
                double strafe = StickShaping.shape(gamepad1.left_stick_x, 0.05, 2);
                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
                double heading = ImuSetup.headingDeg(imu);

                if (fieldCentric) {
                    drive.driveFieldCentric(forward, strafe, turn, heading, speed);
                } else {
                    drive.drive(reverse.apply(forward), reverse.apply(strafe), turn, speed);
                }

                TelemetryHelper.section(telemetry, "Drive");
                telemetry.addData("Mode", fieldCentric ? "field-centric" : "robot-centric");
                TelemetryHelper.flag(telemetry, "Reversed", reverse.isReversed());
                TelemetryHelper.value(telemetry, "Heading", heading, "deg");
                TelemetryHelper.value(telemetry, "Speed", speed, 2);
                telemetry.update();
            }
        } finally {
            drive.stop();
        }
    }
}
