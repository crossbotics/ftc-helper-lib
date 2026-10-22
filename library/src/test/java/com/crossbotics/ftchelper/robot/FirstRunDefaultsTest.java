package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.finders.FinderMath;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The first run, before any tuning: a part with no numbers written gets starting values from the parts
 * catalog where the catalog knows its motor or servo (not a number made for some other motor), and INIT
 * says so once, with the tool that tunes them. A written number always wins.
 */
public class FirstRunDefaultsTest {

    @Before public void setUp() {
        TuningReset.clear();
        Launcher.lastTrim.clear();
    }

    @After public void back() {
        TuningReset.clear();
    }

    // ----------------------------- the math -----------------------------

    // REV's formula (docs.revrobotics.com/first-global/fgc-pid): F = 32767 / top counts a second, P = F/10, I = P/10.
    @Test public void startingGainsAreRevsFormulaAndTheFlywheelFindersToo() {
        double[] g = Flywheel.startingPidf(2800);   // goBILDA 6000 RPM, 28 counts a turn: 6000 x 28 / 60
        assertEquals(32767 / 2800.0, g[3], 1e-12);
        assertEquals(g[3] / 10, g[0], 1e-12);
        assertEquals(g[0] / 10, g[1], 1e-12);
        assertEquals(0, g[2], 0);
        assertTrue("the Flywheel Finder uses the same math", Arrays.equals(g, FinderMath.flywheelPidf(2800)));
        assertTrue(Arrays.equals(new double[] {0, 0, 0, 0}, Flywheel.startingPidf(0)));
    }

    @Test public void theStartingSpeedIsSixtyPercentOfTheMotorsTopSpeed() {
        assertEquals(3600, Launcher.startingRpm(Motors.get("gobilda-5203-6000")), 0);
        assertEquals(970, Launcher.startingRpm(Motors.get("gobilda-5203-1620")), 0);    // 972 to 10 RPM
        assertEquals(3960, Launcher.startingRpm(Motors.get("am-neverest-orbital-1")), 0);
    }

    @Test public void aPlainFlywheelCanAskForStartingGains() {
        com.crossbotics.ftchelper.FakeMotor m = new com.crossbotics.ftchelper.FakeMotor();
        new Flywheel("f", m.motor).countsPerRev(28).maxRpm(6000).startingGains();
        assertEquals(32767 / 2800.0, m.pidf[3], 1e-9);
        assertEquals(32767 / 28000.0, m.pidf[0], 1e-9);
    }

    // ----------------------------- the launcher -----------------------------

    public static class NoLauncherNumbers {
        public static class Launcher {
            public static double stepRpm = 100;
        }
    }

    public static class TunedLauncher {
        public static class Launcher {
            public static double speedRpm = 2679, kP = 40, kI = 0, kD = 0, kF = 12.5;
        }
    }

