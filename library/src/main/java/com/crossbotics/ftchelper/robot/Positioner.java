package com.crossbotics.ftchelper.robot;

import java.util.List;
import java.util.Map;

/**
 * Any servo with named positions: a deploy servo, a fold-out pivot, a gate, a flag. No suggested
 * buttons: use it from {@code Controls.java} or an action.
 *
 * <pre>{@code
 * intakeDeploy = add(Positioner.servo("intake_deploy"));             // Robot.java
 *
 * public static class IntakeDeploy {                                  // Tunables.java (servo 0 to 1)
 *     public static double stowed = 0.0, deployed = 0.8;
 * }
 *
 * robot.intakeDeploy.goTo("deployed");                                // anywhere
 * }</pre>
 *
 * <p>For the simulator, give its servo and draw what it swings: {@code .part("gobilda-2000-speed")
 * .looks(Shape.arm().atMm(...).sizeMm(...).angle("stowed", 95).angle("deployed", -10))}. Parts that
 * ride on it ({@code Shape.on(intakeDeploy)}) fold with it.
 *
 * Docs: docs/modules/robot.md (section "Positioner")
 */
public final class Positioner extends ServoPositions<Positioner> {

    private Positioner(String servo) {
        super(servo);
    }

    /**
     * A positioner on this servo. Its Tunables section is the name in CamelCase ("intake_deploy" ->
     * {@code IntakeDeploy}).
     *
     * @param servo the servo's configuration name
     */
    public static Positioner servo(String servo) {
        return new Positioner(servo);
    }

    @Override
    Map<String, Double> defaultPositions() {
        return positions("stowed", 0.0, "deployed", 0.5);
    }

    /** The next position, in the order written in Tunables.java (after the last comes the first). */
    public void next() {
        needBuilt(part);
        touch();
        part.next();
    }

    @Override
    boolean drawsLooks() {
        return true;
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        out.add(simServoLine(t, "gobilda-2000-torque"));
        lookLines(out, t);
    }

    @Override
    boolean makesDefaultShape() {
        return false;   // it draws nothing of its own: a shape cannot ride on it without looks(...)
    }
}
