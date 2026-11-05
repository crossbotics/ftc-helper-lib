package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
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
 * A feed wheel (Launcher.feedWheel): a CR servo moves the pieces up to a push arm (the feeder). Only
 * while firing: the wheel starts, the arm pushes stageSec later, the wheel stops stopSec after the push
 * (below 0: before it). Always on: it runs while the flywheel is on, and the arm pushes at once.
 */
public class FeedWheelTest {

    // A pretend clock: each test moves the time by hand, so it never waits and gives the same result on a
    // slow computer.
    private final PretendClock clock = PretendClock.use();

    @After public void back() {
        PretendClock.stopUsing();
        SettingsFile.useForTests(null);
        TuningReset.clear();
        MatchTimer.forget();
    }

    public static class WhileFiring {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class FeederArm { public static double rest = 0.1, push = 0.7, pushSec = 0.3, returnSec = 0.3; }
        public static class FeederWheel { public static double power = 0.8, stageSec = 0.2, stopSec = 0.15; }
    }

    public static class StopsEarly {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class FeederArm { public static double rest = 0.1, push = 0.7, pushSec = 0.3, returnSec = 0.3; }
        public static class FeederWheel { public static double stageSec = 0.25, stopSec = -0.1; }
    }

    public static class AlwaysOn {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class FeederArm { public static double rest = 0.1, push = 0.7, pushSec = 0.3, returnSec = 0.3; }
        public static class FeederWheel { public static boolean alwaysOn = true; public static double power = 1.0, stageSec = 0.5; }
    }

    public static class LongStop {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class FeederArm { public static double rest = 0.1, push = 0.7, pushSec = 0.2, returnSec = 0.2; }
        public static class FeederWheel { public static double stageSec = 0.1, stopSec = 1.0; }
    }

    public static class Bot extends RobotDefinition {
        public Launcher launcher;
        private final Class<?> numbers;

