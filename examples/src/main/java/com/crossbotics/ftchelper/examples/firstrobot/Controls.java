package com.crossbotics.ftchelper.examples.firstrobot;

// recipe: first-controls
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.teleop.LibraryButtons;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/** The five files, file 3 of 5: the buttons, and the TeleOp. */
@TeleOp(name = "First Robot TeleOp")
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = true;

    // Always there: the library's buttons, on gamepad 1. These lines are the defaults (one driver):
    // without this method the buttons are the same. Change a line to move a button; null = no button.
    @Override
    protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
        lib.swapSticks(false);                   // left stick: drive and slide; right stick X: turn
        lib.slow(null);                          // slow while a trigger is pulled: none (the triggers are free)
        lib.slowToggle(g1.leftStickButton);      // left stick click: slow on / off
        lib.fieldCentric(g1.rightStickButton);   // right stick click, a double tap: field-centric on / off
        lib.pause(g1.back);                      // BACK: pause at the press; a tap goes on
        lib.resetForward(g1.back);               // BACK held 1 s: "forward" is where the robot faces now
        lib.debug(g1.start, g1.back);            // hold START, then press BACK: the debug lines
        lib.setPose(g1.start);                   // START held 1 s: set pose here (robots with odometry)
        lib.buttonMap(g1.guide);                 // GUIDE: the button map on the Driver Hub
        lib.tuningMenu(g1.guide);                // GUIDE held 1 s: the tuning menu (PRACTICE profile)
        lib.allianceInInit(g1.x, g1.b);          // in INIT: X = BLUE, B = RED
    }

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // Every other button is a line here: each part's buttons, then your own. This robot has no parts yet.
        // The free buttons: replace free() with a job, e.g. .press(...), .hold(...), .toggle(...), .tap(...).
        g1.a.free();
        g1.b.free();                              // B and X choose the alliance in INIT only: free in the match
        g1.x.free();
        g1.y.free();
        g1.lb.free();
        g1.rb.free();
        g1.lt.free();                             // a trigger counts as pressed past half way
        g1.rt.free();
        g1.dpadUp.free();
        g1.dpadDown.free();
        g1.dpadLeft.free();
        g1.dpadRight.free();
        g1.rightStickY.free();                    // a stick: .use(...) gets its value every loop
    }
}
// end recipe
