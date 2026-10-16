// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (io.github.jasoncross...).
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
 *
 * Tutorial: docs/tutorials/05-drive-tank.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Example: Helper Tank TeleOp", group = "Examples")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom. The SDK gives us
// hardwareMap (the robot's parts), gamepad1, gamepad2 and telemetry (the screen).
public class ExampleTankTeleOp extends LinearOpMode {

    // A "field": a true/false switch the whole program can see. The X button flips it.
    // false = arcade style (one stick drives, the other turns), the usual way to start.
    private boolean tankStyle = false;

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: find and set up the hardware. Nothing moves yet. ----

        // Read all sensors from the hub in one message per loop: a faster loop. (docs/modules/hardware.md)
        BulkReads.auto(hardwareMap);
        // Two motors named "left_drive" and "right_drive" (the same names as the FTC SDK samples).
        // Change the names on driveConfig if yours differ, e.g. driveConfig.frontLeft = "left".
        DriveConfig driveConfig = DriveConfig.tankTwoMotor();
        // Never more than 80% power to a wheel: easier to control while drivers learn.
        driveConfig.powerLimit = 0.8;
        // Finds the motors and sets their directions. Stops with a clear message if a name is wrong.
        TankDrive drive = new TankDrive(hardwareMap, driveConfig);

        // Reverse drive: the back of the robot acts as the front (handy when loading from behind).
        ReverseDrive reverse = new ReverseDrive();
        // Buzzes gamepad1 to confirm a switch: two buzzes = ON, one buzz = OFF.
        ToggleRumble confirm = new ToggleRumble(gamepad1);
        // The buttons, in one place. "() -> { ... }" is a lambda: a small piece of code we hand over
        // now, to be run LATER when the button is pressed. Buttons: docs/tutorials/03-buttons.md.
        ControllerBindings controls = new ControllerBindings()
                // X: tank style on/off. Each press flips it, with a buzz so the driver knows.
                .bindToggle(Pad.G1, Btn.X,
                        () -> { tankStyle = true; confirm.play(true); },
                        () -> { tankStyle = false; confirm.play(false); })
                // Left stick click: reverse drive on/off. toggle() gives back the new state for the buzz.
                .bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()));

        // Wait here until the driver presses START.
        waitForStart();
        // A button already held when START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // try/finally: the "finally" part runs however the program ends (STOP, or an error),
        // so the wheels are always stopped.
        try {
            // The main loop: runs many times a second until STOP. Nothing in it waits.
            while (opModeIsActive()) {
                // Read the gamepads and run the actions of any buttons just pressed.
                controls.update(gamepad1, gamepad2);
                // Left trigger = slow: not pressed = full speed (1.0), fully pressed = 30%.
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
                // shape(stick, 0.05, 2): ignore tiny values under 0.05 so a resting stick does not
                // make the robot creep, then "square" the rest for fine control at low speed.
                // The minus sign: stick Y reads NEGATIVE when pushed up, and up should be forward.
                if (tankStyle) {
                    // Tank style: each stick drives one side of the robot.
                    double left = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
                    double right = StickShaping.shape(-gamepad1.right_stick_y, 0.05, 2);
                    // Reversed tank: swap and flip the sides, so the back acts as the front.
                    if (reverse.isReversed()) drive.tank(-right, -left, speed);
                    else drive.tank(left, right, speed);
                } else {
                    // Arcade style: left stick = forward/back, right stick = turn.
                    // Only "forward" is flipped in reverse; turning right still turns the robot right.
                    double forward = reverse.apply(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2));
                    double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                    drive.arcade(forward, turn, speed);
                }
                // Show the modes on the Driver Station screen.
                // "a ? b : c" = if a is true use b, otherwise c.
                telemetry.addData("Style", tankStyle ? "tank" : "arcade");
                telemetry.addData("Reverse", reverse.isReversed() ? "ON" : "OFF");
                telemetry.update();
            }
        } finally {
            // SAFETY: stop the wheels when the program ends, whatever the reason.
            drive.stop();
        }
    }
}
