package io.github.jasoncross.ftchelper.robot;

import com.qualcomm.robotcore.hardware.Gamepad;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.StickShaping;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * The whole button map while it is being made: the library's own buttons (driving), each module's
 * suggestions, then the team's {@code Controls.java}. Checks it (one job per button) and turns it
 * into {@link ControllerBindings}.
 *
 * <p>Rules: a module's suggestion goes only on a free button (else a note says it has none). A job
 * in Controls.java replaces a suggestion; two jobs on one button in Controls.java, or a job on one of
 * the library's driving buttons, stop the program at INIT with the button's name.
 */
final class ButtonPlan {
    /** Who made the jobs being added now: a module's name, or null for Controls.java. */
    String source = null;
    final boolean oneDriver;
    final Pad g1, g2;
    // True while a button given Button.slow() is held (one driver).
    boolean slowHeld = false;
    // A trigger given Button.slow(): the further it is pulled, the slower (one driver). Null if none.
    ControllerBindings.Trigger slowTrigger = null;
    // Every input with a job, in the order they were given one.
    private final Map<Object, Entry> jobs = new LinkedHashMap<>();
    // Suggestions that found their button taken (for the INIT notes).
    private final List<Entry> rejected = new ArrayList<>();

    static final String TEAM = "Controls.java";
    static final String LIBRARY = "library";

    enum Kind { PRESS, RELEASE, HOLD, TOGGLE, ACTION, AXIS, RESERVED, FREE }

    /** One job on one input. */
    static final class Entry {
        final Kind kind;
        final Runnable a, b;
        final Action action;
        final DoubleConsumer axis;
        String says;
        String source;
        // Only for a suggestion that found its input taken: the input, and who has it.
        Object input;
        String takenBy;

        Entry(Kind kind, Runnable a, Runnable b, String says, Action action) {
            this.kind = kind;
            this.a = a;
            this.b = b;
            this.says = says;
            this.action = action;
            this.axis = null;
        }

        Entry(DoubleConsumer axis) {
            this.kind = Kind.AXIS;
            this.a = null;
            this.b = null;
            this.action = null;
            this.axis = axis;
            this.says = "every loop";
        }
    }

    ButtonPlan(boolean oneDriver) {
        this.oneDriver = oneDriver;
        g1 = new Pad(this, ControllerBindings.Pad.G1);
        g2 = new Pad(this, ControllerBindings.Pad.G2);
    }

    /** The operator's gamepad: G2, or G1 when one driver does everything. */
    Pad operator() {
        return oneDriver ? g1 : g2;
    }

    /** One of the library's own buttons (driving, menus): nobody else may use it. */
    void reserve(Object input, String says) {
        Entry e = new Entry(Kind.RESERVED, null, null, says, null);
        e.source = LIBRARY;
        jobs.put(input, e);
    }

    /**
     * Gives an input a job (see the rules above). Returns the job, so {@code says(...)} can name it;
     * a suggestion that found its input taken is returned too, but only kept for the notes.
     */
    Entry put(Object input, Entry e) {
        Entry old = jobs.get(input);
        if (source != null) {
            // A module's suggestion: only on a free input.
            e.source = source;
            if (old != null) {
                e.input = input;
                e.takenBy = old.source;
                rejected.add(e);
                return e;
            }
            jobs.put(input, e);
            return e;
        }
        e.source = TEAM;
        if (old != null && old.kind == Kind.RESERVED) {
            throw new IllegalStateException("Controls.java: " + input + " is the library's \"" + old.says
                    + "\" button. Pick another button.");
        }
        if (old != null && TEAM.equals(old.source) && old.kind != Kind.FREE && e.kind != Kind.FREE) {
            throw new IllegalStateException("Controls.java: " + input + " has two jobs. Give each button "
                    + "one job (put both commands in one lambda if you want both).");
        }
        jobs.put(input, e);
        return e;
    }

    /** Notes for INIT, e.g. a module suggestion that found no free button. */
    List<String> notes() {
        List<String> out = new ArrayList<>();
        for (Entry e : rejected) out.add(e.source + " " + e.says + ": no button (" + e.input + " is taken by " + e.takenBy + ")");
        return out;
    }

    /**
     * The help page: one line per input with a job, then the free ones.
     * E.g. "G2 RT: in (hold) [Intake]".
     */
    List<String> help() {
        return help(true);
    }

