package com.crossbotics.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The Encoder Check's steps with pretend motors and a pretend clock: each motor's push must count on its own
 * port, up. Wiring faults are made by choosing which shaft each port reads.
 */
public class EncoderCheckTest {

    // A pretend robot: each motor's shaft turns while it has power; each port reads one shaft (or none).
    static final class Rig {
        double now = 0;
        final Map<String, Double> power = new HashMap<>();
        final Map<String, Double> shaft = new HashMap<>();
        // Which shaft each port reads (null = no cable) and its sign (-1 = counts backward).
        final Map<String, String> wire = new HashMap<>();
        final Map<String, Integer> sign = new HashMap<>();
        final List<EncoderCheck.Job> jobs = new ArrayList<>();
        final List<EncoderCheck.Port> ports = new ArrayList<>();
        double maxPower = 0;
        // Ports whose read throws (a hub that does not answer).
        final java.util.Set<String> broken = new HashSet<>();
        final Map<String, Double> maxOf = new HashMap<>();
        final Map<String, Double> minOf = new HashMap<>();
        EncoderCheck check;

        Rig(String... motors) {
            for (final String m : motors) {
                power.put(m, 0.0);
                shaft.put(m, 0.0);
                wire.put(m, m);
                sign.put(m, 1);
                ports.add(new EncoderCheck.Port(m, () -> {
                    if (broken.contains(m)) throw new RuntimeException("read failed");
                    String w = wire.get(m);
                    return w == null ? 0 : (int) Math.round(sign.get(m) * shaft.get(w));
                }));
            }
        }

        EncoderCheck.Job job(String name, boolean read, String... mates) {
            EncoderCheck.Job j = new EncoderCheck.Job(name, "part", read, null);
            j.power.add(p -> setPower(name, p));
            for (final String mate : mates) {
                j.mates.add(mate);
                j.power.add(p -> setPower(mate, p));
            }
            jobs.add(j);
            return j;
        }

        void setPower(String m, double p) {
            power.put(m, p);
            maxPower = Math.max(maxPower, Math.abs(p));
            maxOf.put(m, Math.max(maxOf.containsKey(m) ? maxOf.get(m) : 0, Math.abs(p)));
            minOf.put(m, Math.min(minOf.containsKey(m) ? minOf.get(m) : 0, p));
        }

        EncoderCheck start() {
            check = new EncoderCheck(jobs, ports, () -> now);
            return check;
        }

        // 20 ms loops: 1000 counts per second at full power.
        void run(double seconds, boolean go) {
            for (double t = 0; t < seconds; t += 0.02) {
                now += 0.02;
                for (String m : power.keySet()) shaft.put(m, shaft.get(m) + power.get(m) * 1000 * 0.02);
                check.update(go);
            }
        }

        double minPowerOf(String m) {
            return minOf.containsKey(m) ? minOf.get(m) : 0;
        }

        double maxPowerOf(String m) {
            return maxOf.containsKey(m) ? maxOf.get(m) : 0;
        }

        String result(String name) {
            for (EncoderCheck.Job j : jobs) if (j.name.equals(name)) return j.result;
            return null;
        }
    }

    @Test public void everyCableRightIsOkAndThePushIsShortAndLow() {
        Rig r = new Rig("a", "b");
        r.job("a", true);
        r.job("b", true);
        r.start();
        r.run(5, true);
        assertTrue(r.check.done());
        assertTrue(r.result("a"), r.result("a").startsWith("OK"));
        assertTrue(r.result("b"), r.result("b").startsWith("OK"));
        assertEquals("never more than the check's low power", EncoderCheck.POWER, r.maxPower, 1e-9);
        assertEquals("every motor stopped at the end", 0, r.power.get("a") + r.power.get("b"), 1e-9);
    }

