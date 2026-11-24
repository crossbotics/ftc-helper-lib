package com.crossbotics.ftchelper.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.launcher.ContinuousFire.End;
import com.crossbotics.ftchelper.launcher.ContinuousFire.State;
import com.crossbotics.ftchelper.mechanism.AngleDevice;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import org.junit.Test;

/** The group of continuous fire in launcher.Launcher: the strict start, every end, and no restart. */
public class ContinuousFireTest {
    private static final double E = 1e-6;
    private final Fakes.Clock c = new Fakes.Clock();
    private final FakeMotor fly = new FakeMotor(), feed = new FakeMotor();
    private Flywheel wheel;
    private MotorFeeder feeder;
    private Launcher launcher;
    private ContinuousFire group;
    private ShotTable table;
    private ShotSight sight = sight(true, 36, 0, true);

    private static ShotSight sight(boolean seen, double d, double error, boolean aimed) {
        return new ShotSight(seen, d, error, aimed, 1.5, 1, -4);
    }

    private void make(Flywheel.SpeedControl control) {
        wheel = new Flywheel("fly", fly.motor).withClock(c::get).countsPerRev(28).maxRpm(6000).tolerance(100).settleTime(0.1);
        assertTrue(wheel.speedControl(control));
        feeder = new MotorFeeder("feed", feed.motor).withClock(c::get).power(0.9).feedTime(0.25).jamDetection(100, 0.2);
        feed.velocity = 1000;
        table = new ShotTable().add(27, 2500).add(32, 2550).add(35, 2615).add(41, 2780).add(45, 2918).extendEnds(4800);
        launcher = new Launcher(wheel, feeder).withClock(c::get).table(table);
        launcher.setDistance(36);
        group = launcher.continuous();
    }

    private void make() {
        make(Flywheel.SpeedControl.PID);
    }

    // One 20 ms loop with the wheel at this share of its target.
    private void loop(double share) {
        c.addSec(0.02);
        fly.velocity = wheel.getTargetRpm() * share / 60 * 28;
        launcher.update();
    }

    private void run(double sec) {
        for (int i = 0; i < Math.round(sec / 0.02); i++) loop(1);
    }

    private void feeding() {
        assertTrue(group.start(() -> sight));
        run(0.3);
        assertEquals(State.FEEDING, group.getState());
        assertEquals(0.9, feed.power, E);
    }

    private void ended(End why) {
        assertEquals(State.IDLE, group.getState());
        assertEquals(why, group.getEnd());
        assertEquals("the feed stopped", 0, feed.power, 0);
        assertFalse(feeder.isBusy());
    }

    // Nothing starts again by itself, with everything good again.
    private void staysEnded() {
        sight = sight(true, 36, 0, true);
        run(1.5);
        assertFalse(group.isActive());
        assertEquals(0, feed.power, 0);
    }

    // ---- The start ----

    @Test public void theStartItselfNeverFeeds() {
        make();
        assertTrue(group.start(() -> sight));
        assertEquals(State.WAITING, group.getState());
        assertTrue(launcher.isShooting());
        assertEquals(0, feed.powerWrites);
    }

    @Test public void itWaitsForSteadyReady() {
        make();
        group.start(() -> sight);
        for (int i = 0; i < 5; i++) {
            loop(0.5);
            assertEquals(0, feed.power, 0);
        }
        assertTrue(group.whyNotReady(), group.whyNotReady().equals("wheel NOT at speed"));
        loop(1);
        assertEquals("one good reading is not steady", 0, feed.power, 0);
        run(0.2);
        assertTrue(group.isFeeding());
    }

    private void neverFeeds(ShotSight seen, String missing) {
        sight = seen;
        group.start(() -> sight);
        for (int i = 0; i < 160 && group.isActive(); i++) {
            loop(1);
            assertEquals(0, feed.power, 0);
        }
        assertEquals(End.READY_TIMEOUT, group.getEnd());
        assertTrue(group.getEndWords(), group.getEndWords().contains(missing));
        assertEquals(0, feed.powerWrites);
        assertTrue(launcher.getLastProblem(), launcher.getLastProblem().contains(missing));
        staysEnded();
    }

    @Test public void noGoalNoFeedAndTheTimeoutSaysSo() {
        make();
        neverFeeds(sight(false, 36, 0, true), "Did NOT fire: goal NOT seen");
    }

    @Test public void notAimedNeverFeedsNotEvenAfterATimeLimit() {
        make();
        neverFeeds(sight(true, 36, 1.6, false), "Did NOT fire: NOT aimed");
    }

