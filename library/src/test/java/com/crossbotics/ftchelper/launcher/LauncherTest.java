package com.crossbotics.ftchelper.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import org.junit.Test;

public class LauncherTest {
    private long keepBefore;

    // These tests move the pretend turret motor with no time passing: read the encoder every time.
    @org.junit.Before public void freshReads() {
        keepBefore = Turret.READ_KEEP_NANOS;
        Turret.READ_KEEP_NANOS = 0;
    }

    // Review (Codex): homing uses a fresh encoder count, even inside the 3 ms reuse window.
    @Test public void turretHomesWithAFreshCount() {
        Turret.READ_KEEP_NANOS = 60_000_000_000L;
        FakeMotor m = new FakeMotor();
        boolean[] closed = {false};
        Turret t = new Turret("turret", m.motor).countsPerDegree(10).limits(-190, 190).homeSwitch(() -> closed[0], 0);
        m.position = 100;
        t.update();
        m.position = 101;
        closed[0] = true;
        t.update();
        Turret.READ_KEEP_NANOS = 0;
        assertEquals(0, t.getAngle(), 0.01);
    }

    @org.junit.After public void back() {
        Turret.READ_KEEP_NANOS = keepBefore;
    }
    private static final double E = 1e-9;

    @Test public void flywheelSendsTicksPerSecondAndNeedsToSettle() {
        FakeMotor m = new FakeMotor();
        Fakes.Clock c = new Fakes.Clock();
        Flywheel fw = new Flywheel("fly", m.motor).withClock(c::get).countsPerRev(28).tolerance(50).settleTime(0.1);
        fw.setRpm(3000);
        fw.update();
        assertEquals(3000 / 60.0 * 28, m.velocityCommand, E);    // 1400 ticks/s
        m.velocity = 1400;                                        // at speed
        fw.update();
        assertFalse("not steady long enough yet", fw.isReady());
        c.addSec(0.11); fw.update();
        assertTrue(fw.isReady());
        assertEquals(3000, fw.getRpm(), 1e-6);
        m.velocity = 2500 / 60.0 * 28;                            // a ball drops it to 2500 rpm
        c.addSec(0.02); fw.update();
        assertFalse(fw.isReady());
        assertEquals(1, fw.getShotCount());
        fw.stop();
        assertEquals("stop coasts: plain power 0", 0, m.power, E);
        assertEquals(com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_WITHOUT_ENCODER, m.mode);
    }

    @Test public void shotTableFillsInBetweenAndHoldsTheEnds() {
        ShotTable t = new ShotTable().add(48, 3100, 15).add(24, 2600, 8).add(72, 3700, 21);
        ShotTable.Shot s = t.lookup(36);
        assertEquals(2850, s.rpm, E);
        assertEquals(11.5, s.hoodDeg, E);
        assertEquals(2600, t.lookup(5).rpm, E);
        assertEquals(3700, t.lookup(200).rpm, E);
        assertTrue(t.covers(60));
        assertFalse(t.covers(80));
    }

    @Test public void turretPicksTheLegalWayRound() {
        FakeMotor m = new FakeMotor();
        Turret turret = new Turret("turret", m.motor).countsPerDegree(10).limits(-190, 190);
        // At 0: 170 is reachable directly.
        assertEquals(170, turret.legalTarget(170), E);
        // At 180: -175 is the same direction as 185, which is inside the limits and closer.
        m.position = 1800;
        assertEquals(185, turret.legalTarget(-175), E);
        // At 180: 200 is outside; its twin -160 is legal (the long way, without passing a limit).
        assertEquals(-160, turret.legalTarget(200), E);
        assertFalse(turret.isOutOfRange());
        // Limits narrower than a full turn: a direction outside them goes to the nearest limit.
        Turret narrow = new Turret("narrow", new FakeMotor().motor).limits(-90, 90);
        assertEquals(90, narrow.legalTarget(120), E);
        assertTrue(narrow.isOutOfRange());
    }

    @Test public void turretAimsAndStopsAtItsLimits() {
        FakeMotor m = new FakeMotor();
        Turret turret = new Turret("turret", m.motor).countsPerDegree(10).limits(-190, 190)
                .pid(0.03, 0, 0, 0).tolerance(1);
        turret.aimField(90, 30);                                    // field 90, robot heading 30 -> 60
        for (int i = 0; i < 300; i++) { turret.update(); m.position += m.power * 30; }
        assertTrue(turret.isAimed());
        assertEquals(60, turret.getAngle(), 1);
        turret.manual(1);
        for (int i = 0; i < 300; i++) { turret.update(); m.position += m.power * 30; }
        assertTrue(turret.getAngle() <= 190.5);                    // never past the limit
    }

