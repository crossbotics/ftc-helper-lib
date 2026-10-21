// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (com.crossbotics...).
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.hardware.ServoSetup;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 02: one motor and one servo.
 * Needs a motor named "arm" and a servo named "claw". A touch sensor "arm_touch" is optional.
 * Left stick Y = motor power. A = claw open, B = claw closed. X = zero the encoder.
 *
 * Tutorial: docs/tutorials/02-motors-and-servos.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Tutorial 02: Motor and Servo", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom (see tutorial 01).
public class Tut02MotorAndServo extends LinearOpMode {

    // Claw positions inside the servo's range (see .range below): 0 = open, 1 = closed.
    // "static final" = fixed numbers that never change. Named, so the code below reads
    // "CLAW_OPEN" instead of a mystery 0.0.
    static final double CLAW_OPEN = 0.0;
    static final double CLAW_CLOSED = 1.0;

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set up the parts. Nothing moves yet. ----

        // The arm motor, set up in one readable chain. "arm" must match the name in your
        // robot's configuration exactly. Each step:
        DcMotorEx arm = MotorSetup.get(hardwareMap, "arm")
                // At power 0 the motor resists turning, so the arm does not drop by itself.
                .brake()
                // The encoder (a sensor that counts how far the shaft has turned, in "ticks")
                // starts at 0 here. Do this BEFORE choosing the run mode.
                .resetEncoder()
                // The power we give goes straight to the motor. We can still read the encoder.
                .runWithoutEncoder()
                // Sets the power to 0 (so nothing moves in INIT) and hands us the motor.
                .done();

        // The claw servo. A servo turns to a position from 0 to 1 and holds it.
        Servo claw = ServoSetup.get(hardwareMap, "claw")
                // Only use the part of the servo's travel between 0.20 and 0.75, so the code can never
                // push the claw into the frame. After this, position 0 means 0.20 and 1 means 0.75.
                .range(0.20, 0.75)     // measured on the robot: fully open, fully closed
                // Hands us the servo. It does not move until we first set a position.
                .done();

        // Optional part: null if this robot does not have it.
        // (null means "nothing". tryGet does not stop the program when the part is missing,
        // so we must check for null before we use it: see "if (touch != null)" below.)
        TouchSensor touch = Hw.tryGet(hardwareMap, TouchSensor.class, "arm_touch");

        // The buttons. "() -> ..." is a lambda: a small piece of code run LATER, when pressed.
        ControllerBindings controls = new ControllerBindings()
                // A: open the claw.
                .bindPress(Pad.G1, Btn.A, () -> claw.setPosition(CLAW_OPEN))
                // B: close the claw.
                .bindPress(Pad.G1, Btn.B, () -> claw.setPosition(CLAW_CLOSED))
                // X: make the arm's current spot "0 ticks" again. Resetting the encoder stops the motor,
                // so we set the run mode again right after, and done() sets power 0 until the next loop.
                .bindPress(Pad.G1, Btn.X, () -> MotorSetup.of(arm).resetEncoder().runWithoutEncoder().done());

        // Wait here until START is pressed.
        waitForStart();
        // A button held down while START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // try/finally: the "finally" part runs however the program ends, so the motor always stops.
        try {
            // The main loop: runs many times a second until STOP. Nothing in it waits.
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                // The stick sets the power. The minus sign: stick Y reads NEGATIVE when pushed up,
                // and up should be positive (docs/conventions.md). shape(..., 0.05, 1) ignores tiny
                // values under 0.05 (a resting stick is rarely exactly 0), with no curve.
                // "* 0.5" = at most half power: a first test should be gentle.
                double power = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 1) * 0.5;   // half power max
                arm.setPower(power);

                // Show what the parts are doing: motor power and position, the servo's position.
                TelemetryHelper.section(telemetry, "Arm");
                TelemetryHelper.motor(telemetry, "Arm", arm);
                // A servo cannot report where it really is: this is the position we last asked for.
                TelemetryHelper.servo(telemetry, "Claw", claw);
                // Only read the touch sensor if the robot has one (otherwise the program would crash).
                if (touch != null) TelemetryHelper.flag(telemetry, "Touch", touch.isPressed());
                telemetry.update();
            }
        } finally {
            // SAFETY: stop the motor when the program ends, whatever the reason.
            arm.setPower(0);
        }
    }
}
