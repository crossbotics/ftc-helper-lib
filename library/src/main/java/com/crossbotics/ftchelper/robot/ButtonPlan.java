package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

/**
 * The whole button map while it is being made: the library's own buttons (driving), each module's
 * suggestions, then the team's {@code Controls.java}. Checks it (one job per button) and turns it
 * into {@link ControllerBindings}, plus the jobs watched every loop (gestures, pairs, buzzes).
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
    // Two-button jobs ("hold LB, press Y"), in the order given.
    final List<Entry> pairs = new ArrayList<>();
    // "Buzz when this becomes true" (feedback from the robot), per gamepad.
    final List<Watch> watches = new ArrayList<>();
    // Made by compile(): run every loop by ControlMap (gestures, buzz watches).
    final List<LoopJob> loopJobs = new ArrayList<>();
    // The robot (set by ControlMap.makePlan): runs actions given to gestures and pairs.
    RobotDefinition robot;

    /** Plays {@code n} short buzzes on a gamepad (tests count them instead). n = 0 does nothing. */
    static ObjIntConsumer<Gamepad> buzzer = ButtonPlan::buzz;

    static final String TEAM = "Controls.java";
    static final String LIBRARY = "library";

    enum Kind { PRESS, RELEASE, HOLD, TOGGLE, ACTION, AXIS, RESERVED, FREE, GESTURES, SHIFT, PAIR }

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
        // Feedback: buzzes when the job runs (0 none; -1 a toggle: 2 = on, 1 = off).
        int rumble = 0;
        // toggle(part): the part (to know on or off for the buzz).
        Toggleable part;
        // GESTURES: the button's gestures.
        Gestures gestures;
        // PAIR: the shift key held down, and the button pressed with it.
        Pad.Button shift, with;

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

    /** True if this input is one of the library's own (driving, menus). */
    boolean isReserved(Object input) {
        Entry old = jobs.get(input);
        return old != null && old.kind == Kind.RESERVED;
    }

    /** The input's job given by whoever is adding jobs now (a module, or Controls.java), or null. */
    Entry mine(Object input) {
        Entry old = jobs.get(input);
        String who = source != null ? source : TEAM;
        return old != null && who.equals(old.source) ? old : null;
    }

    /** Puts {@code e} in place of the input's job from the same source (a hold that gains gestures). */
    Entry replace(Object input, Entry e) {
        e.source = jobs.get(input).source;
        jobs.put(input, e);
        return e;
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
        // One shift key can start several pairs (LB + X, LB + Y).
        if (old != null && TEAM.equals(old.source) && old.kind == Kind.SHIFT && e.kind == Kind.SHIFT) return old;
        if (old != null && old.kind == Kind.RESERVED) {
            throw new IllegalStateException("Controls.java: " + input + " is the library's \"" + old.says
                    + "\" button. Pick another button.");
        }
        if (old != null && TEAM.equals(old.source) && old.kind != Kind.FREE && e.kind != Kind.FREE
                && (old.kind == Kind.SHIFT || e.kind == Kind.SHIFT)) {
            throw new IllegalStateException("Controls.java: " + input + " is a shift key in a pair (hold it, "
                    + "then press the other button): it gets no job of its own. Pick another button for the job.");
        }
        if (old != null && TEAM.equals(old.source) && old.kind != Kind.FREE && e.kind != Kind.FREE) {
            throw new IllegalStateException("Controls.java: " + input + " has two jobs. Give each button "
                    + "one job (put both commands in one lambda if you want both).");
        }
        // A shift key made free: its pairs go too.
        if (old != null && old.kind == Kind.SHIFT && e.kind != Kind.SHIFT) {
            pairs.removeIf(p -> p.shift == input);
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
                out.add(p + " " + name + ": " + words(e) + who);
            }
            for (Entry pair : pairs) {
                if (pair.shift.pad == p) out.add(p + " " + pair.shift.label + " + " + pair.with.label + ": " + words(pair));
            }
            if (withFree && free.length() > 0) out.add(p + " free: " + free);
        }
        return out;
    }

    // A job's words on the help page, with "(buzz)" when it buzzes.
    private static String words(Entry e) {
        if (e.kind == Kind.GESTURES) return e.gestures.help();
        if (e.rumble == 0) return e.says;
        return e.says + (e.rumble < 0 ? " (buzz: 2 on, 1 off)" : " (buzz)");
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
            Supplier<Gamepad> gp = btn.pad.which == ControllerBindings.Pad.G1 ? pad1 : pad2;
            BooleanSupplier shifted = shifted(btn, gp);
            if (e.kind == Kind.GESTURES) {
                loopJobs.add(gestureJob(robot, btn, e, gp, shifted));
            } else if (e.kind != Kind.SHIFT) {
                bind(robot, into, btn, e, gp, shifted);
            }
        }
        // A Tunables.Buttons section is in the tuning menu even before any button has gestures.
        if (TunableSection.of(robot.tunablesClass(), "Buttons").exists()) {
            tapGap(robot);
            holdTime(robot);
        }
        // Pairs: pressing the second button while the shift key is down.
        for (Entry pair : pairs) {
            final Pad.Button shift = pair.shift;
            final Supplier<Gamepad> gp = shift.pad.which == ControllerBindings.Pad.G1 ? pad1 : pad2;
            ControllerBindings into = shift.pad.which == ControllerBindings.Pad.G1 ? driver : operator;
            final Runnable job = withBuzz(unlessPaused(robot, pair.a), pair, gp, robot);
            Runnable run = () -> { if (isDown(gp.get(), shift)) job.run(); };
            if (pair.with.btn != null) into.bindPress(pair.with.pad.which, pair.with.btn, run);
            else into.bindTriggerPress(pair.with.pad.which, pair.with.trigger, run);
        }
        for (Watch w : watches) {
            loopJobs.add(new LoopJob(w.pad == ControllerBindings.Pad.G1) {
                private boolean was = true;   // true at first: something already true at START does not buzz
                @Override void run(boolean paused) {
                    boolean now = w.when.getAsBoolean();
                    if (now && !was) buzzer.accept((w.pad == ControllerBindings.Pad.G1 ? pad1 : pad2).get(), w.buzzes);
                    was = now;
                }
                @Override void reset() { }
            });
        }
    }

    /**
     * One loop of the watched jobs (ControlMap calls it after the buttons).
     *
     * @param paused   the robot is paused (BACK): gestures forget what they were doing
     * @param menuOpen the tuning menu is open: gamepad 1's gestures forget what they were doing
     */
    void loop(boolean paused, boolean menuOpen) {
        for (LoopJob j : loopJobs) {
            if (j.driverPad && menuOpen) j.reset();
            else j.run(paused);
        }
    }

    /** Something watched every loop. */
    abstract static class LoopJob {
        final boolean driverPad;

        LoopJob(boolean driverPad) {
            this.driverPad = driverPad;
        }

        abstract void run(boolean paused);

        /** Forget what it was doing (a hold gets its "let go" job). */
        abstract void reset();
    }

    /** A "buzz when this becomes true" from {@link Pad#rumbleWhen}. */
    static final class Watch {
        final ControllerBindings.Pad pad;
        final BooleanSupplier when;
        final int buzzes;

        Watch(ControllerBindings.Pad pad, BooleanSupplier when, int buzzes) {
            this.pad = pad;
            this.when = when;
            this.buzzes = buzzes;
        }
    }

    // The gesture machine of one button, with each job paused-safe and buzzing as asked.
    private LoopJob gestureJob(RobotDefinition robot, final Pad.Button btn, Entry e,
                               final Supplier<Gamepad> gp, final BooleanSupplier shifted) {
        Gestures given = e.gestures;
        Gestures g = new Gestures();
        g.tap = job(robot, given, Gestures.TAP, given.tap, gp);
        g.doubleTap = job(robot, given, Gestures.DOUBLE, given.doubleTap, gp);
        for (Map.Entry<Integer, Runnable> t : given.taps.entrySet()) {
            g.taps.put(t.getKey(), job(robot, given, Gestures.tapsKey(t.getKey()), t.getValue(), gp));
        }
        g.heldForSec = given.heldForSec;
        g.heldFor = job(robot, given, Gestures.heldForKey(given.heldForSec), given.heldFor, gp);
        Runnable[] hold = holdJobs(robot, given, Gestures.HOLD, given.hold, given.holdEnd, gp);
        g.hold = hold[0];
        g.holdEnd = hold[1];
        Runnable[] tapHold = holdJobs(robot, given, Gestures.TAP_HOLD, given.tapThenHold, given.tapThenHoldEnd, gp);
        g.tapThenHold = tapHold[0];
        g.tapThenHoldEnd = tapHold[1];
        final com.crossbotics.ftchelper.tuning.Tunable gap = tapGap(robot), holdSec = holdTime(robot);
        final Gestures.Machine m = new Gestures.Machine(g, gap::get, holdSec::get, () -> Pad.clock.getAsLong());
        return new LoopJob(btn.pad.which == ControllerBindings.Pad.G1) {
            private boolean pausedBefore = false;
            @Override void run(boolean paused) {
                boolean down = isDown(gp.get(), btn);
                // Its shift key is down (a pair): this button's own gesture stops, and the button does
                // nothing more until it is let go.
                if (shifted.getAsBoolean()) {
                    m.reset(down);
                    return;
                }
                // Paused (BACK): forget the gesture (a hold gets its let-go job); a button held through
                // the pause does nothing.
                if (paused || pausedBefore) {
                    m.reset(down);
                    pausedBefore = paused;
                    return;
                }
                m.update(down);
            }
            @Override void reset() {
                m.reset(isDown(gp.get(), btn));
            }
        };
    }

    // Tunables.Buttons: the gesture times (live in the tuning menu).
    private static com.crossbotics.ftchelper.tuning.Tunable tapGap(RobotDefinition robot) {
        return TunableSection.of(robot.tunablesClass(), "Buttons").tunable("tapGapSec", 0.35, 0.05).range(0.1, 1);
    }

    private static com.crossbotics.ftchelper.tuning.Tunable holdTime(RobotDefinition robot) {
        return TunableSection.of(robot.tunablesClass(), "Buttons").tunable("holdSec", 0.3, 0.05).range(0.1, 2);
    }

    // One gesture's job: does nothing while paused, buzzes if asked.
    private static Runnable job(RobotDefinition robot, Gestures g, String key, Runnable job, Supplier<Gamepad> gp) {
        if (job == null) return null;
        final Runnable safe = unlessPaused(robot, job);
        final Integer n = g.rumble.get(key);
        if (n == null || n == 0) return safe;
        return () -> { safe.run(); buzzer.accept(gp.get(), Math.max(1, n)); };
    }

    // A hold's two jobs (every loop while held, once at let-go); the buzz plays once when it starts.
    // The let-go job runs even while paused, so a part a hold started is told to stop (and stays
    // stopped when the pause ends).
    private static Runnable[] holdJobs(RobotDefinition robot, Gestures g, String key, Runnable whileHeld,
                                       Runnable end, final Supplier<Gamepad> gp) {
        if (whileHeld == null) return new Runnable[] {null, null};
        final Runnable held = unlessPaused(robot, whileHeld);
        final Runnable letGo = end;
        final Integer n = g.rumble.get(key);
        if (n == null || n == 0) return new Runnable[] {held, letGo};
        final boolean[] started = {false};
        Runnable first = () -> {
            if (!started[0]) buzzer.accept(gp.get(), Math.max(1, n));
            started[0] = true;
            held.run();
        };
        Runnable last = () -> {
            started[0] = false;
            if (letGo != null) letGo.run();
        };
        return new Runnable[] {first, last};
    }

    // True while one of the shift keys paired with this button is down (its own job waits then).
    private BooleanSupplier shifted(Pad.Button btn, final Supplier<Gamepad> gp) {
        final List<Pad.Button> shifts = new ArrayList<>();
        for (Entry pair : pairs) if (pair.with == btn) shifts.add(pair.shift);
        if (shifts.isEmpty()) return () -> false;
        return () -> {
            Gamepad g = gp.get();
            for (Pad.Button s : shifts) if (isDown(g, s)) return true;
            return false;
        };
    }

    /** Is this button (or trigger, past half way) down on this gamepad? False with no gamepad. */
    static boolean isDown(Gamepad g, Pad.Button b) {
        if (g == null) return false;
        if (b.trigger != null) return (b.trigger == ControllerBindings.Trigger.LT ? g.left_trigger : g.right_trigger) > 0.5;
        switch (b.btn) {
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

    // n short buzzes (0.12 s each, 0.08 s apart), handed to the gamepad at once (it never waits).
    private static void buzz(Gamepad g, int n) {
        if (g == null || n <= 0) return;
        Gamepad.RumbleEffect.Builder b = new Gamepad.RumbleEffect.Builder();
        for (int i = 0; i < n; i++) {
            if (i > 0) b.addStep(0, 0, 80);
            b.addStep(0.8, 0.8, 120);
        }
        g.runRumbleEffect(b.build());
    }

    // A job that buzzes after it runs: n buzzes, or for a toggle 2 if now on, 1 if now off.
    private static Runnable withBuzz(final Runnable job, final Entry e, final Supplier<Gamepad> gp, final RobotDefinition robot) {
        if (job == null || e.rumble == 0) return job;
        final boolean[] parity = {false};
        return () -> {
            if (robot.isPaused()) return;
            job.run();
            int n = e.rumble;
            if (n < 0) {
                Boolean on = isOn(e.part);
                if (on == null) {
                    parity[0] = !parity[0];
                    on = parity[0];
                }
                n = on ? 2 : 1;
            }
            buzzer.accept(gp.get(), n);
        };
    }

    // On or off, for the toggle buzz; null if the part cannot say.
    private static Boolean isOn(Toggleable part) {
        if (part instanceof Intake) return ((Intake) part).isRunning();
        if (part instanceof Launcher) return ((Launcher) part).isSpinning();
        if (part instanceof Claw) return ((Claw) part).isClosed();
        if (part instanceof Mode) return ((Mode) part).isOn();
        return null;
    }

    // While the robot is paused (BACK), a button's job does nothing: a part told to move would start
    // with nothing updating it to stop.
    private static Runnable unlessPaused(final RobotDefinition robot, final Runnable job) {
        if (job == null) return null;
        return () -> { if (!robot.isPaused()) job.run(); };
    }

    private static void bind(final RobotDefinition robot, ControllerBindings into, Pad.Button btn, Entry given,
                             final Supplier<Gamepad> gp, final BooleanSupplier shifted) {
        ControllerBindings.Pad pad = btn.pad.which;
        final Entry e = new Entry(given.kind, unlessPaused(robot, given.a), unlessPaused(robot, given.b), given.says, given.action);
        e.rumble = given.rumble;
        e.part = given.part;
        switch (e.kind) {
            case PRESS:
                press(into, btn, unlessShifted(shifted, withBuzz(e.a, e, gp, robot)));
                break;
            case ACTION:
                press(into, btn, unlessShifted(shifted, withBuzz(unlessPaused(robot, () -> robot.runner.toggle(e.action)), e, gp, robot)));
                break;
            case RELEASE:
                Runnable letGo = unlessShifted(shifted, withBuzz(e.a, e, gp, robot));
                if (btn.btn != null) into.bindRelease(pad, btn.btn, letGo);
                else into.bindTriggerRelease(pad, btn.trigger, letGo);
                break;
            case HOLD:
                // The let-go job runs even while paused (given.b, not the paused-safe e.b): a part the
                // hold started is told to stop, and stays stopped when the pause ends.
                Runnable whileHeld = unlessShifted(shifted, e.a);
                if (btn.btn != null) {
                    into.bindHold(pad, btn.btn, whileHeld);
                    if (given.b != null) into.bindRelease(pad, btn.btn, given.b);
                } else {
                    into.bindTriggerHold(pad, btn.trigger, whileHeld);
                    if (given.b != null) into.bindTriggerRelease(pad, btn.trigger, given.b);
                }
                // The buzz plays once, when the hold starts.
                if (e.rumble != 0) press(into, btn, unlessShifted(shifted, withBuzz(() -> { }, e, gp, robot)));
                break;
            case TOGGLE:
                // Each press flips: the first runs a (on), the next b (off). Two buzzes = on, one = off.
                final boolean[] on = {false};
                final Entry onOff = e;
                Runnable flip = () -> {
                    if (robot.isPaused()) return;
                    on[0] = !on[0];
                    (on[0] ? onOff.a : onOff.b).run();
                    if (onOff.rumble != 0) buzzer.accept(gp.get(), onOff.rumble < 0 ? (on[0] ? 2 : 1) : onOff.rumble);
                };
                press(into, btn, unlessShifted(shifted, flip));
                break;
            default:
                break;
        }
    }

    private static void press(ControllerBindings into, Pad.Button btn, Runnable job) {
        if (btn.btn != null) into.bindPress(btn.pad.which, btn.btn, job);
        else into.bindTriggerPress(btn.pad.which, btn.trigger, job);
    }

    // While a shift key paired with this button is down, the button's own job waits (the pair runs).
    private static Runnable unlessShifted(final BooleanSupplier shifted, final Runnable job) {
        if (job == null) return null;
        return () -> { if (!shifted.getAsBoolean()) job.run(); };
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
