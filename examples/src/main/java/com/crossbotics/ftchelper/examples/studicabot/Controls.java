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
 * These are the library's standard buttons, not Studica's layout:
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#studica-bot-code
 */
@TeleOp(name = "Studica Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = true;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g1.y.press(Actions.fireAll(robot)).says("fire all");
    }
}