    /** The map; {@code withFree} false leaves out the free buttons (the GUIDE view). */
    List<String> help(boolean withFree) {
        List<String> out = new ArrayList<>();
        for (Pad p : oneDriver ? new Pad[] {g1} : new Pad[] {g1, g2}) {
            StringBuilder free = new StringBuilder();
            for (Object input : inputs(p)) {
                Entry e = jobs.get(input);
                String name = input instanceof Pad.Button ? ((Pad.Button) input).label : ((Pad.Stick) input).label;
                if (e == null || e.kind == Kind.FREE) {
                    free.append(free.length() == 0 ? "" : ", ").append(name);
                    continue;
                }
                String who = e.source.equals(TEAM) ? "" : " [" + e.source + "]";
                out.add(p + " " + name + ": " + e.says + who);
            }
            if (withFree && free.length() > 0) out.add(p + " free: " + free);
        }
        return out;
    }

    /**
     * Turns the map into button bindings (G1 into {@code driver}, G2 into {@code operator}) and stick
     * jobs (into {@code sticks}, run every loop).
     */
    void compile(RobotDefinition robot, ControllerBindings driver, ControllerBindings operator,
                 List<StickJob> sticks, Supplier<Gamepad> pad1, Supplier<Gamepad> pad2) {
        for (Map.Entry<Object, Entry> j : jobs.entrySet()) {
            Entry e = j.getValue();
            if (e.kind == Kind.RESERVED || e.kind == Kind.FREE) continue;
            if (j.getKey() instanceof Pad.Stick) {
                Pad.Stick s = (Pad.Stick) j.getKey();
                sticks.add(new StickJob(s, e.axis, s.pad.which == ControllerBindings.Pad.G1 ? pad1 : pad2));
                continue;
            }
            Pad.Button btn = (Pad.Button) j.getKey();
            ControllerBindings into = btn.pad.which == ControllerBindings.Pad.G1 ? driver : operator;
            bind(robot, into, btn, e);
        }
    }

    private static void bind(final RobotDefinition robot, ControllerBindings into, Pad.Button btn, final Entry e) {
        ControllerBindings.Pad pad = btn.pad.which;
        switch (e.kind) {
            case PRESS:
                if (btn.btn != null) into.bindPress(pad, btn.btn, e.a);
                else into.bindTriggerPress(pad, btn.trigger, e.a);
                break;
            case ACTION:
                Runnable run = () -> robot.runner.toggle(e.action);
                if (btn.btn != null) into.bindPress(pad, btn.btn, run);
                else into.bindTriggerPress(pad, btn.trigger, run);
                break;
            case RELEASE:
                if (btn.btn != null) into.bindRelease(pad, btn.btn, e.a);
                else into.bindTriggerRelease(pad, btn.trigger, e.a);
                break;
            case HOLD:
                if (btn.btn != null) {
                    into.bindHold(pad, btn.btn, e.a);
                    if (e.b != null) into.bindRelease(pad, btn.btn, e.b);
                } else {
                    into.bindTriggerHold(pad, btn.trigger, e.a);
                    if (e.b != null) into.bindTriggerRelease(pad, btn.trigger, e.b);
                }
                break;
            case TOGGLE:
                if (btn.btn != null) into.bindToggle(pad, btn.btn, e.a, e.b);
                else into.bindTriggerToggle(pad, btn.trigger, e.a, e.b);
                break;
            default:
                break;
        }
    }

    // Every input of a pad, in the help page's order.
    private static Object[] inputs(Pad p) {
        return new Object[] {p.leftStickX, p.leftStickY, p.rightStickX, p.rightStickY,
                p.lt, p.rt, p.lb, p.rb, p.a, p.b, p.x, p.y,
                p.dpadUp, p.dpadDown, p.dpadLeft, p.dpadRight,
                p.leftStickButton, p.rightStickButton, p.back, p.start};
    }

    /** A stick job, run every loop with the stick's value (up and right +). */
    static final class StickJob {
        final Pad.Stick stick;
        private final DoubleConsumer use;
        private final Supplier<Gamepad> gamepad;

        StickJob(Pad.Stick stick, DoubleConsumer use, Supplier<Gamepad> gamepad) {
            this.stick = stick;
            this.use = use;
            this.gamepad = gamepad;
        }

        boolean isDriverPad() {
            return stick.pad.which == ControllerBindings.Pad.G1;
        }

        /** The stick at rest: 0, once. */
        void letGo() {
            use.accept(0);
        }

        void run() {
            Gamepad g = gamepad.get();
            if (g == null) return;
            double raw = stick.left ? (stick.xAxis ? g.left_stick_x : -g.left_stick_y)
                    : (stick.xAxis ? g.right_stick_x : -g.right_stick_y);
            // A small dead zone (a resting stick is never exactly 0) and a gentle curve for slow moves.
            use.accept(StickShaping.shape(raw, 0.05, 2));
        }
    }
}
