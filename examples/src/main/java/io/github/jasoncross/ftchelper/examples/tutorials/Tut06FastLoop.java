package io.github.jasoncross.ftchelper.examples.tutorials;

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
 * Tutorial: docs/tutorials/06-fast-clear-loop.md
 */
@TeleOp(name = "Tutorial 06: Fast, Clear Loop", group = "Tutorials")
@Disabled
public class Tut06FastLoop extends LinearOpMode {

    private boolean bulk = true;
    private boolean showDebug = false;

    @Override
    public void runOpMode() {
        List<LynxModule> hubs = BulkReads.manual(hardwareMap);
        MecanumDrive drive = new MecanumDrive(hardwareMap, DriveConfig.mecanum());
        DcMotorEx[] wheels = drive.getMotors();
        LoopTimer loopTimer = new LoopTimer();

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> {
                    bulk = !bulk;
                    for (LynxModule hub : hubs) {
                        hub.setBulkCachingMode(bulk ? LynxModule.BulkCachingMode.MANUAL : LynxModule.BulkCachingMode.OFF);
                    }
                    loopTimer.reset();
                })
                .bindPress(Pad.G1, Btn.B, () -> showDebug = !showDebug);

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                if (bulk) BulkReads.clear(hubs);       // FIRST line of the loop with MANUAL bulk reads
                loopTimer.update();
                controls.update(gamepad1, gamepad2);
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
