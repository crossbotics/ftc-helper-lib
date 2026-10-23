// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
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
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/02-motors-and-servos/
 */
@TeleOp(name = "Tutorial 02: Motor and Servo", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut02MotorAndServo extends LinearOpMode {

    // Claw positions inside the servo's range (see .range below): 0 = open, 1 = closed.
    static final double CLAW_OPEN = 0.0;
    static final double CLAW_CLOSED = 1.0;

    @Override
    public void runOpMode() {
        // ---- INIT: set up the parts. Nothing moves yet. ----

        // "arm" = the name in the robot configuration. Reset the encoder BEFORE the run mode.
        DcMotorEx arm = MotorSetup.get(hardwareMap, "arm")
                .brake()
                .resetEncoder()
                .runWithoutEncoder()
                .done();

        Servo claw = ServoSetup.get(hardwareMap, "claw")
                .range(0.20, 0.75)     // measured on the robot: fully open, fully closed
                .done();

        // Optional part: null if this robot does not have it. Check for null before use.
        TouchSensor touch = Hw.tryGet(hardwareMap, TouchSensor.class, "arm_touch");

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> claw.setPosition(CLAW_OPEN))
                .bindPress(Pad.G1, Btn.B, () -> claw.setPosition(CLAW_CLOSED))
                // X: the arm's current spot becomes 0 ticks.
                .bindPress(Pad.G1, Btn.X, () -> MotorSetup.of(arm).resetEncoder().runWithoutEncoder().done());

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                // Minus: stick up reads negative. Ignore values under 0.05, no curve.
                double power = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 1) * 0.5;   // half power max
                arm.setPower(power);

                TelemetryHelper.section(telemetry, "Arm");
                TelemetryHelper.motor(telemetry, "Arm", arm);
                // The position we last asked for: a servo cannot report where it really is.
                TelemetryHelper.servo(telemetry, "Claw", claw);
                if (touch != null) TelemetryHelper.flag(telemetry, "Touch", touch.isPressed());
                telemetry.update();
            }
        } finally {
            // SAFETY: stop the motor when the program ends, whatever the reason.
            arm.setPower(0);
        }
    }
}
