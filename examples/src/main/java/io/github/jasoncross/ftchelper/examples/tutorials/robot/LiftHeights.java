// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials.robot;

/**
 * Tutorial 12: the lift's set heights, and how to step between them.
 * No hardware in this class, so tutorial 13 can test it on a laptop.
 */
// An "enum" is a fixed list of named choices. Here the choices are the four lift heights, so code
// can say LiftHeights.HIGH instead of a bare number, and a typo is caught when the code is built.
public enum LiftHeights {
    // Each name with its height in encoder ticks (in brackets). Keep them in order, lowest first:
    // up() and down() below step through them in this order.
    BOTTOM(0),
    LOW(800),
    MIDDLE(1600),
    HIGH(2400);

    /** Encoder ticks from the bottom. Measure these on your robot. */
    // "final" = each height's number is fixed once it is made.
    public final int ticks;

    // Makes each height above with its number (Java runs this once for each name in the list).
    LiftHeights(int ticks) {
        this.ticks = ticks;
    }

    /** One height up. HIGH stays HIGH. */
    // values() is the list of all heights in order; ordinal() is this height's place in it
    // (BOTTOM = 0, LOW = 1, ...). So values()[ordinal() + 1] is the next one up.
    // "a ? b : c" = if a is true use b, otherwise c: at HIGH there is no next one, so stay.
    public LiftHeights up() {
        return this == HIGH ? HIGH : values()[ordinal() + 1];
    }

    /** One height down. BOTTOM stays BOTTOM. */
    public LiftHeights down() {
        return this == BOTTOM ? BOTTOM : values()[ordinal() - 1];
    }

    /** The set height closest to a position (used after the driver moves the lift by hand). */
    // "static" = you call it on the list itself (LiftHeights.closestTo(1500)), not on one height.
    public static LiftHeights closestTo(int position) {
        // Start with BOTTOM as the best guess, then check every height.
        LiftHeights best = BOTTOM;
        // "for (LiftHeights h : values())" = "for each height h in the list".
        for (LiftHeights h : values()) {
            // Math.abs = the distance, ignoring above or below. Keep h if it is closer than the best so far.
            if (Math.abs(h.ticks - position) < Math.abs(best.ticks - position)) best = h;
        }
        return best;
    }
}
