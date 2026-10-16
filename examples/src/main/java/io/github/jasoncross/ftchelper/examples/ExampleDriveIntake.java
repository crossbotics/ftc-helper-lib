package io.github.jasoncross.ftchelper.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.mechanism.SpinMotor;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;

/**
 * Mecanum drive + slow-mode toggle + an intake that is ON from the start and can be stopped,
 * started again, and reversed. One driver, gamepad 1.
 *
 *   Left stick        drive (forward/back, sideways)
 *   Right stick X     turn
 *   Y                 slow mode on/off (2 buzzes = on, 1 buzz = off)
 *   Right bumper      intake on/off
 *   Left bumper       HOLD to reverse the intake (spit out); let go = back to what it was doing
 *   BACK              debug telemetry on/off
 *
 * Needs: motors front_left, front_right, back_left, back_right, intake.
 * Explained in: docs/guides/example-drive-intake.md
 */
@TeleOp(name = "Example: Drive + Intake", group = "Examples")
@Disabled
public class ExampleDriveIntake extends LinearOpMode {

    static final double SLOW_SPEED = 0.35;   // slow mode = 35% speed

    private boolean slowMode = false;
    private boolean intakeOn = true;         // the intake starts ON
    private boolean reversing = false;

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        RobotTelemetry t = new RobotTelemetry(telemetry);

        MecanumDrive drive = new MecanumDrive(hardwareMap, DriveConfig.mecanum());
        SpinMotor intake = SpinMotor.create(hardwareMap, "intake")
                .powers(1.0, -0.7);                  // in = full, out = 70% (gentler)
        ToggleRumble confirm = new ToggleRumble(gamepad1);

        ControllerBindings controls = new ControllerBindings()
                // Slow mode toggle, with a buzz so the driver knows without looking.
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { slowMode = true;  confirm.play(true); },
                        () -> { slowMode = false; confirm.play(false); })
                // Intake on/off.
                .bindPress(Pad.G1, Btn.RB, () -> intakeOn = !intakeOn)
                // Reverse while held.
                .bindPress(Pad.G1, Btn.LB, () -> reversing = true)
                .bindRelease(Pad.G1, Btn.LB, () -> reversing = false)
                // Debug lines on/off.
                .bindPress(Pad.G1, Btn.BACK, t::toggleDebug);

        t.text("Intake starts ON at START. Press START.");
        t.update();
        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);

                // ---- Drive ----
                double speed = slowMode ? SLOW_SPEED : 1.0;
                drive.drive(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
                        StickShaping.shape(gamepad1.left_stick_x, 0.05, 2),
                        StickShaping.shape(gamepad1.right_stick_x, 0.05, 2),
                        speed);

                // ---- Intake: reverse wins, then on/off. (in/out/stop are safe to call every loop.) ----
                if (reversing) intake.out();
                else if (intakeOn) intake.in();
                else intake.stop();
                intake.update();

                // ---- Telemetry ----
                t.section("Driver");
                t.line("Speed", slowMode ? "SLOW" : "full");
                t.line("Intake", reversing ? "REVERSING" : intakeOn ? "ON" : "off");
                intake.addTelemetry(t);
                t.update();
            }
        } finally {
            drive.stop();
            intake.stop();
        }
    }
}