    @Test public void turretHomesOnItsSwitch() {
        FakeMotor m = new FakeMotor();
        boolean[] sw = {false};
        Turret turret = new Turret("turret", m.motor).countsPerDegree(10).limits(-190, 190)
                .homeSwitch(() -> sw[0], 45).homingPower(0.2);
        assertFalse(turret.isHomed());
        turret.aimAt(10);
        turret.update();
        assertEquals("no aiming before homing", 0, m.power, E);
        turret.home(1);
        turret.update();
        assertEquals(0.2, m.power, E);
        m.position = 1234;                                          // somewhere; the switch closes here
        sw[0] = true;
        turret.update();
        assertTrue(turret.isHomed());
        assertEquals(45, turret.getAngle(), E);
    }

    @Test public void motorFeederRunsForItsTimeAndSeesJams() {
        FakeMotor m = new FakeMotor();
        Fakes.Clock c = new Fakes.Clock();
        MotorFeeder f = new MotorFeeder("feeder", m.motor).withClock(c::get).feedTime(0.25);
        f.feedOne();
        assertTrue(f.isBusy());
        assertEquals(1.0, m.power, E);
        c.addSec(0.26); f.update();
        assertFalse(f.isBusy());
        assertEquals(0, m.power, E);
        f.jamDetection(50, 0.1);
        f.feedOne();
        m.velocity = 0;
        f.update();
        c.addSec(0.11); f.update();
        assertTrue(f.isJammed());
        assertFalse(f.isBusy());
    }

    @Test public void servoFeederPushesThenReturns() {
        Fakes.FakeServo s = new Fakes.FakeServo();
        Fakes.Clock c = new Fakes.Clock();
        ServoFeeder f = new ServoFeeder("kicker", s.servo).withClock(c::get).positions(0.1, 0.6).times(0.1, 0.1);
        f.feedOne();
        assertEquals(0.6, s.position, E);
        c.addSec(0.11); f.update();
        assertEquals(0.1, s.position, E);
        assertTrue(f.isBusy());
        c.addSec(0.11); f.update();
        assertFalse(f.isBusy());
    }

    /** A launcher on pretend hardware: the flywheel reaches speed 0.3 s after spin-up. */
    @Test public void launcherWaitsForReadyThenFeedsEachPiece() {
        FakeMotor fm = new FakeMotor(), feedMotor = new FakeMotor();
        Fakes.Clock c = new Fakes.Clock();
        Flywheel fw = new Flywheel("fly", fm.motor).withClock(c::get).countsPerRev(28).tolerance(50).settleTime(0.05);
        MotorFeeder feeder = new MotorFeeder("feeder", feedMotor.motor).withClock(c::get).feedTime(0.1);
        boolean[] aimed = {false};
        Launcher l = new Launcher(fw, feeder).withClock(c::get).shotInterval(0.2).readyTimeout(2)
                .requireReady("Turret", () -> aimed[0]);
        l.setRpm(3000);
        l.shoot(3);
        double spunAt = 0;
        int feeds = 0;
        boolean wasFeeding = false;
        for (int i = 0; i < 200; i++) {
            c.addSec(0.02);
            if (fm.velocityCommand > 0 && c.nanos / 1e9 - spunAt > 0.3) fm.velocity = fm.velocityCommand;
            if (i == 30) aimed[0] = true;
            l.update();
            boolean feeding = feedMotor.power > 0;
            if (feeding && !wasFeeding) feeds++;
            wasFeeding = feeding;
            if (i == 20) assertEquals("Turret", l.whyNotReady());   // flywheel ready, turret not yet
        }
        assertEquals(3, feeds);
        assertEquals(3, l.getShotsFired());
        assertFalse(l.isShooting());
        assertEquals("", l.getLastProblem());
    }

    @Test public void launcherGivesUpAndSaysWhy() {
        FakeMotor fm = new FakeMotor(), feedMotor = new FakeMotor();
        Fakes.Clock c = new Fakes.Clock();
        Flywheel fw = new Flywheel("fly", fm.motor).withClock(c::get);
        Launcher l = new Launcher(fw, new MotorFeeder("feeder", feedMotor.motor).withClock(c::get))
                .withClock(c::get).readyTimeout(1);
        l.setRpm(3000);
        l.shoot(2);
        for (int i = 0; i < 100; i++) { c.addSec(0.02); l.update(); }   // flywheel never gets there
        assertFalse(l.isShooting());
        assertEquals("gave up: flywheel", l.getLastProblem());
        assertEquals(0, l.getShotsFired());
    }