    @Test public void noDistanceNeverFeeds() {
        make();
        neverFeeds(sight(true, Double.NaN, 0, true), "NO distance to the goal");
    }

    @Test public void aDistancePastTheLimitNeverFeeds() {
        make();
        launcher.setDistance(130);
        neverFeeds(sight(true, 130, 0, true), "too far from the goal");
    }

    @Test public void aFlatEndNeverFeedsPastIt() {
        make();
        launcher.table(new ShotTable().add(27, 2500).add(45, 2918));
        launcher.setDistance(50);
        neverFeeds(sight(true, 50, 0, true), "too far from the goal");
    }

    @Test public void aFixedSpeedHasNoDistanceSoNeverFeeds() {
        make();
        launcher.setRpm(2600);
        neverFeeds(sight(true, 36, 0, true), "NO speed for this distance");
    }

    @Test public void aSpeedFromAnotherDistanceNeverFeeds() {
        make();
        neverFeeds(sight(true, 43, 0, true), "distance still changing");
    }

    @Test public void everyOtherCheckCounts() {
        make();
        launcher.requireReady("gate closed", () -> false);
        neverFeeds(sight(true, 36, 0, true), "Still waiting for: gate closed");
    }

    @Test public void itWaitsForASingleThatStillFeeds() {
        make();
        launcher.spinUp();
        run(0.2);
        launcher.shoot(1);
        loop(1);
        assertTrue(feeder.isBusy());
        group.start(() -> sight);
        run(0.1);
        assertEquals(State.WAITING, group.getState());
        group.stop();
        assertTrue("letting go of a waiting group leaves the single alone", feeder.isBusy());
    }

    @Test public void startDropsSinglesStillWaiting() {
        make();
        launcher.shoot(3);
        group.start(() -> sight);
        assertEquals(0, launcher.getShotsLeft());
    }

    @Test public void aRefusedStartKeepsTheSinglesAlreadyAskedFor() {
        make();
        launcher = new Launcher(wheel, new Feeder() {
            @Override public void feedOne() { }
            @Override public boolean isBusy() { return false; }
            @Override public void update() { }
        }).withClock(c::get).table(table);
        launcher.setDistance(36);
        launcher.shoot(2);
        assertFalse(launcher.continuous().start(() -> sight));
        assertEquals(2, launcher.getShotsLeft());
    }

    @Test public void aFeederThatCannotIsRefusedWithAReason() {
        make();
        int[] singles = {0};
        Feeder plain = new Feeder() {
            @Override public void feedOne() { singles[0]++; }
            @Override public boolean isBusy() { return false; }
            @Override public void update() { }
        };
        launcher = new Launcher(wheel, plain).withClock(c::get).table(table);
        launcher.setDistance(36);
        group = launcher.continuous();
        assertFalse(group.canFire());
        assertFalse(group.start(() -> sight));
        assertEquals(End.NO_FEEDER, group.getEnd());
        assertTrue(launcher.getLastProblem().contains("feeder can't run nonstop"));
        run(1);
        assertEquals("never faked with single feeds", 0, singles[0]);
    }

    // ---- While it feeds ----

    @Test public void oneUninterruptedFeedThatIsNotCountedAsShots() {
        make();
        feeding();
        int writes = feed.powerWrites;
        for (int i = 0; i < 75; i++) {
            loop(i % 10 < 4 ? 0.85 : 1);                    // a dip per ball: never a READY wait
            assertEquals(0.9, feed.power, E);
        }
        assertEquals("never off and on again", writes, feed.powerWrites);
        assertEquals(0, launcher.getShotsFired());
        assertTrue(launcher.getShotStats().volley().isEmpty());
    }

    @Test public void theSnapshotDoesNotMove() {
        make();
        launcher.speedScale(1.02);
        feeding();
        ContinuousFire.Snapshot s = group.getSnapshot();
        assertEquals(36, s.distanceIn, 0);
        assertEquals(2642.5 * 1.02, s.targetRpm, E);
        assertEquals(-4, s.aimOffsetDeg, 0);
        assertEquals(Flywheel.SpeedControl.PID, s.control);
        assertTrue(s.table == table);
        launcher.setDistance(39);
        launcher.speedScale(1.1);
        group.boostRpm(500).maxSec(1).feedPower(0.2);       // for the next group
        run(1.2);
        assertTrue("still the old time limit", group.isFeeding());
        assertEquals(2642.5 * 1.02, wheel.getTargetRpm(), E);
        assertEquals(0.9, feed.power, E);
        group.stop();
        run(0.02);
        assertEquals("afterwards the new numbers", table.lookup(39).rpm * 1.1, wheel.getTargetRpm(), E);
    }

