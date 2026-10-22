package com.crossbotics.ftchelper.robot;

import static com.crossbotics.ftchelper.robot.Starting.DEFAULT_FROM_CATALOG;
import static com.crossbotics.ftchelper.robot.Starting.MEASURE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.tuning.TuningReset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * DEFAULT_FROM_CATALOG and MEASURE in Tunables.java, and parts that are not in the catalog
 * (Motors.custom, Motors.unknown, Parts.customServo, Parts.unknownServo).
 */
public class StartingWordsTest {

    @Before public void setUp() {
        TuningReset.clear();
        Launcher.lastTrim.clear();
    }

    @After public void back() {
        TuningReset.clear();
    }

    private static RobotDefinition bot(final Class<?> numbers, final java.util.function.Consumer<RobotDefinition> parts) {
        return new RobotDefinition() {
            @Override protected void define() {
                if (numbers != null) tunables(numbers);
                drive(Drive.tank("l", "r"));
                parts.accept(this);
            }
        };
    }

    private static String stopMessage(RobotDefinition r) {
        try {
            r.setup(new RobotTestKit());
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        fail("INIT should have stopped");
        return "";
    }

    // ----------------------------- the words themselves -----------------------------

    @Test public void theWordsAreTwoOwnNotANumbers() {
        assertTrue(Double.isNaN(DEFAULT_FROM_CATALOG));
        assertTrue(Double.isNaN(MEASURE));
        assertTrue(Starting.isDefaultFromCatalog(DEFAULT_FROM_CATALOG));
        assertTrue(Starting.isMeasure(MEASURE));
        assertFalse(Starting.isDefaultFromCatalog(MEASURE));
        assertFalse("a NaN from a sum is not one of them", Starting.isDefaultFromCatalog(0.0 / 0.0));
        assertFalse(Starting.isMeasure(Double.NaN));
        assertFalse(Starting.isDefaultFromCatalog(1.5));
    }

    // ----------------------------- DEFAULT_FROM_CATALOG -----------------------------

    public static class LauncherWords {
        public static class Launcher {
            public static double kP = DEFAULT_FROM_CATALOG, kI = DEFAULT_FROM_CATALOG, kD = DEFAULT_FROM_CATALOG, kF = DEFAULT_FROM_CATALOG;
            public static double speedRpm = DEFAULT_FROM_CATALOG, stepRpm = DEFAULT_FROM_CATALOG;
            public static double readyBandRpm = 60, idleRpm = 0, shotIntervalSec = 0.25;
        }
    }

    @Test public void defaultFromCatalogIsLikeLeavingTheLineOutButSaidOnce() {
        RobotTestKit kit = new RobotTestKit();
        RobotDefinition r = bot(LauncherWords.class, d -> d.add(Launcher.flywheel("launcher", "gobilda-5203-6000")));
        r.setup(kit);
        assertEquals(11.7025, kit.motors.get("launcher").pidf[3], 1e-4);
        assertEquals(3600, ((Launcher) r.modules().get(0)).speedRpm(), 0);
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7, kP 1.17, kI 0.117, "
                + "speed 3600 rpm. Run the Flywheel Finder, then the Shot Tuner"));
        // stepRpm has no catalog value: the library's, listed once, not as "not written".
        assertTrue(notes, notes.contains("Tunables.Launcher: DEFAULT_FROM_CATALOG, library defaults: stepRpm 100"));
        assertFalse(notes, notes.contains("not written"));
        // The tuning menu says where the value came from.
        assertEquals("default from the catalog (gobilda-5203-6000)", find("Launcher/speedRpm").source());
        assertEquals("library default", find("Launcher/stepRpm").source());
    }

    private static Tunable find(String name) {
        for (Tunable t : Tunable.all()) if (t.name().equals(name)) return t;
        throw new AssertionError("no tunable " + name);
    }

    public static class LiftWords {
        public static class Lift {
            public static double min = 0, max = 2000;
            public static double kP = DEFAULT_FROM_CATALOG, kD = DEFAULT_FROM_CATALOG, high = 1800;
        }
    }

    @Test public void aTunedDefaultCopiesBackAsARealNumber() {
        RobotDefinition r = bot(LiftWords.class, d -> d.add(Lift.slide("lift").part("gobilda-5203-435")));
        r.setup(new RobotTestKit());
        Tunable kP = find("Lift/kP");
        assertEquals(0.0056, kP.get(), 1e-12);
        assertEquals("default from the catalog (gobilda-5203-435)", kP.source());
        kP.set(0.007);
        assertTrue(Tunable.changedAsCode().toString(), Tunable.changedAsCode().contains("Lift: kP = 0.007"));
    }

    @Test public void aLiftWithNoPartSaysItsDefaultsAreGeneral() {
        RobotDefinition r = bot(LiftWords.class, d -> d.add(Lift.slide("lift")));
        r.setup(new RobotTestKit());
        assertEquals(0.004, find("Lift/kP").get(), 0);
        assertEquals("general default (no part in Robot.java)", find("Lift/kP").source());
        assertTrue(r.notes().toString(), r.notes().toString().contains("Lift: no part in Robot.java, so general defaults"));
    }

    public static class LiftMaxFromCatalog {
        public static class Lift {
            public static double min = 0, max = DEFAULT_FROM_CATALOG;
        }
    }

    @Test public void aSafetyNumberNeverComesFromTheCatalog() {
        String m = stopMessage(bot(LiftMaxFromCatalog.class, d -> d.add(Lift.slide("lift").part("gobilda-5203-435"))));
        assertTrue(m, m.startsWith("Tunables.Lift.max = DEFAULT_FROM_CATALOG, but it is a safety number"));
        assertTrue(m, m.contains("Motor Finder, B"));
    }

    // ----------------------------- MEASURE -----------------------------

    public static class LiftMeasure {
        public static class Lift {
            public static double min = MEASURE, max = MEASURE;
        }
    }

    @Test public void measureStopsInitAndNamesTheTool() {
        String m = stopMessage(bot(LiftMeasure.class, d -> d.add(Lift.slide("lift"))));
        assertTrue(m, m.startsWith("Tunables.Lift.min = MEASURE: find it on the robot: "));
        assertTrue(m, m.contains("Motor Finder, X"));
    }

    public static class GainsMeasure {
        public static class Launcher {
            public static double kF = MEASURE;
        }
    }

    @Test public void measureOnAnyNumberNamesItsTool() {
        String m = stopMessage(bot(GainsMeasure.class, d -> d.add(Launcher.flywheel("launcher", "gobilda-5203-6000"))));
        assertTrue(m, m.startsWith("Tunables.Launcher.kF = MEASURE: a number you find on the robot: the Flywheel Finder"));
    }

    public static class ClawMeasure {
        public static class Claw {
            public static double open = MEASURE, closed = 0.7;
        }
    }

    @Test public void aPositionToMeasureNamesTheServoFinder() {
        String m = stopMessage(bot(ClawMeasure.class, d -> d.add(Claw.servo("claw"))));
        assertTrue(m, m.startsWith("Tunables.Claw.open = MEASURE: a number you find on the robot: the Servo Finder"));
    }

    @Test public void aPartNotOnTheRobotDoesNotStopOnMeasure() {
        RobotDefinition r = bot(LiftMeasure.class, d -> d.add(Lift.slide("lift").optional()));
        RobotTestKit kit = new RobotTestKit();
        kit.absent.add("lift");
        r.setup(kit);
    }

    @Test public void theSimulatorFileIsMadeWithMeasureStillInIt() {
        RobotDefinition r = bot(LiftMeasure.class, d -> d.add(Lift.slide("lift").part("gobilda-5203-435")));
        String lines = r.simLines().toString();
        assertTrue(lines, lines.contains("motor.lift = lift, part=gobilda-5203-435"));
    }

    // ----------------------------- parts not in the catalog -----------------------------

    @Test public void aCustomMotorGivesItsOwnDefaultsAndRobotFile() {
        RobotTestKit kit = new RobotTestKit();
        RobotDefinition r = bot(LauncherWords.class,
                d -> d.add(Launcher.flywheel("launcher", Motors.custom("my-flywheel-motor", 28, 5000))));
        r.setup(kit);
        assertEquals(32767 / (5000 * 28 / 60.0), kit.motors.get("launcher").pidf[3], 1e-9);
        assertEquals(3000, ((Launcher) r.modules().get(0)).speedRpm(), 0);
        assertTrue(r.notes().toString(), r.notes().toString().contains(
                "Launcher: defaults from my-flywheel-motor (custom): kF 14, kP 1.4, kI 0.14, speed 3000 rpm"));
        assertEquals("default from my-flywheel-motor (custom)", find("Launcher/speedRpm").source());
        String lines = r.simLines().toString();
        assertTrue(lines, lines.contains("motor.launcher = flywheel, ticksPerRev=28, rpm=5000"));
    }

    @Test public void aCustomMotorWithNoSpeedUsesGeneralDefaults() {
        RobotDefinition r = bot(null, d -> d.add(Launcher.flywheel("launcher", Motors.custom("no-speed-motor", 28, Double.NaN))));
        r.setup(new RobotTestKit());
        assertEquals(3600, ((Launcher) r.modules().get(0)).speedRpm(), 0);
        assertTrue(r.notes().toString(), r.notes().toString().contains("Launcher: the top speed of no-speed-motor is not known, so "
                + "general defaults (made for a 6000 RPM motor: kF 11.7, kP 1.17, kI 0.117, speed 3600 rpm). Give it with "
                + "Motors.custom(...), or run the Flywheel Finder, then the Shot Tuner"));
    }

    @Test public void aLauncherNeedsItsMotorsCounts() {
        try {
            Launcher.flywheel("launcher", Motors.unknown("mystery"));
            fail("needs counts per turn");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("has no counts per turn"));
        }
    }

    @Test public void anUnknownLiftMotorUsesGeneralDefaultsAndSaysSo() {
        RobotDefinition r = bot(LiftWords.class, d -> d.add(Lift.slide("lift").part(Motors.unknown("old-lift-motor"))));
        r.setup(new RobotTestKit());
        assertEquals(0.004, find("Lift/kP").get(), 0);
        assertTrue(r.notes().toString(), r.notes().toString().contains("Lift: the counts per turn of old-lift-motor are not known, "
                + "so general defaults"));
        assertTrue(r.simLines().toString(), r.simLines().toString().contains("# TODO old-lift-motor's numbers (Motors.custom in Robot.java)"));
    }

    @Test public void aCustomLiftMotorScalesLikeACatalogOne() {
        RobotDefinition r = bot(LiftWords.class, d -> d.add(Lift.slide("lift").part(Motors.custom("my-lift-motor", 384.5, 435))));
        r.setup(new RobotTestKit());
        assertEquals(0.0056, find("Lift/kP").get(), 1e-12);
        assertEquals("default from my-lift-motor (custom)", find("Lift/kP").source());
    }

    @Test public void aCatalogIdCannotBeACustomOne() {
        try {
            Motors.custom("gobilda-5203-312", 1, 1);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("is in the parts catalog"));
        }
    }

    public static class ClawTravel {
        public static class Claw {
            public static double open = 0.3, closed = 0.6, travelSec = DEFAULT_FROM_CATALOG;
        }
    }

    @Test public void aCustomServoTimesItsMoves() {
        RobotDefinition r = bot(ClawTravel.class, d -> d.add(Claw.servo("claw").part(Parts.customServo("my-servo", 200, 0.3))));
        r.setup(new RobotTestKit());
        // 200 degrees over 500-2500 us = 180 over the SDK's 600-2400; 180 / 60 x 0.3 = 0.9 s.
        assertEquals(0.9, ((Claw) r.modules().get(0)).part().getTravelTime(), 1e-9);
        assertTrue(r.notes().toString(), r.notes().toString().contains("Claw: defaults from my-servo (custom): travelSec 0.9 s"));
        assertTrue(r.simLines().toString(), r.simLines().toString().contains("servo.claw = 0.30, rangeDeg=180, secPer60=0.3"));
    }

    @Test public void aServoWithNoPartOrAnUnknownOneSaysItsTimeIsGeneral() {
        RobotDefinition r = bot(ClawTravel.class, d -> d.add(Claw.servo("claw")));
        r.setup(new RobotTestKit());
        assertTrue(r.notes().toString(), r.notes().toString().contains("Claw: no part in Robot.java, so general defaults "
                + "(travelSec 0.3 s). Add .part(...) (or Parts.customServo(...)), or time the move"));
        TuningReset.clear();
        RobotDefinition u = bot(ClawTravel.class, d -> d.add(Claw.servo("claw").part(Parts.unknownServo("mystery-servo"))));
        u.setup(new RobotTestKit());
        assertTrue(u.notes().toString(), u.notes().toString().contains("Claw: the numbers of mystery-servo are not known, so general "
                + "defaults (travelSec 0.3 s). Give them with Parts.customServo(...), or time the move"));
    }

    // ----------------------------- the turret's range, and no Tunables class -----------------------------

    public static class TurretNoRange {
        public static class Turret {
            public static double kP = 0.02;
        }
    }

    @Test public void aTurretOnTheDefaultRangeSaysSo() {
        RobotDefinition r = bot(TurretNoRange.class, d -> d.add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5)));
        r.setup(new RobotTestKit());
        assertTrue(r.notes().toString(), r.notes().toString().contains(
                "Turret: using the default range -90 to 90 deg: write your own min and max"));
    }

    @Test public void withNoTunablesClassEachPartStillSaysWhatItUses() {
        RobotDefinition r = bot(null, d -> d.add(Launcher.flywheel("launcher", "gobilda-5203-6000")));
        r.setup(new RobotTestKit());
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("No Tunables class next to Robot.java"));
        assertTrue(notes, notes.contains("Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7"));
        assertFalse(notes, notes.contains("not written"));
    }
}
