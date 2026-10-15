package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.TouchSensor;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.hardware.Hw;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.safety.SafeMotor;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 08: a lift that cannot hurt itself. Soft limits, a bottom switch for homing,
 * a slow zone near the ends, set heights with PID, and the stick to take over by hand.
 * Needs a motor "lift". A touch sensor "lift_bottom" is optional but recommended.
 *
 * gamepad2: left stick Y = lift by hand, A = bottom, X = middle, Y = top, B = stop holding.
 *
 * Tutorial: docs/tutorials/08-safe-lift.md
 */
@TeleOp(name = "Tutorial 08: Safe Lift", group = "Tutorials")
@Disabled
public class Tut08SafeLift extends LinearOpMode {

    static final int BOTTOM = 0, MIDDLE = 1200, TOP = 2400, MAX = 2600;

    private Integer target = null;   // null = by hand

    @Override
    public void runOpMode() {
        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        SafeMotor lift = new SafeMotor(liftMotor)
                .setLimits(BOTTOM, MAX)
                .setSlowZone(250, 0.2)
                .setMaxPower(0.9);
        TouchSensor bottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
        if (bottom != null) lift.setLowerSwitch(bottom::isPressed);

        PidController pid = new PidController(0.004, 0, 0.0002, 0.05).setTolerance(20);

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G2, Btn.A, () -> goTo(BOTTOM, pid))
                .bindPress(Pad.G2, Btn.X, () -> goTo(MIDDLE, pid))
                .bindPress(Pad.G2, Btn.Y, () -> goTo(TOP, pid))
                .bindPress(Pad.G2, Btn.B, () -> target = null);

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                lift.update();                         // FIRST: switches and homing
                controls.update(gamepad1, gamepad2);

                double stick = StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1);
                if (stick != 0 || lift.isWaitingForHoming()) target = null;   // the stick takes over
                if (target == null) {
                    lift.setPower(stick);
                } else {
                    lift.moveTo(target, pid);
                }

                TelemetryHelper.section(telemetry, "Lift");
                if (lift.isWaitingForHoming()) telemetry.addLine("Push the lift DOWN to the switch to home it.");
                telemetry.addData("Target", target == null ? "by hand" : target.toString());
                telemetry.addData("Position", lift.getPosition());
                TelemetryHelper.value(telemetry, "Asked power", lift.getRequestedPower(), 2);
                TelemetryHelper.value(telemetry, "Sent power", lift.getAppliedPower(), 2);
                TelemetryHelper.flag(telemetry, "At bottom", lift.isAtLower());
                TelemetryHelper.flag(telemetry, "At top", lift.isAtUpper());
                TelemetryHelper.flag(telemetry, "Homed", lift.isHomed());
                telemetry.update();
            }
        } finally {
            lift.stop();
        }
    }

    private void goTo(int ticks, PidController pid) {
        target = ticks;
        pid.reset();
    }
}
