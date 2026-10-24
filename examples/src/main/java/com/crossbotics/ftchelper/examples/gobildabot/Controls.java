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

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g1.y.press(Actions.fireAll(robot)).says("fire all");
    }
}