    public static class TableNoGains {
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2200}, {48, 2550}};
        }
    }

    private static RobotDefinition launcherBot(final Class<?> numbers, final String motor) {
        return new RobotDefinition() {
            @Override protected void define() {
                if (numbers != null) tunables(numbers);
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", motor));
            }
        };
    }

    private static Launcher launcherOf(RobotDefinition r) {
        return (Launcher) r.modules().get(0);
    }

    @Test public void aLauncherWithNoGainsUsesTheCatalogNotTheHubsDefaults() {
        RobotTestKit kit = new RobotTestKit();
        RobotDefinition r = launcherBot(NoLauncherNumbers.class, "gobilda-5203-6000");
        r.setup(kit);
        double[] sent = kit.motors.get("launcher").pidf;
        assertEquals(1.17025, sent[0], 1e-4);
        assertEquals(0.117025, sent[1], 1e-5);
        assertEquals(0, sent[2], 0);
        assertEquals(11.7025, sent[3], 1e-4);
        assertEquals(3600, launcherOf(r).speedRpm(), 0);
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7, kP 1.17, kI 0.117, "
                + "speed 3600 rpm. Run the Flywheel Finder, then the Shot Tuner"));
        // speedRpm is in that note, not in the "library defaults used" one.
        assertFalse(notes, notes.contains("speedRpm"));
        // One note about it, not one per number.
        assertEquals(notes, 1, notes.split("defaults from the catalog").length - 1);
    }

    @Test public void writtenNumbersAlwaysWin() {
        RobotTestKit kit = new RobotTestKit();
        RobotDefinition r = launcherBot(TunedLauncher.class, "gobilda-5203-6000");
        r.setup(kit);
        assertTrue(Arrays.equals(new double[] {40, 0, 0, 12.5}, kit.motors.get("launcher").pidf));
        assertEquals(2679, launcherOf(r).speedRpm(), 0);
        assertFalse(r.notes().toString(), r.notes().toString().contains("defaults from"));
    }

    // A slow motor and no speedRpm: before, the fixed 2600 RPM default was above its top speed and INIT stopped.
    @Test public void aSlowMotorStartsBelowItsTopSpeed() {
        RobotTestKit kit = new RobotTestKit();
        RobotDefinition r = launcherBot(null, "gobilda-5203-1620");
        r.setup(kit);
        assertEquals(970, launcherOf(r).speedRpm(), 0);
        assertEquals(32767 / (1620 * 103.8 / 60), kit.motors.get("launcher").pidf[3], 1e-9);
    }

    // With a shot table the speed comes from the table: the note only names the gains and their tool.
    @Test public void withAShotTableTheNoteIsAboutTheGainsOnly() {
        RobotDefinition r = launcherBot(TableNoGains.class, "gobilda-5203-6000");
        r.setup(new RobotTestKit());
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7, kP 1.17, kI 0.117. "
                + "Run the Flywheel Finder"));
        assertFalse(notes, notes.contains("rpm)"));
    }

    // ----------------------------- the lift -----------------------------

    public static class LiftRange {
        public static class Lift {
            public static double min = 0, max = 2000;
        }
    }

    public static class LiftTuned {
        public static class Lift {
            public static double min = 0, max = 2000, kP = 0.01, tolerance = 30;
        }
    }

    private static RobotDefinition liftBot(final Class<?> numbers, final String part) {
        return new RobotDefinition() {
            @Override protected void define() {
                tunables(numbers);
                drive(Drive.tank("l", "r"));
                Lift l = Lift.slide("lift");
                add(part == null ? l : l.part(part));
            }
        };
    }

    private static Lift liftOf(RobotDefinition r) {
        return (Lift) r.modules().get(0);
    }

    // The plain defaults are for a 312 RPM motor (537.7 counts a turn): a 435 (384.5) gets them per motor turn.
    @Test public void aLiftWithAKnownMotorScalesItsStartingValues() {
        RobotDefinition r = liftBot(LiftRange.class, "gobilda-5203-435");
        r.setup(new RobotTestKit());
        Lift l = liftOf(r);
        assertEquals(0.0056, l.part().getPid().getKP(), 1e-12);    // 0.004 x 537.7 / 384.5 = 0.00559
        assertEquals(0.00028, l.part().getPid().getKD(), 1e-12);
        assertEquals(0.05, l.part().getPid().getKF(), 1e-12);       // a power: the same for every motor
        assertEquals(14, l.toleranceUsed, 0);                       // 20 x 384.5 / 537.7 = 14.3
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("Lift: defaults from the catalog (gobilda-5203-435): kP 0.0056, kD 0.00028, "
                + "slowZone 179, tolerance 14. Tune them live in the tuning menu (PRACTICE, hold GUIDE)"));
    }

    @Test public void aLiftsWrittenNumbersWinAndASlowMotorSlowsDownSooner() {
        RobotDefinition r = liftBot(LiftTuned.class, "gobilda-5203-117");
        r.setup(new RobotTestKit());
        Lift l = liftOf(r);
        assertEquals(0.01, l.part().getPid().getKP(), 0);
        assertEquals(30, l.toleranceUsed, 0);
        String notes = r.notes().toString();
        // 250 counts on a 312 RPM motor = 0.46 turn = 663 counts on a 117 RPM motor (1425.1 a turn).
        assertTrue(notes, notes.contains("(gobilda-5203-117): kD 0.000075, slowZone 663. Tune them"));
    }

    // No part(...): the motor is not known, so the plain defaults (and no catalog note).
    @Test public void aLiftWithNoKnownMotorKeepsThePlainDefaults() {
        RobotDefinition r = liftBot(LiftRange.class, null);
        r.setup(new RobotTestKit());
        assertEquals(0.004, liftOf(r).part().getPid().getKP(), 0);
        assertEquals(20, liftOf(r).toleranceUsed, 0);
        assertFalse(r.notes().toString(), r.notes().toString().contains("defaults from the catalog"));
        assertTrue(r.notes().toString(), r.notes().toString().contains("Lift: no part in Robot.java, so general defaults (kP, kD, "
                + "slowZone, tolerance made for a goBILDA 312 RPM motor). Add .part(...) (or Motors.custom(...)), or tune them in "
                + "the tuning menu"));
    }

    // ----------------------------- servos -----------------------------

    public static class ClawNumbers {
        public static class Claw {
            public static double open = 0.3, closed = 0.6;
        }
    }

    @Test public void aServoPartsTravelTimeComesFromTheCatalog() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(ClawNumbers.class);
                drive(Drive.tank("l", "r"));
                add(Claw.servo("claw").part("gobilda-2000-torque"));
            }
        };
        r.setup(new RobotTestKit());
        // 270 degrees (the SDK's normal range) at 0.25 s per 60 degrees (4.8 V): 1.125 s.
        assertEquals(1.13, ((Claw) r.modules().get(0)).part().getTravelTime(), 1e-9);
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("Claw: defaults from the catalog (gobilda-2000-torque): travelSec 1.13 s "
                + "for a full move. Time a full move to check"));
        assertFalse("its positions are written: no guess note", notes.contains("library guesses"));
    }

    /** A Tunables.java with no Claw section. */
    public static class NoClaw {
        public static class Auto {
            public static double parkInches = 24;
        }
    }

    @Test public void aClawWithNoPositionsSaysItsPositionsAreGuesses() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(NoClaw.class);
                drive(Drive.tank("l", "r"));
                add(Claw.servo("claw"));
            }
        };
        r.setup(new RobotTestKit());
        assertEquals("no part: the plain 0.3 s", 0.3, ((Claw) r.modules().get(0)).part().getTravelTime(), 0);
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("Claw: no positions written: open 0.2, closed 0.75 are library guesses, not your "
                + "servo's: find yours with the Servo Finder before the first TeleOp"));
    }

    public static class HoodStops {
        public static class Hood {
            public static double posA = 0.2, posB = 0.6;
        }
        public static class Launcher {
            public static double speedRpm = 3000, kF = 12;
        }
        public static class Indexer {
            public static double rest = 0.1, push = 0.6;
        }
    }

    @Test public void aServoHoodAndIndexerTimeTheirMovesFromTheCatalog() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(HoodStops.class);
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                        .feeder(Indexer.servo("indexer").part("gobilda-2000-torque"))
                        .hood(Hood.servo("hood").part("gobilda-2000-torque")));
            }
        };
        r.setup(new RobotTestKit());
        String notes = r.notes().toString();
        // Hood: 0.4 of 270 degrees at 0.25 s per 60 degrees = 0.45 s.
        assertTrue(notes, notes.contains("Hood: defaults from the catalog (gobilda-2000-torque): travelSec 0.45 s "
                + "from stop to stop"));
        // Indexer: 0.5 of 270 degrees = 0.5625 s, to 2 decimals.
        assertTrue(notes, notes.contains("Indexer: defaults from the catalog (gobilda-2000-torque): pushSec 0.56, "
                + "returnSec 0.56. Watch a feed and change them by test"));
    }
}
