package com.crossbotics.ftchelper.examples.tutorials;

// "import" lines tell Java where to find the classes we use: FTC SDK classes (com.qualcomm...)
// and helper-library classes (com.crossbotics.ftchelper...).
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
 *
 * An AprilTag is a black-and-white square printed on the field, a bit like a QR code. Each has a
 * number (its id), and the camera can tell which one it sees and in which direction it is.
 *
 * Before you use it: remove the {@code @Disabled} line, make "Webcam 1" / "limelight" match your
 * configuration, and choose the targets for this season's field (BIOBUZZ: the 4 tags under each CELL
 * are one target, e.g. "RED SCORING").
 *
 * Tutorial: docs/tutorials/18-vision-aim.md (more in docs/modules/vision.md)
 */
// @TeleOp puts this program in the Driver Station's TeleOp list, with this name and group.
@TeleOp(name = "Tutorial 18: Vision Aim", group = "Tutorials")
// @Disabled hides it from the Driver Station. Delete this line to use the program.
@Disabled
// "extends LinearOpMode": this class IS an FTC program that runs top to bottom; we write runOpMode().
public class Tut18VisionAim extends LinearOpMode {

    // @Override: this replaces the SDK's runOpMode(), where our program starts when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---------- Setup (once, in INIT) ----------
        RobotTelemetry t = new RobotTelemetry(telemetry);
        // The mecanum drive, with motor names and power limit from RobotSettings.java.
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());

        // ---- INIT: choose the camera and the target ----
        // Two menu lines, changed with gamepad 1's D-pad. The first option is the default.
        InitMenu menu = new InitMenu(gamepad1)
                .choice("Camera", "Webcam", "Limelight")
                .choice("Target", "RED SCORING", "BLUE SCORING", "any");
        // Stay in this menu until A is pressed (or START/STOP ends INIT). The camera starts only
        // after A, because we need to know WHICH camera to start.
        while (opModeInInit() && !gamepad1.a) {
            menu.update();
            menu.addTelemetry(t);
            t.text("Press A to confirm (the camera starts).");
            t.update();
        }
        // One line decides the camera; the rest of the program does not care which.
        // TargetSource is an "interface": a list of things every camera can do (start, update,
        // targets...). Both WebcamTags and LimelightTags can do them, so "camera" can hold either.
        // "a ? b : c" is a short if/else: if the menu says Limelight, make a Limelight (using
        // pipeline 0, a setup saved on the Limelight itself); otherwise make a webcam at
        // 640 x 480 pixels (a good start: higher sees tags farther away but runs slower).
        TargetSource camera = menu.get("Camera").equals("Limelight")
                ? LimelightTags.create(hardwareMap, "limelight").pipeline(0)
                : WebcamTags.create(hardwareMap, "Webcam 1").resolution(640, 480);
        camera.start();   // a webcam needs about 1-2 seconds to start; this is why we do it in INIT
        String targetChoice = menu.get("Target");

        // AimController turns "the tag is 10 degrees to the left" into a turn power.
        // 0.02 = turn power per degree off (10 degrees off gives 0.2); 0.003 = eases off as it
        //   closes in, so the robot does not swing past the tag (the "D" part of PID, tutorial 07).
        // deadband(1.5): within 1.5 degrees counts as aimed and gives 0 power, so the robot does
        //   not twitch back and forth forever.
        // maxPower(0.5): never turn harder than 0.5 while aiming.
        // maxAgeMs(150): ignore pictures older than 150 milliseconds; an old picture would point
        //   the wrong way because the robot has moved since.
        AimController aim = new AimController(0.02, 0.003).deadband(1.5).maxPower(0.5).maxAgeMs(150);
        // Buzz gamepad 1 when the tag is within 3 degrees: the driver feels "aimed".
        AimRumble feel = new AimRumble(gamepad1).setThreshold(3);

        // Until START: keep the camera running and show what it sees, so the team can check the
        // camera works BEFORE the match starts.
        while (opModeInInit()) {
            camera.update();
            t.line("Camera", camera.status());
            t.line("Targets seen", camera.targets().size());
            t.update();
        }
        // STOP pressed in INIT: close the camera (so the next program can use it) and end.
        if (isStopRequested()) {
            camera.close();
            return;
        }

        // try/finally: whatever happens in the loop (STOP, or a crash), "finally" still runs:
        // the wheels stop and the camera is closed.
        try {
            // ---------- Every loop (many times per second until STOP) ----------
            while (opModeIsActive()) {
                camera.update();   // get the newest picture's results (never waits for a picture)
                // What to aim at. TargetObservation = one target the camera sees (its name,
                // direction and distance). null means "nothing seen right now".
                // "any": the first target in the list, or null if the list is empty.
                // Otherwise: the target with the chosen name. In BIOBUZZ the 4 tags under a CELL are
                // one target ("RED SCORING"...), with either camera. camera.target(...) gives null if
                // it is not seen now. (One tag by its number also works: camera.target(31).)
                TargetObservation target = targetChoice.equals("any")
                        ? (camera.targets().isEmpty() ? null : camera.targets().get(0))
                        : camera.target(targetChoice);

                // The driver's turn from the right stick (dead zone 0.05, squared for fine control).
                double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                if (gamepad1.right_bumper) {
                    // Aiming: the camera takes over turning. With no fresh target the power is 0,
                    // so the robot never turns toward something it cannot see now.
                    turn = aim.turnPower(target);    // same sign as the drive's turn (clockwise +)
                    // Always check for null before using a target, or the program crashes.
                    if (target != null) feel.update(target.bearingDeg);
                } else {
                    // Bumper let go: forget the old aim, so the next aim does not start with a
                    // wrong kick from an old picture.
                    aim.reset();
                }
                // Robot-centric drive: forward (minus sign because the SDK gives UP as negative),
                // strafe right, and the turn chosen above. The driver keeps driving while aiming.
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
