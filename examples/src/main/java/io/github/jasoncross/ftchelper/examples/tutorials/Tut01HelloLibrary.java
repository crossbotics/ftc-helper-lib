package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;
import io.github.jasoncross.ftchelper.util.LoopTimer;

/**
 * Tutorial 01: the shape of every OpMode in these tutorials. No motors needed.
 * Press A to count. Hold B to see "held". Watch the loop speed.
 *
 * Tutorial: docs/tutorials/01-opmode-basics.md
 */
@TeleOp(name = "Tutorial 01: Hello Library", group = "Tutorials")
@Disabled
public class Tut01HelloLibrary extends LinearOpMode {

    // A FIELD: a variable that belongs to the whole class. Button actions can change it.
    private int presses = 0;

    @Override
    public void runOpMode() {
        // ---------- INIT: runs once, when you press INIT ----------
        LoopTimer loopTimer = new LoopTimer();
        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> presses++);

        telemetry.addLine("Ready. Press START.");
        telemetry.update();

        // ---------- wait for START ----------
        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        // ---------- RUN: repeats until STOP ----------
        try {
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
            // ---------- STOP: always runs, even after an error ----------
            telemetry.addLine("Stopped.");
            telemetry.update();
        }
    }
}
