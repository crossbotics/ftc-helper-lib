package io.github.jasoncross.ftchelper.robot;

import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Trigger;
import java.util.function.DoubleConsumer;

/**
 * One gamepad in {@code Controls.java}: every button and stick, by name. {@code g1} is the driver's
 * gamepad, {@code g2} the operator's.
 *
 * <pre>{@code
 * g2.rt.hold(robot.intake::in, robot.intake::stop);   // runs while held, stops on release
 * g2.y.press(Actions.scoreHigh(robot));               // an action: press again to cancel
 * g2.b.toggle(robot.claw);                            // open / close
 * g2.leftStickY.use(robot.lift::manual);              // up = +, every loop
 * g1.a.free();                                         // free: nothing yet
 * }</pre>
 *
 * <p>Each button takes ONE job. Two jobs on one button in Controls.java stop the program at INIT
 * with the button's name; a job you put on a button the modules suggested replaces their suggestion.
 * The help on the Driver Station (in INIT) shows the whole map.
 *
 * Docs: docs/modules/robot.md (section "Controls")
 */
public final class Pad {
    /** Face buttons. */
    public final Button a, b, x, y;
    /** Bumpers (LB, RB) and triggers (LT, RT; a trigger counts as pressed past half way). */
    public final Button lb, rb, lt, rt;
    /** BACK and START. */
    public final Button back, start;
    /** The D-pad. */
    public final Button dpadUp, dpadDown, dpadLeft, dpadRight;
    /** Pressing a stick down (a click). */
    public final Button leftStickButton, rightStickButton;
    /** The sticks: up and right are +, from -1 to 1. */
    public final Stick leftStickX, leftStickY, rightStickX, rightStickY;

    final ControllerBindings.Pad which;
    final ButtonPlan plan;

    Pad(ButtonPlan plan, ControllerBindings.Pad which) {
        this.plan = plan;
        this.which = which;
        a = new Button(this, "A", Btn.A, null);
        b = new Button(this, "B", Btn.B, null);
        x = new Button(this, "X", Btn.X, null);
        y = new Button(this, "Y", Btn.Y, null);
        lb = new Button(this, "LB", Btn.LB, null);
        rb = new Button(this, "RB", Btn.RB, null);
        lt = new Button(this, "LT", null, Trigger.LT);
        rt = new Button(this, "RT", null, Trigger.RT);
        back = new Button(this, "BACK", Btn.BACK, null);
        start = new Button(this, "START", Btn.START, null);
        dpadUp = new Button(this, "D-pad up", Btn.DPAD_UP, null);
        dpadDown = new Button(this, "D-pad down", Btn.DPAD_DOWN, null);
        dpadLeft = new Button(this, "D-pad left", Btn.DPAD_LEFT, null);
        dpadRight = new Button(this, "D-pad right", Btn.DPAD_RIGHT, null);
        leftStickButton = new Button(this, "left stick click", Btn.L_STICK_BTN, null);
        rightStickButton = new Button(this, "right stick click", Btn.R_STICK_BTN, null);
        leftStickX = new Stick(this, "left stick X", true, true);
        leftStickY = new Stick(this, "left stick Y", true, false);
        rightStickX = new Stick(this, "right stick X", false, true);
        rightStickY = new Stick(this, "right stick Y", false, false);
    }

    /** "G1" or "G2". */
    @Override
    public String toString() {
        return which.name();
    }

    /** One button or trigger. Each method gives it a job and hands the button back. */
    public static final class Button {
        final Pad pad;
        final String label;
        final Btn btn;            // null for a trigger
        final Trigger trigger;    // null for a button
        // The binding this button's last call made (null if it was not taken), for says().
        ButtonPlan.Entry last;

        Button(Pad pad, String label, Btn btn, Trigger trigger) {
            this.pad = pad;
            this.label = label;
            this.btn = btn;
            this.trigger = trigger;
        }

        /** "G2 RT". */
        @Override
        public String toString() {
            return pad + " " + label;
        }

        /** Runs {@code onPress} once each time it is pressed. */
        public Button press(Runnable onPress) {
            return put(ButtonPlan.Kind.PRESS, onPress, null, "press");
        }

        /** Starts the action; pressing again while it runs cancels it. */
        public Button press(final Action action) {
            last = pad.plan.put(this, new ButtonPlan.Entry(ButtonPlan.Kind.ACTION, null, null, action.name(), action));
            return this;
        }

        /** Runs {@code onRelease} once each time it is let go. */
        public Button release(Runnable onRelease) {
            return put(ButtonPlan.Kind.RELEASE, onRelease, null, "on release");
        }

        /** Runs {@code whileHeld} every loop while it is held. */
        public Button hold(Runnable whileHeld) {
            return put(ButtonPlan.Kind.HOLD, whileHeld, null, "hold");
        }

        /** Runs {@code whileHeld} every loop while it is held, and {@code onRelease} once when let go. */
        public Button hold(Runnable whileHeld, Runnable onRelease) {
            return put(ButtonPlan.Kind.HOLD, whileHeld, onRelease, "hold");
        }

        /** Each press switches the part (an intake, a launcher, a claw, a mode) on or off. */
        public Button toggle(Toggleable part) {
            return put(ButtonPlan.Kind.PRESS, part::toggle, null, "toggle");
        }

        /** Each press flips between running {@code onEnable} and {@code onDisable}. */
        public Button toggle(Runnable onEnable, Runnable onDisable) {
            return put(ButtonPlan.Kind.TOGGLE, onEnable, onDisable, "toggle");
        }

        /** No job: it stays free, even if a module suggested one for it. Listed as free on the help page. */
        public Button free() {
            last = pad.plan.put(this, new ButtonPlan.Entry(ButtonPlan.Kind.FREE, null, null, "free", null));
            return this;
        }

        /** Words for the help page, e.g. {@code .says("score high")}. */
        public Button says(String words) {
            if (last != null) last.says = words;
            return this;
        }

        private Button put(ButtonPlan.Kind kind, Runnable a, Runnable b, String says) {
            if (a == null) throw new IllegalArgumentException(this + ": the job is null (a module that is not on the robot?)");
            last = pad.plan.put(this, new ButtonPlan.Entry(kind, a, b, says, null));
            return this;
        }
    }

    /** One stick direction: up and right are +, from -1 to 1. */
    public static final class Stick {
        final Pad pad;
        final String label;
        final boolean left;
        final boolean xAxis;
        ButtonPlan.Entry last;

        Stick(Pad pad, String label, boolean left, boolean xAxis) {
            this.pad = pad;
            this.label = label;
            this.left = left;
            this.xAxis = xAxis;
        }

        /** "G2 left stick Y". */
        @Override
        public String toString() {
            return pad + " " + label;
        }

        /**
         * Gives the stick's value to {@code use} every loop (0 when it rests in the middle). A small
         * dead zone and a gentle curve make slow moves easy.
         */
        public Stick use(DoubleConsumer use) {
            if (use == null) throw new IllegalArgumentException(this + ": the job is null (a module that is not on the robot?)");
            last = pad.plan.put(this, new ButtonPlan.Entry(use));
            return this;
        }

        /** No job: it stays free, even if a module suggested one for it. */
        public Stick free() {
            last = pad.plan.put(this, new ButtonPlan.Entry(ButtonPlan.Kind.FREE, null, null, "free", null));
            return this;
        }

        /** Words for the help page. */
        public Stick says(String words) {
            if (last != null) last.says = words;
            return this;
        }
    }
}
