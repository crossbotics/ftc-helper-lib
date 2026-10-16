// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials.robot;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (io.github.jasoncross...).
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.examples.tutorials.RobotSettings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import io.github.jasoncross.ftchelper.util.FieldPose;
import io.github.jasoncross.ftchelper.util.PoseStore;

/**
 * Tutorial 12: a short TeleOp, because the Robot and subsystem classes do the work.
 * Driver: drive (field-centric), BACK = reset heading. Operator: D-pad up/down = lift step,
 * A = bottom, Y = high, right bumper = claw, left stick Y = lift by hand.
 *
 * Tutorial: docs/tutorials/12-robot-structure.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Tutorial 12: Main TeleOp", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom (see tutorial 01).
public class MainTeleOp extends LinearOpMode {

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set up. Nothing moves until START. ----

        // The lift, the claw and the IMU, from Robot.java (the same class autonomous uses).
        // Note: setting up the IMU makes the way the robot faces NOW heading 0.
        Robot robot = new Robot(hardwareMap);
        // The wheels, with the settings from RobotSettings.java.
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());

        // If autonomous saved a pose, it is still there. This TeleOp only shows it.
        // (With odometry you would set your position from it.)
        // Careful: the IMU was set up again above, so its heading now starts at 0 from where the robot
        // faces in TeleOp INIT, not from where autonomous started. Face the robot away from you and
        // press BACK if field-centric "forward" looks wrong.
        // consume() gives the saved pose and forgets it, so an old one is never used twice.
        FieldPose fromAuto = PoseStore.consume();

        // The buttons. "() -> ..." is a lambda: code run LATER, when the button is pressed.
        // "robot.lift::stepUp" is a method reference: "run robot.lift.stepUp() when pressed".
        ControllerBindings controls = new ControllerBindings()
                // Driver BACK: "the way the robot faces now is forward". Point it away from you first.
                .bindPress(Pad.G1, Btn.BACK, robot.imu::resetYaw)
                // Operator D-pad: one set height up / down.
                .bindPress(Pad.G2, Btn.DPAD_UP, robot.lift::stepUp)
                .bindPress(Pad.G2, Btn.DPAD_DOWN, robot.lift::stepDown)
                // Operator A / Y: straight to the bottom / the top.
                .bindPress(Pad.G2, Btn.A, () -> robot.lift.goTo(LiftHeights.BOTTOM))
                .bindPress(Pad.G2, Btn.Y, () -> robot.lift.goTo(LiftHeights.HIGH))
                // Operator right bumper: claw open/close.
                .bindPress(Pad.G2, Btn.RB, robot.claw::toggle);

        // "a ? b : c" = if a is true use b, otherwise c.
        telemetry.addData("From auto", fromAuto == null ? "nothing" : fromAuto.toString());
        telemetry.update();
        // Wait here until START is pressed.
        waitForStart();
        // A button held down while START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // try/finally: the "finally" part runs however the program ends, so everything stops.
        try {
            // The main loop: runs many times a second until STOP. Nothing in it waits.
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                // The operator's stick moves the lift by hand. Deadband 0.1 so a bumped stick does not
                // cancel a set height; exponent 1 = no curve. Minus: stick up reads negative.
                robot.lift.manual(StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1));
                // The lift does its work (switch, PID or hand power), once per loop.
                robot.update();

                // Field-centric drive: "forward" on the stick is away from the driver.
                // shape(stick, 0.05, 2) ignores tiny stick values and "squares" the rest for fine control.
                // Left trigger = slow: not pressed = full speed, squeezed all the way = 30%.
                drive.driveFieldCentric(
                        StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
                        StickShaping.shape(gamepad1.left_stick_x, 0.05, 2),
                        StickShaping.shape(gamepad1.right_stick_x, 0.05, 2),
                        ImuSetup.headingDeg(robot.imu),
                        StickShaping.slowScale(gamepad1.left_trigger, 0.3));

                robot.addTelemetry(telemetry);
                telemetry.update();
            }
        } finally {
            // SAFETY: stop the wheels and the mechanisms when the program ends, whatever the reason.
            drive.stop();
            robot.stop();
        }
    }
}
