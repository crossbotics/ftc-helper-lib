package com.crossbotics.ftchelper.examples.cookbook;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/*
 * Cookbook (docs/cookbook.md): a whole file, as it looks in your TeamCode. The lines between
 * "// recipe: name" and "// end recipe" are shown on the page; tools/check_docs.py keeps them the same.
 */
// recipe: file-controls
@TeleOp(name = "Cookbook TeleOp", group = "Cookbook")   // the name on the Driver Station
@Disabled                                               // delete this line in your copy
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // robot = your Robot.java, g1 = gamepad 1, g2 = gamepad 2. Button lines go here:
        g2.y.press(Actions.scoreHigh(robot)).says("score high");
        show("Lift ticks", robot.lift::position);
    }
}
// end recipe
