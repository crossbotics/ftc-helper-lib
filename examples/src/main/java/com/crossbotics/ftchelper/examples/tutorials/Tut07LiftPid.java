// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;

/**
 * Tutorial 07: hold a lift at a height with PID, and tune the PID live from the gamepad.
 * Needs a motor "lift" with an encoder. Turn the robot on with the lift at the BOTTOM.
 * A / X / Y = bottom / middle / top. D-pad left/right = pick a gain, up/down = change it.
 * WARNING: no software limits yet (tutorial 08). Keep a hand near STOP.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/07-pid-lift/
 */
@TeleOp(name = "Tutorial 07: Lift PID Tuner", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut07LiftPid extends LinearOpMode {

    // 0 = where the lift was at INIT. TOP must be below the real top of the lift.
    static final int BOTTOM = 0, MIDDLE = 1200, TOP = 2400;   // encoder ticks: measure yours

    static final String[] GAIN_NAMES = {"kP", "kI", "kD", "kF"};

    // kP, kI, kD, kF. The D-pad changes the numbers inside.
    private final double[] gains = {0.002, 0.0, 0.0, 0.0};   // start: small P only
    private int selected = 0;   // which gain the D-pad changes: 0 = kP ... 3 = kF
    private int target = BOTTOM;   // ticks

    @Override
    public void runOpMode() {
        DcMotorEx lift = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();

        PidController pid = new PidController(gains[0], gains[1], gains[2], gains[3])
                .setOutputLimits(-0.4, 0.7)     // gentle while tuning: down at 40%, up at 70%
                .setIntegralLimit(0.2)          // I adds at most 0.2 power (no windup)
                .setTolerance(20);              // ticks

        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> newTarget(BOTTOM, pid))
                .bindPress(Pad.G1, Btn.X, () -> newTarget(MIDDLE, pid))
                .bindPress(Pad.G1, Btn.Y, () -> newTarget(TOP, pid))
                // D-pad right / left: next / previous gain, wrapping round.
                .bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> selected = (selected + 1) % gains.length)
                .bindPress(Pad.G1, Btn.DPAD_LEFT, () -> selected = (selected + gains.length - 1) % gains.length)
                .bindPress(Pad.G1, Btn.DPAD_UP, () -> changeGain(pid, true))
                .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> changeGain(pid, false));

        waitForStart();
        controls.ignoreHeldButtons(gamepad1, gamepad2);
        try {
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                // Every loop, even at the target: that is what holds the lift up.
                double power = pid.calculate(target, lift.getCurrentPosition());
                lift.setPower(power);

                TelemetryHelper.section(telemetry, "Lift");
                telemetry.addData("Target", target);
                telemetry.addData("Position", lift.getCurrentPosition());
                TelemetryHelper.value(telemetry, "Error", pid.getLastError(), 0);
                TelemetryHelper.value(telemetry, "Power", power, 2);
                TelemetryHelper.flag(telemetry, "At target", pid.atTarget());
                TelemetryHelper.section(telemetry, "Gains (D-pad)");
                // The chosen gain gets a "> " in front.
                for (int i = 0; i < gains.length; i++) {
                    telemetry.addData((i == selected ? "> " : "  ") + GAIN_NAMES[i], "%.5f", gains[i]);
                }
                telemetry.update();
            }
        } finally {
            lift.setPower(0);
        }
    }

    // Only sets the new height: the loop does the moving.
    private void newTarget(int ticks, PidController pid) {
        target = ticks;
        pid.reset();   // forget the old I and D memory
    }

    // D-pad up (up = true) and down. The PID uses the new gains at once.
    private void changeGain(PidController pid, boolean up) {
        if (selected == 3) {
            // F never goes below 0 (it would push the lift DOWN).
            gains[3] = Math.max(0, gains[3] + (up ? 0.01 : -0.01));   // F: small steps up and down
        } else if (gains[selected] == 0) {
            // 0 x 1.25 is still 0: jump to a small value first.
            gains[selected] = up ? (selected == 0 ? 0.001 : 0.0001) : 0;   // start from a small value
        } else {
            gains[selected] *= up ? 1.25 : 0.8;
        }
        pid.setGains(gains[0], gains[1], gains[2], gains[3]);
    }
}
