package io.github.jasoncross.ftchelper.examples.tutorials.robot;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.examples.tutorials.RobotSettings;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * Tutorial 12: the whole robot's mechanisms in one object. TeleOp and autonomous both make one.
 * The drivetrain is NOT in here, because TeleOp drives with MecanumDrive and autonomous with
 * AutoDrive; both read the same RobotSettings.drive().
 */
public class Robot {
    public final IMU imu;
    public final Lift lift;
    public final Claw claw;

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

    public void stop() {
        lift.stop();
    }

    public void addTelemetry(Telemetry telemetry) {
        lift.addTelemetry(telemetry);
        claw.addTelemetry(telemetry);
    }
}
