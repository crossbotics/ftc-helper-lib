package com.crossbotics.ftchelper.teleop;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Runs the library's own buttons ({@link LibraryButtons}) every loop: taps, holds and pairs, the
 * same way for every job. It reads the gamepads itself and never waits.
 *
 * <p>How a button (or a pair) works:
 * <ul>
 *   <li>Only a quick job, not part of a pair: it runs the moment the button is pressed.</li>
 *   <li>A quick job and a long job: the quick one runs when it is let go before the hold time; the
 *       long one runs once at the hold time.</li>
 *   <li>A pair (two buttons together): when both are down, each button's own job is cancelled for
 *       that press (it waits until let go). A pair's quick job runs when the buttons are let go
 *       (always, so it works the same with or without a long job on the pair).</li>
 *   <li>A pair with the pause button in it (START + BACK: debug lines, the alliance switch) only
 *       forms when the OTHER button went down first (or in the same loop): hold START, then press
 *       BACK. BACK pressed first is always a plain pause (its hold still resets "forward"); the other
 *       button joining later does nothing that press. So a pair never un-does a pause (a robot that
 *       paused and went on again for 0.1-0.3 s was a surprise).</li>
 *   <li>START held: cancelled while A or B is down on the same gamepad (START + A/B pairs a gamepad on
 *       the Driver Station).</li>
 *   <li>Pause (when {@link #pauseAtPress} is given): a press of the pause button stops the robot AT
 *       ONCE, even when the button also has a long job or is in a pair (a panicking driver holding it
 *       must get the stop). Held on to the long job's time, the long job runs too and the robot stays
 *       paused (a pair cannot form after the pause, see above).
 *       Going on again (already paused) is a tap, at the release, so holding the button never
 *       starts the robot.</li>
 *   <li>While the tuning menu is open, only the menu's own buttons work; the others wait until let go.</li>
 * </ul>
 */
final class LibraryKeys {

    /** One button or pair, its jobs, and what it is doing now. */
    static final class Group {
        final LibraryButtons.Slot slot;
        final Runnable tap, hold;
        // True for the tuning menu and button map: they work while the menu is open.
        final boolean menu;
        // The quick job runs at the press (no long job, not in a pair).
        boolean tapAtPress;
        private boolean wasDown = false;
        private long downAt = 0;
        // The long job ran (or the quick one at the press): nothing more until let go.
        private boolean done = false;
        // Cancelled for this press (a pair took it, the menu opened, held through START): wait for let go.
        private boolean blocked = false;
        // This press paused the robot at once (see pauseAtPress): nothing more on the release.
        private boolean pausedHere = false;
        // A pair only: the pause button's own group when the pair includes it (then the other button
        // must go down first), else null.
        Group pauseIn = null;

        Group(LibraryButtons.Slot slot, Runnable tap, Runnable hold, boolean menu) {
            this.slot = slot;
            this.tap = tap;
            this.hold = hold;
            this.menu = menu;
        }

        boolean isDown(Gamepad g1, Gamepad g2) {
            return slot.key.isDown(g1, g2) && (slot.with == null || slot.with.isDown(g1, g2));
        }

        boolean uses(Key k) {
            return slot.key.equals(k) || k.equals(slot.with);
        }
    }

    private final List<Group> pairs = new ArrayList<>(), singles = new ArrayList<>();
    private final LongSupplier clock;
    // "Is the robot paused now?", or null: then pause is an ordinary quick job (see pauseAtPress).
    private BooleanSupplier paused = null;

    /**
     * @param slots the checked buttons ({@link LibraryButtons#slots})
     * @param jobs  what each job does (a job missing here is left out)
     * @param clock the time in nanoseconds (tests use a pretend one)
     */
    LibraryKeys(List<LibraryButtons.Slot> slots, Map<LibraryButtons.Job, Runnable> jobs, LongSupplier clock) {
        this.clock = clock;
        for (LibraryButtons.Slot s : slots) {
            Runnable tap = s.tap == null ? null : jobs.get(s.tap);
            Runnable hold = s.hold == null ? null : jobs.get(s.hold);
            if (tap == null && hold == null) continue;
            boolean menu = s.tap == LibraryButtons.Job.TUNING_MENU || s.tap == LibraryButtons.Job.BUTTON_MAP
                    || s.hold == LibraryButtons.Job.TUNING_MENU;
            Group g = new Group(s, tap, hold, menu);
            (s.isPair() ? pairs : singles).add(g);
        }
        for (Group g : singles) {
            boolean inPair = false;
            for (Group p : pairs) if (p.uses(g.slot.key)) inPair = true;
            g.tapAtPress = g.hold == null && !inPair;
        }
        // A pair's quick job (debug) always runs when let go: the same whether or not the pair also
        // has a long job (switch alliance, when the team turns it on).
        for (Group p : pairs) p.tapAtPress = false;
        // A pair that includes the pause button forms only when the other button was down first.
        for (Group p : pairs)
            for (Group g : singles) if (g.slot.tap == LibraryButtons.Job.PAUSE && p.uses(g.slot.key)) p.pauseIn = g;
    }

    /**
     * Makes the pause job act at the PRESS of its button (see the class): {@code isPaused} tells
     * whether the robot is paused now, so a press only pauses (going on stays a tap). Without it, a
     * pause button that also has a long job (BACK: hold = reset forward) would pause only when let go.
     *
     * @param isPaused true while the robot is paused (null: the old way, the pause at the release)
     */
    LibraryKeys pauseAtPress(BooleanSupplier isPaused) {
        paused = isPaused;
        return this;
    }

    /** A map of jobs, for the constructor. */
    static Map<LibraryButtons.Job, Runnable> jobs() {
        return new EnumMap<>(LibraryButtons.Job.class);
    }

    /** Everything held down now waits until it is let go (at START, so a held button is not a press). */
    void ignoreHeld(Gamepad g1, Gamepad g2) {
        for (Group g : all()) {
            boolean down = g.isDown(g1, g2);
            g.wasDown = down;
            g.blocked = down;
        }
    }

    /**
     * One loop: reads both gamepads and runs the jobs that are due.
     *
     * @param menuOpen the tuning menu is open: only its own buttons work
     */
    void update(Gamepad g1, Gamepad g2, boolean menuOpen) {
        long now = clock.getAsLong();
        // Paused: no job that changes how the robot drives (field-centric, slow on/off) runs; the pause
        // button itself, "forward", the menus, debug, set pose and the alliance still work.
        boolean pausedNow = paused != null && paused.getAsBoolean();
        // Pairs first, so a pair that forms this loop cancels its buttons' own jobs before they run.
        for (Group p : pairs) {
            boolean down = p.isDown(g1, g2);
            if (menuOpen && !p.menu) {
                // Held while the menu is open: it waits until let go (also after the menu closes).
                p.blocked = down;
                p.wasDown = down;
                continue;
            }
            if (down && !p.wasDown && !p.blocked && p.pauseIn != null && p.pauseIn.wasDown) {
                // The pause button went down first: it stays a plain pause (it may already have
                // paused the robot). No pair this press, and the other button's own job waits too.
                p.blocked = true;
                for (Group s : singles) if (s != p.pauseIn && p.uses(s.slot.key)) block(s);
            }
            if (down && !p.wasDown && !p.blocked) {
                p.downAt = now;
                p.done = false;
                for (Group s : singles) if (p.uses(s.slot.key)) block(s);
                if (p.tapAtPress) {
                    p.tap.run();
                    p.done = true;
                }
            }
            step(p, down, now);
        }
        for (Group s : singles) {
            boolean down = s.isDown(g1, g2);
            if ((menuOpen && !s.menu) || (pausedNow && drivingJob(s))) {
                s.blocked = down;
                s.wasDown = down;
                continue;
            }
            // START + A or B pairs a gamepad on the Driver Station: not a START job.
            if (down && s.slot.key.btn == Btn.START && pairing(s.slot.key, g1, g2)) block(s);
            if (down && !s.wasDown && !s.blocked) {
                s.downAt = now;
                s.done = false;
                if (s.tapAtPress) {
                    s.tap.run();
                    s.done = true;
                } else if (pausesAtPress(s) && !paused.getAsBoolean()) {
                    // SAFETY: stop now, not when the button is let go (the long job may still follow).
                    s.tap.run();
                    s.pausedHere = true;
                }
            }
            step(s, down, now);
        }
    }

    // While down: the long job at its time. When let go: the quick job if nothing else happened.
    private static void step(Group g, boolean down, long now) {
        if (down && g.wasDown && !g.blocked && !g.done && g.hold != null
                && (now - g.downAt) / 1e9 >= g.slot.holdSec) {
            g.done = true;
            g.hold.run();
        }
        if (!down && g.wasDown) {
            if (!g.blocked && !g.done && !g.pausedHere && g.tap != null) g.tap.run();
            g.blocked = false;
            g.done = false;
            g.pausedHere = false;
        }
        if (!down) g.blocked = false;
        g.wasDown = down;
    }

    // A job that changes how the robot drives: it waits while the robot is paused.
    private static boolean drivingJob(Group g) {
        return g.slot.tap == LibraryButtons.Job.FIELD_CENTRIC || g.slot.hold == LibraryButtons.Job.FIELD_CENTRIC
                || g.slot.tap == LibraryButtons.Job.SLOW_TOGGLE || g.slot.hold == LibraryButtons.Job.SLOW_TOGGLE;
    }

    // True for the pause button when pause acts at the press (and it is not already a press job).
    private boolean pausesAtPress(Group g) {
        return paused != null && g.slot.tap == LibraryButtons.Job.PAUSE && !g.tapAtPress;
    }

    // Cancels a button's own job for this press. (Never the pause button after it paused: a pair with
    // the pause button only forms when the other button went down first, see update.)
    private static void block(Group g) {
        g.blocked = true;
    }

    private static boolean pairing(Key start, Gamepad g1, Gamepad g2) {
        Gamepad g = start.pad == com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad.G1 ? g1 : g2;
        return g != null && (g.a || g.b);
    }

    private List<Group> all() {
        List<Group> out = new ArrayList<>(pairs);
        out.addAll(singles);
        return out;
    }
}
