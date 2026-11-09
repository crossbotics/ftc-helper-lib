package com.crossbotics.ftchelper.examples.allbuttons;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.teleop.LibraryButtons;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Every button, written out: the TeleOp. The library's buttons in libraryButtons(...), each part's
 * usual buttons in buttons(...) (a test checks they are the parts' usual ones), and a line for every
 * free button to start from.
 * more: https://crossbotics.com/ftc-helper/docs/five-files/03-controls-java/#a-whole-robots-buttons
 */
@TeleOp(name = "All Buttons TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;   // two drivers: gamepad 1 drives, gamepad 2 runs the parts

    // The library's own buttons, on gamepad 1. These lines are the defaults: without them, the same.
    @Override
    protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
        lib.swapSticks(false);                   // left stick drives and slides, right stick X turns
        lib.slow(g1.lt);                         // slow: the further it is pulled, the slower
        lib.slowToggle(null);                    // slow on/off: no button (one driver: left stick click)
        lib.fieldCentric(g1.y);                  // field-centric on/off: a double tap
        lib.pause(g1.back);                      // pause at the press; a tap goes on
        lib.resetForward(g1.back);               // held 1 s: "forward" is where the robot faces now
        lib.debug(g1.start, g1.back);            // hold START, then press BACK: the debug lines
        lib.setPose(g1.start);                   // held 1 s: set pose here (robots with odometry)
        lib.buttonMap(g1.guide);                 // a tap: the button map on the Driver Hub
        lib.tuningMenu(g1.guide);                // held 1 s: the tuning menu (PRACTICE profile)
        lib.allianceInInit(g1.x, g1.b);          // in INIT: X = BLUE, B = RED
        lib.switchAlliance(null, null);          // switch the alliance in TeleOp: off
    }

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // The intake's own buttons.
        robot.intake.inOn(g2.rt);
        robot.intake.outOn(g2.lt);

        // The launcher's own buttons.
        robot.launcher.spinOn(g2.lb);
        robot.launcher.fireOn(g2.rb);
        robot.launcher.speedOn(g2.dpadUp, g2.dpadDown);
        robot.launcher.tableOn(g2.dpadRight);

        // Free on gamepad 2: replace free() with a job, e.g. .press(...), .tap(...), .toggle(...), .heldFor(...).
        g2.a.free();
        g2.b.free();
        g2.x.free();
        g2.y.free();
        g2.dpadLeft.free();
        g2.dpadRight.free();
        g2.leftStickButton.free();
        g2.rightStickButton.free();
        g2.back.free();
        g2.start.free();
        g2.guide.free();
        g2.leftStickX.free();                     // a stick: .use(...) gets its value every loop
        g2.leftStickY.free();
        g2.rightStickX.free();
        g2.rightStickY.free();

        // Free on gamepad 1 (the driver's): keep them for quick jobs the driver needs.
        g1.a.free();
        g1.lb.free();
        g1.rb.free();
        g1.rt.free();
        g1.dpadUp.free();
        g1.dpadDown.free();
        g1.dpadLeft.free();
        g1.dpadRight.free();
        g1.leftStickButton.free();
        g1.rightStickButton.free();
        g1.rightStickY.free();
        g1.x.free();                              // X and B choose the alliance in INIT only: free in the match
        g1.b.free();
    }
}
