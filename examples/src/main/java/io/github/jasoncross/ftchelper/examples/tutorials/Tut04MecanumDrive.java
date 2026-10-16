// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (io.github.jasoncross...).
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.drive.ReverseDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 04: a complete mecanum drive.
 * Left stick = move, right stick X = turn, left trigger = slow,
 * Y = field-centric on/off, BACK = "away from me is forward" (reset heading),
 * left stick click = reverse drive.
 *
 * Needs motors front_left, front_right, back_left, back_right and the hub's "imu".
 * Check the motor directions first with MotorDirectionCheck (docs/modules/drive.md).
 *
 * Tutorial: docs/tutorials/04-drive-mecanum.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Tutorial 04: Mecanum Drive", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom (see tutorial 01).
public class Tut04MecanumDrive extends LinearOpMode {

    // A "field" the Y button flips. false = robot-centric: "forward" on the stick is where the
    // robot's front points. true = field-centric: "forward" is away from the driver.
    private boolean fieldCentric = false;

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set up. Nothing moves until START. ----

        // Mecanum settings: motor names front_left, front_right, back_left, back_right, with the
        // left motors reversed. Change the names here if yours differ, e.g. driveConfig.frontLeft = "fl";
        DriveConfig driveConfig = DriveConfig.mecanum();
        // At most 60% power while you test a new drive: slower is safer if something is wrong.
        driveConfig.powerLimit = 0.6;               // raise this after the robot drives correctly
        // Finds the four motors and sets them up. Stops with a clear message if a name is wrong.
        MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);

        // The IMU: the hub's built-in turn sensor, needed for field-centric driving. Say which way
        // the hub's logo and USB port face on YOUR robot (docs/modules/hardware.md has a picture).
        IMU imu = ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);

        // Reverse drive: the robot's back acts as its front (handy when the driver faces the back).
        ReverseDrive reverse = new ReverseDrive();
        // Buzzes gamepad1 to confirm a switch: two buzzes = ON, one buzz = OFF.
        ToggleRumble confirm = new ToggleRumble(gamepad1);
        // The buttons (tutorial 03). "() -> ..." is a lambda: code run LATER, when the button is pressed.
        ControllerBindings controls = new ControllerBindings()
                // Y: field-centric on/off, with a buzz.
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { fieldCentric = true; confirm.play(true); },
                        () -> { fieldCentric = false; confirm.play(false); })
                // BACK: "the way the robot faces now is forward". Point the robot away from you first.
                // "imu::resetYaw" is a method reference: "run imu.resetYaw() when BACK is pressed".
                .bindPress(Pad.G1, Btn.BACK, imu::resetYaw)
                // Left stick click: reverse on/off. toggle() gives back the new state for the buzz.
                .bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()));

        // Wait here until START is pressed.
        waitForStart();
        // A button held down while START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // try/finally: the "finally" part runs however the program ends, so the wheels always stop.
        try {
            // The main loop: runs many times a second until STOP. Nothing in it waits.
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);

                // shape(stick, 0.05, 2): ignore tiny values under 0.05 (a resting stick is rarely exactly
                // 0, and the robot must not creep), then "square" the rest: small pushes give fine control,
                // a full push still gives full power.
                // The minus sign: stick Y reads NEGATIVE when pushed up, and up = forward (docs/conventions.md).
                double forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
                double strafe = StickShaping.shape(gamepad1.left_stick_x, 0.05, 2);
                // Positive turn = clockwise (right), the same way the stick is pushed.
                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                // Left trigger = slow: not pressed = full speed (1.0), squeezed all the way = 30%.
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
                // Which way the robot faces, in degrees. Turning left makes it bigger.
                double heading = ImuSetup.headingDeg(imu);

                if (fieldCentric) {
                    // Field-centric uses the heading to turn "away from the driver" into robot directions.
                    // (Reverse drive is not used here: in field-centric, the robot's front does not matter.)
                    drive.driveFieldCentric(forward, strafe, turn, heading, speed);
                } else {
                    // Robot-centric. Reverse flips forward and sideways only; turning stays the same.
                    drive.drive(reverse.apply(forward), reverse.apply(strafe), turn, speed);
                }

                // Show the modes and numbers on the Driver Station.
                TelemetryHelper.section(telemetry, "Drive");
                // "a ? b : c" = if a is true use b, otherwise c.
                telemetry.addData("Mode", fieldCentric ? "field-centric" : "robot-centric");
                TelemetryHelper.flag(telemetry, "Reversed", reverse.isReversed());
                TelemetryHelper.value(telemetry, "Heading", heading, "deg");
                TelemetryHelper.value(telemetry, "Speed", speed, 2);
                telemetry.update();
            }
        } finally {
            // SAFETY: stop the wheels when the program ends, whatever the reason.
            drive.stop();
        }
    }
}
