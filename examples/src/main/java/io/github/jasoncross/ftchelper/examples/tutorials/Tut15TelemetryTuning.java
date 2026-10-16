package io.github.jasoncross.ftchelper.examples.tutorials;

// "import" lines tell Java where to find the classes we use: FTC SDK classes (com.qualcomm...)
// and helper-library classes (io.github.jasoncross.ftchelper...).
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
 * Words used here:
 * - Profile: PRACTICE (at the shop: gentle, extra info, tuning allowed) or COMPETITION (at events).
 *   It is saved on the hub, so it stays the same until someone changes it. See docs/guides/profiles.md.
 * - Tunable: a number you can change from the gamepad while the robot runs, without rebuilding
 *   the code. When it is right, copy the new value into the code. See docs/guides/tuning.md.
 * - Debug lines: extra telemetry for programmers, hidden from drivers unless turned on.
 *
 * Before you use it: remove the {@code @Disabled} line, and make "lift" match your configuration.
 *
 * Tutorial: docs/tutorials/15-telemetry-profiles-tuning.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list, with this name and group.
@TeleOp(name = "Tutorial 15: Telemetry, Profiles, Tuning", group = "Tutorials")
// @Disabled hides it from the Driver Station. Delete this line to use the program.
@Disabled
// "extends LinearOpMode": this class IS an FTC program that runs top to bottom; we write runOpMode().
public class Tut15TelemetryTuning extends LinearOpMode {

    // A tunable number that is not part of a mechanism: change it live in the menu.
    // "Lift/maxPower" is its name in the menu (group "Lift"). 0.8 = the value in the code.
    // 0.05 = how much one D-pad press in the menu changes it. range(0.1, 1.0) = the menu will
    // not let it go outside 0.1 to 1.0, so nobody can tune the lift to full reverse by mistake.
    // "static final": one shared value made once; the NAME never changes, though the number
    // inside it can be changed from the menu.
    static final Tunable LIFT_MAX_POWER = Tunable.number("Lift/maxPower", 0.8, 0.05).range(0.1, 1.0);

    // @Override: this replaces the SDK's runOpMode(), where our program starts when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---------- Setup (once, in INIT) ----------

        RobotTelemetry t = new RobotTelemetry(telemetry);     // debug ON in PRACTICE, OFF in COMPETITION
        // Measures how many loops per second we run. Under about 30 means something is slow.
        LoopTimer loopTimer = new LoopTimer();

        // The lift, like tutorial 14 (positions in encoder ticks, limits 0..2600, PID gains).
        PositionMotor lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600)
                .pid(0.004, 0, 0.0002, 0.05)
                .position("DOWN", 0)
                .position("HIGH", 2400)
                .tunable("Lift");                              // kP..kF and positions in the menu

        // The tuning menu, run with gamepad 1. It refuses to open in the COMPETITION profile, so a
        // driver cannot change numbers by accident at an event.
        TuningMenu tuning = new TuningMenu(gamepad1);
        // A 2-minute TeleOp clock that buzzes gamepad 1 when 20 seconds are left (endgame warning).
        MatchTimer timer = MatchTimer.teleOp().rumbleAt(20, gamepad1);

        // Three small button lists: the menu key, the driver's (paused while the menu is open), the operator's.
        // Why three? While the menu is open, gamepad 1's buttons must ONLY work the menu, so the
        // driver's list is not updated then. The menu key and gamepad 2 always work.
        // "tuning::toggle" is a method reference: short for "() -> tuning.toggle()".
        // "() -> lift.goTo("DOWN")" is a lambda: a small piece of code the button runs later.
        ControllerBindings menuKey = new ControllerBindings().bindPress(Pad.G1, Btn.GUIDE, tuning::toggle);
        ControllerBindings driver = new ControllerBindings().bindPress(Pad.G1, Btn.BACK, t::toggleDebug);
        ControllerBindings operator = new ControllerBindings()
                .bindPress(Pad.G2, Btn.A, () -> lift.goTo("DOWN"))
                .bindPress(Pad.G2, Btn.Y, () -> lift.goTo("HIGH"));
        // Remembers whether the menu was open last loop (see the loop below).
        boolean menuWasOpen = false;

        // ---------- INIT: choose the profile ----------
        // InitMenu shows choices on the screen; gamepad 1's D-pad changes them before START.
        InitMenu menu = new InitMenu(gamepad1).profile();
        while (opModeInInit()) {
            menu.update();
            // Warn if some tuned values (saved from an earlier run) differ from the code: either
            // copy them into the code or reset them, so the robot does what the code says.
            if (Tunable.countChanged() > 0) t.warn(Tunable.countChanged() + " tuned value(s) differ from the code");
            menu.addTelemetry(t);
            // Profile.pick(practice, competition) gives the first value in PRACTICE, the second in
            // COMPETITION. "%.0f%%" shows a whole number followed by a % sign.
            t.line("Lift speed in this profile", "%.0f%%", Profile.pick(50.0, 100.0));
            t.update();
        }
        // STOP pressed during INIT: end without moving anything.
        if (isStopRequested()) return;
        timer.start();   // the match clock starts now, at START
        // A button held through START is ignored until let go, so nothing happens by surprise.
        menuKey.ignoreHeldButtons(gamepad1, gamepad2);
        driver.ignoreHeldButtons(gamepad1, gamepad2);
        operator.ignoreHeldButtons(gamepad1, gamepad2);

        // try/finally: whatever happens in the loop (STOP, or a crash), "finally" still runs and
        // stops the lift. Safety first.
        try {
            // ---------- Every loop (many times per second until STOP) ----------
            // Nothing here waits, so the robot keeps reacting. See docs/guides/non-blocking.md.
            while (opModeIsActive()) {
                loopTimer.update();
                menuKey.update(gamepad1, gamepad2);
                operator.update(gamepad1, gamepad2);
                tuning.update();   // reads gamepad 1 for the menu (does nothing while it is closed)
                if (tuning.isOpen()) {
                    menuWasOpen = true;           // gamepad 1's buttons belong to the menu now
                } else {
                    if (menuWasOpen) driver.ignoreHeldButtons(gamepad1, gamepad2);   // a button still held from the menu does not fire
                    menuWasOpen = false;
                    driver.update(gamepad1, gamepad2);
                }
                timer.update();   // checks the clock, buzzes at 20 s left

                // Profile.pick: gentle in practice, full at events. Tunable: changed live.
                // Math.min takes the smaller of the two, so the lift's top power is never more
                // than the profile allows, whatever the tuned value is.
                lift.maxPower(Math.min(LIFT_MAX_POWER.get(), Profile.pick(0.5, 1.0)));
                lift.update();   // the lift does its work for this loop

                // Telemetry: the menu takes over the screen while it is open.
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
