package com.crossbotics.ftchelper.examples.studicabot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * Studica starter bot, file 3 of 5: the buttons (and the TeleOp). One driver, gamepad 1:
 * - left stick drives, right stick X turns (left stick click: slow on/off); RT/LT intake in/out
 * - LB flywheel on/off, RB fires one (hold to keep firing), Y fires all
 * - D-pad up/down: flywheel speed +/- 100 motor RPM
 * These are each part's usual buttons, written out below,, not Studica's layout:
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#studica-bot-code
 */
@TeleOp(name = "Studica Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = true;

    // Always pre-assigned on gamepad 1 (the library's): sticks: drive; left stick click: slow; BACK: pause;
    // GUIDE: button map (held: tuning menu); START + BACK: debug lines; X / B in INIT: alliance.

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // The intake's own buttons.
        robot.intake.inOn(g2.rt);
        robot.intake.outOn(g2.lt);

        // The launcher's own buttons.
        robot.launcher.spinOn(g2.lb);
        robot.launcher.fireOn(g2.rb);
        robot.launcher.speedOn(g2.dpadUp, g2.dpadDown);
        robot.launcher.tableOn(g2.dpadRight);

        // Ours.
        g1.y.press(Actions.fireAll(robot)).says("fire all");
    }
}
