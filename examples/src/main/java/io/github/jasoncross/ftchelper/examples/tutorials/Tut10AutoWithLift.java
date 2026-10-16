package io.github.jasoncross.ftchelper.examples.tutorials;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.util.ElapsedTime;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.drive.AutoDrive;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.safety.SafeMotor;
import io.github.jasoncross.ftchelper.util.FieldPose;
import io.github.jasoncross.ftchelper.util.PoseStore;

/**
 * Tutorial 10: an autonomous that runs a mechanism while it drives, chooses its plan in INIT,
 * reacts to a stuck move, and hands its heading to TeleOp.
 *
 * In INIT: X = blue, B = red (the route mirrors), D-pad up/down = start delay.
 *
 * Tutorial: docs/tutorials/10-auto-with-mechanisms.md
 */
@Autonomous(name = "Tutorial 10: Auto with Lift", group = "Tutorials")
@Disabled
public class Tut10AutoWithLift extends LinearOpMode {

    static final int LIFT_DOWN = 0, LIFT_UP = 2400;

    private boolean red = false;
    private double delaySec = 0;
    private int liftTarget = LIFT_DOWN;

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(),
                RobotSettings.imu(hardwareMap));

        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        SafeMotor lift = new SafeMotor(liftMotor).setLimits(0, 2600).setSlowZone(250, 0.2);
        PidController liftPid = new PidController(0.004, 0, 0.0002, 0.05).setTolerance(20);

        // The lift keeps working DURING every drive move.
        auto.setLoopHook(() -> {
            lift.update();
            lift.moveTo(liftTarget, liftPid);
        });

        // ---------- INIT: choose the plan with the gamepad ----------
        ControllerBindings setup = new ControllerBindings()
                .bindPress(Pad.G1, Btn.X, () -> red = false)
                .bindPress(Pad.G1, Btn.B, () -> red = true)
                .bindPress(Pad.G1, Btn.DPAD_UP, () -> delaySec = Math.min(delaySec + 1, 10))
                .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> delaySec = Math.max(delaySec - 1, 0));
        while (opModeInInit()) {
            setup.update(gamepad1, gamepad2);
            telemetry.addData("Alliance (X blue / B red)", red ? "RED" : "BLUE");
            telemetry.addData("Start delay (D-pad)", "%.0f s", delaySec);
            telemetry.update();
        }
        if (isStopRequested()) return;

        // Red is the mirror image of blue: flip left/right directions and turns.
        double side = red ? -1 : 1;

        try {
            // Start delay: wait WITHOUT sleep(), so STOP and telemetry keep working.
            ElapsedTime delay = new ElapsedTime();
            while (opModeIsActive() && delay.seconds() < delaySec) {
                telemetry.addData("Waiting", "%.1f s", delaySec - delay.seconds());
                telemetry.update();
                idle();
            }

            liftTarget = LIFT_UP;                          // start raising...
            AutoDrive.Result r = auto.move(30, 0, 0.6);    // ...while driving
            if (r == AutoDrive.Result.STALLED) {
                auto.move(4, 180, 0.4);                    // blocked: back off and try once more
                auto.move(6, 0, 0.4);
            }
            auto.turn(90 * side, 0.5);
            auto.move(12, 0, 0.5);

            // Wait (with a time limit) until the lift is really up, still holding it.
            ElapsedTime wait = new ElapsedTime();
            while (opModeIsActive() && !liftPid.atTarget() && wait.seconds() < 2.0) {
                lift.update();
                lift.moveTo(liftTarget, liftPid);
                idle();
            }

            liftTarget = LIFT_DOWN;
            auto.move(12, 180, 0.5);                       // the hook lowers the lift on the way
            auto.turnTo(0, 0.5);
        } finally {
            lift.stop();
            // TeleOp can read this in INIT: PoseStore.consume()
            PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
        }
    }
}
