package com.crossbotics.ftchelper.examples.lessonbot;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * The lesson robot, file 3 of 5: the buttons, and the TeleOp. The parts bring their own buttons
 * (driving on gamepad 1; intake, lift and claw on gamepad 2). Here we add only our own.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/03-buttons/
 */
@TeleOp(name = "Lesson Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;   // true: everything on gamepad 1

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g2.rb.press(Actions.scoreHigh(robot)).says("score high");     // lesson 5
        g2.b.toggle(robot.claw).says("claw").rumble();                 // 2 buzzes = closed, 1 = open
        g2.lb.tap(Actions.grabAndLift(robot)).says("grab and lift")    // a tap...
             .heldFor(1.0, () -> robot.lift.goTo("down")).says("lift all the way down");   // ...or held 1 s
    }
}
