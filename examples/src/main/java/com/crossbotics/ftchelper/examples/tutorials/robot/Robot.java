// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials.robot;

// "import" lines list the ready-made pieces this file uses. RobotSettings is the tutorials'
// settings file, one folder up.
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import com.crossbotics.ftchelper.examples.tutorials.RobotSettings;
import com.crossbotics.ftchelper.hardware.BulkReads;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * Tutorial 12: the whole robot's mechanisms in one object. TeleOp and autonomous both make one.
 * The drivetrain is NOT in here, because TeleOp drives with MecanumDrive and autonomous with
 * AutoDrive; both read the same RobotSettings.drive().
 */
// Not an OpMode, so it never shows on the Driver Station. See docs/tutorials/12-robot-structure.md.
public class Robot {
    // The parts. "public" = OpModes can use them (robot.lift.goTo(...)). "final" = each is set once,
    // in the constructor below, and never swapped for another.
    public final IMU imu;
    public final Lift lift;
    public final Claw claw;

    // The "constructor": runs when an OpMode writes "new Robot(hardwareMap)". It finds and sets up
    // every part. Nothing moves here, so it is safe in INIT.
    public Robot(HardwareMap hardwareMap) {
        // Read all sensors and encoders from the hub in one message per loop: a faster loop.
        BulkReads.auto(hardwareMap);
        // The IMU (turn sensor). Setting it up also makes the way the robot faces now heading 0.
        imu = RobotSettings.imu(hardwareMap);
        lift = new Lift(hardwareMap);
        claw = new Claw(hardwareMap);
    }

    /** Call once every loop (TeleOp), or from AutoDrive's loop hook (autonomous). */
    // Only the lift needs regular work (its PID). The claw is a servo: it goes where it is told.
    public void update() {
        lift.update();
    }

    // SAFETY: stop every motor. OpModes call this in their "finally" part.
    public void stop() {
        lift.stop();
    }

    // Every part's lines for the Driver Station screen.
    public void addTelemetry(Telemetry telemetry) {
        lift.addTelemetry(telemetry);
        claw.addTelemetry(telemetry);
    }
}
