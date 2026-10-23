// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials.robot;

// com.qualcomm... and org.firstinspires... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * Tutorial 12: the lift as one "subsystem" class: its hardware, limits, PID and modes.
 * OpModes only say WHAT to do ("go to HIGH"). The commands only set what we want;
 * update(), once every loop, does the work, so a button never makes the robot wait.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/12-robot-structure/#3-a-subsystem-lift
 */
public class Lift {
    // Ticks: above HIGH and below the real top of the lift.
    static final int MAX_TICKS = 2600;

    private final SafeMotor motor;
    // kP, kI, kD, kF: use the numbers you found in tutorial 07.
    private final PidController pid = new PidController(0.004, 0, 0.0002, 0.05).setTolerance(20);
    private LiftHeights target = null;   // null = by hand
    private double handPower = 0;        // the stick, -1 to 1: update() sends it when there is no target

    // Nothing moves here, so it is safe in INIT.
    public Lift(HardwareMap hardwareMap) {
        // Turn the robot on with the lift DOWN: this spot is 0 ticks.
        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        motor = new SafeMotor(liftMotor).setLimits(0, MAX_TICKS).setSlowZone(250, 0.2);
        // Optional. Until it is pressed once, the lift only goes down, slowly.
        TouchSensor bottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
        if (bottom != null) motor.setLowerSwitch(bottom::isPressed);
    }

    /** Go to a set height and hold it. */
    public void goTo(LiftHeights height) {
        target = height;
        pid.reset();   // the new move starts fresh
    }

    /** One set height up from the target, or from the closest set height after hand moves. */
    public void stepUp() {
        goTo((target != null ? target : LiftHeights.closestTo(motor.getPosition())).up());
    }

    /** One set height down from where the lift is now. */
    public void stepDown() {
        goTo((target != null ? target : LiftHeights.closestTo(motor.getPosition())).down());
    }

    /** Move by hand, every loop with the stick value. Any non-zero power cancels the set height. */
    public void manual(double power) {
        handPower = power;
        if (power != 0) target = null;
    }

    /** Call once every loop. */
    public void update() {
        motor.update();   // first: the switch and homing
        if (motor.isWaitingForHoming()) target = null;   // zero not known yet
        if (target == null) {
            motor.setPower(handPower);
        } else {
            // Every loop, even when there: that holds the lift up.
            motor.moveTo(target.ticks, pid);
        }
    }

    /** True when holding a set height and within tolerance. */
    public boolean isAtTarget() {
        return target != null && pid.atTarget();
    }

    // SAFETY: OpModes call this in "finally". Forgets the target so update() does not restart it.
    public void stop() {
        target = null;
        handPower = 0;
        motor.stop();
    }

    /** Adds the lift's lines to telemetry. */
    public void addTelemetry(Telemetry telemetry) {
        TelemetryHelper.section(telemetry, "Lift");
        if (motor.isWaitingForHoming()) telemetry.addLine("Push the lift down to home it.");
        telemetry.addData("Target", target == null ? "by hand" : target.name());
        telemetry.addData("Position", motor.getPosition());
        TelemetryHelper.flag(telemetry, "At target", isAtTarget());
    }
}
