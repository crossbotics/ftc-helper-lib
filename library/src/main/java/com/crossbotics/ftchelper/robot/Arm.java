package com.crossbotics.ftchelper.robot;

import java.util.List;
import java.util.Map;

/**
 * An arm that swings on one motor, with named angles in degrees.
 *
 * <pre>{@code
 * arm = add(Arm.motor("arm", "gobilda-5203-117").gearRatio(2));   // Robot.java: 2 motor turns per arm turn
 *
 * public static class Arm {                                         // Tunables.java (degrees)
 *     public static double min = 0, max = 180;                     // the safe range
 *     public static double kP = 0.02, kI = 0, kD = 0.001, kF = 0.0;
 *     public static double stowed = 0, score = 110;                // named angles
 * }
 * }</pre>
 *
 * <p>0 degrees is where the arm is when the program starts (the encoder counts from there). Start
 * every match with the arm resting on the same stop, and measure the angles from it (so {@code min}
 * is usually 0). Suggested button: operator right stick Y moves it by hand.
 *
 * Docs: docs/modules/robot.md (section "Arm")
 */
public final class Arm extends MotorPositions<Arm> {
    private final String motorId;
    private double gearRatio = 1;

    private Arm(String motor, String motorId) {
        super(motor);
        Motors.get(motorId);   // stops here, with the list, on a typo
        this.motorId = motorId;
    }

    /**
     * An arm on this motor.
     *
     * @param motor   the motor's configuration name
     * @param motorId the motor's parts-catalog id (see {@link Motors}), e.g. "gobilda-5203-117"
     */
    public static Arm motor(String motor, String motorId) {
        return new Arm(motor, motorId);
    }

    /** Motor turns per arm turn (gears, sprockets or a belt). Default 1. */
    public Arm gearRatio(double motorTurnsPerArmTurn) {
        gearRatio = motorTurnsPerArmTurn;
        return this;
    }

    /** A touch sensor at the {@code min} end: pressing it sets the angle to 0. */
    public Arm bottomSwitch(String switchName) {
        lowerSwitchName(switchName);
        return this;
    }

    @Override
    double[] defaults() {
        // min, max, kP, kI, kD, kF, maxPower, slowZone, slowZonePower, tolerance (degrees)
        return new double[] {0, 180, 0.02, 0, 0.001, 0, 0.6, 15, 0.2, 3};
    }

    @Override
    Map<String, Double> defaultPositions() {
        return positions("stowed", 0, "score", 90);
    }

    @Override
    double countsPerUnit() {
        return Motors.get(motorId).countsPerRev * gearRatio / 360.0;
    }

    @Override
    String unit() {
        return "deg";
    }

    // Its top speed (degrees a second at full power): the motor's free speed through its gears.
    @Override
    double zoneTopSpeed() {
        return Motors.get(motorId).freeRpm / 60.0 * 360.0 / gearRatio;
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        plan.operator().rightStickY.use(this::manual).says("by hand");
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        double[] d = defaults();
        double min = t.number("min", d[0]), max = t.number("max", d[1]);
        double cpd = countsPerUnit();
        out.add(fmt("motor.%s = lift, part=%s, min=%.0f, max=%.0f, gravity=0.1", device, motorId, min * cpd, max * cpd));
        if (switchName != null) out.add(fmt("touch.%s = %s, below=20", switchName, device));
        out.add(fmt("shape.%s = arm, at=0,0,8, size=9x1.5x1.5, axis=left, motor=%s, ticksPerDeg=%.3f, start=%.0f   # TODO measure",
                device, device, cpd, min));
    }
}
