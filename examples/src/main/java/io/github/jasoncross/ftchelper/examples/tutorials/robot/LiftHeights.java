package io.github.jasoncross.ftchelper.examples.tutorials.robot;

/**
 * Tutorial 12: the lift's set heights, and how to step between them.
 * No hardware in this class, so tutorial 13 can test it on a laptop.
 */
public enum LiftHeights {
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
