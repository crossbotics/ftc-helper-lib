package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.control.SlewRateLimiter;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.gamepad.AimRumble;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;
import io.github.jasoncross.ftchelper.util.Angles;

/**
 * Tutorial 11: driver assists. Snap to a heading with the D-pad (PID on the IMU), feel a buzz
 * when the robot points the right way (AimRumble), and smooth starts (SlewRateLimiter).
 *
 * D-pad up/left/down/right = face 0 / 90 / 180 / -90 degrees (field). Moving the right stick
 * cancels the snap. Mecanum robot; for a standard drive use TankDrive.arcade the same way.
 *
 * Tutorial: docs/tutorials/11-driver-assists.md
 */
@TeleOp(name = "Tutorial 11: Heading Hold", group = "Tutorials")
@Disabled
public class Tut11HeadingHold extends LinearOpMode {

    private Double targetHeading = null;   // null = driver turns by hand

    @Override
    public void runOpMode() {
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());
        IMU imu = RobotSettings.imu(hardwareMap);

        PidController headingPid = new PidController(0.015, 0, 0.002)
                .setAngleMode(true)
                .setOutputLimits(-0.6, 0.6)
                .setTolerance(2);
        AimRumble aimFeel = new AimRumble(gamepad1).setThreshold(3);
        SlewRateLimiter forwardRamp = new SlewRateLimiter(2.5, 6.0);
        SlewRateLimiter strafeRamp = new SlewRateLimiter(2.5, 6.0);

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.DPAD_UP, () -> snapTo(0, headingPid))
                .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> snapTo(90, headingPid))
                .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> snapTo(180, headingPid))
                .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> snapTo(-90, headingPid))
                .bindPress(Pad.G1, Btn.BACK, imu::resetYaw);

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                double heading = ImuSetup.headingDeg(imu);

                double forward = forwardRamp.calculate(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2));
                double strafe = strafeRamp.calculate(StickShaping.shape(gamepad1.left_stick_x, 0.05, 2));
                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);

                if (turn != 0) targetHeading = null;           // the driver takes over
                if (targetHeading != null) {
                    // PID gives counterclockwise power; the drive's turn is clockwise: flip the sign.
                    turn = -headingPid.calculate(targetHeading, heading);
                    aimFeel.update(Angles.shortestDiff(targetHeading, heading));
                }
                drive.driveFieldCentric(forward, strafe, turn, heading);

                TelemetryHelper.value(telemetry, "Heading", heading, "deg");
                telemetry.addData("Snap to", targetHeading == null ? "off" : String.format("%.0f deg", targetHeading));
                TelemetryHelper.flag(telemetry, "Pointed right", targetHeading != null && headingPid.atTarget());
                telemetry.update();
            }
        } finally {
            drive.stop();
        }
    }

    private void snapTo(double degrees, PidController pid) {
        targetHeading = degrees;
        pid.reset();
    }
}
