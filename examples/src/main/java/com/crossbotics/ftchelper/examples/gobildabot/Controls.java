package com.crossbotics.ftchelper.examples.gobildabot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * goBILDA starter bot, file 3 of 5: one driver does everything on gamepad 1, like goBILDA's TeleOp.
 * The parts bring their buttons; Y (fire all four) is ours.
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#gobilda-ftc-starterbot
 */
@TeleOp(name = "goBILDA Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = true;

    // Always pre-assigned on gamepad 1 (the library's): sticks: drive; left stick click: slow; BACK: pause;
    // GUIDE: button map (held: tuning menu); START + BACK: debug lines; X / B in INIT: alliance.

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // The intake's own buttons.
        g2.rt.hold(robot.intake::holdIn, robot.intake::stop).says("in (hold)");
        g2.lt.hold(robot.intake::out, robot.intake::stop).says("out (hold)");

        // The launcher's own buttons.
        g2.lb.toggle(robot.launcher).says("spin up / down");
        g2.rb.hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("fire one; hold: keep firing");
        g2.dpadUp.hold(() -> robot.launcher.repeatStep(1), robot.launcher::repeatEnd).says("speed up, 4 a second")
                .tap(() -> robot.launcher.step(1)).says("speed up");
        g2.dpadDown.hold(() -> robot.launcher.repeatStep(-1), robot.launcher::repeatEnd).says("speed down, 4 a second")
                .tap(() -> robot.launcher.step(-1)).says("speed down")
                .doubleTap(robot.launcher::resetTrim).says("back to speedRpm");

        // Ours.
        g1.y.press(Actions.fireAll(robot)).says("fire all");
    }
}
