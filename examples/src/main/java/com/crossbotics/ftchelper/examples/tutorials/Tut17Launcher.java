package com.crossbotics.ftchelper.examples.tutorials;

// "import" lines tell Java where to find the classes we use: FTC SDK classes (com.qualcomm...)
// and helper-library classes (com.crossbotics.ftchelper...).
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.launcher.Launcher;
import com.crossbotics.ftchelper.launcher.MotorFeeder;
import com.crossbotics.ftchelper.launcher.ShotTable;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;

/**
 * Tutorial 17: a shooter. A flywheel, a hood, a feeder, and a shot table, joined by a Launcher.
 * Needs: motor "flywheel", servo "hood", motor "feeder".
 *
 * gamepad2: D-pad up/down = distance +/- 6 in, A = spin up, B = stop,
 *           right bumper = shoot 1, left bumper = shoot 3.
 * gamepad1 (testing): D-pad left/right = flywheel rpm -/+ 100 in "manual rpm" mode (X toggles).
 *
 * The parts: the FLYWHEEL is a fast-spinning wheel that throws the game piece; the HOOD is a
 * servo-moved flap that sets how steep the shot goes up; the FEEDER pushes one piece at a time
 * into the flywheel. The Launcher waits until all of them are ready before it feeds a piece.
 *
 * Before you use it: remove the {@code @Disabled} line, make the names in quotes match your
 * configuration, and replace the numbers with YOUR measurements (docs/modules/launcher.md).
 *
 * Tutorial: docs/tutorials/17-launcher.md
 */
// @TeleOp puts this program in the Driver Station's TeleOp list, with this name and group.
@TeleOp(name = "Tutorial 17: Launcher", group = "Tutorials")
// @Disabled hides it from the Driver Station. Delete this line to use the program.
@Disabled
// "extends LinearOpMode": this class IS an FTC program that runs top to bottom; we write runOpMode().
public class Tut17Launcher extends LinearOpMode {

    // Fields: values for the whole program, so the button actions below can change them.
    private double distanceIn = 36;        // distance to the goal, inches (the operator sets it)
    private boolean manualRpm = false;     // true = test mode: set the flywheel speed by hand
    private double rpm = 3000;             // the hand-set speed, RPM (turns per minute)

    // @Override: this replaces the SDK's runOpMode(), where our program starts when INIT is pressed.
    @Override
    public void runOpMode() {
        // ---------- Setup (once, in INIT) ----------

        RobotTelemetry t = new RobotTelemetry(telemetry);

        // The flywheel. It holds a speed in RPM (turns per minute).
        // countsPerRev(28): the encoder (the sensor that counts motor turns) gives 28 counts per
        //   turn; that is right only when the flywheel is on the motor shaft with no gears.
        // pidf(10, 3, 0, 12): the hub's own speed-control settings (P, I, D, F). Start here and
        //   tune (docs/modules/launcher.md). F does most of the work for a flywheel.
        // tolerance(75) + settleTime(0.15): "ready" = within 75 RPM of the target for 0.15 s, so
        //   one lucky reading does not count. A piece fed before it is ready flies short.
        Flywheel flywheel = Flywheel.create(hardwareMap, "flywheel")
                .countsPerRev(28)               // bare motor encoder, flywheel on the motor shaft
                .pidf(10, 3, 0, 12)
                .tolerance(75)
                .settleTime(0.15);
        // The hood servo, set up to work in degrees: two measured points (servo position 0.10
        // is 0 degrees, 0.90 is 25 degrees) and a straight line in between.
        // travelTime(0.4): the servo needs about 0.4 s for a full move; a servo cannot report
        // where it is, so the launcher uses this to know when the hood has arrived.
        PositionServo hood = PositionServo.create(hardwareMap, "hood")
                .degrees(0.10, 0, 0.90, 25)     // measured: raw 0.10 = 0 deg, raw 0.90 = 25 deg
                .travelTime(0.4);
        // The feeder motor: full power (1.0) for 0.25 seconds pushes one piece into the flywheel.
        MotorFeeder feeder = MotorFeeder.create(hardwareMap, "feeder").power(1.0).feedTime(0.25);

        // Measured shots: distance (in), rpm, hood (deg). Replace with YOUR measurements.
        // Between two points the table draws a straight line (36 in = halfway between 24 and 48).
        // Outside them it uses the nearest end: it never guesses past what you tested.
        ShotTable table = new ShotTable()
                .add(24, 2600, 6)
                .add(48, 3100, 14)
                .add(72, 3700, 21);

        // The Launcher joins the parts: spin up, wait until EVERYTHING is ready, feed, repeat.
        // shotInterval(0.3): at least 0.3 s between pieces, so the flywheel gets back to speed.
        // readyTimeout(2.5): if it is not ready within 2.5 s it gives up the shots (and the
        //   screen says why) instead of waiting forever.
        Launcher launcher = new Launcher(flywheel, feeder)
                .hood(hood)
                .table(table)
                .shotInterval(0.3)
                .readyTimeout(2.5);

        // Buttons. bindPress runs the action once each time the button goes down.
        // "() -> ..." is a lambda: a small piece of code the button runs later.
        // "launcher::spinUp" is a method reference: short for "() -> launcher.spinUp()".
        // Math.min/Math.max keep the distance between 12 and 96 inches.
        // shoot() does not wait: it only asks for shots, and launcher.update() feeds each piece
        // when ready, so the robot keeps driving meanwhile (docs/guides/non-blocking.md).
        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G2, Btn.DPAD_UP, () -> distanceIn = Math.min(distanceIn + 6, 96))
                .bindPress(Pad.G2, Btn.DPAD_DOWN, () -> distanceIn = Math.max(distanceIn - 6, 12))
                .bindPress(Pad.G2, Btn.A, launcher::spinUp)
                .bindPress(Pad.G2, Btn.B, launcher::idle)          // cancel shots, back to idle (stopped)
                .bindPress(Pad.G2, Btn.RB, () -> launcher.shoot(1))
                .bindPress(Pad.G2, Btn.LB, () -> launcher.shoot(3))
                // Testing only (gamepad 1): "!manualRpm" means "not manualRpm", so X flips it.
                .bindPress(Pad.G1, Btn.X, () -> manualRpm = !manualRpm)
                .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> rpm += 100)
                // Math.max(0, ...): never below 0, or the wheel would spin backward.
                .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> rpm = Math.max(0, rpm - 100));

        // Wait here until the driver presses START.
        waitForStart();
        // A button held through START is ignored until let go, so nothing fires by surprise.
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        // try/finally: whatever happens in the loop (STOP, or a crash), "finally" still runs and
        // turns the shooter off. Safety first.
        try {
            // ---------- Every loop (many times per second until STOP) ----------
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);       // run the actions of pressed buttons
                // Choose the shot speed: by hand while building the table, else from the table.
                if (manualRpm) launcher.setRpm(rpm);       // for building the table
                else launcher.setDistance(distanceIn);     // the table picks rpm + hood
                launcher.update();   // updates the flywheel, feeder and hood, and feeds when ready

                // "a ? b : c" is a short if/else: show nothing extra when the table covers this
                // distance, else add a warning that the shot is a guess from the nearest point.
                t.line("Distance", "%.0f in%s", distanceIn, table.covers(distanceIn) ? "" : " (outside the table)");
                if (manualRpm) t.line("MANUAL rpm", "%.0f", rpm);
                launcher.addTelemetry(t);
                t.update();
            }
        } finally {
            launcher.stop();   // safety: feeder off, flywheel coasts to a stop (no hard braking)
        }
    }
}
