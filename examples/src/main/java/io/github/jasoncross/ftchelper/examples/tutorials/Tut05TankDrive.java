package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.drive.TankDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;

/**
 * Tutorial 05: a standard (tank) drive with two motors, "left_drive" and "right_drive".
 * Arcade: left stick Y = forward, right stick X = turn. X = switch to tank style.
 * Left trigger = slow.
 *
 * Tutorial: docs/tutorials/05-drive-tank.md
 */
@TeleOp(name = "Tutorial 05: Tank Drive", group = "Tutorials")
@Disabled
public class Tut05TankDrive extends LinearOpMode {

    private boolean tankStyle = false;

    @Override
    public void runOpMode() {
        DriveConfig driveConfig = DriveConfig.tankTwoMotor();   // DriveConfig.tank() for 4 motors
        driveConfig.powerLimit = 0.6;
        TankDrive drive = new TankDrive(hardwareMap, driveConfig);

        ToggleRumble confirm = new ToggleRumble(gamepad1);
        ControllerBindings controls = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.X,
                        () -> { tankStyle = true; confirm.play(true); },
                        () -> { tankStyle = false; confirm.play(false); });

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);

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
