// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library. java... = Java's own.
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.drive.MecanumDrive;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.hardware.BulkReads;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;
import com.crossbotics.ftchelper.util.LoopTimer;
import java.util.List;

/**
 * Tutorial 06: measure the loop speed, then make it faster with bulk reads,
 * and lay out telemetry so it is easy to read.
 * A = bulk reads on/off (watch the Hz change). B = show/hide the debug section.
 * Needs motors front_left, front_right, back_left, back_right.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/06-fast-clear-loop/
 */
@TeleOp(name = "Tutorial 06: Fast, Clear Loop", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut06FastLoop extends LinearOpMode {

    // The buttons flip these.
    private boolean bulk = true;
    private boolean showDebug = false;

    @Override
    public void runOpMode() {
        // MANUAL bulk reads: BulkReads.clear(hubs) at the top of every loop. Most programs use BulkReads.auto.
        List<LynxModule> hubs = BulkReads.manual(hardwareMap);
        MecanumDrive drive = new MecanumDrive(hardwareMap, DriveConfig.mecanum());
        DcMotorEx[] wheels = drive.getMotors();   // FL, FR, BL, BR
        LoopTimer loopTimer = new LoopTimer();

        ControllerBindings controls = new ControllerBindings()
                // A: bulk reads on/off, to compare the loop speed both ways.
                .bindPress(Pad.G1, Btn.A, () -> {
                    bulk = !bulk;
                    for (LynxModule hub : hubs) {
                        hub.setBulkCachingMode(bulk ? LynxModule.BulkCachingMode.MANUAL : LynxModule.BulkCachingMode.OFF);
                    }
                    loopTimer.reset();   // do not mix the new loop speed with the old one
                })
                .bindPress(Pad.G1, Btn.B, () -> showDebug = !showDebug);

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                if (bulk) BulkReads.clear(hubs);       // FIRST line of the loop with MANUAL bulk reads
                loopTimer.update();
                controls.update(gamepad1, gamepad2);
                // Plain driving, no deadband or curve (tutorial 04 adds them).
                drive.drive(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);

                // What the driver needs: at the top.
                TelemetryHelper.section(telemetry, "Driver");
                TelemetryHelper.flag(telemetry, "Bulk reads (A)", bulk);
                TelemetryHelper.value(telemetry, "Loop", loopTimer.getHz(), "Hz");

                // Debug numbers: at the bottom, and only when asked for.
                if (showDebug) {
                    TelemetryHelper.section(telemetry, "Debug (B)");
                    String[] names = {"FL", "FR", "BL", "BR"};
                    for (int i = 0; i < wheels.length; i++) TelemetryHelper.motor(telemetry, names[i], wheels[i]);
                    TelemetryHelper.value(telemetry, "Loop time", loopTimer.getAverageMs(), "ms");
                }
                telemetry.update();
            }
        } finally {
            drive.stop();
        }
    }
}
