package com.crossbotics.ftchelper.teleop;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 *       that press (it waits until let go). A pair with only a quick job runs when the second one is
 *       pressed.</li>
 *   <li>START held: cancelled while A or B is down on the same gamepad (START + A/B pairs a gamepad on
 *       the Driver Station).</li>
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
        for (Group p : pairs) p.tapAtPress = p.hold == null;
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
        // Pairs first, so a pair that forms this loop cancels its buttons' own jobs before they run.
        for (Group p : pairs) {
            boolean down = p.isDown(g1, g2);
            if (menuOpen && !p.menu) {
                // Held while the menu is open: it waits until let go (also after the menu closes).
                p.blocked = down;
                p.wasDown = down;
                continue;
            }
            if (down && !p.wasDown && !p.blocked) {
                p.downAt = now;
                p.done = false;
                for (Group s : singles) if (p.uses(s.slot.key)) s.blocked = true;
                if (p.tapAtPress) {
                    p.tap.run();
                    p.done = true;
                }
            }
            step(p, down, now);
        }
        for (Group s : singles) {
            boolean down = s.isDown(g1, g2);
            if (menuOpen && !s.menu) {
                s.blocked = down;
                s.wasDown = down;
                continue;
            }
            // START + A or B pairs a gamepad on the Driver Station: not a START job.
            if (down && s.slot.key.btn == Btn.START && pairing(s.slot.key, g1, g2)) s.blocked = true;
            if (down && !s.wasDown && !s.blocked) {
                s.downAt = now;
                s.done = false;
                if (s.tapAtPress) {
                    s.tap.run();
                    s.done = true;
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
            if (!g.blocked && !g.done && g.tap != null) g.tap.run();
            g.blocked = false;
            g.done = false;
        }
        if (!down) g.blocked = false;
        g.wasDown = down;
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
