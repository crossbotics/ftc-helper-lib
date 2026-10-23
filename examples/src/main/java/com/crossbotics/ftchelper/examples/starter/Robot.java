// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.starter;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.hardware.BulkReads;
import com.crossbotics.ftchelper.mechanism.PositionMotor;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import com.crossbotics.ftchelper.mechanism.SpinMotor;

/**
 * STARTER KIT, file 2 of 6: the robot's mechanisms, set up once, used by TeleOp and autonomous.
 * Not an OpMode: TeleOp and autonomous each make one with "new Robot(hardwareMap)".
 * NEW SEASON: delete what you do not have, add what you build (copy a line and change it).
 * more: https://crossbotics.com/ftc-helper/docs/starter-kit/#robotjava-the-mechanisms
 */
public class Robot {
    // The mechanisms, set once in the constructor below.
    public final PositionMotor lift;
    public final PositionServo claw;
    public final SpinMotor intake;

    /** Every mechanism, for update(), stop() and telemetry. */
    public final Subsystems all;

    // Runs at "new Robot(hardwareMap)", in INIT: finds every part and sets it up. Nothing moves here.
    public Robot(HardwareMap hardwareMap) {
        // Read all sensors and encoders from the hub in one message per loop: a faster loop.
        BulkReads.auto(hardwareMap);

        // Each name in quotes ("lift", "lift_bottom", "claw", "intake") = the device's name in the robot
        // configuration on the Driver Station.
        // The lift: named heights, in encoder ticks. Measure yours with the Motor Finder.
        lift = PositionMotor.create(hardwareMap, "lift")
                // SAFETY: the software range, ticks. It still needs a physical stop at the top.
                .limits(0, 2600)
                .lowerSwitch(hardwareMap, "lift_bottom")   // optional: skipped if not configured
                .slowZone(250, 0.2)                        // near either end: slow down (ticks, power)
                .pid(0.004, 0, 0.0002, 0.05)               // P, I, D, F: starting values, tune yours
                .position("DOWN", 0)                       // named heights, ticks
                .position("LOW", 900)
                .position("HIGH", 2400)
                .tunable("Lift");                          // in the tuning menu: copy good values back here

        // The claw: a servo with named positions, 0 to 1. Measure yours with the Servo Finder.
        claw = PositionServo.create(hardwareMap, "claw")
                .position("OPEN", 0.20)
                .position("CLOSED", 0.75)
                .travelTime(0.3)                           // seconds for a full move (0 to 1)
                .tunable("Claw");

        // The intake: IN at full power, OUT at 70% so pieces are pushed out, not thrown.
        intake = SpinMotor.create(hardwareMap, "intake")
                .powers(1.0, -0.7);

        // Every mechanism, in one group. Add a new mechanism here too, or it is never updated or stopped.
        all = new Subsystems().add(lift, claw, intake);
    }
}
