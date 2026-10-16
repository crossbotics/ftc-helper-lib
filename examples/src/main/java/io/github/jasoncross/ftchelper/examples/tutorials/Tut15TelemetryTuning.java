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
 * INIT: D-pad to choose the profile. RUN: A/Y = lift DOWN/HIGH, BACK = debug lines on/off,
 * GUIDE (the center button) = tuning menu (PRACTICE profile only).
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

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> lift.goTo("DOWN"))
                .bindPress(Pad.G1, Btn.Y, () -> lift.goTo("HIGH"))
                .bindPress(Pad.G1, Btn.BACK, t::toggleDebug)
                .bindPress(Pad.G1, Btn.GUIDE, tuning::toggle);

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
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        try {
            while (opModeIsActive()) {
                loopTimer.update();
                controls.update(gamepad1, gamepad2);
                tuning.update();
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
