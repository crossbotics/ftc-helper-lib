// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.IMU;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.drive.MecanumDrive;
import com.crossbotics.ftchelper.drive.ReverseDrive;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.gamepad.ToggleRumble;
import com.crossbotics.ftchelper.hardware.ImuSetup;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 04: a complete mecanum drive. Left stick = move, right stick X = turn, LT = slow,
 * Y = field-centric on/off, BACK = reset heading, left stick click = reverse drive.
 * Needs front_left, front_right, back_left, back_right and the hub's "imu".
 * Check the motor directions first with MotorDirectionCheck.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/04-drive-mecanum/
 */
@TeleOp(name = "Tutorial 04: Mecanum Drive", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut04MecanumDrive extends LinearOpMode {

    // Y flips it. true = field-centric: "forward" is away from the driver.
    private boolean fieldCentric = false;

    @Override
    public void runOpMode() {
        // Names front_left ... back_right, left side reversed. Other names: driveConfig.frontLeft = "fl";
        DriveConfig driveConfig = DriveConfig.mecanum();
        driveConfig.powerLimit = 0.6;               // raise this after the robot drives correctly
        MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);

        // Which way the hub's logo and USB ports face on YOUR robot.
        IMU imu = ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);

        ReverseDrive reverse = new ReverseDrive();
        ToggleRumble confirm = new ToggleRumble(gamepad1);   // two buzzes = ON, one = OFF
        ControllerBindings controls = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { fieldCentric = true; confirm.play(true); },
                        () -> { fieldCentric = false; confirm.play(false); })
                // BACK: "where the robot faces now is forward". Point it away from you first.
                .bindPress(Pad.G1, Btn.BACK, imu::resetYaw)
                .bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()));

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);

                // Deadband 0.05, then squared. Minus: stick up reads negative.
                double forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
                double strafe = StickShaping.shape(gamepad1.left_stick_x, 0.05, 2);
                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);   // + = clockwise
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);   // full pull = 30%
                double heading = ImuSetup.headingDeg(imu);

                if (fieldCentric) {
                    // No reverse here: in field-centric, the robot's front does not matter.
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
