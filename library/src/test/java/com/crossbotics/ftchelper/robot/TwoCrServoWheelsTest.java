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
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/**
 * Two CR servo wheels on one indexer (Indexer.crServo(...).alsoServoReversed(...)): one wheel on each side of
 * the piece squeezes it into the launcher. Both get the same power; the second spins the other way.
 */
public class TwoCrServoWheelsTest {

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
        public static class FeederLeft { public static double power = 0.9, feedSec = 0.4; }
    }

    public static class Bot extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right")
                            .part("gobilda-2000-speed").optional()));
        }
    }

    private void runFor(RobotDefinition r, PretendMotor fly, double sec) {
        for (int i = 0; i < Math.round(sec / 0.005); i++) {
            clock.addSec(0.005);
            fly.velocity = fly.velocityCommand;
            r.all().update();
        }
    }

    @Test public void bothWheelsSpinTogetherTheOtherWayAndStop() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        assertEquals("nothing moves in INIT", 0, d.crServo("feeder_left").power + d.crServo("feeder_right").power, 1e-9);
        assertEquals(DcMotorSimple.Direction.FORWARD, d.crServo("feeder_left").direction);
        assertEquals("the second wheel spins the other way", DcMotorSimple.Direction.REVERSE, d.crServo("feeder_right").direction);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        runFor(r, fly, 0.3);
        r.launcher.fire();
        runFor(r, fly, 0.05);
        assertEquals("both wheels run", 0.9, d.crServo("feeder_left").power, 1e-9);
        assertEquals(0.9, d.crServo("feeder_right").power, 1e-9);
        runFor(r, fly, 0.5);
        assertEquals("both stop after feedSec", 0, d.crServo("feeder_left").power, 1e-9);
        assertEquals(0, d.crServo("feeder_right").power, 1e-9);
    }

    public static class SameWayBot extends RobotDefinition {
        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("feeder_left").reversed().alsoServo("feeder_right")));
        }
    }

    @Test public void alsoServoSpinsTheSameWayAsTheFirst() {
        PretendDevices d = new PretendDevices();
        new SameWayBot().setup(d);
        assertEquals(DcMotorSimple.Direction.REVERSE, d.crServo("feeder_left").direction);
        assertEquals("alsoServo: the same direction as the first (an upside-down servo)",
                DcMotorSimple.Direction.REVERSE, d.crServo("feeder_right").direction);
    }

    public static class WheelNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class FeederArm { public static double rest = 0.1, push = 0.7, pushSec = 0.3, returnSec = 0.3; }
        public static class FeederWheel { public static double power = 0.8, stageSec = 0.2, stopSec = 0.15; }
    }

    public static class WheelBot extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            tunables(WheelNumbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.servo("feeder_arm"))
                    .feedWheel(Indexer.crServo("feeder_wheel").alsoServoReversed("feeder_wheel_2")));
        }
    }

    @Test public void aTwoServoFeedWheelRunsBoth() {
        PretendDevices d = new PretendDevices();
        WheelBot r = new WheelBot();
        r.setup(d);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        runFor(r, fly, 0.3);
        r.launcher.fire();
        runFor(r, fly, 0.05);
        assertEquals(0.8, d.crServo("feeder_wheel").power, 1e-9);
        assertEquals("the second wheel gets the same power", 0.8, d.crServo("feeder_wheel_2").power, 1e-9);
        assertEquals(DcMotorSimple.Direction.REVERSE, d.crServo("feeder_wheel_2").direction);
        runFor(r, fly, 1.0);
        assertEquals(0, d.crServo("feeder_wheel").power, 1e-9);
        assertEquals(0, d.crServo("feeder_wheel_2").power, 1e-9);
    }

    @Test public void bothWheelsMissingIsSafe() {
        PretendDevices d = new PretendDevices();
        d.notConfigured("feeder_left", "feeder_right");
        Bot r = new Bot();
        r.setup(d);
        r.startForTests();
        r.launcher.spinUp();
        runFor(r, d.motor("launcher"), 0.3);
        r.launcher.fire();
        runFor(r, d.motor("launcher"), 0.5);
        // No crash, and nothing was sent to the wheels that are not there.
        assertEquals(0, d.crServo("feeder_left").writes + d.crServo("feeder_right").writes);
    }

    public static class TrimNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class FeederLeft { public static double alsoTrim = 0.05; }
    }

    public static class TrimBot extends RobotDefinition {
        @Override protected void define() {
            tunables(TrimNumbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right")));
        }
    }

    @Test public void alsoTrimOnTwoWheelsStopsInit() {
        try {
            new TrimBot().setup(new PretendDevices());
            fail("alsoTrim is for push servos");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("alsoTrim is for two push servos"));
        }
    }

    @Test public void theSimulatorGetsBothWheels() {
        Bot r = new Bot();
        List<String> lines = new ArrayList<>();
        for (String line : r.simLines()) if (line.startsWith("crservo.")) lines.add(line.trim());
        assertTrue(lines.toString(), lines.contains("crservo.feeder_left =   # gobilda-2000-speed"));
        assertTrue(lines.toString(), lines.contains("crservo.feeder_right =   # gobilda-2000-speed"));
    }
    // The two wheels are the feeder (no separate feed wheel): the intake's "out" turns them backward too.
    public static class OutBot extends RobotDefinition {
        public Launcher launcher;
        public Intake intake;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            intake = add(Intake.motor("intake"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right").part("gobilda-2000-speed"))
                    .feedWheelOutWith(intake));
        }
    }

    @Test public void theFeederWheelsRunBackwardWhileTheIntakePushesOut() {
        PretendDevices d = new PretendDevices();
        OutBot r = new OutBot();
        r.setup(d);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        runFor(r, fly, 0.05);
        assertEquals("still before out", 0, d.crServo("feeder_left").power, 1e-9);
        r.intake.out();
        runFor(r, fly, 0.05);
        assertEquals("backward at its power", -0.9, d.crServo("feeder_left").power, 1e-9);
        assertEquals("the second one too", -0.9, d.crServo("feeder_right").power, 1e-9);
        r.intake.in();
        runFor(r, fly, 0.05);
        assertEquals("stopped again", 0, d.crServo("feeder_left").power, 1e-9);
        assertEquals(0, d.crServo("feeder_right").power, 1e-9);
    }

    @Test public void aShotWinsOverBackward() {
        PretendDevices d = new PretendDevices();
        OutBot r = new OutBot();
        r.setup(d);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        runFor(r, fly, 0.3);   // at speed
        r.intake.out();
        runFor(r, fly, 0.05);
        assertEquals("backward", -0.9, d.crServo("feeder_left").power, 1e-9);
        r.launcher.fire();
        runFor(r, fly, 0.1);
        assertEquals("the shot: forward", 0.9, d.crServo("feeder_left").power, 1e-9);
        assertEquals(0.9, d.crServo("feeder_right").power, 1e-9);
        runFor(r, fly, 1.0);
        assertTrue("the shot is done", !r.launcher.isShooting());
        assertEquals("then backward again (the intake still pushes out)", -0.9, d.crServo("feeder_left").power, 1e-9);
    }

    // A push servo cannot turn backward: it still needs a feed wheel.
    public static class PushOutBot extends RobotDefinition {
        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            Intake intake = add(Intake.motor("intake"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.servo("feeder_arm")).feedWheelOutWith(intake));
        }
    }

    @Test public void aPushServoFeederStillNeedsAFeedWheel() {
        try {
            new PushOutBot().setup(new PretendDevices());
            fail("a push servo cannot turn backward");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("needs a part that spins"));
        }
    }

    // An optional feeder that is missing: nothing to turn, and nothing breaks.
    public static class MissingOutBot extends RobotDefinition {
        public Intake intake;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            intake = add(Intake.motor("intake"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right").optional())
                    .feedWheelOutWith(intake));
        }
    }

    @Test public void aMissingFeederWithOutWithIsSafe() {
        PretendDevices d = new PretendDevices();
        d.notConfigured("feeder_left", "feeder_right");
        MissingOutBot r = new MissingOutBot();
        r.setup(d);
        r.startForTests();
        r.intake.out();
        runFor(r, d.motor("launcher"), 0.1);
        assertEquals("nothing sent to wheels that are not there", 0,
                d.crServo("feeder_left").writes + d.crServo("feeder_right").writes);
    }
    // Pushes out with the intake and runs a little, so the feeder runs backward.
    private OutBot backingOut(PretendDevices d) {
        OutBot r = new OutBot();
        r.setup(d);
        r.startForTests();
        r.intake.out();
        runFor(r, d.motor("launcher"), 0.05);
        assertEquals("backward", -0.9, d.crServo("feeder_left").power, 1e-9);
        return r;
    }

    // A new power from the tuning menu reaches the wheels at once, also 0.
    @Test public void aNewPowerFromTheTuningMenuAppliesWhileBackward() {
        PretendDevices d = new PretendDevices();
        OutBot r = backingOut(d);
        com.crossbotics.ftchelper.tuning.Tunable.number("FeederLeft/power", 0, 1).set(0.5);
        runFor(r, d.motor("launcher"), 0.05);
        assertEquals(-0.5, d.crServo("feeder_left").power, 1e-9);
        assertEquals(-0.5, d.crServo("feeder_right").power, 1e-9);
        com.crossbotics.ftchelper.tuning.Tunable.number("FeederLeft/power", 0, 1).set(0);
        runFor(r, d.motor("launcher"), 0.05);
        assertEquals(0, d.crServo("feeder_left").power, 1e-9);
    }

    // The feeder's own command (not the launcher's fire) while backward: its feed runs, nothing stops it.
    @Test public void theFeedersOwnFeedWhileBackwardIsNotStopped() {
        PretendDevices d = new PretendDevices();
        OutBot r = backingOut(d);
        r.launcher.feeder().feedOne();
        runFor(r, d.motor("launcher"), 0.1);
        assertEquals("its feed runs forward", 0.9, d.crServo("feeder_left").power, 1e-9);
        runFor(r, d.motor("launcher"), 0.5);
        assertEquals("then backward again", -0.9, d.crServo("feeder_left").power, 1e-9);
    }

    @Test public void stopWhileBackwardStopsBothWheels() {
        PretendDevices d = new PretendDevices();
        OutBot r = backingOut(d);
        r.launcher.stop();
        assertEquals(0, d.crServo("feeder_left").power, 1e-9);
        assertEquals(0, d.crServo("feeder_right").power, 1e-9);
        r.intake.in();
        runFor(r, d.motor("launcher"), 0.1);
        assertEquals("stays stopped", 0, d.crServo("feeder_left").power, 1e-9);
    }

    // A motor feeder (a belt or roller) runs backward the same way.
    public static class MotorNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; }
        public static class Feeder { public static double power = 0.7, feedSec = 0.4; }
    }

    public static class MotorOutBot extends RobotDefinition {
        public Launcher launcher;
        public Intake intake;

        @Override protected void define() {
            tunables(MotorNumbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            intake = add(Intake.motor("intake"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.motor("feeder")).feedWheelOutWith(intake));
        }
    }

    @Test public void aMotorFeederRunsBackwardToo() {
        PretendDevices d = new PretendDevices();
        MotorOutBot r = new MotorOutBot();
        r.setup(d);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        runFor(r, fly, 0.3);
        r.intake.out();
        runFor(r, fly, 0.05);
        assertEquals("backward", -0.7, d.motor("feeder").power, 1e-9);
        r.launcher.fire();
        runFor(r, fly, 0.1);
        assertEquals("a shot wins", 0.7, d.motor("feeder").power, 1e-9);
        r.intake.in();
        runFor(r, fly, 1.0);
        assertEquals("stopped", 0, d.motor("feeder").power, 1e-9);
    }

    // With a feed stop the feeder still backs out, and a shot still wins.
    public static class StopNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Launcher { public static double speedRpm = 3000; public static double feedStopHold = 0.1, feedStopRelease = 0.6; }
        public static class FeederLeft { public static double power = 0.9, feedSec = 0.4; }
    }

    public static class StopOutBot extends RobotDefinition {
        public Launcher launcher;
        public Intake intake;

        @Override protected void define() {
            tunables(StopNumbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            intake = add(Intake.motor("intake"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feedStop("feed_stop")
                    .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right"))
                    .feedWheelOutWith(intake));
        }
    }

    @Test public void withAFeedStopTheFeederStillBacksOutAndAShotWins() {
        PretendDevices d = new PretendDevices();
        StopOutBot r = new StopOutBot();
        r.setup(d);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        runFor(r, fly, 0.3);
        r.intake.out();
        runFor(r, fly, 0.05);
        assertEquals("backward", -0.9, d.crServo("feeder_left").power, 1e-9);
        r.launcher.fire();
        runFor(r, fly, 0.2);
        assertEquals("a shot wins", 0.9, d.crServo("feeder_left").power, 1e-9);
        r.intake.in();
        runFor(r, fly, 1.5);
        assertEquals("stopped", 0, d.crServo("feeder_left").power, 1e-9);
    }

    // An optional feed wheel that is missing: a feeder that spins backs out in its place.
    public static class MissingWheelBot extends RobotDefinition {
        public Intake intake;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            intake = add(Intake.motor("intake"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right"))
                    .feedWheel(Indexer.crServo("feeder_wheel").optional())
                    .feedWheelOutWith(intake));
        }
    }

    @Test public void aMissingFeedWheelLetsTheSpinningFeederBackOut() {
        PretendDevices d = new PretendDevices();
        d.notConfigured("feeder_wheel");
        MissingWheelBot r = new MissingWheelBot();
        r.setup(d);
        r.startForTests();
        r.intake.out();
        runFor(r, d.motor("launcher"), 0.05);
        assertEquals(-0.9, d.crServo("feeder_left").power, 1e-9);
    }
}
