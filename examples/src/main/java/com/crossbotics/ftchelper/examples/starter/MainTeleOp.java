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
 * STARTER KIT, file 3 of 6: the TeleOp, ONE for both alliances (it reads the alliance autonomous
 * chose). EasyTeleOp does the driving and what every TeleOp needs; this file adds the mechanisms
 * and the operator's buttons (gamepad 2). Buzzes at 1:00 and 0:20 left.
 * Every button, to print for the drive team:
 * https://crossbotics.com/ftc-helper/docs/starter-kit/#the-buttons
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

    // The operator's buttons (gamepad 2). Each one only sets a target, so nothing here waits.
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
        // The lift by hand: deadband 0.1; minus because stick up reads negative. Let go: it holds.
        robot.lift.manual(StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1));
        // The intake: reverse wins, then on/off.
        if (intakeReverse) robot.intake.out();
        else if (intakeOn) robot.intake.in();
        else robot.intake.stop();
    }
}