    @Test public void swappedCablesNameTheOtherPort() {
        Rig r = new Rig("lift", "flywheel");
        r.wire.put("lift", "flywheel");
        r.wire.put("flywheel", "lift");
        r.job("lift", true);
        r.job("flywheel", true);
        r.start();
        r.run(5, true);
        assertTrue(r.result("lift"), r.result("lift").contains("counts on \"flywheel\" instead"));
        assertTrue(r.result("lift"), r.result("lift").contains("swapped"));
        assertTrue(r.result("flywheel"), r.result("flywheel").contains("counts on \"lift\" instead"));
        assertFalse(r.check.jobs().get(0).ok);
    }

    @Test public void aMissingCableHasNoCountsButIsFineWhereNothingReadsIt() {
        Rig r = new Rig("arm", "intake");
        r.wire.put("arm", null);
        r.wire.put("intake", null);
        r.job("arm", true);
        r.job("intake", false);
        r.start();
        r.run(6, true);
        assertTrue(r.result("arm"), r.result("arm").startsWith("NO COUNTS"));
        assertFalse(r.check.jobs().get(0).ok);
        assertTrue(r.result("intake"), r.result("intake").contains("fine"));
        assertTrue(r.check.jobs().get(1).ok);
    }

    @Test public void countingDownIsBackward() {
        Rig r = new Rig("lift");
        r.sign.put("lift", -1);
        r.job("lift", true);
        r.start();
        r.run(3, true);
        assertTrue(r.result("lift"), r.result("lift").contains("BACKWARD"));
    }

    @Test public void aTwoMotorLiftWithTheEncoderOnTheSecondMotorSaysWhereToPlugIt() {
        Rig r = new Rig("lift_left", "lift_right");
        r.wire.put("lift_left", null);
        r.job("lift_left", true, "lift_right");
        r.start();
        r.run(3, true);
        assertEquals(0, r.power.get("lift_right"), 1e-9);
        assertTrue(r.result("lift_left"), r.result("lift_left").contains("plug the encoder cable into \"lift_left\""));
    }

    @Test public void lettingGoStopsAtOnceAndPushesThatMotorAgain() {
        Rig r = new Rig("a");
        r.job("a", true);
        r.start();
        // Wait until the push starts.
        while (!r.check.pushing()) r.run(0.02, true);
        assertEquals(EncoderCheck.POWER, r.power.get("a"), 1e-9);
        r.run(0.02, false);
        assertEquals("let go = power 0 in the same loop", 0, r.power.get("a"), 1e-9);
        assertEquals(null, r.result("a"));
        r.run(3, true);
        assertTrue(r.result("a").startsWith("OK"));
    }

    @Test public void waitsForAStillSpinningFlywheelBeforeTheNextPush() {
        Rig r = new Rig("flywheel", "lift");
        r.wire.put("lift", null);   // the lift's cable is missing: only the flywheel's port can move
        r.job("flywheel", true);
        r.job("lift", true);
        r.start();
        // The flywheel's push.
        while (r.check.at() == 0) r.run(0.02, true);
        // It keeps coasting (its shaft still turns slowly) for a while after its push.
        r.power.put("flywheel", 0.05);
        r.run(0.5, true);
        assertFalse("still moving: the lift is not pushed yet", r.check.pushing());
        r.power.put("flywheel", 0.0);
        r.run(3, true);
        assertTrue(r.result("lift"), r.result("lift").startsWith("NO COUNTS"));
    }

    @Test public void skippedMotorsAreNeverPushed() {
        Rig r = new Rig("a", "b");
        r.jobs.add(new EncoderCheck.Job("a", "drive", true, "its port reads an odometry pod"));
        r.job("b", true);
        r.start();
        r.run(3, true);
        assertEquals(0, r.maxPowerOf("a"), 1e-9);
        assertTrue(r.result("a").startsWith("skipped"));
        assertTrue(r.result("b").startsWith("OK"));
    }

    @Test public void judgeIgnoresJitter() {
        Map<String, Integer> delta = new LinkedHashMap<>();
        delta.put("a", 5);
        delta.put("b", -7);
        EncoderCheck.Verdict v = EncoderCheck.judge("a", Collections.<String>emptySet(), true, 1, delta);
        assertTrue(v.text, v.text.startsWith("NO COUNTS"));
        delta.put("b", 400);
        v = EncoderCheck.judge("a", new HashSet<>(Arrays.asList("x")), true, 1, delta);
        assertTrue(v.text, v.text.contains("counts on \"b\" instead"));
    }

