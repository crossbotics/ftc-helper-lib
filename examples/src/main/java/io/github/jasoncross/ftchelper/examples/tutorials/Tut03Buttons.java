// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (io.github.jasoncross...).
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Trigger;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 03: every kind of button binding. No motors needed: everything shows on telemetry.
 *
 * gamepad1: A = count presses, B = count while held, X = count releases, Y = toggle (buzzes),
 *           D-pad up/down = choose a height from a list, right trigger = "fire" once per pull,
 *           left trigger = shows its value.
 * gamepad2: A = the same counter as gamepad1 A (two drivers, one action).
 *
 * Tutorial: docs/tutorials/03-buttons.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Tutorial 03: Buttons", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom (see tutorial 01).
public class Tut03Buttons extends LinearOpMode {

    // A fixed list of names ("String[]" = a list of pieces of text). "static final" = it never changes.
    // The D-pad moves through it: HEIGHT_NAMES[0] is "Floor", HEIGHT_NAMES[3] is "High".
    static final String[] HEIGHT_NAMES = {"Floor", "Low", "Middle", "High"};

    // "Fields": values that belong to the whole program, so the button actions can change them
    // and the loop can show them. "int" = a whole number, "boolean" = true/false, "double" = a decimal.
    private int presses = 0;
    private int holdLoops = 0;
    private int releases = 0;
    private boolean slowMode = false;
    // Which entry of HEIGHT_NAMES is chosen (0 = the first one).
    private int heightIndex = 0;
    private int shots = 0;
    private double leftTrigger = 0;

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // Buzzes gamepad1 to confirm the toggle: two buzzes = ON, one buzz = OFF.
        // A toggle is invisible, so the driver needs to FEEL which way it went.
        ToggleRumble confirm = new ToggleRumble(gamepad1);

        // Every kind of binding. Each line: on this gamepad (G1 or G2), this button, runs this.
        // "() -> ..." is a lambda: a small piece of code we hand over now, to be run LATER.
        // The actions must be quick: never wait or sleep inside one, or the whole robot freezes.
        ControllerBindings controls = new ControllerBindings()
                // bindPress: once each time the button goes down. Holding it does not repeat.
                .bindPress(Pad.G1, Btn.A, () -> presses++)
                // The same action on the other gamepad: either driver can do it.
                .bindPress(Pad.G2, Btn.A, () -> presses++)
                // bindHold: EVERY loop while the button is down (so the count climbs fast).
                // Use it for things that should keep going while held, like moving a lift.
                .bindHold(Pad.G1, Btn.B, () -> holdLoops++)
                // bindRelease: once when the button comes back up.
                .bindRelease(Pad.G1, Btn.X, () -> releases++)
                // bindToggle: each press flips between ON (first action) and OFF (second action).
                // It starts OFF. The curly brackets { } hold two steps in one lambda.
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { slowMode = true; confirm.play(true); },
                        () -> { slowMode = false; confirm.play(false); })
                // D-pad up: one entry up the list. Math.min stops it at the last entry
                // (HEIGHT_NAMES.length - 1 = 3), so it can never run off the end of the list.
                .bindPress(Pad.G1, Btn.DPAD_UP, () -> heightIndex = Math.min(heightIndex + 1, HEIGHT_NAMES.length - 1))
                // D-pad down: one entry down. Math.max stops it at 0, the first entry.
                .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> heightIndex = Math.max(heightIndex - 1, 0))
                // A trigger used like a button: once each time it is pulled past halfway (0.5).
                .bindTriggerPress(Pad.G1, Trigger.RT, () -> shots++)
                // A trigger's VALUE (0 = let go, 1 = squeezed all the way), handed over every loop.
                // "value -> ..." is a lambda that receives that number as "value".
                .bindTriggerAxis(Pad.G1, Trigger.LT, value -> leftTrigger = value);

        // An experiment: hold A while pressing START, and see that it does NOT count (next lines).
        telemetry.addLine("Try holding A while you press START.");
        telemetry.update();
        // Wait here until START is pressed.
        waitForStart();
        // A button that was already held when START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        // The main loop: runs many times a second until STOP. (No try/finally here: there are
        // no motors to stop.)
        while (opModeIsActive()) {
            // Reads both gamepads and runs the actions above. Without this line no button does anything.
            controls.update(gamepad1, gamepad2);

            // Show every counter, so you can see what each kind of binding does.
            TelemetryHelper.section(telemetry, "Buttons");
            telemetry.addData("A presses (both pads)", presses);
            telemetry.addData("B loops held", holdLoops);
            telemetry.addData("X releases", releases);
            TelemetryHelper.flag(telemetry, "Slow mode (Y)", slowMode);
            telemetry.addData("Height (D-pad)", HEIGHT_NAMES[heightIndex]);
            telemetry.addData("Shots (RT)", shots);
            // Shown with 2 digits after the decimal point.
            TelemetryHelper.value(telemetry, "Left trigger", leftTrigger, 2);
            telemetry.update();
        }
    }
}
