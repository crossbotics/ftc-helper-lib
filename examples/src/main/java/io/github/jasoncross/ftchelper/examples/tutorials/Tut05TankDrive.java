// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (io.github.jasoncross...).
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
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Tutorial 05: Tank Drive", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom (see tutorial 01).
public class Tut05TankDrive extends LinearOpMode {

    // A "field" the X button flips. false = arcade style (one stick drives, the other turns).
    // true = tank style (each stick drives one side).
    private boolean tankStyle = false;

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set up. Nothing moves until START. ----

        // Two motors named "left_drive" and "right_drive" (the same names as the FTC SDK samples).
        // If yours differ, set them here, e.g. driveConfig.frontLeft = "left";
        DriveConfig driveConfig = DriveConfig.tankTwoMotor();   // DriveConfig.tank() for 4 motors
        // At most 60% power while you test: slower is safer if a direction is wrong.
        // Raise it once the robot drives correctly.
        driveConfig.powerLimit = 0.6;
        // Finds the motors and sets their directions. Stops with a clear message if a name is wrong.
        TankDrive drive = new TankDrive(hardwareMap, driveConfig);

        // Buzzes gamepad1 to confirm the switch: two buzzes = tank ON, one buzz = OFF (arcade).
        ToggleRumble confirm = new ToggleRumble(gamepad1);
        // X: tank style on/off. "() -> { ... }" is a lambda: code run LATER, when X is pressed.
        ControllerBindings controls = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.X,
                        () -> { tankStyle = true; confirm.play(true); },
                        () -> { tankStyle = false; confirm.play(false); });

        // Wait here until START is pressed.
        waitForStart();
        // A button held down while START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // try/finally: the "finally" part runs however the program ends, so the wheels always stop.
        try {
            // The main loop: runs many times a second until STOP. Nothing in it waits.
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                // Left trigger = slow: not pressed = full speed (1.0), squeezed all the way = 30%.
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);

                // shape(stick, 0.05, 2): ignore tiny values under 0.05 so a resting stick does not make
                // the robot creep, then "square" the rest for fine control at low speed.
                // The minus signs: stick Y reads NEGATIVE when pushed up, and up = forward.
                if (tankStyle) {
                    // Tank: left stick Y drives the left side, right stick Y drives the right side.
                    drive.tank(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
                            StickShaping.shape(-gamepad1.right_stick_y, 0.05, 2), speed);
                } else {
                    // Arcade: left stick Y = forward/back, right stick X = turn (right = clockwise).
                    drive.arcade(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
                            StickShaping.shape(gamepad1.right_stick_x, 0.05, 2), speed);
                }

                // "a ? b : c" = if a is true use b, otherwise c.
                telemetry.addData("Style", tankStyle ? "tank (X to change)" : "arcade (X to change)");
                telemetry.update();
            }
        } finally {
            // SAFETY: stop the wheels when the program ends, whatever the reason.
            drive.stop();
        }
    }
}
