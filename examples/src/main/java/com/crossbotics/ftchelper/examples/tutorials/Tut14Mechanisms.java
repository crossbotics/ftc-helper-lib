package com.crossbotics.ftchelper.examples.tutorials;

// "import" lines tell Java where to find the classes we use: FTC SDK classes (com.qualcomm...)
// and helper-library classes (com.crossbotics.ftchelper...).
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.mechanism.PositionMotor;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import com.crossbotics.ftchelper.mechanism.SpinMotor;
import com.crossbotics.ftchelper.mechanism.SpinServo;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;

/**
 * Tutorial 14: the four mechanism helpers. Each mechanism is a few lines of setup, and each
 * button is one line. Needs: motor "lift" (+ optional touch "lift_bottom"), servo "wrist",
 * motor "intake", continuous-rotation servo "spinner". Delete what you do not have.
 *
 * gamepad1: D-pad up/down = lift one position up/down, A/X/Y = lift DOWN/LOW/HIGH,
 *           left stick Y = lift by hand, LB/RB = wrist previous/next position,
 *           right trigger = intake in (hold), B = intake out (hold), D-pad right = spinner (hold).
 *
 * Before you use it: remove the {@code @Disabled} line, make the names in quotes ("lift",
 * "wrist"...) match your robot's configuration, and measure your own positions and powers.
 *
 * Tutorial: docs/tutorials/14-mechanisms.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list, with this name and group.
@TeleOp(name = "Tutorial 14: Mechanisms", group = "Tutorials")
// @Disabled hides it from the Driver Station. Delete this line to use the program.
@Disabled
// "extends LinearOpMode": this class IS an FTC program that runs top to bottom; we write runOpMode().
public class Tut14Mechanisms extends LinearOpMode {

    // @Override: this replaces the SDK's runOpMode(), where our program starts when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---------- Setup (once, in INIT) ----------

        // RobotTelemetry: the library's helper for the Driver Station screen. See docs/modules/telemetry.md.
        RobotTelemetry t = new RobotTelemetry(telemetry);

        // 1. A motor that goes to named positions (a lift).
        // Positions are encoder ticks (the encoder counts how far the motor has turned).
        // The motor's position when the program starts counts as 0, so start with the lift DOWN.
        // limits(0, 2600): it will never be driven below 0 or above 2600 ticks, so a bug or a
        //   wrong number cannot drive it into the end of its travel.
        // lowerSwitch: a touch sensor at the bottom. Pressing it resets the position to 0, which
        //   fixes counting errors. Until it is pressed once, the lift only goes DOWN, slowly.
        //   If "lift_bottom" is not in your configuration, the lift runs without a switch and
        //   telemetry shows a warning (then turn the robot on with the lift all the way down).
        // slowZone(250, 0.2): within 250 ticks of either end, power drops to at most 0.2 at the
        //   end, so it does not slam. (FTC also needs a physical hard stop; see docs/modules/safety.md.)
        // pid(kP, kI, kD, kF): how hard it pushes toward a position (tutorial 07 explains PID).
        //   0.05 is a small steady push up that holds the lift against gravity.
        // position("NAME", ticks): named heights the buttons can ask for. Measure YOUR lift.
        PositionMotor lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600)
                .lowerSwitch(hardwareMap, "lift_bottom")
                .slowZone(250, 0.2)
                .pid(0.004, 0, 0.0002, 0.05)
                .position("DOWN", 0)
                .position("LOW", 900)
                .position("HIGH", 2400);

        // 2. A servo with named positions (a wrist), moving slowly so it does not swing.
        // Servo positions go from 0 to 1 (one end of its turn to the other). Find yours by testing.
        // maxSpeed(1.5): at most 1.5 of that 0..1 range per second, so STOW to DROP (0.7) takes
        // about half a second instead of a sudden snap that could fling a game piece.
        PositionServo wrist = PositionServo.create(hardwareMap, "wrist")
                .position("STOW", 0.15)
                .position("LEVEL", 0.50)
                .position("DROP", 0.85)
                .maxSpeed(1.5);

        // 3. A motor that spins (an intake).
        // powers(in, out): full power (1.0) to pull pieces in; -0.7 to push them out, a bit
        // gentler so a piece is spat out, not fired.
        SpinMotor intake = SpinMotor.create(hardwareMap, "intake").powers(1.0, -0.7);

        // 4. A continuous-rotation servo (a small spinner).
        // A continuous-rotation servo spins round and round like a small motor, instead of going to
        // a position. power(0.8): it runs at 0.8 when told to go forward.
        SpinServo spinner = SpinServo.create(hardwareMap, "spinner").power(0.8);

        // One group: updated, shown and stopped together.
        // Then one line in the loop updates everything, and one line stops everything.
        Subsystems all = new Subsystems().add(lift, wrist, intake, spinner);

        // Buttons. Each line links one gamepad1 (G1) button to one action.
        // bindPress = once each time the button goes down; bindHold = every loop while it is held;
        // bindRelease = once when it is let go.
        // "lift::stepUp" is a method reference: a short way to write "() -> lift.stepUp()",
        // i.e. "call lift.stepUp() when the button is pressed".
        // "() -> lift.goTo("DOWN")" is a lambda: a small piece of code the button runs later.
        // These actions only CHOOSE a target; the moving happens in all.update() below, so no
        // button ever makes the robot wait. See docs/guides/non-blocking.md.
        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.DPAD_UP, lift::stepUp)          // next named height up
                .bindPress(Pad.G1, Btn.DPAD_DOWN, lift::stepDown)      // next named height down
                .bindPress(Pad.G1, Btn.A, () -> lift.goTo("DOWN"))
                .bindPress(Pad.G1, Btn.X, () -> lift.goTo("LOW"))
                .bindPress(Pad.G1, Btn.Y, () -> lift.goTo("HIGH"))
                .bindPress(Pad.G1, Btn.LB, wrist::previous)            // positions in the order added
                .bindPress(Pad.G1, Btn.RB, wrist::next)
                // Hold B = intake out; let go = intake stops.
                .bindHold(Pad.G1, Btn.B, intake::out)
                .bindRelease(Pad.G1, Btn.B, intake::stop)
                // Hold D-pad right = spinner runs; let go = it stops.
                .bindHold(Pad.G1, Btn.DPAD_RIGHT, spinner::forward)
                .bindRelease(Pad.G1, Btn.DPAD_RIGHT, spinner::stop);

        // Wait here until the driver presses START. Nothing moves before START (FTC rule).
        waitForStart();
        // A button held through START is ignored until it is let go, so nothing moves by surprise.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // Put the wrist in a known, safe position at the start.
        wrist.goTo("STOW");
        // try/finally: whatever happens in the loop (STOP, or a crash), "finally" still runs and
        // stops every mechanism. Safety first.
        try {
            // ---------- Every loop (many times per second until STOP) ----------
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);   // run the actions of pressed buttons
                // Lift by hand with the left stick. The minus sign makes stick UP = lift up (the
                // SDK gives a negative number for UP). 0.1 = ignore small stick wobble; 1 = no curve.
                // Any stick movement cancels a button's target; let go and the lift holds where it is.
                lift.manual(StickShaping.shape(-gamepad1.left_stick_y, 0.1, 1));
                // The intake has two inputs. While B (out) is held, B's bindings are in charge, so
                // we leave the intake alone. Otherwise the right trigger pressed past 0.2 = in,
                // released = stop. (in() is safe to call every loop: it only starts once.)
                if (!gamepad1.b) {
                    if (gamepad1.right_trigger > 0.2) intake.in(); else intake.stop();
                }
                all.update();            // every mechanism does its work for this loop
                all.addTelemetry(t);     // every mechanism adds its lines to the screen
                t.update();              // send the screen to the Driver Station
            }
        } finally {
            // Safety: when the program ends, for any reason, the lift, intake and spinner stop.
            // (A normal servo like the wrist just stays where it is.)
            all.stop();
        }
    }
}