    @Test public void theHoodStaysPutWhileItFeeds() {
        make();
        FakeHood hood = new FakeHood();
        ShotTable withHood = new ShotTable().add(24, 2400, 5).add(48, 3000, 15);
        launcher = new Launcher(wheel, feeder).withClock(c::get).table(withHood)
                .hood(hood);
        group = launcher.continuous();
        launcher.setDistance(36);
        group.start(() -> sight);
        run(0.6);
        assertTrue(group.whyNotReady(), group.isFeeding());
        double at = launcher.getHood().getTargetDegrees();
        launcher.setDistance(39);
        assertEquals(at, launcher.getHood().getTargetDegrees(), 0);
    }

    private static final class FakeHood implements AngleDevice {
        private double at = Double.NaN;
        @Override public void setDegrees(double degrees) { at = degrees; }
        @Override public double getTargetDegrees() { return at; }
        @Override public boolean isDone() { return true; }
        @Override public void update() { }
        @Override public void stop() { }
    }

    @Test public void theBoostStartsWithTheFeedAndEndsWithTheGroup() {
        make();
        group.boostRpm(150);
        group.start(() -> sight);
        while (group.getState() == State.WAITING) {
            loop(1);
            assertEquals("never before the first READY", 2642.5, wheel.getTargetRpm(), E);
        }
        assertTrue(group.isFeeding());
        loop(1);
        assertEquals(2792.5, wheel.getTargetRpm(), E);
        group.stop();
        loop(1);
        assertEquals(2642.5, wheel.getTargetRpm(), E);
    }

    @Test public void theEstimatesLimitHoldsAfterTrimAndBoost() {
        make();
        launcher.setDistance(99);
        launcher.speedScale(1.05);
        group.boostRpm(300);
        sight = sight(true, 99, 0, true);
        feeding();
        run(0.1);
        assertEquals(4800, wheel.getTargetRpm(), E);
        assertEquals(4800, group.getSnapshot().feedRpm, E);
    }

    @Test public void aMeasuredShotsBoostStopsAtTheMotorsTop() {
        make();
        group.boostRpm(5000);
        feeding();
        assertEquals(6000, group.getSnapshot().feedRpm, E);
    }

    @Test public void theLastResortSlowerFeedPower() {
        make();
        group.feedPower(0.4);
        group.start(() -> sight);
        run(0.3);
        assertEquals(0.4, feed.power, E);
    }

    @Test public void feedSecIsTheCommandedTime() {
        make();
        feeding();
        double before = group.getFeedSec();
        run(0.5);
        assertEquals(before + 0.5, group.getFeedSec(), E);
        group.stop();
        double atEnd = group.getFeedSec();
        run(0.5);
        assertEquals(atEnd, group.getFeedSec(), 0);
    }

    // ---- Every end ----

    @Test public void releaseStopsInTheSameCall() {
        make();
        feeding();
        group.stop();
        ended(End.RELEASED);
        staysEnded();
    }

    @Test public void stopShootingStopsInTheSameCall() {
        make();
        feeding();
        launcher.stopShooting();
        ended(End.STOPPED);
        staysEnded();
    }

    @Test public void idleStopsInTheSameCall() {
        make();
        feeding();
        launcher.idle();
        ended(End.STOPPED);
        staysEnded();
    }

    @Test public void stopStopsTheFeederAndTheWheel() {
        make();
        feeding();
        launcher.stop();
        ended(End.STOPPED);
        assertEquals(0, fly.power, 0);
        staysEnded();
    }

    @Test public void everyNamedReasonStopsInTheSameCall() {
        for (End why : new End[] {End.PAUSED, End.HALTED, End.HELP, End.TUNING_MENU, End.RELIGHT, End.EJECT,
                End.INTAKE_REVERSE, End.ALLIANCE_CHANGED}) {
            make();
            feeding();
            group.stop(why);
            ended(why);
            staysEnded();
        }
    }

    @Test public void anotherTableEndsIt() {
        make();
        feeding();
        launcher.table(new ShotTable().add(20, 2000).add(60, 3000));
        ended(End.TABLE_CHANGED);
        staysEnded();
    }

    @Test public void theSameTableAgainDoesNot() {
        make();
        feeding();
        launcher.table(table);
        assertTrue(group.isFeeding());
    }

