// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.starter;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (io.github.jasoncross...).
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.core.Profile;
import io.github.jasoncross.ftchelper.drive.DriverHeading;
import io.github.jasoncross.ftchelper.drive.MecanumDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;
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
 *   Y              field-centric on/off (starts off)   right bumper    claw open/close
 *   BACK           reset "forward"        left bumper     intake on/off
 *   GUIDE          tuning menu (practice) B (hold)        intake reverse
 *   hold START, press BACK   debug telemetry on/off
 * While the tuning menu is open, gamepad 1 runs the menu (its other buttons and sticks pause).
 *
 * Buzzes at 1:00 and 0:20 left (2026-27 BIOBUZZ: FLOWER ownership unlocks at 1:00; final 20 s).
 *
 * The starter kit is explained in docs/starter-kit.md; buttons in docs/tutorials/03-buttons.md;
 * the tuning menu in docs/guides/tuning.md.
 */
// @TeleOp puts it in the Driver Station's TeleOp list. The autonomous files pick it after autonomous
// by this exact name ("preselectTeleOp"), so if you rename it, rename it there too.
@TeleOp(name = "Main TeleOp", group = "Starter")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
// "extends LinearOpMode" = an FTC program that runs from top to bottom. The SDK gives us
// hardwareMap (the robot's parts), gamepad1, gamepad2 and telemetry (the screen).
public class MainTeleOp extends LinearOpMode {

    // "Fields": true/false switches the whole program can see. Buttons flip them; the loop reads them.
    // Field-centric starts OFF: the stick drives the way the robot's nose points (robot-centric).
    // Y turns field-centric on: "forward" on the stick is then away from the driver, whichever way
    // the robot faces.
    private boolean fieldCentric = false;
    // The intake starts off; the operator's left bumper turns it on.
    private boolean intakeOn = false;
    // True only while the operator holds B.
    private boolean intakeReverse = false;

    // @Override = "this replaces the SDK's own runOpMode()". It runs when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---------- INIT: set up. Nothing moves until START. ----------

