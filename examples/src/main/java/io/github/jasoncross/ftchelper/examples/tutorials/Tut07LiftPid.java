// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (io.github.jasoncross...).
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 07: hold a lift at a height with PID, and tune the PID live from the gamepad.
 * Needs a motor "lift" with an encoder. Turn the robot on with the lift at the BOTTOM.
 *
 * A = bottom, X = middle, Y = top.
 * D-pad left/right = choose which gain to change (P, I, D, F).
 * D-pad up/down = make that gain bigger/smaller (x1.25, or +0.01 for F).
 * Write the final numbers down; they are lost when the OpMode stops.
 *
 * WARNING: this program has no software limits yet (tutorial 08 adds them). Measure your heights
 * first, keep a hand near STOP, and make sure the lift has a physical stop at the top.
 *
 * Tutorial: docs/tutorials/07-pid-lift.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Tutorial 07: Lift PID Tuner", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom (see tutorial 01).
public class Tut07LiftPid extends LinearOpMode {

    // The three heights, in encoder ticks (counts of how far the motor shaft has turned).
    // 0 = where the lift was when the program started. Measure yours: move the lift by hand and
    // read the position (tutorial 02). TOP must be below the real top of the lift.
    // "static final" = fixed numbers that never change.
    static final int BOTTOM = 0, MIDDLE = 1200, TOP = 2400;   // encoder ticks: measure yours

    // The names of the four gains, in the same order as the "gains" list below.
    static final String[] GAIN_NAMES = {"kP", "kI", "kD", "kF"};

    // The four gains P, I, D, F (tutorial 07 explains each one). "double[]" = a list of decimal
    // numbers. "final" means this field always points to the SAME list; the numbers inside can
    // still change (the D-pad changes them).
    // Start with a small P only: the lift moves gently, and you add more step by step.
    private final double[] gains = {0.002, 0.0, 0.0, 0.0};   // start: small P only
    // Which gain the D-pad up/down changes: 0 = kP, 1 = kI, 2 = kD, 3 = kF.
    private int selected = 0;
    // Where the lift should be, in ticks. It starts at the bottom.
    private int target = BOTTOM;

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set up. Nothing moves until START. ----

        // The lift motor: brake at power 0 (so it does not drop), the encoder starts at 0 here,
        // our code chooses the power, and done() sets power 0 for INIT.
        DcMotorEx lift = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();

        // The PID controller: each loop it looks at "target minus position" (the error) and works out
        // a power. new PidController(P, I, D, F) uses the four starting gains above.
        PidController pid = new PidController(gains[0], gains[1], gains[2], gains[3])
                // Never more than 40% power down or 70% up while tuning: a bad gain cannot slam the
                // lift. Up gets more because it fights gravity.
                .setOutputLimits(-0.4, 0.7)     // gentle while tuning: down at 40%, up at 70%
                // The I part may add at most 0.2 power. Without a limit, I can build up while the lift
                // is stuck and then make it shoot past the target ("windup").
                .setIntegralLimit(0.2)
                // Within 20 ticks of the target counts as "at target".
                .setTolerance(20);

        // The buttons. "() -> ..." is a lambda: a small piece of code run LATER, when pressed.
        ControllerBindings controls = new ControllerBindings()
                // A / X / Y: choose a height.
                .bindPress(Pad.G1, Btn.A, () -> newTarget(BOTTOM, pid))
                .bindPress(Pad.G1, Btn.X, () -> newTarget(MIDDLE, pid))
                .bindPress(Pad.G1, Btn.Y, () -> newTarget(TOP, pid))
                // D-pad right: next gain. "%" gives the remainder after dividing, so after the last
                // gain (3) it wraps round to the first (0).
                .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> selected = (selected + 1) % gains.length)
                // D-pad left: previous gain. Adding gains.length first keeps it from going below 0,
                // so before the first gain it wraps round to the last.
                .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> selected = (selected + gains.length - 1) % gains.length)
                // D-pad up / down: make the chosen gain bigger / smaller.
                .bindPress(Pad.G1, Btn.DPAD_UP, () -> changeGain(pid, true))
                .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> changeGain(pid, false));

        // Wait here until START is pressed.
        waitForStart();
        // A button held down while START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // try/finally: the "finally" part runs however the program ends, so the lift motor always stops.
        try {
            // The main loop: runs many times a second until STOP. Nothing in it waits.
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                // The PID work, every loop: where should it be, where is it now -> what power.
                // It must run every loop, even at the target, to HOLD the lift there against gravity.
                double power = pid.calculate(target, lift.getCurrentPosition());
                lift.setPower(power);

                // Everything you need to judge the tuning: is it getting there, overshooting, wobbling?
                TelemetryHelper.section(telemetry, "Lift");
                telemetry.addData("Target", target);
                telemetry.addData("Position", lift.getCurrentPosition());
                // Error = target minus position, in ticks, shown with no decimals.
                TelemetryHelper.value(telemetry, "Error", pid.getLastError(), 0);
                TelemetryHelper.value(telemetry, "Power", power, 2);
                TelemetryHelper.flag(telemetry, "At target", pid.atTarget());
                TelemetryHelper.section(telemetry, "Gains (D-pad)");
                // One line per gain. "a ? b : c" = if a is true use b, otherwise c: the chosen gain
                // gets a "> " in front. "%.5f" shows the number with 5 digits after the point.
                for (int i = 0; i < gains.length; i++) {
                    telemetry.addData((i == selected ? "> " : "  ") + GAIN_NAMES[i], "%.5f", gains[i]);
                }
                telemetry.update();
            }
        } finally {
            // SAFETY: stop the lift motor when the program ends, whatever the reason.
            lift.setPower(0);
        }
    }

    // Called by A / X / Y. Only sets the new height: the loop does the moving.
    private void newTarget(int ticks, PidController pid) {
        target = ticks;
        pid.reset();   // forget the old I and D memory
    }

    // Called by D-pad up (up = true) and down (up = false). Changes the chosen gain, then gives
    // the new gains to the PID controller, so you see the effect at once.
    private void changeGain(PidController pid, boolean up) {
        if (selected == 3) {
            // F is a fixed extra push (e.g. to hold the lift up), so it changes in plain steps.
            // Math.max(0, ...): F never goes below 0 (a negative F would push the lift DOWN).
            gains[3] = Math.max(0, gains[3] + (up ? 0.01 : -0.01));   // F: small steps up and down
        } else if (gains[selected] == 0) {
            // Multiplying 0 by 1.25 is still 0, so a gain at 0 first jumps to a small starting value:
            // 0.001 for P, 0.0001 for I and D. Down from 0 stays at 0.
            gains[selected] = up ? (selected == 0 ? 0.001 : 0.0001) : 0;   // start from a small value
        } else {
            // P, I, D change by 25% at a time (x1.25 up, x0.8 down), so small and large gains both
            // change by a sensible amount.
            gains[selected] *= up ? 1.25 : 0.8;
        }
        // Use the new gains from now on (this does not reset the controller).
        pid.setGains(gains[0], gains[1], gains[2], gains[3]);
    }
}
