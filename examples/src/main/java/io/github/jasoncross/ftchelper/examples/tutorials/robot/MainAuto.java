package io.github.jasoncross.ftchelper.examples.tutorials.robot;

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
 */
@Autonomous(name = "Tutorial 12: Main Auto", group = "Tutorials")
@Disabled
public class MainAuto extends LinearOpMode {

    private Robot robot;

    @Override
    public void runOpMode() {
        robot = new Robot(hardwareMap);
        AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(), robot.imu);
        auto.setLoopHook(robot::update);      // the lift keeps working during every move

        waitForStart();
        try {
            robot.claw.close();
            robot.lift.goTo(LiftHeights.HIGH);
            auto.move(30, 0, 0.6);
            waitForLift(2.0);
            robot.claw.open();
            pause(0.3);                        // let the piece fall
            robot.lift.goTo(LiftHeights.BOTTOM);
            auto.move(30, 180, 0.6);
            auto.turnTo(0, 0.5);
        } finally {
            robot.stop();
            PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
        }
    }

    /** Keeps the lift working until it reaches its target, or the time runs out. */
    private void waitForLift(double maxSeconds) {
        ElapsedTime timer = new ElapsedTime();
        while (opModeIsActive() && !robot.lift.isAtTarget() && timer.seconds() < maxSeconds) {
            robot.update();
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
        }
    }
}
