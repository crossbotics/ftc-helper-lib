package org.octobots.ftchelper.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import org.octobots.ftchelper.drive.DriveConfig;
import org.octobots.ftchelper.drive.MecanumDrive;

/**
 * Checks the drive motor directions. PUT THE ROBOT ON A BLOCK so the wheels are off the ground.
 *
 * Hold a button to spin ONE wheel forward at low power:
 *   X = front-left, Y = front-right, A = back-left, B = back-right.
 * Each wheel must roll so the robot would go FORWARD. If one goes backward, flip its
 * "Reversed" setting in DriveConfig.
 *
 * Steps: docs/modules/drive.md, "Check motor directions"
 */
@TeleOp(name = "Example: Motor Direction Check", group = "Examples")
@Disabled
public class MotorDirectionCheck extends LinearOpMode {

    @Override
    public void runOpMode() {
        MecanumDrive drive = new MecanumDrive(hardwareMap, new DriveConfig());
        telemetry.addLine("Wheels off the ground! X=FL  Y=FR  A=BL  B=BR");
        telemetry.update();
        waitForStart();
        try {
            while (opModeIsActive()) {
                double p = 0.3;
                drive.setPowers(new double[]{
                        gamepad1.x ? p : 0,
                        gamepad1.y ? p : 0,
                        gamepad1.a ? p : 0,
                        gamepad1.b ? p : 0});
                telemetry.addLine("Each wheel must roll FORWARD.");
                telemetry.update();
            }
        } finally {
            drive.stop();
        }
    }
}
