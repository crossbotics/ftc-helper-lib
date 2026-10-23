package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.drive.MecanumDrive;
import com.crossbotics.ftchelper.gamepad.AimRumble;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.match.InitMenu;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.vision.AimController;
import com.crossbotics.ftchelper.vision.LimelightTags;
import com.crossbotics.ftchelper.vision.TargetObservation;
import com.crossbotics.ftchelper.vision.TargetSource;
import com.crossbotics.ftchelper.vision.WebcamTags;

/**
 * Tutorial 18: aim at a target with a webcam OR a Limelight 3A (choose in INIT).
 * Hold the right bumper: the robot turns to face the target while you keep driving.
 * Needs: the drivetrain, and a webcam "Webcam 1" or a Limelight "limelight".
 * Choose the targets for this season's field (BIOBUZZ: the 4 tags under each CELL are one target).
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/18-vision-aim/
 */
@TeleOp(name = "Tutorial 18: Vision Aim", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut18VisionAim extends LinearOpMode {

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());

        // ---- INIT: choose the camera and the target (gamepad 1's D-pad) ----
        InitMenu menu = new InitMenu(gamepad1)
                .choice("Camera", "Webcam", "Limelight")
                .choice("Target", "RED SCORING", "BLUE SCORING", "any");
        // The camera starts only after A: we need to know WHICH camera to start.
        while (opModeInInit() && !gamepad1.a) {
            menu.update();
            menu.addTelemetry(t);
            t.text("Press A to confirm (the camera starts).");
            t.update();
        }
        // One line decides the camera; the rest of the program does not care which.
        TargetSource camera = menu.get("Camera").equals("Limelight")
                ? LimelightTags.create(hardwareMap, "limelight").pipeline(0)
                : WebcamTags.create(hardwareMap, "Webcam 1").resolution(640, 480);
        camera.start();   // a webcam needs about 1-2 seconds to start; this is why we do it in INIT
        String targetChoice = menu.get("Target");

        // kP, kD. Within 1.5 degrees = aimed. Pictures older than 150 ms are ignored.
        AimController aim = new AimController(0.02, 0.003).deadband(1.5).maxPower(0.5).maxAgeMs(150);
        AimRumble feel = new AimRumble(gamepad1).setThreshold(3);   // buzz within 3 degrees

        // Until START: show what the camera sees, so the team can check it before the match.
        while (opModeInInit()) {
            camera.update();
            t.line("Camera", camera.status());
            t.line("Targets seen", camera.targets().size());
            t.update();
        }
        if (isStopRequested()) {
            camera.close();   // free the camera for the next program
            return;
        }

        try {
            while (opModeIsActive()) {
                camera.update();   // get the newest picture's results (never waits for a picture)
                // null = not seen now. One tag by its number also works: camera.target(31).
                TargetObservation target = targetChoice.equals("any")
                        ? (camera.targets().isEmpty() ? null : camera.targets().get(0))
                        : camera.target(targetChoice);

                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                if (gamepad1.right_bumper) {
                    turn = aim.turnPower(target);    // same sign as the drive's turn (clockwise +)
                    if (target != null) feel.update(target.bearingDeg);   // always check for null
                } else {
                    aim.reset();   // the next aim starts fresh
                }
                drive.drive(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
                        StickShaping.shape(gamepad1.left_stick_x, 0.05, 2), turn);

                t.line("Camera", camera.status());
                t.line("Target", target == null ? "not seen" : target.toString());
                t.line("Aimed", aim.isAimed());
                camera.addTelemetry(t);
                t.update();
            }
        } finally {
            drive.stop();     // safety: wheels off
            camera.close();   // free the camera for the next program
        }
    }
}
