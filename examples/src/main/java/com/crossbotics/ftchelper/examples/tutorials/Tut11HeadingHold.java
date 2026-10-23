package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.IMU;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.control.SlewRateLimiter;
import com.crossbotics.ftchelper.drive.MecanumDrive;
import com.crossbotics.ftchelper.gamepad.AimRumble;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.hardware.ImuSetup;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;
import com.crossbotics.ftchelper.util.Angles;

/**
 * Tutorial 11: driver assists. Snap to a heading with the D-pad (PID on the IMU), feel a buzz
 * when the robot points the right way (AimRumble), and smooth starts (SlewRateLimiter).
 * D-pad up/left/down/right = face 0 / 90 / 180 / -90 degrees. The right stick cancels the snap.
 * BACK = "the robot now faces away from me". Uses RobotSettings.java.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/11-driver-assists/
 */
@TeleOp(name = "Tutorial 11: Heading Hold", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut11HeadingHold extends LinearOpMode {

    private Double targetHeading = null;   // null = driver turns by hand

    @Override
    public void runOpMode() {
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());
        IMU imu = RobotSettings.imu(hardwareMap);

        // Angle mode: always turn the short way. At most 0.6 turn power. Within 2 degrees = aimed.
        PidController headingPid = new PidController(0.015, 0, 0.002)
                .setAngleMode(true)
                .setOutputLimits(-0.6, 0.6)
                .setTolerance(2);
        AimRumble aimFeel = new AimRumble(gamepad1).setThreshold(3);   // buzz within 3 degrees
        // Power may grow 2.5 per second and drop 6 per second: no jerk, still stops quickly.
        SlewRateLimiter forwardRamp = new SlewRateLimiter(2.5, 6.0);
        SlewRateLimiter strafeRamp = new SlewRateLimiter(2.5, 6.0);

        // Headings: positive = left (counterclockwise). BACK: face away from you first.
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
                controls.update(gamepad1, gamepad2);       // run the actions of pressed buttons
                double heading = ImuSetup.headingDeg(imu); // which way we face, degrees, left = +

                double forward = forwardRamp.calculate(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2));
                double strafe = strafeRamp.calculate(StickShaping.shape(gamepad1.left_stick_x, 0.05, 2));
                // Turning is not ramped: the driver needs quick turns.
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
            drive.stop();   // safety: wheels off when the program ends, for any reason
        }
    }

    /** Starts a snap to a heading (degrees, positive = left). Resets the PID's memory. */
    private void snapTo(double degrees, PidController pid) {
        targetHeading = degrees;
        pid.reset();
    }
}
