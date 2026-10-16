package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.mechanism.PositionMotor;
import io.github.jasoncross.ftchelper.mechanism.PositionServo;
import io.github.jasoncross.ftchelper.mechanism.SpinMotor;
import io.github.jasoncross.ftchelper.mechanism.SpinServo;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;

/**
 * Tutorial 14: the four mechanism helpers. Each mechanism is a few lines of setup, and each
 * button is one line. Needs: motor "lift" (+ optional touch "lift_bottom"), servo "wrist",
 * motor "intake", continuous-rotation servo "spinner". Delete what you do not have.
 *
 * gamepad1: D-pad up/down = lift one position up/down, A/X/Y = lift DOWN/LOW/HIGH,
 *           left stick Y = lift by hand, LB/RB = wrist previous/next position,
 *           right trigger = intake in (hold), B = intake out (hold), D-pad right = spinner (hold).
 *
 * Tutorial: docs/tutorials/14-mechanisms.md
 */
@TeleOp(name = "Tutorial 14: Mechanisms", group = "Tutorials")
@Disabled
public class Tut14Mechanisms extends LinearOpMode {

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);

        // 1. A motor that goes to named positions (a lift).
        PositionMotor lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600)
                .lowerSwitch(hardwareMap, "lift_bottom")
                .slowZone(250, 0.2)
                .pid(0.004, 0, 0.0002, 0.05)
                .position("DOWN", 0)
                .position("LOW", 900)
                .position("HIGH", 2400);

        // 2. A servo with named positions (a wrist), moving slowly so it does not swing.
        PositionServo wrist = PositionServo.create(hardwareMap, "wrist")
                .position("STOW", 0.15)
                .position("LEVEL", 0.50)
                .position("DROP", 0.85)
                .maxSpeed(1.5);

        // 3. A motor that spins (an intake).
        SpinMotor intake = SpinMotor.create(hardwareMap, "intake").powers(1.0, -0.7);

        // 4. A continuous-rotation servo (a small spinner).
        SpinServo spinner = SpinServo.create(hardwareMap, "spinner").power(0.8);

        // One group: updated, shown and stopped together.
        Subsystems all = new Subsystems().add(lift, wrist, intake, spinner);

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.DPAD_UP, lift::stepUp)
                .bindPress(Pad.G1, Btn.DPAD_DOWN, lift::stepDown)
                .bindPress(Pad.G1, Btn.A, () -> lift.goTo("DOWN"))
                .bindPress(Pad.G1, Btn.X, () -> lift.goTo("LOW"))
                .bindPress(Pad.G1, Btn.Y, () -> lift.goTo("HIGH"))
                .bindPress(Pad.G1, Btn.LB, wrist::previous)
                .bindPress(Pad.G1, Btn.RB, wrist::next)
                .bindHold(Pad.G1, Btn.B, intake::out)
                .bindRelease(Pad.G1, Btn.B, intake::stop)
                .bindHold(Pad.G1, Btn.DPAD_RIGHT, spinner::forward)
                .bindRelease(Pad.G1, Btn.DPAD_RIGHT, spinner::stop);

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        wrist.goTo("STOW");
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                lift.manual(StickShaping.shape(-gamepad1.left_stick_y, 0.1, 1));
                if (!gamepad1.b) {
                    if (gamepad1.right_trigger > 0.2) intake.in(); else intake.stop();
                }
                all.update();
                all.addTelemetry(t);
                t.update();
            }
        } finally {
            all.stop();
        }
    }
}
