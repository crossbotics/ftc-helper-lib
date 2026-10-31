package com.crossbotics.ftchelper.gamepad;

import com.qualcomm.robotcore.hardware.Gamepad;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/**
 * Connects gamepad buttons to robot actions in one place.
 *
 * <p>You say what each button does once, at the start. Then you call {@link #update} every loop.
 * The class does the "was it just pressed?" work for you.
 *
 * <p>Why "was it just pressed?" matters: the loop runs 50-200 times a second, and a quick tap
 * keeps a button down for several loops. If we ran the action every loop the button is down,
 * one tap would open the claw ten times. So for a "press" we only act on the loop where the
 * button changes from up to down. That change is called an "edge" (like stepping off a curb).
 *
 * <pre>{@code
 * ControllerBindings controls = new ControllerBindings()
 *         .bindPress(Pad.G2, Btn.A, () -> claw.open())             // once per press
 *         .bindHold(Pad.G2, Btn.DPAD_UP, () -> lift.up())          // every loop while held
 *         .bindToggle(Pad.G1, Btn.Y, () -> slow = true, () -> slow = false)
 *         .bindTriggerPress(Pad.G2, Trigger.RT, () -> shooter.fire());
 *
 * waitForStart();
 * controls.ignoreHeldButtons(gamepad1, gamepad2);  // a button held through START does not fire
 * while (opModeIsActive()) {
 *     controls.update(gamepad1, gamepad2);
 *     ...
 * }
 * }</pre>
 *
 * <p>{@code () -> claw.open()} is a "lambda": a small piece of code you hand over now, to be run
 * later (here, when the button is pressed). {@code Runnable} is Java's name for such a piece of
 * code that takes nothing and gives nothing back.
 *
 * <p>Many actions can share one button. They run in the order you added them.
 * Actions must be quick: never sleep or wait inside one.
 * See docs/guides/non-blocking.md, and Tutorial 03 (docs/tutorials/03-buttons.md) for a walk-through.
 *
 * Docs: docs/modules/gamepad.md
 */
public class ControllerBindings {

    // An "enum" is a fixed list of named choices. A Pad can only ever be G1 or G2.

    /** Which gamepad: G1 = gamepad1 (usually the driver), G2 = gamepad2 (usually the operator). */
    public enum Pad { G1, G2 }

    /** Gamepad buttons. M1 and M2 are optional extra paddles; see {@link #registerExtraButton}. */
    public enum Btn {
        A, B, X, Y,
        LB, RB,
        BACK, START, GUIDE,
        L_STICK_BTN, R_STICK_BTN,
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
        TOUCHPAD,
        M1, M2
    }

    /** The two analog triggers. LT = left trigger, RT = right trigger. */
    public enum Trigger { LT, RT }

    // A trigger gives a number from 0 (let go) to 1 (pulled all the way). Past this it counts as "pressed".
    private double triggerThreshold = 0.5;

    // The gamepads from the latest update(), so the reading methods below can use them.
    private Gamepad g1, g2;

    // "static" on the class below means a Slot does not need a ControllerBindings around it to exist.
    /** One input (a button, or a trigger read as a button) with its list of actions. */
    private static final class Slot {
        final List<Action> actions = new ArrayList<>();
    }

    // One bound action. "abstract" means this is only a pattern: each kind of action (press,
    // hold, toggle...) fills in its own handle(...) further down.
    private abstract static class Action {
        // Was the button down in the previous loop? Comparing it with "now" finds the edges.
        boolean wasDown = false;
        // True when the button was already held at START: ignore it until it is let go once.
        boolean waitForRelease = false;

        // Called every loop with the button's state. "final" so no action can skip the
        // held-through-START check.
        final void run(boolean isDown) {
            if (waitForRelease) {
                // Held through START: do nothing until it is let go once.
                if (!isDown) waitForRelease = false;
                wasDown = isDown;
                return;
            }
            handle(isDown);
        }

        abstract void handle(boolean isDown);
    }

    /** An action slower than this (milliseconds) is reported by {@link #getSlowActionWarning()}. */
    public static final double SLOW_ACTION_MS = 20;

    /** A slow-action note stays on the screen this many seconds after the last slow action. */
    public static final double WARNING_SHOW_SEC = 5;

    // Every input that has something bound to it, by its name such as "G2:A".
    // A LinkedHashMap is a lookup table (name -> slot) that remembers the order things were
    // added, so actions always run in the order you bound them.
    private final Map<String, Slot> slots = new LinkedHashMap<>();
    // The latest slow-action note, or "" when no action has been slow.
    private String slowWarning = "";
    // When the note was written (System.nanoTime). The note disappears WARNING_SHOW_SEC later.
    private long slowWarningAt = 0;
    // How to read the extra paddles M1/M2, which the SDK's Gamepad does not know about.
    private final Map<String, BooleanSupplier> extraButtons = new LinkedHashMap<>();
    // The trigger-axis bindings, which run every loop no matter what.
    private final List<AxisAction> axisActions = new ArrayList<>();

