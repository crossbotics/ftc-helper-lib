package io.github.jasoncross.ftchelper.robot;

/**
 * A rule a module follows every loop while it is on: a turret following the target, a launcher
 * setting its speed from the distance. A button switches it ({@code g2.back.toggle(robot.turret.followMode())});
 * an action or an autonomous can too ({@code robot.turret.followMode().on()}).
 *
 * <p>Modes are for "always" rules; an {@link Action} is for steps that end.
 *
 * Docs: docs/modules/robot.md (section "Modes")
 */
public final class Mode implements Toggleable {
    private final String name;
    private boolean on;

    /**
     * @param name     shown on the Driver Station, e.g. "Turret follow"
     * @param startsOn true if it is on from the start
     */
    Mode(String name, boolean startsOn) {
        this.name = name;
        this.on = startsOn;
    }

    /** Switches it on. */
    public void on() { on = true; }

    /** Switches it off. */
    public void off() { on = false; }

    /** On if off, off if on. */
    @Override
    public void toggle() { on = !on; }

    /** True while on. */
    public boolean isOn() { return on; }

    /** E.g. "Turret follow". */
    public String name() { return name; }
}
