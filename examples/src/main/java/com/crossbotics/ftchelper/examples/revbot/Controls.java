package com.crossbotics.ftchelper.examples.revbot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;

/**
 * REV starter bot, file 3 of 5: one driver does everything on gamepad 1 ({@code oneDriver = true}),
 * like REV's own TeleOp.
 * <ul>
 *   <li>left stick forward/back drives, right stick X turns (left stick click: slow on/off)</li>
 *   <li>RT intake in, LT intake out, while held (from the intake)</li>
 *   <li>LB spins the flywheel up / down, RB fires one piece (hold RB to keep firing), Y fires all
 *       (from the launcher and Actions.java)</li>
 *   <li>D-pad up/down: flywheel speed +/- 100 RPM</li>
 *   <li>A poker down, B poker up (as in REV's code)</li>
 * </ul>
 * REV's code puts the intake on RB and RT, and the feeder on the D-pad by hand. Here the library's
 * standard buttons are used, so this robot drives like the other examples; change a button here if
 * your drivers want REV's layout.
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
