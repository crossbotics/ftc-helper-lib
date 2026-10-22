package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.finders.ShotPoints;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import com.crossbotics.ftchelper.util.FieldPose;
import java.io.File;
import java.util.function.LongSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The launcher's hood: the angle from the shot table's third column (blended), the wait for it before a
 * shot, by hand and the trim, the hard limits, a linkage's calibration table, plain positions, a motor
 * hood, and the INIT messages.
 */
public class HoodTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final Fakes.Clock clock = new Fakes.Clock();
    private LongSupplier aimClockBefore, padClockBefore;

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        aimClockBefore = Aim.clock;
        padClockBefore = Pad.clock;
        clock.nanos = 10_000_000_000L;
        Aim.clock = clock::get;
        Pad.clock = clock::get;
        Launcher.lastTrim.clear();
    }

    @After public void back() {
        Aim.clock = aimClockBefore;
        Pad.clock = padClockBefore;
        SettingsFile.useForTests(null);
        TuningReset.clear();
        Launcher.lastTrim.clear();
    }

    // ----------------------------- the numbers -----------------------------

    /** A servo hood, two stops with angles; one table with hood angles, one without. */
    public static class Angles {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double speedRpm = 2600;
            public static double[][] speedByDistancePollen = {{24, 2000, 4}, {72, 3000, 20}};
            public static double[][] speedByDistanceNectar = {{24, 2500}, {72, 3500}};
        }
        public static class Hood {
            // Stop A (0 deg) at 0.70, stop B (25 deg) at 0.20: the servo turns "the other way".
            public static double posA = 0.70, posB = 0.20, angleA = 0, angleB = 25;
            public static double homeAngle = 2, travelSec = 0.5, trimMax = 3;
        }
    }

    /** A linkage: a calibration table, not a straight line. */
    public static class Linkage {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000, 10}, {72, 3000, 30}};
        }
        public static class Hood {
            public static double[][] calibration = {{0.2, 0}, {0.5, 10}, {0.6, 30}};
        }
    }

    /** No angles measured: the hood column holds servo positions. */
    public static class Positions {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000, 0.30}, {72, 3000, 0.50}};
        }
        public static class Hood {
            public static double posA = 0.25, posB = 0.60;
        }
    }

    /** A motor hood: encoder ticks at the stops (0 = where it was at INIT). */
    public static class MotorTicks {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000, 5}, {72, 3000, 25}};
        }
        public static class Hood {
            public static double posA = 0, posB = 500, angleA = 0, angleB = 25;
            public static double kP = 0.01, maxPower = 0.6, tolerance = 0.5;
        }
    }

    public static class NoCalibration {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double speedRpm = 2600;
        }
        public static class Hood {
            public static double angleA = 0, angleB = 25;
        }
    }

    public static class MixedRows {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000, 4}, {72, 3000}};
        }
        public static class Hood {
            public static double posA = 0.2, posB = 0.7, angleA = 0, angleB = 25;
        }
    }

    public static class OutsideRange {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000, 4}, {72, 3000, 40}};
        }
        public static class Hood {
            public static double posA = 0.2, posB = 0.7, angleA = 0, angleB = 25;
        }
    }

    public static class HoodColumnNoHood {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000, 4}, {72, 3000, 20}};
        }
    }

    /** A robot whose distance comes from the odometry (the goal 48 in ahead at the start). */
    static final class HoodBot extends RobotDefinition {
        final AimTest.PlacedTracker tracker = new AimTest.PlacedTracker();
        final Class<?> numbers;
        final boolean motor, hood;
        Launcher launcher;

        HoodBot(Class<?> numbers, boolean motor, boolean hood) {
            this.numbers = numbers;
            this.motor = motor;
            this.hood = hood;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            odometry(Odometry.custom(d -> tracker));
            Launcher l = Launcher.flywheel("launcher", "gobilda-5203-6000").speedFromOdometry();
            if (numbers == Angles.class) l.startTable("pollen");   // the table with hood angles
            if (hood) l.hood(motor ? Hood.motor("hood", "gobilda-5203-117") : Hood.servo("hood").shotAngles(80, 55));
            launcher = add(l);
        }
    }

    private RobotTestKit kit;

    private HoodBot bot(Class<?> numbers, boolean motor) {
        HoodBot r = new HoodBot(numbers, motor, true);
        kit = new RobotTestKit();
        r.setup(kit);
        r.launcher.hood().clock = clock::get;
        r.started();
        return r;
    }

    // The robot this far from the goal (inches), and one loop.
    private void at(HoodBot r, double inches) {
        r.tracker.setPose(new FieldPose(-24, 48 - inches, 0));
        r.tracker.update();
        r.all().update();
    }

    private static String screen(Module<?> m) {
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        m.addTelemetry(t);
        t.update();
        return ft.lastScreen.toString();
    }

    private void pass(double sec) {
        clock.nanos += (long) (sec * 1e9);
    }

    private static void initFails(Class<?> numbers, String expected) {
        try {
            new HoodBot(numbers, false, true).setup(new RobotTestKit());
            fail("INIT should stop: " + expected);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expected));
        }
    }

    // ----------------------------- the tests -----------------------------

    @Test public void theHoodGoesHomeAtStartAndTheTableSetsItBlended() {
        HoodBot r = bot(Angles.class, false);
        Hood h = r.launcher.hood();
        assertEquals("home at START", 2, h.angle(), 1e-9);
        // 2 deg between 0 deg (0.70) and 25 deg (0.20): 0.70 - 2/25 x 0.5 = 0.66
        assertEquals(0.66, kit.servos.get("hood").position, 1e-9);
        at(r, 48);   // POLLEN table at 48 in: halfway between 4 and 20 deg
        assertEquals(12, h.angle(), 1e-9);
        assertTrue(h.followsTable());
        assertEquals(0.70 - 12 / 25.0 * 0.5, kit.servos.get("hood").position, 1e-9);
        assertTrue(screen(r.launcher), screen(r.launcher).contains("12.0 deg (table)"));
    }

    @Test public void theLauncherWaitsForTheHoodBeforeFeeding() {
        HoodBot r = bot(Angles.class, false);
        pass(1);                       // the first move (home) is over
        at(r, 24);                     // 4 deg: a 2 deg move = 2/25 of the range x 0.5 s = 0.04 s
        r.launcher.spinUp();
        assertTrue(r.launcher.part().whyNotReady(), r.launcher.part().whyNotReady().contains("hood"));
        pass(0.03);
        r.all().update();
        assertTrue(r.launcher.part().whyNotReady().contains("hood"));
        pass(0.02);
        r.all().update();
        assertFalse(r.launcher.part().whyNotReady(), r.launcher.part().whyNotReady().contains("hood"));
        // A small jitter in the distance (0.25 in = 0.08 deg) does not move the hood again.
        at(r, 24.25);
        assertEquals(4, r.launcher.hood().angle(), 1e-9);
        assertTrue(r.launcher.hood().isArrived());
    }

    @Test public void aTableWithoutHoodAnglesLeavesTheHoodByHand() {
        HoodBot r = bot(Angles.class, false);
        Hood h = r.launcher.hood();
        at(r, 48);
        assertEquals(12, h.angle(), 1e-9);
        r.launcher.useTable("nectar");      // two numbers per row: the hood stays where it is
        at(r, 48);
        assertFalse(h.followsTable());
        assertEquals(12, h.angle(), 1e-9);
        h.setAngle(30);                     // past the stop: kept to 25
        r.all().update();
        assertEquals(25, h.angle(), 1e-9);
        h.setAngle(-5);
        r.all().update();
        assertEquals(0, h.angle(), 1e-9);
        assertEquals("kept off stop A", 0.70, kit.servos.get("hood").position, 1e-9);
        assertTrue(screen(r.launcher), screen(r.launcher).contains("by hand) at its stop"));
    }

    @Test public void theTrimAddsToTheTableAndDoubleTapResetsIt() {
        HoodBot r = bot(Angles.class, false);
        Hood h = r.launcher.hood();
        at(r, 48);
        h.step(+1);                     // with the table: a trim of stepAngle (25 / 25 = 1)
        at(r, 48);
        assertEquals(13, h.angle(), 1e-9);
        h.trimBy(+10);                  // trimMax = 3
        at(r, 48);
        assertEquals(3, h.trim(), 1e-9);
        assertEquals(15, h.angle(), 1e-9);
        assertTrue(screen(r.launcher), screen(r.launcher).contains("trim +3.0 deg"));
        r.launcher.resetTrim();         // the launcher's double-tap D-pad down
        at(r, 48);
        assertEquals(0, h.trim(), 1e-9);
        assertEquals(12, h.angle(), 1e-9);
    }

    @Test public void theStickMovesTheHoodByHandAtItsSpeed() {
        HoodBot r = bot(Angles.class, false);
        Hood h = r.launcher.hood();
        r.launcher.useTable("nectar");  // no hood column: the stick moves the hood itself
        at(r, 48);
        h.byHand(1);                    // first push: no time yet
        pass(0.1);
        h.byHand(1);                    // 0.1 s at 25 / 2.5 = 10 per second: +1
        r.all().update();
        assertEquals(3, h.angle(), 1e-9);
        h.byHand(0);
        pass(1);
        h.byHand(-1);                   // a fresh push: no jump from the second it was let go
        r.all().update();
        assertEquals(3, h.angle(), 1e-9);
    }

    @Test public void aLinkageCalibrationTableIsBlended() {
        HoodBot r = bot(Linkage.class, false);
        Hood h = r.launcher.hood();
        assertEquals(0, h.minAngle(), 1e-9);
        assertEquals(30, h.maxAngle(), 1e-9);
        at(r, 48);                      // 20 deg: halfway between the 10 and 30 deg rows -> 0.55
        assertEquals(20, h.angle(), 1e-9);
        assertEquals(0.55, kit.servos.get("hood").position, 1e-9);
        at(r, 24);                      // 10 deg -> 0.5
        assertEquals(0.5, kit.servos.get("hood").position, 1e-9);
        assertEquals(5, h.angleAt(0.35), 1e-9);
    }

    @Test public void withoutAnglesTheHoodColumnIsAServoPosition() {
        HoodBot r = bot(Positions.class, false);
        Hood h = r.launcher.hood();
        assertFalse(h.usesAngles());
        assertEquals(0.25, h.angle(), 1e-9);   // home = posA
        at(r, 48);
        assertEquals(0.40, h.angle(), 1e-9);
        assertEquals(0.40, kit.servos.get("hood").position, 1e-9);
        assertTrue(screen(r.launcher), screen(r.launcher).contains("0.400 (table)"));
    }

    @Test public void aMotorHoodDrivesToItsTicksAndIsReadyByItsEncoder() {
        HoodBot r = bot(MotorTicks.class, true);
        Hood h = r.launcher.hood();
        at(r, 48);                       // 15 deg = 300 ticks
        assertEquals(15, h.angle(), 1e-9);
        assertEquals("full power toward it (kept to maxPower)", 0.6, kit.motors.get("hood").power, 1e-9);
        assertFalse(h.isArrived());
        kit.motors.get("hood").position = 292;    // 14.6 deg: within the tolerance of 0.5
        r.all().update();
        assertTrue(h.isArrived());
        assertEquals(0.08, kit.motors.get("hood").power, 1e-9);
        kit.motors.get("hood").position = 299;    // close: it rests
        r.all().update();
        assertEquals(0, kit.motors.get("hood").power, 1e-9);
        kit.motors.get("hood").position = 520;    // past stop B (500): it only pulls back
        r.all().update();
        assertTrue(kit.motors.get("hood").power < 0);
    }

    @Test public void initStopsWithClearMessages() {
        initFails(NoCalibration.class, "posA");
        initFails(MixedRows.class, "same columns");
        initFails(OutsideRange.class, "outside the hood's range");
    }

    @Test public void aHoodColumnWithNoHoodIsANote() {
        HoodBot r = new HoodBot(HoodColumnNoHood.class, false, false);
        r.setup(new RobotTestKit());
        assertTrue(r.notes().toString(), r.notes().toString().contains("no .hood(...)"));
    }

    @Test public void theSimulatorShotAngleFollowsTheHoodServo() {
        String lines = String.valueOf(new HoodBot(Angles.class, false, true).simLines());
        // The shot angle at servo 0 and 1: 80 deg at 0.70, 55 at 0.20 -> 45 at 0, 95 at 1.
        assertTrue(lines, lines.contains("hood=hood, hoodAt0=45, hoodAt1=95"));
        assertTrue(lines, lines.contains("servo.hood = 0.660"));
    }

    @Test public void theShotTunerLineHasTheHoodColumn() {
        ShotPoints p = new ShotPoints(true, 1);
        p.save(48, 2550, 12.5);
        p.save(24, 2200, 4);
        assertEquals("public static double[][] speedByDistance = {{24, 2200, 4}, {48, 2550, 12.5}};   // in Tunables.Launcher",
                p.tunablesLine("Launcher"));
        ShotPoints servo = new ShotPoints(true, 3);
        servo.save(48, 2550, 0.425);
        assertTrue(servo.tunablesLine("Launcher"), servo.tunablesLine("Launcher").contains("{48, 2550, 0.425}"));
        assertTrue(new ShotPoints(false).tunablesLine("Launcher").isEmpty());
    }
}
