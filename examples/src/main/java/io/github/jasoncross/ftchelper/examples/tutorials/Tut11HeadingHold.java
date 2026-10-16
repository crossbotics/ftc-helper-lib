package io.github.jasoncross.ftchelper.examples.tutorials;

// "import" lines tell Java where to find the classes we use: FTC SDK classes (com.qualcomm...)
// and helper-library classes (io.github.jasoncross.ftchelper...).
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
 * BACK = "the robot now faces away from me": sets the heading to 0.
 *
 * Before you use it: remove the {@code @Disabled} line, and fill in RobotSettings.java so the
 * motor names and the IMU (hub) mounting match YOUR robot.
 *
 * Tutorial: docs/tutorials/11-driver-assists.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list, with this name and group.
@TeleOp(name = "Tutorial 11: Heading Hold", group = "Tutorials")
// @Disabled hides it from the Driver Station. Delete this line to use the program.
@Disabled
// "extends LinearOpMode": this class IS an FTC program that runs top to bottom; we write runOpMode().
public class Tut11HeadingHold extends LinearOpMode {

    // The heading (direction, degrees) the robot should turn to and hold, or "no target".
    // It is a field (belongs to the whole program) because the button actions change it.
    // "Double" with a capital D can hold a number OR null; plain "double" cannot be null.
    // We use null to mean "no snap: the driver turns by hand".
    private Double targetHeading = null;   // null = driver turns by hand

    // @Override: this replaces the SDK's runOpMode(), where our program starts when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---------- Setup (once, in INIT) ----------

        // The mecanum drive, with motor names and power limit from RobotSettings.java.
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());
        // The IMU: the sensor inside the hub that knows which way the robot faces.
        IMU imu = RobotSettings.imu(hardwareMap);

        // PID that turns the robot toward targetHeading (tutorial 07 explains PID).
        // 0.015 = turn power per degree of error, 0 = no I part, 0.002 = slows the turn near the
        // target so it does not swing past.
        // setAngleMode(true): headings wrap around (179 and -179 are only 2 degrees apart), so the
        // robot always turns the short way.
        // setOutputLimits(-0.6, 0.6): never more than 0.6 turn power, so a snap is not violent.
        // setTolerance(2): within 2 degrees counts as "pointed right".
        PidController headingPid = new PidController(0.015, 0, 0.002)
                .setAngleMode(true)
                .setOutputLimits(-0.6, 0.6)
                .setTolerance(2);
        // Buzzes gamepad 1 when the heading error is under 3 degrees: the driver FEELS that the
        // robot is aimed, without looking at the screen. Closer = stronger buzz.
        AimRumble aimFeel = new AimRumble(gamepad1).setThreshold(3);
        // Ramps (SlewRateLimiter) stop the drive power jumping all at once, so the robot does not
        // jerk or tip when the stick is slammed. 2.5 = power can grow by at most 2.5 per second
        // (0 to full in 0.4 s). 6.0 = it can drop by 6 per second (full to 0 in about 0.17 s),
        // so letting go of the stick still stops the robot quickly. One ramp per stick direction.
        SlewRateLimiter forwardRamp = new SlewRateLimiter(2.5, 6.0);
        SlewRateLimiter strafeRamp = new SlewRateLimiter(2.5, 6.0);

        // Button bindings: each D-pad press (once per press) sets a new heading to snap to.
        // Headings: 0 = the way the robot faced when the heading was last zeroed, positive = left
        // (counterclockwise). So D-pad LEFT = 90 = face left. See docs/conventions.md.
        // "() -> snapTo(0, headingPid)" is a lambda: a small piece of code the button runs later.
        // "imu::resetYaw" is a method reference: a short way to write "() -> imu.resetYaw()".
        // BACK resets the heading to 0: press it when the robot faces straight away from you.
        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.DPAD_UP, () -> snapTo(0, headingPid))
                .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> snapTo(90, headingPid))
                .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> snapTo(180, headingPid))
                .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> snapTo(-90, headingPid))
                .bindPress(Pad.G1, Btn.BACK, imu::resetYaw);

        // Wait here until the driver presses START.
        waitForStart();
        // A button held down while START was pressed is ignored until it is let go, so nothing
        // happens by surprise at the start.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // try/finally: whatever happens in the loop (STOP, or a crash), "finally" still runs and
        // stops the wheels. Safety first.
        try {
            // ---------- Every loop (many times per second until STOP) ----------
            // Nothing in this loop waits, so the robot reacts to the sticks at once.
            // See docs/guides/non-blocking.md.
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);       // run the actions of pressed buttons
                double heading = ImuSetup.headingDeg(imu); // which way we face, degrees, left = +

                // StickShaping.shape(value, 0.05, 2): ignore tiny stick values under 0.05 (a stick
                // rarely rests at exactly 0), then square the value so small pushes give fine,
                // slow control and a full push still gives full power.
                // The minus sign: pushing the stick UP gives a negative number in the SDK, and we
                // want up = forward. See docs/conventions.md.
                double forward = forwardRamp.calculate(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2));
                double strafe = strafeRamp.calculate(StickShaping.shape(gamepad1.left_stick_x, 0.05, 2));
                // Turning is not ramped: the driver needs quick turns.
                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);

                if (turn != 0) targetHeading = null;           // the driver takes over
                if (targetHeading != null) {
                    // PID gives counterclockwise power; the drive's turn is clockwise: flip the sign.
                    turn = -headingPid.calculate(targetHeading, heading);
                    // Buzz when close: shortestDiff is the error the short way round (-180 to 180).
                    aimFeel.update(Angles.shortestDiff(targetHeading, heading));
                }
                // Field-centric: stick up always drives away from the driver, whichever way the
                // robot faces. That is why it needs the heading.
                drive.driveFieldCentric(forward, strafe, turn, heading);

                // Telemetry: lines on the Driver Station screen.
                TelemetryHelper.value(telemetry, "Heading", heading, "deg");
                // Ternary "a ? b : c": if no target show "off", else show the target in degrees.
                telemetry.addData("Snap to", targetHeading == null ? "off" : String.format("%.0f deg", targetHeading));
                TelemetryHelper.flag(telemetry, "Pointed right", targetHeading != null && headingPid.atTarget());
                telemetry.update();
            }
        } finally {
            drive.stop();   // safety: wheels off when the program ends, for any reason
        }
    }

    /**
     * Starts a snap to a heading. Called by the D-pad buttons.
     *
     * @param degrees the heading to face, degrees (positive = left / counterclockwise)
     * @param pid     the heading PID; it is reset so old memory from the last snap is not used
     */
    private void snapTo(double degrees, PidController pid) {
        targetHeading = degrees;
        pid.reset();
    }
}
