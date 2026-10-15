package io.github.jasoncross.ftchelper.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;

/**
 * Checks the drive motor directions, for mecanum or standard (tank) drive.
 * PUT THE ROBOT ON A BLOCK so the wheels are off the ground.
 *
 * Set config() below to the same DriveConfig your TeleOp uses.
 * Hold a button to spin ONE motor at low power:
 *   X = front-left (tank: left), Y = front-right (tank: right), A = back-left, B = back-right.
 * Each wheel must roll so the robot would go FORWARD. If one goes backward, flip its
 * "Reversed" setting in DriveConfig.
 *
 * Steps: docs/modules/drive.md, "Check motor directions"
 */
@TeleOp(name = "Example: Motor Direction Check", group = "Examples")
@Disabled
public class MotorDirectionCheck extends LinearOpMode {

    static DriveConfig config() {
        return DriveConfig.mecanum();   // or DriveConfig.tank(), DriveConfig.tankTwoMotor()
    }

    @Override
    public void runOpMode() {
        DriveConfig cfg = config();
        cfg.validate();
        DcMotorEx[] motors = {
                motor(cfg.frontLeft, cfg.frontLeftReversed),
                motor(cfg.frontRight, cfg.frontRightReversed),
                cfg.hasBackMotors() ? motor(cfg.backLeft, cfg.backLeftReversed) : null,
                cfg.hasBackMotors() ? motor(cfg.backRight, cfg.backRightReversed) : null};
        telemetry.addLine("Wheels off the ground! X=FL  Y=FR  A=BL  B=BR");
        telemetry.update();
        waitForStart();
        try {
            while (opModeIsActive()) {
                boolean[] held = {gamepad1.x, gamepad1.y, gamepad1.a, gamepad1.b};
                for (int i = 0; i < 4; i++) {
                    if (motors[i] != null) motors[i].setPower(held[i] ? 0.3 : 0);
                }
                telemetry.addLine("Each wheel must roll FORWARD.");
                telemetry.update();
            }
        } finally {
            for (DcMotorEx m : motors) if (m != null) m.setPower(0);
        }
    }

    private DcMotorEx motor(String name, boolean reversed) {
        return MotorSetup.get(hardwareMap, name).reversed(reversed).brake().runWithoutEncoder().done();
    }
}
