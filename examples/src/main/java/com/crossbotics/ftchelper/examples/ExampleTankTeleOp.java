// The folder this file lives in. Change it to your TeamCode folder when you copy the file.
package com.crossbotics.ftchelper.examples;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.drive.ReverseDrive;
import com.crossbotics.ftchelper.drive.TankDrive;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.gamepad.ToggleRumble;
import com.crossbotics.ftchelper.hardware.BulkReads;

/**
 * TeleOp for a standard (tank) drive with two motors, "left_drive" and "right_drive".
 * Driver (gamepad1): left stick Y = forward/back, right stick X = turn (arcade), X = tank style,
 * left trigger = slow, left stick click = reverse drive. Four motors? Use DriveConfig.tank().
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/05-drive-tank/
 */
@TeleOp(name = "Example: Helper Tank TeleOp", group = "Examples")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class ExampleTankTeleOp extends LinearOpMode {

    private boolean tankStyle = false;   // X flips it. false = arcade

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        // Other names: driveConfig.frontLeft = "left";
        DriveConfig driveConfig = DriveConfig.tankTwoMotor();
        driveConfig.powerLimit = 0.8;   // easier to control while drivers learn
        TankDrive drive = new TankDrive(hardwareMap, driveConfig);

        ReverseDrive reverse = new ReverseDrive();
        ToggleRumble confirm = new ToggleRumble(gamepad1);   // two buzzes = ON, one = OFF
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
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);   // full pull = 30%
                // Deadband 0.05, then squared. Minus: stick up reads negative.
                if (tankStyle) {
                    double left = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
                    double right = StickShaping.shape(-gamepad1.right_stick_y, 0.05, 2);
                    // Reversed tank: swap and flip the sides.
                    if (reverse.isReversed()) drive.tank(-right, -left, speed);
                    else drive.tank(left, right, speed);
                } else {
                    // Reverse flips only "forward": turning right still turns right.
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
