package io.github.jasoncross.ftchelper.examples.tutorials;

// "import" lines tell Java where to find the classes we use below: some come from the FTC SDK
// (com.qualcomm...), the rest from this helper library (io.github.jasoncross.ftchelper...).
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.util.ElapsedTime;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.drive.AutoDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.safety.SafeMotor;
import io.github.jasoncross.ftchelper.util.FieldPose;
import io.github.jasoncross.ftchelper.util.PoseStore;

/**
 * Tutorial 10: an autonomous that runs a mechanism while it drives, chooses its plan in INIT,
 * reacts to a stuck move, and hands its heading to TeleOp.
 *
 * In INIT: X = blue, B = red (the route mirrors), D-pad up/down = start delay.
 *
 * Before you use it on your robot:
 * - Remove the {@code @Disabled} line below, or the program will not show up on the Driver Station.
 * - The motor name "lift" must match the name in your robot's configuration (on the Driver Station).
 * - Fill in RobotSettings.java (drive motors, IMU, and the autonomous tuning from tutorial 09).
 * - Change the lift numbers (ticks, PID gains) to fit YOUR lift: tutorials 07 and 08 show how.
 *
 * Tutorial: docs/tutorials/10-auto-with-mechanisms.md
 */
// @Autonomous puts this program in the Driver Station's autonomous list, with this name and group.
@Autonomous(name = "Tutorial 10: Auto with Lift", group = "Tutorials")
// @Disabled hides it from the Driver Station. Delete this line to use the program.
@Disabled
// "extends LinearOpMode" means: this class IS an FTC program of the "linear" kind (it runs from top
// to bottom once, like a recipe). We only have to write runOpMode(); the SDK does the rest.
public class Tut10AutoWithLift extends LinearOpMode {

    // Lift positions in encoder ticks (an encoder counts how far the motor has turned; one "tick"
    // is one small step of that count). 0 = all the way down, 2400 = raised. Measure YOUR lift.
    // "static final" means: one fixed value that never changes while the program runs.
    static final int LIFT_DOWN = 0, LIFT_UP = 2400;

    // These are "fields": values that belong to the whole program, not just one method. They have
    // to be fields (not local values inside runOpMode) because the small button actions and the
    // loop hook below change or read them later.
    private boolean red = false;         // which alliance: false = blue, true = red
    private double delaySec = 0;         // how long to wait after START, seconds (0 to 10)
    private int liftTarget = LIFT_DOWN;  // where the lift should go, ticks. Changing it moves the lift.

    // @Override says "this replaces a method the SDK already has". runOpMode() is where the SDK
    // starts our program when INIT is pressed. Java checks the name is spelled right for us.
    @Override
    public void runOpMode() {
        // ---------- Setup (runs once, when INIT is pressed) ----------

        // Bulk reads: read all the sensors on each hub in one go per loop, instead of one at a time.
        // This makes every loop faster. Safe to always turn on. See docs/modules/hardware.md.
        BulkReads.auto(hardwareMap);
        // AutoDrive drives exact distances and turns using the wheel encoders and the IMU (the
        // hub's built-in sensor that knows which way the robot faces). Its settings live in
        // RobotSettings.java, so TeleOp and every autonomous share the same numbers.
        // "this" means "this program": AutoDrive needs it to check if STOP was pressed.
        AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(),
                RobotSettings.imu(hardwareMap));

        // The lift motor, named "lift" in the robot configuration. Step by step:
        // brake() = at power 0 the motor holds still instead of letting the lift fall freely;
        // resetEncoder() = the current position becomes 0, so START THE ROBOT WITH THE LIFT DOWN;
        // runWithoutEncoder() = we send power ourselves (our PID below decides it), the encoder
        // can still be read; done() = finish the setup and give us the motor.
        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        // SafeMotor wraps the motor so it can never be driven past its safe range.
        // setLimits(0, 2600): software stops at 0 and 2600 ticks (a bit above LIFT_UP), so a
        // wrong target or a bug cannot drive the lift into its top and break it.
        // setSlowZone(250, 0.2): within 250 ticks of either end, power is cut down to at most
        // 0.2 at the very end, so the lift does not slam into the stops.
        // FTC also needs a real, physical hard stop. See docs/modules/safety.md and tutorial 08.
        SafeMotor lift = new SafeMotor(liftMotor).setLimits(0, 2600).setSlowZone(250, 0.2);
        // PID: the math that works out how much power to send to reach a target and stay there
        // (tutorial 07 explains it step by step). The four numbers are kP, kI, kD and kF:
        // 0.004 = push harder the farther away we are; 0 = no I part; 0.0002 = brake a little as
        // we get close, so it does not overshoot; 0.05 = a small push always added, to hold the
        // lift up against gravity. setTolerance(20): within 20 ticks counts as "there".
        PidController liftPid = new PidController(0.004, 0, 0.0002, 0.05).setTolerance(20);

