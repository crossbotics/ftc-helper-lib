package io.github.jasoncross.ftchelper.examples.tutorials.robot;

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
 */
@TeleOp(name = "Tutorial 12: Main TeleOp", group = "Tutorials")
@Disabled
public class MainTeleOp extends LinearOpMode {

    @Override
    public void runOpMode() {
        Robot robot = new Robot(hardwareMap);
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());

        // If autonomous saved a pose, it is still there. Its heading is the IMU's, so nothing to do
        // here except show it. (With odometry you would set your position from it.)
        FieldPose fromAuto = PoseStore.consume();

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.BACK, robot.imu::resetYaw)
                .bindPress(Pad.G2, Btn.DPAD_UP, robot.lift::stepUp)
                .bindPress(Pad.G2, Btn.DPAD_DOWN, robot.lift::stepDown)
                .bindPress(Pad.G2, Btn.A, () -> robot.lift.goTo(LiftHeights.BOTTOM))
                .bindPress(Pad.G2, Btn.Y, () -> robot.lift.goTo(LiftHeights.HIGH))
                .bindPress(Pad.G2, Btn.RB, robot.claw::toggle);

        telemetry.addData("From auto", fromAuto == null ? "nothing" : fromAuto.toString());
        telemetry.update();
        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                robot.lift.manual(StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1));
                robot.update();

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
            drive.stop();
            robot.stop();
        }
    }
}
