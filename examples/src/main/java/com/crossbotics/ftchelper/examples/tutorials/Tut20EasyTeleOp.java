// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.mechanism.PositionMotor;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.teleop.EasyTeleOp;

/**
 * Tutorial 20: an easy TeleOp, the partner of tutorial 16's autonomous. EasyTeleOp does the
 * driving, the match timer, the tuning menu, "forward" from autonomous and stopping everything.
 * Needs: the drivetrain from RobotSettings, motor "lift", servo "claw".
 * Operator (gamepad 2): A = lift down, Y = lift high, RB = claw, left stick Y = lift by hand.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/20-easy-teleop/
 */
@TeleOp(name = "Tutorial 20: Easy TeleOp", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
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
