package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.match.MatchTimer;
import com.crossbotics.ftchelper.testkit.PretendClock;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.testkit.PretendMotor;
import com.crossbotics.ftchelper.tuning.TuningReset;
import org.junit.After;
import org.junit.Test;

/**
 * The Feed Stop Finder's test shot (Launcher.feedTest): it opens the stop with the finder's numbers, times
 * the piece until the wheel sees the shot, counts a second piece, and says when no shot came. Also the
 * catalog move time (Parts.ServoPart.moveSec) and feedStop(servo, part).
 */
public class FeedTestTest {

    private final PretendClock clock = PretendClock.use();

    @After public void back() {
        PretendClock.stopUsing();
        SettingsFile.useForTests(null);
        TuningReset.clear();
        MatchTimer.forget();
    }

    public static class Numbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher {
            public static double speedRpm = 3000, feedStopHold = 0.1, feedStopRelease = 0.6;
        }
    }

    public static class Bot extends RobotDefinition {
        public Intake intake;
        public Launcher launcher;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feedStop("stop", "gobilda-2000-speed")
                    .pushedBy(intake));
        }
    }

    // Loops for this many pretend seconds (5 ms a loop); the wheel is `dropRpm` below its target.
    private void runFor(FeedTest test, PretendMotor fly, double sec, double dropRpm) {
        for (int i = 0; i < Math.round(sec / 0.005); i++) {
            clock.addSec(0.005);
            fly.velocity = fly.velocityCommand - dropRpm / 60.0 * 28;
            test.update();
        }
    }

    // A robot after INIT, its test feed with the finder's numbers, and the wheel up to speed.
    private FeedTest ready(PretendDevices d, Bot r) {
        r.setup(d);
        FeedTest test = r.launcher.feedTest(0.15, 0.55, 0.2, 3.0);
        assertEquals("the test feed holds the stop first", 0.15, d.servo("stop").position, 1e-9);
        test.spin(true);
        runFor(test, d.motor("launcher"), 1.0, 0);
        assertTrue("at speed", test.isReady());
        return test;
    }

    @Test public void aTestShotIsTimedUntilTheWheelSeesIt() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        FeedTest test = ready(d, r);
        PretendMotor fly = d.motor("launcher");
        test.fire();
        assertTrue(test.isBusy());
        assertEquals("the stop opens to the finder's release", 0.55, d.servo("stop").position, 1e-9);
        runFor(test, fly, 0.1, 0);
        assertTrue("the intake pushes once the stop had its 0.2 s to open... not yet", d.motor("intake").power == 0);
        runFor(test, fly, 0.2, 0);
        assertTrue("then the intake pushes the piece in", d.motor("intake").power > 0);
        runFor(test, fly, 0.05, 400);   // the shot: the wheel slows
        assertEquals("the stop closes on the shot", 0.15, d.servo("stop").position, 1e-9);
        runFor(test, fly, 1.0, 0);
        assertFalse("done after the watch for a second piece", test.isBusy());
        assertEquals("shot seen", test.lastEnd());
        assertEquals("about 0.3 s to the shot", 0.3, test.lastSec(), 0.011);
        assertEquals(1, test.lastPieces());
    }

    @Test public void aSecondPieceThatSlipsThroughIsCounted() {
        PretendDevices d = new PretendDevices();
        FeedTest test = ready(d, new Bot());
        PretendMotor fly = d.motor("launcher");
        test.fire();
        runFor(test, fly, 0.3, 0);
        runFor(test, fly, 0.05, 400);   // the first piece
        runFor(test, fly, 0.1, 0);      // the speed comes back...
        runFor(test, fly, 0.05, 400);   // ...and a second piece hits the wheel while the stop closes
        runFor(test, fly, 1.0, 0);
        assertFalse(test.isBusy());
        assertEquals(2, test.lastPieces());
    }

    @Test public void noShotEndsOnTheBackupTime() {
        PretendDevices d = new PretendDevices();
        FeedTest test = ready(d, new Bot());
        test.fire();
        runFor(test, d.motor("launcher"), 4.0, 0);
        assertFalse(test.isBusy());
        assertEquals("backup time", test.lastEnd());
        assertTrue("no time without a shot", Double.isNaN(test.lastSec()));
        assertEquals(0.15, d.servo("stop").position, 1e-9);
    }

    @Test public void stoppingTheWheelCancelsTheShotAndHolds() {
        PretendDevices d = new PretendDevices();
        FeedTest test = ready(d, new Bot());
        test.fire();
        runFor(test, d.motor("launcher"), 0.1, 0);
        test.spin(false);
        assertFalse(test.isBusy());
        assertEquals("the stop holds", 0.15, d.servo("stop").position, 1e-9);
        assertEquals("the wheel is off", 0, d.motor("launcher").power, 1e-9);
        test.fire();
        assertFalse("no test shot with the wheel off", test.isBusy());
    }

    @Test public void noFeedStopGivesATestThatDoesNothing() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        Launcher plain = Launcher.flywheel("l", "gobilda-5203-6000");
        FeedTest none = plain.feedTest(0.15, 0.55, 0.2, 3.0);
        none.spin(true);
        none.update();
        none.fire();
        assertFalse(none.isReady());
        assertFalse(none.isBusy());
    }

    @Test public void theCatalogGivesTheMoveTime() {
        Parts.ServoPart speed = Parts.servoPart("gobilda-2000-speed");
        // 0.4 of the normal 270 degrees = 108 degrees.
        assertEquals(108.0 / 60 * speed.secPer60Deg, speed.moveSec(0.15, 0.55, false), 1e-9);
        assertEquals("the same either way", speed.moveSec(0.15, 0.55, false), speed.moveSec(0.55, 0.15, false), 1e-12);
    }

    @Test public void feedStopRemembersItsPart() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        assertNotNull(r.launcher.feedStopPart());
        assertEquals("gobilda-2000-speed", r.launcher.feedStopPart().id);
        assertEquals("not written: 0.1", 0.1, r.launcher.feedStopMoveSec(), 1e-9);
        assertNull(Launcher.flywheel("l", "gobilda-5203-6000").feedStop("stop").feedStopPart());
        try {
            Launcher.flywheel("l", "gobilda-5203-6000").feedStop("stop", "gobilda-2000-sped");
            fail("a typo in the part stops");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("gobilda-2000-speed"));
        }
    }
}