        Bot(Class<?> numbers) {
            this.numbers = numbers;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.servo("feeder_arm").part("gobilda-2000-torque"))
                    .feedWheel(Indexer.crServo("feeder_wheel").part("gobilda-2000-speed")));
        }
    }

    private Bot started(PretendDevices d, Class<?> numbers) {
        Bot r = new Bot(numbers);
        r.setup(d);
        r.startForTests();
        r.launcher.spinUp();
        runFor(r, d.motor("launcher"), 0.3);   // at speed
        return r;
    }

    // Runs the loop for this many pretend seconds (5 ms a loop) with the flywheel at speed.
    private void runFor(Bot r, PretendMotor fly, double sec) {
        for (int i = 0; i < Math.round(sec / 0.005); i++) {
            clock.addSec(0.005);
            fly.velocity = fly.velocityCommand;
            r.all().update();
        }
    }

    @Test public void whileFiringTheWheelStagesThenTheArmPushesThenTheWheelStops() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, WhileFiring.class);
        PretendMotor fly = d.motor("launcher");
        assertEquals("off before firing", 0, d.crServo("feeder_wheel").power, 1e-9);
        r.launcher.fire();
        runFor(r, fly, 0.08);
        assertEquals("the wheel runs at its power at once", 0.8, d.crServo("feeder_wheel").power, 1e-9);
        assertEquals("the arm waits while the wheel stages the piece", 0.1, d.servo("feeder_arm").position, 1e-9);
        runFor(r, fly, 0.2);   // 0.28 s: past stageSec 0.2
        assertEquals("the arm pushes", 0.7, d.servo("feeder_arm").position, 1e-9);
        assertEquals("the wheel still turns while the arm lifts", 0.8, d.crServo("feeder_wheel").power, 1e-9);
        runFor(r, fly, 0.15);  // 0.43 s: past the push (about 0.2) + stopSec 0.15
        assertEquals("the wheel stops stopSec after the push", 0, d.crServo("feeder_wheel").power, 1e-9);
    }

    @Test public void aStopSecBelowZeroStopsTheWheelBeforeThePush() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, StopsEarly.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.19);  // stops at 0.25 - 0.1 = 0.15 s; the arm pushes at 0.25 s
        assertEquals("the wheel stopped", 0, d.crServo("feeder_wheel").power, 1e-9);
        assertEquals("the arm has not pushed yet", 0.1, d.servo("feeder_arm").position, 1e-9);
        runFor(r, fly, 0.12);
        assertEquals("then the arm pushes", 0.7, d.servo("feeder_arm").position, 1e-9);
    }

    @Test public void alwaysOnRunsWithTheFlywheelAndTheArmPushesAtOnce() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, AlwaysOn.class);
        PretendMotor fly = d.motor("launcher");
        assertEquals("on with the flywheel, before any shot", 1.0, d.crServo("feeder_wheel").power, 1e-9);
        r.launcher.fire();
        runFor(r, fly, 0.05);
        assertEquals("the arm pushes at once (no staging time)", 0.7, d.servo("feeder_arm").position, 1e-9);
        r.launcher.stop();
        r.all().update();
        assertEquals("off when the launcher stops", 0, d.crServo("feeder_wheel").power, 1e-9);
    }

    @Test public void aCancelledFireStopsTheWheelAndTheArmNeverPushes() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, WhileFiring.class);
        PretendMotor fly = d.motor("launcher");
        Action a = r.launcher.fireAction(1);
        r.run(a);
        runFor(r, fly, 0.05);
        assertEquals(0.8, d.crServo("feeder_wheel").power, 1e-9);
        r.cancel(a);
        runFor(r, fly, 0.3);
        assertEquals("the wheel stopped", 0, d.crServo("feeder_wheel").power, 1e-9);
        assertEquals("the arm never pushed", 0.1, d.servo("feeder_arm").position, 1e-9);
    }

    public static class WheelOnly extends RobotDefinition {
        @Override protected void define() {
            tunables(WhileFiring.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000").feedWheel(Indexer.crServo("feeder_wheel")));
        }
    }

    @Test public void aFeedWheelNeedsAFeederAndMustSpin() {
        try {
            new WheelOnly().setup(new PretendDevices());
            fail("a feed wheel with no arm");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("needs a feeder(...)"));
        }
        try {
            Launcher.flywheel("launcher", "gobilda-5203-6000").feedWheel(Indexer.servo("arm"));
            fail("a push servo is not a feed wheel");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("a feed wheel spins"));
        }
    }

    @Test public void theWheelStartsAtFireNotAtTheNextLoop() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, WhileFiring.class);
        r.launcher.fire();
        r.all().update();   // one loop: the feed begins, and the staging time counts from now
        assertEquals(0.8, d.crServo("feeder_wheel").power, 1e-9);
    }

    @Test public void theNextShotWaitsUntilTheWheelStops() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, LongStop.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.7);   // the arm pushed at 0.1 s and is back; the wheel runs until about 1.1 s
        assertEquals("the arm is back", 0.1, d.servo("feeder_arm").position, 1e-9);
        r.launcher.fire();
        runFor(r, fly, 0.15);
        assertEquals("a second push waits for the first wheel cycle", 0.1, d.servo("feeder_arm").position, 1e-9);
        assertEquals("the wheel still runs its stopSec", 1.0, d.crServo("feeder_wheel").power, 1e-9);
    }

    @Test public void aNewPowerFromTheTuningMenuReachesARunningWheel() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, AlwaysOn.class);
        com.crossbotics.ftchelper.tuning.Tunable.number("FeederWheel/power", 0, 1).set(0.5);
        runFor(r, d.motor("launcher"), 0.05);
        assertEquals(0.5, d.crServo("feeder_wheel").power, 1e-9);
    }

    @Test public void theWheelsOwnFeedAndStopDoNothing() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, AlwaysOn.class);
        r.launcher.feedWheel().stop();     // the launcher runs it: a student's call must not leave it stuck off
        r.launcher.feedWheel().feedOne();
        runFor(r, d.motor("launcher"), 0.6);
        assertEquals(1.0, d.crServo("feeder_wheel").power, 1e-9);
    }

    @Test public void nothingMovesInInit() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot(AlwaysOn.class);
        r.setup(d);                        // INIT only: no START (the INIT loop updates no part)
        assertEquals("the feed wheel stays still", 0, d.crServo("feeder_wheel").power, 1e-9);
        assertEquals("the arm gets no position", 0, d.servo("feeder_arm").writes);
        assertEquals("the flywheel stays still", 0, d.motor("launcher").power, 1e-9);
        assertEquals("no flywheel speed asked", 0, d.motor("launcher").velocityCommand, 1e-9);
    }

    @Test public void aPauseStopsTheWheelAndTheArmThenTheyGoOn() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, WhileFiring.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.08);              // staging: the wheel runs, the arm waits
        assertEquals(0.8, d.crServo("feeder_wheel").power, 1e-9);
        r.pauseAll();
        runFor(r, fly, 0.4);               // paused longer than stageSec
        assertEquals("paused: the wheel stops", 0, d.crServo("feeder_wheel").power, 1e-9);
        assertEquals("paused: the arm does not push", 0.1, d.servo("feeder_arm").position, 1e-9);
        r.resume();
        runFor(r, fly, 0.6);               // the shot that was waiting goes on: stage, then push
        assertTrue("after the pause the arm pushes", d.servo("feeder_arm").writes >= 2);
    }

    @Test public void aPauseStopsAnAlwaysOnWheelAndResumeStartsIt() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, AlwaysOn.class);
        assertEquals(1.0, d.crServo("feeder_wheel").power, 1e-9);
        r.pauseAll();
        runFor(r, d.motor("launcher"), 0.1);
        assertEquals("paused", 0, d.crServo("feeder_wheel").power, 1e-9);
        r.resume();
        runFor(r, d.motor("launcher"), 0.1);
        assertEquals("on again", 1.0, d.crServo("feeder_wheel").power, 1e-9);
    }

    @Test public void aStopWhilePausedDropsTheWaitingShot() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, WhileFiring.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.08);              // staging
        r.pauseAll();
        r.launcher.stop();                 // the driver stops the launcher while paused
        r.resume();
        runFor(r, fly, 0.6);
        assertEquals("the stopped shot never fires", 0.1, d.servo("feeder_arm").position, 1e-9);
        assertEquals("the arm never pushed (only its rest at START)", 1, d.servo("feeder_arm").writes);
        assertEquals(0, d.crServo("feeder_wheel").power, 1e-9);
    }

    @Test public void anIdleWhileStagingDropsThatShot() {
        PretendDevices d = new PretendDevices();
        Bot r = started(d, WhileFiring.class);
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire();
        runFor(r, fly, 0.08);              // staging: the arm has not pushed
        r.launcher.idle();                 // LB: the wheel slows down
        runFor(r, fly, 0.5);
        assertEquals("no push into a slowing wheel", 0.1, d.servo("feeder_arm").position, 1e-9);
        assertEquals(1, d.servo("feeder_arm").writes);
        assertEquals("the feed wheel stopped", 0, d.crServo("feeder_wheel").power, 1e-9);
    }
}