    // ---------------------------------------------------------------------------------------
    // Binding methods. Each returns `this`, so you can chain them.
    // ---------------------------------------------------------------------------------------

    /**
     * Runs {@code onPress} once each time the button goes down. Holding does not repeat it.
     *
     * @param pad     which gamepad
     * @param btn     which button
     * @param onPress the code to run, for example {@code () -> claw.open()}
     * @return this ControllerBindings, so you can chain the next binding
     */
    public ControllerBindings bindPress(Pad pad, Btn btn, Runnable onPress) {
        return add(key(pad, btn), edge(onPress, true));
    }

    /**
     * Runs {@code onRelease} once each time the button comes back up.
     *
     * @return this ControllerBindings, so you can chain the next binding
     */
    public ControllerBindings bindRelease(Pad pad, Btn btn, Runnable onRelease) {
        return add(key(pad, btn), edge(onRelease, false));
    }

    /**
     * Runs {@code whileHeld} every loop while the button is down.
     * Pair it with a {@link #bindRelease} that stops the mechanism when the button is let go.
     *
     * @return this ControllerBindings, so you can chain the next binding
     */
    public ControllerBindings bindHold(Pad pad, Btn btn, Runnable whileHeld) {
        return add(key(pad, btn), hold(whileHeld));
    }

    /**
     * Each press flips between ON and OFF. Starts OFF.
     *
     * @param onEnable  runs when it turns ON (may be null)
     * @param onDisable runs when it turns OFF (may be null)
     * @return this ControllerBindings, so you can chain the next binding
     */
    public ControllerBindings bindToggle(Pad pad, Btn btn, Runnable onEnable, Runnable onDisable) {
        return add(key(pad, btn), toggle(onEnable, onDisable));
    }

    /**
     * Runs {@code onPress} once each time the trigger goes past the threshold (default 0.5).
     *
     * @return this ControllerBindings, so you can chain the next binding
     */
    public ControllerBindings bindTriggerPress(Pad pad, Trigger trigger, Runnable onPress) {
        return add(key(pad, trigger), edge(onPress, true));
    }

    /**
     * Runs {@code whileHeld} every loop while the trigger is past the threshold.
     *
     * @return this ControllerBindings, so you can chain the next binding
     */
    public ControllerBindings bindTriggerHold(Pad pad, Trigger trigger, Runnable whileHeld) {
        return add(key(pad, trigger), hold(whileHeld));
    }

    /**
     * Runs {@code onRelease} once each time the trigger comes back under the threshold. Pair it with
     * {@link #bindTriggerHold} to stop a mechanism when the trigger is let go.
     *
     * @return this ControllerBindings, so you can chain the next binding
     */
    public ControllerBindings bindTriggerRelease(Pad pad, Trigger trigger, Runnable onRelease) {
        return add(key(pad, trigger), edge(onRelease, false));
    }

    /**
     * Each pull flips between ON and OFF, like {@link #bindToggle} for a button. Starts OFF.
     *
     * @param onEnable  runs when it turns ON (may be null)
     * @param onDisable runs when it turns OFF (may be null)
     * @return this ControllerBindings, so you can chain the next binding
     */
    public ControllerBindings bindTriggerToggle(Pad pad, Trigger trigger, Runnable onEnable, Runnable onDisable) {
        return add(key(pad, trigger), toggle(onEnable, onDisable));
    }

    /**
     * Gives the trigger's value (0 to 1) to {@code use} every loop.
     * Example: {@code bindTriggerAxis(Pad.G2, Trigger.RT, v -> intake.setPower(v))}.
     *
     * @param use code that takes the trigger value; {@code v -> ...} is a lambda that gets the value as v
     * @return this ControllerBindings, so you can chain the next binding
     */
    public ControllerBindings bindTriggerAxis(Pad pad, Trigger trigger, DoubleConsumer use) {
        // Save a small piece of code that, every loop, reads the trigger and hands the value on.
        axisActions.add(new AxisAction(() -> readTrigger(pad, trigger), use));
        return this;
    }

    /**
     * Lets you bind extra paddles (M1, M2) on controllers that have them.
     * You tell the class how to read the paddle.
     *
     * @param isDown code that answers true while the paddle is pressed
     * @return this ControllerBindings, so you can chain the next binding
     * @throws IllegalArgumentException if {@code btn} is not M1 or M2
     */
    public ControllerBindings registerExtraButton(Pad pad, Btn btn, BooleanSupplier isDown) {
        // Normal buttons are read from the Gamepad; only the paddles need a custom reader.
        if (btn != Btn.M1 && btn != Btn.M2) {
            throw new IllegalArgumentException("Only M1 and M2 can be extra buttons, not " + btn);
        }
        extraButtons.put(key(pad, btn), isDown);
        return this;
    }

