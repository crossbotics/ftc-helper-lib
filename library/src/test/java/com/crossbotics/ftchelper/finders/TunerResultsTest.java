package com.crossbotics.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.robot.PieceSensor;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * What the tuners tell a student to copy: the file, the class and exact Java, never a line from a test
 * that failed, and words that cannot read as a pass.
 */
public class TunerResultsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void aWheelAtRestNeverReadsMinusZero() {
        assertEquals("0", FinderResults.num(-0.0, 0));
        assertEquals("0", FinderResults.num(-0.4, 0));
        assertEquals("0.0", FinderResults.num(-0.04, 1));
        assertEquals("0.000", FinderResults.num(-0.0, 3));
        assertEquals("a real negative stays", "-1", FinderResults.num(-0.6, 0));
        assertEquals("-0.5", FinderResults.num(-0.5, 1));
    }

    @Test public void aNumberCopiedBackKeepsItsDigits() {
        assertEquals("10.00", FinderResults.exact(10, 2));
        assertEquals("0.005", FinderResults.exact(0.005, 2));
        assertEquals("0.0125", FinderResults.exact(0.0125, 3));
    }

    @Test public void aResultNamesTheFileTheClassAndTheExactJava() {
        FinderResults r = new FinderResults("Test finder");
        r.setTunable("a", "Launcher", "kF", "12.10");
        r.setTunable("b", "Drive", "calibrated", "true");
        r.setTunable("c", "Launcher", "kD", "0");
        assertEquals(Arrays.asList(
                "Open Tunables.java, find class Launcher.",
                "Change these lines (not there? add them):",
                "  public static double kF = 12.10;",
                "  public static double kD = 0;",
                "Open Tunables.java, find class Drive.",
                "Change these lines (not there? add them):",
                "  public static boolean calibrated = true;"), r.lines());
    }

    @Test public void anUnfinishedResultSaysNotDoneOnTheScreenAndInTheFile() throws Exception {
        FinderResults r = new FinderResults("Test finder");
        r.setTunable("a", "Feeder", "feedSec", "0.45");
        r.notDone("2 of 5 test shots done.");
        assertEquals("When it says DONE, change these lines:", r.lines().get(1));
        File was = FinderResults.file;
        try {
            FinderResults.file = new File(tmp.getRoot(), "results.txt");
            assertTrue(r.saveNow());
            List<String> saved = Files.readAllLines(FinderResults.file.toPath());
            assertEquals("NOT DONE: 2 of 5 test shots done.", saved.get(1));
        } finally {
            FinderResults.file = was;
        }
        r.done();
        assertEquals("DONE", r.status());
        assertEquals("Change these lines (not there? add them):", r.lines().get(1));
    }

    @Test public void aLongTableIsStillJavaAndFitsTheScreen() {
        String value = "{{27, 2500}, {32, 2550}, {35, 2615}, {41, 2780}, {45, 2918}, {50, 3090}}";
        List<String> java = FinderResults.javaLines("speedByDistance", value);
        StringBuilder one = new StringBuilder();
        for (String line : java) {
            assertTrue(line, line.length() <= 50);
            one.append(line.trim()).append(line.endsWith(",") ? " " : "");
        }
        assertEquals("public static double[][] speedByDistance = " + value + ";", one.toString());
    }

    @Test public void aFailedCheckNeverShowsTheNameOfPassing() {
        assertEquals(Arrays.asList("Flywheel is OFF", "NOT aimed at the goal"),
                FinderOpMode.notReadyWords("off, aimed at the goal"));
        assertEquals(Arrays.asList("Flywheel NOT at speed", "Still waiting for: gate closed"),
                FinderOpMode.notReadyWords("flywheel, gate closed"));
        assertTrue(FinderOpMode.notReadyWords("").isEmpty());
    }

    // Bug 11: "no number to change" is not a pass.
    @Test public void aMoveThatDidNotFinishCannotPass() {
        MoveMath.Suggestion s = MoveMath.suggestMoveTurn(0, 0, 0.6, 0.35, true);
        assertFalse(s.ok());
        assertEquals(MoveMath.Outcome.RUN_AGAIN, s.outcome);
        assertEquals("no number changes from it", null, s.field);
        assertEquals(MoveMath.Outcome.CANT_FIX, MoveMath.suggestMoveTurn(20, 0, 1.0, 0.8, false).outcome);
        assertEquals(MoveMath.Outcome.PASSED, MoveMath.suggestMoveTurn(4, 2, 0.6, 0.35, false).outcome);
        assertEquals(MoveMath.Outcome.RUN_AGAIN, MoveMath.suggestMoveTurn(3, 8, 0.6, 0.35, false).outcome);
    }

    // Bug 14: measured again and failed, the old nearMm and minSaturation lines go.
    @Test public void aFailedPieceMeasurementLeavesNoOldLine() {
        PieceTunerOpMode tuner = new PieceTunerOpMode() {
            @Override protected RobotDefinition robot() { return null; }
        };
        tuner.results = new FinderResults("Piece Tuner");
        PieceSensor s = PieceSensor.color("intake_color").kinds("POLLEN");
        java.util.Map<String, double[]> m = new java.util.LinkedHashMap<>();
        tuner.sensors.add(s);
        tuner.measured.put(s, m);
        m.put("POLLEN", new double[] {60, 0.6, 30});
        m.put(PieceTunerOpMode.EMPTY, new double[] {200, 0.1, 140});
        tuner.writeLines(s);
        assertTrue(tuner.results.lines().toString(), tuner.results.lines().toString().contains("nearMm = 85;"));
        assertEquals("DONE", tuner.results.status());

        m.put(PieceTunerOpMode.EMPTY, new double[] {200, 0.1, 31});      // now EMPTY reads like a piece
        tuner.writeLines(s);
        assertFalse(tuner.results.lines().toString().contains("nearMm"));
        assertTrue(tuner.results.status().startsWith("NOT DONE"));

        m.put(PieceTunerOpMode.EMPTY, new double[] {200, 0.1, 140});
        tuner.writeLines(s);
        tuner.beginMeasuring();                                            // the first choice (POLLEN) again
        assertFalse("the old one goes before the new one is found", tuner.results.lines().toString().contains("nearMm"));
        assertFalse(tuner.results.lines().toString().contains("POLLEN ="));

        m.put("POLLEN", new double[] {60, 0.6, 30});
        m.put(PieceTunerOpMode.EMPTY, new double[] {200, 0.1, Double.NaN});
        tuner.writeLines(s);
        assertFalse("never nearMm = NaN", tuner.results.lines().toString().contains("nearMm"));
    }

    // The same, for a camera's minSaturation.
    @Test public void aFailedCameraBackgroundLeavesNoOldSaturation() {
        PieceTunerOpMode tuner = new PieceTunerOpMode() {
            @Override protected RobotDefinition robot() { return null; }
        };
        tuner.results = new FinderResults("Piece Tuner");
        PieceSensor s = PieceSensor.camera("Webcam 2").kinds("POLLEN");
        java.util.Map<String, double[]> m = new java.util.LinkedHashMap<>();
        tuner.sensors.add(s);
        tuner.measured.put(s, m);
        m.put("POLLEN", new double[] {60, 0.6, Double.NaN});
        m.put(PieceTunerOpMode.EMPTY, new double[] {200, 0.1, Double.NaN});
        tuner.writeLines(s);
        assertTrue(tuner.results.lines().toString().contains("minSaturation = 0.35;"));
        m.put(PieceTunerOpMode.EMPTY, new double[] {200, 0.58, Double.NaN});
        tuner.writeLines(s);
        assertFalse(tuner.results.lines().toString().contains("minSaturation"));
    }

    // Bug 5: the Flywheel Finder has no feeder button.
    @Test public void theFlywheelFinderNeverSaysToUseAFeeder() throws Exception {
        String help = Arrays.toString(new FlywheelFinderOpMode() { }.helpLines());
        assertFalse(help, help.toLowerCase(java.util.Locale.US).contains("feeder"));
        assertTrue(help, help.contains("rod") && help.contains("NEVER fingers"));
        assertFalse(doc("06-flywheel-finder.md").contains("with the robot's own feeder"));
    }

    // Bug 6: the docs said the Hardware Check only reads, and has no help page. It moves parts, and has one.
    @Test public void theDocsDoNotSayTheHardwareCheckOnlyReads() throws Exception {
        assertFalse(doc("02-wiring-checks.md").contains("only reads the devices"));
        assertFalse(doc("01-tune-and-check.md").contains("has no help page"));
    }

    private static String doc(String page) throws Exception {
        return new String(Files.readAllBytes(new File("../docs/tuning/" + page).toPath()), StandardCharsets.UTF_8);
    }
}
