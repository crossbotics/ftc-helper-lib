package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.gamepad.AimRumble;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.match.InitMenu;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.vision.AimController;
import io.github.jasoncross.ftchelper.vision.LimelightTags;
import io.github.jasoncross.ftchelper.vision.TargetObservation;
import io.github.jasoncross.ftchelper.vision.TargetSource;
import io.github.jasoncross.ftchelper.vision.WebcamTags;

/**
 * Tutorial 18: aim at an AprilTag with a webcam OR a Limelight 3A (choose in INIT).
 * Hold the right bumper: the robot turns to face the tag while you keep driving.
 * Needs: the drivetrain, and a webcam "Webcam 1" or a Limelight "limelight".
 *
 * Tutorial: docs/tutorials/18-vision-aim.md
 */
@TeleOp(name = "Tutorial 18: Vision Aim", group = "Tutorials")
@Disabled
public class Tut18VisionAim extends LinearOpMode {

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());

        // ---- INIT: choose the camera and the tag ----
        InitMenu menu = new InitMenu(gamepad1)
                .choice("Camera", "Webcam", "Limelight")
                .choice("Tag id", "20", "24", "any");
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
        camera.start();
        String tagChoice = menu.get("Tag id");

        AimController aim = new AimController(0.02, 0.003).deadband(1.5).maxPower(0.5).maxAgeMs(150);
        AimRumble feel = new AimRumble(gamepad1).setThreshold(3);

        while (opModeInInit()) {
            camera.update();
            t.line("Camera", camera.status());
            t.line("Tags seen", camera.targets().size());
            t.update();
        }
        if (isStopRequested()) {
            camera.close();
            return;
        }

        try {
            while (opModeIsActive()) {
                camera.update();
                TargetObservation target = tagChoice.equals("any")
                        ? (camera.targets().isEmpty() ? null : camera.targets().get(0))
                        : camera.target(Integer.parseInt(tagChoice));

                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                if (gamepad1.right_bumper) {
                    turn = aim.turnPower(target);    // same sign as the drive's turn (clockwise +)
                    if (target != null) feel.update(target.bearingDeg);
                } else {
                    aim.reset();
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
            drive.stop();
            camera.close();
        }
    }
}
