package com.crossbotics.ftchelper.robot;

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
    // onlyWhen(...): it turns on only while this is true; null = always may. And why not, for the screen.
    private java.util.function.BooleanSupplier allowed = null;
    private String notAllowedWhy = "";
    private boolean refused = false;
    private long refusedAt = 0;   // System.nanoTime() of the refused press

    /**
     * @param name     shown on the Driver Station, e.g. "Turret follow"
     * @param startsOn true if it is on from the start
     */
    Mode(String name, boolean startsOn) {
        this.name = name;
        this.on = startsOn;
    }

    /**
     * A button press ({@link #toggle()}) turns it on only while {@code check} is true (whole-robot aim:
     * only with a goal in view). A press that cannot turn it on leaves it off: with {@code .rumble()} on
     * the button, one buzz (off) instead of two. Once on it stays on. Code that calls {@link #on()}
     * is not checked (an action may switch it on before the goal is in view).
     */
    void onlyWhen(java.util.function.BooleanSupplier check, String why) {
        allowed = check;
        notAllowedWhy = why;
    }

    /** True if the last try to switch it on was refused ({@link #whyRefused()} says why). */
    public boolean wasRefused() { return refused; }

    /** Why it could not be switched on, e.g. "no goal in view" ("" if it could). */
    public String whyRefused() { return refused ? notAllowedWhy : ""; }

    /** Switches it on (from code: never refused). */
    public void on() {
        on = true;
        refused = false;
    }

    /** Switches it off. */
    public void off() {
        on = false;
        refused = false;
    }

    /** On if off, off if on. */
    @Override
    public void toggle() {
        if (on) {
            off();
            return;
        }
        refused = allowed != null && !allowed.getAsBoolean();
        if (refused) refusedAt = System.nanoTime();
        else on = true;
    }

    // True for 3 s after a refused press (the Driver Station's warning).
    boolean refusedLately() {
        return refused && System.nanoTime() - refusedAt < 3_000_000_000L;
    }

    /** True while on. */
    public boolean isOn() { return on; }

    /** E.g. "Turret follow". */
    public String name() { return name; }
}