    @Test public void aRefusedControlSwitchEndsIt() {
        make();
        feeding();
        assertFalse(wheel.speedControl(Flywheel.SpeedControl.BANG_BANG));
        loop(1);
        ended(End.CONTROL_CHANGED);
        staysEnded();
    }

    @Test public void newControlNumbersEndIt() {
        make();
        feeding();
        wheel.pidf(50, 0, 0, 12);
        loop(1);
        ended(End.CONTROL_CHANGED);
    }

    @Test public void anotherFireCommandEndsIt() {
        make();
        feeding();
        launcher.shoot(1);
        ended(End.OTHER_FIRE);
        assertEquals(1, launcher.getShotsLeft());
    }

    @Test public void aJamEndsItInTheLoopItIsSeen() {
        make();
        feeding();
        feed.velocity = 0;
        for (int i = 0; i < 20 && group.isActive(); i++) loop(1);
        ended(End.JAM);
        staysEnded();
    }

    @Test public void somethingElseStoppingTheFeederEndsIt() {
        make();
        feeding();
        feeder.stop();
        loop(1);
        ended(End.FEEDER_STOPPED);
        staysEnded();
    }

    @Test public void anEncoderFaultEndsItInTheSameLoop() {
        make(Flywheel.SpeedControl.BANG_BANG);
        feeding();
        loop(Double.NaN);
        ended(End.ENCODER_FAULT);
        assertEquals(0, fly.power, 0);
        assertFalse("refused while faulted", group.start(() -> sight));
        assertEquals(End.ENCODER_FAULT, group.getEnd());
        launcher.shoot(1);
        assertEquals("no singles either", 0, launcher.getShotsLeft());
        run(1);
        assertEquals(0, feed.power, 0);
        assertTrue("asking again did not clear it", wheel.isFaulted());
    }

    @Test public void aFaultAlsoStopsASinglesFeed() {
        make(Flywheel.SpeedControl.BANG_BANG);
        launcher.spinUp();
        run(0.3);
        launcher.shoot(2);
        loop(1);
        assertTrue(feed.power > 0);
        loop(Double.NaN);
        assertEquals(0, feed.power, 0);
        assertEquals(0, launcher.getShotsLeft());
    }

    @Test public void aWheelUnderHalfEndsItInTheSameLoop() {
        make();
        feeding();
        loop(0.49);
        ended(End.WHEEL_SLOW);
        staysEnded();
    }

    @Test public void anUnreadableSpeedCountsAsTooSlow() {
        make();
        feeding();
        loop(Double.NaN);
        ended(End.WHEEL_SLOW);
    }

    @Test public void theGoalMayBeGoneHalfASecondAndNoLonger() {
        make();
        feeding();
        sight = sight(false, Double.NaN, Double.NaN, false);
        run(0.52);                                          // lost at the first loop: 0.5 s so far
        assertTrue(group.isFeeding());
        loop(1);
        ended(End.GOAL_LOST);
        staysEnded();
    }

    // The odometry backup's goal: seen, but not by the camera (a pose can drift).
    private static ShotSight fromOdometry(double d, double error, boolean aimed) {
        return new ShotSight(true, false, d, error, aimed, 1.5, 1, -4);
    }

    @Test public void theOdometryBackupNeverStartsAGroup() {
        make();
        neverFeeds(fromOdometry(36, 0, true), "Did NOT fire: goal NOT seen");
    }

    @Test public void theOdometryBackupDoesNotStopTheGoalLostClock() {
        make();
        feeding();
        sight = fromOdometry(36, 0, true);
        run(0.52);                                          // the camera lost it: 0.5 s so far
        assertTrue(group.isFeeding());
        loop(1);
        ended(End.GOAL_LOST);
        staysEnded();
    }

    @Test public void aShortlyLostGoalDoesNotEndIt() {
        make();
        feeding();
        for (int i = 0; i < 4; i++) {
            sight = sight(false, Double.NaN, Double.NaN, false);
            run(0.2);
            sight = sight(true, 36, 0, true);
            run(0.1);
        }
        assertTrue(group.isFeeding());
    }

    @Test public void theAimMayBeOutThreeTenthsAndNoLonger() {
        make();
        feeding();
        sight = sight(true, 36, 2.4, false);
        run(0.8);
        assertTrue("inside tolerance + margin", group.isFeeding());
        sight = sight(true, 36, 2.6, false);
        run(0.32);
        assertTrue(group.isFeeding());
        loop(1);
        ended(End.AIM_LOST);
        staysEnded();
    }

