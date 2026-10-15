package io.github.jasoncross.ftchelper.examples.tutorials.robot;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.TouchSensor;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.hardware.Hw;
import io.github.jasoncross.ftchelper.hardware.MotorSetup;
import io.github.jasoncross.ftchelper.safety.SafeMotor;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * Tutorial 12: the lift as one "subsystem" class. Everything about the lift is here:
 * its hardware, its limits, its PID, and its modes. OpModes only say WHAT to do
 * ("go to HIGH", "move by hand"), never HOW.
 */
public class Lift {
    static final int MAX_TICKS = 2600;

    private final SafeMotor motor;
    private final PidController pid = new PidController(0.004, 0, 0.0002, 0.05).setTolerance(20);
    private LiftHeights target = null;   // null = by hand
    private double handPower = 0;

    public Lift(HardwareMap hardwareMap) {
        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        motor = new SafeMotor(liftMotor).setLimits(0, MAX_TICKS).setSlowZone(250, 0.2);
        TouchSensor bottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
        if (bottom != null) motor.setLowerSwitch(bottom::isPressed);
    }

    /** Go to a set height and hold it. */
    public void goTo(LiftHeights height) {
        target = height;
        pid.reset();
    }

    /** One set height up from where the lift is now. */
    public void stepUp() {
        goTo((target != null ? target : LiftHeights.closestTo(motor.getPosition())).up());
    }

    /** One set height down from where the lift is now. */
    public void stepDown() {
        goTo((target != null ? target : LiftHeights.closestTo(motor.getPosition())).down());
    }

    /** Move by hand. Any non-zero power cancels the set height. */
    public void manual(double power) {
        handPower = power;
        if (power != 0) target = null;
    }

    /** Call once every loop. */
    public void update() {
        motor.update();
        if (motor.isWaitingForHoming()) target = null;
        if (target == null) {
            motor.setPower(handPower);
        } else {
            motor.moveTo(target.ticks, pid);
        }
    }

    /** True when holding a set height and within tolerance. */
    public boolean isAtTarget() {
        return target != null && pid.atTarget();
    }

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
