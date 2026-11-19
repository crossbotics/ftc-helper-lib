package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.match.MatchTimer;
import com.crossbotics.ftchelper.testkit.PretendClock;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.testkit.PretendMotor;
import com.crossbotics.ftchelper.tuning.TuningReset;
import org.junit.After;
import org.junit.Test;

/**
 * The Feed Time Finder's test shot (Launcher.feedTimeTest): it runs the feeder at its power, times the
 * piece until the wheel sees the shot, stops the feeder at the shot, and says when no shot came in the
 * time limit. Also which launchers have a timed feeder.
 */
public class FeedTimeTestTest {

    private final PretendClock clock = PretendClock.use();

    @After public void back() {
        PretendClock.stopUsing();
        SettingsFile.useForTests(null);
        TuningReset.clear();
        MatchTimer.forget();
    }

    public static class Numbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; }
    }

    public static class Bot extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            Indexer feeder = Indexer.crServo("feeder_left").alsoServoReversed("feeder_right").tunables("Feeder");
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(feeder));
        }
    }

    public static class StopBot extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            Indexer feeder = Indexer.crServo("feeder_left").tunables("Feeder");
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feedStop("stop").feeder(feeder));
        }
    }

    public static class PushBot extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("feeder")));
        }
    }

    // Loops for this many pretend seconds (5 ms a loop); the wheel is `dropRpm` below its target.
    private void runFor(FeedTimeTest test, PretendMotor fly, double sec, double dropRpm) {
        for (int i = 0; i < Math.round(sec / 0.005); i++) {
            clock.addSec(0.005);
            fly.velocity = fly.velocityCommand - dropRpm / 60.0 * 28;
            test.update();
        }
    }

    private FeedTimeTest ready(PretendDevices d, Bot r) {
        r.setup(d);
        FeedTimeTest test = r.launcher.feedTimeTest(3.0);
        assertTrue(test.works());
        test.spin(true);
        runFor(test, d.motor("launcher"), 1.0, 0);
        assertTrue("at speed", test.isReady());
        return test;
    }

    @Test public void aTestShotIsTimedUntilTheWheelSeesIt() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        FeedTimeTest test = ready(d, r);
        PretendMotor fly = d.motor("launcher");
        test.fire();
        assertTrue(test.isBusy());
        assertEquals("both wheels at the feeder's power", 0.9, d.crServo("feeder_left").power, 1e-9);
        assertEquals(0.9, d.crServo("feeder_right").power, 1e-9);
        runFor(test, fly, 0.6, 0);
        assertTrue("past feedSec (0.4): it still runs, to time a slow piece", test.isBusy());
        runFor(test, fly, 0.01, 400);
        assertFalse("the shot ends it", test.isBusy());
        assertEquals("the feeder stops at the shot", 0, d.crServo("feeder_left").power, 1e-9);
        assertEquals(0, d.crServo("feeder_right").power, 1e-9);
        assertEquals(0.605, test.lastSec(), 0.011);
        assertEquals("shot seen", test.lastEnd());
        assertEquals("Feeder", test.feederSection());
        assertEquals(0.4, test.feedSecNow(), 1e-9);
        assertFalse(test.stopsOnShot());
    }

    @Test public void noShotInTheLimitIsNotATime() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        FeedTimeTest test = ready(d, r);
        test.fire();
        runFor(test, d.motor("launcher"), 3.1, 0);
        assertFalse(test.isBusy());
        assertTrue(Double.isNaN(test.lastSec()));
        assertEquals("time limit", test.lastEnd());
        assertEquals("the feeder stops", 0, d.crServo("feeder_left").power, 1e-9);
    }

    @Test public void itFiresOnlyWhenReady() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        FeedTimeTest test = r.launcher.feedTimeTest(3.0);
        test.fire();
        assertFalse("wheel off: no test shot", test.isBusy());
        assertEquals(0, d.crServo("feeder_left").power, 1e-9);
    }

    @Test public void wheelOffStopsAFeed() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        FeedTimeTest test = ready(d, r);
        PretendMotor fly = d.motor("launcher");
        test.fire();
        runFor(test, fly, 0.3, 0);
        runFor(test, fly, 0.005, 400);                // a first shot: 0.3 s
        assertEquals(0.305, test.lastSec(), 0.011);
        runFor(test, fly, 1.0, 0);                    // back to speed
        test.fire();
        test.spin(false);                             // the wheel goes off part way
        assertFalse(test.isBusy());
        assertEquals(0, d.crServo("feeder_left").power, 1e-9);
        assertTrue("no time for a stopped feed (not the last shot's again)", Double.isNaN(test.lastSec()));
        assertEquals("stopped", test.lastEnd());
    }

    @Test public void onlyATimedFeederHasTheTest() {
        assertTrue(new Bot().describe().modules().stream().anyMatch(m -> m instanceof Launcher && ((Launcher) m).hasTimedFeeder()));
        StopBot s = new StopBot();
        s.describe();
        assertFalse("a feed stop: the Feed Stop Finder times it", s.launcher.hasTimedFeeder());
        PushBot p = new PushBot();
        p.describe();
        assertFalse("a push servo: no feedSec", p.launcher.hasTimedFeeder());
        PushBot set = new PushBot();
        set.setup(new PretendDevices());
        assertFalse("it gives a test that does nothing", set.launcher.feedTimeTest(3).works());
    }
}
