package com.crossbotics.ftchelper.robot;

import java.util.List;
import java.util.Map;

/**
 * A wrist on one servo, with named angles (raw servo positions).
 *
 * <pre>{@code
 * wrist = add(Wrist.servo("wrist"));                                 // Robot.java
 *
 * public static class Wrist {                                         // Tunables.java (servo 0 to 1)
 *     public static double down = 0.15, level = 0.5, up = 0.85;
 * }
 * }</pre>
 *
 * <p>Suggested buttons: operator D-pad left/right go to the next position down/up (lowest value first).
 *
 * Docs: docs/modules/robot.md (section "Wrist")
 */
public final class Wrist extends ServoPositions<Wrist> {

    private Wrist(String servo) {
        super(servo);
    }

    /**
     * A wrist on this servo.
     *
     * @param servo the servo's configuration name
     */
    public static Wrist servo(String servo) {
        return new Wrist(servo);
    }

    @Override
    Map<String, Double> defaultPositions() {
        return positions("down", 0.15, "level", 0.5, "up", 0.85);
    }

    @Override
    boolean sortByValue() {
        return true;
    }

    /** The next position (by value; after the last comes the first). */
    public void next() {
        needBuilt(part);
        touch();
        part.next();
    }

    /** The previous position. */
    public void previous() {
        needBuilt(part);
        touch();
        part.previous();
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        Pad op = plan.operator();
        op.dpadLeft.press(this::previous).says("previous position");
        op.dpadRight.press(this::next).says("next position");
    }

    @Override
    boolean drawsLooks() {
        return true;
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        out.add(simServoLine(t, "gobilda-2000-speed"));
        if (!looks.isEmpty()) {
            lookLines(out, t);   // e.g. an arm on the lift: .looks(Shape.arm().on(lift)...)
            return;
        }
        out.add(fmt("shape.%s = arm, at=9,0,4, size=2.5x1.5x1.5, axis=left, servo=%s, pos0=-90, pos1=90   # TODO measure",
                device, device));
    }
}
