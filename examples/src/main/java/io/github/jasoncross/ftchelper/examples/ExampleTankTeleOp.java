package io.github.jasoncross.ftchelper.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.drive.ReverseDrive;
import io.github.jasoncross.ftchelper.drive.TankDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;
import io.github.jasoncross.ftchelper.hardware.BulkReads;

/**
 * TeleOp for a standard (tank) drive with two motors, "left_drive" and "right_drive".
 * Copy it into TeamCode and remove the {@code @Disabled} line.
 *
 * Driver (gamepad1): left stick Y = forward/back, right stick X = turn (arcade),
 *                    X = switch to tank style (left stick = left side, right stick = right side),
 *                    left trigger = slow, left stick click = reverse drive.
 *
 * Four motors? Use DriveConfig.tank() instead of tankTwoMotor().
 */
@TeleOp(name = "Example: Helper Tank TeleOp", group = "Examples")
@Disabled
public class ExampleTankTeleOp extends LinearOpMode {

    private boolean tankStyle = false;

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        DriveConfig driveConfig = DriveConfig.tankTwoMotor();
        driveConfig.powerLimit = 0.8;
        TankDrive drive = new TankDrive(hardwareMap, driveConfig);

        ReverseDrive reverse = new ReverseDrive();
        ToggleRumble confirm = new ToggleRumble(gamepad1);
        ControllerBindings controls = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.X,
                        () -> { tankStyle = true; confirm.play(true); },
                        () -> { tankStyle = false; confirm.play(false); })
                .bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()));

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
                if (tankStyle) {
                    double left = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
                    double right = StickShaping.shape(-gamepad1.right_stick_y, 0.05, 2);
                    // Reversed tank: swap and flip the sides, so the back acts as the front.
                    if (reverse.isReversed()) drive.tank(-right, -left, speed);
                    else drive.tank(left, right, speed);
                } else {
                    double forward = reverse.apply(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2));
                    double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                    drive.arcade(forward, turn, speed);
                }
                telemetry.addData("Style", tankStyle ? "tank" : "arcade");
                telemetry.addData("Reverse", reverse.isReversed() ? "ON" : "OFF");
                telemetry.update();
            }
        } finally {
            drive.stop();
        }
    }
}
