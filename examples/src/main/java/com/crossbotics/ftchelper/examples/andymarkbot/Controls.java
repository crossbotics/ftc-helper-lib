package com.crossbotics.ftchelper.examples.andymarkbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * AndyMark starter bot, file 3 of 5: the buttons (and the TeleOp). One driver, gamepad 1:
 * - left stick drives, right stick X turns (left stick click: slow on/off)
 * - RT intake in, LT intake out (while held); RB throws one piece, Y throws all (Actions.java)
 * These are the library's standard buttons, not AndyMark's layout:
 * more: https://crossbotics.com/ftc-helper/docs/guides/starter-bots/#andymark-bot-code
 */
@TeleOp(name = "AndyMark Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = true;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g1.y.press(Actions.throwAll(robot)).says("throw all");
    }
}
