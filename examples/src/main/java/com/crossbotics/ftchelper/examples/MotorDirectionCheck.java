// The folder this file lives in. Change it to your TeamCode folder when you copy the file.
package com.crossbotics.ftchelper.examples;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.hardware.MotorSetup;

/**
 * Checks the drive motor directions, for mecanum or standard (tank) drive.
 * PUT THE ROBOT ON A BLOCK so the wheels are off the ground.
 * Hold X = front-left (tank: left), Y = front-right (tank: right), A = back-left, B = back-right.
 * Each wheel must roll so the robot would go FORWARD. Run it on every new robot and after rewiring.
 * more: https://crossbotics.com/ftc-helper/docs/modules/drive/#check-motor-directions
 */
@TeleOp(name = "Example: Motor Direction Check", group = "Examples")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class MotorDirectionCheck extends LinearOpMode {

    // Return the SAME drive settings your TeleOp uses, or this checks something else.
    static DriveConfig config() {
        return DriveConfig.mecanum();   // or DriveConfig.tank(), DriveConfig.tankTwoMotor()
    }

    @Override
    public void runOpMode() {
        DriveConfig cfg = config();
        cfg.validate();   // stops here with a plain message if a setting is wrong
        // FL, FR, BL, BR. A two-motor tank drive has no back motors (null).
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
                    if (motors[i] != null) motors[i].setPower(held[i] ? 0.3 : 0);   // 30% while held
                }
                telemetry.addLine("Each wheel must roll FORWARD.");
                telemetry.update();
            }
        } finally {
            for (DcMotorEx m : motors) if (m != null) m.setPower(0);
        }
    }

    // Set up as the drive classes do: direction from the settings, brake, plain power, power 0.
    private DcMotorEx motor(String name, boolean reversed) {
        return MotorSetup.get(hardwareMap, name).reversed(reversed).brake().runWithoutEncoder().done();
    }
}
