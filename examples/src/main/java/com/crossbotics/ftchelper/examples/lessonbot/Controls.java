package com.crossbotics.ftchelper.examples.lessonbot;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * The lesson robot, file 3 of 5: the buttons, and the TeleOp (lesson 3). The parts bring their own
 * buttons: driving on gamepad 1; intake (RT in, LT out), lift (A down, X low, Y high, D-pad, left
 * stick by hand) and claw (B) on gamepad 2. Here we add only what is ours: two actions.
 */
@TeleOp(name = "Lesson Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;   // true: everything on gamepad 1

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g2.rb.press(Actions.scoreHigh(robot)).says("score high");     // lesson 5
        g2.lb.press(Actions.grabAndLift(robot)).says("grab and lift");
    }
}
