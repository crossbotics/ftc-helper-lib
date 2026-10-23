// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.starter;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm...)
// and this library's (com.crossbotics...).
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.hardware.BulkReads;
import com.crossbotics.ftchelper.mechanism.PositionMotor;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import com.crossbotics.ftchelper.mechanism.SpinMotor;

/**
 * STARTER KIT, file 2 of 6: the robot's mechanisms, set up once, used by TeleOp and autonomous.
 *
 * NEW SEASON: delete what you do not have, add what you build (copy a line and change it).
 * Every mechanism goes into {@code all}, so it is updated, shown and stopped for you.
 *
 * Tutorial 12 explains why a robot is split into files like this (docs/tutorials/12-robot-structure.md),
 * and docs/modules/mechanism.md explains each mechanism helper.
 */
// Not an OpMode: it never shows on the Driver Station. TeleOp and autonomous each make one
// with "new Robot(hardwareMap)" and then use robot.lift, robot.claw and robot.intake.
public class Robot {
    // The mechanisms. "public" = TeleOp and autonomous can use them. "final" = each is set once,
    // in the constructor below, and never swapped for another one.
    public final PositionMotor lift;
    public final PositionServo claw;
    public final SpinMotor intake;

    /** Every mechanism, for update(), stop() and telemetry. */
    public final Subsystems all;

    // The "constructor": the code that runs when someone writes "new Robot(hardwareMap)".
    // It finds every part and sets it up. Nothing moves here, so it is safe to run in INIT.
    public Robot(HardwareMap hardwareMap) {
        // Read all sensors and encoders from the hub in one message per loop: a faster loop.
        BulkReads.auto(hardwareMap);

        // Each name in quotes ("lift", "lift_bottom", "claw", "intake") = the device's name in the robot
        // configuration on the Driver Station.
        // The lift: a motor that goes to named heights and holds them. Numbers are encoder ticks
        // (counts of how far the motor shaft has turned). Measure yours with the Hardware Check.
        lift = PositionMotor.create(hardwareMap, "lift")
                // The safe range: the software never drives it below 0 or above 2600 ticks.
                // It still needs a real, physical stop at the top (a rule, and a good idea).
                .limits(0, 2600)
                // A bottom switch (touch sensor). Pressing it resets the count to 0, which fixes
                // drift. Until it is pressed once, the lift only goes down, slowly.
                .lowerSwitch(hardwareMap, "lift_bottom")   // optional: skipped if not configured
                // Within 250 ticks of either end, power is cut, down to 20% right at the end,
                // so the lift does not slam into its stops.
                .slowZone(250, 0.2)
                // PID gains: how hard it pushes toward a height. P = 0.004 (push harder when far),
                // I = 0 (not used), D = 0.0002 (calms it so it does not overshoot),
                // F = 0.05 (a small steady push up, to hold the lift against gravity).
                // Starting values: tune yours (docs/tutorials/07-pid-lift.md).
                .pid(0.004, 0, 0.0002, 0.05)
                // Named heights, in ticks. Buttons and autonomous steps use these names.
                .position("DOWN", 0)
                .position("LOW", 900)
                .position("HIGH", 2400)
                // Puts the gains and heights in the tuning menu as "Lift/...", so you can adjust
                // them from the gamepad in PRACTICE. Copy good values back here (docs/guides/tuning.md).
                .tunable("Lift");

        // The claw: a servo with two named positions. Servo positions go from 0 to 1.
        // Find yours with the Hardware Check (move the servo, read the number).
        claw = PositionServo.create(hardwareMap, "claw")
                .position("OPEN", 0.20)
                .position("CLOSED", 0.75)
                // About how many seconds the servo takes to move the whole way (0 to 1). A servo cannot
                // say where it is, so this time is used to guess when the claw has finished moving.
                .travelTime(0.3)
                .tunable("Claw");

        // The intake: a motor that spins IN, OUT or stops.
        // IN at full power to grab quickly; OUT at 70% so pieces are pushed out, not thrown.
        intake = SpinMotor.create(hardwareMap, "intake")
                .powers(1.0, -0.7);

        // Put every mechanism in one group, so one line updates, stops or shows them all.
        // If you add a mechanism above, add it here too, or it will never get updated or stopped.
        all = new Subsystems().add(lift, claw, intake);
    }
}
