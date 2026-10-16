// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials.robot;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (io.github.jasoncross...).
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;
import io.github.jasoncross.ftchelper.drive.AutoDrive;
import io.github.jasoncross.ftchelper.examples.tutorials.RobotSettings;
import io.github.jasoncross.ftchelper.util.FieldPose;
import io.github.jasoncross.ftchelper.util.PoseStore;

/**
 * Tutorial 12: the autonomous uses the SAME Robot class as TeleOp.
 * Close the claw on a game piece, raise the lift while driving, drop it, come back.
 *
 * Before it runs: tune with Tut09DriveTuner and set calibrationConfirmed to true in
 * RobotSettings.auto(). Until then it stops in INIT with a message, on purpose.
 *
 * Tutorial: docs/tutorials/12-robot-structure.md
 */
// @Autonomous puts this program in the Driver Station's Autonomous list with this name.
@Autonomous(name = "Tutorial 12: Main Auto", group = "Tutorials")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs its steps from top to bottom, in order.
public class MainAuto extends LinearOpMode {

    // A "field", so the helper methods at the bottom (waitForLift, pause) can use the robot too.
    private Robot robot;

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: set up. Nothing moves until START. ----

        // All the mechanisms and the IMU, from Robot.java (the same class TeleOp uses).
        robot = new Robot(hardwareMap);
        // AutoDrive does the measured moves, with the settings from RobotSettings.java.
        // "this" = this program, so AutoDrive can notice STOP.
        AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(), robot.imu);
        // A move waits until it is finished, so without this the lift would get no updates (no PID)
        // while the robot drives. The loop hook runs robot.update() again and again during every move.
        // "robot::update" is a method reference: "here is the code to run each time".
        auto.setLoopHook(robot::update);      // the lift keeps working during every move

        // Wait here until the driver presses START.
        waitForStart();
        // try/finally: the "finally" part runs however autonomous ends (all done, STOP, or an error).
        try {
            // Grab the piece we start with.
            robot.claw.close();
            // Only SETS the target: the lift rises during the next move, thanks to the loop hook.
            robot.lift.goTo(LiftHeights.HIGH);
            // 30 inches forward (direction 0) at 60% power.
            auto.move(30, 0, 0.6);
            // The lift may still be rising: wait for it, but at most 2 seconds, so a stuck lift
            // cannot use up the whole autonomous.
            waitForLift(2.0);
            robot.claw.open();
            pause(0.3);                        // let the piece fall
            // Start the lift down; it keeps coming down during the drive back.
            robot.lift.goTo(LiftHeights.BOTTOM);
            // 30 inches back (direction 180), then face the way we faced at the start (heading 0).
            auto.move(30, 180, 0.6);
            auto.turnTo(0, 0.5);
        } finally {
            // SAFETY: stop the mechanisms (the wheels stop by themselves at the end of every move).
            robot.stop();
            // Save the heading for TeleOp (x and y are 0: this example does not track position).
            PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
        }
    }

    /** Keeps the lift working until it reaches its target, or the time runs out. */
    // "private" = only this file uses it. ElapsedTime is the SDK's stopwatch: it starts at 0 when made.
    private void waitForLift(double maxSeconds) {
        ElapsedTime timer = new ElapsedTime();
        // Repeat while: not stopped, AND the lift is not there yet, AND there is time left.
        // ("&&" means "and"; "!" means "not".)
        while (opModeIsActive() && !robot.lift.isAtTarget() && timer.seconds() < maxSeconds) {
            // Keep the lift's PID working while we wait.
            robot.update();
            // Let the robot app do its own work for a moment.
            idle();
        }
    }

    /**
     * Waits without letting go of the lift. Use this instead of sleep() whenever a mechanism
     * must keep holding its position (sleep() stops all your code, including the lift's PID).
     */
    private void pause(double seconds) {
        ElapsedTime timer = new ElapsedTime();
        while (opModeIsActive() && timer.seconds() < seconds) {
            robot.update();
            idle();
        }
    }
}
