package com.crossbotics.ftchelper.robot;

import java.util.List;
import java.util.Map;

/**
 * A claw on one servo: {@code open} and {@code closed}, plus any other positions you name.
 *
 * <pre>{@code
 * claw = add(Claw.servo("claw"));                                   // Robot.java
 *
 * public static class Claw {                                         // Tunables.java (servo 0 to 1)
 *     public static double open = 0.20, closed = 0.75;
 * }
 * }</pre>
 *
 * <p>Suggested button: operator B opens and closes it.
 *
 * Docs: docs/modules/robot.md (section "Claw")
 */
public final class Claw extends ServoPositions<Claw> implements Toggleable {

    private Claw(String servo) {
        super(servo);
    }

    /**
     * A claw on this servo.
     *
     * @param servo the servo's configuration name
     */
    public static Claw servo(String servo) {
        return new Claw(servo);
    }

    @Override
    Map<String, Double> defaultPositions() {
        return positions("open", 0.20, "closed", 0.75);
    }

    /** Opens it (goes to "open"). */
    public void open() {
        goTo("open");
    }

    /** Closes it (goes to "closed"). */
    public void close() {
        goTo("closed");
    }

    /** Closes it if it is open, else opens it. */
    @Override
    public void toggle() {
        if (isAt("open")) close();
        else open();
    }

    /** True if its last command was "closed". */
    public boolean isClosed() {
        return isAt("closed");
    }

    @Override
    void build(Devices devices, TunableSection t) {
        super.build(devices, t);
        if (findName(order, "open") == null || findName(order, "closed") == null) {
            // A typo ("closd") gets a "did you mean": the claw's two names are fixed, so the fix is clear.
            StringBuilder hint = new StringBuilder();
            for (String want : new String[] {"open", "closed"}) {
                if (findName(order, want) != null) continue;
                for (String have : order) {
                    if (want.equals(com.crossbotics.ftchelper.util.Spelling.closest(have, java.util.Collections.singletonList(want)))) {
                        hint.append(hint.length() == 0 ? " (" : ", ").append(have).append(" -> ").append(want).append("?");
                    }
                }
            }
            if (hint.length() > 0) hint.append(")");
            throw new IllegalStateException("Tunables." + name() + " needs \"open\" and \"closed\" (it has " + order + ")" + hint);
        }
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        plan.operator().b.toggle(this).says("open / close");
    }

    @Override
    boolean drawsLooks() {
        return true;
    }

    // Its claw shape opens with the servo (the gap is a guess: measure it in the robot file); an arm
    // shape swings like a positioner's.
    @Override
    String driverFor(Shape s, TunableSection t) {
        if (s.type.equals("claw")) return "servo=" + device + ", pos0=0, pos1=3";
        return super.driverFor(s, t);
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        Map<String, Double> p = positionsFrom(t);
        Double closed = p.get("closed"), open = p.get("open");
        if (closed != null && open != null) {
            closed = physical(closed);
            open = physical(open);
        }
        out.add(simServoLine(t, "gobilda-2000-torque"));
        if (looks.isEmpty()) {
            out.add(fmt("shape.%s = claw, at=8,0,4, size=2x0.5x2, servo=%s, pos0=0, pos1=3   # TODO measure", device, device));
        } else {
            lookLines(out, t);   // e.g. on a wrist: .looks(Shape.claw().on(wrist)...)
        }
        if (closed != null && open != null) {
            // The claw holds a piece while the servo is on the "closed" side of half way.
            double mid = (closed + open) / 2;
            out.add(fmt("grab.%s = shape=%s, servo=%s, %s=%.2f", device, firstShapeName(), device,
                    closed < open ? "closedBelow" : "closedAbove", mid));
        }
    }
}