    @Test public void launcherSetsHoodFromTheTable() {
        Fakes.FakeServo s = new Fakes.FakeServo();
        PositionServo hood = new PositionServo("hood", s.servo).degrees(0.1, 0, 0.9, 25);
        Launcher l = new Launcher(new Flywheel("fly", new FakeMotor().motor), new MotorFeeder("f", new FakeMotor().motor))
                .hood(hood).table(new ShotTable().add(24, 2600, 5).add(72, 3600, 20));
        l.setDistance(48);
        assertEquals(12.5, hood.getDegrees(), 1e-9);
    }

    // ---------------- review regressions ----------------

    @Test public void turretAlreadyOnTheSwitchHomesWithoutMoving() {
        FakeMotor m = new FakeMotor();
        Turret turret = new Turret("turret", m.motor).countsPerDegree(10).homeSwitch(() -> true, 45);
        turret.home(1);
        turret.update();
        assertTrue(turret.isHomed());
        assertFalse(turret.isHoming());
        assertEquals(45, turret.getAngle(), E);
    }

    @Test public void turretHomingGivesUpWithAFault() {
        FakeMotor m = new FakeMotor();
        Fakes.Clock c = new Fakes.Clock();
        Turret turret = new Turret("turret", m.motor).withClock(c::get).countsPerDegree(10)
                .homeSwitch(() -> false, 45).homingTimeout(2);
        turret.home(1);
        turret.update();
        assertEquals(0.2, m.power, E);
        c.addSec(2.1);
        turret.update();
        assertFalse(turret.isHoming());
        assertEquals(0, m.power, E);
        assertTrue(turret.getFault().contains("homing failed"));
    }

    @Test public void turretManualIsSlowBeforeHoming() {
        FakeMotor m = new FakeMotor();
        Turret turret = new Turret("turret", m.motor).countsPerDegree(10).homeSwitch(() -> false, 45).homingPower(0.2);
        turret.manual(1);
        turret.update();
        assertEquals(0.2, m.power, E);
    }

    @Test public void outOfRangeTargetIsNeverAimed() {
        FakeMotor m = new FakeMotor();
        Turret turret = new Turret("turret", m.motor).countsPerDegree(10).limits(-90, 90).tolerance(1);
        m.position = 900;                                     // at the +90 limit
        turret.aimAt(120);                                    // outside: goes to 90
        turret.update();
        assertTrue(turret.isOutOfRange());
        assertFalse("must not pass as a launcher ready check", turret.isAimed());
    }

    @Test public void turretUsesEachCameraPictureOnlyOnce() {
        FakeMotor m = new FakeMotor();
        Fakes.Clock c = new Fakes.Clock();
        c.nanos = 1_000_000_000L;
        Turret turret = new Turret("turret", m.motor).withClock(c::get).countsPerDegree(10).limits(-190, 190);
        com.crossbotics.ftchelper.vision.TargetObservation t =
                new com.crossbotics.ftchelper.vision.TargetObservation(20, "Tag 20", 10, 40, c.nanos);
        assertTrue(turret.aimAtTarget(t, true));
        assertEquals(10, turret.getTargetAngle(), E);
        m.position = 50;                                      // the turret moved 5 deg
        assertFalse("same picture again: ignored", turret.aimAtTarget(t, true));
        assertEquals(10, turret.getTargetAngle(), E);
        c.addSec(0.5);
        com.crossbotics.ftchelper.vision.TargetObservation old =
                new com.crossbotics.ftchelper.vision.TargetObservation(20, "Tag 20", 10, 40, c.nanos - 400_000_000L);
        assertFalse("old picture: ignored", turret.aimAtTarget(old, true));
        assertFalse(turret.aimAtTarget(null, true));
    }

    @Test public void launcherStopStaysOffEvenWithAnIdleSpeed() {
        FakeMotor fm = new FakeMotor();
        Fakes.Clock c = new Fakes.Clock();
        Launcher l = new Launcher(new Flywheel("fly", fm.motor).withClock(c::get), new MotorFeeder("f", new FakeMotor().motor))
                .withClock(c::get).idleRpm(1500);
        l.idle();
        l.update();
        assertTrue(fm.velocityCommand > 0);                   // idling
        l.stop();
        for (int i = 0; i < 5; i++) { c.addSec(0.02); l.update(); }
        assertEquals(Launcher.State.OFF, l.getState());
        assertEquals(0, fm.power, E);
        assertEquals(com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_WITHOUT_ENCODER, fm.mode);
    }

