package com.crossbotics.ftchelper.examples.quickstart;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Quick start, file 3 of 5: the buttons, and the TeleOp. Gamepad 1 drives (left stick, right stick X
 * turns, LT slow); the intake's buttons are on gamepad 2 (RT in, LT out). INIT shows the whole map;
 * a tap of GUIDE shows it during the match; a press of BACK pauses everything (a tap again: go on).
 */
// STEP 7: the name on the Driver Station (if you change it, change preselectTeleOp in
//         autos/FirstAuto.java to the same name). In your TeamCode, delete the @Disabled line and
//         its import, and change group = "Examples" to your own group name (or delete it).
@TeleOp(name = "Quick Start TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    // STEP 7: true if one driver does everything on gamepad 1.
    public static boolean oneDriver = false;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // Runs ONCE, at INIT: each line plugs a job into a button. No if, while or sleep() here (lesson 3).
        // An example: an action on gamepad 2's D-pad left (delete it if you deleted the intake in STEP 5).
        g2.dpadLeft.press(Actions.spitOut(robot)).says("spit out");
    }
}
