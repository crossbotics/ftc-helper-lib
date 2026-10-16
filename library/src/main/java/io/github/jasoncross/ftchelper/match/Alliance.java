package io.github.jasoncross.ftchelper.match;

/**
 * Red or blue.
 *
 * <p>Many fields are mirror images: what blue does turning LEFT, red does turning RIGHT.
 * Write your routes for BLUE, and {@link #mirror(double)} flips them for RED.
 * (Picture the field folded down the middle: blue's left side lands on red's right side.)
 *
 * <pre>{@code
 * auto.turn(alliance.mirror(90), 0.5);       // blue: left 90, red: right 90
 * int goalTag = alliance.pick(20, 24);       // blue value, red value
 * }</pre>
 *
 * <p>Check each season's field: if it is ROTATED instead of mirrored, forward and back
 * flip too, and you need your own rule. {@code AutoRoutine.mirrorFor(...)} uses this class.
 * See docs/guides/new-season.md.
 *
 * <p>Java note: an {@code enum} is a fixed list of named choices. An Alliance can only ever be
 * BLUE or RED, so a typo cannot create a third one. An enum can also have methods, like the ones below.
 *
 * Docs: docs/modules/match.md (section "Alliance")
 */
public enum Alliance {
    BLUE,
    RED;

    /** The other alliance. */
    public Alliance other() {
        // "a ? b : c" means "if a then b, else c".
        return this == BLUE ? RED : BLUE;
    }

    /** +1 for blue, -1 for red. Handy to multiply a left/right number by. */
    public int sign() {
        return this == BLUE ? 1 : -1;
    }

    /**
     * Mirrors a left/right value written for BLUE: unchanged for blue, negated for red.
     * Use it for turns, sideways directions and headings.
     * Example: {@code RED.mirror(90)} is -90 (a left turn becomes a right turn).
     */
    public double mirror(double blueValue) {
        // Mirroring left and right is the same as flipping the sign: +90 (left) becomes -90 (right).
        return this == BLUE ? blueValue : -blueValue;
    }

    /**
     * One value for blue, another for red.
     * The {@code <T>} means it works with any kind of value (text, a pose, a number...),
     * as long as both are the same kind.
     */
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
