package com.crossbotics.ftchelper.examples.gobildabot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * goBILDA starter bot, file 3 of 5: like goBILDA's own TeleOp, one driver does everything on
 * gamepad 1 ({@code oneDriver = true}):
 * - left stick forward/back drives, right stick X turns (left stick click: slow on/off)
 * - RT intake in, LT intake out, while held
 * - LB spins the flywheel up / down, RB fires one piece (it waits until the wheel is at speed; hold RB to keep firing),
 *   Y fires all three (hold RT too, to help push them in)
 * - D-pad up/down: flywheel speed +/- 50 RPM
 * Like goBILDA's code, holding RB keeps firing. One difference: there, RB also spins the wheel up;
 * here spinning up is LB too, so the driver can spin up early and fire the moment the shot is lined up
 * (RB alone also works: it spins up first, then fires).
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
