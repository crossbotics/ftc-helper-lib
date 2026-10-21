package com.crossbotics.ftchelper.robot;

import java.util.List;
import java.util.Map;

/**
 * A lift (a slide that goes up and down) on one motor, with named heights and an optional bottom
 * switch. Heights are in encoder counts.
 *
 * <pre>{@code
 * lift = add(Lift.slide("lift").bottomSwitch("lift_bottom"));   // Robot.java
 *
 * public static class Lift {                                      // Tunables.java
 *     public static double min = 0, max = 2600;                  // the safe range (hard stops)
 *     public static double kP = 0.004, kI = 0, kD = 0.0002, kF = 0.05;
 *     public static double down = 0, low = 900, high = 2400;     // named heights: any names you like
 * }
 * }</pre>
 *
 * <p>Suggested buttons (operator): A = lowest height, X = the second one, Y = the highest, D-pad
 * up/down = next height up/down, left stick Y = by hand (it holds where you let go).
 *
 * <p>A bottom switch makes the bottom always right: pressing it sets the height to 0. Until it has
 * been pressed once, the lift only goes down, slowly.
 *
 * Docs: docs/modules/robot.md (section "Lift")
 */
public final class Lift extends MotorPositions<Lift> {

    private Lift(String motor) {
        super(motor);
    }

    /**
     * A lift on this motor.
     *
     * @param motor the motor's configuration name
     */
    public static Lift slide(String motor) {
        return new Lift(motor);
    }

    /**
     * A touch sensor at the bottom of the travel.
     *
     * @param switchName its configuration name
     */
    public Lift bottomSwitch(String switchName) {
        lowerSwitchName(switchName);
        return this;
    }

    @Override
    double[] defaults() {
        // min, max, kP, kI, kD, kF, maxPower, slowZone, slowZonePower, tolerance
        return new double[] {0, 2600, 0.004, 0, 0.0002, 0.05, 1.0, 250, 0.2, 20};
    }

    @Override
    Map<String, Double> defaultPositions() {
        return positions("down", 0, "low", 900, "high", 2400);
    }

    @Override
    double countsPerUnit() {
        return 1;
    }

    @Override
    String unit() {
        return "counts";
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        Pad op = plan.operator();
        int n = order.size();
        if (n >= 1) {
            final String low = order.get(0);
            op.a.press(() -> goTo(low)).says(low);
        }
        if (n >= 3) {
            final String second = order.get(1);
            op.x.press(() -> goTo(second)).says(second);
        }
        if (n >= 2) {
            final String top = order.get(n - 1);
            op.y.press(() -> goTo(top)).says(top);
        }
        op.dpadUp.press(this::stepUp).says("next height up");
        op.dpadDown.press(this::stepDown).says("next height down");
        op.leftStickY.use(this::manual).says("by hand");
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        double[] d = defaults();
        double min = t.number("min", d[0]), max = t.number("max", d[1]);
        // The motor from .part(...); without one, a common lift motor, marked to check.
        out.add(partId != null
                ? fmt("motor.%s = lift, part=%s, min=%.0f, max=%.0f, gravity=0.06", device, partId, min, max)
                : fmt("motor.%s = lift, part=gobilda-5203-312, min=%.0f, max=%.0f, gravity=0.06   # TODO the real motor",
                        device, min, max));
        if (switchName != null) out.add(fmt("touch.%s = %s, below=20", switchName, device));
        out.add(fmt("shape.%s = slide, at=-7,0,4, size=2x8x8, axis=up, motor=%s, ticksPerIn=%.0f   # TODO measure",
                device, device, Math.max(1, max / 8)));
    }
}
