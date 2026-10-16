// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...),
// this library's (io.github.jasoncross...) and Java's own (java.util.List = a list of things).
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;
import io.github.jasoncross.ftchelper.util.LoopTimer;
import java.util.List;

/**
 * Tutorial 06: measure the loop speed, then make it faster with bulk reads,
 * and lay out telemetry so it is easy to read.
 * A = bulk reads on/off (watch the Hz change). B = show/hide the debug section.
 *
 * Needs motors front_left, front_right, back_left, back_right.
 *
 * Tutorial: docs/tutorials/06-fast-clear-loop.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Tutorial 06: Fast, Clear Loop", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom (see tutorial 01).
public class Tut06FastLoop extends LinearOpMode {

    // "Fields" the buttons flip. Bulk reads start ON (the fast way); the debug section starts hidden.
    private boolean bulk = true;
    private boolean showDebug = false;

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set up. Nothing moves until START. ----

        // Bulk reads, MANUAL kind: each hub sends ALL its sensor and encoder readings in one message,
        // instead of one message (about 2 ms) per reading. "Manual" means WE say when to fetch fresh
        // readings, with BulkReads.clear(...) at the top of every loop. It gives back the list of hubs
        // ("List<LynxModule>" = a list of hubs; a Control Hub or Expansion Hub is a "LynxModule").
        // Most programs use the simpler BulkReads.auto(hardwareMap) instead (docs/modules/hardware.md).
        List<LynxModule> hubs = BulkReads.manual(hardwareMap);
        // A mecanum drive with the standard names and directions.
        MecanumDrive drive = new MecanumDrive(hardwareMap, DriveConfig.mecanum());
        // The four wheel motors, so the debug section can show their encoders.
        // Order: front-left, front-right, back-left, back-right (docs/conventions.md).
        DcMotorEx[] wheels = drive.getMotors();
        // Measures loops per second.
        LoopTimer loopTimer = new LoopTimer();

        // The buttons. "() -> { ... }" is a lambda: code run LATER, when the button is pressed.
        ControllerBindings controls = new ControllerBindings()
                // A: bulk reads on/off, so you can compare the loop speed both ways.
                .bindPress(Pad.G1, Btn.A, () -> {
                    // "!bulk" = the opposite: flip it.
                    bulk = !bulk;
                    // "for (LynxModule hub : hubs)" = "for each hub in the list". Each hub is switched to
                    // MANUAL bulk reads (on) or OFF. "a ? b : c" = if a is true use b, otherwise c.
                    for (LynxModule hub : hubs) {
                        hub.setBulkCachingMode(bulk ? LynxModule.BulkCachingMode.MANUAL : LynxModule.BulkCachingMode.OFF);
                    }
                    // Start measuring again, so the new loop speed is not mixed with the old one.
                    loopTimer.reset();
                })
                // B: show or hide the debug section.
                .bindPress(Pad.G1, Btn.B, () -> showDebug = !showDebug);

        // Wait here until START is pressed.
        waitForStart();
        // A button held down while START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // try/finally: the "finally" part runs however the program ends, so the wheels always stop.
        try {
            // The main loop: runs many times a second until STOP. Nothing in it waits.
            while (opModeIsActive()) {
                // With MANUAL bulk reads, throw away last loop's readings, so this loop's first read
                // fetches fresh ones. Forget this and the readings never change.
                if (bulk) BulkReads.clear(hubs);       // FIRST line of the loop with MANUAL bulk reads
                loopTimer.update();
                controls.update(gamepad1, gamepad2);
                // Plain driving, kept simple here (no deadband or curve; tutorial 04 adds them).
                // Minus sign: stick Y reads NEGATIVE when pushed up, and up = forward.
                drive.drive(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);

                // What the driver needs: at the top.
                TelemetryHelper.section(telemetry, "Driver");
                TelemetryHelper.flag(telemetry, "Bulk reads (A)", bulk);
                // Loops per second. Higher = the robot reacts faster to the sticks.
                TelemetryHelper.value(telemetry, "Loop", loopTimer.getHz(), "Hz");

                // Debug numbers: at the bottom, and only when asked for.
                // This is also where the encoders are read, so the bulk-read difference shows most
                // with the debug section on.
                if (showDebug) {
                    TelemetryHelper.section(telemetry, "Debug (B)");
                    // Short names for the four wheels, in the same order as "wheels".
                    String[] names = {"FL", "FR", "BL", "BR"};
                    // Go through the wheels one by one (i = 0, 1, 2, 3) and show each one's power,
                    // position and speed.
                    for (int i = 0; i < wheels.length; i++) TelemetryHelper.motor(telemetry, names[i], wheels[i]);
                    // Average time for one loop, in milliseconds (1000 / Hz).
                    TelemetryHelper.value(telemetry, "Loop time", loopTimer.getAverageMs(), "ms");
                }
                telemetry.update();
            }
        } finally {
            // SAFETY: stop the wheels when the program ends, whatever the reason.
            drive.stop();
        }
    }
}
