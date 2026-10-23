package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
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
 * gamepad1: D-pad up/down, A/X/Y, left stick = lift; LB/RB = wrist; RT / B (hold) = intake in / out;
 * D-pad right (hold) = spinner. The names in quotes = the names in the robot configuration.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/14-mechanisms/
 */
@TeleOp(name = "Tutorial 14: Mechanisms", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut14Mechanisms extends LinearOpMode {

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);

        // 1. A lift. Ticks: measure YOURS. Start with the lift DOWN (0 = where it is at INIT).
        PositionMotor lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600)                          // never below 0 or above 2600 ticks
                .lowerSwitch(hardwareMap, "lift_bottom")  // stops at the bottom and sets 0 there
                .slowZone(250, 0.2)                       // gentle near the ends
                .pid(0.004, 0, 0.0002, 0.05)              // from tutorial 07
                .position("DOWN", 0)
                .position("LOW", 900)
                .position("HIGH", 2400);

        // 2. A wrist. Servo positions 0 to 1: find yours by testing.
        PositionServo wrist = PositionServo.create(hardwareMap, "wrist")
                .position("STOW", 0.15)
                .position("LEVEL", 0.50)
                .position("DROP", 0.85)
                .maxSpeed(1.5);                           // at most 1.5 positions per second: no swinging

        // 3. An intake: in at full power, out a bit gentler.
        SpinMotor intake = SpinMotor.create(hardwareMap, "intake").powers(1.0, -0.7);

        // 4. A continuous-rotation servo.
        SpinServo spinner = SpinServo.create(hardwareMap, "spinner").power(0.8);

        // One group: updated, shown and stopped together.
        Subsystems all = new Subsystems().add(lift, wrist, intake, spinner);

        // The actions only CHOOSE a target: all.update() does the moving, so no button waits.
        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.DPAD_UP, lift::stepUp)          // next named height up
                .bindPress(Pad.G1, Btn.DPAD_DOWN, lift::stepDown)      // next named height down
                .bindPress(Pad.G1, Btn.A, () -> lift.goTo("DOWN"))
                .bindPress(Pad.G1, Btn.X, () -> lift.goTo("LOW"))
                .bindPress(Pad.G1, Btn.Y, () -> lift.goTo("HIGH"))
                .bindPress(Pad.G1, Btn.LB, wrist::previous)            // positions in the order added
                .bindPress(Pad.G1, Btn.RB, wrist::next)
                .bindHold(Pad.G1, Btn.B, intake::out)
                .bindRelease(Pad.G1, Btn.B, intake::stop)
                .bindHold(Pad.G1, Btn.DPAD_RIGHT, spinner::forward)
                .bindRelease(Pad.G1, Btn.DPAD_RIGHT, spinner::stop);

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        wrist.goTo("STOW");   // a known, safe position at the start
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);   // run the actions of pressed buttons
                // Any stick push cancels a button's target; let go and the lift holds.
                lift.manual(StickShaping.shape(-gamepad1.left_stick_y, 0.1, 1));
                // While B (out) is held, B's bindings are in charge of the intake.
                if (!gamepad1.b) {
                    if (gamepad1.right_trigger > 0.2) intake.in(); else intake.stop();
                }
                all.update();             // every loop: PID, limits, slow moves, jam checks
                all.addTelemetry(t);      // each part adds its own lines
                t.update();              // send the screen to the Driver Station
            }
        } finally {
            all.stop();               // in finally
        }
    }
}
