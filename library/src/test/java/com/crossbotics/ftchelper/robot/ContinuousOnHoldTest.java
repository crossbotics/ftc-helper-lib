package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.launcher.ContinuousFire.End;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.launcher.Launcher.ShotMode;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.match.MatchTimer;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.testkit.PretendClock;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.testkit.PretendMotor;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.tuning.TuningReset;
import com.crossbotics.ftchelper.vision.LightingSweep;
import com.crossbotics.ftchelper.vision.TargetObservation;
import com.crossbotics.ftchelper.Fakes;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tunables continuousOnHold on a whole robot: the press, the strict start, every end from the robot's
 * side, the aim, and, with the option off, the fire button exactly as before.
 */
public class ContinuousOnHoldTest {
    private static final double E = 1e-6, DT = 0.02;

    private final PretendClock clock = PretendClock.use();
    private LongSupplier aimClock, padClock;

    @Before public void setUp() {
        TuningReset.clear();
        aimClock = Aim.clock;
        padClock = Pad.clock;
        Aim.clock = clock;
        Pad.clock = clock;
        clock.addSec(5);
    }

    @After public void back() {
        Aim.clock = aimClock;
        Pad.clock = padClock;
        PretendClock.stopUsing();
        SettingsFile.useForTests(null);
        TuningReset.clear();
        MatchTimer.forget();
    }

    // ---- The numbers ----

