package io.github.jasoncross.ftchelper.examples.gobildabot;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.robot.ControlMap;
import io.github.jasoncross.ftchelper.robot.Pad;

/**
 * goBILDA starter bot, file 3 of 5: like goBILDA's own TeleOp, one driver does everything on
 * gamepad 1 ({@code oneDriver = true}):
 * <ul>
 *   <li>left stick forward/back drives, right stick X turns (left stick click: slow on/off)</li>
 *   <li>RT intake in, LT intake out, while held</li>
 *   <li>LB spins the flywheel up / down, RB fires one piece (it waits until the wheel is at speed),
 *       Y fires all three (hold RT too, to help push them in)</li>
 *   <li>D-pad up/down: flywheel speed +/- 50 RPM</li>
 * </ul>
 * One difference from goBILDA's code: there, holding RB spins up AND feeds; here spinning up and
 * firing are two buttons, so the driver can spin up early and fire the moment the shot is lined up.
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
