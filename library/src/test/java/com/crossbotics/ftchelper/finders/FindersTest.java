package com.crossbotics.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FindersTest {
    private static final double E = 1e-9;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void flywheelGainsFromTopSpeed() {
        double[] g = FinderMath.flywheelPidf(2800);            // ~6000 RPM at 28 counts per turn
        assertEquals(32767 / 2800.0, g[3], E);                 // F
        assertEquals(g[3] / 10, g[0], E);                      // P
        assertEquals(g[0] / 10, g[1], E);                      // I
        assertEquals(0, g[2], E);                              // D
        assertEquals(g[3], FinderMath.flywheelPidf(-2800)[3], E);   // a reversed motor gives the same gains
        assertEquals(0, FinderMath.flywheelPidf(0)[3], E);
        assertEquals(6000, FinderMath.ticksPerSecToRpm(2800, 28), E);
    }

    @Test public void wheelSizeFromPushAndDriveTests() {
        // 537.7 counts per turn, 1:1; a 96 mm (3.78 in) wheel rolls pi x 3.78 in per turn.
        double ticks = 48 / (Math.PI * 3.78) * 537.7;
        assertEquals(3.78, FinderMath.wheelDiameterFromPush(ticks, 48, 537.7, 1), 1e-6);
        assertTrue(Double.isNaN(FinderMath.wheelDiameterFromPush(0, 48, 537.7, 1)));
        // Asked 48, went 50: the wheels are really bigger.
        assertEquals(3.78 * 50 / 48, FinderMath.correctedDiameter(3.78, 48, 50), E);
        // Strafe asked 48, went only 40: the correction must grow.
        assertEquals(1.15 * 48 / 40, FinderMath.correctedStrafe(1.15, 48, 40), E);
    }

    @Test public void motorLimitsHandleACountThatGoesDown() {
        assertTrue(Arrays.equals(new double[] {0, 2580, 0}, FinderMath.limits(0, 2580)));
        // Counts go DOWN as it goes up: reversed, and the signs flip.
        assertTrue(Arrays.equals(new double[] {0, 2580, 1}, FinderMath.limits(0, -2580)));
        assertEquals(0.5, FinderMath.servoPosition(Double.NaN), E);
        assertEquals(1, FinderMath.servoPosition(1.2), E);
    }

    @Test public void resultsReplaceByKeyAndSaveToAFile() throws Exception {
        FinderResults r = new FinderResults("Test finder");
        r.set("servo:claw:0", "a");
        r.set("servo:claw:1", "b");
        r.set("servo:wrist:0", "c");
        r.set("servo:claw:0", "A");                            // same key: replaced, same place
        assertEquals(Arrays.asList("A", "b", "c"), r.lines());
        r.removeStartingWith("servo:claw");
        assertEquals(Arrays.asList("c"), r.lines());
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry);
        r.addTelemetry(t);
        assertTrue(t.pending().contains("c"));

        FinderResults.file = new File(tmp.getRoot(), "results.txt");
        assertTrue(FinderResults.append("== header ==", r.lines()));
        assertTrue(FinderResults.append("== again ==", r.lines()));   // adds, keeps the first
        List<String> saved = Files.readAllLines(FinderResults.file.toPath());
        assertEquals("== header ==", saved.get(0));
        assertEquals("c", saved.get(1));
        assertEquals("== again ==", saved.get(3));
    }

    @Test public void chooserWrapsAround() {
        Chooser c = new Chooser(Arrays.asList("lift", "claw", "arm"));
        assertEquals("arm", c.current());                      // sorted A to Z
        c.previous();
        assertEquals("lift", c.current());
        c.next();
        assertEquals("arm", c.current());
        Chooser empty = new Chooser(Arrays.<String>asList());
        assertTrue(empty.isEmpty());
        empty.next();
        assertEquals(null, empty.current());
    }

    @Test public void shotPointsSortReplaceAndWriteCode() {
        ShotPoints p = new ShotPoints(true);
        p.save(72, 3600, 20);
        p.save(36, 2900, 10);
        p.save(36.4, 2950, 11);                                // within 1 in: replaces the 36
        assertEquals(2, p.points().size());
        assertEquals(36.4, p.points().get(0).distance, E);
        assertEquals(Arrays.asList("new ShotTable()", "    .add(36.4, 2950, 11.0)", "    .add(72.0, 3600, 20.0)"),
                p.codeLines());
        assertTrue(p.removeNear(72.5));
        assertFalse(p.removeNear(50));
        ShotPoints noHood = new ShotPoints(false);
        noHood.save(36.4, 2950, 0);
        assertEquals(Arrays.asList("new ShotTable()", "    .add(36.4, 2950)"), noHood.codeLines());
    }

    // The lines for the robot in five files: the section is the device name in CamelCase.
    @Test public void tunablesLinesUseTheSectionName() {
        assertEquals("Tunables.Lift: min = 0, max = 2580, P1 = 1200",
                FinderResults.tunablesLine("lift", Arrays.asList("min = 0", "max = 2580", "P1 = 1200")));
        assertEquals("Tunables.IntakeDeploy: P1 = 0.210",
                FinderResults.tunablesLine("intake_deploy", Arrays.asList("P1 = 0.210")));
        ShotPoints p = new ShotPoints(false);
        assertEquals("", p.tunablesLine("Launcher"));
        p.save(72, 2900, 0);
        p.save(48.5, 2550, 0);
        assertEquals("public static double[][] speedByDistance = {{48.5, 2550}, {72, 2900}};   // in Tunables.Launcher", p.tunablesLine("Launcher"));
        // One table per game piece: its own field name.
        assertEquals("public static double[][] speedByDistanceNectar = {{48.5, 2550}, {72, 2900}};   // in Tunables.Launcher",
                p.tunablesLine("Launcher", "speedByDistanceNectar"));
    }

    @Test public void removingOneDevicesLinesKeepsADeviceWithALongerName() {
        FinderResults r = new FinderResults("Test finder");
        r.set("servo:claw:0", "claw");
        r.set("servo:claw_right:0", "claw_right");
        r.removeStartingWith("servo:claw:");
        assertEquals(Arrays.asList("claw_right"), r.lines());
    }

    @Test public void pushTestNeedsEveryWheelToAgree() {
        assertEquals(1000, FinderMath.pushTicks(new int[] {990, 1010, 1000, 1000}), E);
        assertTrue(Double.isNaN(FinderMath.pushTicks(new int[] {1000, 1000, 1000, -1000})));   // one wheel backward
        assertTrue(Double.isNaN(FinderMath.pushTicks(new int[] {1000, 1000, 1000, 100})));     // one wheel barely counts
        assertTrue(Double.isNaN(FinderMath.pushTicks(new int[] {0, 0, 0, 0})));
        assertEquals(-1000, FinderMath.pushTicks(new int[] {-1000, -1000, -1000, -1000}), E);  // all backward: caller says so
    }

    @Test public void everyHelpPageFitsOnTheDriverStation() {
        FinderOpMode[] finders = {
            new ServoFinderOpMode() { },
            new MotorFinderOpMode() { },
            new FlywheelFinderOpMode() { },
            new DriveTunerOpMode() {
                @Override protected com.crossbotics.ftchelper.core.RobotConfig robotConfig() { return null; }
            },
            new ShotTunerOpMode() {
                @Override protected com.crossbotics.ftchelper.launcher.Launcher createLauncher() { return null; }
            },
            new AimTunerOpMode() {
                @Override protected com.crossbotics.ftchelper.vision.TargetSource createCamera() { return null; }
            },
        };
        for (FinderOpMode f : finders) {
            String[] lines = f.helpLines();
            assertTrue(f.finderName() + ": help is empty", lines.length > 0);
            assertTrue(f.finderName() + ": help is too long for the screen", lines.length <= 13);
            for (String line : lines) assertTrue(f.finderName() + ": line too wide: " + line, line.length() <= 52);
        }
    }

    // v0.7.2 shipped a help-key method that called itself forever: every finder crashed at INIT.
    @Test public void theHelpKeysCanBeReadWithoutCrashing() throws Exception {
        FinderOpMode f = new ServoFinderOpMode() { };
        f.gamepad1 = new com.qualcomm.robotcore.hardware.Gamepad();
        f.gamepad2 = new com.qualcomm.robotcore.hardware.Gamepad();
        java.lang.reflect.Method m = FinderOpMode.class.getDeclaredMethod("updateHelpKeys");
        m.setAccessible(true);
        m.invoke(f);                                    // no help key: nothing happens
        f.gamepad1.start = true;
        m.invoke(f);                                    // START just pressed: not held long enough yet
        assertFalse(f.helpKeyDown() == false);
    }

    // The Shot Tuner, the way teams tune: 3-5 shots per speed, HIT or MISS, keep the best hit ratio.
    @Test public void theTallyKeepsTheSpeedWithTheBestHitRatio() {
        ShotPoints.Tally t = new ShotPoints.Tally();
        assertTrue(Double.isNaN(t.bestRpm()));
        t.mark(2500, false, 2490);
        t.mark(2500, false, 2480);
        assertTrue("nothing went in: no best", Double.isNaN(t.bestRpm()));
        t.mark(2500, true, 2500);                              // 1/3
        t.mark(2600, true, 2590);
        t.mark(2600, true, 2600);
        t.mark(2600, false, 2580);
        t.mark(2600, true, Double.NaN);                        // 3/4, one not measured
        t.mark(2700, true, 2690);                              // 1/1: a better ratio, but one shot
        t.mark(2700, false, 2690);                             // 1/2
        assertEquals(2600, t.bestRpm(), 1e-9);
        assertEquals(3, t.hits(2600));
        assertEquals(4, t.shots(2600));
        assertEquals(9, t.totalShots());
        assertEquals((2590 + 2600 + 2580) / 3.0, t.measuredRpm(2600), 1e-9);
        assertEquals("2500: 1/3, 2600: 3/4 (best), 2700: 1/2", t.summary());
        ShotPoints.Tally tie = new ShotPoints.Tally();
        tie.mark(2400, true, 2400);
        tie.mark(2400, false, 2400);                           // 1/2
        tie.mark(2300, true, 2300);
        tie.mark(2300, true, 2300);
        tie.mark(2300, false, 2300);
        tie.mark(2300, false, 2300);                           // 2/4: the same ratio, more shots
        assertEquals(2300, tie.bestRpm(), 1e-9);
        tie.setSpot(48);
        assertEquals(48, tie.spotIn(), 1e-9);
        tie.clear();
        assertTrue(tie.isEmpty());
        assertTrue(Double.isNaN(tie.spotIn()));
    }

    // Codex: with a hood, each speed AND hood angle is its own try (the winner keeps its hood).
    @Test public void theTallyKeepsEachHoodAngleApart() {
        ShotPoints.Tally t = new ShotPoints.Tally();
        t.mark(2500, 10, true, 2500);
        t.mark(2500, 10, true, 2500);                          // 2/2 at 10 deg
        t.mark(2500, 15, false, 2500);
        t.mark(2500, 15, true, 2500);                          // 1/2 at 15 deg
        assertEquals(2, t.tries());
        assertEquals(2, t.hits(2500, 10));
        assertEquals(1, t.hits(2500, 15));
        assertEquals(0, t.shots(2500));                        // no shot without a hood
        assertEquals(2500, t.best()[0], 1e-9);
        assertEquals("the hood the hits were shot with", 10, t.best()[1], 1e-9);
        assertEquals("2500 @ hood 10: 2/2 (best), 2500 @ hood 15: 1/2", t.summary());
        t.unmark(2500, 10, true, 2500);
        t.unmark(2500, 10, true, 2500);
        assertEquals("undone: the 15 deg try is the best now", 15, t.best()[1], 1e-9);
        assertEquals(1, t.tries());
    }

    // Codex + Alex: each shot keeps the speed, hood and distance it was FIRED with; undo; drop a phantom.
    @Test public void trialsMarkTheShotAsFiredUndoAndDrop() {
        ShotPoints.Trials tr = new ShotPoints.Trials();
        assertTrue(tr.mark(true).startsWith("No shot to mark"));
        tr.fired(2500, 10, 2490, 48);
        tr.fired(2500, 10, 2480, 48.5);
        // The speed and hood are changed now, before marking: the marks still go to 2500 / 10 deg.
        String note = tr.mark(true);
        assertTrue(note, note.startsWith("HIT at 2500 RPM, hood 10: 1/1"));
        tr.mark(false);
        assertEquals(1, tr.tally().hits(2500, 10));
        assertEquals(2, tr.tally().shots(2500, 10));
        assertEquals("the spot is where the first marked shot was fired", 48, tr.tally().spotIn(), 1e-9);
        assertTrue(tr.undo().startsWith("Took back the MISS"));
        assertEquals(1, tr.tally().shots(2500, 10));
        assertEquals("the shot waits to be marked again", 1, tr.unmarkedCount());
        tr.mark(true);
        assertEquals(2, tr.tally().hits(2500, 10));
        assertEquals("Nothing to undo.", new ShotPoints.Trials().undo());
        // A phantom: the wheel dipped with no piece. Drop the newest unmarked one.
        tr.fired(2600, 10, 2590, 48);
        tr.fired(2600, 10, 2590, 48);
        assertTrue(tr.dropLast().startsWith("Dropped the last shot (2600 RPM, hood 10)"));
        assertEquals(1, tr.unmarkedCount());
        assertEquals("No shot waiting to be marked.", new ShotPoints.Trials().dropLast());
        // Undo everything: the spot is forgotten with the last mark.
        ShotPoints.Trials one = new ShotPoints.Trials();
        one.fired(2500, Double.NaN, 2500, 30);
        one.mark(true);
        one.undo();
        assertTrue(one.tally().isEmpty());
        assertTrue(Double.isNaN(one.tally().spotIn()));
    }

    // The screen shows a hood ANGLE with 1 decimal (22.7), not 22.674.
    @Test public void theHoodOnTheScreenHasTheHoodsDecimals() {
        ShotPoints.Trials tr = new ShotPoints.Trials();
        tr.hoodDecimals(1);
        tr.fired(3220, 22.674, 3210, 48);
        String note = tr.mark(true);
        assertTrue(note, note.startsWith("HIT at 3220 RPM, hood 22.7: 1/1"));
        assertEquals("3220 @ hood 22.7: 1/1 (best)", tr.tally().summary());
        ShotPoints.Trials servo = new ShotPoints.Trials();
        servo.hoodDecimals(3);
        servo.fired(3220, 0.4215, 3210, 48);
        assertTrue(servo.mark(true).startsWith("HIT at 3220 RPM, hood 0.422"));
    }

    // Hood Finder: a calibration table the Hood would refuse at INIT is not shown.
    @Test public void theHoodFinderRefusesATableTheHoodWouldRefuse() {
        java.util.List<double[]> up = java.util.Arrays.asList(new double[] {0.2, 0}, new double[] {0.4, 10}, new double[] {0.6, 25});
        assertEquals("", HoodFinderOpMode.tableProblem(up));
        java.util.List<double[]> down = java.util.Arrays.asList(new double[] {0.2, 25}, new double[] {0.4, 10}, new double[] {0.6, 0});
        assertEquals("either way is fine, if it keeps going", "", HoodFinderOpMode.tableProblem(down));
        java.util.List<double[]> bent = java.util.Arrays.asList(new double[] {0.2, 0}, new double[] {0.4, 30}, new double[] {0.6, 25});
        assertTrue(HoodFinderOpMode.tableProblem(bent).contains("keep going one way"));
        java.util.List<double[]> same = java.util.Arrays.asList(new double[] {0.2, 0}, new double[] {0.2, 10}, new double[] {0.6, 25});
        assertTrue(HoodFinderOpMode.tableProblem(same).contains("same place"));
    }

    @Test public void movingAwayDropsTheOldSpotsShots() {
        ShotPoints.Trials tr = new ShotPoints.Trials();
        tr.fired(2500, Double.NaN, 2500, 48);
        assertEquals("still here", "", tr.check(50));
        assertEquals("no distance: nothing changes", "", tr.check(Double.NaN));
        assertTrue(tr.check(60).startsWith("Moved: 1 shot fired at the old spot dropped"));
        assertEquals(0, tr.unmarkedCount());
        tr.fired(2500, Double.NaN, 2500, 60);
        tr.mark(true);
        tr.fired(2500, Double.NaN, 2500, 60);
        assertTrue(tr.check(72).startsWith("Moved more than 4 in from where the shots were marked: the hit counts start again"));
        assertTrue(tr.tally().isEmpty());
        assertEquals(0, tr.unmarkedCount());
    }

    @Test public void savedPointsCarryTheirHitsAndMeasuredSpeed() {
        ShotPoints p = new ShotPoints(false);
        p.save(48, 2550, 0, 2531, 4, 5);
        assertEquals(Arrays.asList("new ShotTable()", "    .add(48.0, 2550)   // 4/5 in, measured 2531 RPM"), p.codeLines());
        assertEquals("the Tunables line keeps the speed asked for",
                "public static double[][] speedByDistance = {{48, 2550}};   // in Tunables.Launcher", p.tunablesLine("Launcher"));
        assertEquals(1, p.size());
        assertEquals(4, p.saved().get(0).hits);
    }

    @Test public void theDistanceIsAveragedOverHalfASecondOfFreshReadings() {
        ShotPoints.DistanceAverage a = new ShotPoints.DistanceAverage();
        assertTrue(Double.isNaN(a.average(10)));
        a.add(10.0, 47, "camera");
        a.add(10.1, 49, "camera");
        a.add(10.2, 54, "camera");                             // one noisy frame
        assertEquals(50, a.average(10.2), 1e-9);
        assertEquals(3, a.count(10.2));
        assertEquals(51.5, a.average(10.55), 1e-9);            // the 10.0 reading is too old now
        assertTrue("no fresh readings: no distance", Double.isNaN(a.average(11.5)));
        a.add(12.0, 60, "camera");
        a.add(12.1, 70, "odometry");                           // another source: starts again
        assertEquals(70, a.average(12.1), 1e-9);
        assertEquals("odometry", a.source());
    }

    @Test public void aSaggedShotIsMoreThanThreePercentOrSixtyRpmUnder() {
        assertEquals(60, ShotTunerOpMode.sagLimitRpm(1000), 1e-9);
        assertEquals(90, ShotTunerOpMode.sagLimitRpm(3000), 1e-9);
    }
}
