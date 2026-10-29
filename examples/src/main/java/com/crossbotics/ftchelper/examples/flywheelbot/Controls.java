package com.crossbotics.ftchelper.examples.flywheelbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * Example robot 1, file 3 of 5: the buttons, and the TeleOp. The parts bring their standard
 * buttons (INIT shows the whole map); here are only our own.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/09-shooter-and-camera/#10-no-turret-aim-the-whole-robot
 */
@TeleOp(name = "FlywheelBot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;   // true: everything on gamepad 1

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
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
