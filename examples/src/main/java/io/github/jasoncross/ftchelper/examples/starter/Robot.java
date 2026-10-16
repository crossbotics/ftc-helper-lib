package io.github.jasoncross.ftchelper.examples.starter;

import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.mechanism.PositionMotor;
import io.github.jasoncross.ftchelper.mechanism.PositionServo;
import io.github.jasoncross.ftchelper.mechanism.SpinMotor;

/**
 * STARTER KIT, file 2 of 6: the robot's mechanisms, set up once, used by TeleOp and autonomous.
 *
 * NEW SEASON: delete what you do not have, add what you build (copy a line and change it).
 * Every mechanism goes into {@code all}, so it is updated, shown and stopped for you.
 */
public class Robot {
    public final PositionMotor lift;
    public final PositionServo claw;
    public final SpinMotor intake;

    /** Every mechanism, for update(), stop() and telemetry. */
    public final Subsystems all;

    public Robot(HardwareMap hardwareMap) {
        BulkReads.auto(hardwareMap);

        lift = PositionMotor.create(hardwareMap, "lift")
                .limits(0, 2600)
                .lowerSwitch(hardwareMap, "lift_bottom")   // optional: skipped if not configured
                .slowZone(250, 0.2)
                .pid(0.004, 0, 0.0002, 0.05)
                .position("DOWN", 0)
                .position("LOW", 900)
                .position("HIGH", 2400)
                .tunable("Lift");

        claw = PositionServo.create(hardwareMap, "claw")
                .position("OPEN", 0.20)
                .position("CLOSED", 0.75)
                .travelTime(0.3)
                .tunable("Claw");

        intake = SpinMotor.create(hardwareMap, "intake")
                .powers(1.0, -0.7);

        all = new Subsystems().add(lift, claw, intake);
    }
}
