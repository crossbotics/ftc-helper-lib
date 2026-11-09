package com.crossbotics.ftchelper.finders;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * The Encoder Check's steps, without the hardware (so tests can run it): one motor at a time, wait until
 * nothing moves, read EVERY motor port's encoder, give the motor a short push, read them all again, and
 * judge which port counted. Never waits: {@link #update(boolean)} does one small step per loop.
 * The OpMode is {@link EncoderCheckOpMode}. Guide: docs/guides/finders.md, "Encoder Check".
 */
final class EncoderCheck {
    // The push: low power, short, so a lift moves a little and a wheel turns a little (robot on a block).
    static final double POWER = 0.3;
    static final double PUSH_SEC = 0.3;
    // Before each push, every port must be still this long (a flywheel spins down, an arm settles), so a part
    // still moving from the last push is never blamed. After STILL_SLOW_SEC the screen names the moving port.
    static final double STILL_SEC = 0.25;
    static final double STILL_SLOW_SEC = 2.0;
    static final int STILL_COUNTS = 3;
    // Fewer counts than this = "did not count" (an encoder that only jitters).
    static final int MIN_COUNTS = 20;

    /** One encoder port that can be read: a motor in the configuration. */
    static final class Port {
        final String name;
        final IntSupplier count;

        Port(String name, IntSupplier count) {
            this.name = name;
            this.count = count;
        }
    }

    /** One motor to check (with the motors that always get the same power), and its result. */
    static final class Job {
        final String name;
        final String part;
        final boolean encoderRead;
        final String skip;
        /** True if the skip reason is a fault (shown as a problem), false if it is fine. */
        final boolean skipIsFault;
        /** +1 = pushed with positive power, -1 = with negative power (the part's room is that way). */
        int sign = 1;
        final List<DoubleConsumer> power = new ArrayList<>();
        final Set<String> mates = new HashSet<>();
        /** The result's words (null = not checked yet). */
        String result = null;
        /** True if the result is fine. */
        boolean ok = false;

        Job(String name, String part, boolean encoderRead, String skip) {
            this(name, part, encoderRead, skip, false);
        }

        Job(String name, String part, boolean encoderRead, String skip, boolean skipIsFault) {
            this.name = name;
            this.part = part;
            this.encoderRead = encoderRead;
            this.skip = skip == null ? "" : skip;
            this.skipIsFault = skipIsFault;
        }
    }

    private enum Phase { SETTLE, PUSH, DONE }

    private final List<Job> jobs;
    private final List<Port> ports;
    private final DoubleSupplier clockSec;
    private int at = 0;
    private Phase phase = Phase.SETTLE;
    private double settleStart = Double.NaN, stillSince = Double.NaN, pushStart = Double.NaN;
    // The counts when "still" began, and just before the push (null = that port could not be read).
    private Map<String, Integer> last = null, before = null;
    // The port that moved last while settling (for the screen when it takes long).
    private String movingPort = null;

    EncoderCheck(List<Job> jobs, List<Port> ports, DoubleSupplier clockSec) {
        this.jobs = jobs;
        this.ports = ports;
        this.clockSec = clockSec;
        restart();
    }

    /** Back to the first motor; every result is cleared. Stops what moves. */
    void restart() {
        stop();
        for (Job j : jobs) {
            j.result = null;
            j.ok = false;
        }
        at = 0;
        phase = Phase.SETTLE;
        settleStart = Double.NaN;
        skipSkipped();
    }

    /**
     * One step. Call it every loop.
     *
     * @param go true while the driver holds the button: a push runs only then. Let go = power 0 at once,
     *           and that motor is pushed again from the start next time
     */
    void update(boolean go) {
        if (phase == Phase.DONE) return;
        double now = clockSec.getAsDouble();
        if (!go) {
            stop();
            return;
        }
        Job job = jobs.get(at);
        if (phase == Phase.SETTLE) {
            Map<String, Integer> counts = readAll();
            // Still = no port moved more than STILL_COUNTS since stillSince (slow coasting adds up too).
            String moved = moved(last, counts);
            if (Double.isNaN(settleStart) || moved != null) {
                if (Double.isNaN(settleStart)) settleStart = now;
                stillSince = now;
                last = counts;
                if (moved != null) movingPort = moved;
            }
            // Only once everything is still: a push while another part moves could blame the wrong port.
            if (now - stillSince >= STILL_SEC) {
                before = counts;
                movingPort = null;
                power(job, POWER * job.sign);
                pushStart = now;
                phase = Phase.PUSH;
            }
        } else if (phase == Phase.PUSH && now - pushStart >= PUSH_SEC) {
            // Read before the power goes off: an arm that falls back afterwards does not undo the count.
            Map<String, Integer> after = readAll();
            power(job, 0);
            Map<String, Integer> delta = new LinkedHashMap<>();
            for (Port p : ports) {
                Integer b = before.get(p.name), a = after.get(p.name);
                // Not read (an error), or a jump to exactly 0 (a hub's answer when a read fails): unknown.
                if (b == null || a == null || (a == 0 && Math.abs(b) >= MIN_COUNTS)) continue;
                delta.put(p.name, a - b);
            }
            Verdict v = judge(job.name, job.mates, job.encoderRead, job.sign, delta);
            job.result = v.text;
            job.ok = v.ok;
            at++;
            phase = Phase.SETTLE;
            settleStart = Double.NaN;
            skipSkipped();
        }
    }

    /** Power 0 to the motor being pushed (all of its motors, even if one fails). Safe to call any time. */
    void stop() {
        if (phase == Phase.PUSH) phase = Phase.SETTLE;
        settleStart = Double.NaN;
        if (at < jobs.size()) power(jobs.get(at), 0);
    }

    /** True once every motor has a result. */
    boolean done() {
        return phase == Phase.DONE;
    }

    /** The motor being checked now (its place in the list), or the list's size when done. */
    int at() {
        return at;
    }

    /** True while a motor is being pushed. */
    boolean pushing() {
        return phase == Phase.PUSH;
    }

    /** The port still moving after STILL_SLOW_SEC of waiting (null = none, or not waiting that long). */
    String stillMoving() {
        if (phase != Phase.SETTLE || Double.isNaN(settleStart)) return null;
        return clockSec.getAsDouble() - settleStart >= STILL_SLOW_SEC ? movingPort : null;
    }

    List<Job> jobs() {
        return jobs;
    }

    // Moves past the motors that are skipped (no push for them), and notes when all are done.
    private void skipSkipped() {
        for (; at < jobs.size() && !jobs.get(at).skip.isEmpty(); at++) {
            Job j = jobs.get(at);
            j.result = j.skipIsFault ? j.skip : "skipped: " + j.skip;
            j.ok = !j.skipIsFault;
        }
        if (at >= jobs.size()) phase = Phase.DONE;
    }

    // Every motor of the job, each tried even if one fails (then the first error is thrown after).
    private static void power(Job job, double p) {
        RuntimeException first = null;
        for (DoubleConsumer c : job.power) {
            try {
                c.accept(p);
            } catch (RuntimeException e) {
                if (first == null) first = e;
            }
        }
        if (first != null) throw first;
    }

    // Every port's count now. A port that cannot be read is null (left out of the judging).
    private Map<String, Integer> readAll() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Port p : ports) {
            Integer c;
            try {
                c = p.count.getAsInt();
            } catch (RuntimeException e) {
                c = null;
            }
            out.put(p.name, c);
        }
        return out;
    }

    // The port that changed more than STILL_COUNTS between two readings (null = none, or no earlier reading).
    private static String moved(Map<String, Integer> a, Map<String, Integer> b) {
        if (a == null) return null;
        for (Map.Entry<String, Integer> e : b.entrySet()) {
            Integer was = a.get(e.getKey());
            if (was != null && e.getValue() != null && Math.abs(e.getValue() - was) > STILL_COUNTS) return e.getKey();
        }
        return null;
    }

    /** A result: fine or not, and its words for the screen. */
    static final class Verdict {
        final boolean ok;
        final String text;

        Verdict(boolean ok, String text) {
            this.ok = ok;
            this.text = text;
        }
    }

    /**
     * Which port counted during one motor's push, and what that means.
     *
     * @param motor the motor pushed (its own port must count, the way it was pushed)
     * @param mates the motors pushed with it (a lift's second motor)
     * @param encoderRead true if the library reads this motor's encoder (else "no counts" is fine)
     * @param sign +1 if pushed with positive power, -1 with negative power
     * @param delta each port's change during the push, counts (a port that could not be read is left out)
     * @return the result
     */
    static Verdict judge(String motor, Set<String> mates, boolean encoderRead, int sign, Map<String, Integer> delta) {
        Integer ownBox = delta.get(motor);
        if (ownBox == null) return new Verdict(false, "could not read its encoder: press {Y} to check again");
        int own = ownBox * (sign < 0 ? -1 : 1);
        String way = sign < 0 ? "down" : "up";
        if (own >= MIN_COUNTS) return new Verdict(true, String.format(Locale.US, "OK: counts %s (%+d)", way, ownBox));
        if (own <= -MIN_COUNTS) {
            return new Verdict(false, String.format(Locale.US,
                    "counts BACKWARD (%+d): power wires swapped, or a reversed encoder?", ownBox));
        }
        // Its own port did not count. Which one did? A port pushed with it (a lift's second motor) first.
        String mate = biggest(delta, mates, null, motor);
        if (mate != null) {
            if (!encoderRead) return new Verdict(true, "no counts (fine: nothing reads this encoder)");
            return new Verdict(false, "counts on \"" + mate + "\" instead: plug the encoder cable into \""
                    + motor + "\"'s port (only its encoder is read)");
        }
        String other = biggest(delta, null, mates, motor);
        if (other != null) return new Verdict(false, "counts on \"" + other + "\" instead: encoder cables swapped?");
        if (!encoderRead) return new Verdict(true, "no counts (fine: nothing reads this encoder)");
        return new Verdict(false, "NO COUNTS: encoder cable missing? (Or it did not turn.)");
    }

    // The port (in "only", or not in "not", never "self") that changed the most, if by MIN_COUNTS or more.
    private static String biggest(Map<String, Integer> delta, Set<String> only, Set<String> not, String self) {
        String best = null;
        int most = MIN_COUNTS - 1;
        for (Map.Entry<String, Integer> e : delta.entrySet()) {
            String n = e.getKey();
            if (n.equals(self) || (only != null && !only.contains(n)) || (not != null && not.contains(n))) continue;
            if (Math.abs(e.getValue()) > most) {
                most = Math.abs(e.getValue());
                best = n;
            }
        }
        return best;
    }
}