        // The lift keeps working DURING every drive move.
        // auto.move() and auto.turn() wait until the move is done, so nothing else in this method
        // runs meanwhile. The "loop hook" is code AutoDrive runs for us many times per second while
        // it waits, so the lift still moves to (and holds) liftTarget while the robot drives.
        // "() -> { ... }" is a lambda: a small piece of code we hand over now, to be run later.
        // It must be quick and never wait, or the drive would stall. See docs/guides/non-blocking.md.
        auto.setLoopHook(() -> {
            lift.update();                    // first: read the lift's switches/position
            lift.moveTo(liftTarget, liftPid); // then: PID power toward the target, limits applied
        });

        // ---------- INIT: choose the plan with the gamepad ----------
        // ControllerBindings links a gamepad button to an action. bindPress runs the action once,
        // each time the button goes down (holding it does not repeat). G1 = gamepad 1.
        // Each "() -> ..." is again a small piece of code that runs when that button is pressed.
        // The D-pad changes the delay by 1 second per press; Math.min/Math.max keep it between
        // 0 and 10 seconds, so a driver cannot set a silly delay by pressing too many times.
        ControllerBindings setup = new ControllerBindings()
                .bindPress(Pad.G1, Btn.X, () -> red = false)
                .bindPress(Pad.G1, Btn.B, () -> red = true)
                .bindPress(Pad.G1, Btn.DPAD_UP, () -> delaySec = Math.min(delaySec + 1, 10))
                .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> delaySec = Math.max(delaySec - 1, 0));
        // This loop runs over and over while the robot is in INIT (before START is pressed).
        // We use it instead of waitForStart() so the drivers can change the plan until START.
        while (opModeInInit()) {
            setup.update(gamepad1, gamepad2);   // read the buttons and run the matching actions
            // "red ? "RED" : "BLUE"" is a short if/else (a "ternary"): if red is true use "RED",
            // otherwise "BLUE". The drivers read this screen to check the choice before START.
            telemetry.addData("Alliance (X blue / B red)", red ? "RED" : "BLUE");
            telemetry.addData("Start delay (D-pad)", "%.0f s", delaySec);   // %.0f = no decimals
            telemetry.update();                 // send the lines to the Driver Station screen
        }
        // If STOP was pressed during INIT, end here without moving anything.
        if (isStopRequested()) return;

        // Red is the mirror image of blue: flip left/right directions and turns.
        // side is 1 for blue and -1 for red; multiply any turn or sideways move by it.
        double side = red ? -1 : 1;

        // try { ... } finally { ... }: whatever happens inside "try" (the route ends, STOP is
        // pressed, or the code crashes), the "finally" part ALWAYS runs afterwards. We use it to
        // make sure the lift motor is stopped and the heading is saved.
        try {
            // Start delay: wait WITHOUT sleep(), so STOP and telemetry keep working.
            // ElapsedTime is a stopwatch that starts when it is made. We check it every loop and
            // leave the loop when the delay has passed (or when STOP is pressed).
            ElapsedTime delay = new ElapsedTime();
            while (opModeIsActive() && delay.seconds() < delaySec) {
                telemetry.addData("Waiting", "%.1f s", delaySec - delay.seconds());   // time left
                telemetry.update();
                idle();   // give the rest of the robot software a moment before the next check
            }

            // Drive forward 30 inches (direction 0 = forward) at up to 0.6 power. The lift rises
            // at the same time, thanks to the loop hook. move() returns a Result that says HOW
            // the move ended: DONE, STALLED (stuck), TIMED_OUT or STOPPED.
            liftTarget = LIFT_UP;                          // start raising...
            AutoDrive.Result r = auto.move(30, 0, 0.6);    // ...while driving
            // STALLED means the wheels stopped making progress (for example the robot hit
            // another robot). Pushing on would waste time and could burn out the motors.
            if (r == AutoDrive.Result.STALLED) {
                auto.move(4, 180, 0.4);                    // blocked: back off and try once more
                auto.move(6, 0, 0.4);                      // (180 = backward, 0 = forward, inches)
            }
            // Turn 90 degrees. Positive = left (counterclockwise), so blue turns left and red
            // (side = -1) turns right, the mirror image. See docs/conventions.md for the signs.
            auto.turn(90 * side, 0.5);
            auto.move(12, 0, 0.5);                         // 12 inches forward at up to 0.5 power

            // Wait (with a time limit) until the lift is really up, still holding it.
            // We are not driving now, so the loop hook does not run: this loop does the lift's
            // work itself. The 2.0 second limit means a stuck lift cannot freeze the whole auto.
            ElapsedTime wait = new ElapsedTime();
            while (opModeIsActive() && !liftPid.atTarget() && wait.seconds() < 2.0) {
                lift.update();
                lift.moveTo(liftTarget, liftPid);
                idle();
            }

            // (Here a real auto would score: open a claw, run an intake...)
            liftTarget = LIFT_DOWN;
            auto.move(12, 180, 0.5);                       // the hook lowers the lift on the way
            // turnTo turns to a heading (the direction the robot faces), not BY an amount:
            // 0 = the way the robot faced at the start of autonomous.
            auto.turnTo(0, 0.5);
        } finally {
            // Safety: always stop the lift motor when autonomous ends, for any reason.
            lift.stop();
            // Save the heading so TeleOp knows which way the robot faces (needed for
            // field-centric driving). TeleOp can read this in INIT: PoseStore.consume()
            // We only know the heading here, so x and y are saved as 0. See docs/modules/util.md.
            PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
        }
    }
}
