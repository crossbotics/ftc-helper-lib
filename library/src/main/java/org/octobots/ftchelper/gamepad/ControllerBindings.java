package org.octobots.ftchelper.gamepad;

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
 * <p>Many actions can share one button. They run in the order you added them.
 * Actions must be quick: never sleep or wait inside one.
 *
 * Docs: docs/modules/gamepad.md
 */
public class ControllerBindings {

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

    private double triggerThreshold = 0.5;

    private Gamepad g1, g2;

    /** One input (a button, or a trigger read as a button) with its list of actions. */
    private static final class Slot {
        final List<Action> actions = new ArrayList<>();
    }

    private abstract static class Action {
        boolean wasDown = false;
        boolean waitForRelease = false;

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

    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private final Map<String, BooleanSupplier> extraButtons = new LinkedHashMap<>();
    private final List<Runnable> axisActions = new ArrayList<>();

    // ---------------------------------------------------------------------------------------
    // Binding methods. Each returns `this`, so you can chain them.
    // ---------------------------------------------------------------------------------------

    /** Runs {@code onPress} once each time the button goes down. Holding does not repeat it. */
    public ControllerBindings bindPress(Pad pad, Btn btn, Runnable onPress) {
        return add(key(pad, btn), edge(onPress, true));
    }

    /** Runs {@code onRelease} once each time the button comes back up. */
    public ControllerBindings bindRelease(Pad pad, Btn btn, Runnable onRelease) {
        return add(key(pad, btn), edge(onRelease, false));
    }

    /** Runs {@code whileHeld} every loop while the button is down. */
    public ControllerBindings bindHold(Pad pad, Btn btn, Runnable whileHeld) {
        return add(key(pad, btn), hold(whileHeld));
    }

    /**
     * Each press flips between ON and OFF. Starts OFF.
     *
     * @param onEnable  runs when it turns ON (may be null)
     * @param onDisable runs when it turns OFF (may be null)
     */
    public ControllerBindings bindToggle(Pad pad, Btn btn, Runnable onEnable, Runnable onDisable) {
        return add(key(pad, btn), toggle(onEnable, onDisable));
    }

    /** Runs {@code onPress} once each time the trigger goes past the threshold (default 0.5). */
    public ControllerBindings bindTriggerPress(Pad pad, Trigger trigger, Runnable onPress) {
        return add(key(pad, trigger), edge(onPress, true));
    }

    /** Runs {@code whileHeld} every loop while the trigger is past the threshold. */
    public ControllerBindings bindTriggerHold(Pad pad, Trigger trigger, Runnable whileHeld) {
        return add(key(pad, trigger), hold(whileHeld));
    }

    /**
     * Gives the trigger's value (0 to 1) to {@code use} every loop.
     * Example: {@code bindTriggerAxis(Pad.G2, Trigger.RT, v -> intake.setPower(v))}.
     */
    public ControllerBindings bindTriggerAxis(Pad pad, Trigger trigger, DoubleConsumer use) {
        axisActions.add(() -> use.accept(readTrigger(pad, trigger)));
        return this;
    }

    /**
     * Lets you bind extra paddles (M1, M2) on controllers that have them.
     * You tell the class how to read the paddle.
     */
    public ControllerBindings registerExtraButton(Pad pad, Btn btn, BooleanSupplier isDown) {
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
     */
    public ControllerBindings setTriggerThreshold(double threshold) {
        this.triggerThreshold = threshold;
        return this;
    }

    // ---------------------------------------------------------------------------------------
    // Every loop
    // ---------------------------------------------------------------------------------------

    /** Reads both gamepads and runs the actions. Call once per loop. */
    public void update(Gamepad gamepad1, Gamepad gamepad2) {
        this.g1 = gamepad1;
        this.g2 = gamepad2;
        for (Map.Entry<String, Slot> entry : slots.entrySet()) {
            boolean down = isDown(entry.getKey());
            for (Action action : entry.getValue().actions) action.run(down);
        }
        for (Runnable axis : axisActions) axis.run();
    }

    /**
     * Call once right after START. For any button or trigger already held, NOTHING bound to it runs
     * (press, release, hold or toggle) until it is let go once. This stops surprise actions
     * when a driver holds a button through START. Trigger axis bindings are not affected.
     */
    public void ignoreHeldButtons(Gamepad gamepad1, Gamepad gamepad2) {
        this.g1 = gamepad1;
        this.g2 = gamepad2;
        for (Map.Entry<String, Slot> entry : slots.entrySet()) {
            boolean down = isDown(entry.getKey());
            for (Action action : entry.getValue().actions) {
                action.wasDown = down;
                action.waitForRelease = down;
            }
        }
    }

    /** Removes every binding. */
    public void clear() {
        slots.clear();
        extraButtons.clear();
        axisActions.clear();
    }

    /** True if the button is down right now (as of the last update). */
    public boolean isDown(Pad pad, Btn btn) {
        return isDown(key(pad, btn));
    }

    // ---------------------------------------------------------------------------------------
    // Inside
    // ---------------------------------------------------------------------------------------

    private ControllerBindings add(String key, Action action) {
        Slot slot = slots.get(key);
        if (slot == null) {
            slot = new Slot();
            slots.put(key, slot);
        }
        slot.actions.add(action);
        return this;
    }

    private static Action edge(final Runnable onEdge, final boolean onPress) {
        return new Action() {
            @Override void handle(boolean isDown) {
                if (isDown != wasDown && isDown == onPress && onEdge != null) onEdge.run();
                wasDown = isDown;
            }
        };
    }

    private static Action hold(final Runnable whileHeld) {
        return new Action() {
            @Override void handle(boolean isDown) {
                if (isDown && whileHeld != null) whileHeld.run();
                wasDown = isDown;
            }
        };
    }

    private static Action toggle(final Runnable onEnable, final Runnable onDisable) {
        return new Action() {
            boolean on = false;
            @Override void handle(boolean isDown) {
                if (isDown && !wasDown) {
                    on = !on;
                    Runnable r = on ? onEnable : onDisable;
                    if (r != null) r.run();
                }
                wasDown = isDown;
            }
        };
    }

    private static String key(Pad pad, Btn btn) {
        return pad.name() + ":" + btn.name();
    }

    private static String key(Pad pad, Trigger trigger) {
        return pad.name() + ":" + trigger.name();
    }

    private boolean isDown(String key) {
        Pad pad = key.startsWith("G1") ? Pad.G1 : Pad.G2;
        String name = key.substring(3);
        if (name.equals("LT") || name.equals("RT")) {
            return readTrigger(pad, Trigger.valueOf(name)) > triggerThreshold;
        }
        Btn btn = Btn.valueOf(name);
        if (btn == Btn.M1 || btn == Btn.M2) {
            BooleanSupplier reader = extraButtons.get(key);
            return reader != null && reader.getAsBoolean();
        }
        Gamepad g = pad == Pad.G1 ? g1 : g2;
        if (g == null) return false;
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

    private double readTrigger(Pad pad, Trigger trigger) {
        Gamepad g = pad == Pad.G1 ? g1 : g2;
        if (g == null) return 0;
        return trigger == Trigger.RT ? g.right_trigger : g.left_trigger;
    }
}