    @Test public void anUnknownAimErrorCountsAsOut() {
        make();
        feeding();
        sight = sight(true, 36, Double.NaN, false);
        run(0.4);
        ended(End.AIM_LOST);
    }

    @Test public void aDistanceMovedOverFourInchesEndsItInTheSameLoop() {
        make();
        feeding();
        sight = sight(true, 39.9, 0, true);
        loop(1);
        assertTrue(group.isFeeding());
        sight = sight(true, 40.1, 0, true);
        loop(1);
        ended(End.DISTANCE_MOVED);
        staysEnded();
    }

    @Test public void threeSecondsIsTheMost() {
        make();
        feeding();
        double left = ContinuousFire.DEFAULT_MAX_SEC - group.getFeedSec();
        run(left - 0.02);
        assertTrue(group.isFeeding());
        run(0.06);
        ended(End.MAX_TIME);
        assertEquals(3, group.getFeedSec(), 0.021);
        staysEnded();
    }

    @Test public void anErrorInAPartStopsTheFeed() {
        make();
        boolean[] broken = {false};
        group.start(() -> {
            if (broken[0]) throw new IllegalStateException("camera broke");
            return sight;
        });
        run(0.3);
        broken[0] = true;
        try {
            loop(1);
            fail("the error is shown");
        } catch (IllegalStateException e) {
            assertEquals("camera broke", e.getMessage());
        }
        ended(End.ERROR);
    }

    @Test public void theStopReachesTheWheelWhenTheFeederFails() {
        make();
        feeding();
        fly.power = 0.7;
        feed.failSetPower = true;
        try {
            launcher.stop();
            fail("the failure is shown");
        } catch (RuntimeException expected) {
            assertEquals("motor unplugged", expected.getMessage());
        }
        assertEquals(0, fly.power, 0);
        assertFalse(group.isActive());
        feed.failSetPower = false;
        launcher.stop();
        assertEquals("the next stop writes again", 0, feed.power, 0);
    }

    @Test public void aFreshStartWorksAfterAnEnd() {
        make();
        feeding();
        group.stop();
        assertEquals(End.RELEASED, group.getEnd());
        assertTrue(group.start(() -> sight));
        assertNull(group.getSnapshot());
        assertEquals(End.NONE, group.getEnd());
        run(0.3);
        assertTrue(group.isFeeding());
    }

    // ---- Plain words on a small screen ----

    @Test public void everyEndSaysWhatIsWrongAndWhatToDoInShortLines() {
        for (End why : End.values()) {
            if (why.isAsked()) continue;
            assertFalse(why + ": no thing to do", why.whatToDo().isEmpty());
            assertTrue(why + ": " + why.whatToDo(), why.whatToDo().length() <= 50);
            assertTrue(why + ": " + why.words(), ("Firing stopped: " + why.words()).length() <= 50);
        }
    }

    @Test public void theScreenSaysNotAimedAndWhatToDo() {
        make();
        sight = sight(true, 36, 5, false);
        group.start(() -> sight);
        run(0.3);
        String screen = screen();
        assertTrue(screen, screen.contains("Let go to cancel"));
        assertTrue(screen, screen.contains("Problem : NOT aimed"));
        assertTrue(screen, screen.contains("Do this : Let go of the turn stick."));
        assertFalse("never the name of passing", screen.contains("waits for"));
        assertFalse("no READY while not aimed", screen.contains("WAITING_READY"));
        run(3);
        screen = screen();
        assertTrue(screen, screen.contains("!! Did NOT fire: NOT aimed"));
        assertTrue(screen, screen.contains("!! Let go of the turn stick."));
        assertTrue(screen, screen.contains("!! Then press fire again."));
    }

    @Test public void theScreenSaysHowToStopAFeed() {
        make();
        feeding();
        assertTrue(screen().contains("feeder RUNNING. Let go to stop."));
    }

    private String screen() {
        RobotTelemetry t = new RobotTelemetry(new Fakes.FakeTelemetry().telemetry);
        launcher.addTelemetry(t);
        return t.pending().toString();
    }

    @Test public void noGroupLeavesTheLauncherAsItWas() {
        make();
        launcher.shoot(2);
        run(1.5);
        assertEquals(2, launcher.getShotsFired());
        assertEquals(0, feed.power, 0);
        assertEquals(State.IDLE, group.getState());
        assertEquals(End.NONE, group.getEnd());
        assertEquals("", launcher.getLastProblem());
    }
}
