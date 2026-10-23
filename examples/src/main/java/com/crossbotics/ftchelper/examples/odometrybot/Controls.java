package com.crossbotics.ftchelper.examples.odometrybot;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.teleop.LibraryButtons;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import java.util.Locale;

/**
 * Lesson 7's TeleOp: the intake's own buttons, and the robot's place on the field on the Driver Station.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/07-odometry/#7-teleop-starts-where-autonomous-ended
 */
@TeleOp(name = "Odometry Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        show("Where", () -> String.format(Locale.US, "x %.0f, y %.0f in", robot.pose().x, robot.pose().y));
    }

    // Optional: switch the alliance in TeleOp (hold START, then BACK, 1.5 s).
    @Override
    protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
        lib.switchAlliance(g1.start, g1.back);
    }
}
