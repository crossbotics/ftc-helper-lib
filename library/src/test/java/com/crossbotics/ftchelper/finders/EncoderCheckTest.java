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
        final Map<String, Double> maxOf = new HashMap<>();
        EncoderCheck check;

        Rig(String... motors) {
            int n = 0;
            for (final String m : motors) {
                power.put(m, 0.0);
                shaft.put(m, 0.0);
                wire.put(m, m);
                sign.put(m, 1);
                ports.add(new EncoderCheck.Port(m, n++, () -> {
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
        assertTrue(r.result("lift"), r.result("lift").contains("counts on \"flywheel\" (port 1) instead"));
        assertTrue(r.result("lift"), r.result("lift").contains("swapped"));
        assertTrue(r.result("flywheel"), r.result("flywheel").contains("counts on \"lift\" (port 0)"));
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
        Map<String, Integer> ports = new HashMap<>();
        ports.put("a", 0);
        ports.put("b", 1);
        EncoderCheck.Verdict v = EncoderCheck.judge("a", Collections.<String>emptySet(), true, delta, ports);
        assertTrue(v.text, v.text.startsWith("NO COUNTS"));
        delta.put("b", 400);
        v = EncoderCheck.judge("a", new HashSet<>(Arrays.asList("x")), true, delta, ports);
        assertTrue(v.text, v.text.contains("\"b\" (port 1)"));
    }
}
