package com.crossbotics.ftchelper.robot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * The gestures one button has in {@code Controls.java}: a tap, a hold, a double-tap, a tap then a
 * hold, N taps, held for N seconds. {@link Pad.Button#tap(Runnable)} and the methods next to it fill
 * this in; {@link Machine} watches the button every loop and runs the right job.
 *
 * <p>The rule a driver can feel: a job runs at once, unless the same button also has a longer
 * gesture that starts the same way. Then the library waits only until it knows (a let-go, the
 * "hold" time, or the time for another tap).
 */
final class Gestures {
    // The jobs (null = not used). "End" jobs run once when a hold is let go.
    Runnable tap, hold, holdEnd, doubleTap, tapThenHold, tapThenHoldEnd, heldFor;
    double heldForSec;
    // N taps -> job, for N >= 2 (a double-tap given with taps(2, ...) lands in doubleTap).
    final TreeMap<Integer, Runnable> taps = new TreeMap<>();
    // Words for the help page and buzzes, per gesture ("tap", "hold", ...), in the order given.
    final Map<String, String> says = new LinkedHashMap<>();
    final Map<String, Integer> rumble = new LinkedHashMap<>();

    static final String TAP = "tap", HOLD = "hold", DOUBLE = "double-tap", TAP_HOLD = "tap, then hold";

    static String tapsKey(int n) {
        return n + " taps";
    }

    static String heldForKey(double sec) {
        return "held " + trim(sec) + " s";
    }

    /** The help page's words, e.g. "tap: fire one, hold: stream, tap, then hold: spray". */
    String help() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : says.entrySet()) {
            if (out.length() > 0) out.append("; ");
            out.append(e.getKey()).append(": ").append(e.getValue());
            Integer buzz = rumble.get(e.getKey());
            if (buzz != null) out.append(buzz < 0 ? " (buzz: 2 on, 1 off)" : " (buzz)");
        }
        return out.toString();
    }

    // The most taps a gesture on this button needs (1 when it has no double-tap and no taps(N)).
    int longestChain() {
        int n = doubleTap != null || tapThenHold != null ? 2 : 1;
        if (!taps.isEmpty()) n = Math.max(n, taps.lastKey());
        return n;
    }

    // True if the k-th tap in a row can also turn into a longer press (hold, held-for, tap-then-hold).
    boolean longAt(int k) {
        return k == 1 ? hold != null || heldFor != null : k == 2 && tapThenHold != null;
    }

    // The job of k quick taps in a row, or null.
    Runnable shortAt(int k) {
        if (k == 1) return tap;
        if (k == 2 && doubleTap != null) return doubleTap;
        return taps.get(k);
    }

    static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /**
     * Watches one button every loop and runs its gestures' jobs. Times come from
     * {@code Tunables.Buttons} (live in the tuning menu): {@code tapGapSec} is the most time between
     * the taps of a double-tap (and of a tap then a hold), {@code holdSec} how long a press must last
     * to count as a hold.
     */
    static final class Machine {
        private final Gestures g;
        private final DoubleSupplier tapGapSec, holdSec;
        private final LongSupplier clock;
        private boolean wasDown = false;
        // A button held when watching starts (START, or the tuning menu just closed) does nothing until let go.
        private boolean ignoreUntilUp = true;
        // Taps in a row so far; waiting = let go after a short press, maybe more taps to come.
        private int count = 0;
        private boolean waiting = false;
        private long pressAt, releaseAt;
        // This press's job already ran when it was pressed (nothing longer was possible).
        private boolean ranAtPress = false;
        // This press already ran its "held for N s" job: its let-go runs no tap.
        private boolean heldForDone = false;
        // A hold (or tap-then-hold) running: which one.
        private int longK = 0;

        Machine(Gestures g, DoubleSupplier tapGapSec, DoubleSupplier holdSec, LongSupplier clock) {
            this.g = g;
            this.tapGapSec = tapGapSec;
            this.holdSec = holdSec;
            this.clock = clock;
        }

        /**
         * Forget everything (a hold that is running gets its "let go" job). A button still down now
         * does nothing until it is let go.
         */
        void reset(boolean downNow) {
            endLong();
            count = 0;
            waiting = false;
            wasDown = downNow;
            ignoreUntilUp = downNow;
        }

        /** True while a hold or tap-then-hold job is running. */
        boolean isHolding() {
            return longK != 0;
        }

        /** One loop: is the button down now? */
        void update(boolean down) {
            long now = clock.getAsLong();
            if (ignoreUntilUp) {
                if (!down) ignoreUntilUp = false;
                wasDown = down;
                return;
            }
            if (down && !wasDown) pressed(now);
            else if (!down && wasDown) letGo(now);
            else if (down) stillDown(now);
            else stillUp(now);
            wasDown = down;
        }

        private void pressed(long now) {
            if (waiting && sec(now - releaseAt) <= tapGapSec.getAsDouble()) {
                count++;
            } else {
                if (waiting) runShort(count);   // the gap ran out between two loops
                count = 1;
            }
            waiting = false;
            pressAt = now;
            ranAtPress = false;
            heldForDone = false;
            // Nothing longer can start the same way: run it now, no waiting.
            if (!g.longAt(count) && count >= g.longestChain()) {
                runShort(count);
                ranAtPress = true;
            }
        }

        private void stillDown(long now) {
            double held = sec(now - pressAt);
            if (ranAtPress) return;
            if (count == 1 && g.heldFor != null && !heldForDone && held >= g.heldForSec) {
                heldForDone = true;
                g.heldFor.run();
            }
            if (longK == 0) {
                Runnable job = count == 1 ? g.hold : count == 2 ? g.tapThenHold : null;
                if (job != null && held >= holdSec.getAsDouble()) longK = count;
            }
            if (longK == 1) g.hold.run();
            else if (longK == 2) g.tapThenHold.run();
        }

        private void letGo(long now) {
            // A threshold passed between the last loop and the let-go still counts (held 1.02 s is held).
            stillDown(now);
            if (longK != 0) {
                endLong();
                count = 0;
                return;
            }
            if (ranAtPress || heldForDone) {
                count = 0;
                return;
            }
            // A short press: wait for another tap if a longer row of taps is possible.
            if (count < g.longestChain()) {
                waiting = true;
                releaseAt = now;
            } else {
                runShort(count);
                count = 0;
            }
        }

        private void stillUp(long now) {
            if (waiting && sec(now - releaseAt) > tapGapSec.getAsDouble()) {
                waiting = false;
                runShort(count);
                count = 0;
            }
        }

        private void endLong() {
            Runnable end = longK == 1 ? g.holdEnd : longK == 2 ? g.tapThenHoldEnd : null;
            longK = 0;
            if (end != null) end.run();
        }

        // k quick taps: their own job, or (none given) the tap job once per tap.
        private void runShort(int k) {
            Runnable job = g.shortAt(k);
            if (job != null) {
                job.run();
            } else if (g.tap != null) {
                for (int i = 0; i < k; i++) g.tap.run();
            }
        }

        private static double sec(long nanos) {
            return nanos / 1e9;
        }
    }
}
