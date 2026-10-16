package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.core.Profile;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.mechanism.PositionMotor;
import io.github.jasoncross.ftchelper.match.InitMenu;
import io.github.jasoncross.ftchelper.match.MatchTimer;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.tuning.Tunable;
import io.github.jasoncross.ftchelper.tuning.TuningMenu;
import io.github.jasoncross.ftchelper.util.LoopTimer;

/**
 * Tutorial 15: telemetry levels, practice/competition profiles, live tuning, and the match timer.
 * Needs a motor "lift" (like tutorial 08).
 *
 * INIT: D-pad to choose the profile. RUN: gamepad 2 A/Y = lift DOWN/HIGH (so you can test while
 * tuning), gamepad 1 BACK = debug lines on/off, gamepad 1 GUIDE (center button) = tuning menu
 * (PRACTICE profile only). While the menu is open, gamepad 1's other buttons belong to the menu.
 *
 * Tutorial: docs/tutorials/15-telemetry-profiles-tuning.md
 */
@TeleOp(name = "Tutorial 15: Telemetry, Profiles, Tuning", group = "Tutorials")
@Disabled
public class Tut15TelemetryTuning extends LinearOpMode {

    // A tunable number that is not part of a mechanism: change it live in the menu.
    static final Tunable LIFT_MAX_POWER = Tunable.number("Lift/maxPower", 0.8, 0.05).range(0.1, 1.0);

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);     // debug ON in PRACTICE, OFF in COMPETITION
        LoopTimer loopTimer = new LoopTimer();

        PositionMotor lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600)
                .pid(0.004, 0, 0.0002, 0.05)
                .position("DOWN", 0)
                .position("HIGH", 2400)
                .tunable("Lift");                              // kP..kF and positions in the menu

        TuningMenu tuning = new TuningMenu(gamepad1);
        MatchTimer timer = MatchTimer.teleOp().rumbleAt(20, gamepad1);

        // Three small button lists: the menu key, the driver's (paused while the menu is open), the operator's.
        ControllerBindings menuKey = new ControllerBindings().bindPress(Pad.G1, Btn.GUIDE, tuning::toggle);
        ControllerBindings driver = new ControllerBindings().bindPress(Pad.G1, Btn.BACK, t::toggleDebug);
        ControllerBindings operator = new ControllerBindings()
                .bindPress(Pad.G2, Btn.A, () -> lift.goTo("DOWN"))
                .bindPress(Pad.G2, Btn.Y, () -> lift.goTo("HIGH"));
        boolean menuWasOpen = false;

        // ---------- INIT: choose the profile ----------
        InitMenu menu = new InitMenu(gamepad1).profile();
        while (opModeInInit()) {
            menu.update();
            if (Tunable.countChanged() > 0) t.warn(Tunable.countChanged() + " tuned value(s) differ from the code");
            menu.addTelemetry(t);
            t.line("Lift speed in this profile", "%.0f%%", Profile.pick(50.0, 100.0));
            t.update();
        }
        if (isStopRequested()) return;
        timer.start();
        menuKey.ignoreHeldButtons(gamepad1, gamepad2);
        driver.ignoreHeldButtons(gamepad1, gamepad2);
        operator.ignoreHeldButtons(gamepad1, gamepad2);

        try {
            while (opModeIsActive()) {
                loopTimer.update();
                menuKey.update(gamepad1, gamepad2);
                operator.update(gamepad1, gamepad2);
                tuning.update();
                if (tuning.isOpen()) {
                    menuWasOpen = true;           // gamepad 1's buttons belong to the menu now
                } else {
                    if (menuWasOpen) driver.ignoreHeldButtons(gamepad1, gamepad2);   // a button still held from the menu does not fire
                    menuWasOpen = false;
                    driver.update(gamepad1, gamepad2);
                }
                timer.update();

                // Profile.pick: gentle in practice, full at events. Tunable: changed live.
                lift.maxPower(Math.min(LIFT_MAX_POWER.get(), Profile.pick(0.5, 1.0)));
                lift.update();

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
            lift.stop();
        }
    }
}
