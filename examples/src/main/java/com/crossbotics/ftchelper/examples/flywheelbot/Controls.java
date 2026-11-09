package com.crossbotics.ftchelper.examples.flywheelbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * Example robot 1, file 3 of 5: the buttons, and the TeleOp. Each part's buttons are written
 * out below (INIT shows the whole map), then our own.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/09-shooter-and-camera/#10-no-turret-aim-the-whole-robot
 */
@TeleOp(name = "FlywheelBot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;   // true: everything on gamepad 1

    // Always pre-assigned on gamepad 1 (the library's): sticks: drive; LT: slow; BACK: pause; GUIDE: button
    // map (held: tuning menu); START + BACK: debug lines; X / B in INIT: alliance.

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
        // RB: whole-robot aim on/off (two buzzes = on, one = off). Numbers: Tunables.Aim.
        g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();
        // Gamepad 2 buzzes while the robot points at the goal (the closer, the stronger).
        g2.rumbleWhenAimed();
        // Y: fire every piece the robot holds (the launcher's ready-made action; press again to cancel).
        g2.y.press(robot.launcher.fireAllAction()).says("fire all");
        // Free buttons, listed on the help page.
        g2.a.free();
        g2.x.free();
        g2.b.free();
    }
}
