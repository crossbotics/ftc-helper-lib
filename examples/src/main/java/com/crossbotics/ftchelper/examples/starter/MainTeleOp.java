// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.starter;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.teleop.EasyTeleOp;

/**
 * STARTER KIT, file 3 of 6: the TeleOp. ONE TeleOp for both alliances (it reads the alliance
 * autonomous chose).
 *
 * EasyTeleOp (the library class this builds on) does the driving and everything every TeleOp
 * needs. This file only says what is special about this robot: its mechanisms and the operator's
 * buttons. See docs/modules/teleop.md and tutorial 20.
 *
 * Gamepad 1 (driver), from EasyTeleOp:     Gamepad 2 (operator), from this file:
 *   left stick     drive                    D-pad up/down   lift one position up/down
 *   right stick X  turn                     A / Y           lift DOWN / HIGH
 *   left trigger   slow (squeeze)           left stick Y    lift by hand
 *   Y              field-centric on/off     right bumper    claw open/close
 *   BACK           reset "forward"          left bumper     intake on/off
 *   GUIDE          tuning menu (practice)   B (hold)        intake reverse
 *   START + BACK together    debug telemetry on/off
 *
 * Buzzes at 1:00 and 0:20 left (2026-27 BIOBUZZ: FLOWER ownership unlocks at 1:00; final 20 s).
 */
@TeleOp(name = "Main TeleOp", group = "Starter")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
public class MainTeleOp extends EasyTeleOp {

    // The mechanisms (Robot.java). Filled in by createSubsystems() during INIT.
    private Robot robot;
    // The intake starts off; the operator's left bumper turns it on. B held = reverse.
    private boolean intakeOn = false;
    private boolean intakeReverse = false;

    // The robot's settings: the same as every autonomous uses (RobotSettings.java).
    @Override
    protected RobotConfig robotConfig() {
        return new RobotSettings();
    }

    // The mechanisms. EasyTeleOp updates them every loop, shows them, and stops them at the end.
    @Override
    protected Subsystems createSubsystems() {
        robot = new Robot(hardwareMap);
        return robot.all;
    }

    // The operator's buttons. Each action only sets a target: the mechanisms do the moving in
    // their update(), so nothing here waits. ("() -> ..." = code run later, when pressed.)
    @Override
    protected void bindButtons(ControllerBindings driver, ControllerBindings operator) {
        operator.bindPress(Pad.G2, Btn.DPAD_UP, robot.lift::stepUp)
                .bindPress(Pad.G2, Btn.DPAD_DOWN, robot.lift::stepDown)
                .bindPress(Pad.G2, Btn.A, () -> robot.lift.goTo("DOWN"))
                .bindPress(Pad.G2, Btn.Y, () -> robot.lift.goTo("HIGH"))
                .bindPress(Pad.G2, Btn.RB, () -> robot.claw.toggle("OPEN", "CLOSED"))
                .bindPress(Pad.G2, Btn.LB, () -> intakeOn = !intakeOn)
                .bindPress(Pad.G2, Btn.B, () -> intakeReverse = true)
                .bindRelease(Pad.G2, Btn.B, () -> intakeReverse = false);
    }

    // Every loop, after the buttons and driving.
    @Override
    protected void everyLoop() {
        // The operator's left stick moves the lift by hand. Deadband 0.1 so a bumped stick does not
        // cancel a button height. Minus: stick up reads negative. Let go and the lift holds.
        robot.lift.manual(StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1));
        // The intake: reverse wins, then on/off.
        if (intakeReverse) robot.intake.out();
        else if (intakeOn) robot.intake.in();
        else robot.intake.stop();
    }
}
