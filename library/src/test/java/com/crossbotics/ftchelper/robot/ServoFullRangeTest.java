package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.hardware.ServoSetup;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * .fullRange(): a servo part asks the hub for the servo's whole pulse range (500-2500 us, or the
 * catalog part's) at INIT. Without it nothing is changed (the SDK's 600-2400). A servo that cannot
 * change its pulse keeps its range and INIT says so.
 */
public class ServoFullRangeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        SafeMotor.freshReadingsForTests(true);
    }

    @After public void back() {
        SafeMotor.freshReadingsForTests(false);
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    public static class Numbers {
        public static class Claw { public static double open = 0.6, closed = 0.4; }
        public static class Wrist { public static double folded = 0.1, out = 0.6; }
        public static class Gate { public static double shut = 0.2, open = 0.7; }
        public static class Hood { public static double posA = 0.2, posB = 0.7, angleA = 0, angleB = 25; }
        public static class Indexer { public static double rest = 0.1, push = 0.5; }
        public static class Turret { public static double posA = 0.1, angleA = -90, posB = 0.9, angleB = 90; }
    }

    static class Bot extends RobotDefinition {
        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            add(Claw.servo("claw").part("gobilda-2000-torque").fullRange());
            add(Wrist.servo("wrist").part("gobilda-2000-speed"));                 // the SDK's normal range
            add(Positioner.servo("gate").fullRange());                            // no part: 500-2500
            add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer").fullRange())
                    .hood(Hood.servo("hood").fullRange()));
            add(Turret.servo("turret").fullRange());
        }
    }

    @Test public void fullRangeWidensThePulseAtInitAndNothingElseChanges() {
        RobotTestKit kit = new RobotTestKit();
        Bot r = new Bot();
        r.setup(kit);
        for (String s : new String[] {"claw", "gate", "indexer", "hood", "turret"}) {
            assertArrayEquals(s, new double[] {500, 2500}, kit.servos.get(s).pulseUs, 1e-9);
        }
        assertNull("no fullRange(): the pulse range is not touched", kit.servos.get("wrist").pulseUs);
        assertFalse(r.notes().toString(), r.notes().toString().contains("pulse"));
    }

    @Test public void theSimulatorFileSaysWhichServosUseTheirFullRange() {
        String lines = String.valueOf(new Bot().simLines());
        assertTrue(lines, lines.contains("servo.claw = 0.60, part=gobilda-2000-torque, pwm=500-2500"));
        assertTrue(lines, lines.contains("servo.wrist = 0.10, part=gobilda-2000-speed"));
        assertFalse(lines, lines.contains("servo.wrist = 0.10, part=gobilda-2000-speed, pwm"));
        assertTrue(lines, lines.contains("servo.gate = 0.20, part=gobilda-2000-torque, pwm=500-2500   # TODO"));
        assertTrue(lines, lines.contains("servo.hood = "));
        assertTrue(lines, lines.contains("pwm=500-2500   # TODO the real servo"));
    }

    @Test public void aServoThatCannotChangeItsPulseIsNotedAtInit() {
        RobotTestKit kit = new RobotTestKit();
        kit.servos.put("claw", new Fakes.FakeServo(false));
        Bot r = new Bot();
        r.setup(kit);
        assertTrue(r.notes().toString(), r.notes().toString().contains("Claw: the servo \"claw\" cannot change its pulse range"));
    }

    @Test public void fullRangeIsOnlyForServos() {
        expect(() -> Hood.motor("hood", "gobilda-5203-117").fullRange(), "Hood.motor(\"hood\") has no servo");
        expect(() -> Turret.motor("turret", "gobilda-5203-117").fullRange(), "fullRange() is for Turret.servo");
        expect(() -> Indexer.crServo("feed").fullRange(), "fullRange() is for Indexer.servo");
    }

    @Test public void theHardwareAndMechanismHelpersSetThePulse() {
        Fakes.FakeServo s = new Fakes.FakeServo();
        ServoSetup.of(s.servo).fullRange().done();
        assertArrayEquals(new double[] {500, 2500}, s.pulseUs, 1e-9);
        Fakes.FakeServo p = new Fakes.FakeServo();
        assertTrue(new PositionServo("p", p.servo).pulseRange(550, 2450).isFullRange());
        assertArrayEquals(new double[] {550, 2450}, p.pulseUs, 1e-9);
        assertFalse("a plain servo cannot", new PositionServo("q", new Fakes.FakeServo(false).servo).fullRange().isFullRange());
        expect(() -> ServoSetup.of(s.servo).pulseRange(2500, 500), "low first");
    }

    private static void expect(Runnable r, String words) {
        try {
            r.run();
            fail("expected: " + words);
        } catch (RuntimeException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(words));
        }
    }
}
