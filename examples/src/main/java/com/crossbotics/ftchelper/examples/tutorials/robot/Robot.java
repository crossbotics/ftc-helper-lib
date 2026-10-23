// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials.robot;

// RobotSettings is the tutorials' settings file, one folder up.
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import com.crossbotics.ftchelper.examples.tutorials.RobotSettings;
import com.crossbotics.ftchelper.hardware.BulkReads;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * Tutorial 12: the whole robot's mechanisms in one object. TeleOp and autonomous both make one.
 * The drivetrain is NOT in here: TeleOp drives with MecanumDrive, autonomous with AutoDrive.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/12-robot-structure/#4-the-whole-robot-robot
 */
public class Robot {
    public final IMU imu;
    public final Lift lift;
    public final Claw claw;

    // Nothing moves here, so it is safe in INIT.
    public Robot(HardwareMap hardwareMap) {
        BulkReads.auto(hardwareMap);
        imu = RobotSettings.imu(hardwareMap);
        lift = new Lift(hardwareMap);
        claw = new Claw(hardwareMap);
    }

    /** Call once every loop (TeleOp), or from AutoDrive's loop hook (autonomous). */
    public void update() {
        lift.update();
    }

    // SAFETY: OpModes call this in their "finally" part.
    public void stop() {
        lift.stop();
    }

    public void addTelemetry(Telemetry telemetry) {
        lift.addTelemetry(telemetry);
        claw.addTelemetry(telemetry);
    }
}