    /**
     * How far a trigger must be pulled to count as "pressed". Default 0.5.
     *
     * @param threshold 0 to 1
     * @return this ControllerBindings, so you can chain the next binding
     */
    public ControllerBindings setTriggerThreshold(double threshold) {
        this.triggerThreshold = threshold;
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Every loop
    // ---------------------------------------------------------------------------------------

    /**
     * Reads both gamepads and runs the actions. Call once per loop.
     *
     * @param gamepad1 the OpMode's {@code gamepad1}
     * @param gamepad2 the OpMode's {@code gamepad2}
     */
    public void update(Gamepad gamepad1, Gamepad gamepad2) {
        // Keep the gamepads so isDown(...) and readTrigger(...) can read them.
        this.g1 = gamepad1;
        this.g2 = gamepad2;
        // For each bound input: read it ONCE, then give that same reading to all its actions,
        // so two actions on one button always agree about whether it is down.
        for (Map.Entry<String, Slot> entry : slots.entrySet()) {
            boolean down = isDown(entry.getKey());
            for (Action action : entry.getValue().actions) {
                // Time each action: a slow one freezes the whole robot, so we report it.
                long start = System.nanoTime();
                action.run(down);
                checkSlow(entry.getKey(), start);
            }
        }
        // Trigger-axis actions run every loop, whatever the trigger's value.
        for (AxisAction axis : axisActions) {
            long start = System.nanoTime();
            axis.use.accept(axis.value.getAsDouble());
            checkSlow("trigger axis", start);
        }
    }

    /**
     * "" or a note about a button action that took too long (more than {@link #SLOW_ACTION_MS}), e.g.
     * "G2:A action took 250 ms: actions must not wait or sleep". Show it with {@code t.warn(...)}.
     * A slow action freezes the WHOLE robot for that time.
     *
     * <p>The note stays {@link #WARNING_SHOW_SEC} seconds after the last slow action, so the drivers
     * have time to read it, then goes away.
     */
    public String getSlowActionWarning() {
        // Hide the note once it is older than WARNING_SHOW_SEC: the problem may be fixed by now.
        if (!slowWarning.isEmpty() && (System.nanoTime() - slowWarningAt) / 1e9 > WARNING_SHOW_SEC) slowWarning = "";
        return slowWarning;
    }

    // Works out how long an action took (1 ms = 1e6 nanoseconds) and saves a note if it was slow.
    private void checkSlow(String what, long startNanos) {
        double ms = (System.nanoTime() - startNanos) / 1e6;
        if (ms > SLOW_ACTION_MS) {
            // Locale.US makes the number always print the same way, whatever the phone's language.
            slowWarning = String.format(java.util.Locale.US,
                    "%s button job took %.0f ms: the whole robot stopped that long. No sleep() or while loop in a "
                    + "button job: use an Action with waitSeconds (lesson 5)", what, ms);
            slowWarningAt = System.nanoTime();
        }
    }

    /**
     * Call once right after START. For any button or trigger already held, NOTHING bound to it runs
     * (press, release, hold or toggle) until it is let go once. This stops surprise actions
     * when a driver holds a button through START. Trigger axis bindings are not affected.
     *
     * @param gamepad1 the OpMode's {@code gamepad1}
     * @param gamepad2 the OpMode's {@code gamepad2}
     */
    public void ignoreHeldButtons(Gamepad gamepad1, Gamepad gamepad2) {
        this.g1 = gamepad1;
        this.g2 = gamepad2;
        for (Map.Entry<String, Slot> entry : slots.entrySet()) {
            boolean down = isDown(entry.getKey());
            for (Action action : entry.getValue().actions) {
                // Remember it as already down (so no "press" is seen) and, if it is down,
                // ignore it until the driver lets go.
                action.wasDown = down;
                action.waitForRelease = down;
            }
        }
    }

    /**
     * Acts as if every button and trigger were let go now: "release" actions run for buttons that are
     * down, and {@link #bindTriggerAxis} code gets 0.
     * Call it before you stop calling {@link #update} for a while (for example while a menu uses
     * the gamepad), so a "start on press, stop on release" action can never be left running.
     * Afterwards, call {@link #ignoreHeldButtons} when you start calling update() again.
     */
    public void releaseAll() {
        for (Map.Entry<String, Slot> entry : slots.entrySet()) {
            for (Action action : entry.getValue().actions) {
                long start = System.nanoTime();
                action.run(false);
                checkSlow(entry.getKey(), start);
            }
        }
        // A trigger let go is 0: a motor set from a trigger must not keep its last power.
        for (AxisAction axis : axisActions) {
            long start = System.nanoTime();
            axis.use.accept(0);
            checkSlow("trigger axis", start);
        }
    }

    /** Removes every binding. */
    public void clear() {
        slots.clear();
        extraButtons.clear();
        axisActions.clear();
    }

    /**
     * True if the button is down right now (as of the last update).
     *
     * @param pad which gamepad
     * @param btn which button
     */
    public boolean isDown(Pad pad, Btn btn) {
        return isDown(key(pad, btn));
    }

    // ---------------------------------------------------------------------------------------
    // Inside
    // ---------------------------------------------------------------------------------------

    // Adds an action to an input's list, making the list the first time that input is used.
    private ControllerBindings add(String key, Action action) {
        Slot slot = slots.get(key);
        if (slot == null) {
            slot = new Slot();
            slots.put(key, slot);
        }
        slot.actions.add(action);
        return this;
    }

    // Makes an action that runs once on an edge: when the button goes down (onPress = true)
    // or comes back up (onPress = false).
    // "new Action() { ... }" makes a one-off Action right here, with its own handle(...).
    // "@Override" means "this replaces the method of the same name in Action".
    private static Action edge(final Runnable onEdge, final boolean onPress) {
        return new Action() {
            @Override void handle(boolean isDown) {
                // It changed since last loop, it changed the way we care about, and there is
                // something to run: run it, just this once.
                if (isDown != wasDown && isDown == onPress && onEdge != null) onEdge.run();
                wasDown = isDown;
            }
        };
    }

    // Makes an action that runs every loop while the button is down.
    private static Action hold(final Runnable whileHeld) {
        return new Action() {
            @Override void handle(boolean isDown) {
                if (isDown && whileHeld != null) whileHeld.run();
                wasDown = isDown;
            }
        };
    }

    // Makes an action that flips ON/OFF on each press (the up-to-down edge).
    private static Action toggle(final Runnable onEnable, final Runnable onDisable) {
        return new Action() {
            // Each toggle remembers its own ON/OFF state. Starts OFF.
            boolean on = false;
            @Override void handle(boolean isDown) {
                if (isDown && !wasDown) {
                    on = !on;
                    // "a ? b : c" is a short if/else: pick onEnable if now ON, else onDisable.
                    Runnable r = on ? onEnable : onDisable;
                    if (r != null) r.run();
                }
                wasDown = isDown;
            }
        };
    }

    // The name we file each input under, for example "G2:A" or "G1:RT".
    private static String key(Pad pad, Btn btn) {
        return pad.name() + ":" + btn.name();
    }

    private static String key(Pad pad, Trigger trigger) {
        return pad.name() + ":" + trigger.name();
    }

    // Reads an input from its name ("G2:A"): true while it is pressed.
    private boolean isDown(String key) {
        // The first two letters say which gamepad; after "G1:" (3 letters) comes the input name.
        Pad pad = key.startsWith("G1") ? Pad.G1 : Pad.G2;
        String name = key.substring(3);
        // A trigger counts as "down" once it is pulled past the threshold.
        if (name.equals("LT") || name.equals("RT")) {
            return readTrigger(pad, Trigger.valueOf(name)) > triggerThreshold;
        }
        Btn btn = Btn.valueOf(name);
        // Extra paddles: use the reader the team registered. No reader = never pressed.
        if (btn == Btn.M1 || btn == Btn.M2) {
            BooleanSupplier reader = extraButtons.get(key);
            return reader != null && reader.getAsBoolean();
        }
        Gamepad g = pad == Pad.G1 ? g1 : g2;
        // No gamepad yet (update not called): treat every button as up, so nothing runs by surprise.
        if (g == null) return false;
        // "switch" picks the line that matches btn and returns that gamepad field.
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
            case L_STICK_BTN: return g.left_stick_button;
            case R_STICK_BTN: return g.right_stick_button;
            case DPAD_UP: return g.dpad_up;
            case DPAD_DOWN: return g.dpad_down;
            case DPAD_LEFT: return g.dpad_left;
            case DPAD_RIGHT: return g.dpad_right;
            case TOUCHPAD: return g.touchpad;
            default: return false;
        }
    }

    // A trigger's value, 0 (let go) to 1 (pulled all the way). 0 if there is no gamepad yet.
    private double readTrigger(Pad pad, Trigger trigger) {
        Gamepad g = pad == Pad.G1 ? g1 : g2;
        if (g == null) return 0;
        return trigger == Trigger.RT ? g.right_trigger : g.left_trigger;
    }

    // A trigger binding: where its value comes from, and the code that gets it.
    private static final class AxisAction {
        final java.util.function.DoubleSupplier value;
        final DoubleConsumer use;

        AxisAction(java.util.function.DoubleSupplier value, DoubleConsumer use) {
            this.value = value;
            this.use = use;
        }
    }
}
