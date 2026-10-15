package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.TouchSensor;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.hardware.Hw;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.hardware.ServoSetup;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 02: one motor and one servo.
 * Needs a motor named "arm" and a servo named "claw". A touch sensor "arm_touch" is optional.
 * Left stick Y = motor power. A = claw open, B = claw closed. X = zero the encoder.
 *
 * Tutorial: docs/tutorials/02-motors-and-servos.md
 */
@TeleOp(name = "Tutorial 02: Motor and Servo", group = "Tutorials")
@Disabled
public class Tut02MotorAndServo extends LinearOpMode {

    // Claw positions inside the servo's range (see .range below): 0 = open, 1 = closed.
    static final double CLAW_OPEN = 0.0;
    static final double CLAW_CLOSED = 1.0;

    @Override
    public void runOpMode() {
        DcMotorEx arm = MotorSetup.get(hardwareMap, "arm")
                .brake()
                .resetEncoder()
                .runWithoutEncoder()
                .done();

        Servo claw = ServoSetup.get(hardwareMap, "claw")
                .range(0.20, 0.75)     // measured on the robot: fully open, fully closed
                .done();

        // Optional part: null if this robot does not have it.
        TouchSensor touch = Hw.tryGet(hardwareMap, TouchSensor.class, "arm_touch");

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> claw.setPosition(CLAW_OPEN))
                .bindPress(Pad.G1, Btn.B, () -> claw.setPosition(CLAW_CLOSED))
                .bindPress(Pad.G1, Btn.X, () -> MotorSetup.of(arm).resetEncoder().runWithoutEncoder().done());

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                double power = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 1) * 0.5;   // half power max
                arm.setPower(power);

                TelemetryHelper.section(telemetry, "Arm");
                TelemetryHelper.motor(telemetry, "Arm", arm);
                TelemetryHelper.servo(telemetry, "Claw", claw);
                if (touch != null) TelemetryHelper.flag(telemetry, "Touch", touch.isPressed());
                telemetry.update();
            }
        } finally {
            arm.setPower(0);
        }
    }
}
