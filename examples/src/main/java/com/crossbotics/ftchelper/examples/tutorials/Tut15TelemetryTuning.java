package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.core.Profile;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.mechanism.PositionMotor;
import com.crossbotics.ftchelper.match.InitMenu;
import com.crossbotics.ftchelper.match.MatchTimer;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.tuning.TuningMenu;
import com.crossbotics.ftchelper.util.LoopTimer;

/**
 * Tutorial 15: telemetry levels, practice/competition profiles, live tuning, and the match timer.
 * Needs a motor "lift" (like tutorial 08). INIT: D-pad = profile. RUN: gamepad 2 A/Y = lift
 * DOWN/HIGH, gamepad 1 BACK = debug lines, GUIDE = tuning menu (PRACTICE profile only).
 * When a tuned number is right, copy it into the code.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/15-telemetry-profiles-tuning/
 */
@TeleOp(name = "Tutorial 15: Telemetry, Profiles, Tuning", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut15TelemetryTuning extends LinearOpMode {

    // Menu name, value in the code, step per D-pad press, allowed range.
    static final Tunable LIFT_MAX_POWER = Tunable.number("Lift/maxPower", 0.8, 0.05).range(0.1, 1.0);

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);     // debug ON in PRACTICE, OFF in COMPETITION
        LoopTimer loopTimer = new LoopTimer();   // under about 30 Hz means something is slow

        // The lift, like tutorial 14.
        PositionMotor lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600)
                .pid(0.004, 0, 0.0002, 0.05)
                .position("DOWN", 0)
                .position("HIGH", 2400)
                .tunable("Lift");                              // kP..kF and positions in the menu

        TuningMenu tuning = new TuningMenu(gamepad1);   // will not open in COMPETITION
        MatchTimer timer = MatchTimer.teleOp().rumbleAt(20, gamepad1);

        // Three button lists: the driver's is paused while the menu is open (gamepad 1 = the menu).
        ControllerBindings menuKey = new ControllerBindings().bindPress(Pad.G1, Btn.GUIDE, tuning::toggle);
        ControllerBindings driver = new ControllerBindings().bindPress(Pad.G1, Btn.BACK, t::toggleDebug);
        ControllerBindings operator = new ControllerBindings()
                .bindPress(Pad.G2, Btn.A, () -> lift.goTo("DOWN"))
                .bindPress(Pad.G2, Btn.Y, () -> lift.goTo("HIGH"));
        boolean menuWasOpen = false;

        // ---------- INIT: choose the profile with gamepad 1's D-pad ----------
        InitMenu menu = new InitMenu(gamepad1).profile();
        while (opModeInInit()) {
            menu.update();
            if (Tunable.countChanged() > 0) t.warn(Tunable.countChanged() + " tuned value(s) differ from the code");
            menu.addTelemetry(t);
            t.line("Lift speed in this profile", "%.0f%%", Profile.pick(50.0, 100.0));
            t.update();
        }
        if (isStopRequested()) return;
        timer.start();   // the match clock starts now, at START
        menuKey.ignoreHeldButtons(gamepad1, gamepad2);
        driver.ignoreHeldButtons(gamepad1, gamepad2);
        operator.ignoreHeldButtons(gamepad1, gamepad2);

        try {
            while (opModeIsActive()) {
                loopTimer.update();
                menuKey.update(gamepad1, gamepad2);
                operator.update(gamepad1, gamepad2);
                tuning.update();   // reads gamepad 1 for the menu (does nothing while it is closed)
                if (tuning.isOpen()) {
                    menuWasOpen = true;           // gamepad 1's buttons belong to the menu now
                } else {
                    // a button still held from the menu does not fire
                    if (menuWasOpen) driver.ignoreHeldButtons(gamepad1, gamepad2);
                    menuWasOpen = false;
                    driver.update(gamepad1, gamepad2);
                }
                timer.update();   // checks the clock, buzzes at 20 s left

                // Never more than the profile allows, whatever the tuned value is.
                lift.maxPower(Math.min(LIFT_MAX_POWER.get(), Profile.pick(0.5, 1.0)));
                lift.update();   // the lift does its work for this loop

                // The menu takes over the screen while it is open.
                if (tuning.isOpen()) {
                    tuning.addTelemetry(t);
                } else {
                    t.line("Time", timer.format());                        // standard: drivers
                    lift.addTelemetry(t);                                   // standard + debug lines
                    t.debug("Loop", "%.0f Hz", loopTimer.getHz());         // debug: programmers
                    t.debug("Profile", Profile.current());
                    if (timer.secondsLeft() < 20) t.warn("Final 20 seconds!");
                }
                t.update();
            }
        } finally {
            lift.stop();   // safety: no power to the lift when the program ends, for any reason
        }
    }
}
