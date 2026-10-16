package io.github.jasoncross.ftchelper.match;

/**
 * Red or blue.
 *
 * <p>Many fields are mirror images: what blue does turning LEFT, red does turning RIGHT.
 * Write your routes for BLUE, and {@link #mirror(double)} flips them for RED.
 *
 * <pre>{@code
 * auto.turn(alliance.mirror(90), 0.5);       // blue: left 90, red: right 90
 * int goalTag = alliance.pick(20, 24);       // blue value, red value
 * }</pre>
 *
 * <p>Check each season's field: if it is ROTATED instead of mirrored, forward and back
 * flip too, and you need your own rule. {@code AutoRoutine.mirrorFor(...)} uses this class.
 *
 * Docs: docs/modules/match.md
 */
public enum Alliance {
    BLUE,
    RED;

    /** The other alliance. */
    public Alliance other() {
        return this == BLUE ? RED : BLUE;
    }

    /** +1 for blue, -1 for red. */
    public int sign() {
        return this == BLUE ? 1 : -1;
    }

    /**
     * Mirrors a left/right value written for BLUE: unchanged for blue, negated for red.
     * Use it for turns, sideways directions and headings.
     */
    public double mirror(double blueValue) {
        return this == BLUE ? blueValue : -blueValue;
    }

    /** One value for blue, another for red. */
    public <T> T pick(T blue, T red) {
        return this == BLUE ? blue : red;
    }

    /** Same as {@link #pick(Object, Object)} for whole numbers. */
    public int pick(int blue, int red) {
        return this == BLUE ? blue : red;
    }

    /** Same as {@link #pick(Object, Object)} for numbers. */
    public double pick(double blue, double red) {
        return this == BLUE ? blue : red;
    }
}
