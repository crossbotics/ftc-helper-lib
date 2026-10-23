// The folder this file lives in. Change it to your TeamCode folder when you copy the file.
package com.crossbotics.ftchelper.examples;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.drive.MecanumDrive;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.gamepad.ToggleRumble;
import com.crossbotics.ftchelper.hardware.BulkReads;
import com.crossbotics.ftchelper.mechanism.SpinMotor;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;

/**
 * Mecanum drive + slow-mode toggle + an intake that is ON from the start and can be stopped,
 * started again, and reversed. One driver, gamepad 1: left stick = drive, right stick X = turn,
 * Y = slow mode, RB = intake on/off, LB (hold) = intake reverse, BACK = debug lines.
 * Needs: motors front_left, front_right, back_left, back_right, intake.
 * more: https://crossbotics.com/ftc-helper/docs/guides/example-drive-intake/
 */
@TeleOp(name = "Example: Drive + Intake", group = "Examples")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class ExampleDriveIntake extends LinearOpMode {

    static final double SLOW_SPEED = 0.35;   // slow mode = 35% speed

    private boolean slowMode = false;
    private boolean intakeOn = true;          // the intake starts ON
    private boolean reversing = false;        // true while the left bumper is held

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        RobotTelemetry t = new RobotTelemetry(telemetry);

        // Standard mecanum names and directions. Other names: use a DriveConfig (tutorial 04).
        MecanumDrive drive = new MecanumDrive(hardwareMap, DriveConfig.mecanum());
        // "intake" = the name in the robot configuration.
        SpinMotor intake = SpinMotor.create(hardwareMap, "intake")
                .powers(1.0, -0.7);                  // in = full, out = 70% (gentler)
        ToggleRumble confirm = new ToggleRumble(gamepad1);

        // The buttons only change the switches; the loop decides what the intake does.
        ControllerBindings controls = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { slowMode = true;  confirm.play(true); },
                        () -> { slowMode = false; confirm.play(false); })
                .bindPress(Pad.G1, Btn.RB, () -> intakeOn = !intakeOn)
                .bindPress(Pad.G1, Btn.LB, () -> reversing = true)
                .bindRelease(Pad.G1, Btn.LB, () -> reversing = false)
                .bindPress(Pad.G1, Btn.BACK, t::toggleDebug);

        t.text("Intake starts ON at START. Press START.");
        t.update();
        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);

                // ---- Drive: deadband 0.05, then squared. Minus: stick up reads negative. ----
                double speed = slowMode ? SLOW_SPEED : 1.0;
                drive.drive(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
                        StickShaping.shape(gamepad1.left_stick_x, 0.05, 2),
                        StickShaping.shape(gamepad1.right_stick_x, 0.05, 2),
                        speed);

                // ---- Intake (in/out/stop are safe to call every loop) ----
                if (reversing) intake.out();       // reverse wins
                else if (intakeOn) intake.in();
                else intake.stop();
                intake.update();                   // the motor gets its power here

                // ---- Telemetry ----
                // A button action that took too long froze the whole robot: show a warning.
                if (!controls.getSlowActionWarning().isEmpty()) t.warn(controls.getSlowActionWarning());
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