    @Test public void launcherStopReachesTheFlywheelEvenIfTheFeederThrows() {
        FakeMotor fm = new FakeMotor();
        Feeder broken = new Feeder() {
            public void feedOne() { }
            public boolean isBusy() { return false; }
            public void update() { }
            public void stop() { throw new RuntimeException("feeder broke"); }
        };
        Flywheel fw = new Flywheel("fly", fm.motor);
        fw.setRpm(3000);
        fw.update();
        Launcher l = new Launcher(fw, broken);
        try {
            l.stop();
            org.junit.Assert.fail("the error should come through");
        } catch (RuntimeException expected) {
            assertEquals(0, fm.power, E);                     // the flywheel was still stopped
        }
    }

    @Test public void shootingAgainClearsAFeederJam() {
        FakeMotor fm = new FakeMotor(), feedMotor = new FakeMotor();
        Fakes.Clock c = new Fakes.Clock();
        Flywheel fw = new Flywheel("fly", fm.motor).withClock(c::get).tolerance(50).settleTime(0.02);
        MotorFeeder feeder = new MotorFeeder("feeder", feedMotor.motor).withClock(c::get).feedTime(0.3).jamDetection(50, 0.1);
        Launcher l = new Launcher(fw, feeder).withClock(c::get).shotInterval(0.1);
        l.setRpm(3000);
        l.shoot(1);
        for (int i = 0; i < 20; i++) { c.addSec(0.02); fm.velocity = fm.velocityCommand; l.update(); }   // feeds, jams
        assertTrue(feeder.isJammed());
        assertEquals("feeder jammed", l.getLastProblem());
        feedMotor.velocity = 500;                                  // the driver cleared it
        l.shoot(1);
        assertFalse(feeder.isJammed());
        for (int i = 0; i < 20; i++) { c.addSec(0.02); fm.velocity = fm.velocityCommand; l.update(); }
        assertEquals(2, l.getShotsFired());
    }

    @Test public void hoodCommandedEveryLoopStillWaitsForTravel() {
        Fakes.FakeServo s = new Fakes.FakeServo();
        PositionServo hood = new PositionServo("hood", s.servo).degrees(0.1, 0, 0.9, 25).travelTime(1.0);
        // Through the launcher: setDistance every loop must not mark the hood done early.
        hood.setDegrees(0);
        hood.update();
        Launcher l = new Launcher(new Flywheel("fly", new FakeMotor().motor), new MotorFeeder("f", new FakeMotor().motor))
                .hood(hood).table(new ShotTable().add(24, 2600, 0).add(72, 3600, 25));
        l.setDistance(72);                                   // a big hood move: 0 -> 25 deg
        l.setDistance(72);                                   // the same command again (every loop)
        assertFalse("still travelling", hood.isDone());
    }

    @Test public void followerWithoutAnEncoderIsSwitchedOff() {
        FakeMotor lead = new FakeMotor(), follow = new FakeMotor();
        Fakes.Clock c = new Fakes.Clock();
        Flywheel fw = new Flywheel("fly", lead.motor).withClock(c::get);
        fw.attachFollowerForTests(follow.motor);
        fw.setRpm(3000);
        for (int i = 0; i < 40; i++) {
            c.addSec(0.02);
            lead.velocity = lead.velocityCommand;            // the leader spins
            follow.velocity = 0;                             // the follower's encoder reads nothing
            fw.update();
        }
        assertTrue(fw.getFault().contains("follower"));
        assertEquals(0, follow.power, E);
    }

    @Test public void followerGetsTheGainsInEitherOrder() {
        FakeMotor lead = new FakeMotor(), follow = new FakeMotor();
        Flywheel fw = new Flywheel("fly", lead.motor).pidf(10, 3, 0, 12);   // gains BEFORE the follower
        fw.attachFollowerForTests(follow.motor);
        assertEquals(12, follow.pidf[3], E);
    }

    @Test public void shootingWithNoSpeedSaysWhy() {
        Launcher l = new Launcher(new Flywheel("fly", new FakeMotor().motor), new MotorFeeder("f", new FakeMotor().motor));
        l.shoot(1);
        assertTrue(l.getLastProblem().startsWith("no shot speed"));
        assertFalse(l.isShooting());
    }

    @Test public void turretCameraOffsetShiftsTheAim() {
        FakeMotor m = new FakeMotor();
        long[] now = {1_000_000_000L};
        Turret turret = new Turret("turret", m.motor).withClock(() -> now[0]).countsPerDegree(10)
                .limits(-190, 190).cameraOffset(4);
        // Camera on the body: the target at bearing 10, offset 4 -> the turret goes to 6.
        assertTrue(turret.aimAtTarget(new com.crossbotics.ftchelper.vision.TargetObservation(1, "t", 10, 40, now[0]), false));
        assertEquals(6, turret.getTargetAngle(), E);
    }
}
