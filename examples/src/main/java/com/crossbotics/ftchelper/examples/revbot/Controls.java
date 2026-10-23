package com.crossbotics.ftchelper.examples.revbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * REV starter bot, file 3 of 5: the buttons (and the TeleOp). One driver, gamepad 1:
 * - left stick drives, right stick X turns (left stick click: slow on/off); RT/LT intake in/out
 * - LB flywheel on/off, RB fires one (hold to keep firing), Y fires all; D-pad up/down: +/- 100 RPM
 * - A poker down, B poker up (as in REV's code)
 * The rest are the library's standard buttons, not REV's layout:
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#rev-bot-code
 */
@TeleOp(name = "REV Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = true;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g1.y.press(Actions.fireAll(robot)).says("fire all");
        g1.a.press(() -> robot.poker.goTo("down")).says("poker down");
        g1.b.press(() -> robot.poker.goTo("up")).says("poker up");
    }
}
