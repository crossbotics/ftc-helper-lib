// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials.robot;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm... and
// org.firstinspires...) and this library's (io.github.jasoncross...).
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
 *
 * The commands (goTo, stepUp, manual...) are quick: they only set what we want. update(), called
 * once every loop, does the work. That way a button never makes the robot wait.
 * (docs/guides/non-blocking.md explains why.)
 */
// Not an OpMode, so it never shows on the Driver Station. Robot.java makes one.
public class Lift {
    // The highest the software ever lets the lift go, in encoder ticks. Above HIGH (2400) and
    // below the real top, so the lift never runs into its physical stop under power.
    // "static final" = a fixed number that never changes.
    static final int MAX_TICKS = 2600;

    // The motor, wrapped in SafeMotor so every power goes through the safety rules.
    // "private final" = only this file uses it, and it is set once in the constructor.
    private final SafeMotor motor;
    // The PID controller that finds the power to reach a height: P = 0.004, I = 0, D = 0.0002,
    // F = 0.05 (a small steady push up against gravity). Within 20 ticks counts as "there".
    // Use the numbers you found in tutorial 07.
    private final PidController pid = new PidController(0.004, 0, 0.0002, 0.05).setTolerance(20);
    // The height we are going to, or null (= "none") when the lift is moved by hand.
    private LiftHeights target = null;   // null = by hand
    // The latest hand power from the stick, -1 to 1. update() sends it when there is no target.
    private double handPower = 0;

    // The "constructor": runs when someone writes "new Lift(hardwareMap)". It finds and sets up
    // the hardware. Nothing moves here, so it is safe in INIT.
    public Lift(HardwareMap hardwareMap) {
        // brake = holds still at power 0 (the lift does not drop); resetEncoder = this spot is 0 ticks
        // (turn the robot on with the lift DOWN); runWithoutEncoder = our code picks the power;
        // done() = power 0 and hand us the motor.
        DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
                .brake().resetEncoder().runWithoutEncoder().done();
        // Software stops at 0 and MAX_TICKS; within 250 ticks of either end, power is cut down to
        // 20% at the very end, so it does not slam into its stops.
        motor = new SafeMotor(liftMotor).setLimits(0, MAX_TICKS).setSlowZone(250, 0.2);
        // An optional bottom switch: null if the robot does not have "lift_bottom" in its configuration.
        TouchSensor bottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
        // With a switch, pressing it resets the count to 0 ("homing"); until then the lift only goes
        // down, slowly. "bottom::isPressed" is a method reference: "here is how to read the switch".
        if (bottom != null) motor.setLowerSwitch(bottom::isPressed);
    }

    /** Go to a set height and hold it. */
    public void goTo(LiftHeights height) {
        target = height;
        // Clear the PID's memory from the last move, so the new move starts fresh.
        pid.reset();
    }

    /** One set height up from where the lift is now. */
    // "a ? b : c" = if a is true use b, otherwise c. If we have a target, step up from it;
    // if the lift was moved by hand, start from the set height closest to where it is now.
    public void stepUp() {
        goTo((target != null ? target : LiftHeights.closestTo(motor.getPosition())).up());
    }

    /** One set height down from where the lift is now. */
    public void stepDown() {
        goTo((target != null ? target : LiftHeights.closestTo(motor.getPosition())).down());
    }

    /** Move by hand. Any non-zero power cancels the set height. */
    // Call it every loop with the stick value (0 when the stick is let go). It only remembers
    // the power: update() sends it.
    public void manual(double power) {
        handPower = power;
        if (power != 0) target = null;
    }

    /** Call once every loop. */
    public void update() {
        // First read the switch (and home at the bottom), so the decisions below use fresh readings.
        motor.update();
        // Before homing, the zero is not known, so a set height would be a guess: cancel it.
        if (motor.isWaitingForHoming()) target = null;
        if (target == null) {
            // By hand. Stick at rest = power 0, and the brake holds the lift.
            motor.setPower(handPower);
        } else {
            // To the set height. Runs every loop, even when there, to HOLD it against gravity.
            motor.moveTo(target.ticks, pid);
        }
    }

    /** True when holding a set height and within tolerance. */
    public boolean isAtTarget() {
        return target != null && pid.atTarget();
    }

    // SAFETY: stop the motor and forget the target and hand power, so the next update() does not
    // start it again. OpModes call this in their "finally" part.
    public void stop() {
        target = null;
        handPower = 0;
        motor.stop();
    }

    /** Adds the lift's lines to telemetry. */
    public void addTelemetry(Telemetry telemetry) {
        TelemetryHelper.section(telemetry, "Lift");
        if (motor.isWaitingForHoming()) telemetry.addLine("Push the lift down to home it.");
        // target.name() is the height's name as text, e.g. "HIGH".
        telemetry.addData("Target", target == null ? "by hand" : target.name());
        telemetry.addData("Position", motor.getPosition());
        TelemetryHelper.flag(telemetry, "At target", isAtTarget());
    }
}
