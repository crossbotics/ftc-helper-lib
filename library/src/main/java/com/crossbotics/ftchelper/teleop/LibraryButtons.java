package com.crossbotics.ftchelper.teleop;

import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Trigger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the library's own TeleOp jobs are on the gamepads: driving, pause, slow, field-centric, the
 * button map, the tuning menu, "set pose here" and "switch alliance" (off unless you turn it on). Every
 * one can be moved to another button, or freed ({@code null}: no button). The defaults are below.
 *
 * <pre>{@code
 * // Controls.java (the robot in five files):
 * @Override protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
 *     lib.fieldCentric(g1.x);           // field-centric on/off on X instead of Y
 *     lib.slow(g1.rt);                  // slow on the right trigger (LT is free now)
 *     lib.switchAlliance(g1.back, g1.start);   // turn on the alliance switch: hold START, then BACK, 1.5 s
 * }
 *
 * // EasyTeleOp (your own TeleOp):
 * @Override protected void libraryButtons(LibraryButtons lib) {
 *     lib.pause(Key.g1(Btn.A));
 * }
 * }</pre>
 *
 * <table>
 *   <caption>The defaults (two drivers; one driver in brackets)</caption>
 *   <tr><th>Job</th><th>Default</th></tr>
 *   <tr><td>drive / strafe, turn</td><td>G1 left stick, G1 right stick X ({@link #swapSticks})</td></tr>
 *   <tr><td>pause (at the press) / go on (a tap)</td><td>G1 BACK</td></tr>
 *   <tr><td>reset "forward" (hold 1 s, field-centric)</td><td>G1 BACK</td></tr>
 *   <tr><td>debug lines (hold START, then press BACK; runs when let go)</td><td>G1 START + BACK</td></tr>
 *   <tr><td>switch alliance (both held 1.5 s)</td><td>none: off until {@link #switchAlliance} turns it on</td></tr>
 *   <tr><td>set pose here (hold 1 s, robots with odometry; also in INIT)</td><td>G1 START</td></tr>
 *   <tr><td>slow (squeeze)</td><td>G1 LT (one driver: none)</td></tr>
 *   <tr><td>slow on/off</td><td>none (one driver: G1 left stick click)</td></tr>
 *   <tr><td>field-centric on/off (mecanum with an IMU)</td><td>G1 Y (one driver: G1 right stick click)</td></tr>
 *   <tr><td>button map (a tap)</td><td>G1 GUIDE</td></tr>
 *   <tr><td>tuning menu (hold 1 s, PRACTICE profile)</td><td>G1 GUIDE</td></tr>
 *   <tr><td>choose the alliance in INIT</td><td>G1 X = BLUE, G1 B = RED</td></tr>
 * </table>
 *
 * <p><b>Rules, checked at INIT</b> (a clear message names the button): a button gets at most one
 * quick job (a tap) and one long job (held); "reset forward" and "tuning menu" are the long job when
 * their button has a quick one, else a tap. The slow trigger gets nothing else. A pair is two
 * different buttons on one gamepad; debug and "switch alliance" may share one pair (a quick press of
 * both = debug, both held 1.5 s = switch). START + A or B (pairing a gamepad on the Driver Station)
 * cancels START's own job for that press. The tuning menu's button may not be one the menu itself
 * uses on gamepad 1 (D-pad, A, B, X, Y, LB, RB).
 *
 * Docs: docs/guides/buttons.md (section "Moving the library's buttons"), docs/modules/teleop.md
 */
public final class LibraryButtons {

    /** How long a "hold" job takes: reset forward, set pose here, the tuning menu (seconds). */
    public static final double HOLD_SEC = 1.0;
    /** How long both "switch alliance" buttons are held (seconds): long, so it never happens by accident. */
    public static final double SWITCH_ALLIANCE_SEC = 1.5;

    /** The library's jobs, with the words the button map shows. */
    public enum Job {
        PAUSE("pause / go on"),
        RESET_FORWARD("forward = away from you now"),
        DEBUG("debug lines"),
        SLOW("slow (squeeze: the further, the slower)"),
        SLOW_TOGGLE("slow on/off"),
        FIELD_CENTRIC("field-centric on/off"),
        BUTTON_MAP("button map"),
        TUNING_MENU("tuning menu"),
        SET_POSE("set pose here"),
        SWITCH_ALLIANCE("switch alliance (BLUE / RED)");

        /** The words on the button map. */
        public final String words;

        Job(String words) {
            this.words = words;
        }
    }

    private final Map<Job, Key> keys = new EnumMap<>(Job.class);
    // The second button of the pairs (debug, switch alliance); null = one button alone.
    private Key debugWith, switchWith;
    private Key initBlue, initRed;
    private boolean swapSticks = false;

    private LibraryButtons() { }

    /** The two-driver defaults (see the table above). */
    public static LibraryButtons twoDrivers() {
        LibraryButtons b = new LibraryButtons();
        b.keys.put(Job.PAUSE, Key.g1(Btn.BACK));
        b.keys.put(Job.RESET_FORWARD, Key.g1(Btn.BACK));
        b.keys.put(Job.DEBUG, Key.g1(Btn.START));
        b.debugWith = Key.g1(Btn.BACK);
        // No alliance switch in TeleOp unless the team turns it on (switchAlliance(...)): the
        // alliance is chosen in INIT, and a switch by accident mid-match would aim at the wrong goal.
        b.keys.put(Job.SET_POSE, Key.g1(Btn.START));
        b.keys.put(Job.SLOW, Key.g1(Trigger.LT));
        b.keys.put(Job.FIELD_CENTRIC, Key.g1(Btn.Y));
        b.keys.put(Job.BUTTON_MAP, Key.g1(Btn.GUIDE));
        b.keys.put(Job.TUNING_MENU, Key.g1(Btn.GUIDE));
        b.initBlue = Key.g1(Btn.X);
        b.initRed = Key.g1(Btn.B);
        return b;
    }

    /**
     * The one-driver defaults (Controls.java with {@code oneDriver = true}): no slow trigger (the
     * triggers are the operator's), slow on/off on the left stick click, field-centric on the right
     * stick click. The rest as with two drivers.
     */
    public static LibraryButtons oneDriver() {
        LibraryButtons b = twoDrivers();
        b.keys.remove(Job.SLOW);
        b.keys.put(Job.SLOW_TOGGLE, Key.g1(Btn.L_STICK_BTN));
        b.keys.put(Job.FIELD_CENTRIC, Key.g1(Btn.R_STICK_BTN));
        return b;
    }

    // ----------------------------- moving them -----------------------------

    /**
     * Pause / go on. A press pauses at once (even when the button also has a long job, like BACK's
     * "reset forward"); a tap goes on again. A pair with this button in it (debug lines, the alliance
     * switch) needs the other button down first: this button pressed first is always a plain pause.
     * Null = no button (INIT then warns: nothing can pause).
     */
    public LibraryButtons pause(Key key) {
        return put(Job.PAUSE, key);
    }

    /**
     * Reset "forward" (field-centric: the robot now faces away from you). Held 1 s when its button
     * also has a quick job (like pause), else a tap. Null = no button.
     */
    public LibraryButtons resetForward(Key key) {
        return put(Job.RESET_FORWARD, key);
    }

    /**
     * Debug lines on/off: press {@code key} and {@code with} together (either order, except that when
     * one of them is the pause button, the other must go down first: hold START, then press BACK).
     * {@code with} null: {@code key} alone, a tap. Both null = no button.
     */
    public LibraryButtons debug(Key key, Key with) {
        put(Job.DEBUG, key);
        debugWith = key == null ? null : with;
        return this;
    }

    /**
     * Slow driving. A trigger: the further it is pulled, the slower (down to the slow speed at a full
     * squeeze). A button: slow while it is held. Null = none.
     */
    public LibraryButtons slow(Key key) {
        return put(Job.SLOW, key);
    }

    /** Slow on/off: each press switches slow driving on or off. Null = none. */
    public LibraryButtons slowToggle(Key key) {
        return put(Job.SLOW_TOGGLE, key);
    }

    /** Field-centric on/off (a mecanum with an IMU or odometry). Null = none (it stays as it starts). */
    public LibraryButtons fieldCentric(Key key) {
        return put(Job.FIELD_CENTRIC, key);
    }

    /** The button map on the Driver Station (a tap shows it, a tap again hides it). Null = none. */
    public LibraryButtons buttonMap(Key key) {
        return put(Job.BUTTON_MAP, key);
    }

    /**
     * The tuning menu (PRACTICE profile): held 1 s when its button also shows the button map, else a
     * tap. The menu itself is worked with gamepad 1's D-pad, A, B, X, Y and bumpers. Null = none.
     */
    public LibraryButtons tuningMenu(Key key) {
        return put(Job.TUNING_MENU, key);
    }

    /**
     * "Set pose here": held 1 s, the odometry's pose becomes the reset spot
     * ({@code Tunables.Odometry.resetX, resetY, resetHeading}). Only on a robot with odometry, only with
     * the drive sticks let go, and also in INIT (before the match). Null = none.
     */
    public LibraryButtons setPose(Key key) {
        return put(Job.SET_POSE, key);
    }

    /**
     * Turns on switching the alliance (BLUE / RED) during TeleOp: hold {@code key} and {@code with}
     * together {@link #SWITCH_ALLIANCE_SEC} seconds. Off by default (the alliance is chosen in INIT);
     * {@code lib.switchAlliance(g1.back, g1.start)} is the usual pair (it shares START + BACK with the
     * debug lines: a quick press of both = debug). BACK is the pause button, so hold START first, then
     * BACK (BACK first is just a pause). {@code with} null: {@code key} alone, held. Both null = off again.
     */
    public LibraryButtons switchAlliance(Key key, Key with) {
        put(Job.SWITCH_ALLIANCE, key);
        switchWith = key == null ? null : with;
        return this;
    }

    /** The INIT buttons that choose the alliance (before START). Null = no button for that color. */
    public LibraryButtons allianceInInit(Key blue, Key red) {
        if (blue != null && blue.equals(red)) throw new IllegalArgumentException("allianceInInit: BLUE and RED need two different buttons");
        initBlue = blue;
        initRed = red;
        return this;
    }

    /**
     * True: drive (and strafe) with the RIGHT stick and turn with the LEFT stick's X. False (default):
     * the left stick drives, the right stick X turns.
     */
    public LibraryButtons swapSticks(boolean swap) {
        swapSticks = swap;
        return this;
    }

    private LibraryButtons put(Job job, Key key) {
        if (key == null) keys.remove(job);
        else keys.put(job, key);
        return this;
    }

    // ----------------------------- reading them -----------------------------

    /** The button of a job (the first one of a pair), or null if it has none. */
    public Key key(Job job) {
        return keys.get(job);
    }

    /** The second button of a pair (debug, switch alliance), or null. */
    public Key with(Job job) {
        if (job == Job.DEBUG) return debugWith;
        if (job == Job.SWITCH_ALLIANCE) return switchWith;
        return null;
    }

    /** The INIT button for BLUE, or null. */
    public Key initBlue() {
        return initBlue;
    }

    /** The INIT button for RED, or null. */
    public Key initRed() {
        return initRed;
    }

    /** True when the right stick drives and the left stick X turns. */
    public boolean sticksSwapped() {
        return swapSticks;
    }

    /**
     * Moves every gamepad 2 button of these jobs to the same button on gamepad 1 (one driver: there is
     * no gamepad 2). Two jobs that land on one button are then checked like any other clash.
     * Controls.java with {@code oneDriver = true} does it for you.
     */
    public LibraryButtons moveAllToGamepad1() {
        for (Map.Entry<Job, Key> e : keys.entrySet()) e.setValue(g1(e.getValue()));
        debugWith = g1(debugWith);
        switchWith = g1(switchWith);
        initBlue = g1(initBlue);
        initRed = g1(initRed);
        return this;
    }

    private static Key g1(Key k) {
        return k == null ? null : k.onPad(ControllerBindings.Pad.G1);
    }

    // ----------------------------- checking them -----------------------------

    /** One button's (or pair's) library jobs, ready to run. */
    static final class Slot {
        final Key key;
        final Key with;            // null = the button alone
        Job tap;                   // the quick job (null = none)
        Job hold;                  // the long job (null = none)
        double holdSec = HOLD_SEC;

        Slot(Key key, Key with) {
            this.key = key;
            this.with = with;
        }

        boolean isPair() {
            return with != null;
        }

        boolean same(Key k, Key w) {
            if (w == null) return with == null && key.equals(k);
            return with != null && ((key.equals(k) && with.equals(w)) || (key.equals(w) && with.equals(k)));
        }

        // "pause / go on; hold 1 s: forward = away from you now"
        String words() {
            String quick = tap == null ? "" : tap.words;
            if (hold == null) return quick;
            String held = (isPair() ? "hold both " : "hold ") + secs(holdSec) + ": " + hold.words;
            return quick.isEmpty() ? held : quick + "; " + held;
        }
    }

    private static String secs(double s) {
        return (s == Math.rint(s) ? String.valueOf((int) s) : String.valueOf(s)) + " s";
    }

    /**
     * Checks the buttons and groups the jobs per button (and per pair). Stops with a clear message if
     * two jobs clash.
     *
     * @param jobs the jobs this TeleOp has (a robot without odometry has no "set pose")
     */
    List<Slot> slots(Set<Job> jobs) {
        List<Slot> out = new ArrayList<>();
        // Pairs first: debug, then switch alliance (they may share one pair).
        for (Job pairJob : new Job[] {Job.DEBUG, Job.SWITCH_ALLIANCE}) {
            Key k = keys.get(pairJob), w = with(pairJob);
            if (k == null || !jobs.contains(pairJob)) continue;
            if (w != null && w.equals(k)) throw clash("the " + pairJob.words + " pair is " + k + " twice: give it two different buttons");
            if (w != null && w.pad != k.pad) throw clash("the " + pairJob.words + " pair " + k + " + " + w + " is on two gamepads: put both on one");
            if (w == null) continue;   // one button alone: a slot of its own, below
            Slot s = find(out, k, w);
            if (s == null) {
                s = new Slot(k, w);
                out.add(s);
            }
            if (pairJob == Job.DEBUG) s.tap = Job.DEBUG;
            else {
                s.hold = Job.SWITCH_ALLIANCE;
                s.holdSec = SWITCH_ALLIANCE_SEC;
            }
        }
        // Then each button alone: its quick jobs, its long jobs.
        Map<Key, List<Job>> quick = new LinkedHashMap<>(), flexible = new LinkedHashMap<>(), held = new LinkedHashMap<>();
        for (Job j : Job.values()) {
            Key k = keys.get(j);
            if (k == null || !jobs.contains(j)) continue;
            switch (j) {
                case PAUSE: case FIELD_CENTRIC: case SLOW_TOGGLE: case BUTTON_MAP:
                    add(quick, k, j);
                    break;
                case RESET_FORWARD: case TUNING_MENU:
                    add(flexible, k, j);
                    break;
                case SET_POSE:
                    add(held, k, j);
                    break;
                case DEBUG:
                    if (debugWith == null) add(quick, k, j);
                    break;
                case SWITCH_ALLIANCE:
                    if (switchWith == null) add(held, k, j);
                    break;
                case SLOW:
                    break;   // checked below
                default:
                    break;
            }
        }
        List<Key> all = new ArrayList<>();
        for (Map<Key, List<Job>> m : java.util.Arrays.asList(quick, flexible, held)) for (Key k : m.keySet()) if (!all.contains(k)) all.add(k);
        for (Key k : all) {
            List<Job> q = list(quick, k), f = list(flexible, k), h = list(held, k);
            if (q.size() > 1) throw clash(k + " has two library jobs: \"" + q.get(0).words + "\" and \"" + q.get(1).words + "\"");
            Slot s = new Slot(k, null);
            if (!q.isEmpty()) s.tap = q.get(0);
            List<Job> longJobs = new ArrayList<>(h);
            for (Job j : f) {
                if (s.tap == null) s.tap = j;      // alone on its button: a tap
                else longJobs.add(j);              // with a quick job: held
            }
            if (longJobs.size() > 1) {
                throw clash(k + " has two long (held) library jobs: \"" + longJobs.get(0).words + "\" and \"" + longJobs.get(1).words + "\"");
            }
            if (!longJobs.isEmpty()) {
                s.hold = longJobs.get(0);
                s.holdSec = s.hold == Job.SWITCH_ALLIANCE ? SWITCH_ALLIANCE_SEC : HOLD_SEC;
            }
            out.add(s);
        }
        // The slow trigger (or button) does nothing else: it is read every loop.
        Key slow = jobs.contains(Job.SLOW) ? keys.get(Job.SLOW) : null;
        if (slow != null) {
            for (Slot s : out) {
                if (s.key.equals(slow) || slow.equals(s.with)) {
                    throw clash(slow + " is the slow " + (slow.isTrigger() ? "trigger" : "button") + " and also \"" + s.words() + "\"");
                }
            }
        }
        // The tuning menu is worked with gamepad 1's D-pad, A, B, X, Y and bumpers.
        Key menu = jobs.contains(Job.TUNING_MENU) ? keys.get(Job.TUNING_MENU) : null;
        if (menu != null && menu.pad == ControllerBindings.Pad.G1 && menu.btn != null) {
            switch (menu.btn) {
                case DPAD_UP: case DPAD_DOWN: case DPAD_LEFT: case DPAD_RIGHT:
                case A: case B: case X: case Y: case LB: case RB:
                    throw clash(menu + " cannot open the tuning menu: the menu itself uses it. Pick GUIDE, BACK, START, a stick click or a trigger");
                default:
                    break;
            }
        }
        return out;
    }

    /**
     * Each button's words for the button map: "pause / go on; hold 1 s: forward = away from you now".
     * A pair's words are on both of its buttons ("with A: ..."); a pair with the pause button says
     * which goes first ("START: then BACK: debug lines", "BACK: after START: debug lines"). The slow
     * key is included.
     * Checks the buttons first (two jobs on one button stop with the button's name).
     *
     * @param jobs the jobs this TeleOp has (a robot without odometry has no "set pose")
     */
    public Map<Key, String> words(Set<Job> jobs) {
        Map<Key, String> out = new LinkedHashMap<>();
        List<Slot> slots = slots(jobs);
        // Each button's own jobs first, then the pairs it is in.
        for (Slot s : slots) if (!s.isPair()) join(out, s.key, s.words());
        Key pause = jobs.contains(Job.PAUSE) ? keys.get(Job.PAUSE) : null;
        for (Slot s : slots) {
            if (!s.isPair()) continue;
            if (pause != null && (pause.equals(s.key) || pause.equals(s.with))) {
                // With the pause button: the other button first ("START: then BACK: debug lines").
                Key other = pause.equals(s.key) ? s.with : s.key;
                join(out, s.key, (s.key == other ? "then " + pause.name() : "after " + other.name()) + ": " + s.words());
                join(out, s.with, (s.with == other ? "then " + pause.name() : "after " + other.name()) + ": " + s.words());
                continue;
            }
            join(out, s.key, "with " + s.with.name() + ": " + s.words());
            join(out, s.with, "with " + s.key.name() + ": " + s.words());
        }
        Key slow = jobs.contains(Job.SLOW) ? keys.get(Job.SLOW) : null;
        if (slow != null) join(out, slow, slow.isTrigger() ? Job.SLOW.words : "slow (hold)");
        return out;
    }

    private static void join(Map<Key, String> out, Key k, String words) {
        if (words.isEmpty()) return;
        String old = out.get(k);
        out.put(k, old == null ? words : old + "; " + words);
    }

    private static Slot find(List<Slot> slots, Key k, Key w) {
        for (Slot s : slots) if (s.same(k, w)) return s;
        return null;
    }

    private static void add(Map<Key, List<Job>> m, Key k, Job j) {
        List<Job> l = m.get(k);
        if (l == null) {
            l = new ArrayList<>();
            m.put(k, l);
        }
        l.add(j);
    }

    private static List<Job> list(Map<Key, List<Job>> m, Key k) {
        List<Job> l = m.get(k);
        return l == null ? new ArrayList<Job>() : l;
    }

    private static IllegalStateException clash(String what) {
        return new IllegalStateException("libraryButtons: " + what + ". Move or free one (docs/guides/buttons.md, \"Moving the library's buttons\")");
    }
}
