package com.crossbotics.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.finders.FlywheelTuneResults.Battery;
import com.crossbotics.ftchelper.finders.FlywheelTuneResults.Test;
import com.crossbotics.ftchelper.finders.FlywheelTuneResults.Verdict;
import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Output;
import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Sample;
import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Stage;
import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Stop;
import com.crossbotics.ftchelper.launcher.Flywheel.SpeedControl;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * The Flywheel Tuner's session on a pretend weighted flywheel ({@link FlywheelTunePlant}: 43 RPM reading
 * steps, 50 ms late, 20 to 60 ms loops), at 11 to 13 V, with the wheel 15% weaker or stronger than its kF
 * says and 15% heavier or lighter. What it picks must itself stay within the 100 RPM limit on that wheel.
 */
public class FlywheelTuneSessionTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private FlywheelTunePlant plant;

    @After public void realClock() {
        if (plant != null) plant.close();
    }

    // R1's launcher: table 27 to 45 in (2500 to 2918 RPM, middle 2642.5), ready band 100, a ball takes 230 RPM.
    static FlywheelTuneSession.Config config(SpeedControl installed) {
        FlywheelTuneSession.Config c = new FlywheelTuneSession.Config();
        c.today = new FlywheelSetting(installed, 10, 0, 0, 12.08, 0, 0.002237, 200, 75, 50, 1);
        c.middleRpm = 2642.5;
        c.lowRpm = 2500;
        c.highRpm = 2918;
        c.readyBandRpm = 100;
        c.settleSec = 0.15;
        c.dropRpm = 230;
        c.paceSec = 0.3;
        c.readyTimeoutSec = 3;
        c.countsPerRev = 28;
        c.checkBattery = false;
        return c;
    }

    // Runs until the session finishes: A at each stop, the LOW battery after the FULL run, A at each driving check.
    private static FlywheelTuneSession runAll(FlywheelTunePlant p, FlywheelTuneSession s, double lowVolts) {
        double fullVolts = p.battery;
        for (int i = 0; i < 400000 && s.stage() != Stage.FINISHED; i++) {
            Stage st = s.stage();
            if (st == Stage.READY || st == Stage.ASK_DRIVE) s.pressA();
            if (st == Stage.BETWEEN) {
                p.battery = lowVolts;   // the battery change
                s.pressA();
            }
            assertTrue("stopped: " + s.stopWhy() + " " + s.stopDetail(), st != Stage.STOPPED);
            p.loop(s);
        }
        assertEquals(dump(s), Stage.FINISHED, s.stage());
        p.battery = fullVolts;
        return s;
    }

    // Every finished test, a line each (for a failure message).
    static String dump(FlywheelTuneSession s) {
        StringBuilder b = new StringBuilder();
        for (Test t : s.results().tests()) {
            b.append(t.battery).append(' ').append(t.id).append(' ').append(t.verdict).append(' ').append(t.why)
                    .append(' ').append(t.numbers()).append('\n');
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- the plant test

    @org.junit.Test public void whatItPicksMeetsTheLimitOnThePlantFrom11To13VoltsWithMismatch() {
        double[][] volts = {{13.0, 12.2}, {12.4, 11.6}, {11.6, 11.0}};
        double[] kvShares = {0.85, 1, 1.15};
        double[] tauShares = {0.85, 1.15};
        long seed = 1;
        int pidWins = 0, bangWins = 0;
        StringBuilder report = new StringBuilder();
        for (double[] v : volts) {
            for (double kvs : kvShares) {
                for (double ts : tauShares) {
                    FlywheelTuneSession s = new FlywheelTuneSession(config(SpeedControl.PID), new FlywheelTuneResults("x"));
                    plant = new FlywheelTunePlant(seed++, kvs, ts, v[0]);
                    runAll(plant, s, v[1]);
                    plant.close();
                    String where = String.format(java.util.Locale.US, "%.1f/%.1f V, kv x%.2f, tau x%.2f", v[0], v[1], kvs, ts);
                    if (v[1] < 11.5) {
                        // A battery resting at 11.0 V sags under 10.5 V at full power from rest (its inner resistance):
                        // the voltage rule rejects every setting on it, so that run picks nothing. (Its start
                        // refuses such a battery anyway: the LOW run needs 12.0 to 12.4 V.)
                        for (Test t : s.results().tests()) {
                            if (t.battery == Battery.LOW && t.isCandidate()) {
                                assertEquals(where + " " + t.id, FlywheelTuneSession.Reason.VOLTAGE.words, t.why);
                            }
                        }
                    }
                    for (SpeedControl c : SpeedControl.values()) {
                        FlywheelSetting w = s.results().winner(c);
                        report.append(where).append(" ").append(c).append(": ").append(w == null ? "none" : w.text()).append('\n');
                        if (w == null) continue;
                        if (c == SpeedControl.PID) pidWins++;
                        else bangWins++;
                        // The picked setting, on the same wheel at both batteries and at 11.0 V: never more than
                        // 100 RPM past.
                        for (double b : new double[] {v[0], v[1], 11.0}) {
                            double worst = replay(w, seed++, kvs, ts, b, s.config());
                            assertTrue(where + " " + c + " at " + b + " V: " + worst + " RPM past the target\n" + w.text(), worst <= 100);
                        }
                    }
                }
            }
        }
        System.out.println(report);
        // Bang-bang reads the wheel's own power (the fit), so it finds a setting on every wheel whose LOW battery
        // does not sag under 10.5 V (12 of the 18).
        assertEquals(report.toString(), 12, bangWins);
        assertTrue(report.toString(), pidWins > 0);
    }

    // A real hub's reading flickers between two steps (R1, 2026-11-24: every FULL test failed "said at speed, then
    // lost it right away"). A wheel that creeps up to the band's edge reads in and out of the band. That is the
    // reading, not the setting: a reading counts as "lost" only when it is more than one step out of the band.
    @org.junit.Test public void aReadingThatFlickersAtTheBandEdgeIsNotLostSpeed() {
        int seed = 1;
        StringBuilder report = new StringBuilder();
        for (double kvs : new double[] {0.95, 1, 1.05}) {
            for (double ts : new double[] {1, 1.15}) {
                FlywheelTuneSession s = new FlywheelTuneSession(config(SpeedControl.PID), new FlywheelTuneResults("x"));
                plant = new FlywheelTunePlant(seed++, kvs, ts, 12.9);
                plant.flicker = true;
                runAll(plant, s, 12.3);
                plant.close();
                for (Test t : s.results().tests()) {
                    if (FlywheelTuneSession.Reason.FALSE_READY.words.equals(t.why)) report.append(kvs).append('/').append(ts).append(' ').append(t.battery).append(' ').append(t.id).append('\n');
                }
            }
        }
        assertEquals(report.toString(), "", report.toString());
    }

    // The picked setting on a fresh wheel: from a stop, hold, 3 pretend shots, up, down. The most REAL RPM past
    // the target (above after a rise, below after the step down).
    private double replay(FlywheelSetting w, long seed, double kvs, double ts, double volts, FlywheelTuneSession.Config c) {
        FlywheelTunePlant p = new FlywheelTunePlant(seed, kvs, ts, volts);
        try {
            w.withControl(w.control).applyTo(p.flywheel);
            double worst = 0;
            double[] targets = {c.middleRpm, c.highRpm, c.lowRpm};
            for (int k = 0; k < targets.length; k++) {
                double target = targets[k];
                double endAt = p.t + 6;
                while (p.t < endAt) {
                    p.run(0.02 + p.random.nextDouble() * 0.04);
                    p.flywheel.setRpm(target);
                    p.flywheel.update();
                    double past = k == 2 ? (p.t > endAt - 3 ? target - p.rpm : 0) : p.rpm - target;
                    worst = Math.max(worst, past);
                }
                if (k == 0) {
                    for (int shot = 0; shot < 3; shot++) {
                        p.flywheel.stop();
                        while (p.rpm > target - c.dropRpm) p.run(0.02);
                        double back = p.t + 3;
                        while (p.t < back) {
                            p.run(0.02 + p.random.nextDouble() * 0.04);
                            p.flywheel.setRpm(target);
                            p.flywheel.update();
                            worst = Math.max(worst, p.rpm - target);
                        }
                    }
                }
            }
            p.flywheel.stop();
            return worst;
        } finally {
            p.close();
        }
    }

    // ---------------------------------------------------------------- start and stop

    private FlywheelTuneSession fresh() {
        plant = new FlywheelTunePlant(7, 1, 1, 12.8);
        return new FlywheelTuneSession(config(SpeedControl.PID), new FlywheelTuneResults("x"));
    }

    private void loops(FlywheelTuneSession s, double sec) {
        double end = plant.t + sec;
        while (plant.t < end) plant.loop(s);
    }

    @org.junit.Test public void nothingMovesBeforeAAndDuringTheCountdown() {
        FlywheelTuneSession s = fresh();
        for (int i = 0; i < 50; i++) assertEquals(Output.Kind.OFF, plant.loop(s).kind);
        assertEquals(Stage.READY, s.stage());
        s.pressA();
        assertEquals(Stage.COUNTDOWN, s.stage());
        assertEquals(3, s.countdownLeft());
        double start = plant.t;
        while (s.stage() == Stage.COUNTDOWN) assertEquals(Output.Kind.OFF, plant.loop(s).kind);
        assertTrue("3 s countdown", plant.t - start >= 3 - 1e-6 && plant.t - start < 3.1);
        assertEquals(Stage.TESTING, s.stage());
    }

    @org.junit.Test public void aStopInTheCountdownStartsNothing() {
        FlywheelTuneSession s = fresh();
        plant.loop(s);
        s.pressA();
        loops(s, 1);
        s.stop(Stop.BUTTON, "");
        assertEquals(Stage.STOPPED, s.stage());
        loops(s, 5);
        assertEquals(0, plant.m.power, 0);
        assertEquals(0, plant.rpm, 0);
        assertEquals(Stage.STOPPED, s.stage());
    }

    @org.junit.Test public void aStopCutsThePowerAtOnceAndANewPressGoesOnFromTheSameTest() {
        FlywheelTuneSession s = fresh();
        plant.loop(s);
        s.pressA();
        // Through "today" (test 1) and into full power (test 2).
        while (s.results().find(Battery.FULL, "today") == null) plant.loop(s);
        while (plant.m.power < 1) plant.loop(s);
        assertEquals(2, s.testNumber());
        s.stop(Stop.BUTTON, "");
        assertEquals(Output.Kind.OFF, plant.loop(s).kind);
        assertEquals("the power is off in the same loop", 0, plant.m.power, 0);
        assertEquals(DcMotorMode.WITHOUT, mode());
        loops(s, 3);
        assertEquals("nothing restarts by itself", 0, plant.m.power, 0);
        assertEquals(1, s.results().tests().size());
        s.pressA();
        assertEquals(Stage.COUNTDOWN, s.stage());
        assertEquals("goes on from test 2", 2, s.testNumber());
    }

    private enum DcMotorMode { WITHOUT, OTHER }

    private DcMotorMode mode() {
        return plant.m.mode == com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_WITHOUT_ENCODER ? DcMotorMode.WITHOUT : DcMotorMode.OTHER;
    }

    @org.junit.Test public void aButtonDoesNothingWhileItIsNotMovingExceptAAndB() {
        FlywheelTuneSession s = fresh();
        s.stop(Stop.BUTTON, "");
        assertEquals("a stop with nothing moving changes nothing", Stage.READY, s.stage());
        s.pressB();
        assertEquals(Stage.READY, s.stage());
    }

    @org.junit.Test public void aSpeedThatReadsZeroWithThePowerOnStops() {
        FlywheelTuneSession s = fresh();
        plant.loop(s);
        s.pressA();
        // A broken cable: the reading stays 0 whatever the wheel does.
        plant.deadEncoder = true;
        while (s.stage() != Stage.STOPPED && plant.t < 60) plant.loop(s);
        assertEquals(Stage.STOPPED, s.stage());
        assertEquals(Stop.ENCODER, s.stopWhy());
        assertEquals(0, plant.m.power, 0);
    }

    // Decided: 2 readings in a row under 10 V stop the run; the finished tests are kept (only the running one is lost).
    @org.junit.Test public void aBatteryUnder10VoltsForTwoReadingsStopsTheRunAndKeepsTheFinishedTests() {
        FlywheelTuneSession s = fresh();
        plant.loop(s);
        s.pressA();
        while (s.results().find(Battery.FULL, "today") == null) plant.loop(s);
        int kept = s.results().tests().size();
        plant.battery = 9.8;
        loops(s, 1);
        assertEquals(Stage.STOPPED, s.stage());
        assertEquals(Stop.LOW_BATTERY, s.stopWhy());
        assertEquals("the finished tests are kept", kept, s.results().tests().size());
        assertEquals(0, plant.m.power, 0);
    }

    // One dip under 10 V (a full-power start on a used battery) does not stop anything.
    @org.junit.Test public void oneReadingUnder10VoltsDoesNotStopTheRun() {
        FlywheelTuneSession s = fresh();
        plant.loop(s);
        s.pressA();
        while (s.results().find(Battery.FULL, "today") == null) plant.loop(s);
        while (plant.m.power < 1) plant.loop(s);       // full power: the test that dips the battery
        plant.run(0.03);
        double read = plant.m.motor.getVelocity() / FlywheelTunePlant.CPR * 60;
        plant.driver.apply(s.update(new Sample(plant.t, read, plant.m.motor.getCurrentPosition(), 9.7, plant.amps,
                plant.driver.appliedPower(), "")));
        loops(s, 0.5);
        assertEquals("one low reading: it goes on", Stage.TESTING, s.stage());
        assertEquals(1, plant.m.power, 0);
    }

    // ---------------------------------------------------------------- current, hard braking, the drive

    // Everything before this test is done (today's settings, full power), so the next one is the power fit.
    private static FlywheelTuneResults upToFit(FlywheelTuneSession.Config c) {
        FlywheelTuneResults r = new FlywheelTuneResults("x");
        r.add(new Test(Battery.FULL, "today", Verdict.PASS, "", c.today, readyIn(4)));
        r.add(new Test(Battery.FULL, "fullpower", Verdict.INFO, "", null, fullPower()));
        return r;
    }

    private static Map<String, Double> fullPower() {
        Map<String, Double> fp = new LinkedHashMap<>();
        fp.put("accel", 1700.0);
        fp.put("step", 43.0);
        fp.put("delay", 0.11);
        fp.put("reachSec", 1.6);
        return fp;
    }

    @org.junit.Test public void tooMuchCurrentAtFullPowerStopsTheRun() {
        FlywheelTuneSession s = fresh();
        plant.loop(s);
        s.pressA();
        while (s.results().find(Battery.FULL, "today") == null) plant.loop(s);
        plant.extraAmps = 6;    // the wheel drags: the motor takes 6 A more whenever it has power
        for (int i = 0; i < 5000 && s.stage() == Stage.TESTING; i++) plant.loop(s);
        assertEquals(Stage.STOPPED, s.stage());
        assertEquals(Stop.CURRENT, s.stopWhy());
        assertNull("the measurement does not count", s.results().find(Battery.FULL, "fullpower"));
        assertEquals("the power is off", 0, plant.m.power, 0);
    }

    @org.junit.Test public void tooMuchCurrentInThePowerFitStopsTheRun() {
        plant = new FlywheelTunePlant(9, 1, 1, 12.8);
        FlywheelTuneSession.Config c = config(SpeedControl.PID);
        FlywheelTuneSession s = new FlywheelTuneSession(c, upToFit(c));
        assertEquals("fit", s.nextId());
        plant.loop(s);
        s.pressA();
        while (plant.m.power == 0 && plant.t < 30) plant.loop(s);
        assertEquals(Stage.TESTING, s.stage());
        plant.extraAmps = 6;
        for (int i = 0; i < 5000 && s.stage() == Stage.TESTING; i++) plant.loop(s);
        assertEquals(Stage.STOPPED, s.stage());
        assertEquals(Stop.CURRENT, s.stopWhy());
        assertNull(s.results().find(Battery.FULL, "fit"));
        assertEquals(0, plant.m.power, 0);
    }

    // A speed that never settles in the power fit: the next speed starts with the power OFF for that loop (the
    // wheel may already be at it), never with full power.
    @org.junit.Test public void aSpeedThatDoesNotSettleInThePowerFitGivesNoFullPower() {
        FlywheelTuneSession.Config c = config(SpeedControl.PID);
        FlywheelTuneSession s = new FlywheelTuneSession(c, upToFit(c));
        double t = 0;
        s.update(new Sample(t, 0, Double.NaN, 12.8, 1, 0, ""));
        s.pressA();
        Output out = null;
        // The countdown and the rest at 0 RPM, then the first speed (2500): reached at once, then it swings
        // 100 RPM either way, never steady, until its time limit.
        int k = 0;
        for (; t < 3 + 1.2; t += 0.02) out = s.update(new Sample(t, 0, Double.NaN, 12.8, 1, Double.NaN, ""));
        double plateauEnd = t + FlywheelTuneSession.PLATEAU_LIMIT + 0.5;
        boolean sawEnd = false;
        for (; t < plateauEnd && s.stage() == Stage.TESTING; t += 0.02) {
            double rpm = 2500 + (k++ % 2 == 0 ? 100 : -100);
            out = s.update(new Sample(t, rpm, Double.NaN, 12.8, 1, Double.NaN, ""));
            if (s.doing().contains("2 of 3")) {
                sawEnd = true;
                break;
            }
        }
        assertTrue("the first speed gave up: " + s.doing(), sawEnd);
        assertEquals(Output.Kind.POWER, out.kind);
        assertEquals("no full power when a speed did not settle", 0, out.power, 0);
    }

    // A PID step down that would brake the motor hard fails the setting BEFORE the lower speed is sent.
    @org.junit.Test public void aHardBrakingStepDownIsNeverSent() {
        plant = new FlywheelTunePlant(13, 1, 1, 12.8);
        FlywheelTuneSession.Config c = config(SpeedControl.PID);
        FlywheelTuneResults r = upToFit(c);
        Map<String, Double> fit = new LinkedHashMap<>();
        fit.put("ks", 0.2);
        fit.put("kv", 0.00216);
        r.add(new Test(Battery.FULL, "fit", Verdict.INFO, "", null, fit));
        FlywheelTuneSession s = new FlywheelTuneSession(c, r);
        for (double kp : s.pidList()) {
            if (kp >= 150) break;
            r.add(new Test(Battery.FULL, "pid:kP=" + (long) kp, Verdict.PASS, "", c.today.withPid(kp, 12.08), readyIn(1)));
        }
        s = new FlywheelTuneSession(c, r);
        assertEquals("pid:kP=150", s.nextId());
        plant.loop(s);
        s.pressA();
        List<String> braked = new ArrayList<>();
        for (int i = 0; i < 20000 && s.results().find(Battery.FULL, "pid:kP=150") == null; i++) {
            plant.run(0.02 + plant.random.nextDouble() * 0.04);
            double read = plant.m.motor.getVelocity() / FlywheelTunePlant.CPR * 60;
            Output out = s.update(new Sample(plant.t, read, plant.m.motor.getCurrentPosition(), plant.terminal, plant.amps,
                    plant.driver.appliedPower(), plant.flywheel.getFault()));
            if (out.kind == Output.Kind.CONTROL && FlywheelTuneSession.hardReverse(out.setting, read, out.targetRpm, 28)) {
                braked.add(String.format(java.util.Locale.US, "%.0f RPM, target %.0f", read, out.targetRpm));
            }
            plant.driver.apply(out);
        }
        Test t = s.results().find(Battery.FULL, "pid:kP=150");
        assertNotNull(t);
        assertEquals("kP 150 from 2918 to 2500 RPM would brake hard: " + t.numbers(), FlywheelTuneSession.Reason.REVERSE.words, t.why);
        assertTrue("a hard brake was sent: " + braked, braked.isEmpty());
    }

    // No drive on the robot: A cannot start a driving check (the sticks would move nothing, and it would pass).
    @org.junit.Test public void noDriveMeansEveryDrivingCheckIsSkippedAndThereIsNoWinner() {
        plant = new FlywheelTunePlant(31, 1, 1, 12.8);
        FlywheelTuneSession.Config c = config(SpeedControl.PID);
        c.hasDrive = false;
        FlywheelTuneSession s = new FlywheelTuneSession(c, new FlywheelTuneResults("x"));
        boolean asked = false;
        for (int i = 0; i < 400000 && s.stage() != Stage.FINISHED; i++) {
            Stage st = s.stage();
            if (st == Stage.READY) s.pressA();
            if (st == Stage.BETWEEN) {
                plant.battery = 12.2;
                s.pressA();
            }
            if (st == Stage.ASK_DRIVE) {
                asked = true;
                s.pressA();
                assertTrue("A starts no countdown", s.stage() != Stage.COUNTDOWN);
                assertTrue(s.refusal(), s.refusal().startsWith("No drive found: the driving check is skipped."));
            }
            plant.loop(s);
        }
        assertTrue(asked);
        assertEquals(Stage.FINISHED, s.stage());
        for (Test t : s.results().tests()) {
            if (t.id.startsWith("drive:")) assertEquals(t.id, FlywheelTuneSession.Reason.SKIPPED.words, t.why);
        }
        assertNull(s.results().winner(SpeedControl.BANG_BANG));
        assertNull(s.results().winner(SpeedControl.PID));
    }

    // ---------------------------------------------------------------- the pretend shot's size

    @org.junit.Test public void aPretendShotTooSmallToTimeDoesNotRankByRecovery() {
        FlywheelSetting a = config(SpeedControl.PID).today.withPid(25, 12.08), b = a.withPid(50, 12.08);
        for (double drop : new double[] {150, 230}) {
            FlywheelTuneSession.Config c = config(SpeedControl.PID);
            c.dropRpm = drop;
            FlywheelTuneResults r = new FlywheelTuneResults(c.setup());
            r.add(new Test(Battery.FULL, "fullpower", Verdict.INFO, "", null, fullPower()));
            // a: back sooner, but wobbles more; b: slower back, steadier.
            r.add(new Test(Battery.FULL, "pid:kP=25", Verdict.PASS, "", a, numbers(0.20, 30)));
            r.add(new Test(Battery.FULL, "pid:kP=50", Verdict.PASS, "", b, numbers(0.30, 10)));
            String best = r.ranked(SpeedControl.PID).get(0).id;
            if (drop == 150) {
                assertFalse("150 - 100 = 50 RPM: under 2 steps of 43", r.recoveryRanks());
                assertEquals("the steadier wins", "pid:kP=50", best);
            } else {
                assertTrue("230 - 100 = 130 RPM: 2 steps or more", r.recoveryRanks());
                assertEquals("the quicker back wins", "pid:kP=25", best);
            }
        }
    }

    // kI: after the kP list, on the best kP that passed, smallest first; an unsafe failure ends the kI list.
    @org.junit.Test public void kITriesSmallFirstOnTheBestKpAndStopsWhenUnsafe() {
        FlywheelTuneSession.Config c = config(SpeedControl.PID);
        FlywheelTuneResults r = upToFit(c);
        Map<String, Double> fit = new LinkedHashMap<>();
        fit.put("ks", 0.2);
        fit.put("kv", 0.00216);
        r.add(new Test(Battery.FULL, "fit", Verdict.INFO, "", null, fit));
        FlywheelTuneSession s = new FlywheelTuneSession(c, r);
        for (double kp : s.pidList()) {
            // kP 50 is back the soonest: the best.
            r.add(new Test(Battery.FULL, "pid:kP=" + (long) kp, Verdict.PASS, "", c.today.withPid(kp, 12.3),
                    numbers(kp == 50 ? 0.2 : 0.4, 20)));
        }
        s = new FlywheelTuneSession(c, r);
        assertEquals("pid:kP=50:kI=0.02", s.nextId());
        assertEquals(0.02, s.nextSetting().kI, 1e-12);
        assertEquals(50, s.nextSetting().kP, 1e-12);
        assertEquals("kF from its hold", 12.3, s.nextSetting().kF, 1e-12);
        r.add(new Test(Battery.FULL, "pid:kP=50:kI=0.02", Verdict.PASS, "", s.nextSetting(), numbers(0.5, 20)));
        s = new FlywheelTuneSession(c, r);
        assertEquals("pid:kP=50:kI=0.05", s.nextId());
        r.add(new Test(Battery.FULL, "pid:kP=50:kI=0.05", Verdict.FAIL, FlywheelTuneSession.Reason.OVERSHOOT.words,
                s.nextSetting(), numbers(Double.NaN, 20)));
        s = new FlywheelTuneSession(c, r);
        assertTrue("no bigger kI after going too far past: " + s.nextId(), s.nextId().startsWith("bang:"));
        // The LOW battery checks the best 3, and also the best one with kI (it is for a weaker battery).
        List<String> low = new ArrayList<>();
        for (Test t : s.forLow(SpeedControl.PID)) low.add(t.id);
        assertEquals(4, low.size());
        assertEquals("pid:kP=50:kI=0.02", low.get(3));
    }

    // A robot that already has a kI tries no other kI.
    @org.junit.Test public void aRobotWithKiTriesNoOtherKi() {
        FlywheelTuneSession.Config c = config(SpeedControl.PID);
        c.today = c.today.withKi(0.1);
        FlywheelTuneResults r = upToFit(c);
        r.add(new Test(Battery.FULL, "fit", Verdict.INFO, "", null, new LinkedHashMap<String, Double>()));
        FlywheelTuneSession s = new FlywheelTuneSession(c, r);
        for (double kp : s.pidList()) {
            r.add(new Test(Battery.FULL, "pid:kP=" + (long) kp, Verdict.PASS, "", c.today.withPid(kp, 12.08), numbers(0.3, 20)));
        }
        s = new FlywheelTuneSession(c, r);
        assertTrue(s.nextId(), s.nextId().startsWith("bang:"));
    }

    private static Map<String, Double> numbers(double recovery, double wobble) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("recovery", recovery);
        m.put("wobble", wobble);
        return m;
    }

    @org.junit.Test public void eachBatteryRunStartsOnlyOnTheRightBattery() {
        assertEquals("", FlywheelTuneSession.batteryProblem(Battery.FULL, 12.6));
        assertTrue(FlywheelTuneSession.batteryProblem(Battery.FULL, 12.5).contains("NOT full"));
        assertEquals("", FlywheelTuneSession.batteryProblem(Battery.LOW, 12.0));
        assertEquals("", FlywheelTuneSession.batteryProblem(Battery.LOW, 12.4));
        assertTrue(FlywheelTuneSession.batteryProblem(Battery.LOW, 12.5).contains("too full"));
        assertTrue(FlywheelTuneSession.batteryProblem(Battery.LOW, 11.9).contains("too low"));
        FlywheelTuneSession.Config c = config(SpeedControl.PID);
        c.checkBattery = true;
        plant = new FlywheelTunePlant(3, 1, 1, 12.4);
        FlywheelTuneSession s = new FlywheelTuneSession(c, new FlywheelTuneResults("x"));
        plant.loop(s);
        s.pressA();
        assertEquals(Stage.READY, s.stage());
        assertTrue(s.refusal(), s.refusal().contains("NOT full"));
        plant.battery = 12.8;
        plant.loop(s);
        s.pressA();
        assertEquals(Stage.COUNTDOWN, s.stage());
    }

    // ---------------------------------------------------------------- what it finds

    @org.junit.Test public void itTestsBothControlsWhenTheRobotUsesEither() {
        for (SpeedControl installed : SpeedControl.values()) {
            plant = new FlywheelTunePlant(11, 1, 1, 12.8);
            FlywheelTuneSession s = runAll(plant, new FlywheelTuneSession(config(installed), new FlywheelTuneResults("x")), 12.2);
            plant.close();
            assertEquals(installed, s.results().find(Battery.FULL, "today").setting.control);
            boolean pid = false, bang = false;
            for (Test t : s.results().tests()) {
                if (t.isCandidate() && t.setting.control == SpeedControl.PID) pid = true;
                if (t.isCandidate() && t.setting.control == SpeedControl.BANG_BANG) bang = true;
            }
            assertTrue(installed + ": PID tested", pid);
            assertTrue(installed + ": bang-bang tested", bang);
            assertNotNull(installed + ": a bang-bang winner", s.results().winner(SpeedControl.BANG_BANG));
        }
    }

    @org.junit.Test public void everyBangBangNumberCanChangeAndIsPrinted() {
        // (A wheel that needs a little power to turn at all, so the fit finds a bangKs above today's 0.)
        plant = new FlywheelTunePlant(5, 1.0, 1, 12.8);
        FlywheelTuneSession s = runAll(plant, new FlywheelTuneSession(config(SpeedControl.PID), new FlywheelTuneResults("x")), 12.2);
        FlywheelSetting today = s.config().today;
        // The reading's step is found while the wheel coasts (full power skips steps between loops).
        assertEquals(43, s.results().find(Battery.FULL, "fullpower").number("step"), 1);
        FlywheelSetting w = s.results().winner(SpeedControl.BANG_BANG);
        assertNotNull(w);
        assertTrue("bangKs", w.ks != today.ks);
        assertTrue("bangKv", w.kv != today.kv);
        assertTrue("bangFullBelowRpm", w.fullBelowRpm != today.fullBelowRpm);
        assertTrue("bangCoastAboveRpm", w.coastAboveRpm != today.coastAboveRpm);
        assertTrue("bangHysteresisRpm", w.hysteresisRpm != today.hysteresisRpm);
        List<String> lines = FlywheelTunerOpMode.tunablesLines(w);
        for (String name : new String[] {"bangKs", "bangKv", "bangFullBelowRpm", "bangCoastAboveRpm", "bangHysteresisRpm", "bangMaxPower"}) {
            boolean found = false;
            for (String l : lines) if (l.startsWith("public static double " + name + " = ")) found = true;
            assertTrue(name + " in " + lines, found);
        }
        // Max power: 1 unless a current or voltage limit stopped it.
        assertEquals(1, w.maxPower, 0);
    }

    @org.junit.Test public void aCurrentLimitStepsTheMostPowerDownTo09Then08() {
        FlywheelTuneResults r = new FlywheelTuneResults("x");
        FlywheelTuneSession.Config c = config(SpeedControl.PID);
        FlywheelTuneSession s = new FlywheelTuneSession(c, r);
        // Everything before bang-bang is done; each bang-bang setting at full power fails on current.
        Map<String, Double> none = new LinkedHashMap<>();
        r.add(new Test(Battery.FULL, "today", Verdict.PASS, "", c.today, readyIn(4)));
        Map<String, Double> fp = new LinkedHashMap<>();
        fp.put("accel", 1700.0);
        fp.put("step", 43.0);
        fp.put("delay", 0.11);
        r.add(new Test(Battery.FULL, "fullpower", Verdict.INFO, "", null, fp));
        r.add(new Test(Battery.FULL, "fit", Verdict.INFO, "", null, none));
        for (double kp : s.pidList()) {
            r.add(new Test(Battery.FULL, "pid:kP=" + (long) kp, Verdict.FAIL, FlywheelTuneSession.Reason.AVERAGE.words, c.today.withPid(kp, 12.08), none));
        }
        for (double ki : FlywheelTuneSession.PID_KI) {
            r.add(new Test(Battery.FULL, "pid:kP=10:kI=" + ki, Verdict.FAIL, FlywheelTuneSession.Reason.AVERAGE.words, c.today.withKi(ki), none));
        }
        double w = s.bangWidths().get(0);
        List<Double> powers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            FlywheelTuneSession again = new FlywheelTuneSession(c, r);
            FlywheelSetting next = again.nextSetting();
            if (next == null || next.control != SpeedControl.BANG_BANG) break;
            powers.add(next.maxPower);
            assertEquals(w, next.fullBelowRpm, 0);
            r.add(new Test(Battery.FULL, again.nextId(), Verdict.FAIL, FlywheelTuneSession.Reason.CURRENT.words, next, none));
        }
        assertEquals("[1.0, 0.9, 0.8]", powers.toString());
        assertEquals("then the family stops: today's settings again", "drift", new FlywheelTuneSession(c, r).nextId());
    }

    private static Map<String, Double> readyIn(double sec) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("ready", sec);
        return m;
    }

    @org.junit.Test public void pidHardBrakingIsCalculated() {
        FlywheelSetting s = config(SpeedControl.PID).today.withPid(150, 12.08);
        // 418 RPM too fast at 2500: 150 x 195 = 29260 > 12.08 x 1167 + 6553 = 20650.
        assertTrue(FlywheelTuneSession.hardReverse(s, 2918, 2500, 28));
        assertFalse(FlywheelTuneSession.hardReverse(s.withPid(50, 12.08), 2918, 2500, 28));
        assertFalse("never with no target", FlywheelTuneSession.hardReverse(s, 2918, 0, 28));
    }

    @org.junit.Test public void aLightWheelThatSkipsTheWindowStillGivesItsSpeedUp() {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {0, 2000});
        pts.add(new double[] {0.04, 2240});
        pts.add(new double[] {0.08, 2480});
        assertEquals(6000, FlywheelTuneSession.slope(pts), 1e-6);
        pts.remove(2);
        pts.remove(1);
        assertTrue("one reading is not a slope", Double.isNaN(FlywheelTuneSession.slope(pts)));
    }

    @org.junit.Test public void aStoppedRunGoesOnWithABatteryALittleLower() {
        assertEquals("", FlywheelTuneSession.resumeProblem(Battery.FULL, 12.4));
        assertTrue(FlywheelTuneSession.resumeProblem(Battery.FULL, 12.2).contains("not this run's battery"));
        assertEquals("", FlywheelTuneSession.resumeProblem(Battery.LOW, 11.8));
        assertTrue(FlywheelTuneSession.resumeProblem(Battery.LOW, 12.6).contains("not this run's battery"));
    }

    @org.junit.Test public void thePowerFitFindsTheLine() {
        List<double[]> pts = new ArrayList<>();
        for (double rpm : new double[] {2500, 2642, 2918}) pts.add(new double[] {rpm, 0.3 + 0.002 * rpm});
        double[] f = FlywheelTuneSession.fitLine(pts);
        assertEquals(0.3, f[0], 1e-3);
        assertEquals(0.002, f[1], 1e-6);
        pts.remove(2);
        assertTrue("2 points are not enough", Double.isNaN(FlywheelTuneSession.fitLine(pts)[1]));
    }

    // ---------------------------------------------------------------- saved runs

    @org.junit.Test public void savedRunsSurviveAPowerCycleFullThenLow() throws Exception {
        File was = FlywheelTuneResults.file;
        try {
            FlywheelTuneResults.file = new File(tmp.getRoot(), "flywheel-tuner.txt");
            plant = new FlywheelTunePlant(21, 1, 1, 12.8);
            FlywheelTuneSession.Config c = config(SpeedControl.PID);
            FlywheelTuneSession s = new FlywheelTuneSession(c, new FlywheelTuneResults(c.setup()));
            plant.loop(s);
            s.pressA();
            int saved = 0;
            for (int i = 0; i < 400000 && s.stage() != Stage.BETWEEN; i++) {
                assertTrue("stopped: " + s.stopWhy() + " " + s.stopDetail() + "\n" + dump(s), s.stage() != Stage.STOPPED);
                plant.loop(s);
                if (s.saves() != saved) {
                    saved = s.saves();
                    assertTrue(s.results().saveNow());   // the OpMode saves in the background; here at once
                }
            }
            int fullTests = s.results().tests().size();
            plant.close();
            // Power off, the LOW battery in, the tuner opened again: it reads the file.
            FlywheelTuneResults back = FlywheelTuneResults.load();
            assertEquals(c.setup(), back.setup());
            assertEquals(fullTests, back.tests().size());
            plant = new FlywheelTunePlant(22, 1, 1, 12.2);
            FlywheelTuneSession s2 = new FlywheelTuneSession(c, back);
            assertEquals(Stage.BETWEEN, s2.stage());
            assertEquals(Battery.LOW, s2.battery());
            runAll(plant, s2, 12.2);
            assertTrue(s2.results().saveNow());
            FlywheelTuneResults end = FlywheelTuneResults.load();
            assertTrue(end.isFinished());
            assertNotNull(end.winner(SpeedControl.BANG_BANG));
            assertEquals(s2.results().winner(SpeedControl.BANG_BANG).text(), end.winner(SpeedControl.BANG_BANG).text());
            FlywheelTuneSession s3 = new FlywheelTuneSession(c, end);
            assertEquals(Stage.FINISHED, s3.stage());
        } finally {
            FlywheelTuneResults.file = was;
        }
    }

    @org.junit.Test public void aSettingReadsBackExactly() {
        FlywheelSetting s = new FlywheelSetting(SpeedControl.BANG_BANG, 10, 0.117, 0, 12.08, 0.0312, 0.0021375, 185, 75, 43, 0.9);
        assertEquals(s.text(), FlywheelSetting.parse(s.text()).text());
        assertTrue(s.sameAs(FlywheelSetting.parse(s.text())));
        assertNull(FlywheelSetting.parse("PID kP=1"));
    }

    @org.junit.Test public void aSkippedDrivingCheckMeansNoWinner() {
        plant = new FlywheelTunePlant(31, 1, 1, 12.8);
        FlywheelTuneSession s = new FlywheelTuneSession(config(SpeedControl.PID), new FlywheelTuneResults("x"));
        double lowVolts = 12.2;
        for (int i = 0; i < 400000 && s.stage() != Stage.FINISHED; i++) {
            Stage st = s.stage();
            if (st == Stage.READY) s.pressA();
            if (st == Stage.BETWEEN) {
                plant.battery = lowVolts;
                s.pressA();
            }
            if (st == Stage.ASK_DRIVE) s.pressB();
            plant.loop(s);
        }
        assertEquals(Stage.FINISHED, s.stage());
        assertNull(s.results().winner(SpeedControl.BANG_BANG));
        assertNull(s.results().winner(SpeedControl.PID));
    }
}
