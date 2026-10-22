package com.crossbotics.ftchelper.teleop;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Trigger;

/**
 * One button or trigger on one gamepad, e.g. gamepad 1's BACK. {@link LibraryButtons} uses it to say
 * where each of the library's own jobs goes (pause, slow, field-centric...).
 *
 * <pre>{@code
 * lib.fieldCentric(Key.g1(Btn.X));        // field-centric on/off moves to gamepad 1 X
 * lib.slow(Key.g1(Trigger.RT));           // slow on the right trigger
 * }</pre>
 *
 * <p>In {@code Controls.java} you do not need this class: write the button the usual way,
 * {@code lib.fieldCentric(g1.x)} (each button there is a Key too).
 *
 * <p>Two Keys are equal when they are the same button on the same gamepad.
 *
 * Docs: docs/modules/teleop.md (section "Moving the library's buttons"), docs/guides/buttons.md
 */
public class Key {
    final ControllerBindings.Pad pad;
    final Btn btn;           // null for a trigger
    final Trigger trigger;   // null for a button

    /**
     * A button ({@code trigger} null) or a trigger ({@code btn} null) on one gamepad. Use
     * {@link #g1(Btn)} and friends instead; this is for the library's own buttons in Controls.java.
     */
    protected Key(ControllerBindings.Pad pad, Btn btn, Trigger trigger) {
        if (pad == null || (btn == null) == (trigger == null)) {
            throw new IllegalArgumentException("a Key is one gamepad and one button or trigger");
        }
        this.pad = pad;
        this.btn = btn;
        this.trigger = trigger;
    }

    /** A button on gamepad 1, e.g. {@code Key.g1(Btn.BACK)}. */
    public static Key g1(Btn btn) {
        return new Key(ControllerBindings.Pad.G1, btn, null);
    }

    /** A button on gamepad 2. */
    public static Key g2(Btn btn) {
        return new Key(ControllerBindings.Pad.G2, btn, null);
    }

    /** A trigger on gamepad 1, e.g. {@code Key.g1(Trigger.LT)}. */
    public static Key g1(Trigger trigger) {
        return new Key(ControllerBindings.Pad.G1, null, trigger);
    }

    /** A trigger on gamepad 2. */
    public static Key g2(Trigger trigger) {
        return new Key(ControllerBindings.Pad.G2, null, trigger);
    }

    /** Which gamepad. */
    public final ControllerBindings.Pad pad() {
        return pad;
    }

    /** The button, or null for a trigger. */
    public final Btn btn() {
        return btn;
    }

    /** The trigger, or null for a button. */
    public final Trigger trigger() {
        return trigger;
    }

    /** True for LT or RT. */
    public final boolean isTrigger() {
        return trigger != null;
    }

    /**
     * True while it is down: a button pressed, a trigger pulled past half way. False if that gamepad
     * is missing (null).
     */
    public final boolean isDown(Gamepad gamepad1, Gamepad gamepad2) {
        return pull(gamepad1, gamepad2) > 0.5;
    }

    /** How far it is pressed, 0 to 1: a trigger's pull, or 0/1 for a button. 0 with no gamepad. */
    public final double pull(Gamepad gamepad1, Gamepad gamepad2) {
        Gamepad g = pad == ControllerBindings.Pad.G1 ? gamepad1 : gamepad2;
        if (g == null) return 0;
        if (trigger != null) return trigger == Trigger.LT ? g.left_trigger : g.right_trigger;
        return down(g, btn) ? 1 : 0;
    }

    // Is this button down on this gamepad? (The paddles M1/M2 and the touchpad are not read here.)
    static boolean down(Gamepad g, Btn btn) {
        switch (btn) {
            case A: return g.a;
            case B: return g.b;
            case X: return g.x;
            case Y: return g.y;
            case LB: return g.left_bumper;
            case RB: return g.right_bumper;
            case BACK: return g.back;
            case START: return g.start;
            case GUIDE: return g.guide;
            case DPAD_UP: return g.dpad_up;
            case DPAD_DOWN: return g.dpad_down;
            case DPAD_LEFT: return g.dpad_left;
            case DPAD_RIGHT: return g.dpad_right;
            case L_STICK_BTN: return g.left_stick_button;
            case R_STICK_BTN: return g.right_stick_button;
            default: return false;
        }
    }

    /** The button's name as the button map writes it: "BACK", "LT", "D-pad up", "left stick click". */
    public final String name() {
        if (trigger != null) return trigger.name();
        switch (btn) {
            case LB: case RB: case BACK: case START: case GUIDE: case A: case B: case X: case Y: return btn.name();
            case DPAD_UP: return "D-pad up";
            case DPAD_DOWN: return "D-pad down";
            case DPAD_LEFT: return "D-pad left";
            case DPAD_RIGHT: return "D-pad right";
            case L_STICK_BTN: return "left stick click";
            case R_STICK_BTN: return "right stick click";
            default: return btn.name();
        }
    }

    /** The same button on the other gamepad's place: {@code onPad(G1)} of G2 X is G1 X. */
    public final Key onPad(ControllerBindings.Pad other) {
        return other == pad ? this : new Key(other, btn, trigger);
    }

    /** "G1 BACK". */
    @Override
    public String toString() {
        return pad + " " + name();
    }

    // "equals" decides when two Keys are "the same": the same gamepad and the same button.
    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Key)) return false;
        Key k = (Key) o;
        return k.pad == pad && k.btn == btn && k.trigger == trigger;
    }

    @Override
    public final int hashCode() {
        return pad.hashCode() * 31 + (btn != null ? btn.hashCode() : 7 * trigger.hashCode());
    }
}
