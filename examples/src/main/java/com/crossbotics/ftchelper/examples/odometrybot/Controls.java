package com.crossbotics.ftchelper.examples.odometrybot;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.teleop.LibraryButtons;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import java.util.Locale;

/** Lesson 7's TeleOp: the intake's own buttons, and the robot's place on the field on the Driver Station. */
@TeleOp(name = "Odometry Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        show("Where", () -> String.format(Locale.US, "x %.0f, y %.0f in", robot.pose().x, robot.pose().y));
    }

    // Optional: switch the alliance during TeleOp (hold BACK + START 1.5 s) in case autonomous ran as
    // the wrong one. Off unless this line is here.
    @Override
    protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
        lib.switchAlliance(g1.back, g1.start);
    }
}
