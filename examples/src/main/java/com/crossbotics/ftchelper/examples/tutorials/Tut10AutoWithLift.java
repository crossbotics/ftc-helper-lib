package com.crossbotics.ftchelper.examples.tutorials;

// com.qualcomm... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.drive.AutoDrive;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.hardware.BulkReads;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.util.PoseStore;

/**
 * Tutorial 10: an autonomous that runs a lift while it drives, chooses its plan in INIT,
 * reacts to a stuck move, and hands its heading to TeleOp.
 * In INIT: X = blue, B = red (the route mirrors), D-pad up/down = start delay.
 * Start the robot with the lift DOWN. Change the lift numbers to fit YOUR lift (tutorials 07, 08).
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/10-auto-with-mechanisms/
 */
@Autonomous(name = "Tutorial 10: Auto with Lift", group = "Tutorials")
@Disabled // hides this program on the Driver Station: delete this line in your copy
public class Tut10AutoWithLift extends LinearOpMode {

    static final int LIFT_DOWN = 0, LIFT_UP = 2400;   // encoder ticks: measure yours

    // Fields, because the button actions and the loop hook change or read them.
    private boolean red = false;         // which alliance: false = blue, true = red
    private double delaySec = 0;         // how long to wait after START, seconds (0 to 10)
    private int liftTarget = LIFT_DOWN;  // where the lift should go, ticks. Changing it moves the lift.

    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(),
                RobotSettings.imu(hardwareMap));

        // "lift" = the name in the robot configuration. The encoder starts at 0 here.
        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        // Soft limits 0 to 2600 ticks, slow near the ends. The lift also needs a real hard stop.
        SafeMotor lift = new SafeMotor(liftMotor).setLimits(0, 2600).setSlowZone(250, 0.2);
        PidController liftPid = new PidController(0.004, 0, 0.0002, 0.05).setTolerance(20);   // tutorial 07

        // Runs inside every move and turn, so the lift keeps working while the robot drives.
        // It must be quick and never wait.
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
        // Instead of waitForStart(): the drivers can change the plan until START.
        while (opModeInInit()) {
            setup.update(gamepad1, gamepad2);
            telemetry.addData("Alliance (X blue / B red)", red ? "RED" : "BLUE");
            telemetry.addData("Start delay (D-pad)", "%.0f s", delaySec);
            telemetry.update();
        }
        if (isStopRequested()) return;

        // Red is the mirror image of blue: multiply every turn and sideways move by side.
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
            auto.turn(90 * side, 0.5);                     // blue: left 90, red: right 90
            auto.move(12, 0, 0.5);

            // Wait for the lift, with a time limit. No move is running, so this loop works the lift.
            ElapsedTime wait = new ElapsedTime();
            while (opModeIsActive() && !liftPid.atTarget() && wait.seconds() < 2.0) {
                lift.update();
                lift.moveTo(liftTarget, liftPid);
                idle();
            }

            // (Here a real auto would score: open a claw, run an intake...)
            liftTarget = LIFT_DOWN;
            auto.move(12, 180, 0.5);                       // the hook lowers the lift on the way
            auto.turnTo(0, 0.5);                           // face the start direction
        } finally {
            lift.stop();
            // For TeleOp's field-centric driving: only the heading is known, so x and y are 0.
            PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
        }
    }
}