        // Our helper for the Driver Station screen. "t" is short because we use it a lot.
        RobotTelemetry t = new RobotTelemetry(telemetry);
        // All the mechanisms (lift, claw, intake), set up in Robot.java.
        Robot robot = new Robot(hardwareMap);
        // The wheels, with the names and power limit from RobotSettings.java (same as autonomous).
        MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());
        // The IMU (the hub's turn sensor), needed for field-centric driving.
        IMU imu = RobotSettings.imu(hardwareMap);
        // Which way the robot faces compared with the driver. It continues from where autonomous
        // ended, so field-centric "forward" is right from the first second (drive/DriverHeading.java).
        DriverHeading heading = new DriverHeading(imu);
        // The alliance the last autonomous chose. If there was no autonomous in the last few
        // minutes, use BLUE. One TeleOp then works for both alliances.
        Alliance alliance = MatchMemory.allianceOr(Alliance.BLUE);

        // A 2-minute countdown that buzzes both gamepads with 60 s and 20 s left, so the drivers
        // do not have to watch the field clock.
        MatchTimer timer = MatchTimer.teleOp().rumbleAt(60, gamepad1, gamepad2).rumbleAt(20, gamepad1, gamepad2);
        // The tuning menu: change tunable numbers (lift heights, claw positions, PID gains) from
        // gamepad 1 while the robot runs. It only opens in the PRACTICE profile, never at an event.
        TuningMenu tuning = new TuningMenu(gamepad1);
        // Buzzes gamepad 1 to confirm a switch: two buzzes = ON, one buzz = OFF.
        ToggleRumble confirm = new ToggleRumble(gamepad1);

        // The buttons, in three groups, so the driver's group can be paused while the tuning menu
        // uses gamepad 1. "() -> ..." is a lambda: a small piece of code we hand over now, to be run
        // LATER when the button is pressed. Button actions must be quick: never wait inside one.
        // GUIDE (the center/home button): open or close the tuning menu. "tuning::toggle" is a method
        // reference: "run tuning.toggle() when pressed".
        ControllerBindings menuKey = new ControllerBindings().bindPress(Pad.G1, Btn.GUIDE, tuning::toggle);
        ControllerBindings driver = new ControllerBindings()
                // Y: field-centric on/off. The first press turns it ON (it starts OFF).
                .bindToggle(Pad.G1, Btn.Y,
                        () -> { fieldCentric = true; confirm.play(true); },
                        () -> { fieldCentric = false; confirm.play(false); })
                // BACK on its own: "the way the robot faces now is forward (0 degrees)". Point the robot
                // AWAY from the driver first.
                // BACK while START is held: debug telemetry (extra lines for fixing problems) on/off
                // instead, and "forward" is NOT reset. Needing two buttons makes it hard to do by accident.
                .bindPress(Pad.G1, Btn.BACK, () -> { if (gamepad1.start) t.toggleDebug(); else heading.reset(); });
        ControllerBindings operator = new ControllerBindings()
                // D-pad up/down: the lift goes to the next named height up or down (DOWN, LOW, HIGH).
                .bindPress(Pad.G2, Btn.DPAD_UP, robot.lift::stepUp)
                .bindPress(Pad.G2, Btn.DPAD_DOWN, robot.lift::stepDown)
                // A / Y: straight to the bottom or the top height.
                .bindPress(Pad.G2, Btn.A, () -> robot.lift.goTo("DOWN"))
                .bindPress(Pad.G2, Btn.Y, () -> robot.lift.goTo("HIGH"))
                // Right bumper: claw open if closed, closed if open.
                .bindPress(Pad.G2, Btn.RB, () -> robot.claw.toggle("OPEN", "CLOSED"))
                // Left bumper: intake on/off. "!intakeOn" = the opposite, so each press flips it.
                .bindPress(Pad.G2, Btn.LB, () -> intakeOn = !intakeOn)
                // B: reverse the intake while held (press = start, release = stop reversing).
                .bindPress(Pad.G2, Btn.B, () -> intakeReverse = true)
                .bindRelease(Pad.G2, Btn.B, () -> intakeReverse = false);

        // ---------- INIT screen ----------
        // Instead of a plain waitForStart(), we loop during INIT to keep the screen up to date.
        // The loop ends when START (or STOP) is pressed. Nothing moves in here.
        while (opModeInInit()) {
            // Warnings so nobody plays a match by mistake with shop settings.
            if (Profile.current().isPractice()) t.warn("PRACTICE profile");
            if (Tunable.countChanged() > 0) t.warn(Tunable.countChanged() + " tuned value(s) differ from the code");
            t.line("Alliance", alliance);
            t.text("Field-centric: point the robot away from you, then press BACK.");
            t.update();
        }
        // STOP was pressed instead of START: end here, without moving anything.
        if (isStopRequested()) return;
        // START was pressed: start the 2-minute countdown now.
        timer.start();
        // A button that was already held when START was pressed must not count as a press.
        menuKey.ignoreHeldButtons(gamepad1, gamepad2);
        driver.ignoreHeldButtons(gamepad1, gamepad2);
        operator.ignoreHeldButtons(gamepad1, gamepad2);
        // A "local variable": it only exists inside runOpMode. Remembers if the menu was open last loop.
        boolean menuWasOpen = false;

        // try/finally: the "finally" part runs however the program ends (STOP, or an error),
        // so every motor is always stopped.
        try {
            // The main loop: runs many times a second until the match ends. Nothing in it waits.
            while (opModeIsActive()) {
                // ---- Read the buttons ----
                // The GUIDE button and the operator's buttons always work.
                menuKey.update(gamepad1, gamepad2);
                operator.update(gamepad1, gamepad2);
                // Buzz when a time cue comes up.
                timer.update();
                // Lets the menu read gamepad 1 (it does nothing while closed).
                tuning.update();
                if (tuning.isOpen()) {
                    // The menu is using gamepad 1, so the driver's buttons are paused.
                    menuWasOpen = true;
                } else {
                    // The menu just closed: the button that closed it (or a D-pad press) may still be
                    // held. Ignore held buttons once, so they do not fire a driver action.
                    if (menuWasOpen) driver.ignoreHeldButtons(gamepad1, gamepad2);
                    menuWasOpen = false;
                    driver.update(gamepad1, gamepad2);
                }

                // ---- Drive (not while the tuning menu uses gamepad 1) ----
                // All 0 while the menu is open, so the robot stays still.
                double forward = 0, strafe = 0, turn = 0;
                if (!tuning.isOpen()) {
                    // shape(stick, 0.05, 2): ignore tiny values under 0.05 so a resting stick does not
                    // make the robot creep, then "square" the rest for fine control at low speed.
                    // The minus sign: stick Y reads NEGATIVE when pushed up (docs/conventions.md).
                    forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
                    strafe = StickShaping.shape(gamepad1.left_stick_x, 0.05, 2);
                    turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
                }
                // Left trigger = slow: not pressed = full speed (1.0), squeezed all the way = 30%.
                double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
                if (fieldCentric) drive.driveFieldCentric(forward, strafe, turn, heading.degrees(), speed);
                else drive.drive(forward, strafe, turn, speed);

                // ---- Mechanisms ----
                // The operator's left stick moves the lift by hand. Deadband 0.1 so a bumped stick does
                // not cancel a button height; exponent 1 = no curve. Any push cancels the button target;
                // let go and the lift holds where it is.
                robot.lift.manual(StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1));
                // The intake rules, top to bottom: reverse wins, then on/off. When B is let go it goes
                // back to on or off, whichever it was. in/out/stop are safe to call every loop.
                if (intakeReverse) robot.intake.out();     // reverse wins
                else if (intakeOn) robot.intake.in();
                else robot.intake.stop();
                // Every mechanism does its work (PID, limits, servo timing, motor power), once per loop.
                robot.all.update();

                // ---- Telemetry ----
                if (tuning.isOpen()) {
                    // The menu takes over the screen.
                    tuning.addTelemetry(t);
                } else {
                    // Warn if a button action took too long: that froze the whole robot for a moment.
                    if (!driver.getSlowActionWarning().isEmpty()) t.warn(driver.getSlowActionWarning());
                    if (!operator.getSlowActionWarning().isEmpty()) t.warn(operator.getSlowActionWarning());
                    t.line("Time", timer.format());
                    // "a ? b : c" = if a is true use b, otherwise c. SLOW shows while the trigger is pulled.
                    t.line("Drive", "%s%s", fieldCentric ? "field" : "robot", speed < 0.99 ? "  SLOW" : "");
                    robot.all.addTelemetry(t);
                    // Only shown when debug telemetry is on.
                    t.debug("Heading", "%.1f%s", heading.degrees(), heading.isFromAuto() ? " (from auto)" : "");
                }
                t.update();
            }
        } finally {
            // SAFETY: stop the wheels and every mechanism when the program ends, whatever the reason.
            drive.stop();
            robot.all.stop();
        }
    }
}
