// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (com.crossbotics...).
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.hardware.MotorSetup;

/**
 * Checks the drive motor directions, for mecanum or standard (tank) drive.
 * PUT THE ROBOT ON A BLOCK so the wheels are off the ground.
 *
 * Set config() below to the same DriveConfig your TeleOp uses.
 * Hold a button to spin ONE motor at low power:
 *   X = front-left (tank: left), Y = front-right (tank: right), A = back-left, B = back-right.
 * Each wheel must roll so the robot would go FORWARD. If one goes backward, flip that motor's
 * setting: with the robot in five files, add its name to .reversed(...) in Robot.java (or take it
 * out); with a DriveConfig of your own, flip its "Reversed" setting there.
 *
 * Copy it into TeamCode and remove the @Disabled line. Run it on every new robot, and again
 * after any motor or wire is changed: a wrong direction makes the robot drive in circles.
 *
 * Steps: docs/modules/drive.md, "Check motor directions"
 */
// @TeleOp puts this program in the Driver Station's TeleOp list with this name.
@TeleOp(name = "Example: Motor Direction Check", group = "Examples")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom.
public class MotorDirectionCheck extends LinearOpMode {

    // The drive settings to check. Return the SAME settings your TeleOp uses (with your motor
    // names and "Reversed" choices), or this check tests something else.
    // "static" = it belongs to the program itself, not to one running copy of it, so other
    // files could call MotorDirectionCheck.config() too.
    static DriveConfig config() {
        return DriveConfig.mecanum();   // or DriveConfig.tank(), DriveConfig.tankTwoMotor()
    }

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---- INIT: get the motors. Nothing moves yet. ----
        DriveConfig cfg = config();
        // Check the settings make sense (for example no missing motor name). If not, the program
        // stops here with a plain message on the Driver Station.
        cfg.validate();
        // An array (a numbered list) of the four motors, in the library's usual order:
        // [0] front-left, [1] front-right, [2] back-left, [3] back-right (docs/conventions.md).
        // A two-motor tank drive has no back motors, so those two places hold null (= "none").
        // "a ? b : c" means: if a is true use b, otherwise c.
        DcMotorEx[] motors = {
                motor(cfg.frontLeft, cfg.frontLeftReversed),
                motor(cfg.frontRight, cfg.frontRightReversed),
                cfg.hasBackMotors() ? motor(cfg.backLeft, cfg.backLeftReversed) : null,
                cfg.hasBackMotors() ? motor(cfg.backRight, cfg.backRightReversed) : null};
        // SAFETY reminder on the screen: the wheels will spin, so lift the robot first.
        telemetry.addLine("Wheels off the ground! X=FL  Y=FR  A=BL  B=BR");
        telemetry.update();
        // Wait here until the driver presses START.
        waitForStart();
        // try/finally: the "finally" part runs however the program ends, so the motors stop.
        try {
            // The main loop: runs many times a second until STOP.
            while (opModeIsActive()) {
                // Which buttons are held right now, in the same order as the motors above:
                // X -> front-left, Y -> front-right, A -> back-left, B -> back-right.
                boolean[] held = {gamepad1.x, gamepad1.y, gamepad1.a, gamepad1.b};
                // Go through the four places, i = 0, 1, 2, 3.
                for (int i = 0; i < 4; i++) {
                    // Skip a place with no motor (a two-motor tank drive). A held button spins its
                    // motor at 30%: slow enough to be safe and to see the direction, fast enough to move.
                    // Let go and the power goes back to 0.
                    if (motors[i] != null) motors[i].setPower(held[i] ? 0.3 : 0);
                }
                telemetry.addLine("Each wheel must roll FORWARD.");
                telemetry.update();
            }
        } finally {
            // SAFETY: stop every motor when the program ends, whatever the reason.
            // "for (DcMotorEx m : motors)" means "for each motor m in the list".
            for (DcMotorEx m : motors) if (m != null) m.setPower(0);
        }
    }

    // Gets one motor and sets it up exactly as the drive classes would: the direction from the
    // settings, brake at power 0, plain power (no speed control), and power 0 to start.
    private DcMotorEx motor(String name, boolean reversed) {
        return MotorSetup.get(hardwareMap, name).reversed(reversed).brake().runWithoutEncoder().done();
    }
}
