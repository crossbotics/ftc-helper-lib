// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (com.crossbotics...).
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 08: a lift that cannot hurt itself. Soft limits, a bottom switch for homing,
 * a slow zone near the ends, set heights with PID, and the stick to take over by hand.
 * Needs a motor "lift". A touch sensor "lift_bottom" is optional but recommended.
 *
 * gamepad2: left stick Y = lift by hand, A = bottom, X = middle, Y = top, B = stop holding.
 *
 * Tutorial: docs/tutorials/08-safe-lift.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Tutorial 08: Safe Lift", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom (see tutorial 01).
public class Tut08SafeLift extends LinearOpMode {

    // Heights in encoder ticks (counts of how far the motor shaft has turned). Measure yours.
    // MAX is the highest the software ever allows: a little above TOP, and below the real top,
    // so the lift never hits its physical stop under power.
    // "static final" = fixed numbers that never change.
    static final int BOTTOM = 0, MIDDLE = 1200, TOP = 2400, MAX = 2600;

    // Where the lift is going, or null (= "no target") when the stick moves it by hand.
    // "Integer" (not "int") because an int cannot be null.
    private Integer target = null;   // null = by hand

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set up. Nothing moves until START. ----

        // The lift motor: brake at power 0 (so it does not drop), the encoder starts at 0 here
        // (so turn the robot on with the lift DOWN), our code chooses the power, power 0 for INIT.
        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        // SafeMotor: every power we send goes through it, so it can always say no.
        SafeMotor lift = new SafeMotor(liftMotor)
                // Software stops at BOTTOM and MAX ticks: it will not drive past them.
                .setLimits(BOTTOM, MAX)
                // Within 250 ticks of either end, power is cut, down to 20% right at the end,
                // so the lift does not slam into its stops.
                .setSlowZone(250, 0.2)
                // Never more than 90% power: a little gentler on the motor and the string or belt.
                .setMaxPower(0.9);
        // The bottom switch, if the robot has one (null if not in the configuration).
        TouchSensor bottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
        // With a switch: pressing it stops downward power and resets the count to 0 ("homing"),
        // which fixes drift. Until it has been pressed once, the lift only goes down, slowly.
        // "bottom::isPressed" is a method reference: it hands over "how to read the switch".
        if (bottom != null) lift.setLowerSwitch(bottom::isPressed);

        // The PID controller for the set heights: P = 0.004, I = 0, D = 0.0002, and F = 0.05,
        // a small steady push to hold the lift up against gravity. Within 20 ticks = "at target".
        // Use the numbers you found in tutorial 07.
        PidController pid = new PidController(0.004, 0, 0.0002, 0.05).setTolerance(20);

        // The operator's buttons (gamepad 2). "() -> ..." is a lambda: code run LATER, when pressed.
        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G2, Btn.A, () -> goTo(BOTTOM, pid))
                .bindPress(Pad.G2, Btn.X, () -> goTo(MIDDLE, pid))
                .bindPress(Pad.G2, Btn.Y, () -> goTo(TOP, pid))
                // B: forget the target. With the stick at rest, power goes to 0 and the brake holds.
                .bindPress(Pad.G2, Btn.B, () -> target = null);

        // Wait here until START is pressed.
        waitForStart();
        // A button held down while START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // try/finally: the "finally" part runs however the program ends, so the lift always stops.
        try {
            // The main loop: runs many times a second until STOP. Nothing in it waits.
            while (opModeIsActive()) {
                // Read the switch first, so this loop's power decisions use the newest reading.
                lift.update();                         // FIRST: switches and homing
                controls.update(gamepad1, gamepad2);

                // The stick, with a deadband of 0.1 (bigger than for driving, so a bumped stick does
                // not cancel a set height) and no curve (exponent 1). Minus: stick up reads negative.
                double stick = StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1);
                // Any stick push cancels the button height. Before homing, set heights are cancelled too:
                // the zero is not known yet, so the operator must first bring the lift down by hand.
                if (stick != 0 || lift.isWaitingForHoming()) target = null;   // the stick takes over
                if (target == null) {
                    // By hand. SafeMotor still applies the limits, the slow zone and the switch.
                    lift.setPower(stick);
                } else {
                    // To a set height, every loop. The target is kept inside the limits too.
                    lift.moveTo(target, pid);
                }

                TelemetryHelper.section(telemetry, "Lift");
                if (lift.isWaitingForHoming()) telemetry.addLine("Push the lift DOWN to the switch to home it.");
                // "a ? b : c" = if a is true use b, otherwise c.
                telemetry.addData("Target", target == null ? "by hand" : target.toString());
                telemetry.addData("Position", lift.getPosition());
                // What we asked for, and what SafeMotor really sent after its limits. When they differ,
                // a safety rule is at work.
                TelemetryHelper.value(telemetry, "Asked power", lift.getRequestedPower(), 2);
                TelemetryHelper.value(telemetry, "Sent power", lift.getAppliedPower(), 2);
                TelemetryHelper.flag(telemetry, "At bottom", lift.isAtLower());
                TelemetryHelper.flag(telemetry, "At top", lift.isAtUpper());
                TelemetryHelper.flag(telemetry, "Homed", lift.isHomed());
                telemetry.update();
            }
        } finally {
            // SAFETY: stop the lift when the program ends, whatever the reason.
            lift.stop();
        }
    }

    // Called by A / X / Y. Only sets the new height (the loop does the moving), and clears the
    // PID's memory from the last move so the new one starts fresh.
    private void goTo(int ticks, PidController pid) {
        target = ticks;
        pid.reset();
    }
}
