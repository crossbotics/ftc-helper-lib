// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (com.crossbotics...).
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
 *
 * To use it: copy it into TeamCode, remove the @Disabled line, and change the motor names to
 * the names in YOUR robot's configuration.
 */
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Example: Drive + Intake", group = "Examples")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = this is an FTC program that runs from top to bottom. The SDK gives
// us hardwareMap (the robot's parts), gamepad1, gamepad2 and telemetry (the screen).
public class ExampleDriveIntake extends LinearOpMode {

    // "static final" = a fixed number that never changes. Kept at the top so it is easy to find.
    // 0.35 is slow enough to line up carefully but still gets somewhere. Try other values.
    static final double SLOW_SPEED = 0.35;   // slow mode = 35% speed

    // "Fields": true/false switches that belong to the whole program. The buttons flip them,
    // and the loop reads them to decide what to do. "private" = only this file uses them.
    private boolean slowMode = false;
    // It starts true, but nothing spins until after START: the loop is what turns it on.
    private boolean intakeOn = true;         // the intake starts ON
    // True only while the left bumper is held down.
    private boolean reversing = false;

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: find and set up the hardware. Nothing moves yet. ----

        // Read all sensors from the hub in one message per loop: a faster loop. (docs/modules/hardware.md)
        BulkReads.auto(hardwareMap);
        // Our helper for the Driver Station screen. "t" is a short name because we use it a lot.
        RobotTelemetry t = new RobotTelemetry(telemetry);

        // The four wheel motors, with the standard mecanum names and directions.
        // If your names differ, change them on a DriveConfig first (see docs/tutorials/04-drive-mecanum.md).
        MecanumDrive drive = new MecanumDrive(hardwareMap, DriveConfig.mecanum());
        // The intake: a motor that spins IN, OUT or stops. "intake" is its configuration name.
        // IN at full power to grab pieces quickly; OUT at 70% so pieces are pushed out, not thrown.
        SpinMotor intake = SpinMotor.create(hardwareMap, "intake")
                .powers(1.0, -0.7);                  // in = full, out = 70% (gentler)
        // Buzzes gamepad1 so the driver can feel whether slow mode just turned on or off.
        ToggleRumble confirm = new ToggleRumble(gamepad1);

        // Every button, in one place. "() -> ..." is a lambda: a small piece of code we hand over now
        // to be run LATER, when the button is used. Buttons: docs/tutorials/03-buttons.md.
        ControllerBindings controls = new ControllerBindings()
                // Slow mode toggle, with a buzz so the driver knows without looking.
                // A toggle flips each press: first press runs the first action (ON), next press the second (OFF).
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { slowMode = true;  confirm.play(true); },
                        () -> { slowMode = false; confirm.play(false); })
                // Intake on/off.
                // "!intakeOn" means "the opposite of intakeOn", so each press flips it.
                .bindPress(Pad.G1, Btn.RB, () -> intakeOn = !intakeOn)
                // Reverse while held.
                // bindPress runs once when the bumper goes down, bindRelease once when it comes up.
                .bindPress(Pad.G1, Btn.LB, () -> reversing = true)
                .bindRelease(Pad.G1, Btn.LB, () -> reversing = false)
                // Debug lines on/off.
                // "t::toggleDebug" is a method reference: "run t.toggleDebug() when pressed".
                .bindPress(Pad.G1, Btn.BACK, t::toggleDebug);

        t.text("Intake starts ON at START. Press START.");
        t.update();
        // Wait here until the driver presses START.
        waitForStart();
        // A button that was already held when START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        // try/finally: the "finally" part runs however the program ends (STOP, or an error),
        // so the motors are always stopped.
        try {
            // The main loop: runs many times a second until STOP. Nothing in it waits.
            while (opModeIsActive()) {
                // Read the gamepads and run the actions of any buttons just pressed or released.
                controls.update(gamepad1, gamepad2);

                // ---- Drive ----
                // "a ? b : c" = if a is true use b, otherwise c. So: slow mode = 0.35, else full (1.0).
                double speed = slowMode ? SLOW_SPEED : 1.0;
                // shape(stick, 0.05, 2): ignore tiny stick values under 0.05 (a resting stick is rarely
                // exactly 0, and the robot must not creep), then "square" the rest for fine control.
                // The minus on left_stick_y: the stick reads NEGATIVE when pushed up, and up should be
                // forward (docs/conventions.md).
                drive.drive(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
                        StickShaping.shape(gamepad1.left_stick_x, 0.05, 2),
                        StickShaping.shape(gamepad1.right_stick_x, 0.05, 2),
                        speed);

                // ---- Intake: reverse wins, then on/off. (in/out/stop are safe to call every loop.) ----
                // The rules are all here, readable top to bottom. When the bumper is let go, the
                // intake goes back to on OR off, whichever it was, with no extra code.
                if (reversing) intake.out();
                else if (intakeOn) intake.in();
                else intake.stop();
                // update() sends the power to the motor. Call it once every loop.
                intake.update();

                // ---- Telemetry ----
                // If a button action took too long (it froze the whole robot), show a warning.
                if (!controls.getSlowActionWarning().isEmpty()) t.warn(controls.getSlowActionWarning());
                t.section("Driver");
                t.line("Speed", slowMode ? "SLOW" : "full");
                // Two "? :" in a row: reversing -> "REVERSING", else intakeOn -> "ON", else "off".
                t.line("Intake", reversing ? "REVERSING" : intakeOn ? "ON" : "off");
                intake.addTelemetry(t);
                // Send everything to the screen.
                t.update();
            }
        } finally {
            // SAFETY: stop every motor when the program ends, whatever the reason.
            drive.stop();
            intake.stop();
        }
    }
}
