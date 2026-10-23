// The folder this file lives in. Change it to your TeamCode folder when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;
import com.crossbotics.ftchelper.util.LoopTimer;

/**
 * Tutorial 01: the shape of every OpMode in these tutorials. No motors needed.
 * Press A to count. Hold B to see "held". Watch the loop speed.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/01-opmode-basics/
 */
@TeleOp(name = "Tutorial 01: Hello Library", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut01HelloLibrary extends LinearOpMode {

    // A field: button actions can change it.
    private int presses = 0;

    // The SDK calls runOpMode() when the driver presses INIT.
    @Override
    public void runOpMode() {
        // ---------- INIT: runs once. The robot must not move here. ----------
        LoopTimer loopTimer = new LoopTimer();
        // When A on gamepad 1 is pressed, add 1 to presses (one press = one count).
        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> presses++);

        // Nothing shows until update() sends it.
        telemetry.addLine("Ready. Press START.");
        telemetry.update();

        // ---------- wait for START (or STOP) ----------
        waitForStart();
        // A button held while START was pressed is not a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        // ---------- RUN: repeats until STOP ----------
        try {
            // Never wait or sleep in this loop: the robot must keep reacting.
            while (opModeIsActive()) {
                loopTimer.update();
                controls.update(gamepad1, gamepad2);

                TelemetryHelper.section(telemetry, "Hello");
                telemetry.addData("A presses", presses);
                TelemetryHelper.flag(telemetry, "B held", controls.isDown(Pad.G1, Btn.B));
                TelemetryHelper.value(telemetry, "Loop speed", loopTimer.getHz(), "Hz");
                telemetry.update();
            }
        } finally {
            // ---------- STOP: always runs, even after an error. Stop motors here. ----------
            telemetry.addLine("Stopped.");
            telemetry.update();
        }
    }
}