    public static class On {
        public static class Drive { public static boolean calibrated = true; }
        public static class Aim {
            public static boolean aimOnFire = true;
            public static double aimOnFireSec = 0.5, toleranceDeg = 1.5, cameraOffset = 0;
        }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; public static boolean stopOnShot = true; }
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {32, 2550}, {35, 2615}, {41, 2780}, {45, 2918}};
            public static double[][] speedByDistanceOther = {{20, 2000}, {60, 3000}};
            public static double readyBandRpm = 100, distanceMedianOf = 1;
            public static boolean sprayEnabled = true, continuousOnHold = true;
        }
    }

    public static class Off {
        public static class Drive { public static boolean calibrated = true; }
        public static class Aim {
            public static boolean aimOnFire = true;
            public static double aimOnFireSec = 0.5, toleranceDeg = 1.5, cameraOffset = 0;
        }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; public static boolean stopOnShot = true; }
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {32, 2550}, {35, 2615}, {41, 2780}, {45, 2918}};
            public static double readyBandRpm = 100, distanceMedianOf = 1;
            public static boolean sprayEnabled = true;
        }
    }

    public static class NoAimOnFire {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static double readyBandRpm = 100, distanceMedianOf = 1;
            public static boolean continuousOnHold = true;
        }
    }

    public static class Ends {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {32, 2550}, {35, 2615}, {41, 2780}, {45, 2918}};
            public static double readyBandRpm = 100, distanceMedianOf = 1, extendMaxRpm = 4800;
            public static boolean extendTableEnds = true, continuousOnHold = true;
        }
    }

    public static class Locked {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {32, 2550}, {35, 2615}, {41, 2780}, {45, 2918}};
            public static double readyBandRpm = 100, distanceMedianOf = 1;
            public static boolean continuousOnHold = true, lockSpeedWhileFiring = true;
        }
    }

    public static class EndsDefaultLimit {
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static boolean extendTableEnds = true;
        }
    }

    public static class EndsTwoRowsAlike {
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {35, 2600}, {35, 2650}};
            public static boolean extendTableEnds = true;
        }
    }

    public static class EndsFalling {
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {35, 2700}, {45, 2600}};
            public static boolean extendTableEnds = true;
        }
    }

    public static class EndsBadLimit {
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static double extendMaxRpm = 9000;
            public static boolean extendTableEnds = true;
        }
    }

    public static class Bang {
        public static class Drive { public static boolean calibrated = true; }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; }
        public static class Launcher {
            public static String speedControl = "BANG_BANG";
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static double readyBandRpm = 100, distanceMedianOf = 1, bangKv = 0.0022, ejectRpm = 600;
            public static boolean continuousOnHold = true;
        }
    }

    public static class BangUntuned {
        public static class Launcher {
            public static String speedControl = "BANG_BANG";
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
        }
    }

    public static class BangFromKf {
        public static class Launcher {
            public static String speedControl = "BANG_BANG";
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static double readyBandRpm = 100, distanceMedianOf = 1;
            public static double kP = 10, kI = 0, kD = 0, kF = 12.08, nominalVolts = 13.0;
        }
    }

    public static class BangFromKfNoVolts {
        public static class Launcher {
            public static String speedControl = "BANG_BANG";
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static double kF = 12.08;
        }
    }

    public static class BangKvAndKf {
        public static class Launcher {
            public static String speedControl = "BANG_BANG";
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static double kF = 12.08, nominalVolts = 13.0, bangKv = 0.0021;
        }
    }

    public static class BangKfZero {
        public static class Launcher {
            public static String speedControl = "BANG_BANG";
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static double kF = 0;
        }
    }

    public static class PidWithKf {
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static double kP = 10, kI = 0, kD = 0, kF = 12.08, nominalVolts = 13.0;
        }
    }

    public static class PidWritten {
        public static class Launcher {
            public static String speedControl = "PID";
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
        }
    }

    public static class WrongWord {
        public static class Launcher {
            public static String speedControl = "bang";
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
        }
    }

    public static class NotText {
        public static class Launcher {
            public static boolean speedControl = true;
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
        }
    }

    public static class NoText {
        public static class Launcher {
            public static String speedControl = null;
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
        }
    }

    public static class BadHysteresis {
        public static class Launcher {
            public static double bangFullBelowRpm = 100, bangHysteresisRpm = 100;
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
        }
    }

    public static class BadMaxSec {
        public static class Launcher {
            public static double continuousMaxSec = 12;
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
        }
    }

    public static class BackwardPower {
        public static class Launcher {
            public static double continuousFeedPower = -0.5;
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
        }
    }

    // ---- The robot ----

    enum Feed { CR_SERVOS, MOTOR, PUSH_SERVO, FEED_STOP, NO_CAMERA }

    public static class Bot extends RobotDefinition {
        private final Class<?> numbers;
        private final Feed feed;
        public Camera camera;
        public Intake intake;
        public Indexer feeder;
        public com.crossbotics.ftchelper.robot.Launcher launcher;

        Bot(Class<?> numbers, Feed feed) {
            this.numbers = numbers;
            this.feed = feed;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(com.crossbotics.ftchelper.robot.Drive.tank("left", "right"));
            intake = add(Intake.motor("intake"));
            camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
            feeder = feed == Feed.MOTOR ? Indexer.motor("feeder")
                    : feed == Feed.PUSH_SERVO ? Indexer.servo("feeder")
                    : Indexer.crServo("feeder_left").alsoServoReversed("feeder_right");
            feeder.tunables("Feeder");
            com.crossbotics.ftchelper.robot.Launcher l =
                    com.crossbotics.ftchelper.robot.Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(feeder);
            if (feed != Feed.NO_CAMERA) l.speedFromDistance(camera);
            if (feed == Feed.FEED_STOP) {
                l.feedStop("stop").feedWheel(Indexer.crServo("stage")).pushedBy(intake);
            } else if (feed != Feed.PUSH_SERVO) {
                l.feedWheelOutWith(intake);
            }
            launcher = add(l);
        }
    }

    private Bot r;
    private PretendDevices d;
    private PretendMotor fly;
    private boolean button, seen;
    private double bearing, distance, wheelShare;
    private boolean menu, help;

    private void make(Class<?> numbers, Feed feed) {
        d = new PretendDevices();
        r = new Bot(numbers, feed);
        r.setup(d);
        AimTest.FakeCamera cam = new AimTest.FakeCamera();
        r.camera.source = new PretendEye(cam);
        fly = d.motor("launcher");
        r.setAlliance(Alliance.BLUE);
        r.startForTests();
        button = false;
        seen = true;
        bearing = 0;
        distance = 36;
        wheelShare = 1;
        menu = help = false;
        run(0.2);
    }

    private void make() {
        make(On.class, Feed.CR_SERVOS);
    }

    // A camera that sees the goal where the test puts it, with a fresh picture each loop.
    private final class PretendEye implements com.crossbotics.ftchelper.vision.TargetSource {
        private final AimTest.FakeCamera base;

        PretendEye(AimTest.FakeCamera base) {
            this.base = base;
        }

        @Override public void start() { }
        @Override public void update() {
            base.seen = !seen ? null : new TargetObservation(TargetObservation.CLUSTER,
                    r.alliance() == Alliance.RED ? "RED SCORING" : "BLUE SCORING", bearing, distance, clock.getAsLong(), 0);
        }
        @Override public List<TargetObservation> targets() {
            update();
            return base.targets();
        }
        @Override public boolean isWorking() { return true; }
        @Override public String status() { return "ok"; }
        @Override public void close() { }
        @Override public String name() { return "eye"; }
        @Override public void addTelemetry(RobotTelemetry t) { }
    }

    private boolean wasDown;

    // One loop, in TeleOp's order: the screens, the buttons, then the robot.
    private void loop() {
        clock.addSec(DT);
        r.screensShown(menu, help);
        if (!r.isPaused()) {
            if (button) r.launcher.fireWhileHeld();
        }
        if (wasDown && !button) r.launcher.ceaseFire();
        wasDown = button;
        fly.velocity = r.launcher.targetRpm() * wheelShare / 60 * 28;
        if (!r.isPaused()) r.all().update();
    }

    private void run(double sec) {
        for (int i = 0; i < Math.round(sec / DT); i++) loop();
    }

    private void press() {
        button = true;
        loop();
    }

    private void release() {
        button = false;
        loop();
    }

    private double left() {
        return d.crServo("feeder_left").power;
    }

    private double right() {
        return d.crServo("feeder_right").power;
    }

    private void feeding() {
        press();
        run(0.8);
        assertTrue(r.launcher.part().continuous().getEndWords() + " " + r.launcher.part().continuous().whyNotReady(),
                r.launcher.part().continuous().isFeeding());
        assertEquals(0.9, left(), E);
        assertEquals(0.9, right(), E);
    }

    private void ended(End why) {
        assertFalse(r.launcher.isFiringContinuously());
        assertEquals(why, r.launcher.part().continuous().getEnd());
        assertTrue("no forward feed: " + left(), left() <= 0);
        assertTrue("no forward feed: " + right(), right() <= 0);
    }

    // Still held, everything good again: nothing starts. A fresh press works.
    private void staysEndedUntilAFreshPress() {
        for (int i = 0; i < 75; i++) {
            loop();
            assertTrue("loop " + i + ": it restarted", left() <= 0);
            assertFalse(r.launcher.isFiringContinuously());
        }
        release();
        for (int i = 0; i < 10; i++) {
            loop();
            assertTrue("the release fired", left() <= 0);
        }
        assertEquals(0, r.launcher.part().getShotsLeft());
        run(0.5);
        feeding();
    }

    private String screen() {
        RobotTelemetry t = new RobotTelemetry(new Fakes.FakeTelemetry().telemetry);
        r.launcher.addTelemetry(t);
        r.aim().addTelemetry(t);
        return t.pending().toString();
    }

    // ---- The press ----

    @Test public void thePressSpinsUpAndAimsAtOnceAndFeedsNothingUntilKnown() {
        make();
        r.launcher.idle();
        run(0.1);
        bearing = 10;
        assertEquals("no aim before the press", 0, r.aim().assistTurn(0), 0);
        press();
        assertTrue(r.launcher.isSpinning());
        assertTrue("turns left toward it", r.aim().assistTurn(0) < 0);
        bearing = 0;
        for (int i = 0; i < 12; i++) {
            loop();
            assertEquals("fed before the press was known", 0, left(), 0);
            assertEquals(0, r.launcher.part().getShotsLeft());
        }
    }

    @Test public void aimsAtOnceWithoutAimOnFireToo() {
        make(NoAimOnFire.class, Feed.CR_SERVOS);
        bearing = 10;
        press();
        assertTrue(r.aim().assistTurn(0) < 0);
    }

    @Test public void aTapIsOneSingleAndNeverStreams() {
        make();
        press();
        run(0.1);
        release();
        double longest = 0, running = 0;
        for (int i = 0; i < 100; i++) {
            loop();
            assertFalse(r.launcher.isFiringContinuously());
            running = left() > 0 ? running + DT : 0;
            longest = Math.max(longest, running);
        }
        assertEquals(1, r.launcher.part().getShotsFired());
        assertTrue("one timed feed: " + longest, longest > 0 && longest <= 0.4 + DT + E);
    }

    // Let go a moment after holdSec, before the loop that makes the press a group ran: still a tap, one shot.
    @Test public void aReleaseJustAfterHoldSecBeforeTheGroupLoopIsATap() {
        make();
        press();
        clock.addSec(0.6);          // no loop while held: the press is still undecided at the release
        release();
        run(2);
        assertFalse(r.launcher.isFiringContinuously());
        assertEquals("one shot, as a tap", 1, r.launcher.part().getShotsFired());
    }

    // Bang-bang: a pause lets the wheel coast (FLOAT); a brake would stop a fast, heavy wheel hard. PID: as before.
    @Test public void aPauseLetsABangBangWheelCoastAndStillBrakesAPidOne() {
        make(Bang.class, Feed.CR_SERVOS);
        press();
        run(0.5);
        r.pauseAll();
        assertEquals(0, fly.power, 0);
        assertEquals(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.FLOAT, fly.zeroPower);
        r.resume();
        make();
        press();
        run(0.5);
        r.pauseAll();
        assertEquals(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE, fly.zeroPower);
    }

    // Tunables ballDropRpm: only the tuners read it; the shot count keeps shotDropRpm.
    public static class BallDrop {
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static double ballDropRpm = 230;
        }
    }

    @Test public void ballDropRpmIsForTheTunersOnly() {
        make(BallDrop.class, Feed.CR_SERVOS);
        assertEquals(230, r.launcher.ballDropRpm(), 0);
        assertEquals("the shot count keeps its own drop", 150, r.launcher.part().getFlywheel().getShotDrop(), 0);
        make(On.class, Feed.CR_SERVOS);
        assertTrue("not written: NaN", Double.isNaN(r.launcher.ballDropRpm()));
    }

    // A tuner sets the robot up but does not start it. Its START for the feed (after a person's press) lets the
    // feed stop open for a feed and an always-on feed wheel run; without it neither ever moves.
    public static class StopAndStage {
        public static class Drive { public static boolean calibrated = true; }
        public static class Feeder { public static double power = 0.9, feedSec = 0.4; }
        public static class Stage { public static boolean alwaysOn = true; public static double power = 1; }
        public static class Launcher {
            public static double[][] speedByDistance = {{27, 2500}, {45, 2918}};
            public static double readyBandRpm = 100, distanceMedianOf = 1, feedStopHold = 0.2, feedStopRelease = 0.7;
        }
    }

    @Test public void aTunersFeedStartOpensTheFeedStopAndRunsTheStagingWheel() {
        for (boolean started : new boolean[] {false, true}) {
            d = new PretendDevices();
            r = new Bot(StopAndStage.class, Feed.FEED_STOP);
            r.setup(d);
            r.camera.source = new PretendEye(new AimTest.FakeCamera());
            r.setAlliance(Alliance.BLUE);
            seen = true;
            distance = 36;
            fly = d.motor("launcher");
            // Like the tuners: set up, NOT started. After A: the feed's start, then the robot's own spin-up.
            if (started) r.launcher.startFeedForTuner();
            r.launcher.spinUp();
            for (int i = 0; i < 25; i++) {
                clock.addSec(DT);
                fly.velocity = r.launcher.targetRpm() / 60 * 28;
                r.launcher.update();
            }
            double stage = d.crServo("stage").power;
            r.launcher.part().shoot(1);
            for (int i = 0; i < 5; i++) {
                clock.addSec(DT);
                fly.velocity = r.launcher.targetRpm() / 60 * 28;
                r.launcher.update();
            }
            double stop = d.servo("stop").position;
            if (started) {
                assertTrue("the always-on feed wheel runs with the wheel spun up: " + stage, stage > 0);
                assertEquals("the feed stop opens for the feed", 0.7, stop, E);
            } else {
                assertEquals("never started: the feed wheel stays off", 0, stage, 0);
                assertFalse("never started: the stop was never told to open: " + stop, Math.abs(stop - 0.7) <= E);
            }
        }
    }

    @Test public void aTapKeepsTodaysAimRule() {
        make();
        bearing = 10;
        press();
        release();
        run(0.4);
        assertEquals("waits for the turn", 0, r.launcher.part().getShotsFired());
        run(0.3);
        assertEquals("then fires anyway, as today", 1, r.launcher.part().getShotsFired());
    }

    @Test public void fourQuickTapsWhileSpinningUpAreFourShots() {
        make();
        wheelShare = 0.2;
        for (int i = 0; i < 4; i++) {
            press();
            run(0.04);
            release();
            run(0.06);
        }
        assertEquals(4, r.launcher.part().getShotsLeft());
        assertFalse(r.launcher.isFiringContinuously());
    }

    @Test public void aHoldIsOneUninterruptedFeed() {
        make();
        feeding();
        int writes = d.crServo("feeder_left").writes;
        for (int i = 0; i < 100; i++) {
            wheelShare = i % 12 < 4 ? 0.75 : 1;             // a dip per ball, with stopOnShot on
            loop();
            assertEquals("a dip never ends it", 0.9, left(), E);
        }
        assertEquals(writes, d.crServo("feeder_left").writes);
        assertEquals("not counted as shots", 0, r.launcher.part().getShotsFired());
    }

    @Test public void aGroupIsNotCountedAsFeeds() {
        make();
        int[] counted = {0};
        r.feeder.onFeed(() -> counted[0]++);
        feeding();
        run(1);
        release();
        assertEquals(0, counted[0]);
    }

    @Test public void noFeedBeforeTheAimIsStrictlyIn() {
        make();
        bearing = 1.6;
        press();
        for (int i = 0; i < 100; i++) {                     // far past aimOnFireSec
            loop();
            assertEquals(0, left(), 0);
        }
        assertEquals("NOT aimed", r.launcher.part().continuous().whyNotReady());
        bearing = 1.4;
        loop();
        loop();
        assertTrue(r.launcher.part().continuous().isFeeding());
    }

    @Test public void aimModeOnDoesNotBypassTheStrictAim() {
        make();
        r.aimMode().on();
        bearing = 2.2;                                      // inside the old lock's margin
        press();
        for (int i = 0; i < 100; i++) {
            loop();
            assertEquals(0, left(), 0);
        }
    }

    @Test public void noFeedBeforeSteadyReady() {
        make();
        wheelShare = 0.8;
        press();
        for (int i = 0; i < 100; i++) {
            loop();
            assertEquals(0, left(), 0);
        }
        assertEquals("wheel NOT at speed", r.launcher.part().continuous().whyNotReady());
        wheelShare = 1;
        loop();
        assertEquals("one good reading is not steady", 0, left(), 0);
    }

    @Test public void theTimeoutSaysWhatIsWrongAndNeedsAFreshPress() {
        make();
        bearing = 10;
        press();
        run(3.6);
        ended(End.READY_TIMEOUT);
        String s = screen();
        assertTrue(s, s.contains("Did NOT fire: NOT aimed"));
        assertTrue(s, s.contains("Let go of the turn stick."));
        bearing = 0;
        staysEndedUntilAFreshPress();
    }

    @Test public void noGoalIsNoGroup() {
        make();
        seen = false;
        run(0.7);
        press();
        run(1);
        assertEquals("goal NOT seen", r.launcher.part().continuous().whyNotReady());
        assertEquals(0, left(), 0);
    }

    @Test public void aWaitingGroupSaysHowToStopFirstAndNeverReadyWhileNotAimed() {
        make();
        bearing = 10;
        press();
        run(0.6);
        RobotTelemetry t = new RobotTelemetry(new Fakes.FakeTelemetry().telemetry);
        r.launcher.addTelemetry(t);
        List<String> lines = t.pending();
        assertEquals("Fire : NOT yet. Let go to cancel.", lines.get(0));
        for (String line : lines) assertFalse(line, line.startsWith("Speed trim") && line.contains("READY"));
        assertEquals("Problem : NOT aimed", lines.get(1));
        assertEquals("Do this : Let go of the turn stick.", lines.get(2));
    }

    @Test public void tapThenHoldStillSprays() {
        make();
        press();
        run(0.06);
        release();
        run(0.06);
        press();
        assertTrue("the second press fires at once", r.launcher.part().isShooting());
        run(0.4);
        assertTrue(r.launcher.isSprayingOnHold());
        assertEquals(ShotMode.SPRAY, r.launcher.shotModeNow());
        assertFalse(r.launcher.isFiringContinuously());
    }

    @Test public void aLongHoldIsNotTheTapOfASpray() {
        make();
        feeding();
        release();
        press();
        run(0.4);
        assertFalse(r.launcher.isSprayingOnHold());
        assertTrue(r.launcher.isFiringContinuously());
    }

    @Test public void aPressInTheLastSecondsSpraysAsToday() {
        make();
        MatchTimer timer = new MatchTimer(120, clock);
        timer.start();
        clock.addSec(116);
        press();
        run(0.4);
        assertFalse(r.launcher.isFiringContinuously());
        assertEquals(ShotMode.SPRAY, r.launcher.shotModeNow());
    }

    @Test public void aGroupKeepsItsModeIntoTheLastSeconds() {
        make();
        MatchTimer timer = new MatchTimer(120, clock);
        timer.start();
        clock.addSec(113.6);
        feeding();
        run(1.2);
        assertTrue("now under five seconds", timer.secondsLeft() < 5);
        assertTrue(r.launcher.part().continuous().isFeeding());
        assertEquals(ShotMode.CAREFUL, r.launcher.shotModeNow());
        assertFalse("never SPRAY to the aim", r.launcher.spraysNow());
    }

    @Test public void aPressJustBeforeTheLastSecondsIsStillAGroup() {
        make();
        MatchTimer timer = new MatchTimer(120, clock);
        timer.start();
        clock.addSec(114.8);
        press();
        assertTrue(timer.secondsLeft() > 5);
        run(0.4);
        assertTrue(timer.secondsLeft() < 5);
        assertTrue(r.launcher.isFiringContinuously());
        assertFalse(r.launcher.spraysNow());
    }

    @Test public void anAutonomousFireActionStaysOneAtATime() {
        make();
        r.run(r.launcher.fireAction(2));
        for (int i = 0; i < 100; i++) {
            loop();
            assertFalse(r.launcher.isFiringContinuously());
        }
        assertEquals(2, r.launcher.part().getShotsFired());
    }

    // ---- While it feeds ----

    @Test public void theSpeedAndDistanceAreFrozen() {
        make();
        feeding();
        double rpm = r.launcher.targetRpm();
        distance = 38.5;
        r.launcher.trimBy(5);
        run(0.4);
        assertEquals(rpm, r.launcher.targetRpm(), E);
        assertEquals(36, r.launcher.distanceIn(), E);
        release();
        run(0.1);
        assertEquals(1.05 * (2615 + 3.5 / 6 * 165), r.launcher.targetRpm(), 0.5);
    }

    @Test public void aWaitingGroupFollowsTheDistanceEvenWithTheLock() {
        make(Locked.class, Feed.CR_SERVOS);
        bearing = 5;
        press();
        run(0.5);
        distance = 39;
        run(0.2);
        bearing = 0;
        run(0.5);
        assertTrue(r.launcher.part().continuous().isFeeding());
        assertEquals("the distance when the feed started", 39, r.launcher.part().continuous().getSnapshot().distanceIn, E);
    }

    @Test public void theAimKeepsTurningAndTheDriverWins() {
        make();
        feeding();
        bearing = 2;
        assertTrue(r.aim().assistTurn(0) < 0);
        assertEquals("the stick wins", 0.4, r.aim().assistTurn(0.4), 0);
        release();
        assertEquals("over: the turn is the driver's", 0, r.aim().assistTurn(0), 0);
    }

    @Test public void theAimOffsetIsFrozen() {
        make();
        feeding();
        for (Tunable t : Tunable.all()) if (t.name().equals("Aim/cameraOffset")) t.increase();
        assertEquals("the group's offset", 0, r.aim().shotSight().aimOffsetDeg, E);
        assertEquals(0, r.aim().shotSight().aimErrorDeg, E);
        release();
        assertEquals("the live one again", 0.5, r.aim().shotSight().aimOffsetDeg, E);
    }

    // ---- Every end ----

    @Test public void releaseStopsInTheSameCallBeforeTheRobotsLoop() {
        make();
        feeding();
        r.launcher.ceaseFire();
        assertEquals(0, left(), 0);
        assertEquals(0, right(), 0);
        assertEquals(End.RELEASED, r.launcher.part().continuous().getEnd());
    }

    @Test public void releaseBeforeReadyFeedsNothing() {
        make();
        bearing = 8;
        press();
        run(1);
        release();
        bearing = 0;
        for (int i = 0; i < 100; i++) {
            loop();
            assertEquals(0, left(), 0);
        }
        assertEquals(0, r.launcher.part().getShotsFired());
    }

    @Test public void bStopsTheGroupAtOnce() {
        make();
        feeding();
        r.launcher.toggle();
        ended(End.STOPPED);
        assertFalse(r.launcher.isSpinning());
        staysEndedUntilAFreshPress();
    }

    @Test public void bEndsAGroupThatSpunUpAnIdleWheel() {
        make();
        r.launcher.idle();
        run(0.1);
        feeding();
        r.launcher.toggle();                                // not "keep it on": the group ends
        ended(End.STOPPED);
        assertFalse(r.launcher.isSpinning());
    }

    @Test public void stopStopsTheFeederAndTheWheel() {
        make();
        feeding();
        r.launcher.stop();
        ended(End.STOPPED);
        assertEquals(0, fly.power, 0);
        staysEndedUntilAFreshPress();
    }

    @Test public void idleStopsIt() {
        make();
        feeding();
        r.launcher.idle();
        ended(End.STOPPED);
        staysEndedUntilAFreshPress();
    }

    @Test public void haltStopsIt() {
        make();
        feeding();
        r.launcher.halt();
        ended(End.HALTED);
        staysEndedUntilAFreshPress();
    }

    @Test public void pauseDropsItAndResumeDoesNotRestoreIt() {
        make();
        feeding();
        r.pauseAll();
        assertEquals(0, left(), 0);
        assertEquals(0, fly.power, 0);
        assertEquals(End.PAUSED, r.launcher.part().continuous().getEnd());
        run(0.2);
        r.resume();
        assertEquals(0, r.launcher.part().getShotsLeft());
        staysEndedUntilAFreshPress();
    }

    @Test public void aReleaseWhilePausedFiresNothing() {
        make();
        press();
        r.pauseAll();
        release();
        r.resume();
        run(1);
        assertEquals(0, r.launcher.part().getShotsFired());
    }

    @Test public void helpEndsItInThatLoop() {
        make();
        feeding();
        help = true;
        loop();
        ended(End.HELP);
        help = false;
        staysEndedUntilAFreshPress();
    }

    @Test public void theTuningMenuEndsItInThatLoop() {
        make();
        feeding();
        menu = true;
        loop();
        ended(End.TUNING_MENU);
        menu = false;
        staysEndedUntilAFreshPress();
    }

    @Test public void noGroupStartsUnderHelp() {
        make();
        help = true;
        loop();
        press();
        for (int i = 0; i < 50; i++) {
            loop();
            assertEquals(0, left(), 0);
        }
        assertEquals(End.HELP, r.launcher.part().continuous().getEnd());
    }

    @Test public void aScreenOpenedBeforeTheHoldIsKnownKillsThePress() {
        make();
        press();
        help = true;
        loop();
        help = false;
        release();
        run(1);
        assertEquals("no single from that press", 0, r.launcher.part().getShotsFired());
    }

    @Test public void relightEndsItAtOnce() throws Exception {
        make();
        java.lang.reflect.Field sweep = Camera.class.getDeclaredField("sweep");
        sweep.setAccessible(true);
        sweep.set(r.camera, new LightingSweep(Collections.singletonList("one"), i -> { }, clock));
        feeding();
        assertTrue(r.camera.relight());
        ended(End.RELIGHT);
    }

    @Test public void ejectEndsItAndTheEjectGoesOn() {
        make();
        feeding();
        r.launcher.eject();
        ended(End.EJECT);
        run(0.1);
        assertTrue("the held button does not cancel it", r.launcher.isEjecting());
        release();
        assertTrue(r.launcher.isEjecting());
    }

    @Test public void anEjectActionDuringAGroupGoesOn() {
        make();
        feeding();
        Action eject = r.launcher.ejectAction();
        r.run(eject);
        run(0.1);
        assertEquals(End.EJECT, r.launcher.part().continuous().getEnd());
        assertTrue(eject.isRunning());
    }

    @Test public void intakeReverseEndsItAndOnlyBacksOutAfter() {
        make();
        feeding();
        r.intake.out();
        loop();
        ended(End.INTAKE_REVERSE);
        for (int i = 0; i < 50; i++) {
            loop();
            assertEquals("backing out, never forward", -0.9, left(), E);
        }
        r.intake.stop();
        staysEndedUntilAFreshPress();
    }

    @Test public void noGroupStartsWithTheIntakeReversed() {
        make();
        r.intake.out();
        press();
        for (int i = 0; i < 50; i++) {
            loop();
            assertTrue(left() <= 0);
        }
        assertEquals(End.INTAKE_REVERSE, r.launcher.part().continuous().getEnd());
    }

    @Test public void anotherTableEndsItInTheSameCall() {
        make();
        feeding();
        r.launcher.useTable("other");
        ended(End.TABLE_CHANGED);
        staysEndedUntilAFreshPress();
    }

    @Test public void anAllianceSwitchEndsItInThatLoop() {
        make();
        feeding();
        r.setAlliance(Alliance.RED);
        loop();
        ended(End.ALLIANCE_CHANGED);
    }

    @Test public void anotherSpeedEndsItInTheSameCall() {
        make();
        feeding();
        r.launcher.useSpeed(3000);
        ended(End.CONTROL_CHANGED);
    }

    @Test public void speedFromDistanceSwitchedOffEndsIt() {
        make();
        feeding();
        r.launcher.speedMode().off();
        loop();
        ended(End.CONTROL_CHANGED);
    }

    @Test public void aControlChangeEndsItInThatLoop() {
        make();
        feeding();
        r.launcher.part().getFlywheel().speedControl(Flywheel.SpeedControl.BANG_BANG);
        loop();
        ended(End.CONTROL_CHANGED);
        staysEndedUntilAFreshPress();
    }

    @Test public void aFeederStoppedByHandEndsIt() {
        make();
        feeding();
        r.feeder.stop();
        assertEquals(0, left(), 0);
        loop();
        ended(End.FEEDER_STOPPED);
        staysEndedUntilAFreshPress();
    }

    @Test public void aWheelUnderHalfEndsItInThatLoop() {
        make();
        feeding();
        wheelShare = 0.45;
        loop();
        ended(End.WHEEL_SLOW);
        wheelShare = 1;
        staysEndedUntilAFreshPress();
    }

    @Test public void aLostGoalEndsItAfterHalfASecond() {
        make();
        feeding();
        seen = false;
        run(0.3);
        assertTrue(r.launcher.isFiringContinuously());
        run(0.4);
        ended(End.GOAL_LOST);
        seen = true;
        staysEndedUntilAFreshPress();
    }

    @Test public void aDriftedAimEndsItAfterThreeTenths() {
        make();
        feeding();
        bearing = 3;
        run(0.2);
        assertTrue(r.launcher.isFiringContinuously());
        run(0.2);
        ended(End.AIM_LOST);
        bearing = 0;
        staysEndedUntilAFreshPress();
    }

    @Test public void aMovedRobotEndsIt() {
        make();
        feeding();
        distance = 41;
        loop();
        ended(End.DISTANCE_MOVED);
    }

    @Test public void theTimeLimitEndsItWithTheButtonHeld() {
        make();
        feeding();
        run(3);
        ended(End.MAX_TIME);
        staysEndedUntilAFreshPress();
    }

    @Test public void theOpModesStopStopsEverything() {
        make();
        feeding();
        r.all().stop();
        assertEquals(0, left(), 0);
        assertEquals(0, right(), 0);
        assertEquals(0, fly.power, 0);
    }

    @Test public void theFlywheelStopsWhenSettingTheBrakeFailsInPause() {
        make();
        feeding();
        com.qualcomm.robotcore.hardware.DcMotorEx working = fly.motor;
        java.lang.reflect.InvocationHandler failBrake = (p, m, a) -> {
            if (m.getName().equals("setZeroPowerBehavior")) throw new IllegalStateException("brake failed");
            return m.invoke(working, a);
        };
        Object broken = java.lang.reflect.Proxy.newProxyInstance(working.getClass().getClassLoader(),
                new Class<?>[] {com.qualcomm.robotcore.hardware.DcMotorEx.class}, failBrake);
        try {
            java.lang.reflect.Field f = com.crossbotics.ftchelper.robot.Launcher.class.getDeclaredField("flywheelMotor");
            f.setAccessible(true);
            f.set(r.launcher, broken);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        fly.power = 0.7;
        try {
            r.pauseAll();
            fail("the failure is shown");
        } catch (RuntimeException expected) {
            assertEquals(0, left(), 0);
            assertEquals(0, fly.power, 0);
        }
    }

    // ---- The other feeders ----

    @Test public void aMotorFeederFeedsAndStops() {
        make(On.class, Feed.MOTOR);
        press();
        run(0.8);
        assertEquals(0.9, d.motor("feeder").power, E);
        run(1);
        assertEquals("no feedSec", 0.9, d.motor("feeder").power, E);
        release();
        assertEquals(0, d.motor("feeder").power, 0);
    }

    @Test public void aPushServoRefusesWithANote() {
        make(NoAimOnFire.class, Feed.PUSH_SERVO);
        assertTrue(r.notes().toString(), r.notes().contains("Holding fire will NOT work: wrong feeder."));
        press();
        run(0.8);
        assertEquals(End.NO_FEEDER, r.launcher.part().continuous().getEnd());
        assertEquals("never faked with singles", 0, r.launcher.part().getShotsFired());
        assertTrue(screen(), screen().contains("feeder can't run nonstop"));
        release();
        press();
        release();
        run(1);
        assertEquals("a tap still fires one", 1, r.launcher.part().getShotsFired());
    }

    @Test public void noSpeedFromDistanceHasANote() {
        make(On.class, Feed.NO_CAMERA);
        assertTrue(r.notes().toString(), r.notes().contains("Holding fire will NOT work: no distance."));
    }

    @Test public void theFeedStopTheStageWheelAndThePushersStayOnThenAllStop() {
        make(On.class, Feed.FEED_STOP);
        press();
        double openedAt = Double.NaN, pushedAt = Double.NaN;
        for (int i = 0; i < 60; i++) {
            loop();
            if (Double.isNaN(openedAt) && d.servo("stop").position == 0.5) openedAt = clock.sec();
            if (Double.isNaN(pushedAt) && left() > 0) pushedAt = clock.sec();
        }
        assertTrue("opens first: " + openedAt + " then " + pushedAt, pushedAt - openedAt >= 0.1 - E);
        for (int i = 0; i < 40; i++) {
            wheelShare = i % 12 < 4 ? 0.75 : 1;
            loop();
            assertEquals(0.5, d.servo("stop").position, E);
            assertTrue(left() > 0);
            assertTrue(d.crServo("stage").power > 0);
            assertTrue(d.motor("intake").power > 0);
        }
        r.launcher.ceaseFire();
        assertEquals(0, left(), 0);
        assertEquals(0, right(), 0);
        assertEquals(0, d.crServo("stage").power, 0);
        assertEquals(0, d.motor("intake").power, 0);
        assertEquals(0, d.servo("stop").position, 0);
    }

    @Test public void aFeedStopGroupReleasedBeforeTheStopOpensNeverPushes() {
        make(On.class, Feed.FEED_STOP);
        press();
        for (int i = 0; i < 60 && !r.launcher.part().continuous().isFeeding(); i++) loop();
        assertTrue(r.launcher.part().continuous().isFeeding());
        release();
        for (int i = 0; i < 50; i++) {
            loop();
            assertEquals(0, left(), 0);
        }
    }

    @Test public void aCanceledActionStopsAContinuousFeedItStarted() {
        make();
        Action feed = Action.named("Feed").then(() -> r.feeder.startContinuous(Double.NaN)).waitSeconds(2);
        r.run(feed);
        run(0.1);
        assertEquals(0.9, left(), E);
        r.cancel(feed);
        assertEquals(0, left(), 0);
    }

    @Test public void everyPartStopsWhenOneStopThrows() {
        List<String> stopped = new ArrayList<>();
        com.qualcomm.robotcore.hardware.CRServo bad = servo("left", stopped, true);
        com.qualcomm.robotcore.hardware.CRServo good = servo("right", stopped, false);
        PretendDevices devices = new PretendDevices() {
            @Override @SuppressWarnings("unchecked")
            public <T> T get(Class<? extends T> type, String name) {
                if (name.equals("feeder_left")) return (T) bad;
                if (name.equals("feeder_right")) return (T) good;
                return super.get(type, name);
            }
        };
        Bot bot = new Bot(On.class, Feed.FEED_STOP);
        bot.setup(devices);
        bot.startForTests();
        com.crossbotics.ftchelper.launcher.Feeder f = bot.feeder.forLauncher();
        assertTrue(f.startContinuous(Double.NaN));
        stopped.clear();
        try {
            f.stopContinuous();
            fail("the failure is shown");
        } catch (RuntimeException expected) {
            assertTrue("the second servo still got its stop: " + stopped, stopped.contains("right"));
        }
    }

    private static com.qualcomm.robotcore.hardware.CRServo servo(String name, List<String> stopped, boolean failsAtZero) {
        return (com.qualcomm.robotcore.hardware.CRServo) java.lang.reflect.Proxy.newProxyInstance(
                com.qualcomm.robotcore.hardware.CRServo.class.getClassLoader(),
                new Class<?>[] {com.qualcomm.robotcore.hardware.CRServo.class}, (p, m, a) -> {
                    if (m.getName().equals("setPower") && (Double) a[0] == 0) {
                        stopped.add(name);
                        if (failsAtZero) throw new IllegalStateException(name + " unplugged");
                    }
                    return m.getReturnType() == double.class ? (Object) 0.0
                            : m.getReturnType() == boolean.class ? (Object) false : null;
                });
    }

    // ---- Through the real button bindings ----

    @Test public void theButtonStopsTheFeedInTheLoopItIsLetGoAndAHeldStartDoesNothing() {
        make();
        ButtonPlan plan = ControlMap.makePlan(r, false, true, (g1, g2) -> r.launcher.fireOn(g1.rb));
        ControllerBindings driver = new ControllerBindings(), operator = new ControllerBindings();
        Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        plan.compile(r, driver, operator, new ArrayList<>(), () -> g1, () -> g2);
        g1.right_bumper = true;
        driver.ignoreHeldButtons(g1, g2);                   // held through START
        for (int i = 0; i < 50; i++) {
            driver.update(g1, g2);
            loop();
            assertEquals(0, left(), 0);
        }
        g1.right_bumper = false;
        driver.update(g1, g2);
        loop();
        assertEquals(0, r.launcher.part().getShotsLeft());
        g1.right_bumper = true;
        for (int i = 0; i < 40; i++) {
            driver.update(g1, g2);
            loop();
        }
        assertTrue(left() > 0);
        g1.right_bumper = false;
        driver.update(g1, g2);
        assertEquals("stopped by the button's own loop", 0, left(), 0);
        assertTrue(plan.help().toString(), plan.help().toString().contains("hold: fire nonstop when aimed"));
    }

    // ---- The options off ----

    @Test public void offAPressFiresAtOnceAndAHoldIsQuick() {
        make(Off.class, Feed.CR_SERVOS);
        assertFalse(r.launcher.continuousOnHold());
        assertEquals(Flywheel.SpeedControl.PID, r.launcher.part().getFlywheel().getSpeedControl());
        r.launcher.fireWhileHeld();
        assertEquals("queued at the press", 1, r.launcher.part().getShotsLeft());
        button = true;
        run(1.2);
        assertFalse(r.launcher.isFiringContinuously());
        assertEquals(ShotMode.QUICK, r.launcher.shotModeNow());
        assertTrue(r.launcher.part().getShotsFired() > 1);
        assertFalse(r.notes().toString(), r.notes().toString().contains("Holding fire"));
    }

    @Test public void offTheAimOnlyTurnsForTodaysRule() {
        make(Off.class, Feed.CR_SERVOS);
        seen = false;
        run(0.3);
        press();
        seen = true;
        bearing = 10;
        assertEquals("no turn without a goal at the press", 0, r.aim().assistTurn(0), 0);
    }

    @Test public void offHelpAndMenuLeaveFiringAlone() {
        make(Off.class, Feed.CR_SERVOS);
        press();
        help = true;
        run(1);
        assertTrue("fires as before", r.launcher.part().getShotsFired() > 1);
    }

    @Test public void offPauseStillKeepsWaitingShots() {
        make(Off.class, Feed.CR_SERVOS);
        wheelShare = 0.2;
        r.launcher.fire(2);
        loop();
        r.pauseAll();
        r.resume();
        assertEquals(2, r.launcher.part().getShotsLeft());
    }

    @Test public void offPastTheTableIsFlatAndOnlySeen() {
        make(Off.class, Feed.CR_SERVOS);
        distance = 73;
        run(0.7);
        assertEquals(2918, r.launcher.targetRpm(), E);
        assertEquals(Aim.Stage.SEEN, r.aim().stage());
        assertFalse(screen(), screen().contains("estimated"));
    }

    // ---- Table ends ----

    private void at(double inches) {
        distance = inches;
        run(0.7);
    }

    @Test public void anEstimateLocksAndSaysEstimated() {
        make(Ends.class, Feed.CR_SERVOS);
        at(73);
        assertEquals(3884, r.launcher.targetRpm(), E);
        assertEquals(Aim.Stage.LOCKED, r.aim().stage());
        assertTrue(screen(), screen().contains("estimated"));
        assertFalse(screen(), screen().contains("speed limited"));
        at(36);
        assertFalse(screen(), screen().contains("estimated"));
    }

    @Test public void pastTheLimitSaysSpeedLimitedAndWhatToDo() {
        make(Ends.class, Feed.CR_SERVOS);
        at(130);
        assertEquals(4800, r.launcher.targetRpm(), E);
        assertEquals(Aim.Stage.SEEN, r.aim().stage());
        String s = screen();
        assertTrue(s, s.contains("estimated, speed limited"));
        assertTrue(s, s.contains("Drive closer to the goal."));
    }

    @Test public void theLimitHoldsAfterTheTrim() {
        make(Ends.class, Feed.CR_SERVOS);
        at(99);
        r.launcher.trimBy(5);
        run(0.2);
        assertEquals(4800, r.launcher.targetRpm(), E);
        assertTrue(screen(), screen().contains("(speed limit)"));
    }

    @Test public void anEstimateCanFireAGroup() {
        make(Ends.class, Feed.CR_SERVOS);
        at(73);
        press();
        run(0.8);
        assertTrue(r.launcher.part().continuous().isFeeding());
    }

    @Test public void theDefaultLimitIsFourFifthsOfTheMotorsTop() {
        make(EndsDefaultLimit.class, Feed.CR_SERVOS);
        assertEquals(4800, r.launcher.table("main").getExtendMaxRpm(), E);
    }

    @Test public void aFallingEndHasAnInitNote() {
        make(EndsFalling.class, Feed.CR_SERVOS);
        assertTrue(r.notes().toString(), r.notes().contains("Check shot table speedByDistance:"));
        assertTrue(r.notes().toString(), r.notes().contains("NO estimate past the last row."));
    }

    private void stopsInit(Class<?> numbers, String... words) {
        try {
            new Bot(numbers, Feed.CR_SERVOS).setup(new PretendDevices());
            fail("INIT should stop");
        } catch (IllegalStateException e) {
            for (String w : words) assertTrue(e.getMessage(), e.getMessage().contains(w));
            assertTrue(e.getMessage(), e.getMessage().contains("Open Tunables.java, find class Launcher."));
        }
    }

    @Test public void twoRowsAlikeStopInit() {
        stopsInit(EndsTwoRowsAlike.class, "Two table rows both use 35 inches.", "Delete one of those rows.");
    }

    @Test public void aWrongSettingStopsInitWithTheLineToWrite() {
        stopsInit(EndsBadLimit.class, "Use more than 0, at most 6000 RPM.", "public static double extendMaxRpm = 4800;");
        stopsInit(BadHysteresis.class, "Use 0 to less than 100 RPM.", "public static double bangHysteresisRpm = 50;");
        stopsInit(BadMaxSec.class, "at most 10 seconds", "public static double continuousMaxSec = 3;");
        stopsInit(BackwardPower.class, "turns backward", "public static double continuousFeedPower = 0.5;");
    }

    // ---- The speed control switch ----

    @Test public void theSwitchReadsBothWords() {
        make(Bang.class, Feed.CR_SERVOS);
        assertEquals(Flywheel.SpeedControl.BANG_BANG, r.launcher.part().getFlywheel().getSpeedControl());
        assertEquals(0.0022, r.launcher.part().getFlywheel().getBangKv(), E);
        assertFalse(r.notes().contains("Run the Flywheel Tuner."));
        make(PidWritten.class, Feed.CR_SERVOS);
        assertEquals(Flywheel.SpeedControl.PID, r.launcher.part().getFlywheel().getSpeedControl());
    }

    @Test public void anyOtherValueStopsInit() {
        stopsInit(WrongWord.class, "You wrote \"bang\".", "public static String speedControl = \"PID\";");
        stopsInit(NotText.class, "A setting must be a word in quotes.", "public static String speedControl = \"PID\";");
        stopsInit(NoText.class, "A setting must be a word in quotes.");
    }

    @Test public void anUntunedKvHasANote() {
        make(BangUntuned.class, Feed.CR_SERVOS);
        assertEquals(12.0 / 6000, r.launcher.part().getFlywheel().getBangKv(), E);
        assertTrue(r.notes().toString(), r.notes().contains("Run the Flywheel Tuner."));
    }

    @Test public void anUntunedKvStartsFromTheWrittenKf() {
        make(BangFromKf.class, Feed.CR_SERVOS);
        double kv = 12.08 * 28 / 60 / 32767 * 13.0;
        assertEquals(0.002237, kv, 1e-6);
        assertEquals(kv, r.launcher.part().getFlywheel().getBangKv(), 1e-12);
        String notes = r.notes().toString();
        assertTrue(notes, r.notes().contains("Wheel power starts from your kF (bangKv)."));
        assertTrue(notes, r.notes().contains("Not tuned yet: it may NOT hold the exact speed."));
        assertTrue(notes, r.notes().contains("Run the Flywheel Tuner."));
        assertFalse(notes, notes.contains("catalog guess"));
        make(BangFromKfNoVolts.class, Feed.CR_SERVOS);
        assertEquals("no nominalVolts written: the library's", 12.08 * 28 / 60 / 32767 * Flywheel.DEFAULT_NOMINAL_VOLTS,
                r.launcher.part().getFlywheel().getBangKv(), 1e-12);
    }

    @Test public void aWrittenBangKvWinsOverKf() {
        make(BangKvAndKf.class, Feed.CR_SERVOS);
        assertEquals(0.0021, r.launcher.part().getFlywheel().getBangKv(), 0);
        assertFalse(r.notes().contains("Run the Flywheel Tuner."));
        assertFalse(r.notes().toString().contains("starts from your kF"));
    }

    @Test public void withNoUsableKfTheCatalogGuessShowsItsNote() {
        for (Class<?> numbers : new Class<?>[] {BangUntuned.class, BangKfZero.class}) {
            make(numbers, Feed.CR_SERVOS);
            assertEquals(numbers.getSimpleName(), 12.0 / 6000, r.launcher.part().getFlywheel().getBangKv(), E);
            assertTrue(r.notes().toString(), r.notes().contains("Wheel power is a catalog guess (bangKv)."));
            assertTrue(r.notes().toString(), r.notes().contains("Not tuned yet: it may NOT hold the exact speed."));
        }
    }

    @Test public void pidIsUnchangedByTheKfStart() {
        make(PidWithKf.class, Feed.CR_SERVOS);
        Flywheel wheel = r.launcher.part().getFlywheel();
        assertEquals(Flywheel.SpeedControl.PID, wheel.getSpeedControl());
        wheel.setRpm(2643);
        wheel.update();
        assertEquals(2643.0 / 60 * 28, fly.velocityCommand, E);
        org.junit.Assert.assertArrayEquals(new double[] {10, 0, 0, 12.08}, fly.pidf, E);
        assertEquals(1, wheel.getFScale(), 0);
        assertEquals(com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_USING_ENCODER, fly.mode);
        assertEquals("bang-bang writes nothing", 0, fly.maxAbsPower, 0);
        assertFalse(r.notes().contains("Run the Flywheel Tuner."));
    }

    // The plant: its steady speed is the one that kF holds (power = kF x counts a second / 32767 at 13.0 V).
    @Test public void untunedBangBangReachesReadyOnAWheelThatMatchesKf() {
        for (double volts : new double[] {11.0, 11.5, 12.0, 12.5, 13.0}) {
            for (double target : new double[] {2500, 2643, 2918}) {
                make(BangFromKf.class, Feed.CR_SERVOS);
                d.battery(volts);
                Flywheel wheel = r.launcher.part().getFlywheel();
                wheel.setRpm(target);
                double rpm = 0, readyAt = Double.NaN, sum = 0;
                int settled = 0;
                for (double t = 0; t < 10; t += DT) {
                    double steady = fly.power * volts / 13.0 * 32767 / 12.08 * 60 / 28;
                    rpm += (steady - rpm) * (1 - Math.exp(-DT / 0.8));
                    fly.velocity = rpm / 60 * 28;
                    clock.addSec(DT);
                    wheel.update();
                    if (wheel.isReady() && Double.isNaN(readyAt)) readyAt = t;
                    if (t >= 7) {
                        assertTrue(target + " RPM at " + volts + " V: stays READY, at " + rpm, wheel.isReady());
                        sum += rpm;
                        settled++;
                    }
                }
                assertTrue(target + " RPM at " + volts + " V: READY in time, not at " + readyAt, readyAt < 6);
                assertEquals(target + " RPM at " + volts + " V: it holds the speed", target, sum / settled, 25);
            }
        }
    }

    @Test public void nothingMovesInInitWithBangBang() {
        d = new PretendDevices();
        new Bot(Bang.class, Feed.CR_SERVOS).setup(d);
        assertEquals(0, d.motor("launcher").maxAbsPower, 0);
        assertEquals(0, left(), 0);
    }

    @Test public void aFaultEndsTheGroupAndOnlyOffThenOnClearsIt() {
        make(Bang.class, Feed.CR_SERVOS);
        feeding();
        wheelShare = Double.NaN;
        loop();
        ended(End.ENCODER_FAULT);
        assertEquals(0, fly.power, 0);
        String s = screen();
        assertTrue(s, s.contains("Flywheel power OFF: bad speed reading"));
        assertTrue(s, s.contains("Switch the flywheel off, then on."));
        wheelShare = 1;
        run(1);
        release();
        press();
        run(1);
        assertTrue("holding fire does not clear it", r.launcher.part().getFlywheel().isFaulted());
        assertEquals(0, left(), 0);
        r.launcher.spinUp();
        assertTrue("on alone does not either", r.launcher.part().getFlywheel().isFaulted());
        release();
        r.launcher.toggle();                                // off
        assertTrue(r.launcher.part().getFlywheel().isFaulted());
        r.launcher.toggle();                                // on
        assertFalse(r.launcher.part().getFlywheel().isFaulted());
        run(0.5);
        feeding();
    }

    @Test public void pauseAndResumeDoNotClearAFault() {
        make(Bang.class, Feed.CR_SERVOS);
        wheelShare = Double.NaN;
        loop();
        wheelShare = 1;
        r.pauseAll();
        r.resume();
        run(0.2);
        assertTrue(r.launcher.part().getFlywheel().isFaulted());
        assertEquals(0, fly.power, 0);
    }

    @Test public void anEjectWithBangBangWaitsLongerAndNeverBrakes() {
        make(Bang.class, Feed.CR_SERVOS);
        Action eject = r.launcher.ejectAction();
        r.run(eject);
        wheelShare = 4;                                     // still coasting down from a shot
        for (int i = 0; i < 400; i++) {                     // 8 s: past PID's 3
            loop();
            assertEquals(0, fly.power, 0);
            assertEquals(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.FLOAT, fly.zeroPower);
        }
        assertTrue(eject.isRunning());
        assertEquals(0, left(), 0);
        wheelShare = 1;
        run(0.3);
        assertTrue("then it feeds", r.launcher.part().getShotsFired() == 1);
    }
}
