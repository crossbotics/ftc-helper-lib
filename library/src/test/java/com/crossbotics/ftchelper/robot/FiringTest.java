package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.launcher.Launcher.ShotMode;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.match.MatchTimer;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.testkit.PretendMotor;
import com.crossbotics.ftchelper.tuning.TuningReset;
import org.junit.After;
import org.junit.Test;

/**
 * Batch 1, firing, on the robot in five files: the feed stop (hold at START only, open for a piece,
 * closed when the shot is seen), the intake borrowed to push a piece in, the shot mode chosen from the
 * time left, the alliance trim, and the "ready to launch" action.
 */
public class FiringTest {

    @After public void back() {
        SettingsFile.useForTests(null);
        TuningReset.clear();
        MatchTimer.forget();
    }

    public static class Numbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Indexer { public static double rest = 0.1, push = 0.6, pushSec = 0.8, returnSec = 0.2; }
        public static class Launcher {
            public static double speedRpm = 3000, feedStopHold = 0.1, feedStopRelease = 0.6, feedStopOpenSec = 0.3;
            public static double redTrimPercent = 2, blueTrimPercent = -1;
            public static boolean sprayEnabled = false;
            public static double sprayLastSeconds = 5;
        }
    }

    public static class Bot extends RobotDefinition {
        public Intake intake;
        public Launcher launcher;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feedStop("stop").pushedBy(intake));
        }
    }

    // One loop; the wheel is at its target speed (minus `dropRpm`, for a shot).
    private static void loop(Bot r, PretendMotor fly, double dropRpm) {
        fly.velocity = fly.velocityCommand - dropRpm / 60.0 * 28;
        r.all().update();
    }

    // Loops for this many real seconds with the wheel at speed.
    private static void runFor(Bot r, PretendMotor fly, double sec) throws InterruptedException {
        long end = System.nanoTime() + (long) (sec * 1e9);
        while (System.nanoTime() < end) {
            loop(r, fly, 0);
            Thread.sleep(5);
        }
    }

    @Test public void feedStopHoldsAtStartOpensForAPieceAndClosesOnTheShot() throws Exception {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        assertTrue("no feed stop move in INIT", Double.isNaN(d.servo("stop").position));
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        loop(r, fly, 0);
        assertEquals("hold at START", 0.1, d.servo("stop").position, 1e-9);
        r.launcher.spinUp();
        runFor(r, fly, 0.3);                                  // at speed, settled
        assertTrue(r.launcher.isReady());
        assertEquals("the intake is off", 0, d.motor("intake").power, 1e-9);
        r.launcher.fire();
        loop(r, fly, 0);
        loop(r, fly, 0);
        assertEquals("open for the piece", 0.6, d.servo("stop").position, 1e-9);
        assertTrue("the intake is borrowed to push it in", d.motor("intake").power > 0);
        assertTrue(r.intake.isPushingForShot());
        loop(r, fly, 400);                                    // the shot: the wheel loses 400 rpm
        loop(r, fly, 400);
        assertEquals("closed once the shot is seen", 0.1, d.servo("stop").position, 1e-9);
        loop(r, fly, 400);
        assertEquals("the intake goes back to off", 0, d.motor("intake").power, 1e-9);
        assertTrue(!r.intake.isRunning());
    }

    @Test public void feedStopClosesAfterItsBackupTimeWithoutAShot() throws Exception {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        runFor(r, fly, 0.3);
        r.intake.in();                                        // the driver runs the intake
        r.launcher.fire();
        loop(r, fly, 0);
        loop(r, fly, 0);
        assertEquals(0.6, d.servo("stop").position, 1e-9);
        runFor(r, fly, 0.4);                                  // no speed drop: the 0.3 s backup ends it
        assertEquals(0.1, d.servo("stop").position, 1e-9);
        assertTrue("the intake stays on, as the driver left it", d.motor("intake").power > 0);
    }

    public static class IndexerBot extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feedStop("stop")
                    .feeder(Indexer.servo("indexer").part("gobilda-2000-speed")));
        }
    }

    // Review (Codex): a cancelled firing action must not let the indexer push after the feed stop's delay.
    @Test public void aCancelledFireNeverPushesLate() throws Exception {
        PretendDevices d = new PretendDevices();
        IndexerBot r = new IndexerBot();
        r.setup(d);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        fly.velocity = fly.velocityCommand;
        long end = System.nanoTime() + 300_000_000L;
        while (System.nanoTime() < end) { fly.velocity = fly.velocityCommand; r.all().update(); Thread.sleep(5); }
        double rest = d.servo("indexer").position;
        Action a = r.launcher.fireAction(1);
        r.run(a);
        fly.velocity = fly.velocityCommand;
        r.all().update();                                     // the feed starts: the stop opens, the indexer waits
        assertEquals(0.6, d.servo("stop").position, 1e-9);
        r.cancel(a);
        end = System.nanoTime() + 300_000_000L;
        while (System.nanoTime() < end) { fly.velocity = fly.velocityCommand; r.all().update(); Thread.sleep(5); }
        assertEquals("the indexer never pushed", rest, d.servo("indexer").position, 1e-9);
        assertEquals("the stop closed", 0.1, d.servo("stop").position, 1e-9);
    }

    // Review (Codex): the shot closes the stop at once, even while a slow indexer is still out.
    @Test public void theShotClosesTheStopWhileTheIndexerIsStillOut() throws Exception {
        PretendDevices d = new PretendDevices();
        IndexerBot r = new IndexerBot();
        r.setup(d);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        long end = System.nanoTime() + 300_000_000L;
        while (System.nanoTime() < end) { fly.velocity = fly.velocityCommand; r.all().update(); Thread.sleep(5); }
        r.launcher.fire();
        end = System.nanoTime() + 200_000_000L;               // the stop opens, the indexer pushes (0.1 s later)
        while (System.nanoTime() < end) { fly.velocity = fly.velocityCommand; r.all().update(); Thread.sleep(5); }
        assertEquals(0.6, d.servo("indexer").position, 1e-9);
        for (int i = 0; i < 3; i++) { fly.velocity = fly.velocityCommand - 400 / 60.0 * 28; r.all().update(); }
        assertEquals("closed on the shot", 0.1, d.servo("stop").position, 1e-9);
        assertEquals("the indexer is still out (0.8 s push)", 0.6, d.servo("indexer").position, 1e-9);
        assertTrue("and the launcher waits for it before the next piece", r.launcher.part().getState()
                == com.crossbotics.ftchelper.launcher.Launcher.State.FEEDING);
    }

    @Test public void autonomousChoosesTheModeFromTheTimeLeft() {
        Fakes.Clock c = new Fakes.Clock();
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        r.startForTests();
        MatchTimer timer = new MatchTimer(MatchTimer.AUTO_SECONDS, c::get).reserve(4);
        timer.start();
        PretendMotor fly = d.motor("launcher");
        r.launcher.fire(4);
        loop(r, fly, 0);
        assertEquals("26 s before the park: time for CAREFUL", ShotMode.CAREFUL, r.launcher.shotModeNow());
        c.addSec(24.5);                                       // 1.5 s before the park: 4 CAREFUL shots do not fit
        loop(r, fly, 0);
        assertEquals(ShotMode.QUICK, r.launcher.shotModeNow());
        c.addSec(1.3);                                        // 0.2 s: not even QUICK fits, but SPRAY is off
        loop(r, fly, 0);
        assertEquals("SPRAY only when Tunables allows it", ShotMode.QUICK, r.launcher.shotModeNow());
        Numbers.Launcher.sprayEnabled = true;
        try {
            Bot r2 = new Bot();
            PretendDevices d2 = new PretendDevices();
            r2.setup(d2);
            r2.startForTests();
            timer.start();                                    // setup forgot it: start it again
            c.addSec(25.8);
            r2.launcher.fire(4);
            loop(r2, d2.motor("launcher"), 0);
            assertEquals(ShotMode.SPRAY, r2.launcher.shotModeNow());
            assertTrue(r2.launcher.sprayEnabled());
        } finally {
            Numbers.Launcher.sprayEnabled = false;
        }
    }

    @Test public void teleOpTapIsCarefulHoldIsQuickAndTheLastSecondsSpray() {
        Numbers.Launcher.sprayEnabled = true;
        try {
            Fakes.Clock c = new Fakes.Clock();
            PretendDevices d = new PretendDevices();
            Bot r = new Bot();
            r.setup(d);
            r.startForTests();
            new MatchTimer(MatchTimer.TELEOP_SECONDS, c::get).start();
            PretendMotor fly = d.motor("launcher");
            r.launcher.fire();
            loop(r, fly, 0);
            assertEquals(ShotMode.CAREFUL, r.launcher.shotModeNow());
            r.launcher.fireWhileHeld();
            loop(r, fly, 0);
            assertEquals(ShotMode.QUICK, r.launcher.shotModeNow());
            r.launcher.ceaseFire();
            c.addSec(116);                                    // 4 s left
            loop(r, fly, 0);
            assertEquals(ShotMode.SPRAY, r.launcher.shotModeNow());
            r.launcher.shotMode(ShotMode.CAREFUL);            // set in code: always wins
            loop(r, fly, 0);
            assertEquals(ShotMode.CAREFUL, r.launcher.shotModeNow());
        } finally {
            Numbers.Launcher.sprayEnabled = false;
        }
    }

    @Test public void allianceTrimChangesTheShotSpeed() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        r.startForTests();
        r.setAlliance(Alliance.RED);
        r.launcher.spinUp();
        loop(r, d.motor("launcher"), 0);
        assertEquals(3060, r.launcher.targetRpm(), 1e-6);    // +2%
        r.setAlliance(Alliance.BLUE);
        loop(r, d.motor("launcher"), 0);
        assertEquals(2970, r.launcher.targetRpm(), 1e-6);    // -1%
    }

    @Test public void readyActionIsDoneWhenTheWheelIsAtSpeed() throws Exception {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        r.startForTests();
        Action a = r.launcher.readyAction(3);
        r.run(a);
        PretendMotor fly = d.motor("launcher");
        loop(r, fly, 0);
        assertTrue(a.isRunning());
        assertTrue(r.launcher.isSpinning());
        runFor(r, fly, 0.3);
        assertTrue(!a.isRunning());
        assertEquals("done", a.result());
    }
}
