package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
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
 * Needs: motor "flywheel", servo "hood", motor "feeder". Replace the numbers with YOUR measurements.
 * gamepad2: D-pad up/down = distance +/- 6 in, A = spin up, B = stop, RB = shoot 1, LB = shoot 3.
 * gamepad1 (testing): X = manual rpm mode, D-pad left/right = rpm -/+ 100.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/17-launcher/
 */
@TeleOp(name = "Tutorial 17: Launcher", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut17Launcher extends LinearOpMode {

    private double distanceIn = 36;        // distance to the goal, inches (the operator sets it)
    private boolean manualRpm = false;     // true = test mode: set the flywheel speed by hand
    private double rpm = 3000;             // the hand-set speed, RPM (turns per minute)

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);

        // 28 = bare motor encoder, flywheel on the motor shaft (no gears).
        Flywheel flywheel = Flywheel.create(hardwareMap, "flywheel")
                .countsPerRev(28)       // encoder counts per ONE flywheel turn
                .pidf(10, 3, 0, 12)     // the hub's speed control
                .tolerance(75)          // "at speed" = within 75 rpm...
                .settleTime(0.15);      // ...for 0.15 s
        // travelTime: about 0.4 s for a full move (a servo cannot report where it is).
        PositionServo hood = PositionServo.create(hardwareMap, "hood")
                .degrees(0.10, 0, 0.90, 25)     // measured: raw 0.10 = 0 deg, raw 0.90 = 25 deg
                .travelTime(0.4);
        // Full power for 0.25 s pushes one piece into the flywheel.
        MotorFeeder feeder = MotorFeeder.create(hardwareMap, "feeder").power(1.0).feedTime(0.25);

        // Measured shots: distance (in), rpm, hood (deg). Replace with YOUR measurements.
        // Straight lines between points; outside them, the nearest end.
        ShotTable table = new ShotTable()
                .add(24, 2600, 6)
                .add(48, 3100, 14)
                .add(72, 3700, 21);

        // Spin up, wait until EVERYTHING is ready, feed, repeat. Gives up after 2.5 s (and says why).
        Launcher launcher = new Launcher(flywheel, feeder)
                .hood(hood)
                .table(table)
                .shotInterval(0.3)
                .readyTimeout(2.5);

        // shoot() does not wait: launcher.update() feeds each piece when ready.
        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G2, Btn.DPAD_UP, () -> distanceIn = Math.min(distanceIn + 6, 96))
                .bindPress(Pad.G2, Btn.DPAD_DOWN, () -> distanceIn = Math.max(distanceIn - 6, 12))
                .bindPress(Pad.G2, Btn.A, launcher::spinUp)
                .bindPress(Pad.G2, Btn.B, launcher::idle)          // cancel shots, back to idle (stopped)
                .bindPress(Pad.G2, Btn.RB, () -> launcher.shoot(1))
                .bindPress(Pad.G2, Btn.LB, () -> launcher.shoot(3))
                // Testing only (gamepad 1).
                .bindPress(Pad.G1, Btn.X, () -> manualRpm = !manualRpm)
                .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> rpm += 100)
                .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> rpm = Math.max(0, rpm - 100));

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);       // run the actions of pressed buttons
                if (manualRpm) launcher.setRpm(rpm);       // for building the table
                else launcher.setDistance(distanceIn);     // the table picks rpm + hood
                launcher.update();   // updates the flywheel, feeder and hood, and feeds when ready

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