    // A part whose room is on the negative side (a hood whose posB is below posA) is pushed with negative
    // power, and its count must go down.
    @Test public void aNegativePushMustCountDown() {
        Rig r = new Rig("hood");
        r.job("hood", true).sign = -1;
        r.start();
        r.run(3, true);
        assertEquals(-EncoderCheck.POWER, r.minPowerOf("hood"), 1e-9);
        assertTrue(r.result("hood"), r.result("hood").startsWith("OK: counts down"));
    }

    @Test public void aReadThatFailsIsNeverJudgedAsAFault() {
        Rig r = new Rig("a", "b");
        r.shaft.put("b", 1000.0);   // b has counted before (the hub counts since power-up)
        r.job("a", true);
        r.start();
        // b's read fails during the push: it is left out, not blamed (no "counts on b").
        while (!r.check.pushing()) r.run(0.02, true);
        r.broken.add("b");
        r.run(1, true);
        assertTrue(r.result("a"), r.result("a").startsWith("OK"));
        // a's own read fails: "could not read", never BACKWARD or NO COUNTS.
        Rig r2 = new Rig("a");
        r2.job("a", true);
        r2.start();
        while (!r2.check.pushing()) r2.run(0.02, true);
        r2.broken.add("a");
        r2.run(1, true);
        assertTrue(r2.result("a"), r2.result("a").startsWith("could not read"));
        assertFalse(r2.check.jobs().get(0).ok);
    }

    @Test public void neverPushesWhileAnotherPartStillMoves() {
        Rig r = new Rig("flywheel", "a");
        r.job("a", true);
        r.start();
        r.power.put("flywheel", 0.05);   // coasting on and on
        r.run(EncoderCheck.STILL_SLOW_SEC + 1, true);
        assertFalse(r.check.pushing());
        assertEquals(0, r.maxPowerOf("a"), 1e-9);
        assertEquals("the screen names it", "flywheel", r.check.stillMoving());
        r.power.put("flywheel", 0.0);
        r.run(2, true);
        assertTrue(r.result("a"), r.result("a").startsWith("OK"));
    }

    @Test public void anIntakeWithItsEncoderOnTheSecondMotorIsFine() {
        Rig r = new Rig("intake_a", "intake_b");
        r.wire.put("intake_a", null);
        r.job("intake_a", false, "intake_b");
        r.start();
        r.run(3, true);
        assertTrue(r.result("intake_a"), r.result("intake_a").contains("fine"));
    }

    @Test public void aStopErrorStillStopsTheOtherMotor() {
        Rig r = new Rig("lift_left", "lift_right");
        EncoderCheck.Job j = new EncoderCheck.Job("lift_left", "part", true, null);
        final boolean[] unplugged = {false};
        j.power.add(p -> { if (unplugged[0]) throw new RuntimeException("unplugged"); r.setPower("lift_left", p); });
        j.power.add(p -> r.setPower("lift_right", p));
        j.mates.add("lift_right");
        r.jobs.add(j);
        r.start();
        while (!r.check.pushing()) r.run(0.02, true);
        assertEquals(EncoderCheck.POWER, r.power.get("lift_right"), 1e-9);
        unplugged[0] = true;
        try {
            r.check.stop();
        } catch (RuntimeException expected) {
            // the first error is reported after every motor was tried
        }
        assertEquals(0, r.power.get("lift_right"), 1e-9);
    }

    @Test public void aFaultSkipIsAProblem() {
        Rig r = new Rig("lift");
        r.jobs.add(new EncoderCheck.Job("lift", "Lift", true, "NOT IN THE CONFIGURATION", true));
        r.start();
        assertTrue(r.check.done());
        assertFalse(r.check.jobs().get(0).ok);
        assertEquals(0, r.maxPowerOf("lift"), 1e-9);
    }
}
