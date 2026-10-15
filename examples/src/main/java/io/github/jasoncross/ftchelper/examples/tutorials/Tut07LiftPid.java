package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 07: hold a lift at a height with PID, and tune the PID live from the gamepad.
 * Needs a motor "lift" with an encoder. Turn the robot on with the lift at the BOTTOM.
 *
 * A = bottom, X = middle, Y = top.
 * D-pad left/right = choose which gain to change (P, I, D, F).
 * D-pad up/down = make that gain bigger/smaller (x1.25, or +0.01 for F).
 * Write the final numbers down; they are lost when the OpMode stops.
 *
 * Tutorial: docs/tutorials/07-pid-lift.md
 */
@TeleOp(name = "Tutorial 07: Lift PID Tuner", group = "Tutorials")
@Disabled
public class Tut07LiftPid extends LinearOpMode {

    static final int BOTTOM = 0, MIDDLE = 1200, TOP = 2400;   // encoder ticks: measure yours

    static final String[] GAIN_NAMES = {"kP", "kI", "kD", "kF"};

    private final double[] gains = {0.002, 0.0, 0.0, 0.0};   // start: small P only
    private int selected = 0;
    private int target = BOTTOM;

    @Override
    public void runOpMode() {
        DcMotorEx lift = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();

        PidController pid = new PidController(gains[0], gains[1], gains[2], gains[3])
                .setOutputLimits(-0.4, 0.7)     // gentle while tuning: down at 40%, up at 70%
                .setIntegralLimit(0.2)
                .setTolerance(20);

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> newTarget(BOTTOM, pid))
                .bindPress(Pad.G1, Btn.X, () -> newTarget(MIDDLE, pid))
                .bindPress(Pad.G1, Btn.Y, () -> newTarget(TOP, pid))
                .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> selected = (selected + 1) % gains.length)
                .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> selected = (selected + gains.length - 1) % gains.length)
                .bindPress(Pad.G1, Btn.DPAD_UP, () -> changeGain(pid, true))
                .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> changeGain(pid, false));

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                double power = pid.calculate(target, lift.getCurrentPosition());
                lift.setPower(power);

                TelemetryHelper.section(telemetry, "Lift");
                telemetry.addData("Target", target);
                telemetry.addData("Position", lift.getCurrentPosition());
                TelemetryHelper.value(telemetry, "Error", pid.getLastError(), 0);
                TelemetryHelper.value(telemetry, "Power", power, 2);
                TelemetryHelper.flag(telemetry, "At target", pid.atTarget());
                TelemetryHelper.section(telemetry, "Gains (D-pad)");
                for (int i = 0; i < gains.length; i++) {
                    telemetry.addData((i == selected ? "> " : "  ") + GAIN_NAMES[i], "%.5f", gains[i]);
                }
                telemetry.update();
            }
        } finally {
            lift.setPower(0);
        }
    }

    private void newTarget(int ticks, PidController pid) {
        target = ticks;
        pid.reset();   // forget the old I and D memory
    }

    private void changeGain(PidController pid, boolean up) {
        if (selected == 3) {
            gains[3] += up ? 0.01 : -0.01;                    // F: small steps up and down
        } else if (gains[selected] == 0) {
            gains[selected] = up ? (selected == 0 ? 0.001 : 0.0001) : 0;   // start from a small value
        } else {
            gains[selected] *= up ? 1.25 : 0.8;
        }
        pid.setGains(gains[0], gains[1], gains[2], gains[3]);
    }
}
