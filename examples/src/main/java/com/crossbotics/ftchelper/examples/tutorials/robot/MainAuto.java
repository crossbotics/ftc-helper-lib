// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials.robot;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.crossbotics.ftchelper.drive.AutoDrive;
import com.crossbotics.ftchelper.examples.tutorials.RobotSettings;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.util.PoseStore;

/**
 * Tutorial 12: the autonomous uses the SAME Robot class as TeleOp.
 * Close the claw on a game piece, raise the lift while driving, drop it, come back.
 * First tune with Tut09DriveTuner and set calibrationConfirmed = true in RobotSettings.auto().
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/12-robot-structure/#6-autonomous-uses-the-same-classes
 */
@Autonomous(name = "Tutorial 12: Main Auto", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class MainAuto extends LinearOpMode {

    private Robot robot;   // a field, so waitForLift and pause can use it

    @Override
    public void runOpMode() {
        robot = new Robot(hardwareMap);
        AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(), robot.imu);
        auto.setLoopHook(robot::update);      // the lift keeps working during every move

        waitForStart();
        if (isStopRequested()) return;
        try {
            robot.claw.close();
            robot.lift.goTo(LiftHeights.HIGH);   // rises during the next move (loop hook)
            auto.move(30, 0, 0.6);
            waitForLift(2.0);                    // at most 2 s: a stuck lift cannot use up the auto
            robot.claw.open();
            pause(0.3);                        // let the piece fall
            robot.lift.goTo(LiftHeights.BOTTOM);
            auto.move(30, 180, 0.6);
            auto.turnTo(0, 0.5);
        } finally {
            robot.stop();   // the wheels stop by themselves at the end of every move
            // The heading for TeleOp (x and y are 0: this example does not track position).
            PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
        }
    }

    /** Keeps the lift working until it reaches its target, or the time runs out. */
    private void waitForLift(double maxSeconds) {
        ElapsedTime timer = new ElapsedTime();
        while (opModeIsActive() && !robot.lift.isAtTarget() && timer.seconds() < maxSeconds) {
            robot.update();
            idle();
        }
    }

    /** Waits without letting go of the lift: use it instead of sleep(). */
    private void pause(double seconds) {
        ElapsedTime timer = new ElapsedTime();
        while (opModeIsActive() && timer.seconds() < seconds) {
            robot.update();
            idle();
        }
    }
}
