// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.mechanism.PositionMotor;
import io.github.jasoncross.ftchelper.mechanism.PositionServo;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.teleop.EasyTeleOp;

/**
 * Tutorial 20: an easy TeleOp. EasyTeleOp does the driving, the match timer, the tuning menu,
 * "forward" from autonomous and stopping everything; we only add the lift and the claw.
 * The TeleOp partner of tutorial 16's autonomous (same mechanisms, same RobotSettings).
 * Needs: the drivetrain from RobotSettings, motor "lift", servo "claw".
 *
 * Driver (gamepad 1), from EasyTeleOp: left stick drive, right stick X turn, left trigger slow,
 * Y field-centric on/off, BACK reset "forward", GUIDE tuning menu, START + BACK debug lines.
 * Operator (gamepad 2), from this file: A = lift down, Y = lift high, right bumper = claw,
 * left stick Y = lift by hand.
 *
 * Before you use it: remove the {@code @Disabled} line and make "lift" and "claw" match your
 * configuration.
 *
 * Tutorial: docs/tutorials/20-easy-teleop.md
 */
@TeleOp(name = "Tutorial 20: Easy TeleOp", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
public class Tut20EasyTeleOp extends EasyTeleOp {

    private PositionMotor lift;
    private PositionServo claw;

    // ---- 1. The robot: the same settings as tutorial 16's autonomous ----
    @Override protected RobotConfig robotConfig() { return RobotSettings.config(); }

    // The mechanisms: the same as tutorial 16. EasyTeleOp updates them every loop and stops them.
    @Override
    protected Subsystems createSubsystems() {
        lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600).pid(0.004, 0, 0.0002, 0.05)
                .position("DOWN", 0).position("HIGH", 2400);
        claw = PositionServo.create(hardwareMap, "claw")
                .position("OPEN", 0.2).position("CLOSED", 0.75).travelTime(0.3);
        return new Subsystems().add(lift, claw);
    }

    // ---- 2. The operator's buttons. Each one only sets a target; nothing waits. ----
    @Override
    protected void bindButtons(ControllerBindings driver, ControllerBindings operator) {
        operator.bindPress(Pad.G2, Btn.A, () -> lift.goTo("DOWN"))
                .bindPress(Pad.G2, Btn.Y, () -> lift.goTo("HIGH"))
                .bindPress(Pad.G2, Btn.RB, () -> claw.toggle("OPEN", "CLOSED"));
    }

    // ---- 3. Every loop: the lift by hand (a stick is not a button, so it goes here) ----
    @Override
    protected void everyLoop() {
        lift.manual(StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1));
    }

    // ---- 4. Optional: one extra screen line (the lift and claw show their own lines already) ----
    @Override
    protected void addTelemetry(RobotTelemetry t) {
        t.line("Alliance", alliance());
    }
}
