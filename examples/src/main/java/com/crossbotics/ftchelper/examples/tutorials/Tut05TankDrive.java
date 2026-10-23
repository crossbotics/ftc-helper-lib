// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.drive.TankDrive;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.gamepad.ToggleRumble;

/**
 * Tutorial 05: a standard (tank) drive with two motors, "left_drive" and "right_drive".
 * Arcade: left stick Y = forward, right stick X = turn. X = switch to tank style.
 * Left trigger = slow.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/05-drive-tank/
 */
@TeleOp(name = "Tutorial 05: Tank Drive", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut05TankDrive extends LinearOpMode {

    // X flips it. false = arcade, true = tank (each stick drives one side).
    private boolean tankStyle = false;

    @Override
    public void runOpMode() {
        // "left_drive" and "right_drive". Other names: driveConfig.frontLeft = "left";
        DriveConfig driveConfig = DriveConfig.tankTwoMotor();   // DriveConfig.tank() for 4 motors
        driveConfig.powerLimit = 0.6;
        TankDrive drive = new TankDrive(hardwareMap, driveConfig);

        ToggleRumble confirm = new ToggleRumble(gamepad1);   // two buzzes = tank, one = arcade
        ControllerBindings controls = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.X,
                        () -> { tankStyle = true; confirm.play(true); },
                        () -> { tankStyle = false; confirm.play(false); });

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);   // full pull = 30%

                // Deadband 0.05, then squared. Minus: stick up reads negative.
                if (tankStyle) {
                    drive.tank(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
                            StickShaping.shape(-gamepad1.right_stick_y, 0.05, 2), speed);
                } else {
                    drive.arcade(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
                            StickShaping.shape(gamepad1.right_stick_x, 0.05, 2), speed);
                }

                telemetry.addData("Style", tankStyle ? "tank (X to change)" : "arcade (X to change)");
                telemetry.update();
            }
        } finally {
            drive.stop();
        }
    }
}
