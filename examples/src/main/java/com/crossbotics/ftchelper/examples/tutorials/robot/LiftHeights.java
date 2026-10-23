// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials.robot;

/**
 * Tutorial 12: the lift's set heights, and how to step between them.
 * No hardware in this class, so tutorial 13 can test it on a laptop.
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/12-robot-structure/#2-the-set-heights-liftheights
 */
public enum LiftHeights {
    // Ticks. Keep them in order, lowest first: up() and down() step through this order.
    BOTTOM(0),
    LOW(800),
    MIDDLE(1600),
    HIGH(2400);

    /** Encoder ticks from the bottom. Measure these on your robot. */
    public final int ticks;

    LiftHeights(int ticks) {
        this.ticks = ticks;
    }

    /** One height up. HIGH stays HIGH. */
    public LiftHeights up() {
        return this == HIGH ? HIGH : values()[ordinal() + 1];
    }

    /** One height down. BOTTOM stays BOTTOM. */
    public LiftHeights down() {
        return this == BOTTOM ? BOTTOM : values()[ordinal() - 1];
    }

    /** The set height closest to a position (used after the driver moves the lift by hand). */
    public static LiftHeights closestTo(int position) {
        LiftHeights best = BOTTOM;
        for (LiftHeights h : values()) {
            if (Math.abs(h.ticks - position) < Math.abs(best.ticks - position)) best = h;
        }
        return best;
    }
}
