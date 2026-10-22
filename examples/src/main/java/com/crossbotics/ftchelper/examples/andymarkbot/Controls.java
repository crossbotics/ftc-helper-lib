package com.crossbotics.ftchelper.examples.andymarkbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * AndyMark starter bot, file 3 of 5: one driver does everything on gamepad 1 ({@code oneDriver = true}).
 * - left stick forward/back drives, right stick X turns (left stick click: slow on/off)
 * - RT intake in, LT intake out, while held (from the intake)
 * - RB throws one piece (from the catapult's Indexer: one turn per press), Y throws everything
 *   (Actions.java)
 * AndyMark's code puts the intake on the bumpers (LB in, RB out) and runs the catapult while RT is
 * held. Here the library's standard buttons are used, so this robot drives like the other examples;
 * change a button here if your drivers want AndyMark's layout.
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
