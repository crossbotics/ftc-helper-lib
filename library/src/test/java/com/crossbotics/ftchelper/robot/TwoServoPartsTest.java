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
import com.qualcomm.robotcore.hardware.Servo;
import org.junit.After;
import org.junit.Test;

/**
 * Two servos on one part (alsoServoReversed, alsoServo): two feed arms, one on each side, that pinch together behind the piece
 * and push it into the launcher; and a claw with a servo for each finger. Both go to the same positions;
 * the second mirrors the first, plus its trim.
 */
public class TwoServoPartsTest {

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
        public static class FeederLeft { public static double rest = 0.2, push = 0.6, pushSec = 0.3, returnSec = 0.3; }
    }

    public static class Bot extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.servo("feeder_left").alsoServoReversed("feeder_right")
                            .part("gobilda-2000-torque").optional()));
        }
    }

    private void runFor(Bot r, PretendMotor fly, double sec) {
        for (int i = 0; i < Math.round(sec / 0.005); i++) {
            clock.addSec(0.005);
            fly.velocity = fly.velocityCommand;
            r.all().update();
        }
    }

    @Test public void bothArmsPushTogetherAndComeBack() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        assertEquals("nothing moves in INIT", 0, d.servo("feeder_left").writes + d.servo("feeder_right").writes);
        r.startForTests();
        PretendMotor fly = d.motor("launcher");
        r.launcher.spinUp();
        runFor(r, fly, 0.3);
        assertEquals("both at rest after START", 0.2, d.servo("feeder_left").position, 1e-9);
        assertEquals(0.2, d.servo("feeder_right").position, 1e-9);
        assertEquals("the right arm mirrors the left", Servo.Direction.REVERSE, d.servo("feeder_right").direction);
        assertEquals(Servo.Direction.FORWARD, d.servo("feeder_left").direction);
        r.launcher.fire();
        runFor(r, fly, 0.05);
        assertEquals("both push", 0.6, d.servo("feeder_left").position, 1e-9);
        assertEquals(0.6, d.servo("feeder_right").position, 1e-9);
        runFor(r, fly, 0.4);
        assertEquals("both come back", 0.2, d.servo("feeder_left").position, 1e-9);
        assertEquals(0.2, d.servo("feeder_right").position, 1e-9);
    }

    @Test public void alsoServoIsForAPushServoOnly() {
        try {
            Indexer.crServo("roller").alsoServoReversed("roller_2");
            fail("a CR servo indexer");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("is for Indexer.servo"));
        }
    }

    public static class ClawNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class ClawLeft { public static double open = 0.3, closed = 0.7, alsoTrim = 0.05; }
    }

    public static class ClawBot extends RobotDefinition {
        public Claw claw;

        @Override protected void define() {
            tunables(ClawNumbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            claw = add(Claw.servo("claw_left").alsoServoReversed("claw_right"));
        }
    }

    @Test public void aTwoServoClawMovesBothFingersWithTheTrim() {
        PretendDevices d = new PretendDevices();
        ClawBot r = new ClawBot();
        r.setup(d);
        r.startForTests();
        r.claw.close();
        r.all().update();
        assertEquals(0.7, d.servo("claw_left").position, 1e-9);
        assertEquals("the second finger: the same position plus its trim", 0.75, d.servo("claw_right").position, 1e-9);
        assertEquals("mirrored", Servo.Direction.REVERSE, d.servo("claw_right").direction);
        r.claw.open();
        r.all().update();
        assertEquals(0.3, d.servo("claw_left").position, 1e-9);
        assertEquals(0.35, d.servo("claw_right").position, 1e-9);
    }

    @Test public void bothArmsMissingIsSafe() {
        PretendDevices d = new PretendDevices();
        d.notConfigured("feeder_left", "feeder_right");
        Bot r = new Bot();
        r.setup(d);
        r.startForTests();
        r.launcher.spinUp();
        runFor(r, d.motor("launcher"), 0.3);
        r.launcher.fire();
        runFor(r, d.motor("launcher"), 0.5);
        // No crash, and nothing was sent to the arms that are not there.
    }

    public static class SameWayNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Arm { public static double stowed = 0.2, deployed = 0.6; }
    }

    public static class SameWayBot extends RobotDefinition {
        public Positioner arm;

        @Override protected void define() {
            tunables(SameWayNumbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            arm = add(Positioner.servo("arm").alsoServo("arm_2"));
        }
    }

    @Test public void alsoServoTurnsTheSameWay() {
        PretendDevices d = new PretendDevices();
        SameWayBot r = new SameWayBot();
        r.setup(d);
        r.startForTests();
        r.arm.goTo("deployed");
        r.all().update();
        assertEquals(0.6, d.servo("arm_2").position, 1e-9);
        assertEquals(Servo.Direction.FORWARD, d.servo("arm_2").direction);
    }

    @Test public void aPartTakesOneSecondServoAtMost() {
        try {
            Claw.servo("claw_left").alsoServoReversed("claw_right").alsoServo("claw_extra");
            fail("a third servo");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("already has a second servo"));
        }
        try {
            Claw.servo("claw_left").alsoServoReversed("claw_left");
            fail("itself");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("is its first servo"));
        }
        assertEquals("claw_right", Claw.servo("claw_left").alsoServoReversed("claw_right").secondServoName());
        assertTrue(Claw.servo("claw_left").alsoServoReversed("claw_right").secondServoMirrored());
    }

    @Test public void aPositionServosFullRangeReachesItsSecondServo() {
        PretendDevices d = new PretendDevices();
        com.crossbotics.ftchelper.mechanism.PositionServo p = new com.crossbotics.ftchelper.mechanism.PositionServo("a",
                d.get(Servo.class, "a")).withServo(d.get(Servo.class, "b"), () -> 0).fullRange();
        assertTrue("the first servo's range", d.servo("a").pulseUs != null);
        assertEquals("the second servo gets the same range", d.servo("a").pulseUs[0], d.servo("b").pulseUs[0], 1e-9);
        assertEquals(d.servo("a").pulseUs[1], d.servo("b").pulseUs[1], 1e-9);
    }

    public static class TrimAloneNumbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Claw { public static double open = 0.3, closed = 0.7, alsoTrim = 0.02; }
    }

    public static class TrimAloneBot extends RobotDefinition {
        @Override protected void define() {
            tunables(TrimAloneNumbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            add(Claw.servo("claw"));
        }
    }

    @Test public void alsoTrimWithoutASecondServoStopsInit() {
        try {
            new TrimAloneBot().setup(new PretendDevices());
            fail("alsoTrim on a one-servo claw is ignored: INIT must say so");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("alsoTrim needs a second servo"));
        }
    }
}
