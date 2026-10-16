// The "package" line says which folder this file lives in. When you copy this file into your
// TeamCode folder, change it to match (Android Studio usually offers to fix it for you).
package io.github.jasoncross.ftchelper.examples;

// "import" lines tell Java which ready-made pieces we use in this file, and where to find them.
// The com.qualcomm... ones come from the FTC SDK; the io.github.jasoncross... ones are this library.
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.TouchSensor;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.drive.ReverseDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.hardware.Hw;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.safety.SafeMotor;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;
import io.github.jasoncross.ftchelper.util.LoopTimer;

/**
 * A full TeleOp built with ftc-helper-lib. Copy it into TeamCode, change the names, and
 * remove the {@code @Disabled} line.
 *
 * Driver (gamepad1):  left stick = move, right stick X = turn, left trigger = slow,
 *                     Y = field-centric on/off, left stick click = reverse drive,
 *                     BACK = reset heading (face away from you first).
 * Operator (gamepad2): left stick Y = lift by hand, A = lift down, X = lift middle, Y = lift up.
 *                     With a "lift_bottom" switch, push the lift down once at the start to home it.
 *
 * Needs (names in the Driver Station configuration): motors front_left, front_right, back_left,
 * back_right and lift; the hub's built-in "imu"; optional touch sensor "lift_bottom".
 *
 * Tutorials: docs/tutorials/04-drive-mecanum.md and 08-safe-lift.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list, under this name and group.
// Change the name to something your drivers will recognise.
@TeleOp(name = "Example: Helper TeleOp", group = "Examples")
// @Disabled HIDES this program from the Driver Station list. Delete this line in your copy,
// or you will not see your program.
@Disabled
// "extends LinearOpMode" means: this class is an FTC program (an OpMode) that runs from top to
// bottom, like a list of steps. The SDK gives us hardwareMap, gamepad1, gamepad2 and telemetry.
public class ExampleTeleOp extends LinearOpMode {

    // Lift positions in encoder ticks. Measure these on your robot.
    // (An encoder counts how far the motor shaft has turned; one count is a "tick".
    //  Move the lift with this program running and read the lift's position on the screen.)
    // "static final" = fixed numbers that belong to the program itself and never change.
    // LIFT_MAX is the highest the software ever lets the lift go: keep it a little below the top.
    static final int LIFT_DOWN = 0, LIFT_MIDDLE = 1200, LIFT_UP = 2600, LIFT_MAX = 2900;

    // These are "fields": values that belong to the whole program, so the button actions
    // below can change them and the loop can read them. "private" = only this file uses them.
    // Field-centric drive starts OFF (robot-centric: "forward" is where the robot faces).
    private boolean fieldCentric = false;
    // Where the lift is going, in ticks. "Integer" (not "int") so it can also be null = "no target".
    private Integer liftTarget = null;   // null = driver moves the lift by hand

    // @Override says "this replaces a method the SDK already has". The SDK calls runOpMode()
    // when the driver presses INIT. Everything below runs from that moment.
    @Override
    public void runOpMode() {
        // ---------- INIT: set up hardware ----------
        // Nothing moves during INIT: we only find the hardware and get it ready.

        // Read all sensors and encoders from the hub in one message instead of one at a time.
        // This makes the loop much faster. See docs/modules/hardware.md.
        BulkReads.auto(hardwareMap);

        // Drive settings: start from the standard mecanum settings (motor names front_left,
        // front_right, back_left, back_right; left motors reversed), then change what differs.
        DriveConfig driveConfig = DriveConfig.mecanum();
        // Never send more than 80% power to a wheel: gentler on the robot and easier to control.
        // Raise it toward 1.0 once the drivers are comfortable.
        driveConfig.powerLimit = 0.8;
        // Finds the four wheel motors and sets them up. Stops with a clear message if a name is wrong.
        MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);

        // The IMU is the hub's built-in turn sensor; it tells us which way the robot faces.
        // You MUST say which way the hub's logo and USB port face on YOUR robot, or the heading
        // will be wrong. See docs/modules/hardware.md for a picture.
        IMU imu = ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);

        // The lift motor, set up in one chain:
        //   brake()             = resists turning at power 0, so the lift does not drop by itself;
        //   resetEncoder()      = this spot counts as 0 ticks (so turn the robot on with the lift DOWN);
        //   runWithoutEncoder() = our code chooses the power (the PID below does the position work);
        //   done()              = sets power to 0 and hands us the motor.
        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        // SafeMotor wraps the lift motor so it cannot be driven past its safe range, even by mistake:
        //   setLimits(0, LIFT_MAX) = software stops at 0 and LIFT_MAX ticks;
        //   setSlowZone(200, 0.25) = within 200 ticks of either end, power is cut, down to 25% right
        //                            at the end, so the lift does not slam into its stops.
        // Software limits are not enough on their own: the lift also needs a real, physical stop.
        // See docs/tutorials/08-safe-lift.md.
        SafeMotor lift = new SafeMotor(liftMotor)
                .setLimits(0, LIFT_MAX)
                .setSlowZone(200, 0.25);
        // An OPTIONAL bottom switch. tryGet gives null (= "nothing") if your robot has no
        // "lift_bottom" in its configuration, so this program also works without one.
        TouchSensor liftBottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
        // If there is a switch, the lift uses it: a pressed switch stops downward power and resets
        // the count to 0. Until it has been pressed once, the lift only goes down, slowly ("homing").
        // "liftBottom::isPressed" is a method reference: it hands over "how to read the switch",
        // so SafeMotor can check it itself every loop.
        if (liftBottom != null) lift.setLowerSwitch(liftBottom::isPressed);
        // The PID controller works out the lift power that gets it to a target height.
        // 0.004 = P (push harder when far away), 0 = I (not used), 0.0002 = D (calms the push so
        // it does not overshoot). Tolerance 20 = within 20 ticks counts as "there".
        // These numbers are a starting point: tune them on your robot (docs/tutorials/07-pid-lift.md).
        PidController liftPid = new PidController(0.004, 0, 0.0002).setTolerance(20);

        // ---------- INIT: say what each button does ----------
        // ReverseDrive flips forward and sideways, so the back of the robot acts as the front.
        ReverseDrive reverse = new ReverseDrive();
        // Buzzes gamepad1 to confirm a switch: two buzzes = ON, one buzz = OFF. The driver knows
        // without looking at the screen.
        ToggleRumble confirm = new ToggleRumble(gamepad1);
        // All buttons in one place. Each "bind..." line says: on this gamepad, this button runs this.
        // "() -> { ... }" is a lambda: a small piece of code we hand over to be run LATER, when the
        // button is pressed (not now). Button actions must be quick: never wait inside one.
        // Buttons are explained in docs/tutorials/03-buttons.md.
        ControllerBindings controls = new ControllerBindings()
                // Driver Y: field-centric drive on/off. Each press flips it (it starts OFF).
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { fieldCentric = true; confirm.play(true); },
                        () -> { fieldCentric = false; confirm.play(false); })
                // Driver left stick click: reverse drive on/off. toggle() gives back the new state,
                // so the buzz says whether it is now ON or OFF.
                .bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()))
                // Driver BACK: "the way the robot faces now is 0 degrees". Point the robot AWAY
                // from the driver first, so field-centric "forward" is away from the driver.
                // "imu::resetYaw" is a method reference: "run imu.resetYaw() when pressed".
                .bindPress(Pad.G1, Btn.BACK, imu::resetYaw)
                // Operator A / X / Y: send the lift to the down / middle / up height.
                .bindPress(Pad.G2, Btn.A, () -> setLiftTarget(LIFT_DOWN, liftPid))
                .bindPress(Pad.G2, Btn.X, () -> setLiftTarget(LIFT_MIDDLE, liftPid))
                .bindPress(Pad.G2, Btn.Y, () -> setLiftTarget(LIFT_UP, liftPid));

        // Measures how many loops per second we run, to spot a slow program.
        LoopTimer loopTimer = new LoopTimer();
        // Tell the drivers INIT is done.
        telemetry.addLine("Ready. Press START.");
        telemetry.update();

        // The program waits here until the driver presses START (or STOP).
        waitForStart();
        // A button held down while START was pressed must not count as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        // ---------- RUN: every loop ----------
        // try/finally: whatever happens inside "try" (STOP pressed, or an error), the "finally"
        // part at the bottom still runs, so the motors are always stopped.
        try {
            // Repeat until the match ends or STOP is pressed. Each trip round is one "loop".
            // Nothing in here waits: the robot must react to the sticks many times a second.
            while (opModeIsActive()) {
                loopTimer.update();
                // Read the gamepads and run the actions of any button just pressed.
                controls.update(gamepad1, gamepad2);
                // Let the lift read its switch first (it must come BEFORE setPower or moveTo).
                lift.update();

                // Drive
                // shape(value, 0.05, 2): ignore stick values under 0.05 (a stick at rest is rarely
                // exactly 0, and the robot should not creep), then "square" the rest so small pushes
                // give fine control and a full push still gives full power.
                // Stick Y is NEGATIVE when pushed up, so the minus sign makes "up" = forward
                // (docs/conventions.md). reverse.apply flips the value when reverse drive is on.
                double forward = reverse.apply(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2));
                double strafe = reverse.apply(StickShaping.shape(gamepad1.left_stick_x, 0.05, 2));
                // Turning is not reversed: pushing right still turns the robot right (clockwise).
                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                // Left trigger = slow mode: not pressed = full speed (1.0), fully pressed = 30%.
                // Good for lining up exactly.
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
                if (fieldCentric) {
                    // Field-centric: "forward" is away from the driver, whichever way the robot faces.
                    // It needs the robot's heading from the IMU.
                    drive.driveFieldCentric(forward, strafe, turn, ImuSetup.headingDeg(imu), speed);
                } else {
                    // Robot-centric: "forward" is where the robot faces.
                    drive.drive(forward, strafe, turn, speed);
                }

                // Lift: a stick push takes over from the buttons.
                // Deadband 0.1 (bigger than the drive's) so a slightly bumped stick does not cancel a
                // button move. Exponent 1 = no curve: the lift power follows the stick directly.
                double liftStick = StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1);
                // Any stick push cancels the button target: the operator takes over by hand.
                if (liftStick != 0) liftTarget = null;
                if (liftTarget == null) {
                    // By hand. Stick at rest = power 0, and the brake holds the lift where it is.
                    // SafeMotor still applies the limits and the slow zone.
                    lift.setPower(liftStick);
                } else {
                    // Going to a button's height. Called every loop: the PID works out a new power
                    // each time, and SafeMotor still keeps it inside the limits.
                    lift.moveTo(liftTarget, liftPid);
                }

                // Telemetry: the text on the Driver Station screen. Helps drivers and helps you find problems.
                TelemetryHelper.section(telemetry, "Drive");
                // "a ? b : c" means: if a is true use b, otherwise use c.
                telemetry.addData("Mode", "%s%s", fieldCentric ? "field-centric" : "robot-centric",
                        reverse.isReversed() ? ", REVERSED" : "");
                TelemetryHelper.value(telemetry, "Heading", ImuSetup.headingDeg(imu), "deg");
                TelemetryHelper.section(telemetry, "Lift");
                // The lift motor's details, including its position in ticks: use it to measure LIFT_UP etc.
                TelemetryHelper.motor(telemetry, "Lift", liftMotor);
                telemetry.addData("Target", liftTarget == null ? "by hand" : liftTarget.toString());
                TelemetryHelper.flag(telemetry, "At bottom", lift.isAtLower());
                // Loops per second. If this gets low, the robot feels slow to react.
                TelemetryHelper.value(telemetry, "Loop", loopTimer.getHz(), "Hz");
                // Nothing appears on the screen until update() sends it.
                telemetry.update();
            }
        } finally {
            // SAFETY: always stop every motor when the program ends, even after an error,
            // so nothing keeps running on its own.
            drive.stop();
            lift.stop();
        }
    }

    // Called by the operator's A / X / Y buttons. It only sets the new target: the loop above
    // does the moving. (A button action must be quick and never wait.)
    private void setLiftTarget(int ticks, PidController pid) {
        liftTarget = ticks;
        // Clear the PID's memory from the last move, so the new move starts fresh.
        pid.reset();
    }
}
