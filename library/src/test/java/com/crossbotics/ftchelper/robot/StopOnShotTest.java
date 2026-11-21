package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.launcher.Launcher.ShotMode;
import com.crossbotics.ftchelper.match.MatchTimer;
import com.crossbotics.ftchelper.testkit.PretendClock;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.testkit.PretendMotor;
import com.crossbotics.ftchelper.tuning.TuningReset;
import org.junit.After;
import org.junit.Test;

/**
 * Tunables stopOnShot on a feeder that spins: the feed ends when the flywheel sees the shot (its speed
 * drops), and feedSec stays the time limit. In SPRAY the wheels keep turning.
 */
public class StopOnShotTest {

    private final PretendClock clock = PretendClock.use();

    @After public void back() {
        PretendClock.stopUsing();
        SettingsFile.useForTests(null);
        TuningReset.clear();
        MatchTimer.forget();
    }

    public static class On {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; public static boolean stopOnShot = true; }
    }

    public static class BigDrop {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000, shotDropRpm = 1200; }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; public static boolean stopOnShot = true; }
    }

    public static class Off {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; }
    }

    public static class Bot extends RobotDefinition {
        private final Class<?> numbers;
        public Launcher launcher;
        public Indexer feeder;

        Bot(Class<?> numbers) { this.numbers = numbers; }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            feeder = Indexer.crServo("feeder_left").alsoServoReversed("feeder_right").tunables("Feeder");
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(feeder));
        }
    }

    // The same feeder behind a feed stop servo: the feed stop (ShotGate) closes on the shot and stops the wheels.
    public static class StopBot extends RobotDefinition {
        public Launcher launcher;
        public Indexer feeder;

        @Override protected void define() {
            tunables(On.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            feeder = Indexer.crServo("feeder_left").alsoServoReversed("feeder_right").tunables("Feeder");
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feedStop("stop").feeder(feeder));
        }
    }

    public static class MotorBot extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            tunables(On.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.motor("feeder").tunables("Feeder")));
        }
    }

    // Loops of 5 ms; the flywheel holds the speed it was asked for.
    private void runFor(RobotDefinition r, PretendMotor fly, double sec) {
        for (int i = 0; i < Math.round(sec / 0.005); i++) {
            clock.addSec(0.005);
            fly.velocity = fly.velocityCommand;
            r.all().update();
        }
    }

    // One loop where the piece hits the wheel: its speed drops by 30% (far more than the 150 RPM shot drop).
    private void shot(RobotDefinition r, PretendMotor fly) {
        clock.addSec(0.005);
        fly.velocity = fly.velocityCommand * 0.7;
        r.all().update();
    }

    private Bot started(PretendDevices d, Class<?> numbers) {
        Bot r = new Bot(numbers);
        r.setup(d);
        r.startForTests();
        r.launcher.spinUp();
        runFor(r, d.motor("launcher"), 0.5);
        return r;
    }

    @Test public void theWheelsStopWhenTheShotIsSeen() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, On.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.1);
        assertEquals("both wheels run", 0.9, d.crServo("feeder_left").power, 1e-9);
        shot(r, fly);
        assertEquals("the shot: both stop at once", 0, d.crServo("feeder_left").power, 1e-9);
        assertEquals(0, d.crServo("feeder_right").power, 1e-9);
        runFor(r, fly, 0.05);
        assertFalse("the shot is done", r.launcher.isShooting());
    }

    @Test public void offTheWheelsRunTheFullFeedSec() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, Off.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.1);
        shot(r, fly);
        assertEquals("stopOnShot off (the default): the shot changes nothing", 0.9, d.crServo("feeder_left").power, 1e-9);
        runFor(r, fly, 0.4);
        assertEquals("feedSec ends it", 0, d.crServo("feeder_left").power, 1e-9);
    }

    @Test public void noShotSeenFeedSecIsTheTimeLimit() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, On.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.3);
        assertEquals("no shot yet: still feeding", 0.9, d.crServo("feeder_left").power, 1e-9);
        runFor(r, fly, 0.15);
        assertEquals("feedSec reached: it stops anyway", 0, d.crServo("feeder_left").power, 1e-9);
    }

    @Test public void theNextShotWaitsForItsOwnDrop() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, On.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire(2);
        runFor(r, fly, 0.1);
        shot(r, fly);
        assertEquals(0, d.crServo("feeder_left").power, 1e-9);
        // The wheel gets back to speed, then the second piece feeds; the first drop does not end it.
        runFor(r, fly, 0.5);
        assertEquals("the second piece feeds", 0.9, d.crServo("feeder_left").power, 1e-9);
        shot(r, fly);
        assertEquals("its own shot stops it", 0, d.crServo("feeder_left").power, 1e-9);
    }

    @Test public void inSprayTheWheelsKeepTurning() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, On.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.shotMode(ShotMode.SPRAY);
        r.launcher.fire(3);
        runFor(r, fly, 0.02);
        // Three pieces, a shot every 0.3 s: the wheels never stop between them.
        for (int piece = 0; piece < 3; piece++) {
            runFor(r, fly, 0.1);
            shot(r, fly);
            assertEquals("SPRAY: a shot does not stop the wheels", 0.9, d.crServo("feeder_left").power, 1e-9);
            for (int i = 0; i < 40; i++) {
                runFor(r, fly, 0.005);
                if (!r.launcher.isShooting()) break;
                assertNotEquals("SPRAY: never stopped between pieces", 0, d.crServo("feeder_left").power, 1e-9);
            }
        }
        runFor(r, fly, 0.6);
        assertEquals("the last piece's feedSec ends it", 0, d.crServo("feeder_left").power, 1e-9);
    }

    @Test public void aMotorFeederStopsOnTheShotToo() {
        PretendDevices d = new PretendDevices();
        MotorBot r = new MotorBot();
        r.setup(d);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        runFor(r, fly, 0.5);
        r.launcher.fire();
        runFor(r, fly, 0.1);
        assertTrue("the motor feeds", Math.abs(d.motor("feeder").power) > 0.5);
        shot(r, fly);
        assertEquals("the shot: it stops", 0, d.motor("feeder").power, 1e-9);
    }

    @Test public void shotDropRpmSetsTheDropThatCountsAsAShot() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, BigDrop.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.1);
        shot(r, fly);   // a 900 RPM drop: less than 1200
        assertEquals("a smaller drop is not a shot", 0.9, d.crServo("feeder_left").power, 1e-9);
        runFor(r, fly, 0.05);
        clock.addSec(0.005);
        fly.velocity = fly.velocityCommand * 0.5;   // a 1500 RPM drop: more than 1200
        r.all().update();
        assertEquals("a bigger drop is a shot", 0, d.crServo("feeder_left").power, 1e-9);
    }

    public static class ZeroDrop {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000, shotDropRpm = 0; }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; }
    }

    @Test public void shotDropRpmOfZeroIsAnError() {
        Bot r = new Bot(ZeroDrop.class);
        try {
            r.setup(new PretendDevices());
            org.junit.Assert.fail("0 must stop the setup");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("shotDropRpm"));
        }
    }

    public static class SmallDrop {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000, readyBandRpm = 100, shotDropRpm = 80; }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; }
    }

    @Test public void shotDropRpmUnderTheReadyBandGivesANote() {
        Bot r = new Bot(SmallDrop.class);
        r.setup(new PretendDevices());
        String notes = r.launcher.notes().toString();
        assertTrue(notes, notes.contains("shotDropRpm (80) is not more than readyBandRpm (100)"));
    }

    // Counts the feeds that ended fired and not fired (what Pieces uses to count a piece out).
    private static int[] watch(Indexer feeder) {
        int[] ends = new int[2];   // [0] fired, [1] not fired
        feeder.watchFeeds(new Indexer.FeedWatcher() {
            @Override public void started() { }
            @Override public void ended(boolean fired) { ends[fired ? 0 : 1]++; }
        });
        return ends;
    }

    @Test public void aShotEndedFeedCountsAsFired() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, On.class);
        int[] ends = watch(r.feeder);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.1);
        shot(r, fly);
        runFor(r, fly, 0.05);
        assertEquals("fired", 1, ends[0]);
        assertEquals("not 'stopped part way'", 0, ends[1]);
    }

    @Test public void withAFeedStopTheShotStillCountsAsFired() {
        PretendDevices d = new PretendDevices();
        StopBot r = new StopBot();
        r.setup(d);
        r.startForTests();
        int[] ends = watch(r.feeder);
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        runFor(r, fly, 0.5);
        r.launcher.fire();
        for (int i = 0; i < 200 && d.crServo("feeder_left").power == 0; i++) runFor(r, fly, 0.005);
        assertEquals("the stop is open: the wheels feed", 0.9, d.crServo("feeder_left").power, 1e-9);
        runFor(r, fly, 0.05);
        shot(r, fly);
        runFor(r, fly, 0.05);
        assertEquals("the stop closes on the shot: the wheels stop", 0, d.crServo("feeder_left").power, 1e-9);
        assertEquals("the piece went: fired", 1, ends[0]);
        assertEquals(0, ends[1]);
    }

    @Test public void aFeedStartedByTheIndexerItselfWaitsForItsOwnShot() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, On.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.1);
        shot(r, fly);
        runFor(r, fly, 0.5);   // back to speed; the shot count is 1 now
        r.feeder.feedOne();    // not through the launcher
        runFor(r, fly, 0.1);
        assertEquals("the old shot does not end the new feed", 0.9, d.crServo("feeder_left").power, 1e-9);
        shot(r, fly);
        assertEquals("its own shot does", 0, d.crServo("feeder_left").power, 1e-9);
    }

    public static class Wobble {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000, readyBandRpm = 60, shotDropRpm = 100; }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; public static boolean stopOnShot = true; }
    }

    public static class ShortEject {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000, ejectRpm = 600, ejectSec = 1, ejectFeedSec = 0.2; }
        public static class Feeder { public static double power = 0.9, feedSec = 1.0; public static boolean stopOnShot = true; }
    }

    @Test public void ejectFeedSecStopsTheFeedWheelsEarly() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, ShortEject.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.eject();
        for (int i = 0; i < 10; i++) {                        // the wheel at the eject speed: it feeds
            clock.addSec(0.005);
            fly.velocity = fly.velocityCommand;
            r.all().update();
        }
        assertEquals("feeding the piece out", 0.9, d.crServo("feeder_left").power, 1e-9);
        for (int i = 0; i < 40; i++) {                        // 0.2 s more
            clock.addSec(0.005);
            fly.velocity = fly.velocityCommand;
            r.all().update();
        }
        assertEquals("after ejectFeedSec: stopped (feedSec is 1 s)", 0, d.crServo("feeder_left").power, 1e-9);
    }

    @Test public void wobbleInsideTheReadyBandIsNotAShot() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, Wobble.class);
        PretendMotor fly = d.motor("launcher");
        double target = fly.velocityCommand;
        fly.velocity = target * 3055 / 3000;
        clock.addSec(0.005);
        r.all().update();
        r.launcher.fire();
        for (int i = 0; i < 20; i++) {
            clock.addSec(0.005);
            fly.velocity = target * 3055 / 3000;
            r.all().update();
        }
        assertEquals("feeding", 0.9, d.crServo("feeder_left").power, 1e-9);
        // 2945: 110 RPM under the start, but only 55 under the target (inside the 60 ready band).
        for (int i = 0; i < 5; i++) {
            clock.addSec(0.005);
            fly.velocity = target * 2945 / 3000;
            r.all().update();
        }
        assertEquals("no shot: still feeding", 0.9, d.crServo("feeder_left").power, 1e-9);
    }

    @Test public void aShotInSprayDoesNotStopTheFeedAfterTheModeChangesBack() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, On.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.shotMode(ShotMode.SPRAY);
        r.launcher.fire();
        runFor(r, fly, 0.1);
        shot(r, fly);
        runFor(r, fly, 0.02);
        r.launcher.shotMode(ShotMode.CAREFUL);
        runFor(r, fly, 0.02);
        assertEquals("the SPRAY shot does not end the feed later", 0.9, d.crServo("feeder_left").power, 1e-9);
    }

    public static class ServoOn {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class Indexer { public static boolean stopOnShot = true; }
    }

    public static class ServoBot extends RobotDefinition {
        @Override protected void define() {
            tunables(ServoOn.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer")));
        }
    }

    @Test public void stopOnShotOnAPushServoStopsInit() {
        try {
            new ServoBot().setup(new PretendDevices());
            org.junit.Assert.fail("stopOnShot on a push servo must stop the setup");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("stopOnShot is for a CR servo or motor feeder"));
        }
    }

    public static class Defaults {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000, readyBandRpm = 200; }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; }
    }

    @Test public void theDefaultShotDropUnderABigReadyBandGivesANote() {
        Bot r = new Bot(Defaults.class);
        r.setup(new PretendDevices());
        String notes = r.launcher.notes().toString();
        assertTrue(notes, notes.contains("shotDropRpm (150) is not more than readyBandRpm (200)"));
    }

    @Test public void noNoteAtTheDefaults() {
        Bot r = new Bot(On.class);
        r.setup(new PretendDevices());
        assertFalse(r.launcher.notes().toString().contains("shotDropRpm"));
    }
}
