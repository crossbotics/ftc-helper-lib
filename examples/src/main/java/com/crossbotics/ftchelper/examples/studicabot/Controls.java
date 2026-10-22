package com.crossbotics.ftchelper.examples.studicabot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * Studica starter bot, file 3 of 5: one driver does everything on gamepad 1 ({@code oneDriver = true}).
 * - left stick forward/back drives, right stick X turns (left stick click: slow on/off)
 * - RT intake in, LT intake out, while held (from the intake)
 * - LB spins the flywheel up / down, RB fires one piece (hold RB to keep firing), Y fires all
 *   (from the launcher and Actions.java)
 * - D-pad up/down: flywheel speed +/- 100, in motor units (not the wheel's RPM: see Tunables.FlyWheel)
 * Studica's code drives "tank style" (each stick runs one side), puts the intake on the bumpers, the
 * flywheel on RT and the flap on X and A. The library drives with one stick and its standard
 * buttons, so this robot drives like the other examples; change a button here if your drivers want
 * Studica's layout.
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
