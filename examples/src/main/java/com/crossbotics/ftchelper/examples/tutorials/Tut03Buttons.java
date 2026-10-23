// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Trigger;
import com.crossbotics.ftchelper.gamepad.ToggleRumble;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 03: every kind of button binding. No motors needed: everything shows on telemetry.
 * gamepad1: A press, B hold, X release, Y toggle (buzzes), D-pad up/down = pick a height,
 * RT = "fire" once per pull, LT = its value. gamepad2: A = the same counter as gamepad1 A.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/03-buttons/
 */
@TeleOp(name = "Tutorial 03: Buttons", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut03Buttons extends LinearOpMode {

    // The D-pad moves through this list: HEIGHT_NAMES[0] is "Floor".
    static final String[] HEIGHT_NAMES = {"Floor", "Low", "Middle", "High"};

    // Fields, so the button actions can change them.
    private int presses = 0;
    private int holdLoops = 0;
    private int releases = 0;
    private boolean slowMode = false;
    // Which entry of HEIGHT_NAMES is chosen (0 = the first one).
    private int heightIndex = 0;
    private int shots = 0;
    private double leftTrigger = 0;

    @Override
    public void runOpMode() {
        // Two buzzes = ON, one buzz = OFF.
        ToggleRumble confirm = new ToggleRumble(gamepad1);

        // Actions must be quick: never wait or sleep inside one.
        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> presses++)
                .bindPress(Pad.G2, Btn.A, () -> presses++)
                .bindHold(Pad.G1, Btn.B, () -> holdLoops++)
                .bindRelease(Pad.G1, Btn.X, () -> releases++)
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { slowMode = true; confirm.play(true); },
                        () -> { slowMode = false; confirm.play(false); })
                .bindPress(Pad.G1, Btn.DPAD_UP, () -> heightIndex = Math.min(heightIndex + 1, HEIGHT_NAMES.length - 1))
                .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> heightIndex = Math.max(heightIndex - 1, 0))
                .bindTriggerPress(Pad.G1, Trigger.RT, () -> shots++)
                .bindTriggerAxis(Pad.G1, Trigger.LT, value -> leftTrigger = value);

        telemetry.addLine("Try holding A while you press START.");
        telemetry.update();
        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        // No try/finally here: there are no motors to stop.
        while (opModeIsActive()) {
            controls.update(gamepad1, gamepad2);

            TelemetryHelper.section(telemetry, "Buttons");
            telemetry.addData("A presses (both pads)", presses);
            telemetry.addData("B loops held", holdLoops);
            telemetry.addData("X releases", releases);
            TelemetryHelper.flag(telemetry, "Slow mode (Y)", slowMode);
            telemetry.addData("Height (D-pad)", HEIGHT_NAMES[heightIndex]);
            telemetry.addData("Shots (RT)", shots);
            TelemetryHelper.value(telemetry, "Left trigger", leftTrigger, 2);   // 2 decimal places
            telemetry.update();
        }
    }
}
