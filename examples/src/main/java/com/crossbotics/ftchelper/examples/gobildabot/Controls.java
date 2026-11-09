package com.crossbotics.ftchelper.examples.gobildabot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * goBILDA starter bot, file 3 of 5: one driver does everything on gamepad 1, like goBILDA's TeleOp.
 * Each part's buttons are written out below; Y (fire all four) is ours.
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
