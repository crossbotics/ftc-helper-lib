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
    // Before each push, every port must be still this long (a flywheel spins down, an arm settles),
    // so a part still moving from the last push is not counted. At most STILL_MAX_SEC, then it goes on.
    static final double STILL_SEC = 0.25;
    static final double STILL_MAX_SEC = 2.0;
    static final int STILL_COUNTS = 3;
    // Fewer counts than this = "did not count" (an encoder that only jitters).
    static final int MIN_COUNTS = 20;

    /** One encoder port that can be read: a motor in the configuration. */
    static final class Port {
        final String name;
        final int number;
        final IntSupplier count;

        Port(String name, int number, IntSupplier count) {
            this.name = name;
            this.number = number;
            this.count = count;
        }
    }

    /** One motor to check (with the motors that always get the same power), and its result. */
    static final class Job {
        final String name;
        final String part;
        final boolean encoderRead;
        final String skip;
        final List<DoubleConsumer> power = new ArrayList<>();
        final Set<String> mates = new HashSet<>();
        /** The result's words (null = not checked yet). */
        String result = null;
        /** True if the result is fine. */
        boolean ok = false;

        Job(String name, String part, boolean encoderRead, String skip) {
            this.name = name;
            this.part = part;
            this.encoderRead = encoderRead;
            this.skip = skip == null ? "" : skip;
        }
    }

    private enum Phase { SETTLE, PUSH, DONE }

    private final List<Job> jobs;
    private final List<Port> ports;
    private final DoubleSupplier clockSec;
    private int at = 0;
    private Phase phase = Phase.SETTLE;
    private double settleStart = Double.NaN, stillSince = Double.NaN, pushStart = Double.NaN;
    // The counts when "still" began, and just before the push.
    private Map<String, Integer> last = null, before = null;

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
            if (phase == Phase.PUSH) {
                power(jobs.get(at), 0);
                phase = Phase.SETTLE;
            }
            settleStart = Double.NaN;
            return;
        }
        Job job = jobs.get(at);
        if (phase == Phase.SETTLE) {
            Map<String, Integer> counts = readAll();
            // Still = no port moved more than STILL_COUNTS since stillSince (slow coasting adds up too).
            if (Double.isNaN(settleStart) || moved(last, counts) > STILL_COUNTS) {
                if (Double.isNaN(settleStart)) settleStart = now;
                stillSince = now;
                last = counts;
            }
            if (now - stillSince >= STILL_SEC || now - settleStart >= STILL_MAX_SEC) {
                before = counts;
                power(job, POWER);
                pushStart = now;
                phase = Phase.PUSH;
            }
        } else if (phase == Phase.PUSH && now - pushStart >= PUSH_SEC) {
            // Read before the power goes off: an arm that falls back afterwards does not undo the count.
            Map<String, Integer> after = readAll();
            power(job, 0);
            Map<String, Integer> delta = new LinkedHashMap<>();
            for (Port p : ports) delta.put(p.name, after.get(p.name) - before.get(p.name));
            Verdict v = judge(job.name, job.mates, job.encoderRead, delta, portNumbers());
            job.result = v.text;
            job.ok = v.ok;
            at++;
            phase = Phase.SETTLE;
            settleStart = Double.NaN;
            skipSkipped();
        }
    }

    /** Power 0 to the motor being pushed. Safe to call any time. */
    void stop() {
        if (at < jobs.size()) power(jobs.get(at), 0);
        if (phase == Phase.PUSH) phase = Phase.SETTLE;
        settleStart = Double.NaN;
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

    List<Job> jobs() {
        return jobs;
    }

    // Moves past the motors that are skipped (no push for them), and notes when all are done.
    private void skipSkipped() {
        for (; at < jobs.size() && !jobs.get(at).skip.isEmpty(); at++) {
            jobs.get(at).result = "skipped: " + jobs.get(at).skip;
            jobs.get(at).ok = true;
        }
        if (at >= jobs.size()) phase = Phase.DONE;
    }

    private static void power(Job job, double p) {
        for (DoubleConsumer c : job.power) c.accept(p);
    }

    // Every port's count now. A port that cannot be read counts as 0 (it then looks like "no counts").
    private Map<String, Integer> readAll() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Port p : ports) {
            int c;
            try {
                c = p.count.getAsInt();
            } catch (RuntimeException e) {
                c = 0;
            }
            out.put(p.name, c);
        }
        return out;
    }

    // The biggest change of any port between two readings (0 when there is no earlier reading).
    private static int moved(Map<String, Integer> a, Map<String, Integer> b) {
        if (a == null) return 0;
        int most = 0;
        for (Map.Entry<String, Integer> e : b.entrySet()) {
            Integer was = a.get(e.getKey());
            if (was != null) most = Math.max(most, Math.abs(e.getValue() - was));
        }
        return most;
    }

    private Map<String, Integer> portNumbers() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Port p : ports) out.put(p.name, p.number);
        return out;
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
     * @param motor the motor pushed (its own port must count, up)
     * @param mates the motors pushed with it (a lift's second motor)
     * @param encoderRead true if the library reads this motor's encoder (else "no counts" is fine)
     * @param delta each port's change during the push, counts
     * @param portNumbers each port's number on its hub (for the words)
     * @return the result
     */
    static Verdict judge(String motor, Set<String> mates, boolean encoderRead, Map<String, Integer> delta,
                         Map<String, Integer> portNumbers) {
        Integer ownBox = delta.get(motor);
        int own = ownBox == null ? 0 : ownBox;
        if (own >= MIN_COUNTS) return new Verdict(true, String.format(Locale.US, "OK: counts up (+%d)", own));
        if (own <= -MIN_COUNTS) {
            return new Verdict(false, String.format(Locale.US,
                    "counts BACKWARD (%d): power wires swapped, or a reversed encoder?", own));
        }
        // Its own port did not count. Which one did? A port pushed with it (a lift's second motor) first.
        String mate = biggest(delta, mates, null, motor);
        if (mate != null) {
            return new Verdict(false, "counts on \"" + mate + "\"" + port(portNumbers, mate)
                    + " instead: plug the encoder cable into \"" + motor + "\"'s port (only its encoder is read)");
        }
        String other = biggest(delta, null, mates, motor);
        if (other != null) {
            return new Verdict(false, "counts on \"" + other + "\"" + port(portNumbers, other)
                    + " instead: encoder cables swapped?");
        }
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

    private static String port(Map<String, Integer> numbers, String name) {
        Integer n = numbers.get(name);
        return n == null || n < 0 ? "" : " (port " + n + ")";
    }
}
