package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.launcher.Flywheel;
import io.github.jasoncross.ftchelper.launcher.Launcher;
import io.github.jasoncross.ftchelper.launcher.MotorFeeder;
import io.github.jasoncross.ftchelper.launcher.ShotTable;
import io.github.jasoncross.ftchelper.mechanism.PositionServo;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;

/**
 * Tutorial 17: a shooter. A flywheel, a hood, a feeder, and a shot table, joined by a Launcher.
 * Needs: motor "flywheel", servo "hood", motor "feeder".
 *
 * gamepad2: D-pad up/down = distance +/- 6 in, A = spin up, B = stop,
 *           right bumper = shoot 1, left bumper = shoot 3.
 * gamepad1 (testing): D-pad left/right = flywheel rpm -/+ 100 in "manual rpm" mode (X toggles).
 *
 * Tutorial: docs/tutorials/17-launcher.md
 */
@TeleOp(name = "Tutorial 17: Launcher", group = "Tutorials")
@Disabled
public class Tut17Launcher extends LinearOpMode {

    private double distanceIn = 36;
    private boolean manualRpm = false;
    private double rpm = 3000;

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);

        Flywheel flywheel = Flywheel.create(hardwareMap, "flywheel")
                .countsPerRev(28)               // bare motor encoder, flywheel on the motor shaft
                .pidf(10, 3, 0, 12)
                .tolerance(75)
                .settleTime(0.15);
        PositionServo hood = PositionServo.create(hardwareMap, "hood")
                .degrees(0.10, 0, 0.90, 25)     // measured: raw 0.10 = 0 deg, raw 0.90 = 25 deg
                .travelTime(0.4);
        MotorFeeder feeder = MotorFeeder.create(hardwareMap, "feeder").power(1.0).feedTime(0.25);

        // Measured shots: distance (in), rpm, hood (deg). Replace with YOUR measurements.
        ShotTable table = new ShotTable()
                .add(24, 2600, 6)
                .add(48, 3100, 14)
                .add(72, 3700, 21);

        Launcher launcher = new Launcher(flywheel, feeder)
                .hood(hood)
                .table(table)
                .shotInterval(0.3)
                .readyTimeout(2.5);

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G2, Btn.DPAD_UP, () -> distanceIn = Math.min(distanceIn + 6, 96))
                .bindPress(Pad.G2, Btn.DPAD_DOWN, () -> distanceIn = Math.max(distanceIn - 6, 12))
                .bindPress(Pad.G2, Btn.A, launcher::spinUp)
                .bindPress(Pad.G2, Btn.B, launcher::idle)
                .bindPress(Pad.G2, Btn.RB, () -> launcher.shoot(1))
                .bindPress(Pad.G2, Btn.LB, () -> launcher.shoot(3))
                .bindPress(Pad.G1, Btn.X, () -> manualRpm = !manualRpm)
                .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> rpm += 100)
                .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> rpm -= 100);

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                if (manualRpm) launcher.setRpm(rpm);       // for building the table
                else launcher.setDistance(distanceIn);     // the table picks rpm + hood
                launcher.update();

                t.line("Distance", "%.0f in%s", distanceIn, table.covers(distanceIn) ? "" : " (outside the table)");
                if (manualRpm) t.line("MANUAL rpm", "%.0f", rpm);
                launcher.addTelemetry(t);
                t.update();
            }
        } finally {
            launcher.stop();
        }
    }
}
