package com.crossbotics.ftchelper.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.Fakes;
import org.junit.Test;

/**
 * The shot modes on pretend hardware: a wheel that loses 400 RPM at each feed and gets back at
 * 2000 RPM per second. CAREFUL waits for the full speed every piece, QUICK only for the first,
 * SPRAY never. The firing stats see the drop and the recovery.
 */
public class ShotModeTest {
    private static final double TPR = 28;   // counts per turn

    // The pretend robot: the launcher, its wheel motor, its feeder motor, the clock.
    private static final class Rig {
        final FakeMotor fm = new FakeMotor(), feedMotor = new FakeMotor();
        final Fakes.Clock c = new Fakes.Clock();
        final Launcher l;
        double rpm = 0;
        boolean wasFeeding = false;
        double dropRpm = 400, recoverRpmPerSec = 2000, capRpm = Double.MAX_VALUE;
        final java.util.List<Double> feedTimes = new java.util.ArrayList<>();

        Rig(Launcher.ShotMode mode) {
            Flywheel fw = new Flywheel("fly", fm.motor).withClock(c::get).countsPerRev(TPR).tolerance(50).settleTime(0.05);
            MotorFeeder feeder = new MotorFeeder("feeder", feedMotor.motor).withClock(c::get).feedTime(0.05);
            l = new Launcher(fw, feeder).withClock(c::get).shotInterval(0.1).readyTimeout(3).shotMode(mode);
            l.setRpm(3000);
        }

        // One 20 ms loop: the wheel speeds up toward its target; a new feed takes 400 RPM off.
        void loop() {
            c.addSec(0.02);
            double target = fm.velocityCommand / TPR * 60;
            rpm = Math.min(Math.min(target, capRpm), rpm + recoverRpmPerSec * 0.02);
            fm.velocity = rpm / 60 * TPR;
            l.update();
            boolean feeding = feedMotor.power > 0;
            if (feeding && !wasFeeding) {
                feedTimes.add(c.nanos / 1e9);
                rpm -= dropRpm;
            }
            wasFeeding = feeding;
        }

        // Spins up fully (2 s), then fires `count` and runs until done; the seconds the volley took.
        double volley(int count) {
            l.spinUp();
            for (int i = 0; i < 100; i++) loop();
            double start = c.nanos / 1e9;
            l.shoot(count);
            for (int i = 0; i < 300 && l.isShooting(); i++) loop();
            return feedTimes.get(feedTimes.size() - 1) - start;
        }
    }

    @Test public void quickVolleyIsFasterThanCarefulAndBothFireEveryPiece() {
        Rig careful = new Rig(Launcher.ShotMode.CAREFUL);
        Rig quick = new Rig(Launcher.ShotMode.QUICK);
        double tCareful = careful.volley(4);
        double tQuick = quick.volley(4);
        assertEquals(4, careful.l.getShotsFired());
        assertEquals(4, quick.l.getShotsFired());
        assertTrue("QUICK " + tQuick + " s < CAREFUL " + tCareful + " s", tQuick < tCareful * 0.85);
        // CAREFUL: every piece only after the wheel was back within 50 RPM: about 0.2 s + settle each.
        for (int i = 1; i < careful.feedTimes.size(); i++) {
            assertTrue(careful.feedTimes.get(i) - careful.feedTimes.get(i - 1) >= 0.2);
        }
    }

    @Test public void quickWaitsOnlyForItsBandOrItsTimeLimit() {
        Rig quick = new Rig(Launcher.ShotMode.QUICK);
        quick.l.quickBand(150).quickMaxWait(0.3);
        quick.l.spinUp();
        for (int i = 0; i < 100; i++) quick.loop();
        quick.recoverRpmPerSec = 200;          // now a wheel that recovers very slowly
        quick.l.shoot(3);
        for (int i = 0; i < 300 && quick.l.isShooting(); i++) quick.loop();
        assertEquals(3, quick.l.getShotsFired());
        // Never in the band in time: each next piece after the 0.3 s limit (plus the 0.1 s interval).
        for (int i = 1; i < quick.feedTimes.size(); i++) {
            double gap = quick.feedTimes.get(i) - quick.feedTimes.get(i - 1);
            assertTrue("gap " + gap, gap >= 0.3 && gap < 0.5);
        }
        assertEquals(150, quick.l.getQuickBand(), 1e-9);
    }

    @Test public void sprayDoesNotWaitForTheSpeedButNeedsATurningWheel() {
        Rig still = new Rig(Launcher.ShotMode.SPRAY);
        still.recoverRpmPerSec = 0;            // a wheel that never turns: SPRAY feeds nothing into it
        still.l.shoot(3);
        for (int i = 0; i < 60; i++) still.loop();
        assertEquals(0, still.l.getShotsFired());
        Rig spray = new Rig(Launcher.ShotMode.SPRAY);
        spray.capRpm = 2000;                   // the wheel only gets to 2000 of its 3000 rpm
        spray.dropRpm = 0;
        spray.l.shoot(3);
        for (int i = 0; i < 100; i++) spray.loop();
        assertEquals(3, spray.l.getShotsFired());
        // CAREFUL with the same wheel fires nothing and gives up.
        Rig careful = new Rig(Launcher.ShotMode.CAREFUL);
        careful.capRpm = 2000;
        careful.l.shoot(3);
        for (int i = 0; i < 200; i++) careful.loop();
        assertEquals(0, careful.l.getShotsFired());
        assertEquals("gave up: flywheel", careful.l.getLastProblem());
    }

    @Test public void statsSeeTheDropAndTheRecovery() {
        Rig careful = new Rig(Launcher.ShotMode.CAREFUL);
        careful.volley(3);
        for (int i = 0; i < 100; i++) careful.loop();   // 2 s: longer than a volley's gap
        ShotStats s = careful.l.getShotStats();
        assertEquals(3, s.count());
        assertEquals(3, s.volley().size());
        ShotStats.Shot last = s.last();
        assertEquals(Launcher.ShotMode.CAREFUL, last.mode);
        assertEquals(400, last.dropRpm, 45);              // 400, plus the 20 ms before the next reading
        assertEquals(0.25, last.recoverySec, 0.06);        // 0.2 s back into the band + 0.05 s settle
        assertTrue(Double.isNaN(s.volley().get(0).gapSec));
        assertTrue(s.volley().get(1).gapSec > 0.2);
        assertTrue(ShotStats.summary(s.volley()), ShotStats.summary(s.volley()).startsWith("drop avg "));
        // A new volley a while later: the old one becomes "last volley".
        careful.l.shoot(1);
        for (int i = 0; i < 50; i++) careful.loop();
        assertEquals(1, s.volley().size());
        assertEquals(3, s.lastVolley().size());
    }
}
