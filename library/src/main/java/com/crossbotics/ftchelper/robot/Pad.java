package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Trigger;
import java.util.function.BooleanSupplier;
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
 * g1.lt.slow();                                        // slow: the further it is pulled, the slower (one driver)
 * g1.a.free();                                         // free: nothing yet
 *
 * g2.rb.tap(robot.launcher::fire).says("fire one")     // gestures: several on one button
 *      .hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("keep firing")
 *      .doubleTap(() -> robot.launcher.fire(3)).says("fire three").rumble();
 * g2.a.toggle(robot.intake).rumble();                  // feedback: 2 buzzes = on, 1 = off
 * g2.lb.with(g2.y, () -> robot.lift.goTo("high"));     // a pair: hold LB, press Y
 * g2.rumbleWhen(robot.launcher::isReady);              // buzz when the robot says so
 * }</pre>
 *
 * <p>Each button takes ONE job, or several gestures (tap, hold, double-tap, tap then hold, N taps,
 * held for N seconds). Two jobs on one button in Controls.java stop the program at INIT with the
 * button's name; a job you put on a button the modules suggested replaces their suggestion.
 * The full menu: docs/guides/buttons.md.
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

    /** Two taps closer together than this (seconds) are a double-tap. */
    public static final double DOUBLE_TAP_SEC = 0.35;
    // The time, in nanoseconds (tests use a pretend clock).
    static java.util.function.LongSupplier clock = System::nanoTime;

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

    /**
     * Feedback from the robot: one buzz on this gamepad each time {@code when} becomes true, e.g.
     * {@code g2.rumbleWhen(robot.intake::isFull)}. Something already true at START does not buzz.
     */
    public void rumbleWhen(BooleanSupplier when) {
        rumbleWhen(when, 1);
    }

    /** {@link #rumbleWhen(BooleanSupplier)} with {@code buzzes} short buzzes (1 to 3). */
    public void rumbleWhen(BooleanSupplier when, int buzzes) {
        if (when == null) throw new IllegalArgumentException(this + ".rumbleWhen: the test is null");
        if (buzzes < 1 || buzzes > 3) throw new IllegalArgumentException(this + ".rumbleWhen: buzzes is 1 to 3");
        plan.watches.add(new ButtonPlan.Watch(which, when, buzzes));
    }

    /** One button or trigger. Each method gives it a job and hands the button back. */
    public static final class Button {
        final Pad pad;
        final String label;
        final Btn btn;            // null for a trigger
        final Trigger trigger;    // null for a button
        // The binding this button's last call made (null if it was not taken), for says().
        ButtonPlan.Entry last;
        // The gesture the last call added ("tap", "hold"...), or null: says() and rumble() name it.
        private String lastGesture;

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
            lastGesture = null;
            last = pad.plan.put(this, new ButtonPlan.Entry(ButtonPlan.Kind.ACTION, null, null, action.name(), action));
            return this;
        }

        /**
         * One tap runs {@code tap}; a second tap soon after (within {@link #DOUBLE_TAP_SEC}) runs
         * {@code doubleTap} instead. The first tap of a double-tap has already run {@code tap}, so make
         * the two work together, e.g. tap = intake on/off, double-tap = push out.
         */
        public Button tapOrDoubleTap(Runnable tap, Runnable doubleTap) {
            if (tap == null || doubleTap == null) throw new IllegalArgumentException(this + ": the job is null (a module that is not on the robot?)");
            final long[] lastTap = {Long.MIN_VALUE};
            Runnable both = () -> {
                long now = clock.getAsLong();
                if (lastTap[0] != Long.MIN_VALUE && now - lastTap[0] <= (long) (DOUBLE_TAP_SEC * 1e9)) {
                    lastTap[0] = Long.MIN_VALUE;   // a third tap starts again
                    doubleTap.run();
                } else {
                    lastTap[0] = now;
                    tap.run();
                }
            };
            return put(ButtonPlan.Kind.PRESS, both, null, "tap / double-tap");
        }

        /** Runs {@code onRelease} once each time it is let go. */
        public Button release(Runnable onRelease) {
            return put(ButtonPlan.Kind.RELEASE, onRelease, null, "on release");
        }

        /**
         * Runs {@code whileHeld} every loop while it is held. Alone it starts at once; with other
         * gestures on the same button (tap, double-tap...) it starts once the press lasts
         * {@code Tunables.Buttons.holdSec} (0.3 s), so a quick tap stays a tap.
         */
        public Button hold(Runnable whileHeld) {
            return hold(whileHeld, null);
        }

        /** {@link #hold(Runnable)}, and {@code onRelease} once when it is let go. */
        public Button hold(Runnable whileHeld, Runnable onRelease) {
            ButtonPlan.Entry mine = pad.plan.mine(this);
            if (mine != null && mine.kind == ButtonPlan.Kind.GESTURES) {
                Gestures g = gestures(Gestures.HOLD, whileHeld, mine.gestures.hold);
                g.hold = whileHeld;
                g.holdEnd = onRelease;
                return this;
            }
            return put(ButtonPlan.Kind.HOLD, whileHeld, onRelease, "hold");
        }

        // ----------------------------- gestures: several on one button -----------------------------

        /**
         * A quick tap (press and let go) runs {@code job}. It runs at once when pressed, unless the
         * button also has a longer gesture (hold, double-tap...): then it runs as soon as that can
         * no longer happen (when let go, or {@code Tunables.Buttons.tapGapSec} later).
         */
        public Button tap(Runnable job) {
            gestures(Gestures.TAP, job, gestures().tap).tap = job;
            return this;
        }

        /** {@link #tap(Runnable)} with an action (a tap again while it runs cancels it). */
        public Button tap(Action action) {
            return named(tap(run(action)), action);
        }

        /** Two quick taps run {@code job} (the first tap alone runs the tap job, if any). */
        public Button doubleTap(Runnable job) {
            gestures(Gestures.DOUBLE, job, gestures().doubleTap).doubleTap = job;
            return this;
        }

        /** {@link #doubleTap(Runnable)} with an action. */
        public Button doubleTap(Action action) {
            return named(doubleTap(run(action)), action);
        }

        /** {@code n} quick taps in a row (2 to 5) run {@code job}, e.g. 3 taps = intake backward. */
        public Button taps(int n, Runnable job) {
            if (n < 2 || n > 5) throw new IllegalArgumentException(this + ".taps: n is 2 to 5");
            if (n == 2) return doubleTap(job);
            gestures(Gestures.tapsKey(n), job, gestures().taps.get(n)).taps.put(n, job);
            return this;
        }

        /** {@link #taps(int, Runnable)} with an action. */
        public Button taps(int n, Action action) {
            return named(taps(n, run(action)), action);
        }

        /**
         * A quick tap, then press again and hold: {@code whileHeld} runs every loop while held
         * (last season's "spray": tap, then hold fire).
         */
        public Button tapThenHold(Runnable whileHeld) {
            return tapThenHold(whileHeld, null);
        }

        /** {@link #tapThenHold(Runnable)}, and {@code onRelease} once when it is let go. */
        public Button tapThenHold(Runnable whileHeld, Runnable onRelease) {
            Gestures g = gestures(Gestures.TAP_HOLD, whileHeld, gestures().tapThenHold);
            g.tapThenHold = whileHeld;
            g.tapThenHoldEnd = onRelease;
            return this;
        }

        /**
         * Held down for {@code seconds}: {@code job} runs once (a safe "are you sure?" for big
         * moves, like re-homing a lift). Let go sooner and nothing happens (or the tap job runs).
         */
        public Button heldFor(double seconds, Runnable job) {
            if (!(seconds > 0) || seconds > 5) throw new IllegalArgumentException(this + ".heldFor: seconds is more than 0, up to 5");
            Gestures g = gestures(Gestures.heldForKey(seconds), job, gestures().heldFor);
            g.heldFor = job;
            g.heldForSec = seconds;
            return this;
        }

        /** {@link #heldFor(double, Runnable)} with an action. */
        public Button heldFor(double seconds, Action action) {
            return named(heldFor(seconds, run(action)), action);
        }

        // ----------------------------- pairs and feedback -----------------------------

        /**
         * A pair: hold this button (the "shift key"), then press {@code other} to run {@code job}.
         * The shift key gets no job of its own; {@code other} keeps its own job when pressed alone.
         * Both on the same gamepad.
         */
        public Button with(Button other, Runnable job) {
            if (job == null) throw new IllegalArgumentException(this + " + " + (other == null ? "?" : other.label) + ": the job is null");
            if (other == null || other.pad != pad || other == this) {
                throw new IllegalArgumentException(this + ".with(...): pick another button on the same gamepad");
            }
            if (pad.plan.isReserved(other)) {
                throw new IllegalStateException("Controls.java: " + other + " is one of the library's driving buttons. Pick another button for the pair.");
            }
            lastGesture = null;
            ButtonPlan.Entry shift = pad.plan.put(this, new ButtonPlan.Entry(ButtonPlan.Kind.SHIFT, null, null, "shift key (hold, then press the other button)", null));
            if (shift.input != null) return this;   // a module's suggestion that found the button taken
            for (ButtonPlan.Entry p : pad.plan.pairs) {
                if (p.shift == this && p.with == other) throw new IllegalStateException("Controls.java: " + this + " + " + other.label + " has two jobs");
            }
            ButtonPlan.Entry pair = new ButtonPlan.Entry(ButtonPlan.Kind.PAIR, job, null, "job", null);
            pair.shift = this;
            pair.with = other;
            pad.plan.pairs.add(pair);
            last = pair;
            return this;
        }

        /** {@link #with(Button, Runnable)} with an action. */
        public Button with(Button other, Action action) {
            with(other, run(action));
            if (last != null && last.kind == ButtonPlan.Kind.PAIR) last.says = action.name();
            return this;
        }

        /**
         * Feedback: the gamepad buzzes when this job runs (a hold: when it starts). On a toggle:
         * two buzzes = on, one = off. After a gesture, it is for that gesture.
         */
        public Button rumble() {
            boolean toggle = lastGesture == null && last != null
                    && (last.kind == ButtonPlan.Kind.TOGGLE || last.part != null);
            return rumble(toggle ? -1 : 1);
        }

        /** {@link #rumble()} with {@code buzzes} short buzzes (1 to 3). */
        public Button rumble(int buzzes) {
            if (buzzes != -1 && (buzzes < 1 || buzzes > 3)) throw new IllegalArgumentException(this + ".rumble: buzzes is 1 to 3");
            if (last == null) return this;
            if (lastGesture != null && last.gestures != null) last.gestures.rumble.put(lastGesture, buzzes);
            else last.rumble = buzzes;
            return this;
        }

        // This button's gestures from whoever adds jobs now (a hold given before them joins in).
        private Gestures gestures() {
            ButtonPlan.Entry mine = pad.plan.mine(this);
            if (mine != null && mine.kind == ButtonPlan.Kind.GESTURES) {
                last = mine;
                return mine.gestures;
            }
            ButtonPlan.Entry e = new ButtonPlan.Entry(ButtonPlan.Kind.GESTURES, null, null, "gestures", null);
            e.gestures = new Gestures();
            if (mine != null && mine.kind == ButtonPlan.Kind.HOLD) {
                e.gestures.hold = mine.a;
                e.gestures.holdEnd = mine.b;
                e.gestures.says.put(Gestures.HOLD, mine.says);
                if (mine.rumble != 0) e.gestures.rumble.put(Gestures.HOLD, mine.rumble);
                last = pad.plan.replace(this, e);
            } else {
                last = pad.plan.put(this, e);
            }
            return e.gestures;
        }

        // Adds one gesture's words; stops if the job is null or the gesture is already there.
        private Gestures gestures(String key, Runnable job, Object already) {
            if (job == null) throw new IllegalArgumentException(this + " " + key + ": the job is null (a module that is not on the robot?)");
            if (already != null) throw new IllegalStateException("Controls.java: " + this + " has two \"" + key + "\" jobs");
            Gestures g = gestures();
            g.says.put(key, "job");
            lastGesture = key;
            return g;
        }

        // An action as a job: starts it, or cancels it if it is running.
        private Runnable run(final Action action) {
            if (action == null) throw new IllegalArgumentException(this + ": the action is null");
            final ButtonPlan plan = pad.plan;
            return () -> plan.robot.runner.toggle(action);
        }

        private Button named(Button b, Action action) {
            return b.says(action.name());
        }

        /** Each press switches the part (an intake, a launcher, a claw, a mode) on or off. */
        public Button toggle(Toggleable part) {
            if (part == null) throw new IllegalArgumentException(this + ": the part is null (a module that is not on the robot?)");
            put(ButtonPlan.Kind.PRESS, part::toggle, null, "toggle");
            if (last != null) last.part = part;
            return this;
        }

        /** Each press flips between running {@code onEnable} and {@code onDisable}. */
        public Button toggle(Runnable onEnable, Runnable onDisable) {
            return put(ButtonPlan.Kind.TOGGLE, onEnable, onDisable, "toggle");
        }

        /**
         * Slow driving, for one driver ({@code Tunables.Drive.slowSpeed}). On a trigger, e.g.
         * {@code g1.lt.slow()}: the further it is pulled, the slower, down to {@code slowSpeed} at a
         * full squeeze (like the two-driver LT). On a button: slow while it is held.
         * (With two drivers, G1 LT already does this.)
         */
        public Button slow() {
            final ButtonPlan p = pad.plan;
            if (!p.oneDriver) {
                throw new IllegalStateException("Controls.java: " + this + ".slow() is for one driver (oneDriver = true); "
                        + "with two drivers, G1 LT is slow already");
            }
            if (trigger != null) {
                put(ButtonPlan.Kind.HOLD, () -> { }, null, "slow (squeeze: the further, the slower)");
                if (last != null) p.slowTrigger = trigger;
                return this;
            }
            return put(ButtonPlan.Kind.HOLD, () -> p.slowHeld = true, () -> p.slowHeld = false, "slow (hold)");
        }

        /** No job: it stays free, even if a module suggested one for it. Listed as free on the help page. */
        public Button free() {
            lastGesture = null;
            last = pad.plan.put(this, new ButtonPlan.Entry(ButtonPlan.Kind.FREE, null, null, "free", null));
            return this;
        }

        /** Words for the help page, e.g. {@code .says("score high")}. After a gesture, for that gesture. */
        public Button says(String words) {
            if (last == null) return this;
            if (lastGesture != null && last.gestures != null) last.gestures.says.put(lastGesture, words);
            else last.says = words;
            return this;
        }

        private Button put(ButtonPlan.Kind kind, Runnable a, Runnable b, String says) {
            if (a == null) throw new IllegalArgumentException(this + ": the job is null (a module that is not on the robot?)");
            lastGesture = null;
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
