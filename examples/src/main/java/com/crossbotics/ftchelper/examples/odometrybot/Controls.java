package com.crossbotics.ftchelper.examples.odometrybot;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.teleop.LibraryButtons;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import java.util.Locale;

/**
 * Lesson 7's TeleOp: the intake's own buttons, the robot's place on the field on the Driver Station, and
 * a drive to the park spot by itself.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/start/07-odometry/#7-teleop-starts-where-autonomous-ended
 */
@TeleOp(name = "Odometry Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;

    // Always pre-assigned on gamepad 1 (the library's): sticks: drive; LT: slow; Y: field-centric (double
    // tap); BACK: pause; GUIDE: button map (held: tuning menu); START held 1 s: set pose; START + BACK held
    // 1.5 s: switch alliance; START + BACK: debug lines; X / B in INIT: alliance.

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // The intake's own buttons.
        g2.rt.hold(robot.intake::holdIn, robot.intake::stop).says("in (hold)");
        g2.lt.hold(robot.intake::out, robot.intake::stop).says("out (hold)");

        // Ours.
        show("Where", () -> String.format(Locale.US, "x %.0f, y %.0f in", robot.pose().x, robot.pose().y));
        // Drives to the park spot by itself, around the HIVE frames. Any drive stick stops it.
        Runnable park = route("Park", Tunables.Auto.parkX, Tunables.Auto.parkY, 0, 0.8);
        g1.dpadLeft.tap(park).says("drive to the park spot (a drive stick stops it)");
        atSecondsLeft(10, park);
    }

    // Optional: switch the alliance in TeleOp (hold START, then BACK, 1.5 s).
    @Override
    protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
        lib.switchAlliance(g1.start, g1.back);
    }
}
