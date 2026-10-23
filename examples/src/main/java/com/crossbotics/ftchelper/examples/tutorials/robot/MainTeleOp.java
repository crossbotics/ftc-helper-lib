// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials.robot;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.drive.DriverHeading;
import com.crossbotics.ftchelper.drive.MecanumDrive;
import com.crossbotics.ftchelper.examples.tutorials.RobotSettings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;

/**
 * Tutorial 12: a short TeleOp, because the Robot and subsystem classes do the work.
 * Driver: drive, Y = field-centric on/off, BACK = reset heading. Operator: D-pad up/down = lift
 * step, A = bottom, Y = high, right bumper = claw, left stick Y = lift by hand.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/12-robot-structure/#5-teleop-becomes-short
 */
@TeleOp(name = "Tutorial 12: Main TeleOp", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class MainTeleOp extends LinearOpMode {

    private boolean fieldCentric = false;   // Y switches

    @Override
    public void runOpMode() {
        Robot robot = new Robot(hardwareMap);   // the IMU setup makes the way the robot faces NOW heading 0
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());

        // Continues from the heading autonomous saved, so field-centric "forward" is right at once.
        DriverHeading heading = new DriverHeading(robot.imu);

        ControllerBindings controls = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.Y, () -> fieldCentric = true, () -> fieldCentric = false)
                // BACK: "where the robot faces now is forward". Point it away from you first.
                .bindPress(Pad.G1, Btn.BACK, heading::reset)
                .bindPress(Pad.G2, Btn.DPAD_UP, robot.lift::stepUp)
                .bindPress(Pad.G2, Btn.DPAD_DOWN, robot.lift::stepDown)
                .bindPress(Pad.G2, Btn.A, () -> robot.lift.goTo(LiftHeights.BOTTOM))
                .bindPress(Pad.G2, Btn.Y, () -> robot.lift.goTo(LiftHeights.HIGH))
                .bindPress(Pad.G2, Btn.RB, robot.claw::toggle);

        telemetry.addData("Heading", heading.isFromAuto() ? "from autonomous" : "0 = the way the robot faces now");
        telemetry.update();
        waitForStart();
        heading.forgetSaved();   // a later TeleOp run must not use the saved heading again
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                // Deadband 0.1: a bumped stick does not cancel a set height.
                robot.lift.manual(StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1));
                robot.update();

                double forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
                double strafe = StickShaping.shape(gamepad1.left_stick_x, 0.05, 2);
                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);   // full pull = 30%
                if (fieldCentric) drive.driveFieldCentric(forward, strafe, turn, heading.degrees(), speed);
                else drive.drive(forward, strafe, turn, speed);

                robot.addTelemetry(telemetry);
                telemetry.update();
            }
        } finally {
            drive.stop();
            robot.stop();
        }
    }
}
