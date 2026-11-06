package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.testkit.PretendTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/** The INIT screen: the parts on starting numbers are one short line, so the warnings stay in view. */
public class InitScreenTest {

    @After public void back() {
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    @Test public void theStartingNumberNotesNameTheirPart() {
        assertEquals("Launcher", RobotDefinition.startingPart("Tunables.Launcher: DEFAULT, library defaults: stepRpm 100"));
        assertEquals("Launcher", RobotDefinition.startingPart("Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7. Run the Flywheel Finder"));
        assertEquals("Intake", RobotDefinition.startingPart("Tunables.Intake not written: library defaults used (fine)"));
        assertEquals("Lift", RobotDefinition.startingPart("Tunables.Lift: kD, kF not written: library defaults used (fine)"));
        assertEquals("Lift", RobotDefinition.startingPart("Lift: defaults from my-motor (custom): kP 0.005"));
        assertNull(RobotDefinition.startingPart("\"intake_deploy\" not in the configuration: IntakeDeploy is off (optional part)"));
    }

    public static class Numbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Claw { public static double open = 0.25, closed = 0.70; }
        public static class Launcher { public static double stepRpm = Starting.DEFAULT, readyBandRpm = Starting.DEFAULT; }
    }

    public static class Bot extends RobotDefinition {
        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            add(Intake.motor("intake").part("gobilda-5203-1150"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            add(Claw.servo("claw").part("gobilda-2000-torque"));
        }
    }

    // Only the numbers left at a LIBRARY guess (written DEFAULT) are on the screen, by name, and only in
    // PRACTICE. The parts catalog's numbers (the seller's) and numbers not written are fine: robot log only.
    @Test public void onlyLibraryGuessesAreNamedAndOnlyInPractice() {
        com.crossbotics.ftchelper.core.Profile.set(com.crossbotics.ftchelper.core.Profile.PRACTICE);
        try {
            Bot r = new Bot();
            r.setup(new PretendDevices());
            assertTrue("the full notes are still there: " + r.notes(), r.notes().toString().contains("defaults from the catalog"));
            PretendTelemetry screen = new PretendTelemetry();
            RobotTelemetry t = new RobotTelemetry(screen.telemetry);
            r.showNotes(t);
            String all = t.pending().toString();
            assertFalse("no long per-part lines on the screen: " + all, all.contains("defaults from the catalog ("));
            assertTrue(all + " notes " + r.notes(), all.contains("Library starting values (DEFAULT): Launcher.stepRpm, Launcher.readyBandRpm"));
            assertFalse("the catalog's numbers are not named: " + all, all.contains("Claw.") || all.contains("Intake."));

            com.crossbotics.ftchelper.core.Profile.set(com.crossbotics.ftchelper.core.Profile.COMPETITION);
            RobotTelemetry t2 = new RobotTelemetry(new PretendTelemetry().telemetry);
            r.showNotes(t2);
            assertFalse("not at an event: " + t2.pending(), t2.pending().toString().contains("Library starting values"));
        } finally {
            com.crossbotics.ftchelper.core.Profile.resetForTests();
        }
    }

    @Test public void theGuessesAreReadFromTheNotes() {
        assertEquals(java.util.Arrays.asList("Launcher.stepRpm", "Launcher.readyBandRpm"), RobotDefinition.libraryGuesses(
                java.util.Arrays.asList("Tunables.Launcher: DEFAULT, library defaults: stepRpm 100, readyBandRpm 60",
                        "Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7. Run the Flywheel Finder",
                        "Tunables.Intake not written: library defaults used (fine)")));
    }
}
