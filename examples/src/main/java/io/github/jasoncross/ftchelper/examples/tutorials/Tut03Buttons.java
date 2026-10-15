package io.github.jasoncross.ftchelper.examples.tutorials;

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
@TeleOp(name = "Tutorial 03: Buttons", group = "Tutorials")
@Disabled
public class Tut03Buttons extends LinearOpMode {

    static final String[] HEIGHT_NAMES = {"Floor", "Low", "Middle", "High"};

    private int presses = 0;
    private int holdLoops = 0;
    private int releases = 0;
    private boolean slowMode = false;
    private int heightIndex = 0;
    private int shots = 0;
    private double leftTrigger = 0;

    @Override
    public void runOpMode() {
        ToggleRumble confirm = new ToggleRumble(gamepad1);

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

        while (opModeIsActive()) {
            controls.update(gamepad1, gamepad2);

            TelemetryHelper.section(telemetry, "Buttons");
            telemetry.addData("A presses (both pads)", presses);
            telemetry.addData("B loops held", holdLoops);
            telemetry.addData("X releases", releases);
            TelemetryHelper.flag(telemetry, "Slow mode (Y)", slowMode);
            telemetry.addData("Height (D-pad)", HEIGHT_NAMES[heightIndex]);
            telemetry.addData("Shots (RT)", shots);
            TelemetryHelper.value(telemetry, "Left trigger", leftTrigger, 2);
            telemetry.update();
        }
    }
}
