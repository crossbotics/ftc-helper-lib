package io.github.jasoncross.ftchelper.examples.starter;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.core.Profile;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;
import io.github.jasoncross.ftchelper.hardware.ImuSetup;
import io.github.jasoncross.ftchelper.match.Alliance;
import io.github.jasoncross.ftchelper.match.MatchMemory;
import io.github.jasoncross.ftchelper.match.MatchTimer;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.tuning.Tunable;
import io.github.jasoncross.ftchelper.tuning.TuningMenu;

/**
 * STARTER KIT, file 3 of 6: the TeleOp. ONE TeleOp for both alliances (it reads the alliance
 * the last autonomous chose).
 *
 * DRIVER (gamepad 1)                    OPERATOR (gamepad 2)
 *   left stick     drive                  D-pad up/down   lift one position up/down
 *   right stick X  turn                   A / Y           lift DOWN / HIGH
 *   left trigger   slow (squeeze)         left stick Y    lift by hand
 *   Y              field-centric on/off   right bumper    claw open/close
 *   BACK           reset "forward"        left bumper     intake on/off
 *   GUIDE          tuning menu (practice) B (hold)        intake reverse
 *   START + BACK   debug telemetry
 * While the tuning menu is open, gamepad 1 runs the menu (its other buttons and sticks pause).
 *
 * Buzzes at 1:00 and 0:20 left (2026-27 BIOBUZZ: FLOWER ownership unlocks at 1:00; final 20 s).
 */
@TeleOp(name = "Main TeleOp", group = "Starter")
@Disabled
public class MainTeleOp extends LinearOpMode {

    private boolean fieldCentric = true;
    private boolean intakeOn = false;
    private boolean intakeReverse = false;

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);
        Robot robot = new Robot(hardwareMap);
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());
        IMU imu = RobotSettings.imu(hardwareMap);
        Alliance alliance = MatchMemory.allianceOr(Alliance.BLUE);

        MatchTimer timer = MatchTimer.teleOp().rumbleAt(60, gamepad1, gamepad2).rumbleAt(20, gamepad1, gamepad2);
        TuningMenu tuning = new TuningMenu(gamepad1);
        ToggleRumble confirm = new ToggleRumble(gamepad1);

        ControllerBindings menuKey = new ControllerBindings().bindPress(Pad.G1, Btn.GUIDE, tuning::toggle);
        ControllerBindings driver = new ControllerBindings()
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { fieldCentric = false; confirm.play(false); },
                        () -> { fieldCentric = true; confirm.play(true); })
                .bindPress(Pad.G1, Btn.BACK, imu::resetYaw)
                .bindPress(Pad.G1, Btn.START, () -> { if (gamepad1.back) t.toggleDebug(); });
        ControllerBindings operator = new ControllerBindings()
                .bindPress(Pad.G2, Btn.DPAD_UP, robot.lift::stepUp)
                .bindPress(Pad.G2, Btn.DPAD_DOWN, robot.lift::stepDown)
                .bindPress(Pad.G2, Btn.A, () -> robot.lift.goTo("DOWN"))
                .bindPress(Pad.G2, Btn.Y, () -> robot.lift.goTo("HIGH"))
                .bindPress(Pad.G2, Btn.RB, () -> robot.claw.toggle("OPEN", "CLOSED"))
                .bindPress(Pad.G2, Btn.LB, () -> intakeOn = !intakeOn)
                .bindPress(Pad.G2, Btn.B, () -> intakeReverse = true)
                .bindRelease(Pad.G2, Btn.B, () -> intakeReverse = false);

        // ---------- INIT screen ----------
        while (opModeInInit()) {
            if (Profile.current().isPractice()) t.warn("PRACTICE profile");
            if (Tunable.countChanged() > 0) t.warn(Tunable.countChanged() + " tuned value(s) differ from the code");
            t.line("Alliance", alliance);
            t.text("Field-centric: point the robot away from you, then press BACK.");
            t.update();
        }
        if (isStopRequested()) return;
        timer.start();
        menuKey.ignoreHeldButtons(gamepad1, gamepad2);
        driver.ignoreHeldButtons(gamepad1, gamepad2);
        operator.ignoreHeldButtons(gamepad1, gamepad2);
        boolean menuWasOpen = false;

        try {
            while (opModeIsActive()) {
                menuKey.update(gamepad1, gamepad2);
                operator.update(gamepad1, gamepad2);
                timer.update();
                tuning.update();
                if (tuning.isOpen()) {
                    menuWasOpen = true;
                } else {
                    if (menuWasOpen) driver.ignoreHeldButtons(gamepad1, gamepad2);
                    menuWasOpen = false;
                    driver.update(gamepad1, gamepad2);
                }

                // ---- Drive (not while the tuning menu uses gamepad 1) ----
                double forward = 0, strafe = 0, turn = 0;
                if (!tuning.isOpen()) {
                    forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
                    strafe = StickShaping.shape(gamepad1.left_stick_x, 0.05, 2);
                    turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                }
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
                double heading = ImuSetup.headingDeg(imu);
                if (fieldCentric) drive.driveFieldCentric(forward, strafe, turn, heading, speed);
                else drive.drive(forward, strafe, turn, speed);

                // ---- Mechanisms ----
                robot.lift.manual(StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1));
                if (intakeReverse) robot.intake.out();     // reverse wins
                else if (intakeOn) robot.intake.in();
                else robot.intake.stop();
                robot.all.update();

                // ---- Telemetry ----
                if (tuning.isOpen()) {
                    tuning.addTelemetry(t);
                } else {
                    if (!driver.getSlowActionWarning().isEmpty()) t.warn(driver.getSlowActionWarning());
                    if (!operator.getSlowActionWarning().isEmpty()) t.warn(operator.getSlowActionWarning());
                    t.line("Time", timer.format());
                    t.line("Drive", "%s%s", fieldCentric ? "field" : "robot", speed < 0.99 ? "  SLOW" : "");
                    robot.all.addTelemetry(t);
                    t.debug("Heading", "%.1f", heading);
                }
                t.update();
            }
        } finally {
            drive.stop();
            robot.all.stop();
        }
    }
}
