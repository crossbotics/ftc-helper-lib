package com.crossbotics.ftchelper.examples.lessonbot;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * The lesson robot, file 3 of 5: the buttons, and the TeleOp. Driving is the library's (gamepad 1);
 * the intake's, lift's and claw's buttons are written out below (gamepad 2), then our own.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/03-buttons/
 */
@TeleOp(name = "Lesson Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;   // true: everything on gamepad 1

    // Always pre-assigned on gamepad 1 (the library's): sticks: drive; LT: slow; Y: field-centric (double
    // tap); BACK: pause; GUIDE: button map (held: tuning menu); START + BACK: debug lines; X / B in INIT:
    // alliance.

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // The intake's own buttons.
        robot.intake.inOn(g2.rt);
        robot.intake.outOn(g2.lt);

        // The lift's own buttons.
        g2.a.press(() -> robot.lift.goTo("down")).says("down");
        g2.x.press(() -> robot.lift.goTo("low")).says("low");
        g2.y.press(() -> robot.lift.goTo("high")).says("high");
        g2.dpadUp.press(robot.lift::stepUp).says("next height up");
        g2.dpadDown.press(robot.lift::stepDown).says("next height down");
        g2.leftStickY.use(robot.lift::manual).says("by hand");   // one driver: g2.rightStickY (G1 left stick drives)

        // Ours.
        g2.rb.press(Actions.scoreHigh(robot)).says("score high");     // lesson 5
        g2.b.toggle(robot.claw).says("claw").rumble();                 // 2 buzzes = closed, 1 = open
        g2.lb.tap(Actions.grabAndLift(robot)).says("grab and lift")    // a tap...
             .heldFor(1.0, () -> robot.lift.goTo("down")).says("lift all the way down");   // ...or held 1 s
    }
}
